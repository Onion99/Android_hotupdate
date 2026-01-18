package com.orange.patchgen.signer;

import com.orange.patchgen.config.SigningConfig;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * JAR 签名器
 * 
 * 生成完整的 JAR 签名结构（MANIFEST.MF + .SF + .RSA），
 * 使补丁文件可以通过 JarFile.getCertificates() 验证。
 * 
 * 这是 Tinker 使用的签名方式。
 * 
 * 实现说明：
 * 1. 生成 MANIFEST.MF - 包含所有文件的 SHA-256 摘要
 * 2. 生成 .SF 文件 - 包含 MANIFEST.MF 各部分的摘要
 * 3. 生成 .RSA/.DSA/.EC 文件 - 包含 .SF 的签名和证书链
 */
public class JarSigner {
    
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String DIGEST_MANIFEST_ATTR = "SHA-256-Digest-Manifest";
    private static final String DIGEST_ATTR = "SHA-256-Digest";
    
    private final SigningConfig config;
    
    public JarSigner(SigningConfig config) {
        this.config = config;
    }
    
    /**
     * 对 JAR/ZIP 文件签名
     * 
     * @param jarFile 要签名的文件
     * @throws SigningException 签名失败时抛出
     */
    public void sign(File jarFile) throws SigningException {
        if (jarFile == null || !jarFile.exists()) {
            throw new SigningException("JAR file does not exist");
        }
        
        try {
            // 1. 加载密钥和证书
            KeyStore keyStore = loadKeyStore();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(
                config.getKeyAlias(),
                config.getKeyPassword().toCharArray()
            );
            Certificate[] certChain = keyStore.getCertificateChain(config.getKeyAlias());
            
            if (privateKey == null) {
                throw new SigningException("Private key not found: " + config.getKeyAlias());
            }
            if (certChain == null || certChain.length == 0) {
                throw new SigningException("Certificate chain not found: " + config.getKeyAlias());
            }
            
            // 2. 读取现有 JAR 文件
            Map<String, byte[]> entries = readJarEntries(jarFile);
            
            // 3. 生成 MANIFEST.MF
            Manifest manifest = generateManifest(entries);
            
            // 4. 生成 .SF 文件
            byte[] sfBytes = generateSignatureFile(manifest);
            
            // 5. 生成签名块文件（.RSA/.DSA/.EC）
            byte[] signatureBlock = generateSignatureBlock(sfBytes, privateKey, certChain);
            
            // 6. 确定签名文件扩展名
            String signatureExt = getSignatureExtension(privateKey.getAlgorithm());
            
            // 7. 重新打包 JAR 文件
            repackJar(jarFile, entries, manifest, sfBytes, signatureBlock, signatureExt);
            
        } catch (Exception e) {
            throw new SigningException("Failed to sign JAR: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载密钥库
     */
    private KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(config.getKeystoreFile())) {
            keyStore.load(fis, config.getKeystorePassword().toCharArray());
        }
        return keyStore;
    }
    
    /**
     * 读取 JAR 文件中的所有条目
     */
    private Map<String, byte[]> readJarEntries(File jarFile) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // 跳过目录和旧的签名文件
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                
                entries.put(entry.getName(), baos.toByteArray());
            }
        }
        
        return entries;
    }
    
    /**
     * 生成 MANIFEST.MF
     */
    private Manifest generateManifest(Map<String, byte[]> entries) throws NoSuchAlgorithmException {
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttrs.putValue("Created-By", "HotUpdate JarSigner");
        
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue();
            
            // 计算文件的 SHA-256 摘要
            digest.reset();
            byte[] hash = digest.digest(data);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);
            
            // 添加到 manifest
            Attributes attrs = new Attributes();
            attrs.putValue(DIGEST_ATTR, hashBase64);
            manifest.getEntries().put(name, attrs);
        }
        
        return manifest;
    }
    
    /**
     * 生成 .SF 签名文件
     */
    private byte[] generateSignatureFile(Manifest manifest) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 写入 .SF 文件头
        baos.write("Signature-Version: 1.0\r\n".getBytes("UTF-8"));
        baos.write(("Created-By: HotUpdate JarSigner\r\n").getBytes("UTF-8"));
        
        // 计算整个 MANIFEST.MF 的摘要
        ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
        manifest.write(manifestBytes);
        byte[] manifestData = manifestBytes.toByteArray();
        
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] manifestHash = digest.digest(manifestData);
        String manifestHashBase64 = Base64.getEncoder().encodeToString(manifestHash);
        baos.write((DIGEST_MANIFEST_ATTR + ": " + manifestHashBase64 + "\r\n").getBytes("UTF-8"));
        baos.write("\r\n".getBytes("UTF-8"));
        
        // 为每个条目生成摘要
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            String name = entry.getKey();
            Attributes attrs = entry.getValue();
            
            // 计算该条目在 MANIFEST.MF 中的摘要
            ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
            entryBytes.write(("Name: " + name + "\r\n").getBytes("UTF-8"));
            for (Object key : attrs.keySet()) {
                String attrName = key.toString();
                String attrValue = attrs.getValue(attrName);
                entryBytes.write((attrName + ": " + attrValue + "\r\n").getBytes("UTF-8"));
            }
            entryBytes.write("\r\n".getBytes("UTF-8"));
            
            digest.reset();
            byte[] entryHash = digest.digest(entryBytes.toByteArray());
            String entryHashBase64 = Base64.getEncoder().encodeToString(entryHash);
            
            baos.write(("Name: " + name + "\r\n").getBytes("UTF-8"));
            baos.write((DIGEST_ATTR + ": " + entryHashBase64 + "\r\n").getBytes("UTF-8"));
            baos.write("\r\n".getBytes("UTF-8"));
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 生成签名块文件（.RSA/.DSA/.EC）
     */
    private byte[] generateSignatureBlock(byte[] sfBytes, PrivateKey privateKey, Certificate[] certChain) throws Exception {
        // 对 .SF 文件进行签名
        String signatureAlgorithm = getSignatureAlgorithm(privateKey.getAlgorithm());
        Signature signature = Signature.getInstance(signatureAlgorithm);
        signature.initSign(privateKey);
        signature.update(sfBytes);
        byte[] signatureBytes = signature.sign();
        
        // 生成 PKCS#7 签名块
        return generatePKCS7Block(sfBytes, signatureBytes, certChain, signatureAlgorithm);
    }
    
    /**
     * 生成 PKCS#7 签名块
     * 
     * 这里使用简化的 PKCS#7 格式，兼容 Android 的 JarFile 验证
     */
    private byte[] generatePKCS7Block(byte[] data, byte[] signature, Certificate[] certChain, String algorithm) throws Exception {
        // 使用 sun.security.pkcs.PKCS7（JDK 内部 API）
        // 注意：这在 Android 上不可用，但在桌面 Java 上可用
        try {
            Class<?> pkcs7Class = Class.forName("sun.security.pkcs.PKCS7");
            Class<?> signerInfoClass = Class.forName("sun.security.pkcs.SignerInfo");
            Class<?> algorithmIdClass = Class.forName("sun.security.x509.AlgorithmId");
            
            // 创建 AlgorithmId
            java.lang.reflect.Method getMethod = algorithmIdClass.getMethod("get", String.class);
            Object digestAlgorithmId = getMethod.invoke(null, DIGEST_ALGORITHM);
            Object signatureAlgorithmId = getMethod.invoke(null, algorithm);
            
            // 创建 SignerInfo
            java.lang.reflect.Constructor<?> signerInfoConstructor = signerInfoClass.getConstructor(
                java.security.cert.X509Certificate.class,
                algorithmIdClass,
                algorithmIdClass,
                byte[].class
            );
            Object signerInfo = signerInfoConstructor.newInstance(
                (X509Certificate) certChain[0],
                digestAlgorithmId,
                signatureAlgorithmId,
                signature
            );
            
            // 创建 PKCS7
            java.lang.reflect.Constructor<?> pkcs7Constructor = pkcs7Class.getConstructor(
                new Class[]{algorithmIdClass.arrayType(), java.security.cert.X509Certificate[].class, byte[].class, signerInfoClass.arrayType()}
            );
            
            Object[] digestAlgorithmIds = (Object[]) java.lang.reflect.Array.newInstance(algorithmIdClass, 1);
            java.lang.reflect.Array.set(digestAlgorithmIds, 0, digestAlgorithmId);
            
            X509Certificate[] x509Certs = new X509Certificate[certChain.length];
            for (int i = 0; i < certChain.length; i++) {
                x509Certs[i] = (X509Certificate) certChain[i];
            }
            
            Object[] signerInfos = (Object[]) java.lang.reflect.Array.newInstance(signerInfoClass, 1);
            java.lang.reflect.Array.set(signerInfos, 0, signerInfo);
            
            Object pkcs7 = pkcs7Constructor.newInstance(digestAlgorithmIds, x509Certs, data, signerInfos);
            
            // 编码为 DER 格式
            java.lang.reflect.Method encodeSignedDataMethod = pkcs7Class.getMethod("encodeSignedData");
            return (byte[]) encodeSignedDataMethod.invoke(pkcs7);
            
        } catch (ClassNotFoundException e) {
            // sun.security.pkcs 不可用（可能在 Android 上），使用简化格式
            return generateSimplifiedBlock(signature, certChain);
        }
    }
    
    /**
     * 生成简化的签名块（当 PKCS#7 不可用时）
     * 
     * 这个格式可能不被所有 JarFile 实现接受，但可以作为后备方案
     */
    private byte[] generateSimplifiedBlock(byte[] signature, Certificate[] certChain) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 写入签名
        baos.write(signature);
        
        // 写入证书链
        for (Certificate cert : certChain) {
            byte[] encoded = cert.getEncoded();
            baos.write(encoded);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 根据密钥算法确定签名算法
     */
    private String getSignatureAlgorithm(String keyAlgorithm) {
        switch (keyAlgorithm.toUpperCase()) {
            case "RSA":
                return "SHA256withRSA";
            case "DSA":
                return "SHA256withDSA";
            case "EC":
                return "SHA256withECDSA";
            default:
                return "SHA256withRSA";
        }
    }
    
    /**
     * 确定签名文件扩展名
     */
    private String getSignatureExtension(String keyAlgorithm) {
        switch (keyAlgorithm.toUpperCase()) {
            case "RSA":
                return "RSA";
            case "DSA":
                return "DSA";
            case "EC":
                return "EC";
            default:
                return "RSA";
        }
    }
    
    /**
     * 重新打包 JAR 文件，包含签名
     */
    private void repackJar(File jarFile, Map<String, byte[]> entries, Manifest manifest, 
                          byte[] sfBytes, byte[] signatureBlock, String signatureExt) throws IOException {
        File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
            // 1. 写入 MANIFEST.MF
            zos.putNextEntry(new ZipEntry(MANIFEST_NAME));
            manifest.write(zos);
            zos.closeEntry();
            
            // 2. 写入 .SF 文件
            String sfName = "META-INF/CERT.SF";
            zos.putNextEntry(new ZipEntry(sfName));
            zos.write(sfBytes);
            zos.closeEntry();
            
            // 3. 写入签名块文件
            String sigName = "META-INF/CERT." + signatureExt;
            zos.putNextEntry(new ZipEntry(sigName));
            zos.write(signatureBlock);
            zos.closeEntry();
            
            // 4. 写入所有原始文件
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        
        // 替换原文件
        if (!jarFile.delete()) {
            throw new IOException("Failed to delete original JAR file");
        }
        if (!tempFile.renameTo(jarFile)) {
            throw new IOException("Failed to rename temporary file");
        }
    }
    
    /**
     * 签名异常
     */
    public static class SigningException extends Exception {
        public SigningException(String message) {
            super(message);
        }
        
        public SigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
