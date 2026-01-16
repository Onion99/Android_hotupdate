package com.orange.update;

/**
 * UpdateCallback 的简单实现基类，所有方法都是空实现。
 * 继承此类可以只重写需要的回调方法，而不必实现所有方法。
 */
public class SimpleUpdateCallback implements UpdateCallback {
    
    @Override
    public void onCheckStart() {
        // 空实现
    }
    
    @Override
    public void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
        // 空实现
    }
    
    @Override
    public void onDownloadProgress(long current, long total) {
        // 空实现
    }
    
    @Override
    public void onDownloadComplete(PatchInfo patchInfo) {
        // 空实现
    }
    
    @Override
    public void onApplyStart() {
        // 空实现
    }
    
    @Override
    public void onApplyComplete(boolean success) {
        // 空实现
    }
    
    @Override
    public void onUpdateSuccess() {
        // 空实现
    }
    
    @Override
    public void onError(int errorCode, String message) {
        // 空实现
    }
}
