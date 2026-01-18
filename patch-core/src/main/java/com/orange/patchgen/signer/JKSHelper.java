package com.orange.patchgen.signer;

import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * JKS Helper - 使用 Java 标准 API 解析 JKS 文件
 * 
 * 这个类提供了一个简单的方式来解析 JKS 文件，避免重新实现复杂的加密算法
 */
public class JKSHelper {
    
    private KeyStore keyStore;
    private String lastError;
    
    /**
     * 加载 JKS 文件
     * 
     * @param path JKS 文件路径
     * @param storePassword 密钥库密码
     * @return 成功返回 true，失败返回 false
     */
    public boolean load(String path, String storePassword) {
        try {
            keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(path)) {
                keyStore.load(fis, storePassword.toCharArray());
            }
            return true;
        } catch (Exception e) {
            lastError = "Failed to load JKS: " + e.getMessage();
            return false;
        }
    }
    
    /**
     * 获取私钥数据（PKCS#8 格式）
     * 
     * @param alias 密钥别名
     * @param keyPassword 密钥密码
     * @return 私钥数据（PKCS#8 编码），失败返回 null
     */
    public byte[] getPrivateKey(String alias, String keyPassword) {
        try {
            Key key = keyStore.getKey(alias, keyPassword.toCharArray());
            if (key == null) {
                lastError = "Key not found: " + alias;
                return null;
            }
            return key.getEncoded();
        } catch (Exception e) {
            lastError = "Failed to get private key: " + e.getMessage();
            return null;
        }
    }
    
    /**
     * 获取证书链
     * 
     * @param alias 密钥别名
     * @return 证书链数组（DER 编码），失败返回 null
     */
    public byte[][] getCertificateChain(String alias) {
        try {
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null) {
                lastError = "Certificate chain not found: " + alias;
                return null;
            }
            
            byte[][] result = new byte[chain.length][];
            for (int i = 0; i < chain.length; i++) {
                result[i] = chain[i].getEncoded();
            }
            return result;
        } catch (Exception e) {
            lastError = "Failed to get certificate chain: " + e.getMessage();
            return null;
        }
    }
    
    /**
     * 获取所有私钥别名
     * 
     * @return 别名数组
     */
    public String[] getPrivateKeyAliases() {
        try {
            List<String> aliases = new ArrayList<>();
            Enumeration<String> e = keyStore.aliases();
            while (e.hasMoreElements()) {
                String alias = e.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    aliases.add(alias);
                }
            }
            return aliases.toArray(new String[0]);
        } catch (Exception e) {
            lastError = "Failed to get aliases: " + e.getMessage();
            return new String[0];
        }
    }
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息
     */
    public String getError() {
        return lastError;
    }
}
