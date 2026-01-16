package com.orange.update;

/**
 * 更新过程的回调接口，通知应用各阶段状态。
 * 实现此接口以接收更新检查、下载、应用等过程的回调通知。
 */
public interface UpdateCallback {
    
    /**
     * 检查更新开始时调用
     */
    void onCheckStart();
    
    /**
     * 检查更新完成时调用
     * @param hasUpdate 是否有可用更新
     * @param patchInfo 补丁信息，无更新时为 null
     */
    void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo);
    
    /**
     * 下载进度回调
     * @param current 当前已下载字节数
     * @param total 总字节数
     */
    void onDownloadProgress(long current, long total);
    
    /**
     * 下载完成时调用
     * @param patchInfo 已下载的补丁信息
     */
    void onDownloadComplete(PatchInfo patchInfo);
    
    /**
     * 应用补丁开始时调用
     */
    void onApplyStart();
    
    /**
     * 应用补丁完成时调用
     * @param success 是否应用成功
     */
    void onApplyComplete(boolean success);
    
    /**
     * 更新成功时调用（整个更新流程完成）
     */
    void onUpdateSuccess();
    
    /**
     * 错误回调
     * @param errorCode 错误码，参见 {@link UpdateErrorCode}
     * @param message 错误描述信息
     */
    void onError(int errorCode, String message);
}
