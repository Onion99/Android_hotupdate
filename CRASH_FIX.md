# 崩溃问题修复说明

## 问题描述

朋友的应用 `com.jsapp.update` 在应用补丁后启动时崩溃：

```
java.lang.RuntimeException: Unable to start activity ComponentInfo{com.jsapp.update/com.jsapp.update.rn_1}: 
android.content.res.Resources$NotFoundException: Resource ID #0x7f010000
at com.jsapp.update.rn_1.onInit(rn_1.java:23)
```

## 根本原因

**资源路径不一致问题：**

1. **应用补丁时**：使用 `ResourceMerger` 合并原始 APK 和补丁资源，生成 `merged_resources.apk`
2. **启动加载时**：却使用了 `current_patch.zip`（只包含补丁资源，不包含原始 APK 资源）
3. **结果**：补丁中引用的资源 ID `0x7f010000` 在 `current_patch.zip` 中找不到，导致崩溃

## 修复方案

修改 `HotUpdateHelper.loadPatchIfNeeded()` 方法：

### 修复前：
```java
String patchPath = actualPatchFile.getAbsolutePath();

// 检查补丁是否包含资源
if (hasResourcePatchInternal(actualPatchFile)) {
    // 合并资源
    java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
    boolean merged = ResourceMerger.mergeResources(context, actualPatchFile, mergedResourceFile);
    
    if (merged && mergedResourceFile.exists()) {
        // ❌ 问题：将 patchPath 改为 merged_resources.apk
        patchPath = mergedResourceFile.getAbsolutePath();
    }
}

// ❌ 问题：DEX 注入和资源加载都使用同一个 patchPath
DexPatcher.injectPatchDex(context, patchPath);
ResourcePatcher.loadPatchResources(context, patchPath);
```

### 修复后：
```java
String patchPath = actualPatchFile.getAbsolutePath();
String resourcePath = patchPath; // ✅ 分离 DEX 路径和资源路径

// 检查补丁是否包含资源
if (hasResourcePatchInternal(actualPatchFile)) {
    java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
    
    // ✅ 优先使用已存在的合并资源文件
    if (mergedResourceFile.exists()) {
        resourcePath = mergedResourceFile.getAbsolutePath();
    } else {
        // 如果不存在，重新合并
        boolean merged = ResourceMerger.mergeResources(context, actualPatchFile, mergedResourceFile);
        if (merged && mergedResourceFile.exists()) {
            resourcePath = mergedResourceFile.getAbsolutePath();
        }
    }
}

// ✅ DEX 注入使用原始补丁文件
DexPatcher.injectPatchDex(context, patchPath);

// ✅ 资源加载使用合并后的完整资源包
ResourcePatcher.loadPatchResources(context, resourcePath);
```

## 关键改进

1. **分离 DEX 和资源路径**：
   - DEX 注入：使用 `current_patch.zip`（只需要补丁的 DEX）
   - 资源加载：使用 `merged_resources.apk`（需要完整的资源包）

2. **优先使用已存在的合并资源**：
   - 避免每次启动都重新合并资源（耗时操作）
   - 如果 `merged_resources.apk` 已存在，直接使用

3. **日志改进**：
   - 添加更详细的日志，显示实际使用的资源路径

## 使用说明

### 对于已经崩溃的应用：

1. **清除应用数据**（会删除旧的错误补丁）：
   ```bash
   adb shell pm clear com.jsapp.update
   ```

2. **重新应用补丁**：
   - 使用最新版本的 update 模块（1.3.6+）
   - 重新生成并应用补丁

### 对于新应用：

直接使用最新版本的 update 模块即可，问题已修复。

## 验证方法

查看 logcat 日志，确认资源加载路径：

```bash
adb logcat | grep -E "HotUpdate|ResourcePatcher|DexPatcher"
```

**正确的日志应该是：**
```
DexPatcher: Patch dex injected successfully: /data/user/0/com.jsapp.update/files/update/applied/current_patch.zip
ResourcePatcher: Updated LoadedApk.mResDir to: /data/user/0/com.jsapp.update/files/update/applied/merged_resources.apk
ResourcePatcher: Patch resources loaded successfully from: /data/user/0/com.jsapp.update/files/update/applied/merged_resources.apk
```

**错误的日志（修复前）：**
```
DexPatcher: Patch dex injected successfully: /data/user/0/com.jsapp.update/files/update/applied/current_patch.zip
ResourcePatcher: Updated LoadedApk.mResDir to: /data/user/0/com.jsapp.update/files/update/applied/current_patch.zip
ResourcePatcher: Patch resources loaded successfully from: /data/user/0/com.jsapp.update/files/update/applied/current_patch.zip
```

## 影响范围

- **影响版本**：1.3.5 及之前的版本
- **修复版本**：1.3.6+
- **影响场景**：只影响包含资源的补丁（纯 DEX 补丁不受影响）

## 相关文件

- `update/src/main/java/com/orange/update/HotUpdateHelper.java`
- `update/src/main/java/com/orange/update/ResourceMerger.java`
- `update/src/main/java/com/orange/update/ResourcePatcher.java`
