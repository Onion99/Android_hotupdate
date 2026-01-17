package com.orange.patchnative;

/**
 * Native Patch Engine 异常类
 * 
 * 用于将 Native 层错误码转换为 Java 异常
 * 
 * Requirements: 11.9
 */
public class NativePatchException extends Exception {
    
    private final int errorCode;
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public NativePatchException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     原因异常
     */
    public NativePatchException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * 获取错误码
     * 
     * @return 错误码
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * 检查是否为文件未找到错误
     */
    public boolean isFileNotFound() {
        return errorCode == NativePatchEngine.ERROR_FILE_NOT_FOUND;
    }
    
    /**
     * 检查是否为操作取消错误
     */
    public boolean isCancelled() {
        return errorCode == NativePatchEngine.ERROR_CANCELLED;
    }
    
    /**
     * 检查是否为内存不足错误
     */
    public boolean isOutOfMemory() {
        return errorCode == NativePatchEngine.ERROR_OUT_OF_MEMORY;
    }
    
    /**
     * 检查是否为补丁损坏错误
     */
    public boolean isCorruptPatch() {
        return errorCode == NativePatchEngine.ERROR_CORRUPT_PATCH;
    }
    
    @Override
    public String toString() {
        return "NativePatchException{" +
                "errorCode=" + errorCode +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
