package com.orange.update;

import java.io.File;

/**
 * 下载过程的回调接口。
 * 用于接收文件下载的进度、成功和错误通知。
 */
public interface DownloadCallback {
    
    /**
     * 下载进度回调
     * @param current 当前已下载字节数
     * @param total 总字节数
     */
    void onProgress(long current, long total);
    
    /**
     * 下载成功回调
     * @param file 下载完成的文件
     */
    void onSuccess(File file);
    
    /**
     * 下载错误回调
     * @param errorCode 错误码，参见 {@link UpdateErrorCode}
     * @param message 错误描述信息
     */
    void onError(int errorCode, String message);
}
