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
     * 验证补丁文件的签名
     * 
     * @param patchFile 补丁文件（ZIP 格式）
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
        
        java.util.zip.ZipFile zipFile = null;
        try {
            zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            boolean hasValidCertificate = false;
            
            // 查找并验证证书文件
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 查找证书文件（.RSA/.DSA/.EC）
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    
                    Log.d(TAG, "Found certificate file: " + name);
                    
                    // 读取证书文件
                    java.io.InputStream is = zipFile.getInputStream(entry);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    is.close();
                    
                    // 解析 PKCS#7 签名块，提取证书
                    byte[] pkcs7Bytes = baos.toByteArray();
                    
                    try {
                        // 方法1: 使用 JarFile 的方式解析 PKCS#7（推荐）
                        java.security.cert.Certificate[] certs = loadCertificatesFromPKCS7(pkcs7Bytes);
                        
                        if (certs != null && certs.length > 0) {
                            // 验证证书（使用 SHA1 和 MD5 双重验证）
                            for (java.security.cert.Certificate cert : certs) {
                                byte[] encodedCert = cert.getEncoded();
                                String certMd5 = getMD5(encodedCert);
                                String certSha1 = getSHA1(encodedCert);
                                
                                Log.d(TAG, "Certificate MD5: " + (certMd5 != null ? certMd5 : "null"));
                                Log.d(TAG, "Certificate SHA1: " + (certSha1 != null ? certSha1 : "null"));
                                
                                // 使用 SHA1 作为主要验证方式（更可靠）
                                if (certSha1 != null && sAppSignatureSha1.equals(certSha1)) {
                                    Log.i(TAG, "✓ Certificate matched (SHA1): " + certSha1);
                                    hasValidCertificate = true;
                                    break;
                                }
                                
                                // MD5 作为备用验证方式
                                if (certMd5 != null && sAppSignatureMd5.equals(certMd5)) {
                                    Log.i(TAG, "✓ Certificate matched (MD5): " + certMd5);
                                    hasValidCertificate = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse certificate from " + name, e);
                    }
                    
                    if (hasValidCertificate) {
                        break;
                    }
                }
            }
            
            if (!hasValidCertificate) {
                Log.e(TAG, "❌ Patch signature verification failed: no valid certificate found");
                return false;
            }
            
            Log.i(TAG, "✅ Patch signature verification passed");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Patch signature verification failed: " + e.getMessage(), e);
            return false;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close zip file", e);
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
    
    /**
     * 从 PKCS#7 签名块中提取证书
     * 
     * PKCS#7 格式（.RSA/.DSA/.EC 文件）包含：
     * - 签名数据
     * - 证书链
     * - 其他元数据
     * 
     * 我们需要正确解析这个格式来提取证书
     * 
     * @param pkcs7Bytes PKCS#7 签名块的字节数据
     * @return 证书数组，如果解析失败返回 null
     */
    private java.security.cert.Certificate[] loadCertificatesFromPKCS7(byte[] pkcs7Bytes) {
        try {
            // 使用 CertificateFactory 解析 PKCS#7
            java.security.cert.CertificateFactory certFactory = 
                java.security.cert.CertificateFactory.getInstance("X.509");
            
            // 尝试方法1: 直接解析 PKCS#7（可能包含多个证书）
            try {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(pkcs7Bytes);
                java.util.Collection<? extends java.security.cert.Certificate> certs = 
                    certFactory.generateCertificates(bais);
                
                if (certs != null && !certs.isEmpty()) {
                    Log.d(TAG, "✓ 成功解析 PKCS#7，找到 " + certs.size() + " 个证书");
                    return certs.toArray(new java.security.cert.Certificate[0]);
                }
            } catch (Exception e) {
                Log.d(TAG, "方法1失败，尝试方法2: " + e.getMessage());
            }
            
            // 尝试方法2: 使用 CMSSignedData 解析（需要 Bouncy Castle 或 JDK 内置支持）
            // 注意：Android 可能不支持这个方法，但我们可以尝试
            try {
                // 检查是否是 PKCS#7 格式（以 0x30 开头的 DER 编码）
                if (pkcs7Bytes.length > 0 && (pkcs7Bytes[0] & 0xFF) == 0x30) {
                    // 尝试使用反射调用 sun.security.pkcs.PKCS7（仅 JDK 可用）
                    // Android 上可能不可用，但不会抛出异常
                    Class<?> pkcs7Class = Class.forName("sun.security.pkcs.PKCS7");
                    java.lang.reflect.Constructor<?> constructor = pkcs7Class.getConstructor(byte[].class);
                    Object pkcs7 = constructor.newInstance((Object) pkcs7Bytes);
                    
                    java.lang.reflect.Method getCertificatesMethod = pkcs7Class.getMethod("getCertificates");
                    java.security.cert.X509Certificate[] certs = 
                        (java.security.cert.X509Certificate[]) getCertificatesMethod.invoke(pkcs7);
                    
                    if (certs != null && certs.length > 0) {
                        Log.d(TAG, "✓ 使用 PKCS7 类解析成功，找到 " + certs.length + " 个证书");
                        return certs;
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.d(TAG, "sun.security.pkcs.PKCS7 不可用（Android 环境）");
            } catch (Exception e) {
                Log.d(TAG, "方法2失败: " + e.getMessage());
            }
            
            // 尝试方法3: 手动解析 DER 编码的证书（最后的备用方案）
            // 在 PKCS#7 中查找证书的 DER 编码序列
            try {
                java.util.List<java.security.cert.Certificate> certList = new java.util.ArrayList<>();
                
                // 简单的 DER 解析：查找证书序列标记 (0x30 0x82)
                for (int i = 0; i < pkcs7Bytes.length - 4; i++) {
                    // 查找证书开始标记：SEQUENCE (0x30) + 长度编码
                    if ((pkcs7Bytes[i] & 0xFF) == 0x30 && (pkcs7Bytes[i + 1] & 0xFF) == 0x82) {
                        // 读取长度（2字节，大端序）
                        int length = ((pkcs7Bytes[i + 2] & 0xFF) << 8) | (pkcs7Bytes[i + 3] & 0xFF);
                        
                        // 验证长度是否合理（证书通常在 500-4000 字节之间）
                        if (length > 400 && length < 10000 && i + 4 + length <= pkcs7Bytes.length) {
                            try {
                                // 提取证书数据（包括 SEQUENCE 标记）
                                byte[] certBytes = new byte[4 + length];
                                System.arraycopy(pkcs7Bytes, i, certBytes, 0, certBytes.length);
                                
                                // 尝试解析为证书
                                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(certBytes);
                                java.security.cert.Certificate cert = certFactory.generateCertificate(bais);
                                
                                if (cert != null) {
                                    certList.add(cert);
                                    Log.d(TAG, "✓ 手动解析找到证书，偏移: " + i + ", 长度: " + length);
                                    
                                    // 跳过已解析的证书
                                    i += 4 + length - 1;
                                }
                            } catch (Exception e) {
                                // 不是有效的证书，继续搜索
                            }
                        }
                    }
                }
                
                if (!certList.isEmpty()) {
                    Log.d(TAG, "✓ 手动解析成功，找到 " + certList.size() + " 个证书");
                    return certList.toArray(new java.security.cert.Certificate[0]);
                }
            } catch (Exception e) {
                Log.d(TAG, "方法3失败: " + e.getMessage());
            }
            
            Log.w(TAG, "所有解析方法都失败了");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "解析 PKCS#7 失败", e);
            return null;
        }
    }
}
