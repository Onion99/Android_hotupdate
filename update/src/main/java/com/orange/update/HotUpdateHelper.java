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
                    callback.onProgress(10, "准备应用补丁...");
                }
                
                // 2. 创建 PatchInfo
                PatchInfo patchInfo = createPatchInfo(patchFile);
                
                if (callback != null) {
                    callback.onProgress(20, "验证补丁文件...");
                }
                
                // 3. 应用补丁（PatchApplier 会自动处理解密）
                boolean success = applier.apply(patchInfo);
                
                if (success) {
                    // 获取补丁信息
                    PatchInfo appliedPatch = storage.getAppliedPatchInfo();
                    
                    // 创建结果
                    PatchResult result = new PatchResult();
                    result.success = true;
                    result.patchId = appliedPatch != null ? appliedPatch.getPatchId() : null;
                    result.patchVersion = appliedPatch != null ? appliedPatch.getPatchVersion() : null;
                    result.patchSize = patchFile.length();
                    result.needsRestart = true; // 资源更新需要重启
                    
                    if (callback != null) {
                        callback.onProgress(100, "热更新完成！");
                        callback.onSuccess(result);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("补丁应用失败");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
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
        
        return null;
    }
    
    /**
     * 检查补丁是否有签名
     * 
     * @param patchFile 补丁文件
     * @return 是否有签名
     */
    private boolean checkHasSignature(File patchFile) {
        // 方法1: 检查 zip 内部是否有 signature.sig
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                Log.d(TAG, "✓ 检测到 zip 内部的签名文件");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "检查 zip 内部签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查外部 .sig 文件（向后兼容）
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
}
