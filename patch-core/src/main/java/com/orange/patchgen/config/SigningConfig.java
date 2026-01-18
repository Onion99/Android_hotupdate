package com.orange.patchgen.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyStore;
import java.security.PrivateKey;

/**
 * 签名配置
 * 
 * Requirements: 6.3, 6.4
 */
public class SigningConfig {
    private File keystoreFile;
    private String keystorePassword;
    private String keyAlias;
    private String keyPassword;
    private File pemFile;  // 可选，PEM 格式私钥
    private File sourceApk;  // 可选，用于提取签名的源 APK

    private SigningConfig(Builder builder) {
        this.keystoreFile = builder.keystoreFile;
        this.keystorePassword = builder.keystorePassword;
        this.keyAlias = builder.keyAlias;
        this.keyPassword = builder.keyPassword;
        this.pemFile = builder.pemFile;
        this.sourceApk = builder.sourceApk;
    }

    public File getKeystoreFile() {
        return keystoreFile;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public File getPemFile() {
        return pemFile;
    }
    
    public File getSourceApk() {
        return sourceApk;
    }

    /**
     * 从 keystore 加载私钥
     */
    public PrivateKey loadPrivateKey() throws SigningException {
        if (pemFile != null && pemFile.exists()) {
            return loadFromPem();
        } else if (keystoreFile != null && keystoreFile.exists()) {
            return loadFromKeystore();
        }
        throw new SigningException("No valid key source configured");
    }

    private PrivateKey loadFromKeystore() throws SigningException {
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(fis, keystorePassword.toCharArray());
            return (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
        } catch (Exception e) {
            throw new SigningException("Failed to load private key from keystore: " + e.getMessage(), e);
        }
    }

    private PrivateKey loadFromPem() throws SigningException {
        // PEM loading would require BouncyCastle or similar library
        // For now, throw unsupported exception
        throw new SigningException("PEM file loading not yet implemented");
    }

    public boolean isValid() {
        if (pemFile != null && pemFile.exists()) {
            return true;
        }
        return keystoreFile != null && keystoreFile.exists() &&
               keystorePassword != null && !keystorePassword.isEmpty() &&
               keyAlias != null && !keyAlias.isEmpty() &&
               keyPassword != null && !keyPassword.isEmpty();
    }

    public static class Builder {
        private File keystoreFile;
        private String keystorePassword;
        private String keyAlias;
        private String keyPassword;
        private File pemFile;
        private File sourceApk;

        public Builder keystoreFile(File file) {
            this.keystoreFile = file;
            return this;
        }

        public Builder keystorePassword(String password) {
            this.keystorePassword = password;
            return this;
        }

        public Builder keyAlias(String alias) {
            this.keyAlias = alias;
            return this;
        }

        public Builder keyPassword(String password) {
            this.keyPassword = password;
            return this;
        }

        public Builder pemFile(File file) {
            this.pemFile = file;
            return this;
        }
        
        public Builder sourceApk(File apk) {
            this.sourceApk = apk;
            return this;
        }

        public SigningConfig build() {
            return new SigningConfig(this);
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
