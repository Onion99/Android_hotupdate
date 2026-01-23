# Patch Core 核心�?

补丁生成器的核心引擎模块，提�?APK 解析、差异比较、补丁打包和签名功能�?

## 功能特�?

- **APK 解析**: 解析 APK 文件，提�?dex、资源和 assets
- **Dex 差异比较**: 比较两个 dex 文件，识别修改、新增、删除的�?
- **资源差异比较**: 比较资源文件�?assets 目录
- **补丁打包**: 将差异内容打包为补丁文件
- **补丁签名**: 使用 RSA-2048 对补丁进行签�?

## 依赖

```groovy
implementation project(':patch-core')
```

## 快速开�?

### 基本用法

```java
import io.github.706412584.patchgen.PatchGenerator;
import io.github.706412584.patchgen.config.SigningConfig;
import io.github.706412584.patchgen.model.PatchResult;

// 配置签名
SigningConfig signingConfig = SigningConfig.builder()
    .keystoreFile(new File("keystore.jks"))
    .keystorePassword("password")
    .keyAlias("patch")
    .keyPassword("password")
    .build();

// 创建生成�?
PatchGenerator generator = new PatchGenerator.Builder()
    .baseApk(new File("app-v1.0.apk"))
    .newApk(new File("app-v1.1.apk"))
    .output(new File("patch-v1.1.patch"))
    .signingConfig(signingConfig)
    .build();

// 生成补丁
PatchResult result = generator.generate();

if (result.isSuccess()) {
    System.out.println("补丁生成成功: " + result.getPatchFile());
    System.out.println("补丁大小: " + result.getPatchSize() + " bytes");
}
```

### 异步生成

```java
generator.generateAsync(result -> {
    if (result.isSuccess()) {
        System.out.println("补丁生成成功!");
    } else {
        System.out.println("生成失败: " + result.getErrorMessage());
    }
});
```

### 使用回调监听进度

```java
PatchGenerator generator = new PatchGenerator.Builder()
    .baseApk(baseApk)
    .newApk(newApk)
    .output(outputFile)
    .callback(new SimpleGeneratorCallback() {
        @Override
        public void onParseProgress(int current, int total) {
            System.out.println("解析进度: " + current + "/" + total);
        }
        
        @Override
        public void onCompareProgress(int current, int total, String currentFile) {
            System.out.println("比较进度: " + current + "/" + total + " - " + currentFile);
        }
        
        @Override
        public void onComplete(PatchResult result) {
            System.out.println("完成!");
        }
    })
    .build();
```

## 补丁包格�?

生成的补丁包是一�?zip 文件，包含以下结构：

```
patch.zip
├── patch.json          # 补丁元信�?
├── classes.dex         # 修改�?dex 文件
├── res/                # 修改的资源文�?
├── assets/             # 修改�?assets
└── signature.sig       # 签名文件
```

## API 文档

### PatchGenerator

主要的补丁生成器类�?

| 方法 | 说明 |
|------|------|
| `generate()` | 同步生成补丁 |
| `generateAsync(callback)` | 异步生成补丁 |
| `cancel()` | 取消生成 |

### PatchResult

生成结果类�?

| 属�?| 说明 |
|------|------|
| `success` | 是否成功 |
| `patchFile` | 补丁文件 |
| `patchSize` | 补丁大小 |
| `generateTime` | 生成耗时 |
| `diffSummary` | 差异摘要 |

## 许可�?

Apache License 2.0

