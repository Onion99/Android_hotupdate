# 补丁安全性改进方案

## 问题分析

当前实现存在安全隐患：
1. 补丁解密后以明文形式存储在 `applied/current_patch.zip`
2. 应用启动时直接加载，没有完整性验证
3. 攻击者可能在运行期间篡改已解密的补丁文件

## 改进方案

### 方案 1：每次启动时验证完整性（推荐）

**实现思路**：
1. 在 SharedPreferences 中存储补丁的 SHA-256 哈希值
2. 应用启动时，重新计算文件哈希并验证
3. 如果验证失败，重新从加密存储中解密

**优点**：
- ✅ 防止运行时篡改
- ✅ 实现简单，性能影响小
- ✅ 兼容现有架构

**缺点**：
- ⚠️ 需要额外的哈希计算（约 10-50ms）

**代码示例**：

```java
// PatchStorage.java
public class PatchStorage {
    
    private static final String KEY_APPLIED_PATCH_HASH = "applied_patch_hash";
    
    /**
     * 应用补丁时保存哈希值
     */
    public File decryptPatchToApplied(String patchId) {
        // ... 现有解密逻辑 ...
        
        // 计算并保存哈希
        String hash = calculateSHA256(appliedFile);
        prefs.edit().putString(KEY_APPLIED_PATCH_HASH, hash).apply();
        
        return appliedFile;
    }
    
    /**
     * 验证已应用补丁的完整性
     */
    public boolean verifyAppliedPatchIntegrity() {
        File appliedFile = getAppliedPatchFile();
        if (!appliedFile.exists()) {
            return false;
        }
        
        String savedHash = prefs.getString(KEY_APPLIED_PATCH_HASH, null);
        if (savedHash == null) {
            Log.w(TAG, "No saved hash, patch may be from old version");
            return true; // 向后兼容
        }
        
        String currentHash = calculateSHA256(appliedFile);
        boolean valid = savedHash.equals(currentHash);
        
        if (!valid) {
            Log.e(TAG, "⚠️ Patch integrity check FAILED! File may be tampered!");
            Log.e(TAG, "Expected: " + savedHash);
            Log.e(TAG, "Actual:   " + currentHash);
        }
        
        return valid;
    }
    
    /**
     * 计算文件的 SHA-256 哈希
     */
    private String calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate SHA-256", e);
            return null;
        }
    }
}

// PatchApplication.java
public class PatchApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        loadPatchIfNeeded();
    }
    
    private void loadPatchIfNeeded() {
        try {
            PatchStorage storage = new PatchStorage(this);
            String appliedPatchId = storage.getAppliedPatchId();
            
            if (appliedPatchId == null) {
                return;
            }
            
            // ✅ 验证补丁完整性
            if (!storage.verifyAppliedPatchIntegrity()) {
                Log.e(TAG, "⚠️ Patch integrity check failed! Re-decrypting from secure storage...");
                
                // 重新从加密存储中解密
                File appliedFile = storage.decryptPatchToApplied(appliedPatchId);
                if (appliedFile == null) {
                    Log.e(TAG, "Failed to re-decrypt patch, clearing applied patch");
                    storage.saveAppliedPatchId(null);
                    return;
                }
                
                // 再次验证
                if (!storage.verifyAppliedPatchIntegrity()) {
                    Log.e(TAG, "Re-decrypted patch still invalid, clearing");
                    storage.saveAppliedPatchId(null);
                    return;
                }
            }
            
            // 加载补丁
            File appliedFile = storage.getAppliedPatchFile();
            String patchPath = appliedFile.getAbsolutePath();
            
            // ... 现有加载逻辑 ...
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch", e);
        }
    }
}
```

### 方案 2：使用内存映射 + 即时解密（最安全）

**实现思路**：
1. 不在磁盘上存储解密后的补丁
2. 每次启动时从加密存储中解密到内存
3. 使用内存映射文件或临时文件（应用退出时删除）

**优点**：
- ✅ 最高安全性，磁盘上永远只有加密数据
- ✅ 防止所有形式的磁盘篡改

**缺点**：
- ⚠️ 每次启动都需要解密（性能影响较大）
- ⚠️ 实现复杂度高
- ⚠️ 需要修改现有架构

**代码示例**：

```java
// PatchStorage.java
public class PatchStorage {
    
    /**
     * 解密补丁到临时内存文件（应用退出时自动删除）
     */
    public File decryptPatchToMemory(String patchId) {
        File patchFile = getPatchFile(patchId);
        if (!patchFile.exists()) {
            return null;
        }
        
        try {
            // 创建临时文件（应用退出时自动删除）
            File tempFile = File.createTempFile("patch_", ".tmp", tempDir);
            tempFile.deleteOnExit();
            
            // 解密到临时文件
            File decryptedFile = securityManager.decryptPatch(patchFile);
            
            // 移动到临时目录
            if (!decryptedFile.renameTo(tempFile)) {
                copyFile(decryptedFile, tempFile);
                securityManager.secureDelete(decryptedFile);
            }
            
            Log.d(TAG, "Decrypted patch to memory: " + tempFile.getAbsolutePath());
            return tempFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt patch to memory", e);
            return null;
        }
    }
}

// PatchApplication.java
public class PatchApplication extends Application {
    
    private void loadPatchIfNeeded() {
        try {
            PatchStorage storage = new PatchStorage(this);
            String appliedPatchId = storage.getAppliedPatchId();
            
            if (appliedPatchId == null) {
                return;
            }
            
            // ✅ 每次启动时从加密存储中解密到内存
            File patchFile = storage.decryptPatchToMemory(appliedPatchId);
            if (patchFile == null) {
                Log.e(TAG, "Failed to decrypt patch");
                return;
            }
            
            String patchPath = patchFile.getAbsolutePath();
            
            // 加载补丁
            // ... 现有加载逻辑 ...
            
            // 注意：不要删除 patchFile，它会在应用退出时自动删除
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch", e);
        }
    }
}
```

### 方案 3：混合方案（推荐用于生产环境）

结合方案 1 和方案 2 的优点：

1. **正常情况**：使用方案 1（哈希验证）
   - 快速启动，性能好
   - 验证完整性

2. **检测到篡改**：使用方案 2（重新解密）
   - 从加密存储中重新解密
   - 恢复正确的补丁

3. **多次篡改**：清除补丁并上报
   - 记录篡改次数
   - 超过阈值后清除补丁
   - 上报到服务器

**代码示例**：

```java
// PatchStorage.java
public class PatchStorage {
    
    private static final String KEY_TAMPER_COUNT = "tamper_count";
    private static final int MAX_TAMPER_COUNT = 3;
    
    /**
     * 验证并恢复补丁
     */
    public boolean verifyAndRecoverPatch() {
        // 1. 验证完整性
        if (verifyAppliedPatchIntegrity()) {
            // 完整性正常，重置篡改计数
            prefs.edit().putInt(KEY_TAMPER_COUNT, 0).apply();
            return true;
        }
        
        // 2. 检测到篡改
        int tamperCount = prefs.getInt(KEY_TAMPER_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_TAMPER_COUNT, tamperCount).apply();
        
        Log.e(TAG, "⚠️ Patch tampered! Count: " + tamperCount);
        
        // 3. 超过阈值，清除补丁
        if (tamperCount >= MAX_TAMPER_COUNT) {
            Log.e(TAG, "⚠️ Too many tamper attempts, clearing patch");
            saveAppliedPatchId(null);
            securityManager.secureDelete(getAppliedPatchFile());
            
            // TODO: 上报到服务器
            reportTamperAttempt();
            
            return false;
        }
        
        // 4. 尝试恢复
        String appliedPatchId = getAppliedPatchId();
        if (appliedPatchId != null) {
            Log.i(TAG, "Attempting to recover patch from encrypted storage...");
            File recoveredFile = decryptPatchToApplied(appliedPatchId);
            
            if (recoveredFile != null && verifyAppliedPatchIntegrity()) {
                Log.i(TAG, "✅ Patch recovered successfully");
                return true;
            }
        }
        
        return false;
    }
    
    private void reportTamperAttempt() {
        // TODO: 实现上报逻辑
        Log.e(TAG, "Reporting tamper attempt to server");
    }
}
```

## 推荐实施方案

### 短期（立即实施）
✅ **方案 1：添加哈希验证**
- 实现简单，性能影响小
- 可以检测篡改并恢复
- 向后兼容

### 中期（下个版本）
✅ **方案 3：混合方案**
- 添加篡改计数和上报
- 提供更完善的安全保护

### 长期（可选）
⚠️ **方案 2：内存解密**
- 适用于极高安全要求的场景
- 需要权衡性能和安全性

## 其他安全建议

1. **文件权限**：
   ```java
   // 设置文件为私有，只有应用可以访问
   appliedFile.setReadable(false, false);
   appliedFile.setReadable(true, true);
   appliedFile.setWritable(false, false);
   appliedFile.setWritable(true, true);
   ```

2. **Root 检测**：
   ```java
   // 检测设备是否 Root
   if (isDeviceRooted()) {
       Log.w(TAG, "⚠️ Device is rooted, security may be compromised");
       // 可选：禁用热更新或提示用户
   }
   ```

3. **签名验证**：
   - 确保生产环境强制启用签名验证
   - 定期轮换签名密钥

4. **监控和告警**：
   - 记录所有篡改尝试
   - 上报到服务器进行分析
   - 异常情况下自动禁用热更新

## 总结

当前实现的安全问题是真实存在的，建议：

1. **立即实施**：添加 SHA-256 哈希验证（方案 1）
2. **逐步完善**：实现混合方案（方案 3）
3. **持续监控**：记录和分析篡改尝试

这样可以在保持性能的同时，显著提升安全性。
