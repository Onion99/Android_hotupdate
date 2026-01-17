package com.orange.patchgen.differ;

import com.orange.patchgen.callback.GeneratorErrorCode;

/**
 * Dex 差异比较异常
 * 
 * 在 Dex 文件解析或比较过程中发生错误时抛出。
 */
public class DexDiffException extends Exception {
    private final int errorCode;

    public DexDiffException(String message) {
        super(message);
        this.errorCode = GeneratorErrorCode.ERROR_COMPARE_FAILED;
    }

    public DexDiffException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public DexDiffException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = GeneratorErrorCode.ERROR_COMPARE_FAILED;
    }

    public DexDiffException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
