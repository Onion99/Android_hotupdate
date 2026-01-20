# resources.arsc 压缩问题修复

## 问题描述

补丁生成后，`resources.arsc` 文件被压缩，导致补丁包整体大小只有 200KB，但实际 `resources.arsc` 应该是 900KB+。

**症状：**
- 补丁 ZIP 文件总大小：208KB
- `resources.arsc` 原始大小：985KB (961 KB)
- `resources.arsc` 压缩后大小：208KB (203 KB)
- 压缩率：约 21%（不应该被压缩）

## 根本原因

`JarSigner.java` 在重新打包 JAR 文件时，虽然正确设置了 `resources.arsc` 使用 `STORE` 模式（不压缩），但是 `ZipOutputStream` 的默认行为可能覆盖了这个设置。

**问题代码位置：**
```java
// patch-core/src/main/java/com/orange/patchgen/signer/JarSigner.java
private void repackJar(...) {
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
        // ❌ 没有设置默认压缩级别
        
        // ... 后续代码
        
        // resources.arsc 设置了 STORE 模式
        if ("resources.arsc".equals(name)) {
            zipEntry.setMethod(ZipEntry.STORED);
            // ...
        }
    }
}
```

## 解决方案

在创建 `ZipOutputStream` 后立即设置默认压缩级别，确保 `STORE` 模式生效。

**修复代码：**
```java
private void repackJar(File jarFile, Map<String, byte[]> entries, Manifest manifest, 
                      byte[] sfBytes, byte[] signatureBlock, String signatureExt) throws IOException {
    File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
    
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
        // ✅ 设置默认压缩级别（DEFLATED 模式）
        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
        
        // 1. 写入 MANIFEST.MF
        zos.putNextEntry(new ZipEntry(MANIFEST_NAME));
        manifest.write(zos);
        zos.closeEntry();
        
        // ... 其他代码
        
        // 4. 写入所有原始文件
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue();
            
            ZipEntry zipEntry = new ZipEntry(name);
            
            // resources.arsc 必须使用 STORE 模式（不压缩）
            if ("resources.arsc".equals(name)) {
                System.out.println("[JarSigner] 处理 resources.arsc");
                System.out.println("  原始大小: " + data.length + " bytes (" + (data.length / 1024) + " KB)");
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(data.length);
                zipEntry.setCompressedSize(data.length);
                zipEntry.setCrc(calculateCrc32(data));
                System.out.println("  压缩方式: STORED (不压缩)");
                System.out.println("  CRC32: " + Long.toHexString(zipEntry.getCrc()));
            }
            
            zos.putNextEntry(zipEntry);
            zos.write(data);
            zos.closeEntry();
        }
    }
    
    // 替换原文件
    if (!jarFile.delete()) {
        throw new IOException("Failed to delete original JAR file");
    }
    if (!tempFile.renameTo(jarFile)) {
        throw new IOException("Failed to rename temporary file");
    }
}
```

## 技术细节

### 为什么 resources.arsc 必须不压缩？

Android 系统的 `AssetManager.addAssetPath()` 方法需要直接访问未压缩的 `resources.arsc` 文件。如果文件被压缩，系统无法正确加载资源，导致热更新失败。

### ZipOutputStream 的行为

- `ZipOutputStream` 默认使用 `DEFLATED` 压缩模式
- 即使设置了 `ZipEntry.STORED`，如果没有正确设置 `size`、`compressedSize` 和 `crc`，可能仍会被压缩
- 需要显式调用 `setLevel()` 来确保压缩行为符合预期

### 其他文件的压缩

- DEX 文件：可以压缩（使用 DEFLATED）
- SO 库：可以压缩（使用 DEFLATED）
- 资源文件：可以压缩（使用 DEFLATED）
- META-INF/ 签名文件：可以压缩（使用 DEFLATED）
- **只有 resources.arsc 必须不压缩（使用 STORED）**

## 验证方法

### 1. 检查补丁文件

使用 7-Zip 或 `unzip -l` 查看补丁内容：

```bash
# Windows (PowerShell)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::OpenRead("patch.zip").Entries | 
  Select-Object Name, Length, CompressedLength | Format-Table -AutoSize

# Linux/Mac
unzip -l patch.zip
```

**正确的输出：**
```
Name           Length CompressedLength
----           ------ ----------------
resources.arsc 985128 985128           ← 压缩后大小 = 原始大小（STORED）
classes.dex    123456 45678            ← 压缩后大小 < 原始大小（DEFLATED）
```

### 2. 查看生成日志

生成补丁时会输出详细日志：

```
[PatchPacker] 添加 resources.arsc 到补丁
  文件路径: /path/to/resources.arsc
  文件大小: 985128 bytes (961 KB)
  压缩方式: STORE (不压缩)
  ✓ resources.arsc 已添加到补丁

[JarSigner] 处理 resources.arsc
  原始大小: 985128 bytes (961 KB)
  压缩方式: STORED (不压缩)
  CRC32: 1a2b3c4d
```

### 3. 测试热更新

应用补丁后，检查资源是否正确加载：

```java
// 在应用启动时
HotUpdateHelper.getInstance().loadPatchIfNeeded();

// 检查资源是否可用
try {
    Resources res = getResources();
    String text = res.getString(R.string.app_name);
    Log.i(TAG, "✅ 资源加载成功: " + text);
} catch (Exception e) {
    Log.e(TAG, "❌ 资源加载失败", e);
}
```

## 相关文件

- `patch-core/src/main/java/com/orange/patchgen/packer/PatchPacker.java` - 补丁打包
- `patch-core/src/main/java/com/orange/patchgen/signer/JarSigner.java` - JAR 签名和重新打包
- `update/src/main/java/com/orange/update/ResourcePatcher.java` - 资源热更新加载

## 历史问题

### 第一次尝试（失败）

使用 `zip4j` 的 `addStream()` 方法添加 `resources.arsc`：

```java
// ❌ 错误方法
ZipParameters params = new ZipParameters();
params.setFileNameInZip("resources.arsc");
params.setCompressionMethod(CompressionMethod.STORE);
zipFile.addStream(new FileInputStream(resourcesArsc), params);
```

**问题：** `addStream()` 方法不遵守 `STORE` 设置，仍然压缩文件。

### 第二次尝试（成功）

改用 `addFile()` 方法：

```java
// ✅ 正确方法
ZipParameters params = new ZipParameters();
params.setFileNameInZip("resources.arsc");
params.setCompressionMethod(CompressionMethod.STORE);
zipFile.addFile(resourcesArsc, params);
```

**结果：** `PatchPacker` 阶段正确，但 `JarSigner` 重新打包时又压缩了。

### 第三次尝试（最终修复）

在 `JarSigner` 的 `ZipOutputStream` 中显式设置压缩级别：

```java
// ✅ 最终修复
try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
    zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
    // ...
}
```

## 总结

- ✅ **问题根源**：`ZipOutputStream` 默认行为覆盖了 `STORE` 设置
- ✅ **解决方案**：显式设置压缩级别，确保 `STORE` 模式生效
- ✅ **验证方法**：检查补丁文件中 `resources.arsc` 的压缩大小
- ✅ **Android 限制**：`resources.arsc` 必须不压缩才能被 `AssetManager` 加载

---

**修复版本：** 1.3.9+  
**修复日期：** 2026-01-20  
**相关 Issue：** resources.arsc 被压缩导致热更新失败
