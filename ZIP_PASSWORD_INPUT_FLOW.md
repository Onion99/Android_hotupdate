# ZIP 密码输入流程说明

## 概述

实现了应用补丁时自动检测和输入 ZIP 密码的功能，支持：
- 自动检测是否使用自定义 ZIP 密码
- 弹窗让用户输入密码
- 密码错误时提示重新输入
- 向后兼容派生密码

## 实现细节

### 1. 密码提示文件（.zippwd）

生成补丁时，如果用户输入了自定义 ZIP 密码，会创建一个 `.zippwd` 文件：

```java
// 在 MainActivity.processSecurityOptions() 中
if (isCustomPassword) {
    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
    FileOutputStream fos = new FileOutputStream(zipPasswordFile);
    fos.write(("ZIP 密码提示: 使用自定义密码\n" + 
              "注意: 应用补丁时需要输入相同密码\n" +
              "密码长度: " + finalZipPassword.length() + " 字符").getBytes("UTF-8"));
    fos.close();
}
```

### 2. HotUpdateHelper 回调接口

添加了 `onZipPasswordRequired` 回调方法：

```java
public interface Callback {
    void onProgress(int percent, String message);
    void onSuccess(PatchResult result);
    void onError(String message);
    
    // 新增：需要 ZIP 密码回调
    default void onZipPasswordRequired(File patchFile) {
        onError("补丁需要 ZIP 密码，但未提供密码输入接口");
    }
}
```

### 3. 应用补丁流程

#### 3.1 检测 ZIP 密码类型

```java
// 在 HotUpdateHelper.applyPatch() 中
if (zipPasswordManager.isEncrypted(patchFile)) {
    // 检查是否有密码提示文件（.zippwd）
    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
    boolean hasCustomPassword = zipPasswordFile.exists();
    
    if (hasCustomPassword) {
        // 通知 UI 需要用户输入密码
        if (callback != null) {
            callback.onZipPasswordRequired(patchFile);
        }
        return; // 等待用户输入密码
    }
    
    // 使用派生密码
    String zipPassword = zipPasswordManager.getZipPassword();
    actualPatchFile = decryptZipPatch(patchFile, zipPassword, callback);
}
```

#### 3.2 用户输入密码后继续应用

```java
// 新增方法：applyPatchWithZipPassword
public void applyPatchWithZipPassword(File patchFile, String zipPassword, Callback callback) {
    // 1. 验证密码
    File actualPatchFile = decryptZipPatch(patchFile, zipPassword, callback);
    
    // 2. 继续应用补丁
    if (actualPatchFile != null) {
        applyPatchInternal(actualPatchFile, patchFile, callback);
    }
}
```

### 4. MainActivity 实现

#### 4.1 实现回调接口

```java
hotUpdateHelper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onZipPasswordRequired(File patchFileToDecrypt) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            setButtonsEnabled(true);
            // 显示 ZIP 密码输入对话框
            showZipPasswordDialog(patchFileToDecrypt);
        });
    }
    
    // ... 其他回调方法
});
```

#### 4.2 显示密码输入对话框

```java
private void showZipPasswordDialog(File patchFile) {
    // 创建密码输入对话框
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setTitle("🔑 ZIP 密码验证")
        .setView(layout)
        .setPositiveButton("验证并应用", (d, w) -> {
            String zipPassword = etZipPassword.getText().toString().trim();
            applyPatchWithZipPassword(patchFile, zipPassword);
        })
        .setNegativeButton("取消", null)
        .setCancelable(false);
    
    builder.show();
}
```

#### 4.3 使用密码应用补丁

```java
private void applyPatchWithZipPassword(File patchFile, String zipPassword) {
    hotUpdateHelper.applyPatchWithZipPassword(patchFile, zipPassword, new HotUpdateHelper.Callback() {
        @Override
        public void onError(String message) {
            // 如果是密码错误，提示用户重新输入
            if (message.contains("密码") || message.contains("验证失败")) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("⚠️ 密码错误")
                    .setMessage(message + "\n\n是否重新输入密码？")
                    .setPositiveButton("重新输入", (d, w) -> {
                        showZipPasswordDialog(patchFile);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        }
        
        // ... 其他回调方法
    });
}
```

## 使用流程

### 生成补丁

1. 选择基准 APK 和新 APK
2. 点击「生成补丁」
3. 勾选「🔑 ZIP 密码保护」
4. 输入自定义密码（或留空使用派生密码）
5. 点击「生成」

**结果**：
- 生成加密的 ZIP 补丁文件（如 `patch_1234567890.zip`）
- 如果使用自定义密码，生成密码提示文件（如 `patch_1234567890.zip.zippwd`）

### 应用补丁

#### 场景 1：使用派生密码的补丁

1. 选择补丁文件
2. 点击「应用补丁」
3. **自动验证派生密码**
4. 应用成功

#### 场景 2：使用自定义密码的补丁

1. 选择补丁文件（包含 `.zippwd` 文件）
2. 点击「应用补丁」
3. **弹出密码输入对话框**
4. 输入生成时设置的密码
5. 点击「验证并应用」
6. 应用成功

#### 场景 3：密码错误

1. 输入错误的密码
2. 显示错误提示：「⚠️ ZIP 密码验证失败！密码错误或补丁已被篡改。」
3. 弹出对话框：「是否重新输入密码？」
4. 点击「重新输入」，重新显示密码输入对话框

## 安全特性

### 三重防护

1. **RSA-2048 签名**（防篡改）
   - 验证补丁完整性
   - 防止补丁被修改

2. **ZIP 密码保护**（AES-256，防篡改）
   - 加密 ZIP 文件
   - 密码错误时无法解密
   - 支持自定义密码或派生密码

3. **AES-256-GCM 加密**（存储保护）
   - 加密补丁内容
   - 保护敏感数据

### 密码派生策略

派生密码从应用签名自动生成，具有以下特点：
- **设备绑定**：每个应用的签名唯一
- **自动生成**：无需用户记忆
- **安全性高**：使用 SHA-256 哈希

```java
public String getZipPassword() {
    // 1. 获取应用签名
    String signature = getAppSignature();
    
    // 2. 使用 SHA-256 派生密码
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(signature.getBytes("UTF-8"));
    
    // 3. 取前 16 个字符作为密码
    String password = hexString.toString().substring(0, 16);
    
    return password;
}
```

## 向后兼容

- **旧版本补丁**：没有 `.zippwd` 文件，自动使用派生密码
- **未加密补丁**：跳过 ZIP 密码验证
- **外部签名文件**：仍然支持 `.sig` 文件（向后兼容）

## 测试建议

### 测试用例 1：派生密码补丁

1. 生成补丁时勾选「ZIP 密码保护」，不输入密码
2. 应用补丁，应该自动验证成功

### 测试用例 2：自定义密码补丁

1. 生成补丁时勾选「ZIP 密码保护」，输入密码 `test123`
2. 应用补丁，弹出密码输入对话框
3. 输入 `test123`，应该验证成功

### 测试用例 3：密码错误

1. 生成补丁时输入密码 `test123`
2. 应用补丁时输入错误密码 `wrong`
3. 应该显示错误提示，并提供重新输入选项

### 测试用例 4：向后兼容

1. 使用旧版本生成的补丁（没有 ZIP 密码）
2. 应用补丁，应该正常工作

## 文件结构

```
patch_1234567890.zip          # 补丁文件（可能加密）
patch_1234567890.zip.zippwd   # 密码提示文件（仅自定义密码）
patch_1234567890.zip.enc      # AES 加密后的补丁（可选）
patch_1234567890.zip.pwd      # AES 密码提示文件（可选）
```

## 相关文件

- `update/src/main/java/com/orange/update/HotUpdateHelper.java` - 核心逻辑
- `update/src/main/java/com/orange/update/ZipPasswordManager.java` - ZIP 密码管理
- `app/src/main/java/com/orange/update/MainActivity.java` - UI 实现
- `ZIP_PASSWORD_PROTECTION.md` - ZIP 密码保护文档

## Git 提交

```bash
git commit -m "feat: 应用补丁时支持自定义 ZIP 密码输入

- 在 HotUpdateHelper 中添加 onZipPasswordRequired 回调
- 检测 .zippwd 文件判断是否使用自定义密码
- 如果使用自定义密码，弹窗让用户输入
- 如果使用派生密码，自动验证
- 添加 applyPatchWithZipPassword 方法支持密码输入
- 在 MainActivity 中实现密码输入对话框
- 密码错误时提示用户重新输入"
```

提交哈希：`d6b0ce4`
