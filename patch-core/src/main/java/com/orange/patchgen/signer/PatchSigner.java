package com.orange.patchgen.signer;

import com.orange.patchgen.config.SigningConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 补丁签名器
 * 
 * 使用 RSA-2048 算法对补丁文件进行签名，确保补丁的完整性和真实性。
 * 支持从 keystore 或 PEM 文件加载私钥。
 * 
 * Requirements: 6.1-6.5
 */
public class PatchSigner {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String SIGNATURE_FILE_NAME = "signature.sig";
    private static final String KEY_ALGORITHM = "RSA";

    private final SigningConfig config;
    private PrivateKey privateKey;

    /**
     * 创建 PatchSigner
     * 
     * @param config 签名配置
     */
    public PatchSigner(SigningConfig config) {
        this.config = config;
    }

    /**
     * 对补丁文件签名
     * 
     * 签名文件将被添加到补丁 zip 文件中作为 signature.sig
     * 
     * @param patchFile 补丁文件
     * @throws SigningException 签名失败时抛出
     */
    public void sign(File patchFile) throws SigningException {
        if (patchFile == null || !patchFile.exists()) {
            throw new SigningException("Patch file does not exist");
        }

        try {
            // 加载私钥
            PrivateKey key = loadPrivateKey();
            
            // 读取补丁文件内容
            byte[] patchContent = Files.readAllBytes(patchFile.toPath());
            
            // 生成签名
            byte[] signature = generateSignature(patchContent, key);
            
            // 将签名写入补丁文件
            addSignatureToZip(patchFile, signature);
            
        } catch (IOException e) {
            throw new SigningException("Failed to read patch file: " + e.getMessage(), e);
        }
    }

    /**
     * 对补丁文件签名并输出签名文件
     * 
     * @param patchFile 补丁文件
     * @param signatureFile 签名输出文件
     * @throws SigningException 签名失败时抛出
     */
    public void sign(File patchFile, File signatureFile) throws SigningException {
        if (patchFile == null || !patchFile.exists()) {
            throw new SigningException("Patch file does not exist");
        }

        try {
            // 加载私钥
            PrivateKey key = loadPrivateKey();
            
            // 读取补丁文件内容
            byte[] patchContent = Files.readAllBytes(patchFile.toPath());
            
            // 生成签名
            byte[] signature = generateSignature(patchContent, key);
            
            // 写入签名文件
            try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                fos.write(signature);
            }
            
        } catch (IOException e) {
            throw new SigningException("Failed to sign patch file: " + e.getMessage(), e);
        }
    }

    /**
     * 生成签名
     * 
     * @param data 要签名的数据
     * @return 签名字节数组
     * @throws SigningException 签名失败时抛出
     */
    public byte[] generateSignature(byte[] data) throws SigningException {
        return generateSignature(data, loadPrivateKey());
    }

    /**
     * 生成签名
     * 
     * @param data 要签名的数据
     * @param privateKey 私钥
     * @return 签名字节数组
     * @throws SigningException 签名失败时抛出
     */
    public byte[] generateSignature(byte[] data, PrivateKey privateKey) throws SigningException {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new SigningException("Signature algorithm not available: " + SIGNATURE_ALGORITHM, e);
        } catch (InvalidKeyException e) {
            throw new SigningException("Invalid private key", e);
        } catch (SignatureException e) {
            throw new SigningException("Failed to generate signature", e);
        }
    }

    /**
     * 验证签名
     * 
     * @param data 原始数据
     * @param signature 签名
     * @param publicKey 公钥
     * @return 签名是否有效
     */
    public boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    /**
     * 加载私钥
     * 
     * @return 私钥
     * @throws SigningException 加载失败时抛出
     */
    public PrivateKey loadPrivateKey() throws SigningException {
        if (privateKey != null) {
            return privateKey;
        }

        if (config == null) {
            throw new SigningException("SigningConfig is not set");
        }

        if (config.getPemFile() != null && config.getPemFile().exists()) {
            privateKey = loadFromPem(config.getPemFile());
        } else if (config.getKeystoreFile() != null && config.getKeystoreFile().exists()) {
            privateKey = loadFromKeystore();
        } else {
            throw new SigningException("No valid key source configured");
        }

        return privateKey;
    }

    /**
     * 从 keystore 加载私钥
     */
    private PrivateKey loadFromKeystore() throws SigningException {
        try (FileInputStream fis = new FileInputStream(config.getKeystoreFile())) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(fis, config.getKeystorePassword().toCharArray());
            
            PrivateKey key = (PrivateKey) keyStore.getKey(
                config.getKeyAlias(), 
                config.getKeyPassword().toCharArray()
            );
            
            if (key == null) {
                throw new SigningException("Key not found in keystore: " + config.getKeyAlias());
            }
            
            return key;
        } catch (Exception e) {
            throw new SigningException("Failed to load private key from keystore: " + e.getMessage(), e);
        }
    }

    /**
     * 从 PEM 文件加载私钥
     */
    private PrivateKey loadFromPem(File pemFile) throws SigningException {
        try {
            String pemContent = new String(Files.readAllBytes(pemFile.toPath()));
            
            // 移除 PEM 头尾和换行
            String privateKeyPEM = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyPEM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePrivate(spec);
            
        } catch (Exception e) {
            throw new SigningException("Failed to load private key from PEM file: " + e.getMessage(), e);
        }
    }

    /**
     * 将签名添加到 zip 文件
     */
    private void addSignatureToZip(File patchFile, byte[] signature) throws SigningException {
        try {
            try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile)) {
                // 创建临时签名文件
                File tempSigFile = File.createTempFile("signature", ".sig");
                try (FileOutputStream fos = new FileOutputStream(tempSigFile)) {
                    fos.write(signature);
                }
                
                // 添加到 zip
                net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
                params.setFileNameInZip(SIGNATURE_FILE_NAME);
                zipFile.addFile(tempSigFile, params);
                
                // 删除临时文件
                tempSigFile.delete();
            }
        } catch (IOException e) {
            throw new SigningException("Failed to add signature to patch file: " + e.getMessage(), e);
        }
    }

    /**
     * 获取签名文件名
     */
    public static String getSignatureFileName() {
        return SIGNATURE_FILE_NAME;
    }

    /**
     * 获取签名算法
     */
    public static String getSignatureAlgorithm() {
        return SIGNATURE_ALGORITHM;
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
