# Android 热更新补丁生成工具

一套完整的 Android 热更新补丁生成解决方案，支持多种使用方式。

## 项目结构

```
├── patch-core/              # 核心库 - 补丁生成引擎
├── patch-native/            # Native 库 - C/C++ 高性能引擎
├── patch-generator-android/ # Android SDK - 设备端生成
├── patch-cli/               # 命令行工具 - PC/服务器端
├── patch-gradle-plugin/     # Gradle 插件 - 构建集成
├── update/                  # 热更新 SDK - 补丁应用
└── app/                     # Demo 应用
```

## 模块说明

| 模块 | 说明 | 文档 |
|------|------|------|
| patch-core | 核心引擎，提供 APK 解析、差异比较、打包签名 | [README](patch-core/README.md) |
| patch-native | Native SO 库，BsDiff 算法和快速哈希 | [README](patch-native/README.md) |
| patch-generator-android | Android SDK，设备端补丁生成 | [README](patch-generator-android/README.md) |
| patch-cli | 命令行工具，独立运行 | [README](patch-cli/README.md) |
| patch-gradle-plugin | Gradle 插件，构建集成 | [README](patch-gradle-plugin/README.md) |

## 快速开始

### 1. 命令行生成补丁

```bash
java -jar patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip
```

### 2. Gradle 插件集成

```groovy
// app/build.gradle
plugins {
    id 'com.orange.patch'
}

patchGenerator {
    baselineApk = file("baseline/app-release.apk")
    outputDir = file("build/patch")
}
```

```bash
./gradlew generateReleasePatch
```

### 3. Android 设备端生成

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseFromInstalled("com.example.app")
    .newApk(newApkFile)
    .output(patchFile)
    .callback(callback)
    .build();

generator.generateInBackground();
```

## 编译

### 编译所有模块

```bash
./gradlew build
```

### 编译特定模块

```bash
./gradlew :patch-core:build
./gradlew :patch-native:build
./gradlew :patch-generator-android:build
./gradlew :patch-cli:build
./gradlew :patch-gradle-plugin:build
```

### 运行测试

```bash
./gradlew test
```

## 补丁包格式

```
patch.zip
├── patch.json          # 元信息
├── classes.dex         # 修改的 dex
├── res/                # 修改的资源
├── assets/             # 修改的 assets
└── signature.sig       # 签名
```

### patch.json 示例

```json
{
    "patchId": "patch_20260116_001",
    "patchVersion": "1.0.1",
    "baseVersion": "1.0.0",
    "targetVersion": "1.0.1",
    "md5": "d41d8cd98f00b204e9800998ecf8427e",
    "createTime": 1705420800000,
    "changes": {
        "dex": {
            "modified": ["com.example.MainActivity"],
            "added": ["com.example.NewFeature"],
            "deleted": []
        }
    }
}
```

## 系统要求

- Java 11+
- Android SDK 21+ (Android 5.0)
- Gradle 7.0+
- Android Gradle Plugin 7.0+
- NDK 27.0+ (编译 Native 模块)

## 许可证

Apache License 2.0

## 贡献

欢迎提交 Issue 和 Pull Request！
