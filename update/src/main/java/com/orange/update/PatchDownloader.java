package com.orange.update;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 补丁下载器，负责从服务器下载补丁文件。
 * 支持断点续传和下载进度回调。
 */
public class PatchDownloader {
    
    private static final String TAG = "PatchDownloader";
    
    // 缓冲区大小
    private static final int BUFFER_SIZE = 8192;
    
    // 进度回调间隔（字节数）
    private static final int PROGRESS_CALLBACK_INTERVAL = 32768; // 32KB
    
    private final UpdateConfig config;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private HttpURLConnection currentConnection;
    
    /**
     * 构造函数
     * @param config SDK 配置
     */
    public PatchDownloader(UpdateConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        this.config = config;
    }
    
    /**
     * 下载文件，支持断点续传
     * @param downloadUrl 下载地址
     * @param targetFile 目标文件
     * @param callback 下载回调
     */
    public void download(String downloadUrl, File targetFile, DownloadCallback callback) {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED, "Download URL is empty");
            return;
        }
        
        if (targetFile == null) {
            notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED, "Target file is null");
            return;
        }
        
        cancelled.set(false);
        
        // 确保父目录存在
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                notifyError(callback, UpdateErrorCode.ERROR_FILE_WRITE_FAILED, 
                        "Failed to create directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        HttpURLConnection connection = null;
        InputStream inputStream = null;
        RandomAccessFile outputFile = null;
        
        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            currentConnection = connection;
            
            // 配置连接
            connection.setConnectTimeout(config.getConnectTimeout());
            connection.setReadTimeout(config.getReadTimeout());
            connection.setRequestMethod("GET");
            
            // 检查是否需要断点续传
            long downloadedBytes = 0;
            if (targetFile.exists()) {
                downloadedBytes = targetFile.length();
                // 设置 Range 请求头实现断点续传
                connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
                Log.d(TAG, "Resuming download from byte: " + downloadedBytes);
            }
            
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            
            // 检查响应码
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 服务器不支持断点续传，需要重新下载
                downloadedBytes = 0;
                if (targetFile.exists()) {
                    targetFile.delete();
                }
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 服务器支持断点续传，继续下载
                Log.d(TAG, "Server supports resume, continuing from: " + downloadedBytes);
            } else if (responseCode == 416) { // HTTP 416 Range Not Satisfiable
                // 文件已完整下载
                Log.d(TAG, "File already fully downloaded");
                notifySuccess(callback, targetFile);
                return;
            } else {
                notifyError(callback, UpdateErrorCode.ERROR_SERVER_ERROR,
                        "Server returned error code: " + responseCode);
                return;
            }
            
            // 获取文件总大小 (使用 getContentLength() 兼容 API 21+)
            long contentLength = connection.getContentLength();
            long totalSize = contentLength + downloadedBytes;
            
            if (contentLength <= 0 && downloadedBytes == 0) {
                notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED,
                        "Invalid content length");
                return;
            }
            
            inputStream = connection.getInputStream();
            
            // 使用 RandomAccessFile 支持断点续传
            outputFile = new RandomAccessFile(targetFile, "rw");
            outputFile.seek(downloadedBytes);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long currentBytes = downloadedBytes;
            long lastProgressCallback = currentBytes;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 检查是否取消
                if (cancelled.get()) {
                    Log.d(TAG, "Download cancelled");
                    notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_CANCELLED, 
                            "Download cancelled");
                    return;
                }
                
                outputFile.write(buffer, 0, bytesRead);
                currentBytes += bytesRead;
                
                // 定期回调进度
                if (currentBytes - lastProgressCallback >= PROGRESS_CALLBACK_INTERVAL) {
                    notifyProgress(callback, currentBytes, totalSize);
                    lastProgressCallback = currentBytes;
                }
            }
            
            // 最终进度回调
            notifyProgress(callback, currentBytes, totalSize);
            
            Log.d(TAG, "Download completed: " + targetFile.getAbsolutePath());
            notifySuccess(callback, targetFile);
            
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Download timeout", e);
            notifyError(callback, UpdateErrorCode.ERROR_TIMEOUT, "Download timeout");
        } catch (IOException e) {
            Log.e(TAG, "Download failed", e);
            if (cancelled.get()) {
                notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_CANCELLED, 
                        "Download cancelled");
            } else {
                notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED, 
                        "Download failed: " + e.getMessage());
            }
        } finally {
            closeQuietly(inputStream);
            closeQuietly(outputFile);
            if (connection != null) {
                connection.disconnect();
            }
            currentConnection = null;
        }
    }

    /**
     * 取消当前下载
     */
    public void cancel() {
        cancelled.set(true);
        HttpURLConnection conn = currentConnection;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Error disconnecting", e);
            }
        }
    }
    
    /**
     * 检查下载是否已取消
     * @return true 如果已取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    /**
     * 通知下载进度
     */
    private void notifyProgress(DownloadCallback callback, long current, long total) {
        if (callback != null) {
            try {
                callback.onProgress(current, total);
            } catch (Exception e) {
                Log.e(TAG, "Error in progress callback", e);
            }
        }
    }
    
    /**
     * 通知下载成功
     */
    private void notifySuccess(DownloadCallback callback, File file) {
        if (callback != null) {
            try {
                callback.onSuccess(file);
            } catch (Exception e) {
                Log.e(TAG, "Error in success callback", e);
            }
        }
    }
    
    /**
     * 通知下载错误
     */
    private void notifyError(DownloadCallback callback, int errorCode, String message) {
        if (callback != null) {
            try {
                callback.onError(errorCode, message);
            } catch (Exception e) {
                Log.e(TAG, "Error in error callback", e);
            }
        }
    }
    
    /**
     * 静默关闭流
     */
    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing stream", e);
            }
        }
    }
}
