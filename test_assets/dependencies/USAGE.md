# 依赖库使用指南

## 快速开始

### 在项目中使用这些依赖

#### 方式一：通过 Maven 仓库（推荐）

在 `build.gradle` 中添加：

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'net.lingala.zip4j:zip4j:2.11.5'
    implementation 'com.github.MuntashirAkon:apksig-android:4.4.0'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
}
```

#### 方式二：使用本地文件

如果无法访问 Maven 仓库，可以使用本目录中的文件：

```groovy
dependencies {
    implementation files('path/to/test_assets/dependencies/zip4j-2.11.5.jar')
    implementation files('path/to/test_assets/dependencies/apksig-android-4.4.0.aar')
    implementation files('path/to/test_assets/dependencies/bcprov-jdk18on-1.77.jar')
    implementation files('path/to/test_assets/dependencies/bcpkix-jdk18on-1.77.jar')
    implementation files('path/to/test_assets/dependencies/bcutil-jdk18on-1.77.jar')
}
```

#### 方式三：复制到 libs 目录

1. 将所有 JAR/AAR 文件复制到项目的 `libs` 目录
2. 在 `build.gradle` 中添加：

```groovy
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
}
```

## 各依赖库的用途

### 1. ZIP4J - ZIP 文件处理

**用途**：处理带密码保护的 ZIP 文件

**示例代码**：

```java
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

// 创建带密码的 ZIP 文件
ZipFile zipFile = new ZipFile("patch.zip", "password".toCharArray());
zipFile.addFile(new File("patch.dat"));

// 解压带密码的 ZIP 文件
ZipFile zipFile = new ZipFile("patch.zip", "password".toCharArray());
zipFile.extractAll("/output/path");
```

### 2. apksig-android - APK 签名验证

**用途**：验证 APK 签名，确保补丁未被篡改

**示例代码**：

```java
import com.android.apksig.ApkVerifier;

// 验证 APK 签名
ApkVerifier verifier = new ApkVerifier.Builder(apkFile).build();
ApkVerifier.Result result = verifier.verify();

if (result.isVerified()) {
    Log.i(TAG, "签名验证通过");
} else {
    Log.e(TAG, "签名验证失败");
}
```

### 3. BouncyCastle - 加密和签名

**用途**：JKS keystore 支持、RSA 签名、证书处理

**示例代码**：

```java
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.security.KeyStore;

// 添加 BouncyCastle Provider
Security.addProvider(new BouncyCastleProvider());

// 加载 JKS keystore
KeyStore keyStore = KeyStore.getInstance("JKS");
keyStore.load(new FileInputStream("keystore.jks"), password);

// 获取私钥
PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
```

## 依赖关系

```
update 模块
│
├── zip4j (独立)
│   └── 用于：补丁文件的 ZIP 压缩和解压
│
├── apksig-android (独立)
│   └── 用于：APK 签名验证
│
└── BouncyCastle
    ├── bcprov-jdk18on (基础库)
    │   └── 用于：加密算法、JKS keystore
    │
    └── bcpkix-jdk18on (依赖 bcprov)
        ├── bcutil-jdk18on (传递依赖)
        └── 用于：X.509 证书、签名验证
```

## 常见问题

### Q: 为什么需要这些依赖？

**A:** 
- **zip4j**: Android 原生不支持带密码的 ZIP 文件
- **apksig-android**: 用于验证补丁签名，防止篡改
- **BouncyCastle**: Android 原生不完全支持 JKS keystore

### Q: 可以只使用部分依赖吗？

**A:** 可以，但会失去相应功能：
- 不使用 zip4j：无法处理带密码的补丁
- 不使用 apksig-android：无法验证补丁签名
- 不使用 BouncyCastle：无法使用 JKS keystore 签名

### Q: 这些依赖安全吗？

**A:** 是的，所有依赖都是：
- 开源软件（Apache License 2.0 或 MIT License）
- 广泛使用的成熟库
- 定期更新和维护

### Q: 依赖太大怎么办？

**A:** 
- BouncyCastle 是最大的依赖（约 10 MB）
- 如果不需要 JKS 支持，可以移除 BouncyCastle
- 使用 ProGuard/R8 可以显著减小最终 APK 大小

### Q: 如何更新依赖版本？

**A:** 
1. 修改 `update/build.gradle` 中的版本号
2. 运行 `./gradlew :update:downloadDependencies`
3. 将新文件移动到 `test_assets/dependencies/`

## ProGuard 配置

如果使用 ProGuard/R8，添加以下规则：

```proguard
# ZIP4J
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# apksig-android
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
```

## 许可证信息

所有依赖均为开源软件，可以免费用于商业项目：

| 依赖 | 许可证 | 商业使用 |
|------|--------|----------|
| zip4j | Apache License 2.0 | ✅ 允许 |
| apksig-android | Apache License 2.0 | ✅ 允许 |
| BouncyCastle | MIT License | ✅ 允许 |

## 技术支持

- **项目 Issues**: https://github.com/706412584/Android_hotupdate/issues
- **文档**: [README.md](../../README.md)
- **Email**: 706412584@qq.com

## 更新日志

### 2026-01-20
- 初始版本
- 包含 5 个依赖库
- 总大小约 10.3 MB
