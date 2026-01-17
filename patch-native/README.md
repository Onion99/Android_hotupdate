# Patch Native 原生引擎

高性能的 Native SO 库，使用 C/C++ 实现 BsDiff 算法和快速哈希计算。

## 功能特性

- **BsDiff 算法**: 高效的二进制差异生成
- **BsPatch 算法**: 二进制补丁应用
- **快速哈希**: MD5 和 SHA256 计算
- **多架构支持**: ARM64, ARMv7, x86, x86_64
- **进度回调**: 支持进度监听和取消操作

## 依赖

```groovy
implementation project(':patch-native')
```

## 快速开始

### 检查 Native 库是否可用

```java
import com.orange.patchnative.NativePatchEngine;

if (NativePatchEngine.isAvailable()) {
    System.out.println("Native 引擎可用");
} else {
    System.out.println("Native 引擎不可用，将使用 Java 引擎");
}
```

### 生成二进制差异

```java
NativePatchEngine engine = new NativePatchEngine();

// 初始化
if (engine.init()) {
    // 生成差异
    int result = engine.generateDiff(
        "/path/to/old_file",
        "/path/to/new_file",
        "/path/to/patch_file"
    );
    
    if (result == 0) {
        System.out.println("差异生成成功");
    }
    
    // 释放资源
    engine.release();
}
```

### 应用补丁

```java
NativePatchEngine engine = new NativePatchEngine();

if (engine.init()) {
    int result = engine.applyPatch(
        "/path/to/old_file",
        "/path/to/patch_file",
        "/path/to/new_file"
    );
    
    if (result == 0) {
        System.out.println("补丁应用成功");
    }
    
    engine.release();
}
```

### 计算文件哈希

```java
NativePatchEngine engine = new NativePatchEngine();

if (engine.init()) {
    String md5 = engine.calculateMd5("/path/to/file");
    String sha256 = engine.calculateSha256("/path/to/file");
    
    System.out.println("MD5: " + md5);
    System.out.println("SHA256: " + sha256);
    
    engine.release();
}
```

### 进度回调

```java
engine.setProgressCallback((current, total) -> {
    int percent = (int) (current * 100 / total);
    System.out.println("进度: " + percent + "%");
});
```

### 取消操作

```java
// 在另一个线程中取消
engine.cancel();
```

## 支持的架构

| 架构 | ABI |
|------|-----|
| ARM 64-bit | arm64-v8a |
| ARM 32-bit | armeabi-v7a |
| x86 64-bit | x86_64 |
| x86 32-bit | x86 |

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| -1 | 文件未找到 |
| -2 | 文件读取失败 |
| -3 | 文件写入失败 |
| -4 | 内存不足 |
| -5 | 参数无效 |
| -6 | 操作已取消 |
| -7 | 补丁文件损坏 |

## 编译要求

- Android NDK 27.0 或更高版本
- CMake 3.22 或更高版本

## 许可证

Apache License 2.0
