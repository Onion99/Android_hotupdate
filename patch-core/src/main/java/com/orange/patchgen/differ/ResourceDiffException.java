package com.orange.patchgen.differ;

/**
 * 资源差异比较异常
 * 
 * 当资源比较过程中发生错误时抛出此异常。
 * 
 * Requirements: 3.1, 3.5
 */
public class ResourceDiffException extends Exception {
    private final int errorCode;

    public ResourceDiffException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ResourceDiffException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
