# 补丁签名和验证

## 概述

补丁签名功能使用 `apksig-android` 库对生成的补丁 ZIP 文件进行签名和验证，确保补丁的完整性和来源可信。

## 架构

```
┌─────────────────────────────────────────┐
│  补丁生成（app 模块）                    │
│  ├─ 生成补丁 ZIP                        │
│  ├─ 使用 PatchSigner 签名               │
│  └─ 分发签名后的补丁                    │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│  补丁应用（update 模块）                 │
│  ├─ 下载补丁                            │
│  ├─ 使用 PatchSigner 验证签名           │
│  ├─ 验证签名与应用签名匹配              │
│  └─ 应用补丁                            │
└─────────────────────────────────────────┘
```

## 功能特性

### 1. 补丁签名

- ✅ 支持 JKS 和 PKCS12 keystore
- ✅ 使用 APK 签名方案（v1 + v2）
- ✅ 自动处理证书链
- ✅ 签名后替换原文件

### 2. 补丁验证

- ✅ 验证签名完整性
- ✅ 验证签名者身份
- ✅ 验证签名与应用签名匹配
- ✅ 详细的错误报告

## 使用方法

### 在补丁生成时签名

```java
// 在 MainActivity 或补丁生成器中
PatchSigner signer = new PatchSigner(context);

File signedPatch = signer.signPatch(
    patchFile,              // 补丁文件
    keystoreFile,           // JKS/PKCS12 文件
    keystorePassword,       // Keystore 密码
    keyAlias,              // 密钥别名
    keyPassword            // 密钥密码
);

if (signedPatch != null) {
    Log.i(TAG, "补丁签名成功: " + signedPatch.getAbsolutePath());
} else {
    Log.e(TAG, "补丁签名失败: " + signer.getError());
}
```

### 在补丁应用前验证

```java
// 在 HotUpdateHelper 或补丁应用器中
PatchSigner verifier = new PatchSigner(context);

// 方式 1：仅验证签名有效性
boolean isValid = verifier.verifyPatchSignature(patchFile);
if (isValid) {
    Log.i(TAG, "补丁签名有效");
} else {
    Log.e(TAG, "补丁签名无效: " + verifier.getError());
}

// 方式 2：验证签名且确保与应用签名匹配（推荐）
boolean matches = verifier.verifyPatchSignatureMatchesApp(patchFile);
if (matches) {
    Log.i(TAG, "补丁签名与应用签名匹配，可以安全应用");
    // 应用补丁...
} else {
    Log.e(TAG, "补丁签名不匹配: " + verifier.getError());
    // 拒绝应用补丁
}
```

## 集成到现有流程

### 1. 在 MainActivity 中自动签名

已经集成在 `processSecurityOptions` 方法中：

```java
// 1. APK 签名（使用 apksig 对补丁进行真正的签名）
if (withApkSignature) {
    runOnUiThread(() -> tvStatus.setText("正在签名补丁..."));
    
    PatchSigner patchSigner = new PatchSigner(MainActivity.this);
    File signedPatch = patchSigner.signPatch(
        finalPatchFile,
        selectedKeystoreFile,
        keystorePassword,
        keyAlias,
        keyPassword
    );
    
    if (signedPatch != null && signedPatch.exists()) {
        finalPatchFile = signedPatch;
        Log.d(TAG, "✓ 补丁签名成功");
    } else {
        throw new Exception("补丁签名失败: " + patchSigner.getError());
    }
}
```

### 2. 在 HotUpdateHelper 中验证签名

建议在 `applyPatch` 方法开始时添加：

```java
public boolean applyPatch(File patchFile, String password) {
    // 1. 验证补丁签名（如果启用了签名验证）
    if (isSignatureVerificationEnabled()) {
        PatchSigner verifier = new PatchSigner(context);
        if (!verifier.verifyPatchSignatureMatchesApp(patchFile)) {
            Log.e(TAG, "补丁签名验证失败: " + verifier.getError());
            return false;
        }
        Log.i(TAG, "✓ 补丁签名验证通过");
    }
    
    // 2. 继续原有的补丁应用流程...
    // ...
}
```

## 安全最佳实践

### 1. 强制签名验证

在生产环境中，建议强制要求补丁签名：

```java
// 在 HotUpdateHelper 中
private static final boolean REQUIRE_SIGNATURE = true;  // 生产环境设为 true

public boolean applyPatch(File patchFile, String password) {
    if (REQUIRE_SIGNATURE) {
        PatchSigner verifier = new PatchSigner(context);
        if (!verifier.verifyPatchSignatureMatchesApp(patchFile)) {
            throw new SecurityException("补丁签名验证失败");
        }
    }
    // ...
}
```

### 2. 签名与应用签名匹配

始终使用 `verifyPatchSignatureMatchesApp` 而不是 `verifyPatchSignature`：

```java
// ✅ 推荐：验证签名且确保与应用签名匹配
boolean safe = verifier.verifyPatchSignatureMatchesApp(patchFile);

// ❌ 不推荐：仅验证签名有效性（可能被其他签名的补丁欺骗）
boolean valid = verifier.verifyPatchSignature(patchFile);
```

### 3. 使用相同的签名密钥

确保：
- 补丁签名使用的密钥 = 应用签名使用的密钥
- 开发环境和生产环境使用不同的密钥
- 妥善保管签名密钥

## 签名方案说明

### APK 签名方案

补丁 ZIP 文件使用 APK 签名方案进行签名：

| 方案 | Android 版本 | 是否启用 | 说明 |
|------|-------------|---------|------|
| v1 (JAR) | 所有版本 | ✅ 是 | 基于 JAR 签名，兼容性最好 |
| v2 | 7.0+ | ✅ 是 | 更快的验证速度，更强的安全性 |
| v3 | 9.0+ | ❌ 否 | 对 ZIP 文件可能不适用 |
| v4 | 11.0+ | ❌ 否 | 不需要 |

### 为什么使用 APK 签名方案？

1. **成熟稳定**：Android 官方签名方案，经过充分测试
2. **工具支持**：apksig 库提供完整的签名和验证功能
3. **安全性高**：支持多种签名方案，防止篡改
4. **易于验证**：可以直接验证签名与应用签名是否匹配

## 性能影响

### 签名性能

- 小补丁（< 1MB）：< 100ms
- 中等补丁（1-10MB）：100-500ms
- 大补丁（> 10MB）：500ms-2s

### 验证性能

- 签名验证：< 50ms
- 签名匹配验证：< 100ms

## 故障排查

### 1. 签名失败

**错误**：`签名失败: 私钥不存在`

**解决**：
- 检查 keystore 文件路径是否正确
- 检查密钥别名是否正确
- 检查密钥密码是否正确

### 2. 验证失败

**错误**：`签名验证失败: V1: No JAR signature`

**解决**：
- 确保补丁文件已签名
- 检查补丁文件是否损坏
- 重新生成并签名补丁

### 3. 签名不匹配

**错误**：`补丁签名与应用签名不匹配`

**解决**：
- 确保使用相同的 keystore 签名补丁和应用
- 检查是否使用了正确的密钥别名
- 在开发环境中，确保 debug 和 release 使用相同的签名

## 示例代码

完整的补丁生成和应用流程：

```java
// ========== 补丁生成端 ==========
public void generateAndSignPatch() {
    // 1. 生成补丁
    File patchFile = patchGenerator.generate(baseApk, newApk);
    
    // 2. 签名补丁
    PatchSigner signer = new PatchSigner(context);
    File signedPatch = signer.signPatch(
        patchFile,
        new File("/path/to/keystore.jks"),
        "keystorePassword",
        "keyAlias",
        "keyPassword"
    );
    
    if (signedPatch != null) {
        // 3. 上传到服务器
        uploadPatch(signedPatch);
    }
}

// ========== 补丁应用端 ==========
public boolean downloadAndApplyPatch(String patchUrl) {
    // 1. 下载补丁
    File patchFile = downloadPatch(patchUrl);
    
    // 2. 验证签名
    PatchSigner verifier = new PatchSigner(context);
    if (!verifier.verifyPatchSignatureMatchesApp(patchFile)) {
        Log.e(TAG, "补丁签名验证失败，拒绝应用");
        return false;
    }
    
    // 3. 应用补丁
    HotUpdateHelper helper = new HotUpdateHelper(context);
    return helper.applyPatch(patchFile, null);
}
```

## 参考资料

- [apksig-android GitHub](https://github.com/MuntashirAkon/apksig-android)
- [Android APK 签名方案](https://source.android.com/docs/security/features/apksigning)
- [APK Signature Scheme v2](https://source.android.com/docs/security/features/apksigning/v2)
