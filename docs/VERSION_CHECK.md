# APK 版本检测和自动清除补丁

## 功能说明

�?1.3.7 版本开始，热更新系统会自动检�?APK 版本变化，并在覆盖安装新版本时自动清除旧补丁�?

## 问题背景

**之前的问题：**
1. 应用了补丁（例如 v1.0 + patch�?
2. 覆盖安装新版�?APK（v1.1�?
3. 启动后仍然加载旧补丁
4. 导致版本混乱或崩�?

**原因�?*
- 覆盖安装时，APK 文件被替�?
- 但应用数据不会被清除（包括补丁文件）
- 启动时仍然加�?`/data/data/包名/files/update/` 中的旧补�?

## 解决方案

### 自动版本检�?

在应用启动时（`Application.attachBaseContext()`），自动检�?APK 版本是否变化�?

```java
// 获取当前 APK 版本
PackageInfo packageInfo = context.getPackageManager()
    .getPackageInfo(context.getPackageName(), 0);
long currentVersionCode = packageInfo.versionCode; // �?getLongVersionCode()
String currentVersionName = packageInfo.versionName;

// 获取补丁应用时的 APK 版本
long savedVersionCode = prefs.getLong("apk_version_code", -1);
String savedVersionName = prefs.getString("apk_version_name", null);

// 检查版本是否变�?
if (currentVersionCode != savedVersionCode || 
    !currentVersionName.equals(savedVersionName)) {
    // 版本变化，清除旧补丁
    clearPatchCompletely();
}
```

### 自动清除补丁

当检测到版本变化时，自动清除所有补丁相关文件：

1. **补丁文件**：`current_patch.zip`
2. **合并资源**：`merged_resources.apk`
3. **编译缓存**：`oat/` 目录
4. **配置信息**：SharedPreferences 中的补丁状�?

### 版本信息保存

在应用补丁时，自动保存当�?APK 版本信息�?

```java
// 应用补丁成功�?
prefs.edit()
    .putLong("apk_version_code", versionCode)
    .putString("apk_version_name", versionName)
    .apply();
```

## 使用示例

### 正常流程

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 初始化并加载补丁
        HotUpdateHelper.init(base);
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
        
        // 版本检测和清除是自动的，无需额外代码
    }
}
```

### 日志输出

**首次运行（无补丁）：**
```
D HotUpdateHelper: First run, saving APK version: 4 (1.4)
D HotUpdateHelper: No applied patch to load
```

**应用补丁后：**
```
D HotUpdateHelper: �?记录 APK 版本: 4 (1.4)
I HotUpdateHelper: Patch applied successfully
```

**覆盖安装新版本：**
```
D HotUpdateHelper: Loading applied patch: patch_xxx
I HotUpdateHelper: ⚠️ APK version changed: 4 (1.4) -> 5 (1.5)
I HotUpdateHelper: Clearing old patch due to APK update
D HotUpdateHelper: �?删除补丁文件: current_patch.zip
D HotUpdateHelper: �?删除合并资源文件
D HotUpdateHelper: �?删除 oat 目录
I HotUpdateHelper: �?补丁已完全清�?
I HotUpdateHelper: �?Old patch cleared, ready for new APK version
```

## 技术细�?

### 版本号获�?

使用反射避免 D8/R8 编译器生成合成类导致�?`NoSuchMethodError`�?

```java
long versionCode;
try {
    if (Build.VERSION.SDK_INT >= 28) { // API 28 = Android P
        // 使用反射调用 getLongVersionCode()
        Method method = packageInfo.getClass().getMethod("getLongVersionCode");
        versionCode = (Long) method.invoke(packageInfo);
    } else {
        versionCode = packageInfo.versionCode;
    }
} catch (Exception e) {
    // 反射失败，使用旧方法
    versionCode = packageInfo.versionCode;
}
```

**为什么使用反射？**

直接使用 `getLongVersionCode()` 会导�?D8/R8 编译器生成合成类（`$$ExternalSyntheticApiModelOutline0`），在补�?DEX 中可能找不到这个合成类，导致 `NoSuchMethodError`�?

### 版本比较逻辑

```java
boolean versionChanged = 
    (currentVersionCode != savedVersionCode) ||
    (currentVersionName != null && !currentVersionName.equals(savedVersionName));
```

**比较规则�?*
- `versionCode` 不同 �?版本变化
- `versionName` 不同 �?版本变化
- 任一条件满足即清除补�?

### 清除范围

完整清除所有补丁相关文件和配置�?

```java
private void clearPatchCompletely(SharedPreferences prefs, File appliedFile, String patchId) {
    // 1. 清除 SharedPreferences
    editor.remove("applied_patch_id");
    editor.remove("applied_patch_hash");
    editor.remove("patch_had_signature");
    editor.remove("is_zip_password_protected");
    editor.remove("custom_zip_password");
    editor.remove("tamper_count");
    
    // 2. 删除补丁文件
    appliedFile.delete();
    
    // 3. 删除合并资源
    mergedResourceFile.delete();
    
    // 4. 删除 oat 目录
    deleteDirectory(oatDir);
    
    // 5. 清理缓存
    cleanupCacheFiles();
}
```

## 兼容�?

### 向后兼容

- �?旧版本升级到新版本：自动清除旧补�?
- �?首次运行：自动保存版本信�?
- �?无补丁状态：正常运行，不受影�?

### 异常处理

```java
try {
    // 版本检测逻辑
} catch (Exception e) {
    logE("Failed to check APK version", e);
    // 继续加载补丁，不因版本检查失败而中�?
}
```

**容错策略�?*
- 版本检测失�?�?继续加载补丁（不影响正常使用�?
- 版本信息缺失 �?保存当前版本（向后兼容）
- 清除失败 �?记录日志，不影响应用启动

## 测试场景

### 场景1：正常覆盖安�?

1. 安装 v1.0，应用补�?
2. 覆盖安装 v1.1
3. 启动应用
4. **预期**：自动清除补丁，使用 v1.1 原始代码

### 场景2：版本号不变

1. 安装 v1.0，应用补�?
2. 重新编译 v1.0（版本号不变�?
3. 覆盖安装
4. **预期**：不清除补丁，继续使用补�?

### 场景3：降级安�?

1. 安装 v1.1，应用补�?
2. 卸载后安�?v1.0
3. 启动应用
4. **预期**：检测到版本变化，清除补�?

### 场景4：首次安�?

1. 首次安装应用
2. 启动应用
3. **预期**：保存版本信息，无补丁加�?

## 常见问题

### Q1: 为什么覆盖安装后补丁被清除了�?

**A:** 这是正常行为。覆盖安装意味着 APK 版本变化，旧补丁可能不兼容新版本，自动清除是为了避免崩溃�?

### Q2: 如何保留补丁�?

**A:** 不要覆盖安装，而是�?
- 使用相同版本号重新编�?
- 或者重新生成适配新版本的补丁

### Q3: 版本检测会影响性能吗？

**A:** 不会。版本检测只在应用启动时执行一次，耗时极短�? 1ms）�?

### Q4: 可以禁用自动清除吗？

**A:** 不建议禁用。如果确实需要，可以修改源码移除版本检测逻辑，但可能导致版本混乱�?

### Q5: 如何手动清除补丁�?

**A:** 使用 API�?
```java
HotUpdateHelper.getInstance().clearPatch();
```

或者清除应用数据：
```bash
adb shell pm clear com.your.package
```

## 最佳实�?

1. **版本管理**
   - 每次发布新版本时，递增 `versionCode`
   - 使用语义化版本号（`versionName`�?

2. **补丁策略**
   - 为每�?APK 版本生成对应的补�?
   - 不要跨大版本使用补丁

3. **测试流程**
   - 测试覆盖安装场景
   - 验证补丁自动清除
   - 确认新版本正常运�?

4. **日志监控**
   - 关注版本变化日志
   - 监控补丁清除情况
   - 及时发现异常

## 总结

- �?**自动检�?*：启动时自动检�?APK 版本变化
- �?**自动清除**：版本变化时自动清除旧补�?
- �?**完整清理**：清除所有补丁相关文件和配置
- �?**向后兼容**：不影响旧版本和无补丁状�?
- �?**异常容错**：检测失败不影响应用启动
- �?**性能优化**：使用反射避免合成类问题

