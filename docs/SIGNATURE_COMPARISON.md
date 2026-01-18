# 补丁签名方案对比

## 现有方案 vs apksig 方案

### 方案对比

| 特性 | JarSigner (现有) | PatchSigner (apksig) |
|------|-----------------|---------------------|
| **实现库** | BouncyCastle | apksig-android |
| **签名格式** | JAR 签名 (v1) | APK 签名 (v1+v2) |
| **验证方式** | JarFile.getCertificates() | ApkVerifier |
| **平台支持** | JVM + Android | 仅 Android |
| **签名速度** | 较慢 | 较快 |
| **验证速度** | 较慢 | 较快 |
| **文件大小** | 较小 | 较大 |
| **兼容性** | 所有 Java 平台 | Android 5.0+ |

### 签名结构对比

#### JarSigner 签名结构

```
patch.zip
├── patch_files/
│   ├── classes.dex
│   └── resources.arsc
└── META-INF/
    ├── MANIFEST.MF      # 文件摘要清单
    ├── CERT.SF          # 签名文件
    └── CERT.RSA         # 签名块（PKCS#7）
```

#### PatchSigner 签名结构

```
patch.zip
├── patch_files/
│   ├── classes.dex
│   └── resources.arsc
└── META-INF/
    ├── MANIFEST.MF      # 文件摘要清单
    ├── CERT.SF          # 签名文件
    ├── CERT.RSA         # v1 签名块
    └── *.RSA            # v2 签名块（额外）
```

## 推荐方案

### 方案 A：双轨制（推荐）

**补丁生成端**：
- JVM 环境：使用 JarSigner（patch-core）
- Android 环境：使用 PatchSigner（update）

**补丁验证端**：
- 统一使用 ApkSignatureVerifier（update）
- 兼容两种签名格式

**优势**：
- ✅ 最大兼容性
- ✅ 灵活性高
- ✅ 性能最优

### 方案 B：统一使用 JarSigner

**所有环境**：
- 使用 JarSigner 签名
- 使用 ApkSignatureVerifier 验证

**优势**：
- ✅ 代码统一
- ✅ 跨平台
- ⚠️ 性能稍慢

### 方案 C：Android 专用 apksig

**仅 Android 环境**：
- 使用 PatchSigner 签名和验证
- JVM 环境不支持

**优势**：
- ✅ 性能最优
- ✅ 官方实现
- ❌ 不支持 JVM

## 当前实现状态

### ✅ 已实现

1. **JarSigner**（patch-core）
   - 完整的 JAR 签名实现
   - 支持 JKS/PKCS12
   - 使用 BouncyCastle
   - 生成标准 PKCS#7 签名

2. **ApkSignatureVerifier**（update）
   - 使用 JarFile.getCertificates()
   - 验证签名完整性
   - 比对应用签名

3. **PatchSigner**（update）
   - 使用 apksig 签名
   - 使用 ApkVerifier 验证
   - 支持 v1+v2 签名

### 🔄 需要整合

1. **统一签名接口**
   - 创建统一的签名接口
   - 根据环境选择实现

2. **验证器整合**
   - ApkSignatureVerifier 支持两种格式
   - 或创建统一的验证接口

## 实施建议

### 阶段 1：保持现状（推荐）

```java
// 补丁生成（MainActivity）
if (withApkSignature) {
    // 使用 PatchSigner（apksig）
    PatchSigner signer = new PatchSigner(context);
    signedPatch = signer.signPatch(...);
}

// 补丁验证（HotUpdateHelper）
ApkSignatureVerifier verifier = new ApkSignatureVerifier(context);
boolean valid = verifier.verifyPatchSignature(patchFile);
```

**优势**：
- 无需修改现有代码
- ApkSignatureVerifier 已经可以验证 JAR 签名
- PatchSigner 提供更快的签名速度

### 阶段 2：创建统一接口（可选）

```java
// 统一签名接口
public interface IPatchSigner {
    File signPatch(File patchFile, SigningConfig config);
    boolean verifyPatch(File patchFile);
}

// JarSigner 实现
public class JarSignerImpl implements IPatchSigner {
    // 使用 BouncyCastle
}

// ApkSigner 实现
public class ApkSignerImpl implements IPatchSigner {
    // 使用 apksig
}

// 工厂类
public class PatchSignerFactory {
    public static IPatchSigner create(Context context) {
        if (isAndroid()) {
            return new ApkSignerImpl(context);
        } else {
            return new JarSignerImpl();
        }
    }
}
```

## 签名读取演示

### 使用 JarFile 读取签名

```java
import java.io.File;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SignatureReader {
    
    public static void readPatchSignature(File patchFile) {
        try (JarFile jarFile = new JarFile(patchFile)) {
            System.out.println("=== 补丁签名信息 ===");
            System.out.println("文件: " + patchFile.getName());
            
            Enumeration<JarEntry> entries = jarFile.entries();
            boolean foundSignature = false;
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                // 跳过目录和 META-INF
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                // 必须读取内容才能获取证书
                InputStream is = jarFile.getInputStream(entry);
                byte[] buffer = new byte[8192];
                while (is.read(buffer) > 0) {
                    // 读取内容
                }
                is.close();
                
                // 获取证书
                Certificate[] certs = entry.getCertificates();
                
                if (certs != null && certs.length > 0) {
                    if (!foundSignature) {
                        System.out.println("\n✓ 补丁已签名");
                        System.out.println("证书数量: " + certs.length);
                        
                        // 打印第一个证书信息
                        if (certs[0] instanceof X509Certificate) {
                            X509Certificate x509 = (X509Certificate) certs[0];
                            System.out.println("\n证书信息:");
                            System.out.println("  主题: " + x509.getSubjectDN());
                            System.out.println("  颁发者: " + x509.getIssuerDN());
                            System.out.println("  序列号: " + x509.getSerialNumber());
                            System.out.println("  有效期: " + x509.getNotBefore() + " 至 " + x509.getNotAfter());
                            System.out.println("  签名算法: " + x509.getSigAlgName());
                            
                            // 计算证书指纹
                            byte[] encoded = x509.getEncoded();
                            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                            byte[] digest = md.digest(encoded);
                            System.out.println("  SHA-256: " + bytesToHex(digest));
                        }
                        
                        foundSignature = true;
                    }
                    break;
                }
            }
            
            if (!foundSignature) {
                System.out.println("\n✗ 补丁未签名");
            }
            
        } catch (Exception e) {
            System.err.println("读取签名失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java SignatureReader <patch-file>");
            return;
        }
        
        File patchFile = new File(args[0]);
        if (!patchFile.exists()) {
            System.err.println("文件不存在: " + patchFile);
            return;
        }
        
        readPatchSignature(patchFile);
    }
}
```

### 使用 apksig 读取签名

```java
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.cert.X509Certificate;

public class ApkSigReader {
    
    public static void readPatchSignature(File patchFile) {
        try {
            System.out.println("=== 补丁签名信息（apksig）===");
            System.out.println("文件: " + patchFile.getName());
            
            ApkVerifier.Builder builder = new ApkVerifier.Builder(patchFile);
            ApkVerifier verifier = builder.build();
            ApkVerifier.Result result = verifier.verify();
            
            if (result.isVerified()) {
                System.out.println("\n✓ 补丁签名有效");
                System.out.println("V1 签名: " + result.isVerifiedUsingV1Scheme());
                System.out.println("V2 签名: " + result.isVerifiedUsingV2Scheme());
                System.out.println("V3 签名: " + result.isVerifiedUsingV3Scheme());
                
                // 获取签名者证书
                if (!result.getSignerCertificates().isEmpty()) {
                    X509Certificate cert = result.getSignerCertificates().get(0);
                    System.out.println("\n证书信息:");
                    System.out.println("  主题: " + cert.getSubjectDN());
                    System.out.println("  颁发者: " + cert.getIssuerDN());
                    System.out.println("  序列号: " + cert.getSerialNumber());
                    System.out.println("  有效期: " + cert.getNotBefore() + " 至 " + cert.getNotAfter());
                    System.out.println("  签名算法: " + cert.getSigAlgName());
                }
            } else {
                System.out.println("\n✗ 补丁签名无效");
                
                // 打印错误信息
                for (ApkVerifier.IssueWithParams error : result.getErrors()) {
                    System.err.println("  错误: " + error);
                }
            }
            
        } catch (Exception e) {
            System.err.println("读取签名失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

## 测试用例

### 测试 1：签名生成

```java
// 使用 JarSigner
SigningConfig config = new SigningConfig.Builder()
    .keystoreFile(new File("keystore.jks"))
    .keystorePassword("password")
    .keyAlias("alias")
    .keyPassword("password")
    .build();

JarSigner jarSigner = new JarSigner(config);
jarSigner.sign(new File("patch.zip"));

// 使用 PatchSigner
PatchSigner patchSigner = new PatchSigner(context);
File signed = patchSigner.signPatch(
    new File("patch.zip"),
    new File("keystore.jks"),
    "password",
    "alias",
    "password"
);
```

### 测试 2：签名验证

```java
// 使用 ApkSignatureVerifier
ApkSignatureVerifier verifier = new ApkSignatureVerifier(context);
boolean valid = verifier.verifyPatchSignature(new File("patch.zip"));

// 使用 PatchSigner
PatchSigner signer = new PatchSigner(context);
boolean valid2 = signer.verifyPatchSignature(new File("patch.zip"));
boolean matches = signer.verifyPatchSignatureMatchesApp(new File("patch.zip"));
```

### 测试 3：签名读取

```java
// 读取 JAR 签名
SignatureReader.readPatchSignature(new File("patch.zip"));

// 读取 APK 签名
ApkSigReader.readPatchSignature(new File("patch.zip"));
```

## 结论

1. **现有的 JarSigner + ApkSignatureVerifier 已经很完善**
2. **PatchSigner 提供了更快的 Android 专用方案**
3. **两种方案可以共存，互不冲突**
4. **ApkSignatureVerifier 可以验证两种签名格式**

建议：
- ✅ 保持现有的 JarSigner 实现
- ✅ 在 Android 应用中使用 PatchSigner 作为可选方案
- ✅ 统一使用 ApkSignatureVerifier 进行验证
