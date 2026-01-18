# apksig-android 集成方案

## 概述

由于 JKS 加密算法的复杂性和 Android 平台的限制，我们采用以下方案：

- **Android 应用**：使用 `apksig-android` 库（Google 官方实现）
- **JVM 环境**：继续使用 BouncyCastle（现有方案）

## 为什么选择 apksig-android？

1. **官方实现**：Google 官方 apksig 库的 Android 移植版
2. **完整支持**：支持 JAR 签名和 APK Signature Scheme v2/v3/v4
3. **原生 JKS 支持**：无需自己解析 JKS 文件
4. **维护良好**：基于 Android 官方代码，持续更新

## 实施步骤

### 1. 在 app 模块添加依赖

```gradle
// app/build.gradle
dependencies {
    implementation 'com.github.MuntashirAkon:apksig-android:4.4.0'
}
```

### 2. 在 settings.gradle 添加 JitPack 仓库

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 3. 使用示例

```java
import com.android.apksig.ApkSigner;
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class ApkSignerExample {
    public void signApk(File inputApk, File outputApk, 
                       File keystoreFile, String keystorePassword,
                       String keyAlias, String keyPassword) throws Exception {
        
        // 1. 加载 keystore (支持 JKS 和 PKCS12)
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }
        
        // 2. 获取私钥和证书链
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(
            keyAlias, keyPassword.toCharArray());
        
        java.security.cert.Certificate[] certChain = 
            keyStore.getCertificateChain(keyAlias);
        
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate cert : certChain) {
            certs.add((X509Certificate) cert);
        }
        
        // 3. 创建签名配置
        ApkSigner.SignerConfig signerConfig = 
            new ApkSigner.SignerConfig.Builder(keyAlias, privateKey, certs)
                .build();
        
        // 4. 配置并执行签名
        ApkSigner.Builder signerBuilder = new ApkSigner.Builder(
            Collections.singletonList(signerConfig));
        
        signerBuilder.setInputApk(inputApk);
        signerBuilder.setOutputApk(outputApk);
        signerBuilder.setMinSdkVersion(21);  // Android 5.0+
        
        // 启用签名方案
        signerBuilder.setV1SigningEnabled(true);   // JAR signing
        signerBuilder.setV2SigningEnabled(true);   // v2 (Android 7.0+)
        signerBuilder.setV3SigningEnabled(true);   // v3 (Android 9.0+)
        
        ApkSigner signer = signerBuilder.build();
        signer.sign();
    }
}
```

## 优势

### vs C++ Native 实现

| 特性 | apksig-android | C++ Native |
|------|----------------|------------|
| JKS 解密 | ✅ 原生支持 | ❌ 需要重新实现复杂算法 |
| 签名方案 | ✅ v1/v2/v3/v4 全支持 | ⚠️ 需要逐个实现 |
| 维护成本 | ✅ 低（官方维护） | ❌ 高（自己维护） |
| 代码量 | ✅ 少（几十行） | ❌ 多（数千行） |
| 可靠性 | ✅ 高（官方实现） | ⚠️ 需要大量测试 |

### vs BouncyCastle

| 特性 | apksig-android | BouncyCastle |
|------|----------------|--------------|
| Android 支持 | ✅ 专为 Android 设计 | ⚠️ 需要额外配置 |
| APK v2/v3 | ✅ 原生支持 | ❌ 不支持 |
| JKS 支持 | ✅ 完整支持 | ⚠️ Android 上受限 |
| 文件大小 | ✅ 较小 | ⚠️ 较大 |

## 注意事项

1. **平台限制**：apksig-android 只能在 Android 应用中使用，不能在纯 Java 模块中使用
2. **JKS vs PKCS12**：建议使用 PKCS12 格式（.p12），这是现代标准
3. **最小 SDK**：根据目标 Android 版本选择合适的签名方案

## 迁移建议

### 当前架构

```
patch-core (Java)
  └── BouncyCastle 签名 (JVM 环境)

patch-native (C++)
  └── JKS 解析 (未完成)

app (Android)
  └── 使用 patch-core
```

### 推荐架构

```
patch-core (Java)
  └── BouncyCastle 签名 (JVM 环境 - 保留)

app (Android)
  ├── apksig-android 签名 (Android 环境 - 新增)
  └── 根据环境选择签名方案
```

## 实施优先级

1. ✅ **高优先级**：在 app 模块集成 apksig-android
2. ⚠️ **中优先级**：保留 BouncyCastle 作为 JVM 环境备选
3. ❌ **低优先级**：暂停 C++ Native JKS 实现（投入产出比低）

## 参考资料

- [apksig-android GitHub](https://github.com/MuntashirAkon/apksig-android)
- [Google apksig 官方文档](https://android.googlesource.com/platform/tools/apksig/)
- [Android APK 签名方案](https://source.android.com/docs/security/features/apksigning)
