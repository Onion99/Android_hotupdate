package com.orange.patchnative;

/**
 * Native 进度回调接口
 * 
 * Requirements: 10.8
 */
public interface NativeProgressCallback {
    
    /**
     * 进度更新回调
     * 
     * @param current 当前进度值
     * @param total   总进度值
     */
    void onProgress(long current, long total);
}
