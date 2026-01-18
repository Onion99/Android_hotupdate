package com.orange.update;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * APK 签名验证器
 * 
 * 参考 Tinker 的 ShareSecurityCheck 实现
 * 验证补丁文件的签名是否与应用签名一致
 * 
 * 工作原理：
 * 1. 获取应用的签名证书 MD5
 * 2. 读取补丁 ZIP 文件中的签名证书
 * 3. 比对两者的 MD5 是否一致
 * 
 * 优点：
 * - 无需管理密码
 * - 无需加密存储
 * - 防篡改能力强（攻击者无法伪造签名）
 * - 启动速度快（无需解密）
 */
public class ApkSignatureVerifier {
    
    private static final String TAG = "ApkSignatureVerifier";
    
    /**
     * 应用签名的 MD5（缓存，避免重复计算）
     */
    private static String sAppSignatureMd5 = null;
    
    /**
     * 应用签名的 SHA1（缓存，避免重复计算）
     */
    private static String sAppSignatureSha1 = null;
    
    private final Context context;
    
    public ApkSignatureVerifier(Context context) {
        // 在 attachBaseContext 阶段，不要调用 getApplicationContext()，因为它会返回 null
        // 直接使用传入的 context
        this.context = context;
        // 延迟初始化，避免在 attachBaseContext 阶段出现 NPE
        // initAppSignature() 将在第一次调用 verifyPatchSignature() 时执行
    }
    
    /**
     * 初始化应用签名 MD5 和 SHA1
     */
    @SuppressLint("PackageManagerGetSignatures")
    private void initAppSignature() {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            
            if (packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                throw new SecurityException("Application has no signature");
            }
            
            // 计算应用签名的 MD5 和 SHA1
            byte[] signatureBytes = packageInfo.signatures[0].toByteArray();
            sAppSignatureMd5 = getMD5(signatureBytes);
            sAppSignatureSha1 = getSHA1(signatureBytes);
            
            if (sAppSignatureMd5 == null || sAppSignatureMd5.isEmpty()) {
                throw new SecurityException("Failed to calculate signature MD5");
            }
            
            if (sAppSignatureSha1 == null || sAppSignatureSha1.isEmpty()) {
                throw new SecurityException("Failed to calculate signature SHA1");
            }
            
            Log.i(TAG, "Application signature MD5: " + sAppSignatureMd5);
            Log.i(TAG, "Application signature SHA1: " + sAppSignatureSha1);
            
        } catch (Exception e) {
            throw new SecurityException("Failed to initialize app signature", e);
        }
    }
    
    /**
     * 验证补丁文件的签名（使用 JarFile，参考 Tinker 的实现）
     * 
     * Tinker 的做法：
     * 1. 使用 JarFile 读取补丁文件
     * 2. 遍历所有条目，调用 getCertificates() 获取证书
     * 3. JarFile 会自动验证签名，如果文件被篡改，getCertificates() 返回 null
     * 4. 比对证书的 MD5/SHA1 与应用签名是否一致
     * 
     * 关键点：
     * - 必须先读取条目内容，getCertificates() 才会返回证书
     * - 如果签名无效或文件被篡改，getCertificates() 返回 null
     * - 这提供了完整的防篡改保护
     * 
     * @param patchFile 补丁文件（ZIP 格式，带 JAR 签名）
     * @return true 如果签名验证通过，false 否则
     */
    public boolean verifyPatchSignature(File patchFile) {
        if (patchFile == null || !patchFile.exists() || !patchFile.isFile()) {
            Log.e(TAG, "Patch file is invalid");
            return false;
        }
        
        // 确保应用签名已初始化
        if (sAppSignatureMd5 == null || sAppSignatureSha1 == null) {
            try {
                initAppSignature();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize app signature", e);
                return false;
            }
        }
        
        Log.i(TAG, "Verifying patch signature: " + patchFile.getName());
        Log.i(TAG, "App signature MD5: " + sAppSignatureMd5);
        Log.i(TAG, "App signature SHA1: " + sAppSignatureSha1);
        
        JarFile jarFile = null;
        try {
            // 使用 JarFile 读取补丁（会自动验证签名）
            jarFile = new JarFile(patchFile);
            Enumeration<JarEntry> entries = jarFile.entries();
            
            boolean hasValidEntry = false;
            boolean hasValidCertificate = false;
            
            // 遍历所有条目
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                // 跳过目录和 META-INF/ 文件
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                // 读取条目内容（必须读取才能触发签名验证）
                java.io.InputStream is = jarFile.getInputStream(entry);
                byte[] buffer = new byte[8192];
                while (is.read(buffer) > 0) {
                    // 只需要读取，不需要处理内容
                }
                is.close();
                
                // 获取证书（如果签名有效）
                Certificate[] certs = entry.getCertificates();
                
                if (certs == null || certs.length == 0) {
                    // 没有证书或签名无效
                    Log.e(TAG, "❌ Entry has no valid certificate: " + entry.getName());
                    Log.e(TAG, "   This means either:");
                    Log.e(TAG, "   1. The patch is not signed");
                    Log.e(TAG, "   2. The signature is invalid");
                    Log.e(TAG, "   3. The file has been tampered with");
                    return false;
                }
                
                hasValidEntry = true;
                
                // 验证证书是否与应用签名一致
                if (checkCertificate(patchFile, certs)) {
                    hasValidCertificate = true;
                    Log.d(TAG, "✓ Entry verified: " + entry.getName());
                } else {
                    Log.e(TAG, "❌ Certificate mismatch for entry: " + entry.getName());
                    return false;
                }
            }
            
            if (!hasValidEntry) {
                Log.e(TAG, "❌ Patch has no valid entries to verify");
                return false;
            }
            
            if (!hasValidCertificate) {
                Log.e(TAG, "❌ Patch signature verification failed: no valid certificate found");
                return false;
            }
            
            Log.i(TAG, "✅ Patch signature verification passed");
            Log.i(TAG, "   All entries are signed with the same certificate as the app");
            Log.i(TAG, "   Content integrity is guaranteed by JAR signature");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Patch signature verification failed: " + e.getMessage(), e);
            return false;
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close jar file", e);
                }
            }
        }
    }
    
    /**
     * 检查证书是否与应用签名一致
     * 
     * @param patchFile 补丁文件（用于日志）
     * @param certs 证书数组
     * @return true 如果证书匹配，false 否则
     */
    private boolean checkCertificate(File patchFile, Certificate[] certs) {
        if (certs == null || certs.length == 0) {
            return false;
        }
        
        try {
            for (Certificate cert : certs) {
                // 计算证书的 MD5
                byte[] certBytes = cert.getEncoded();
                String certMd5 = getMD5(certBytes);
                
                if (certMd5 == null) {
                    continue;
                }
                
                // 比对 MD5
                if (sAppSignatureMd5.equals(certMd5)) {
                    Log.d(TAG, "Certificate matched: " + certMd5.substring(0, 16) + "...");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check certificate for " + patchFile.getName(), e);
        }
        
        return false;
    }
    
    /**
     * 计算字节数组的 MD5
     * 
     * @param bytes 字节数组
     * @return MD5 字符串（小写十六进制）
     */
    private String getMD5(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashBytes = digest.digest(bytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate MD5", e);
            return null;
        }
    }
    
    /**
     * 计算字节数组的 SHA1
     * 
     * @param bytes 字节数组
     * @return SHA1 字符串（小写十六进制）
     */
    private String getSHA1(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(bytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate SHA1", e);
            return null;
        }
    }
    
    /**
     * 获取应用签名 MD5（用于调试）
     * 
     * @return 应用签名的 MD5
     */
    public String getAppSignatureMd5() {
        return sAppSignatureMd5;
    }
}
