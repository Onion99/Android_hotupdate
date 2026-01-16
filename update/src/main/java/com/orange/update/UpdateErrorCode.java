package com.orange.update;

/**
 * 错误码常量类，定义 SDK 中所有可能的错误码。
 * 错误码按类别分组，便于识别错误来源。
 */
public final class UpdateErrorCode {
    
    private UpdateErrorCode() {
        // 防止实例化
    }
    
    // ==================== 初始化错误 (1xxx) ====================
    
    /**
     * SDK 未初始化
     */
    public static final int ERROR_NOT_INITIALIZED = 1001;
    
    /**
     * 配置参数无效
     */
    public static final int ERROR_INVALID_CONFIG = 1002;
    
    /**
     * 上下文无效
     */
    public static final int ERROR_INVALID_CONTEXT = 1003;
    
    // ==================== 网络错误 (2xxx) ====================
    
    /**
     * 网络不可用
     */
    public static final int ERROR_NETWORK_UNAVAILABLE = 2001;
    
    /**
     * 服务器错误
     */
    public static final int ERROR_SERVER_ERROR = 2002;
    
    /**
     * 请求超时
     */
    public static final int ERROR_TIMEOUT = 2003;
    
    /**
     * 服务器响应格式错误
     */
    public static final int ERROR_INVALID_RESPONSE = 2004;
    
    // ==================== 下载错误 (3xxx) ====================
    
    /**
     * 下载失败
     */
    public static final int ERROR_DOWNLOAD_FAILED = 3001;
    
    /**
     * 存储空间不足
     */
    public static final int ERROR_INSUFFICIENT_SPACE = 3002;
    
    /**
     * 文件未找到
     */
    public static final int ERROR_FILE_NOT_FOUND = 3003;
    
    /**
     * 下载被取消
     */
    public static final int ERROR_DOWNLOAD_CANCELLED = 3004;
    
    /**
     * 写入文件失败
     */
    public static final int ERROR_FILE_WRITE_FAILED = 3005;
    
    // ==================== 验证错误 (4xxx) ====================
    
    /**
     * MD5 校验失败
     */
    public static final int ERROR_CHECKSUM_MISMATCH = 4001;
    
    /**
     * 补丁格式无效
     */
    public static final int ERROR_INVALID_PATCH_FORMAT = 4002;
    
    /**
     * 补丁版本不匹配
     */
    public static final int ERROR_VERSION_MISMATCH = 4003;
    
    /**
     * 补丁文件损坏
     */
    public static final int ERROR_PATCH_CORRUPTED = 4004;
    
    // ==================== 应用错误 (5xxx) ====================
    
    /**
     * 补丁应用失败
     */
    public static final int ERROR_APPLY_FAILED = 5001;
    
    /**
     * 回滚失败
     */
    public static final int ERROR_ROLLBACK_FAILED = 5002;
    
    /**
     * ClassLoader 注入失败
     */
    public static final int ERROR_CLASSLOADER_INJECTION_FAILED = 5003;
    
    /**
     * 资源替换失败
     */
    public static final int ERROR_RESOURCE_REPLACEMENT_FAILED = 5004;
    
    /**
     * 补丁已应用
     */
    public static final int ERROR_PATCH_ALREADY_APPLIED = 5005;
    
    // ==================== 安全错误 (6xxx) ====================
    
    /**
     * 签名验证失败
     */
    public static final int ERROR_SIGNATURE_INVALID = 6001;
    
    /**
     * 解密失败
     */
    public static final int ERROR_DECRYPTION_FAILED = 6002;
    
    /**
     * KeyStore 错误
     */
    public static final int ERROR_KEYSTORE_ERROR = 6003;
    
    /**
     * 加密失败
     */
    public static final int ERROR_ENCRYPTION_FAILED = 6004;
    
    /**
     * 公钥无效
     */
    public static final int ERROR_INVALID_PUBLIC_KEY = 6005;
    
    /**
     * 获取错误码对应的描述信息
     * @param errorCode 错误码
     * @return 错误描述
     */
    public static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            // 初始化错误
            case ERROR_NOT_INITIALIZED:
                return "SDK not initialized";
            case ERROR_INVALID_CONFIG:
                return "Invalid configuration";
            case ERROR_INVALID_CONTEXT:
                return "Invalid context";
            
            // 网络错误
            case ERROR_NETWORK_UNAVAILABLE:
                return "Network unavailable";
            case ERROR_SERVER_ERROR:
                return "Server error";
            case ERROR_TIMEOUT:
                return "Request timeout";
            case ERROR_INVALID_RESPONSE:
                return "Invalid server response";
            
            // 下载错误
            case ERROR_DOWNLOAD_FAILED:
                return "Download failed";
            case ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case ERROR_FILE_NOT_FOUND:
                return "File not found";
            case ERROR_DOWNLOAD_CANCELLED:
                return "Download cancelled";
            case ERROR_FILE_WRITE_FAILED:
                return "Failed to write file";
            
            // 验证错误
            case ERROR_CHECKSUM_MISMATCH:
                return "Checksum verification failed";
            case ERROR_INVALID_PATCH_FORMAT:
                return "Invalid patch format";
            case ERROR_VERSION_MISMATCH:
                return "Version mismatch";
            case ERROR_PATCH_CORRUPTED:
                return "Patch file corrupted";
            
            // 应用错误
            case ERROR_APPLY_FAILED:
                return "Failed to apply patch";
            case ERROR_ROLLBACK_FAILED:
                return "Failed to rollback";
            case ERROR_CLASSLOADER_INJECTION_FAILED:
                return "ClassLoader injection failed";
            case ERROR_RESOURCE_REPLACEMENT_FAILED:
                return "Resource replacement failed";
            case ERROR_PATCH_ALREADY_APPLIED:
                return "Patch already applied";
            
            // 安全错误
            case ERROR_SIGNATURE_INVALID:
                return "Invalid signature";
            case ERROR_DECRYPTION_FAILED:
                return "Decryption failed";
            case ERROR_KEYSTORE_ERROR:
                return "KeyStore error";
            case ERROR_ENCRYPTION_FAILED:
                return "Encryption failed";
            case ERROR_INVALID_PUBLIC_KEY:
                return "Invalid public key";
            
            default:
                return "Unknown error: " + errorCode;
        }
    }
}
