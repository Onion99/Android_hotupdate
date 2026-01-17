package com.orange.patchgen.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orange.patchgen.PatchGenerator;
import com.orange.patchgen.callback.GeneratorCallback;
import com.orange.patchgen.callback.GeneratorErrorCode;
import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.GeneratorConfig;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.config.SigningConfig;
import com.orange.patchgen.model.PatchResult;
import com.orange.patchnative.NativePatchEngine;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 端补丁生成器
 * 
 * 在 Android 设备上生成热更新补丁，支持：
 * - 从已安装应用获取基线 APK
 * - 后台线程生成
 * - 自动选择 Java/Native 引擎
 * - 存储空间检查
 * - 进度回调和取消机制
 * 
 * Requirements: 9.1-9.11
 */
public class AndroidPatchGenerator {
    
    private final Context context;
    private final File baseApk;
    private final File newApk;
    private final File outputFile;
    private final SigningConfig signingConfig;
    private final EngineType engineType;
    private final PatchMode patchMode;
    private final AndroidGeneratorCallback callback;
    private final boolean checkStorage;
    private final boolean callbackOnMainThread;
    
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private Future<?> currentTask;
    private PatchGenerator coreGenerator;
    private final Handler mainHandler;
    private final StorageChecker storageChecker;
    
    private AndroidPatchGenerator(Builder builder) {
        this.context = builder.context;
        this.baseApk = builder.baseApk;
        this.newApk = builder.newApk;
        this.outputFile = builder.outputFile;
        this.signingConfig = builder.signingConfig;
        this.engineType = builder.engineType;
        this.patchMode = builder.patchMode;
        this.callback = builder.callback;
        this.checkStorage = builder.checkStorage;
        this.callbackOnMainThread = builder.callbackOnMainThread;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.storageChecker = new StorageChecker(context);
    }

    /**
     * 在后台线程生成补丁
     * 
     * 生成结果通过回调返回。如果设置了 callbackOnMainThread，
     * 回调将在主线程执行，否则在后台线程执行。
     * 
     * Requirements: 9.11
     */
    public void generateInBackground() {
        if (running.getAndSet(true)) {
            notifyError(GeneratorErrorCode.ERROR_UNKNOWN, "Generator is already running");
            return;
        }
        
        cancelled.set(false);
        
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AndroidPatchGenerator");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        
        currentTask = executor.submit(() -> {
            try {
                notifyStart();
                PatchResult result = generateInternal();
                if (!cancelled.get()) {
                    notifyComplete(result);
                }
            } catch (PatchGenerator.PatchGeneratorException e) {
                if (!cancelled.get()) {
                    notifyError(e.getErrorCode(), e.getMessage());
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    notifyError(GeneratorErrorCode.ERROR_UNKNOWN, 
                            "Unexpected error: " + e.getMessage());
                }
            } finally {
                running.set(false);
            }
        });
    }
    
    /**
     * 同步生成补丁
     * 
     * 注意：此方法会阻塞当前线程，请勿在主线程调用。
     * 
     * @return 生成结果
     * @throws PatchGenerator.PatchGeneratorException 生成失败时抛出
     * 
     * Requirements: 9.1
     */
    @NonNull
    public PatchResult generate() throws PatchGenerator.PatchGeneratorException {
        if (running.getAndSet(true)) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Generator is already running", GeneratorErrorCode.ERROR_UNKNOWN);
        }
        
        cancelled.set(false);
        
        try {
            return generateInternal();
        } finally {
            running.set(false);
        }
    }
    
    /**
     * 内部生成逻辑
     */
    private PatchResult generateInternal() throws PatchGenerator.PatchGeneratorException {
        // 1. 验证输入
        validateInputs();
        
        // 2. 检查存储空间
        if (checkStorage) {
            notifyProgress(5, "Checking storage space...");
            StorageChecker.StorageCheckResult storageResult = 
                    storageChecker.checkStorage(baseApk, newApk, outputFile.getParentFile());
            
            if (!storageResult.isSufficient()) {
                throw new PatchGenerator.PatchGeneratorException(
                        storageResult.getMessage(), 
                        GeneratorErrorCode.ERROR_INSUFFICIENT_SPACE);
            }
        }
        
        if (cancelled.get()) {
            notifyCancelled();
            return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
        }
        
        // 3. 获取 APK 版本信息（使用 PackageManager，更可靠）
        PackageInfo basePackageInfo = getApkPackageInfo(baseApk);
        PackageInfo newPackageInfo = getApkPackageInfo(newApk);
        
        // 4. 选择引擎
        EngineType actualEngineType = selectEngine();
        notifyProgress(10, "Using " + actualEngineType.name() + " engine");
        
        // 5. 配置生成器
        GeneratorConfig config = GeneratorConfig.builder()
                .engineType(actualEngineType)
                .patchMode(patchMode)
                .tempDir(storageChecker.getTempDir())
                .build();
        
        // 6. 创建核心生成器
        coreGenerator = new PatchGenerator.Builder()
                .baseApk(baseApk)
                .newApk(newApk)
                .output(outputFile)
                .signingConfig(signingConfig)
                .engineType(actualEngineType)
                .patchMode(patchMode)
                .config(config)
                .callback(createCoreCallback())
                .build();
        
        // 7. 执行生成
        PatchResult result;
        try {
            result = coreGenerator.generate();
        } finally {
            coreGenerator = null;
            // 清理临时文件
            storageChecker.cleanTempDir();
        }
        
        // 8. 修正补丁包中的版本信息（使用 PackageManager 获取的准确版本）
        if (result.isSuccess() && result.getPatchFile() != null && 
                (basePackageInfo != null || newPackageInfo != null)) {
            try {
                fixPatchVersionInfo(result.getPatchFile(), basePackageInfo, newPackageInfo);
            } catch (Exception e) {
                // 版本修正失败不影响整体结果，只记录日志
                android.util.Log.w("AndroidPatchGenerator", "Failed to fix version info: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * 使用 PackageManager 获取 APK 版本信息
     */
    @Nullable
    private PackageInfo getApkPackageInfo(File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 修正补丁包中的版本信息
     */
    private void fixPatchVersionInfo(File patchFile, PackageInfo baseInfo, PackageInfo newInfo) throws Exception {
        // 读取补丁包
        java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
        java.util.zip.ZipEntry patchJsonEntry = zipFile.getEntry("patch.json");
        
        if (patchJsonEntry == null) {
            zipFile.close();
            return;
        }
        
        // 读取 patch.json
        java.io.InputStream is = zipFile.getInputStream(patchJsonEntry);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        is.close();
        String jsonContent = baos.toString("UTF-8");
        zipFile.close();
        
        // 修改版本信息
        boolean modified = false;
        
        if (baseInfo != null) {
            String baseVersion = baseInfo.versionName != null ? baseInfo.versionName : "1.0";
            int baseVersionCode = baseInfo.versionCode;
            jsonContent = jsonContent.replaceFirst("\"baseVersion\"\\s*:\\s*\"[^\"]*\"", 
                    "\"baseVersion\": \"" + baseVersion + "\"");
            jsonContent = jsonContent.replaceFirst("\"baseVersionCode\"\\s*:\\s*\\d+", 
                    "\"baseVersionCode\": " + baseVersionCode);
            modified = true;
        }
        
        if (newInfo != null) {
            String newVersion = newInfo.versionName != null ? newInfo.versionName : "1.0";
            int newVersionCode = newInfo.versionCode;
            jsonContent = jsonContent.replaceFirst("\"targetVersion\"\\s*:\\s*\"[^\"]*\"", 
                    "\"targetVersion\": \"" + newVersion + "\"");
            jsonContent = jsonContent.replaceFirst("\"targetVersionCode\"\\s*:\\s*\\d+", 
                    "\"targetVersionCode\": " + newVersionCode);
            jsonContent = jsonContent.replaceFirst("\"patchVersion\"\\s*:\\s*\"[^\"]*\"", 
                    "\"patchVersion\": \"" + newVersion + "\"");
            modified = true;
        }
        
        if (!modified) {
            return;
        }
        
        // 重新打包补丁文件
        File tempFile = new File(patchFile.getParent(), patchFile.getName() + ".tmp");
        
        try {
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new java.io.FileInputStream(patchFile));
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.FileOutputStream(tempFile));
            
            try {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    java.util.zip.ZipEntry newEntry = new java.util.zip.ZipEntry(entry.getName());
                    zos.putNextEntry(newEntry);
                    
                    if ("patch.json".equals(entry.getName())) {
                        // 写入修改后的 patch.json
                        zos.write(jsonContent.getBytes("UTF-8"));
                    } else {
                        // 复制其他文件
                        while ((len = zis.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            } finally {
                zis.close();
                zos.close();
            }
            
            // 替换原文件
            if (!patchFile.delete()) {
                System.err.println("Failed to delete original patch file");
            }
            if (!tempFile.renameTo(patchFile)) {
                System.err.println("Failed to rename temp file to patch file");
                // 如果重命名失败，尝试复制（使用兼容 API 21 的方法）
                try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(patchFile)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to copy temp file: " + e.getMessage());
                }
            }
        } finally {
            // 确保删除临时文件（无论是否发生异常）
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 验证输入参数
     */
    private void validateInputs() throws PatchGenerator.PatchGeneratorException {
        if (baseApk == null) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Base APK is null", GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!baseApk.exists()) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Base APK not found: " + baseApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!baseApk.canRead()) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Cannot read base APK: " + baseApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
        
        if (newApk == null) {
            throw new PatchGenerator.PatchGeneratorException(
                    "New APK is null", GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!newApk.exists()) {
            throw new PatchGenerator.PatchGeneratorException(
                    "New APK not found: " + newApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!newApk.canRead()) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Cannot read new APK: " + newApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
        
        if (outputFile == null) {
            throw new PatchGenerator.PatchGeneratorException(
                    "Output file is null", GeneratorErrorCode.ERROR_FILE_WRITE_FAILED);
        }
        
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new PatchGenerator.PatchGeneratorException(
                        "Cannot create output directory: " + outputDir.getAbsolutePath(),
                        GeneratorErrorCode.ERROR_FILE_WRITE_FAILED);
            }
        }
    }
    
    /**
     * 选择引擎类型
     * 
     * AUTO 模式下优先使用 Native 引擎（如果可用）
     * 
     * Requirements: 9.8, 9.9
     */
    private EngineType selectEngine() {
        if (engineType == EngineType.NATIVE) {
            if (NativePatchEngine.isAvailable()) {
                return EngineType.NATIVE;
            } else {
                // Native 引擎不可用，回退到 Java
                return EngineType.JAVA;
            }
        } else if (engineType == EngineType.JAVA) {
            return EngineType.JAVA;
        } else {
            // AUTO 模式：优先 Native
            if (NativePatchEngine.isAvailable()) {
                return EngineType.NATIVE;
            } else {
                return EngineType.JAVA;
            }
        }
    }
    
    /**
     * 创建核心生成器回调
     */
    private GeneratorCallback createCoreCallback() {
        return new GeneratorCallback() {
            @Override
            public void onParseStart(String apkPath) {
                notifyParseStart(apkPath);
                notifyProgress(15, "Parsing APK...");
            }

            @Override
            public void onParseProgress(int current, int total) {
                notifyParseProgress(current, total);
                int percent = 15 + (int) ((current * 1.0 / total) * 15);
                notifyProgress(percent, "Parsing APK...");
            }

            @Override
            public void onCompareStart() {
                notifyCompareStart();
                notifyProgress(30, "Comparing differences...");
            }

            @Override
            public void onCompareProgress(int current, int total, String currentFile) {
                notifyCompareProgress(current, total, currentFile);
                int percent = 30 + (int) ((current * 1.0 / total) * 30);
                notifyProgress(percent, "Comparing: " + (currentFile != null ? currentFile : ""));
            }

            @Override
            public void onPackStart() {
                notifyPackStart();
                notifyProgress(60, "Packing patch...");
            }

            @Override
            public void onPackProgress(long current, long total) {
                notifyPackProgress(current, total);
                int percent = 60 + (int) ((current * 1.0 / total) * 25);
                notifyProgress(percent, "Packing patch...");
            }

            @Override
            public void onSignStart() {
                notifySignStart();
                notifyProgress(85, "Signing patch...");
            }

            @Override
            public void onComplete(PatchResult result) {
                notifyProgress(100, "Complete");
            }

            @Override
            public void onError(int errorCode, String message) {
                // Error will be handled by the outer try-catch
            }
        };
    }

    /**
     * 取消生成
     * 
     * Requirements: 9.7
     */
    public void cancel() {
        cancelled.set(true);
        if (coreGenerator != null) {
            coreGenerator.cancel();
        }
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
    }
    
    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 关闭生成器，释放资源
     */
    public void shutdown() {
        cancel();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        storageChecker.cleanTempDir();
    }
    
    /**
     * 获取存储检查器
     */
    @NonNull
    public StorageChecker getStorageChecker() {
        return storageChecker;
    }
    
    /**
     * 检查 Native 引擎是否可用
     */
    public static boolean isNativeEngineAvailable() {
        return NativePatchEngine.isAvailable();
    }
    
    // ==================== Callback Notification Methods ====================
    
    private void notifyStart() {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onStart());
        }
    }
    
    private void notifyParseStart(String apkPath) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onParseStart(apkPath));
        }
    }
    
    private void notifyParseProgress(int current, int total) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onParseProgress(current, total));
        }
    }
    
    private void notifyCompareStart() {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onCompareStart());
        }
    }
    
    private void notifyCompareProgress(int current, int total, String currentFile) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onCompareProgress(current, total, currentFile));
        }
    }
    
    private void notifyPackStart() {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onPackStart());
        }
    }
    
    private void notifyPackProgress(long current, long total) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onPackProgress(current, total));
        }
    }
    
    private void notifySignStart() {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onSignStart());
        }
    }
    
    private void notifyProgress(int percent, String stage) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onProgress(percent, stage));
        }
    }
    
    private void notifyComplete(PatchResult result) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onComplete(result));
        }
    }
    
    private void notifyError(int errorCode, String message) {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onError(errorCode, message));
        }
    }
    
    private void notifyCancelled() {
        if (callback != null) {
            runOnCallbackThread(() -> callback.onCancelled());
        }
    }
    
    private void runOnCallbackThread(Runnable runnable) {
        if (callbackOnMainThread) {
            mainHandler.post(runnable);
        } else {
            runnable.run();
        }
    }
    
    // ==================== Builder ====================
    
    /**
     * AndroidPatchGenerator 构建器
     */
    public static class Builder {
        private Context context;
        private File baseApk;
        private File newApk;
        private File outputFile;
        private SigningConfig signingConfig;
        private EngineType engineType = EngineType.AUTO;
        private PatchMode patchMode = PatchMode.FULL_DEX;
        private AndroidGeneratorCallback callback;
        private boolean checkStorage = true;
        private boolean callbackOnMainThread = true;
        
        /**
         * 创建构建器
         * 
         * @param context Android Context
         */
        public Builder(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }
        
        /**
         * 使用已安装应用作为基线
         * 
         * 从 PackageManager 获取已安装应用的 APK 路径
         * 
         * @param packageName 应用包名
         * @return this
         * @throws IllegalArgumentException 如果应用未安装
         * 
         * Requirements: 9.5
         */
        public Builder baseFromInstalled(@NonNull String packageName) {
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                this.baseApk = new File(appInfo.sourceDir);
                return this;
            } catch (PackageManager.NameNotFoundException e) {
                throw new IllegalArgumentException(
                        "Package not installed: " + packageName, e);
            }
        }
        
        /**
         * 设置基准 APK 文件
         * 
         * @param apk 基准 APK 文件
         * @return this
         * 
         * Requirements: 9.3
         */
        public Builder baseApk(@NonNull File apk) {
            this.baseApk = apk;
            return this;
        }
        
        /**
         * 设置新版本 APK 文件
         * 
         * @param apk 新版本 APK 文件
         * @return this
         * 
         * Requirements: 9.3
         */
        public Builder newApk(@NonNull File apk) {
            this.newApk = apk;
            return this;
        }
        
        /**
         * 设置输出文件
         * 
         * @param output 输出补丁文件
         * @return this
         */
        public Builder output(@NonNull File output) {
            this.outputFile = output;
            return this;
        }
        
        /**
         * 设置签名配置
         * 
         * @param config 签名配置
         * @return this
         */
        public Builder signingConfig(@Nullable SigningConfig config) {
            this.signingConfig = config;
            return this;
        }
        
        /**
         * 设置引擎类型
         * 
         * AUTO: 自动选择（优先 Native）
         * JAVA: 强制使用 Java 引擎
         * NATIVE: 强制使用 Native 引擎（不可用时回退到 Java）
         * 
         * @param type 引擎类型
         * @return this
         * 
         * Requirements: 9.8, 9.9
         */
        public Builder engineType(@NonNull EngineType type) {
            this.engineType = type;
            return this;
        }
        
        /**
         * 设置补丁模式
         * 
         * @param mode 补丁模式
         * @return this
         */
        public Builder patchMode(@NonNull PatchMode mode) {
            this.patchMode = mode;
            return this;
        }
        
        /**
         * 设置回调
         * 
         * @param callback 生成回调
         * @return this
         * 
         * Requirements: 9.6
         */
        public Builder callback(@Nullable AndroidGeneratorCallback callback) {
            this.callback = callback;
            return this;
        }
        
        /**
         * 设置是否检查存储空间
         * 
         * 默认为 true
         * 
         * @param check 是否检查
         * @return this
         * 
         * Requirements: 9.2
         */
        public Builder checkStorage(boolean check) {
            this.checkStorage = check;
            return this;
        }
        
        /**
         * 设置回调是否在主线程执行
         * 
         * 默认为 true，方便直接更新 UI
         * 
         * @param onMainThread 是否在主线程
         * @return this
         */
        public Builder callbackOnMainThread(boolean onMainThread) {
            this.callbackOnMainThread = onMainThread;
            return this;
        }
        
        /**
         * 构建 AndroidPatchGenerator
         * 
         * @return AndroidPatchGenerator 实例
         */
        @NonNull
        public AndroidPatchGenerator build() {
            if (context == null) {
                throw new IllegalStateException("Context is required");
            }
            if (baseApk == null) {
                throw new IllegalStateException("Base APK is required");
            }
            if (newApk == null) {
                throw new IllegalStateException("New APK is required");
            }
            if (outputFile == null) {
                // 使用默认输出路径
                StorageChecker checker = new StorageChecker(context);
                outputFile = new File(checker.getDefaultOutputDir(), 
                        "patch_" + System.currentTimeMillis() + ".patch");
            }
            return new AndroidPatchGenerator(this);
        }
    }
}
