package com.orange.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 真正的热更新实现类
 * 
 * 功能：
 * 1. 解析补丁包（ZIP 格式）
 * 2. 提取 DEX 文件
 * 3. 使用 DexPatcher 注入到 ClassLoader
 * 4. 使用 ResourcePatcher 加载资源补丁
 * 
 * 补丁包结构：
 * patch.zip
 * ├── patch.json          # 补丁元数据
 * ├── classes.dex         # 补丁 DEX（可选）
 * ├── classes2.dex        # 多 DEX 支持（可选）
 * ├── resources.arsc      # 资源补丁（可选）
 * └── *.bsdiff            # 二进制差分文件
 */
public class RealHotUpdate {
    
    private static final String TAG = "RealHotUpdate";
    private static final String PREFS_NAME = "real_hot_update_prefs";
    private static final String KEY_PATCHED_VERSION = "patched_version";
    private static final String KEY_PATCHED_VERSION_CODE = "patched_version_code";
    private static final String KEY_PATCH_APPLIED = "patch_applied";
    private static final String KEY_PATCH_TIME = "patch_time";
    private static final String KEY_PATCH_FILE = "patch_file";
    private static final String KEY_DEX_INJECTED = "dex_injected";
    
    private final Context context;
    private final SharedPreferences prefs;
    private final File patchDir;
    private final File dexDir;
    
    public RealHotUpdate(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.patchDir = new File(context.getFilesDir(), "hotupdate/patches");
        this.dexDir = new File(context.getFilesDir(), "hotupdate/dex");
        
        // 确保目录存在
        if (!patchDir.exists()) patchDir.mkdirs();
        if (!dexDir.exists()) dexDir.mkdirs();
    }

    /**
     * 应用补丁 - 真正的热更新
     * 
     * @param patchFile 补丁文件（ZIP 格式）
     * @param callback 回调
     */
    public void applyPatch(File patchFile, ApplyCallback callback) {
        if (patchFile == null || !patchFile.exists()) {
            callback.onError("补丁文件不存在");
            return;
        }
        
        new Thread(() -> {
            try {
                callback.onProgress(5, "读取补丁文件...");
                
                // 1. 解析补丁包中的 patch.json
                PatchMetadata metadata = extractPatchMetadata(patchFile);
                if (metadata == null) {
                    callback.onError("无法解析补丁元数据");
                    return;
                }
                
                callback.onProgress(15, "解析补丁信息...");
                Log.d(TAG, "Patch metadata: " + metadata);
                
                // 2. 检查兼容性
                if (!DexPatcher.isSupported()) {
                    callback.onError("当前设备不支持热更新: " + DexPatcher.getCompatibilityLevel());
                    return;
                }
                
                callback.onProgress(25, "提取 DEX 文件...");
                
                // 3. 提取 DEX 文件
                File extractedDex = extractDexFromPatch(patchFile);
                
                if (extractedDex != null && extractedDex.exists()) {
                    callback.onProgress(50, "注入 DEX 补丁...");
                    
                    // 4. 使用 DexPatcher 注入 DEX
                    try {
                        DexPatcher.injectPatchDex(context, extractedDex.getAbsolutePath());
                        Log.i(TAG, "DEX patch injected successfully");
                        
                        prefs.edit().putBoolean(KEY_DEX_INJECTED, true).apply();
                    } catch (DexPatcher.PatchException e) {
                        Log.e(TAG, "Failed to inject DEX patch", e);
                        callback.onError("DEX 注入失败: " + e.getMessage());
                        return;
                    }
                } else {
                    Log.w(TAG, "No DEX file found in patch, skipping DEX injection");
                }
                
                callback.onProgress(60, "检查 SO 库补丁...");
                
                // 5. SO 库热更新
                boolean soLoaded = false;
                try {
                    SoPatcher.loadPatchLibraries(context, patchFile);
                    Log.i(TAG, "SO libraries loaded successfully");
                    soLoaded = true;
                } catch (SoPatcher.PatchSoException e) {
                    Log.w(TAG, "Failed to load SO libraries", e);
                    // SO 加载失败不影响 DEX 和资源更新
                }
                
                callback.onProgress(70, "检查资源补丁...");
                
                // 6. 资源热更新 - 使用 ResourceMerger 合并原始 APK 和补丁资源
                File resourcePatch = extractResourcesFromPatch(patchFile);
                boolean resourcesLoaded = false;
                if (resourcePatch != null && resourcePatch.exists()) {
                    callback.onProgress(75, "合并资源文件...");
                    
                    // 创建合并后的资源文件
                    File mergedResourceDir = new File(context.getFilesDir(), "hotupdate/merged");
                    if (!mergedResourceDir.exists()) mergedResourceDir.mkdirs();
                    File mergedResourceFile = new File(mergedResourceDir, "merged_resources.apk");
                    
                    // 合并原始 APK 和补丁资源
                    boolean mergeSuccess = ResourceMerger.mergeResources(context, patchFile, mergedResourceFile);
                    
                    if (mergeSuccess && mergedResourceFile.exists()) {
                        callback.onProgress(80, "加载合并后的资源...");
                        try {
                            ResourcePatcher.loadPatchResources(context, mergedResourceFile.getAbsolutePath());
                            Log.i(TAG, "Merged resource patch loaded successfully");
                            resourcesLoaded = true;
                            
                            // 保存合并资源文件路径，供下次启动使用
                            prefs.edit().putString("merged_resource_file", mergedResourceFile.getAbsolutePath()).apply();
                        } catch (ResourcePatcher.PatchResourceException e) {
                            Log.w(TAG, "Failed to load merged resource patch", e);
                            // 资源加载失败不影响 DEX 更新
                        }
                    } else {
                        Log.w(TAG, "Failed to merge resources, skipping resource hot update");
                    }
                }
                
                callback.onProgress(85, "保存补丁状态...");
                
                // 7. 保存补丁信息
                prefs.edit()
                    .putString(KEY_PATCHED_VERSION, metadata.newVersion)
                    .putString(KEY_PATCHED_VERSION_CODE, metadata.newVersionCode)
                    .putBoolean(KEY_PATCH_APPLIED, true)
                    .putLong(KEY_PATCH_TIME, System.currentTimeMillis())
                    .putString(KEY_PATCH_FILE, patchFile.getAbsolutePath())
                    .apply();
                
                callback.onProgress(100, "热更新完成!");
                
                // 8. 返回结果
                PatchResult result = new PatchResult();
                result.success = true;
                result.oldVersion = metadata.baseVersion;
                result.newVersion = metadata.newVersion;
                result.newVersionCode = metadata.newVersionCode;
                result.patchSize = patchFile.length();
                result.dexInjected = extractedDex != null;
                result.soLoaded = soLoaded;
                result.resourcesLoaded = resourcesLoaded;
                result.needsRestart = resourcePatch != null; // 资源更新需要重启
                
                callback.onSuccess(result);
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                callback.onError("应用补丁失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 从补丁包提取 patch.json 元数据
     */
    private PatchMetadata extractPatchMetadata(File patchFile) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(patchFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("patch.json")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    String json = baos.toString("UTF-8");
                    return PatchMetadata.fromJson(json);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析补丁元数据失败", e);
        }
        return null;
    }
    
    /**
     * 从补丁包提取 DEX 文件
     */
    private File extractDexFromPatch(File patchFile) {
        File outputDex = new File(dexDir, "patch_" + System.currentTimeMillis() + ".dex");
        
        // 使用 ZipFile 而不是 ZipInputStream，避免 STORED 模式的兼容性问题
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // 查找 DEX 文件
                if (name.endsWith(".dex") || name.equals("classes.dex")) {
                    Log.d(TAG, "Found DEX file: " + name);
                    
                    try (java.io.InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(outputDex)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    
                    Log.d(TAG, "Extracted DEX to: " + outputDex.getAbsolutePath());
                    return outputDex;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "提取 DEX 文件失败", e);
        }
        
        return null;
    }
    
    /**
     * 从补丁包提取资源文件
     */
    private File extractResourcesFromPatch(File patchFile) {
        // 检查补丁包是否包含资源或 assets
        // 如果补丁包本身是 APK 格式或包含 resources.arsc，可以直接使用
        // 使用 ZipFile 而不是 ZipInputStream，避免 STORED 模式的兼容性问题
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.getName().equals("resources.arsc") || 
                    entry.getName().startsWith("res/") ||
                    entry.getName().startsWith("assets/")) {
                    // 补丁包包含资源或 assets，返回补丁包本身
                    Log.d(TAG, "Found resources/assets in patch: " + entry.getName());
                    return patchFile;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查资源失败", e);
        }
        return null;
    }

    /**
     * 应用启动时加载已应用的补丁
     * 注意：主要的补丁加载已在 PatchApplication.attachBaseContext() 中完成
     * 这个方法现在只用于检查补丁状态
     */
    public void loadAppliedPatch() {
        if (!isPatchApplied()) {
            Log.d(TAG, "No patch applied");
            return;
        }
        
        Log.d(TAG, "Patch is applied, version: " + getPatchedVersion());
        // 补丁已在 PatchApplication.attachBaseContext() 中加载
    }
    
    /**
     * 检查是否已应用补丁
     */
    public boolean isPatchApplied() {
        return prefs.getBoolean(KEY_PATCH_APPLIED, false);
    }
    
    /**
     * 检查 DEX 是否已注入
     */
    public boolean isDexInjected() {
        return prefs.getBoolean(KEY_DEX_INJECTED, false);
    }
    
    /**
     * 获取补丁后的版本
     */
    public String getPatchedVersion() {
        return prefs.getString(KEY_PATCHED_VERSION, null);
    }
    
    /**
     * 获取补丁后的版本号
     */
    public String getPatchedVersionCode() {
        return prefs.getString(KEY_PATCHED_VERSION_CODE, null);
    }
    
    /**
     * 获取补丁应用时间
     */
    public long getPatchTime() {
        return prefs.getLong(KEY_PATCH_TIME, 0);
    }
    
    /**
     * 清除补丁（回滚）
     */
    public void clearPatch() {
        prefs.edit()
            .remove(KEY_PATCHED_VERSION)
            .remove(KEY_PATCHED_VERSION_CODE)
            .remove(KEY_PATCH_APPLIED)
            .remove(KEY_PATCH_TIME)
            .remove(KEY_PATCH_FILE)
            .remove(KEY_DEX_INJECTED)
            .apply();
        
        // 清理 DEX 目录
        if (dexDir.exists()) {
            File[] files = dexDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        
        // 清理合并的资源目录
        File mergedDir = new File(context.getFilesDir(), "hotupdate/merged");
        if (mergedDir.exists()) {
            File[] files = mergedDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        
        // 清理补丁目录
        if (patchDir.exists()) {
            File[] files = patchDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        
        Log.i(TAG, "Patch cleared");
    }
    
    /**
     * 获取显示版本
     */
    public String getDisplayVersion(String originalVersion) {
        if (isPatchApplied()) {
            String patchedVersion = getPatchedVersion();
            if (patchedVersion != null) {
                return patchedVersion + " (热更新)";
            }
        }
        return originalVersion;
    }
    
    /**
     * 获取兼容性信息
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
     * 补丁元数据
     */
    public static class PatchMetadata {
        public String baseVersion;
        public String baseVersionCode;
        public String newVersion;
        public String newVersionCode;
        public long createTime;
        public String description;
        
        public static PatchMetadata fromJson(String json) {
            PatchMetadata metadata = new PatchMetadata();
            
            // 简单的 JSON 解析
            metadata.baseVersion = extractJsonValue(json, "baseVersion");
            metadata.baseVersionCode = extractJsonValue(json, "baseVersionCode");
            metadata.newVersion = extractJsonValue(json, "newVersion");
            metadata.newVersionCode = extractJsonValue(json, "newVersionCode");
            metadata.description = extractJsonValue(json, "description");
            
            // 尝试从其他字段获取版本信息
            if (metadata.newVersion == null || metadata.newVersion.isEmpty()) {
                metadata.newVersion = extractJsonValue(json, "targetVersion");
            }
            if (metadata.newVersion == null || metadata.newVersion.isEmpty()) {
                metadata.newVersion = extractJsonValue(json, "patchVersion");
            }
            if (metadata.newVersionCode == null || metadata.newVersionCode.isEmpty()) {
                metadata.newVersionCode = extractJsonValue(json, "targetVersionCode");
            }
            
            String createTimeStr = extractJsonValue(json, "createTime");
            if (createTimeStr != null) {
                try {
                    metadata.createTime = Long.parseLong(createTimeStr);
                } catch (NumberFormatException e) {
                    metadata.createTime = System.currentTimeMillis();
                }
            }
            
            // 设置默认值
            if (metadata.newVersion == null || metadata.newVersion.isEmpty()) {
                metadata.newVersion = "1.2";
            }
            if (metadata.newVersionCode == null || metadata.newVersionCode.isEmpty()) {
                metadata.newVersionCode = "3";
            }
            
            return metadata;
        }
        
        private static String extractJsonValue(String json, String key) {
            try {
                String searchKey = "\"" + key + "\"";
                int keyIndex = json.indexOf(searchKey);
                if (keyIndex == -1) return null;
                
                int colonIndex = json.indexOf(":", keyIndex);
                if (colonIndex == -1) return null;
                
                int valueStart = json.indexOf("\"", colonIndex);
                if (valueStart == -1) {
                    // 可能是数字
                    int numStart = colonIndex + 1;
                    while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
                        numStart++;
                    }
                    int numEnd = numStart;
                    while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '.')) {
                        numEnd++;
                    }
                    if (numEnd > numStart) {
                        return json.substring(numStart, numEnd);
                    }
                    return null;
                }
                
                int valueEnd = json.indexOf("\"", valueStart + 1);
                if (valueEnd == -1) return null;
                
                return json.substring(valueStart + 1, valueEnd);
            } catch (Exception e) {
                return null;
            }
        }
        
        @Override
        public String toString() {
            return "PatchMetadata{" +
                    "baseVersion='" + baseVersion + '\'' +
                    ", newVersion='" + newVersion + '\'' +
                    ", newVersionCode='" + newVersionCode + '\'' +
                    '}';
        }
    }
    
    /**
     * 回调接口
     */
    public interface ApplyCallback {
        void onProgress(int percent, String message);
        void onSuccess(PatchResult result);
        void onError(String message);
    }
    
    /**
     * 补丁结果
     */
    public static class PatchResult {
        public boolean success;
        public String oldVersion;
        public String newVersion;
        public String newVersionCode;
        public long patchSize;
        public boolean dexInjected;
        public boolean soLoaded;
        public boolean resourcesLoaded;
        public boolean needsRestart; // 是否需要重启才能看到资源更新
    }
}
