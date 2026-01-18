package com.orange.patchgen.signer;

/**
 * JKS Native 接口
 * 
 * 通过 JNI 调用 Native 层的 JKS 解析器
 */
public class JKSNative {
    
    private static boolean libraryLoaded = false;
    
    static {
        try {
            System.loadLibrary("patchengine");
            libraryLoaded = true;
            System.out.println("[JKSNative] ✓ Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[JKSNative] ✗ Failed to load native library: " + e.getMessage());
            libraryLoaded = false;
        }
    }
    
    /**
     * 检查 Native 库是否已加载
     */
    public static boolean isAvailable() {
        return libraryLoaded;
    }
    
    /**
     * 加载 JKS 文件
     * 
     * @param path JKS 文件路径
     * @param storePassword 密钥库密码
     * @return 成功返回 true，失败返回 false
     */
    public static native boolean loadKeyStore(String path, String storePassword);
    
    /**
     * 获取私钥数据（PKCS#8 格式）
     * 
     * @param alias 密钥别名
     * @param keyPassword 密钥密码
     * @return 私钥数据，失败返回 null
     */
    public static native byte[] getPrivateKey(String alias, String keyPassword);
    
    /**
     * 获取证书链
     * 
     * @param alias 密钥别名
     * @return 证书链数组，失败返回 null
     */
    public static native byte[][] getCertificateChain(String alias);
    
    /**
     * 获取所有私钥别名
     * 
     * @return 别名数组
     */
    public static native String[] getPrivateKeyAliases();
    
    /**
     * 释放资源
     */
    public static native void release();
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息
     */
    public static native String getError();
}
