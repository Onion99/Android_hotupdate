package com.orange.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 计算工具类。
 * 提供文件和字节数组的 MD5 哈希计算功能。
 */
public final class Md5Utils {
    
    private static final int BUFFER_SIZE = 8192;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    
    private Md5Utils() {
        // 防止实例化
    }
    
    /**
     * 计算文件的 MD5 哈希值
     * @param file 要计算哈希的文件
     * @return 32 位小写十六进制 MD5 字符串
     * @throws IOException 如果文件读取失败
     * @throws IllegalArgumentException 如果文件为 null 或不存在
     */
    public static String calculateMd5(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("Path is not a file: " + file.getAbsolutePath());
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法应该始终可用
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * 计算字节数组的 MD5 哈希值
     * @param data 要计算哈希的字节数组
     * @return 32 位小写十六进制 MD5 字符串
     * @throws IllegalArgumentException 如果数据为 null
     */
    public static String calculateMd5(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法应该始终可用
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 小写十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    /**
     * 验证文件的 MD5 是否与期望值匹配
     * @param file 要验证的文件
     * @param expectedMd5 期望的 MD5 值（不区分大小写）
     * @return 如果匹配返回 true，否则返回 false
     * @throws IOException 如果文件读取失败
     */
    public static boolean verifyMd5(File file, String expectedMd5) throws IOException {
        if (expectedMd5 == null || expectedMd5.isEmpty()) {
            throw new IllegalArgumentException("Expected MD5 cannot be null or empty");
        }
        
        String actualMd5 = calculateMd5(file);
        return actualMd5.equalsIgnoreCase(expectedMd5);
    }
}
