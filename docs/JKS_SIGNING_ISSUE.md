# JKS 签名问题总结

## 问题描述

在 Android 上使用 JKS/PKCS12 keystore 对补丁进行签名时遇到兼容性问题。

## 根本原因

### 1. JKS 格式不兼容
- **Android 不支持 JKS**: `KeyStore.getInstance("JKS")` 抛出 "JKS not found"
- **BouncyCastle 的 JKS 实现是只读的**: 只支持证书条目，不支持私钥条目
- **Native JKS 解析器的限制**: 解密后的数据是 Java 序列化对象，需要 Java 反序列化才能提取私钥

### 2. PKCS12 加密算法不兼容
- **Java 11+ keytool 使用 PBES2**: OID `1.2.840.113549.1.5.12` (PKCS#5 PBES2)
- **Android 不支持 PBES2**: `SecretKeyFactory not available`
- **BouncyCastle 也无法处理**: 虽然支持 PBKDF2，但不支持这个特定的 OID

## 尝试过的解决方案

### ✗ 方案 1: Native JKS 解析器
- 状态：部分成功
- 问题：解密后的数据是 Java 序列化对象，无法在 C++ 中反序列化
- 代码：`patch-native/src/main/cpp/src/jks_parser.cpp`

### ✗ 方案 2: BouncyCastle JKS 提供者
- 状态：失败
- 问题：BouncyCastle 的 JKS 实现是只读的，不支持私钥
- 代码：`update/src/main/java/com/orange/update/PatchSigner.java`

### ✗ 方案 3: PKCS12 转换
- 状态：失败
- 问题：keytool 生成的 PKCS12 使用了 Android 不支持的加密算法
- 命令：`keytool -importkeystore -srckeystore app.jks -destkeystore app.p12 -deststoretype PKCS12`

### ✗ 方案 4: BouncyCastle PKCS12
- 状态：失败
- 问题：BouncyCastle 也无法处理 PBES2 加密的 PKCS12
- 错误：`exception unwrapping private key - java.security.NoSuchAlgorithmException: 1.2.840.113549.1.5.12`

## 可行的解决方案

### ✅ 方案 A: 使用 BKS 格式 (推荐)
BKS (BouncyCastle KeyStore) 是 BouncyCastle 的原生格式，完全兼容 Android。

```bash
# 1. 转换 JKS 为 BKS
keytool -importkeystore \
  -srckeystore app.jks \
  -destkeystore app.bks \
  -srcstoretype JKS \
  -deststoretype BKS \
  -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
  -providerpath /path/to/bcprov.jar \
  -srcstorepass 123123 \
  -deststorepass 123123

# 2. 在代码中使用
KeyStore keyStore = KeyStore.getInstance("BKS", "BC");
keyStore.load(new FileInputStream("app.bks"), "123123".toCharArray());
```

### ✅ 方案 B: 使用 PEM 格式
从 JKS 导出 PEM 格式的私钥和证书，直接使用。

```bash
# 需要先转换为 PKCS12（即使有问题，也可以导出证书）
keytool -exportcert -keystore app.jks -alias smlieapp -file cert.der -storepass 123123

# 转换为 PEM
openssl x509 -inform DER -in cert.der -out cert.pem
```

### ✅ 方案 C: 使用外部签名工具
使用 apksigner 命令行工具在服务器端签名，客户端只验证。

```bash
apksigner sign --ks app.jks --ks-pass pass:123123 patch.zip
```

### ✅ 方案 D: 不签名，只验证应用签名
补丁不单独签名，只验证补丁来源与应用签名是否匹配。

## 推荐方案

**使用 BKS 格式** 是最简单可靠的方案：
1. 完全兼容 Android
2. BouncyCastle 原生支持
3. 支持所有密钥类型
4. 代码改动最小

## 实施步骤

1. 添加 BouncyCastle 依赖（已完成）
2. 转换 JKS 为 BKS 格式
3. 修改 `loadKeyStore()` 方法支持 BKS
4. 测试签名和验证功能

## 相关文件

- `update/src/main/java/com/orange/update/PatchSigner.java` - 签名实现
- `patch-native/src/main/cpp/src/jks_parser.cpp` - Native JKS 解析器
- `update/build.gradle` - BouncyCastle 依赖配置
- `app/smlieapp.jks` - 原始 JKS 文件

## 参考资料

- [BouncyCastle KeyStore Types](https://www.bouncycastle.org/specifications.html)
- [Android KeyStore](https://developer.android.com/training/articles/keystore)
- [PKCS#12 Specification](https://tools.ietf.org/html/rfc7292)
