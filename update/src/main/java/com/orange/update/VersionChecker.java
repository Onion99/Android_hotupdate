package com.orange.update;

import android.util.Log;

/**
 * 版本检查器，负责与服务器通信检查是否有新补丁。
 * 集成 ServerApi 和 VersionUtils 实现版本检查逻辑。
 */
public class VersionChecker {
    
    private static final String TAG = "VersionChecker";
    
    private final UpdateConfig config;
    private final ServerApi serverApi;
    
    /**
     * 构造函数
     * @param config SDK 配置
     */
    public VersionChecker(UpdateConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        this.config = config;
        this.serverApi = new ServerApi(config);
    }
    
    /**
     * 构造函数（用于测试，允许注入 ServerApi）
     * @param config SDK 配置
     * @param serverApi 服务器接口实例
     */
    VersionChecker(UpdateConfig config, ServerApi serverApi) {
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        if (serverApi == null) {
            throw new IllegalArgumentException("ServerApi cannot be null");
        }
        this.config = config;
        this.serverApi = serverApi;
    }
    
    /**
     * 检查更新
     * @param currentPatchVersion 当前补丁版本，可为 null 表示没有已应用的补丁
     * @return 如果有更新返回 PatchInfo，否则返回 null
     * @throws ServerApi.UpdateException 如果请求失败
     */
    public PatchInfo checkUpdate(String currentPatchVersion) throws ServerApi.UpdateException {
        Log.d(TAG, "Checking for updates, current patch version: " + currentPatchVersion);
        
        // 调用服务器接口检查更新
        PatchInfo serverPatchInfo = serverApi.checkUpdate(
                config.getAppKey(),
                config.getAppVersion(),
                currentPatchVersion
        );
        
        // 如果服务器没有返回补丁信息，说明没有更新
        if (serverPatchInfo == null) {
            Log.d(TAG, "No update available from server");
            return null;
        }
        
        // 验证补丁目标版本是否匹配当前应用版本
        String targetAppVersion = serverPatchInfo.getTargetAppVersion();
        if (targetAppVersion != null && !targetAppVersion.isEmpty()) {
            if (!isAppVersionCompatible(targetAppVersion)) {
                Log.w(TAG, "Patch target version " + targetAppVersion + 
                        " does not match app version " + config.getAppVersion());
                return null;
            }
        }
        
        // 比较补丁版本，确认是否需要更新
        if (currentPatchVersion != null && !currentPatchVersion.isEmpty()) {
            if (!isNewerVersion(serverPatchInfo.getPatchVersion(), currentPatchVersion)) {
                Log.d(TAG, "Server patch version " + serverPatchInfo.getPatchVersion() + 
                        " is not newer than current " + currentPatchVersion);
                return null;
            }
        }
        
        Log.d(TAG, "Update available: " + serverPatchInfo.getPatchVersion());
        return serverPatchInfo;
    }
    
    /**
     * 判断服务器版本是否比本地版本新
     * @param serverVersion 服务器版本号
     * @param localVersion 本地版本号
     * @return 如果服务器版本更新返回 true，否则返回 false
     */
    public boolean isNewerVersion(String serverVersion, String localVersion) {
        if (serverVersion == null || serverVersion.isEmpty()) {
            return false;
        }
        if (localVersion == null || localVersion.isEmpty()) {
            return true;
        }
        
        try {
            return VersionUtils.isNewerVersion(serverVersion, localVersion);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid version format, serverVersion=" + serverVersion + 
                    ", localVersion=" + localVersion, e);
            // 如果版本格式无效，尝试字符串比较
            return !serverVersion.equals(localVersion);
        }
    }
    
    /**
     * 检查补丁目标版本是否与当前应用版本兼容
     * @param targetAppVersion 补丁目标应用版本
     * @return 如果兼容返回 true
     */
    private boolean isAppVersionCompatible(String targetAppVersion) {
        String currentAppVersion = config.getAppVersion();
        
        // 精确匹配
        if (targetAppVersion.equals(currentAppVersion)) {
            return true;
        }
        
        // 如果目标版本包含通配符（如 "1.0.*"），进行前缀匹配
        if (targetAppVersion.endsWith(".*")) {
            String prefix = targetAppVersion.substring(0, targetAppVersion.length() - 2);
            return currentAppVersion.startsWith(prefix);
        }
        
        return false;
    }
}
