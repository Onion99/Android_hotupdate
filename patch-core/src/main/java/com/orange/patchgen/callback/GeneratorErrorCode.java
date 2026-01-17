package com.orange.patchgen.callback;

/**
 * 生成器错误码
 * 
 * Requirements: 13.1-13.8
 */
public class GeneratorErrorCode {
    // 文件错误 (1xxx)
    public static final int ERROR_FILE_NOT_FOUND = 1001;
    public static final int ERROR_FILE_READ_FAILED = 1002;
    public static final int ERROR_FILE_WRITE_FAILED = 1003;
    public static final int ERROR_INSUFFICIENT_SPACE = 1004;
    public static final int ERROR_PERMISSION_DENIED = 1005;

    // APK 解析错误 (2xxx)
    public static final int ERROR_INVALID_APK = 2001;
    public static final int ERROR_APK_PARSE_FAILED = 2002;
    public static final int ERROR_DEX_PARSE_FAILED = 2003;
    public static final int ERROR_MANIFEST_PARSE_FAILED = 2004;

    // 比较错误 (3xxx)
    public static final int ERROR_VERSION_MISMATCH = 3001;
    public static final int ERROR_COMPARE_FAILED = 3002;
    public static final int ERROR_NO_CHANGES = 3003;

    // 签名错误 (4xxx)
    public static final int ERROR_KEYSTORE_NOT_FOUND = 4001;
    public static final int ERROR_INVALID_KEY = 4002;
    public static final int ERROR_SIGNING_FAILED = 4003;
    public static final int ERROR_KEY_LOAD_FAILED = 4004;

    // Native 错误 (5xxx)
    public static final int ERROR_NATIVE_LIB_NOT_FOUND = 5001;
    public static final int ERROR_NATIVE_INIT_FAILED = 5002;
    public static final int ERROR_BSDIFF_FAILED = 5003;
    public static final int ERROR_BSPATCH_FAILED = 5004;

    // 操作错误 (6xxx)
    public static final int ERROR_CANCELLED = 6001;
    public static final int ERROR_TIMEOUT = 6002;
    public static final int ERROR_UNKNOWN = 6999;

    /**
     * 获取错误码描述
     */
    public static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case ERROR_FILE_NOT_FOUND:
                return "File not found";
            case ERROR_FILE_READ_FAILED:
                return "Failed to read file";
            case ERROR_FILE_WRITE_FAILED:
                return "Failed to write file";
            case ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case ERROR_PERMISSION_DENIED:
                return "Permission denied";
            case ERROR_INVALID_APK:
                return "Invalid APK file";
            case ERROR_APK_PARSE_FAILED:
                return "Failed to parse APK";
            case ERROR_DEX_PARSE_FAILED:
                return "Failed to parse DEX file";
            case ERROR_MANIFEST_PARSE_FAILED:
                return "Failed to parse AndroidManifest.xml";
            case ERROR_VERSION_MISMATCH:
                return "Version mismatch between APKs";
            case ERROR_COMPARE_FAILED:
                return "Failed to compare files";
            case ERROR_NO_CHANGES:
                return "No changes detected";
            case ERROR_KEYSTORE_NOT_FOUND:
                return "Keystore file not found";
            case ERROR_INVALID_KEY:
                return "Invalid signing key";
            case ERROR_SIGNING_FAILED:
                return "Failed to sign patch";
            case ERROR_KEY_LOAD_FAILED:
                return "Failed to load private key";
            case ERROR_NATIVE_LIB_NOT_FOUND:
                return "Native library not found";
            case ERROR_NATIVE_INIT_FAILED:
                return "Failed to initialize native engine";
            case ERROR_BSDIFF_FAILED:
                return "BsDiff operation failed";
            case ERROR_BSPATCH_FAILED:
                return "BsPatch operation failed";
            case ERROR_CANCELLED:
                return "Operation cancelled";
            case ERROR_TIMEOUT:
                return "Operation timed out";
            default:
                return "Unknown error";
        }
    }

    private GeneratorErrorCode() {
        // Prevent instantiation
    }
}
