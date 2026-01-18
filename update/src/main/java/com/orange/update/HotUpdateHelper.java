package com.orange.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 热更新辅助类 - 提供简单易用的热更新 API
 * 
 * 这是一个高层封装类，简化了补丁应用流程，提供友好的回调接口。
 * 
 * 功能：
 * - 应用本地补丁文件
 * - 加载已应用的补丁
 * - 清除补丁（回滚）
 * - 查询补丁状态
 * - 进度和结果回调
 * 
 * 使用示例：
 * <pre>
 * // 1. 创建实例
 * HotUpdateHelper helper = new HotUpdateHelper(context);
 * 
 * // 2. 应用补丁
 * helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
 *     {@literal @}Override
 *     public void onProgress(int percent, String message) {
 *         Log.d(TAG, "进度: " + percent + "% - " + message);
 *     }
 *     
 *     {@literal @}Override
 *     public void onSuccess(PatchResult result) {
 *         Log.i(TAG, "热更新成功！");
 *     }
 *     
 *     {@literal @}Override
 *     public void onError(String message) {
 *         Log.e(TAG, "热更新失败: " + message);
 *     }
 * });
 * 
 * // 3. 在 Application.attachBaseContext() 中加载补丁
 * helper.loadAppliedPatch();
 * </pre>
 */
public class HotUpdateHelper {
    
    private static final String TAG = "HotUpdateHelper";
    
    private final Context context;
    private final PatchStorage storage;
    private final PatchApplier applier;
    private final SecurityManager securityManager;
    private final PatchSigner patchSigner;  // 使用 apksig 进行签名验证
    private final ExecutorService executor;
    private final SharedPreferences securityPrefs;
    
    // 安全策略配置键
    private static final String PREFS_SECURITY = "security_policy";
    private static final String KEY_REQUIRE_SIGNATURE = "require_signature";
    private static final String KEY_REQUIRE_ENCRYPTION = "require_encryption";
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public HotUpdateHelper(Context context) {
        this.context = context.getApplicationContext();
        this.securityManager = new SecurityManager(this.context);
        this.patchSigner = new PatchSigner(this.context);  // 使用 apksig
        this.storage = new PatchStorage(this.context, this.securityManager);
        this.applier = new PatchApplier(this.context, storage);
        this.executor = Executors.newSingleThreadExecutor();
        this.securityPrefs = this.context.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE);
    }
    
    /**
     * 应用补丁（异步）
     * 
     * @param patchFile 补丁文件
     * @param callback 回调接口
     */
    public void applyPatch(File patchFile, Callback callback) {
        if (patchFile == null || !patchFile.exists()) {
            if (callback != null) {
                callback.onError("补丁文件不存在");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                // 通知开始
                if (callback != null) {
                    callback.onProgress(5, "准备应用补丁...");
                }
                
                // 1. 检查安全策略
                String securityError = checkSecurityPolicy(patchFile);
                if (securityError != null) {
                    if (callback != null) {
                        callback.onError(securityError);
                    }
                    return;
                }
                
                if (callback != null) {
                    callback.onProgress(10, "检查 ZIP 密码保护...");
                }
                
                // 2. 检查并处理 ZIP 密码加密
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                
                if (zipPasswordManager.isEncrypted(patchFile)) {
                    Log.d(TAG, "检测到 ZIP 密码加密");
                    
                    if (callback != null) {
                        callback.onProgress(15, "检查 ZIP 密码保护...");
                    }
                    
                    // 检查是否有密码提示文件（.zippwd）
                    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
                    boolean hasCustomPassword = zipPasswordFile.exists();
                    
                    if (hasCustomPassword) {
                        Log.d(TAG, "检测到自定义 ZIP 密码，需要用户输入");
                        
                        // 通知 UI 需要用户输入密码
                        if (callback != null) {
                            callback.onZipPasswordRequired(patchFile);
                        }
                        return; // 等待用户输入密码后调用 applyPatchWithZipPassword()
                    }
                    
                    // 没有自定义密码，直接保存加密文件到 applied 目录
                    // 应用启动时会自动使用派生密码解密
                    Log.d(TAG, "使用派生密码，补丁将以加密状态保存");
                }
                
                if (callback != null) {
                    callback.onProgress(20, "准备应用补丁...");
                }
                
                // 3. 继续应用补丁流程（ZIP 密码保护的补丁将以加密状态保存）
                applyPatchInternal(patchFile, patchFile, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 读取文件内容到字节数组
     */
    private byte[] readFileToBytes(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read file", e);
            return null;
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(File patchFile) {
        // 检查文件扩展名或内容
        String fileName = patchFile.getName().toLowerCase(java.util.Locale.ROOT);
        
        // 如果是 APK 或 ZIP 文件，可能包含资源
        if (fileName.endsWith(".apk") || fileName.endsWith(".zip")) {
            return true;
        }
        
        // 如果是纯 DEX 文件，不包含资源
        if (fileName.endsWith(".dex")) {
            return false;
        }
        
        // 检查文件魔数
        try {
            byte[] header = new byte[4];
            java.io.FileInputStream fis = new java.io.FileInputStream(patchFile);
            fis.read(header);
            fis.close();
            
            // ZIP/APK 魔数: PK (0x50 0x4B)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }
            
            // DEX 魔数: dex\n (0x64 0x65 0x78 0x0A)
            if (header[0] == 0x64 && header[1] == 0x65 && 
                header[2] == 0x78 && header[3] == 0x0A) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check patch file type", e);
        }
        
        return false;
    }
    
    /**
     * 应用补丁（同步）
     * 
     * @param patchFile 补丁文件
     * @return 是否应用成功
     */
    public boolean applyPatchSync(File patchFile) {
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        
        try {
            PatchInfo patchInfo = createPatchInfo(patchFile);
            return applier.apply(patchInfo);
        } catch (Exception e) {
            Log.e(TAG, "应用补丁失败", e);
            return false;
        }
    }
    
    /**
     * 加载已应用的补丁
     * 
     * 此方法应在 Application.attachBaseContext() 中调用
     */
    public void loadAppliedPatch() {
        applier.loadAppliedPatch();
    }
    
    /**
     * 清除补丁（回滚）
     * 
     * @return 是否清除成功
     */
    public boolean clearPatch() {
        String appliedPatchId = storage.getAppliedPatchId();
        if (appliedPatchId != null) {
            return storage.deletePatch(appliedPatchId);
        }
        return true;
    }
    
    /**
     * 检查是否有已应用的补丁
     * 
     * @return 是否有已应用的补丁
     */
    public boolean hasAppliedPatch() {
        return storage.getAppliedPatchId() != null;
    }
    
    /**
     * 获取已应用的补丁信息
     * 
     * @return 补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatchInfo() {
        return storage.getAppliedPatchInfo();
    }
    
    /**
     * 获取已应用的补丁 ID
     * 
     * @return 补丁 ID，如果没有返回 null
     */
    public String getAppliedPatchId() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getPatchId() : null;
    }
    
    /**
     * 获取补丁应用时间
     * 
     * @return 补丁应用时间戳（毫秒），如果没有返回 0
     */
    public long getPatchTime() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getCreateTime() : 0;
    }
    
    /**
     * 检查 DEX 是否已注入
     * 
     * @return 是否已注入 DEX
     */
    public boolean isDexInjected() {
        return hasAppliedPatch() && DexPatcher.isSupported();
    }
    
    /**
     * 获取补丁后的版本号
     * 
     * @return 补丁版本号，如果没有返回 null
     */
    public String getPatchedVersion() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getPatchVersion() : null;
    }
    
    /**
     * 获取补丁后的版本代码
     * 
     * @return 补丁版本代码，如果没有返回 null
     */
    public String getPatchedVersionCode() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        if (patchInfo != null && patchInfo.getTargetAppVersion() != null) {
            return patchInfo.getTargetAppVersion();
        }
        return null;
    }
    
    /**
     * 检查是否有已应用的补丁（别名方法，兼容旧API）
     * 
     * @return 是否有已应用的补丁
     */
    public boolean isPatchApplied() {
        return hasAppliedPatch();
    }
    
    /**
     * 获取显示版本
     * 
     * @param originalVersion 原始版本号
     * @return 显示版本（如果有补丁则显示补丁版本）
     */
    public String getDisplayVersion(String originalVersion) {
        if (hasAppliedPatch()) {
            PatchInfo patchInfo = storage.getAppliedPatchInfo();
            if (patchInfo != null && patchInfo.getPatchVersion() != null) {
                return patchInfo.getPatchVersion() + " (热更新)";
            }
        }
        return originalVersion;
    }
    
    /**
     * 获取兼容性信息
     * 
     * @return 兼容性信息描述
     */
    public String getCompatibilityInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("热更新支持: ").append(DexPatcher.isSupported() ? "✓" : "✗").append("\n");
        sb.append("兼容级别: ").append(DexPatcher.getCompatibilityLevel()).append("\n");
        sb.append("SO 热更新: ").append(SoPatcher.isSupported() ? "✓" : "✗").append("\n");
        sb.append("资源热更新: ").append(ResourcePatcher.isSupported() ? "✓" : "✗");
        return sb.toString();
    }
    
    /**
     * 检查当前设备是否支持热更新
     * 
     * @return 是否支持
     */
    public static boolean isSupported() {
        return DexPatcher.isSupported();
    }
    
    /**
     * 获取兼容性级别
     * 
     * @return 兼容性级别描述
     */
    public static String getCompatibilityLevel() {
        return DexPatcher.getCompatibilityLevel();
    }
    
    /**
     * 设置是否强制要求补丁签名
     * 
     * @param required 是否要求签名
     */
    public void setRequireSignature(boolean required) {
        securityPrefs.edit().putBoolean(KEY_REQUIRE_SIGNATURE, required).apply();
    }
    
    /**
     * 设置是否强制要求补丁加密
     * 
     * @param required 是否要求加密
     */
    public void setRequireEncryption(boolean required) {
        securityPrefs.edit().putBoolean(KEY_REQUIRE_ENCRYPTION, required).apply();
    }
    
    /**
     * 检查是否要求补丁签名
     * 
     * @return 是否要求签名
     */
    public boolean isRequireSignature() {
        return securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
    }
    
    /**
     * 检查是否要求补丁加密
     * 
     * @return 是否要求加密
     */
    public boolean isRequireEncryption() {
        return securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
    }
    
    /**
     * 获取 SecurityManager 实例
     * 
     * @return SecurityManager 实例
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
    
    /**
     * 检查安全策略
     * 
     * @param patchFile 补丁文件
     * @return 错误消息，如果通过返回 null
     */
    private String checkSecurityPolicy(File patchFile) {
        boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
        boolean requireEncryption = securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
        
        boolean isEncrypted = patchFile.getName().endsWith(".enc");
        boolean hasSignature = checkHasSignature(patchFile);
        
        Log.d(TAG, "安全策略 - 要求签名: " + requireSignature + ", 要求加密: " + requireEncryption);
        Log.d(TAG, "补丁状态 - 已加密: " + isEncrypted + ", 有签名: " + hasSignature);
        
        if (requireSignature && !hasSignature) {
            return "当前安全策略要求补丁必须签名！此补丁未签名，拒绝应用。";
        }
        
        if (requireEncryption && !isEncrypted) {
            return "当前安全策略要求补丁必须加密！此补丁未加密，拒绝应用。";
        }
        
        // APK 签名验证（如果补丁有签名）- 使用 apksig
        if (hasSignature) {
            Log.d(TAG, "检测到补丁签名，开始验证 APK 签名...");
            boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(patchFile);
            if (!signatureValid) {
                return "⚠️ APK 签名验证失败: " + patchSigner.getError();
            }
            Log.d(TAG, "✅ APK 签名验证通过");
        }
        
        return null;
    }
    
    /**
     * 检查补丁是否有签名
     * 
     * @param patchFile 补丁文件
     * @return 是否有签名
     */
    private boolean checkHasSignature(File patchFile) {
        // 方法1: 检查 zip 内部是否有 META-INF/ 签名文件（新方案）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否有 META-INF/ 签名文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    zipFile.close();
                    Log.d(TAG, "✓ 检测到 APK 签名文件: " + name);
                    return true;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.d(TAG, "检查 META-INF/ 签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查 zip 内部是否有 signature.sig 标记文件（向后兼容）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                Log.d(TAG, "✓ 检测到 zip 内部的签名标记文件");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "检查 zip 内部签名标记失败: " + e.getMessage());
        }
        
        // 方法3: 检查外部 .sig 文件（向后兼容）
        File signatureFile = new File(patchFile.getPath() + ".sig");
        if (signatureFile.exists()) {
            Log.d(TAG, "✓ 检测到外部签名文件");
            return true;
        }
        
        return false;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    /**
     * 从文件创建 PatchInfo
     */
    private PatchInfo createPatchInfo(File patchFile) {
        PatchInfo patchInfo = new PatchInfo();
        patchInfo.setPatchId("patch_" + System.currentTimeMillis());
        patchInfo.setPatchVersion("1.0");
        patchInfo.setDownloadUrl("file://" + patchFile.getAbsolutePath());
        patchInfo.setFileSize(patchFile.length());
        
        // 计算 MD5
        try {
            String md5 = Md5Utils.calculateMd5(patchFile);
            patchInfo.setMd5(md5);
        } catch (Exception e) {
            Log.w(TAG, "Failed to calculate MD5", e);
            patchInfo.setMd5("unknown");
        }
        
        patchInfo.setCreateTime(System.currentTimeMillis());
        
        return patchInfo;
    }
    
    /**
     * 回调接口
     */
    public interface Callback {
        /**
         * 进度回调
         * 
         * @param percent 进度百分比 (0-100)
         * @param message 进度消息
         */
        void onProgress(int percent, String message);
        
        /**
         * 成功回调
         * 
         * @param result 补丁结果
         */
        void onSuccess(PatchResult result);
        
        /**
         * 错误回调
         * 
         * @param message 错误消息
         */
        void onError(String message);
        
        /**
         * 需要 ZIP 密码回调
         * 
         * 当检测到补丁使用了自定义 ZIP 密码时调用此方法。
         * UI 应该弹出对话框让用户输入密码，然后调用 applyPatchWithZipPassword() 继续应用补丁。
         * 
         * @param patchFile 补丁文件
         */
        default void onZipPasswordRequired(File patchFile) {
            // 默认实现：不处理，直接报错
            onError("补丁需要 ZIP 密码，但未提供密码输入接口");
        }
    }
    
    /**
     * 补丁结果
     */
    public static class PatchResult {
        /** 是否成功 */
        public boolean success;
        
        /** 补丁 ID */
        public String patchId;
        
        /** 旧版本号 */
        public String oldVersion;
        
        /** 新版本号 */
        public String patchVersion;
        
        /** 新版本代码 */
        public String newVersionCode;
        
        /** 补丁大小（字节） */
        public long patchSize;
        
        /** DEX 是否已注入 */
        public boolean dexInjected;
        
        /** SO 库是否已加载 */
        public boolean soLoaded;
        
        /** 资源是否已加载 */
        public boolean resourcesLoaded;
        
        /** 是否需要重启才能看到资源更新 */
        public boolean needsRestart;
        
        @Override
        public String toString() {
            return "PatchResult{" +
                    "success=" + success +
                    ", patchId='" + patchId + '\'' +
                    ", oldVersion='" + oldVersion + '\'' +
                    ", patchVersion='" + patchVersion + '\'' +
                    ", newVersionCode='" + newVersionCode + '\'' +
                    ", patchSize=" + patchSize +
                    ", dexInjected=" + dexInjected +
                    ", soLoaded=" + soLoaded +
                    ", resourcesLoaded=" + resourcesLoaded +
                    ", needsRestart=" + needsRestart +
                    '}';
        }
    }
    
    /**
     * 应用补丁（使用自定义 ZIP 密码）
     * 
     * 当 onZipPasswordRequired() 被调用后，UI 应该获取用户输入的密码，
     * 然后调用此方法继续应用补丁。
     * 
     * @param patchFile 补丁文件
     * @param zipPassword 用户输入的 ZIP 密码
     * @param callback 回调接口
     */
    public void applyPatchWithZipPassword(File patchFile, String zipPassword, Callback callback) {
        if (patchFile == null || !patchFile.exists()) {
            if (callback != null) {
                callback.onError("补丁文件不存在");
            }
            return;
        }
        
        if (zipPassword == null || zipPassword.isEmpty()) {
            if (callback != null) {
                callback.onError("ZIP 密码不能为空");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                // 通知开始
                if (callback != null) {
                    callback.onProgress(5, "准备应用补丁...");
                }
                
                // 1. 检查安全策略
                String securityError = checkSecurityPolicy(patchFile);
                if (securityError != null) {
                    if (callback != null) {
                        callback.onError(securityError);
                    }
                    return;
                }
                
                if (callback != null) {
                    callback.onProgress(10, "验证 ZIP 密码...");
                }
                
                // 2. 验证 ZIP 密码
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);
                
                if (!passwordValid) {
                    if (callback != null) {
                        callback.onError("⚠️ ZIP 密码验证失败！密码错误或补丁已被篡改。");
                    }
                    return;
                }
                
                Log.d(TAG, "✓ ZIP 密码验证成功");
                
                // 3. 保存自定义密码到 SharedPreferences（用于应用启动时解密）
                android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("is_zip_password_protected", true)
                    .putString("custom_zip_password", zipPassword)
                    .apply();
                
                // 4. 继续应用补丁流程（保存加密文件到 applied 目录）
                applyPatchInternal(patchFile, patchFile, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 解密 ZIP 补丁
     * 
     * @param patchFile 补丁文件
     * @param zipPassword ZIP 密码
     * @param callback 回调接口
     * @return 解密后的补丁文件，如果失败返回 null
     */
    private File decryptZipPatch(File patchFile, String zipPassword, Callback callback) {
        try {
            ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
            
            // 验证密码
            boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);
            
            if (!passwordValid) {
                if (callback != null) {
                    callback.onError("⚠️ ZIP 密码验证失败！密码错误或补丁已被篡改。");
                }
                return null;
            }
            
            Log.d(TAG, "✓ ZIP 密码验证成功");
            
            if (callback != null) {
                callback.onProgress(18, "解密 ZIP 文件...");
            }
            
            // 解密 ZIP 到临时文件
            File tempDir = new File(context.getCacheDir(), "patch_decrypt_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            boolean extracted = zipPasswordManager.extractEncryptedZip(patchFile, tempDir, zipPassword);
            
            if (!extracted) {
                if (callback != null) {
                    callback.onError("ZIP 解密失败");
                }
                // 清理临时目录
                deleteDirectory(tempDir);
                return null;
            }
            
            // 重新打包为未加密的 ZIP（临时文件）
            File decryptedZip = new File(context.getCacheDir(), "patch_decrypted_" + System.currentTimeMillis() + ".zip");
            repackZip(tempDir, decryptedZip);
            
            // 清理临时目录
            deleteDirectory(tempDir);
            
            Log.d(TAG, "✓ ZIP 解密完成");
            return decryptedZip;
            
        } catch (Exception e) {
            Log.e(TAG, "解密 ZIP 失败", e);
            if (callback != null) {
                callback.onError("解密 ZIP 失败: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * 应用补丁的内部实现（在 ZIP 解密之后）
     * 
     * @param actualPatchFile 实际的补丁文件（可能是解密后的）
     * @param originalPatchFile 原始补丁文件
     * @param callback 回调接口
     */
    private void applyPatchInternal(File actualPatchFile, File originalPatchFile, Callback callback) {
        try {
            if (callback != null) {
                callback.onProgress(20, "准备应用补丁...");
            }
            
            // 判断原始补丁是否是 ZIP 密码保护的
            boolean isZipPasswordProtected = isZipPasswordProtected(originalPatchFile);
            
            // APK 签名验证（应用时再次验证）- 使用 apksig
            if (checkHasSignature(actualPatchFile)) {
                if (callback != null) {
                    callback.onProgress(22, "验证 APK 签名...");
                }
                
                boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(actualPatchFile);
                if (!signatureValid) {
                    if (callback != null) {
                        callback.onError("⚠️ APK 签名验证失败: " + patchSigner.getError());
                    }
                    return;
                }
                
                Log.d(TAG, "✅ APK 签名验证通过（应用时）");
            }
            
            // 3. 创建 PatchInfo
            PatchInfo patchInfo = createPatchInfo(actualPatchFile);
            
            if (callback != null) {
                callback.onProgress(25, "验证补丁文件...");
            }
            
            // 4. 读取补丁文件内容
            // 如果是 ZIP 密码保护的，保存原始加密文件；否则保存实际文件
            File fileToSave = isZipPasswordProtected ? originalPatchFile : actualPatchFile;
            byte[] patchData = readFileToBytes(fileToSave);
            if (patchData == null) {
                if (callback != null) {
                    callback.onError("读取补丁文件失败");
                }
                return;
            }
            
            // 5. 保存补丁文件到存储（ZIP 密码保护的补丁保持加密状态）
            boolean saved = storage.savePatchFile(patchInfo.getPatchId(), patchData);
            if (!saved) {
                if (callback != null) {
                    callback.onError("保存补丁文件失败");
                }
                return;
            }
            
            // 保存 ZIP 密码标记（如果是 ZIP 密码保护的）
            if (isZipPasswordProtected) {
                // 保存一个标记，表示这个补丁是 ZIP 密码保护的
                android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("is_zip_password_protected", true).apply();
                Log.d(TAG, "✓ 补丁已保存为加密状态到 applied 目录");
            }
            
            if (callback != null) {
                callback.onProgress(40, "应用补丁...");
            }
            
            // 6. 应用补丁（PatchApplier 会自动处理解密和资源合并）
            // 检查是否包含资源，如果包含则显示资源合并进度
            boolean hasResources = hasResourcePatch(actualPatchFile);
            if (hasResources && callback != null) {
                callback.onProgress(50, "合并资源文件...");
            }
            
            boolean success = applier.apply(patchInfo);
            
            if (success) {
                if (hasResources && callback != null) {
                    callback.onProgress(80, "资源合并完成");
                }
                
                // 获取补丁信息
                PatchInfo appliedPatch = storage.getAppliedPatchInfo();
                
                // 创建结果
                PatchResult result = new PatchResult();
                result.success = true;
                result.patchId = appliedPatch != null ? appliedPatch.getPatchId() : null;
                result.patchVersion = appliedPatch != null ? appliedPatch.getPatchVersion() : null;
                result.patchSize = originalPatchFile.length();
                
                // 检查补丁内容类型
                result.dexInjected = DexPatcher.isSupported(); // DEX 热更新是否支持
                result.soLoaded = false; // SO 库加载状态（暂不支持检测）
                result.resourcesLoaded = hasResourcePatch(actualPatchFile); // 检查是否包含资源
                result.needsRestart = result.resourcesLoaded; // 资源更新需要重启
                
                if (callback != null) {
                    callback.onProgress(100, "热更新完成！");
                    callback.onSuccess(result);
                }
            } else {
                if (callback != null) {
                    callback.onError("补丁应用失败");
                }
            }
            
            // 清理临时解密文件
            if (actualPatchFile != originalPatchFile && actualPatchFile.exists()) {
                actualPatchFile.delete();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "应用补丁失败", e);
            if (callback != null) {
                callback.onError("应用补丁失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 检查补丁文件是否是 ZIP 密码保护的
     * 通过检查是否存在 .zippwd 标记文件来判断
     */
    private boolean isZipPasswordProtected(File patchFile) {
        // 检查是否有 .zippwd 标记文件
        File zipPwdFile = new File(patchFile.getPath() + ".zippwd");
        if (zipPwdFile.exists()) {
            return true;
        }
        
        // 或者尝试用 zip4j 检查是否加密
        try {
            net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile);
            return zipFile.isEncrypted();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 重新打包目录为 ZIP 文件（不加密，不压缩）
     */
    private void repackZip(File sourceDir, File destZip) throws Exception {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(destZip);
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setCompressionMethod(net.lingala.zip4j.model.enums.CompressionMethod.STORE);
        
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    zipFile.addFolder(file, params);
                } else {
                    zipFile.addFile(file, params);
                }
            }
        }
    }
}

