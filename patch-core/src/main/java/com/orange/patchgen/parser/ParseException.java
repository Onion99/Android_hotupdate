package com.orange.patchgen.parser;

/**
 * APK 解析异常
 */
public class ParseException extends Exception {
    private final int errorCode;

    public ParseException(String message) {
        super(message);
        this.errorCode = 0;
    }

    public ParseException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    public ParseException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
