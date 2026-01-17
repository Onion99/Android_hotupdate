# ZIP 密码保护 - 存储加密机制

## 问题描述

用户报告：使用 ZIP 密码保护生成的补丁，应用后打开隐私目录 `applied` 的文件可以看到无加密的文件。

## 设计目标

ZIP 密码保护的补丁应该在 `applied` 目录保持加密状态，防止用户直接访问和修改补丁内容。

## 解决方案

### 1. 补丁应用流程

#### 使用派生密码（默认）

当用户勾选「🔑 ZIP 密码保护」但不输入自定义密码时：

1. **生成时**：使用从应用签名派生的密码加密 ZIP
2. **应用时**：
   - 检测到 ZIP 加密但没有 `.zippwd` 标记文件
   - **直接保存加密文件**到 `applied` 目录（不解密）
   - 保存标记 `is_zip_password_protected = true`
3. **启动时**：
   - 检测到 `is_zip_password_protected = true`
   - 自动使用派生密码解密到临时文件
   - 加载临时文件
   - 清理临时文件

#### 使用自定义密码

当用户勾选「🔑 ZIP 密码保护」并输入自定义密码时：

1. **生成时**：
   - 使用用户输入的密码加密 ZIP
   - 生成 `.zippwd` 标记文件
2. **应用时**：
   - 检测到 ZIP 加密且有 `.zippwd` 标记文件
   - 弹出密码输入对话框
   - 用户输入密码后验证
   - **保存加密文件**到 `applied` 目录（不解密）
   - 保存标记 `is_zip_password_protected = true`
   - 保存自定义密码 `custom_zip_password`
3. **启动时**：
   - 检测到 `is_zip_password_protected = true`
   - 读取保存的 `custom_zip_password`
   - 使用自定义密码解密到临时文件
   - 加载临时文件
   - 清理临时文件

### 2. 关键代码修改

#### HotUpdateHelper.applyPatch()

```java
// 检查 ZIP 密码加密
if (zipPasswordManager.isEncrypted(patchFile)) {
    // 检查是否有自定义密码标记
    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
    boolean hasCustomPassword = zipPasswordFile.exists();
    
    if (hasCustomPassword) {
        // 需要用户输入密码
        callback.onZipPasswordRequired(patchFile);
        return; // 等待用户输入
    }
    
    // 使用派生密码，直接保存加密文件
    Log.d(TAG, "使用派生密码，补丁将以加密状态保存");
}

// 继续应用补丁（保存加密文件）
applyPatchInternal(patchFile, patchFile, callback);
```

#### HotUpdateHelper.applyPatchWithZipPassword()

```java
// 验证用户输入的密码
boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);

if (!passwordValid) {
    callback.onError("⚠️ ZIP 密码验证失败！");
    return;
}

// 保存自定义密码（用于启动时解密）
prefs.edit()
    .putBoolean("is_zip_password_protected", true)
    .putString("custom_zip_password", zipPassword)
    .apply();

// 保存加密文件到 applied 目录
applyPatchInternal(patchFile, patchFile, callback);
```

#### HotUpdateHelper.applyPatchInternal()

```java
// 判断是否是 ZIP 密码保护的
boolean isZipPasswordProtected = isZipPasswordProtected(originalPatchFile);

// 保存文件：ZIP 密码保护的保存加密文件
File fileToSave = isZipPasswordProtected ? originalPatchFile : actualPatchFile;
byte[] patchData = readFileToBytes(fileToSave);

// 保存到 applied 目录
storage.savePatchFile(patchInfo.getPatchId(), patchData);

// 保存标记
if (isZipPasswordProtected) {
    prefs.edit().putBoolean("is_zip_password_protected", true).apply();
    Log.d(TAG, "✓ 补丁已保存为加密状态到 applied 目录");
}
```

#### PatchApplication.loadPatchIfNeeded()

```java
// 检查是否是 ZIP 密码保护的
if (isZipPasswordProtected(appliedFile)) {
    // 获取保存的自定义密码（如果有）
    String customPassword = prefs.getString("custom_zip_password", null);
    
    // 自动解密到临时文件
    actualPatchFile = decryptZipPatchOnLoad(appliedFile, customPassword);
    
    if (actualPatchFile == null) {
        Log.e(TAG, "Failed to decrypt ZIP password protected patch");
        return;
    }
    
    Log.d(TAG, "✓ ZIP password protected patch decrypted");
}

// 加载解密后的临时文件
String patchPath = actualPatchFile.getAbsolutePath();
DexPatcher.injectPatchDex(this, patchPath);
ResourcePatcher.loadPatchResources(this, patchPath);
```

### 3. 安全优势

#### 与 AES 加密的区别

| 特性 | ZIP 密码保护 | AES 加密 |
|------|-------------|---------|
| **加密位置** | ZIP 文件本身 | 外层包装 |
| **存储状态** | applied 目录保持加密 | applied 目录解密存储 |
| **防篡改** | ✓ 密码错误无法解压 | ✓ 密钥错误无法解密 |
| **防查看** | ✓ 无法直接查看内容 | ✗ 解密后可查看 |
| **防修改** | ✓ 修改后密码验证失败 | ✗ 解密后可修改 |
| **性能** | 解压时验证 | 解密时验证 |

#### 三重安全防护

1. **RSA-2048 签名**：防止补丁被篡改（传输安全）
2. **ZIP 密码保护**：防止补丁内容被查看和修改（存储安全）
3. **AES-256-GCM 加密**：额外的加密层（传输安全）

### 4. 测试步骤

#### 测试 1：派生密码（默认）

1. 生成补丁时勾选「🔑 ZIP 密码保护」，不输入密码
2. 应用补丁
3. 检查 `data/data/com.orange.update/files/update/applied/current_patch.zip`
4. **预期**：文件是加密的，无法直接解压
5. 重启应用
6. **预期**：补丁自动加载成功

#### 测试 2：自定义密码

1. 生成补丁时勾选「🔑 ZIP 密码保护」，输入密码 `test123`
2. 应用补丁时弹出密码输入对话框
3. 输入密码 `test123`
4. 检查 `data/data/com.orange.update/files/update/applied/current_patch.zip`
5. **预期**：文件是加密的，无法直接解压
6. 重启应用
7. **预期**：补丁自动加载成功（使用保存的密码）

#### 测试 3：密码错误

1. 生成补丁时使用密码 `test123`
2. 应用补丁时输入错误密码 `wrong`
3. **预期**：提示「⚠️ ZIP 密码验证失败！」
4. 重新输入正确密码 `test123`
5. **预期**：应用成功

### 5. 文件结构

```
/data/data/com.orange.update/files/update/
├── applied/
│   └── current_patch.zip          # 加密状态（ZIP 密码保护）
├── patches/
│   └── patch_xxx.enc              # 加密状态（AES 加密，可选）
└── merged_resources.apk           # 合并后的资源（启动时生成）

SharedPreferences (patch_storage_prefs):
- is_zip_password_protected: true
- custom_zip_password: "test123" (如果有)
- applied_patch_id: "patch_xxx"
- applied_patch_hash: "sha256..."
```

## 总结

修复后，ZIP 密码保护的补丁在 `applied` 目录保持加密状态，只有在应用启动时才会临时解密到内存中加载，加载完成后立即清理临时文件。这样可以有效防止用户直接访问和修改补丁内容，提供了更强的安全保护。
