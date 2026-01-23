package com.orange.patchgen.android;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 补丁加密器，用于在生成补丁后对其进行加密。
 * 
 * 功能：
 * - Android KeyStore 密钥管理
 * - AES-256-GCM 加密
 * - 基于密码的加密（PBKDF2）
 * 
 * 注意：此类仅用于加密补丁，解密功能在 update 模块的 SecurityManager 中。
 */
public class PatchEncryptor {
    
    private static final String TAG = "PatchEncryptor";
    
    // KeyStore 相关常量
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "patch_encryption_key";
    
    // 加密算法常量
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM_AES = "AES";
    
    // GCM 参数
    private static final int GCM_IV_LENGTH = 12;  // 12 bytes for GCM IV
    private static final int GCM_TAG_LENGTH = 128; // 128 bits for GCM auth tag
    private static final int AES_KEY_SIZE = 256;   // 256 bits for AES key
    
    private final Context context;
    private KeyStore keyStore;
    
    /**
     * 创建 PatchEncryptor 实例
     * @param context 应用上下文
     * @throws SecurityException 如果 KeyStore 初始化失败
     */
    public PatchEncryptor(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        initKeyStore();
    }
    
    /**
     * 初始化 Android KeyStore
     */
    private void initKeyStore() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | 
                 NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Failed to initialize KeyStore", e);
            throw new SecurityException("Failed to initialize KeyStore: " + e.getMessage(), e);
        }
    }
    
    // ==================== 密钥管理 ====================
    
    /**
     * 获取或创建 AES 加密密钥
     * 密钥存储在 Android KeyStore 中，受硬件安全模块保护
     * 注意：此方法需要 API 23+ (Android 6.0+)
     * @return AES 密钥
     * @throws SecurityException 如果密钥生成或获取失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public SecretKey getOrCreateEncryptionKey() {
        try {
            // 检查密钥是否已存在
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            
            // 生成新密钥
            return generateEncryptionKey();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            Log.e(TAG, "Failed to get or create encryption key", e);
            throw new SecurityException("Failed to get or create encryption key: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成新的 AES-256 加密密钥并存储到 KeyStore
     * @return 生成的密钥
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey generateEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE)
                    .setUserAuthenticationRequired(false);
            
            // Android 7.0+ 支持设置密钥失效
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false);
            }
            
            keyGenerator.init(builder.build());
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | 
                 InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed to generate encryption key", e);
            throw new SecurityException("Failed to generate encryption key: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除存储的加密密钥
     * @return 是否删除成功
     */
    public boolean deleteEncryptionKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
                return true;
            }
            return false;
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to delete encryption key", e);
            return false;
        }
    }
    
    /**
     * 检查加密密钥是否存在
     * @return 密钥是否存在
     */
    public boolean hasEncryptionKey() {
        try {
            return keyStore.containsAlias(KEY_ALIAS);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to check encryption key", e);
            return false;
        }
    }
    
    // ==================== AES-256-GCM 加密 ====================
    
    /**
     * 加密补丁文件（使用密码）
     * 使用 PBKDF2 从密码派生密钥，然后使用 AES-256-GCM 加密
     * 
     * @param patchFile 原始补丁文件
     * @param password 加密密码
     * @return 加密后的文件（.enc 扩展名）
     * @throws SecurityException 如果加密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public File encryptPatchWithPassword(File patchFile, String password) {
        if (patchFile == null || !patchFile.exists()) {
            throw new IllegalArgumentException("Patch file does not exist");
        }
        
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        File encryptedFile = new File(patchFile.getPath() + ".enc");
        
        try {
            // 从密码派生密钥
            SecretKey key = deriveKeyFromPassword(password);
            
            // 初始化加密器（不提供 IV，让 Cipher 自动生成）
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            // 读取原始文件
            byte[] plainData = readFileBytes(patchFile);
            
            // 加密数据
            byte[] encryptedData = cipher.doFinal(plainData);
            
            // 获取 Cipher 自动生成的 IV
            byte[] iv = cipher.getIV();
            
            // 写入加密文件：IV + 加密数据（包含 auth tag）
            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                fos.write(iv);
                fos.write(encryptedData);
            }
            
            return encryptedFile;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException | IOException e) {
            Log.e(TAG, "Failed to encrypt patch file with password", e);
            // 清理失败的加密文件
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
            throw new SecurityException("Failed to encrypt patch file: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从密码派生密钥
     * 使用 PBKDF2WithHmacSHA256 算法
     * 
     * @param password 密码
     * @return 派生的密钥
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey deriveKeyFromPassword(String password) {
        try {
            // 使用固定的盐值（与 SecurityManager 保持一致）
            byte[] salt = "OrangeHotUpdateSalt2024".getBytes("UTF-8");
            
            // 使用 PBKDF2 派生密钥
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(), 
                salt, 
                10000,  // 迭代次数
                AES_KEY_SIZE  // 密钥长度
            );
            
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            
            return new javax.crypto.spec.SecretKeySpec(keyBytes, KEY_ALGORITHM_AES);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive key from password", e);
            throw new SecurityException("Failed to derive key from password: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加密补丁文件（使用 KeyStore 密钥）
     * 使用 AES-256-GCM 加密，输出格式：[IV(12 bytes)] + [encrypted_data] + [auth_tag(16 bytes)]
     * 
     * @param patchFile 原始补丁文件
     * @return 加密后的文件（.enc 扩展名）
     * @throws SecurityException 如果加密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public File encryptPatch(File patchFile) {
        if (patchFile == null || !patchFile.exists()) {
            throw new IllegalArgumentException("Patch file does not exist");
        }
        
        File encryptedFile = new File(patchFile.getPath() + ".enc");
        
        try {
            // 获取加密密钥
            SecretKey key = getOrCreateEncryptionKey();
            
            // 初始化加密器（不提供 IV，让 Cipher 自动生成）
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            // 读取原始文件
            byte[] plainData = readFileBytes(patchFile);
            
            // 加密数据
            byte[] encryptedData = cipher.doFinal(plainData);
            
            // 获取 Cipher 自动生成的 IV
            byte[] iv = cipher.getIV();
            
            // 写入加密文件：IV + 加密数据（包含 auth tag）
            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                fos.write(iv);
                fos.write(encryptedData);
            }
            
            return encryptedFile;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException | IOException e) {
            Log.e(TAG, "Failed to encrypt patch file", e);
            // 清理失败的加密文件
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
            throw new SecurityException("Failed to encrypt patch file: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加密字节数组（使用 KeyStore 密钥）
     * @param data 原始数据
     * @return 加密后的数据（包含 IV）
     * @throws SecurityException 如果加密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public byte[] encrypt(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        try {
            SecretKey key = getOrCreateEncryptionKey();
            
            // 初始化加密器（不提供 IV，让 Cipher 自动生成）
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            // 加密数据
            byte[] encryptedData = cipher.doFinal(data);
            
            // 获取 Cipher 自动生成的 IV
            byte[] iv = cipher.getIV();
            
            // 组合 IV + 加密数据
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
            
            return result;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "Failed to encrypt data", e);
            throw new SecurityException("Failed to encrypt data: " + e.getMessage(), e);
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 读取文件内容为字节数组
     */
    private byte[] readFileBytes(File file) throws IOException {
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("File too large: " + fileLength);
        }
        
        byte[] data = new byte[(int) fileLength];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int bytesRead = fis.read(data, offset, remaining);
                if (bytesRead == -1) {
                    throw new IOException("Unexpected end of file");
                }
                offset += bytesRead;
                remaining -= bytesRead;
            }
        }
        return data;
    }
    
    /**
     * 获取临时目录
     * @return 临时目录
     */
    public File getTempDirectory() {
        File tempDir = new File(context.getFilesDir(), "patch_gen/temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        // 创建 .nomedia 文件防止媒体扫描
        File noMedia = new File(tempDir, ".nomedia");
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile();
            } catch (IOException e) {
                Log.w(TAG, "Failed to create .nomedia file", e);
            }
        }
        
        return tempDir;
    }
    
    /**
     * 创建临时文件
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀
     * @return 临时文件
     * @throws IOException 如果创建失败
     */
    public File createTempFile(String prefix, String suffix) throws IOException {
        File tempDir = getTempDirectory();
        return File.createTempFile(prefix, suffix, tempDir);
    }
}
