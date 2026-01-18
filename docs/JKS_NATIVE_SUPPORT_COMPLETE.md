# JKS 原生签名支持完成

## 🎉 成就解锁

成功实现了 **JKS keystore 在 Android 上的原生签名支持**！用户现在可以直接使用 JKS 文件进行补丁签名，无需任何转换。

## 技术方案

### 核心类：JksSigner

创建了 `com.orange.update.signer.JksSigner` 类，封装了底层签名实现：

```java
package com.orange.update.signer;

public class JksSigner {
    // 对 ZIP/APK 文件进行 JKS 签名
    public static boolean sign(File inputFile, File outputFile,
                               File keystoreFile, String keystorePassword,
                               String keyAlias, String keyPassword);
    
    // 检查 JKS 签名工具是否可用
    public static boolean isAvailable();
    
    // 获取版本信息
    public static String getVersion();
}
```

### 架构设计

```
用户选择 keystore 文件
    ↓
PatchSigner.loadKeyStore()
    ├─ 检测 .jks → 标记使用 JksSigner
    ├─ 检测 .bks → 使用 BouncyCastle
    └─ 检测 .p12 → 使用 PKCS12
    ↓
PatchSigner.signPatch()
    ├─ JKS → ZipSignerHelper.signZipWithJks()
    │         └─ JksSigner.sign() ✅
    └─ BKS → apksig (v1 + v2)
    ↓
统一的 JAR 签名验证
    ↓
补丁应用成功 🎉
```

## 功能特性

### 1. 自动格式检测
- **JKS 文件** → 自动使用 JksSigner
- **BKS 文件** → 使用 BouncyCastle + apksig
- **PKCS12 文件** → 使用标准 Java

### 2. 无缝集成
- ✅ 完全兼容现有代码
- ✅ 不影响 BKS 方案
- ✅ 统一的错误处理
- ✅ 详细的日志输出

### 3. 用户友好
- ✅ 直接使用 JKS 文件
- ✅ 无需手动转换
- ✅ 自动选择最佳方案
- ✅ 清晰的错误提示

## 使用方法

### 方法 1: 直接使用 JKS（推荐）

1. **配置签名**：
   - 点击「配置签名」
   - 选择 `.jks` 文件
   - 输入密码和别名

2. **生成补丁**：
   - 勾选「APK 签名验证」
   - 点击生成补丁
   - 系统自动使用 JksSigner 签名

3. **日志输出**：
```
I/PatchSigner: 检测到 JKS 文件，尝试使用 ZipSigner...
D/ZipSignerHelper: 使用 JksSigner
D/JksSigner: JKS 签名: patch.zip
I/JksSigner: ✓ JKS 签名成功
I/PatchSigner: ✓ 补丁签名成功 (via ZipSigner)
```

### 方法 2: 使用 BKS（生产环境推荐）

1. **转换 JKS 为 BKS**（一次性操作）：
```bash
keytool -importkeystore \
  -srckeystore app.jks \
  -destkeystore app.bks \
  -srcstoretype JKS \
  -deststoretype BKS \
  -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
  -providerpath /path/to/bcprov.jar
```

2. **使用 BKS 文件**：
   - 配置时选择 `.bks` 文件
   - 系统自动使用 BouncyCastle + apksig

## 技术细节

### 底层实现

JksSigner 使用反射调用底层签名实现：

```java
// 使用反射调用签名方法
Class<?> signerClass = Class.forName("XiaoMozi.签名");
Method signMethod = signerClass.getMethod("签名", 
    String.class, String.class, String.class, 
    String.class, String.class, String.class);

// 参数顺序：密钥路径, 密钥密码, 别名, 别名密码, APK路径, 输出APK路径
Boolean result = (Boolean) signMethod.invoke(null,
    keystoreFile.getAbsolutePath(),
    keystorePassword,
    keyAlias,
    keyPassword,
    inputFile.getAbsolutePath(),
    outputFile.getAbsolutePath()
);
```

### 依赖关系

```
JksSigner (com.orange.update.signer)
    ↓ (反射调用)
XiaoMozi.签名 (sign_extracted/classes.jar)
    ↓ (使用)
SpongyCastle + kellinwood ZipSigner
```

### 签名验证

所有签名方式（JKS/BKS）都使用统一的 JAR 签名验证：

```java
// 使用 JarFile 验证 v1 签名
java.util.jar.JarFile jarFile = new java.util.jar.JarFile(patchFile, true);

// 读取所有条目以触发签名验证
java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
while (entries.hasMoreElements()) {
    java.util.jar.JarEntry entry = entries.nextElement();
    // 检查签名
    java.security.cert.Certificate[] certs = entry.getCertificates();
}
```

## 优势对比

| 特性 | JKS + JksSigner | BKS + apksig |
|------|----------------|--------------|
| 用户体验 | ⭐⭐⭐⭐⭐ 直接使用 | ⭐⭐⭐⭐ 需要转换 |
| 签名方案 | v1 (JAR) | v1 + v2 |
| Android 兼容 | ⭐⭐⭐⭐⭐ 完美支持 | ⭐⭐⭐⭐⭐ 官方格式 |
| 维护性 | ⭐⭐⭐⭐ 稳定 | ⭐⭐⭐⭐⭐ 官方库 |
| 性能 | ⭐⭐⭐⭐ 快速 | ⭐⭐⭐⭐⭐ 更快 |
| 推荐场景 | 开发测试 | 生产环境 |

## 推荐使用场景

### 使用 JKS + JksSigner
- ✅ 已有 JKS 文件，不想转换
- ✅ 快速测试和开发
- ✅ 临时使用
- ✅ 简化工作流程

### 使用 BKS + apksig（推荐生产环境）
- ✅ 生产环境
- ✅ 长期维护的项目
- ✅ 需要 v2 签名的场景
- ✅ 追求最佳性能

## 文件清单

### 新增文件
- `update/src/main/java/com/orange/update/signer/JksSigner.java` - JKS 签名核心类
- `docs/JKS_NATIVE_SUPPORT_COMPLETE.md` - 本文档

### 修改文件
- `update/src/main/java/com/orange/update/ZipSignerHelper.java` - 重构使用 JksSigner
- `update/src/main/java/com/orange/update/PatchSigner.java` - 集成 JKS 支持

### 删除文件
- `update/src/main/java/com/orange/update/XiaoMoziSigner.java` - 已被 JksSigner 替代

### 依赖文件
- `update/libs/zipsigner.jar` - 签名库（包含底层实现）

## 测试验证

### 测试步骤
1. ✅ 使用 JKS 文件配置签名
2. ✅ 生成补丁并签名
3. ✅ 验证补丁签名
4. ✅ 验证签名与应用匹配
5. ✅ 应用补丁成功

### 测试结果
```
✓ JKS keystore 检测成功
✓ JksSigner 可用
✓ JKS 签名成功
✓ JAR 签名验证通过
✓ 补丁签名与应用签名匹配
✓ 补丁应用成功
```

## 故障排除

### 问题 1: JksSigner 不可用
**症状**: 日志显示 "✗ JKS 签名工具不可用"

**解决方案**:
1. 检查 `update/libs/zipsigner.jar` 是否存在
2. 检查 `update/build.gradle` 中的依赖配置
3. 清理并重新编译: `./gradlew clean :app:assembleDebug`

### 问题 2: JKS 签名失败
**症状**: "JKS 签名失败"

**解决方案**:
1. 检查 keystore 密码是否正确
2. 检查密钥别名是否正确
3. 检查 JKS 文件是否损坏
4. 尝试使用 BKS 格式

### 问题 3: 签名验证失败
**症状**: "补丁签名与应用签名不匹配"

**解决方案**:
1. 确保使用与应用相同的 keystore
2. 确保密钥别名正确
3. 检查应用是否使用了不同的签名

## 版本历史

### v1.0.0 (2026-01-18)
- ✅ 初始版本
- ✅ 实现 JKS 原生签名支持
- ✅ 创建 JksSigner 类
- ✅ 重构为自有包名
- ✅ 完整的错误处理和日志

## 总结

🎉 **成功实现了 JKS keystore 在 Android 上的原生签名支持！**

用户现在可以：
1. ✅ 直接使用 JKS 文件进行签名
2. ✅ 无需任何手动转换
3. ✅ 享受简化的工作流程
4. ✅ 保持与 BKS 方案的完全兼容

这是一个重要的里程碑，极大地提升了用户体验！🚀

---

**作者**: Orange Update Team  
**日期**: 2026-01-18  
**版本**: 1.0.0
