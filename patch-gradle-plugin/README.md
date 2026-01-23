# Patch Gradle Plugin

Gradle 插件，集成到 Android 构建流程自动生成补丁�?

## 功能特�?

- **自动集成**: 自动注册补丁生成任务
- **构建变体支持**: 支持 debug �?release 构建变体
- **DSL 配置**: 通过 Gradle DSL 配置各种选项
- **增量构建**: 支持 Gradle 增量构建

## 安装

### 方式一：通过 Gradle Plugin Portal（推荐）

在项目根目录�?`settings.gradle` 中配置插件仓库（Gradle 7.0+ 默认已包含）�?

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

�?app 模块�?`build.gradle` 中应用插件：

```groovy
plugins {
    id 'com.android.application'
    id 'io.github.706412584.patch' version '1.4.0'
}
```

### 方式二：通过 Maven Central

在项目根目录�?`build.gradle` 中添加插件依赖：

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.9.0'
        classpath 'io.github.706412584:patch-gradle-plugin:1.3.9'
    }
}
```

�?app 模块�?`build.gradle` 中应用插件：

```groovy
plugins {
    id 'com.android.application'
    id 'io.github.706412584.patch'
}
```

### 方式三：使用 JitPack（向后兼容）

在项目根目录�?`settings.gradle` 中添�?JitPack 仓库�?

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在项目根目录�?`build.gradle` 中添加插件依赖：

```groovy
buildscript {
    dependencies {
        classpath 'com.github.706412584.Android_hotupdate:patch-gradle-plugin:v1.2.4'
    }
}
```

### 方式四：使用本地项目依赖

在项目根目录�?`build.gradle` 中添加：

```groovy
buildscript {
    dependencies {
        classpath project(':patch-gradle-plugin')
    }
}
```

### �?app 模块�?build.gradle 中应用插�?

```groovy
plugins {
    id 'com.android.application'
    id 'io.github.706412584.patch'
}
```

## 配置

```groovy
patchGenerator {
    // 基线 APK 路径
    baselineApk = file("baseline/app-release.apk")
    
    // 输出目录
    outputDir = file("build/patch")
    
    // 签名配置
    signing {
        keystoreFile = file("keystore/patch.jks")
        keystorePassword = "password"
        keyAlias = "patch"
        keyPassword = "password"
    }
    
    // 引擎配置 (auto, java, native)
    engine = "auto"
    
    // 补丁模式 (full_dex, bsdiff)
    patchMode = "full_dex"
    
    // 是否启用
    enabled = true
}
```

## 使用

### 生成补丁

```bash
# 生成 debug 变体的补�?
./gradlew generateDebugPatch

# 生成 release 变体的补�?
./gradlew generateReleasePatch
```

### 查看任务

```bash
./gradlew tasks --group=patch
```

## 配置选项

| 选项 | 类型 | 说明 | 默认�?|
|------|------|------|--------|
| `baselineApk` | File | 基线 APK 文件 | 必填 |
| `outputDir` | File | 输出目录 | build/patch |
| `signing.keystoreFile` | File | Keystore 文件 | null |
| `signing.keystorePassword` | String | Keystore 密码 | null |
| `signing.keyAlias` | String | 密钥别名 | null |
| `signing.keyPassword` | String | 密钥密码 | null |
| `engine` | String | 引擎类型 | "auto" |
| `patchMode` | String | 补丁模式 | "full_dex" |
| `enabled` | boolean | 是否启用 | true |

## 完整示例

```groovy
// app/build.gradle
plugins {
    id 'com.android.application'
    id 'io.github.706412584.patch'
}

android {
    // ... Android 配置
}

patchGenerator {
    // 基线 APK（上一个发布版本）
    baselineApk = file("${rootDir}/baseline/app-v1.0-release.apk")
    
    // 补丁输出目录
    outputDir = file("${buildDir}/outputs/patch")
    
    // 签名配置
    signing {
        keystoreFile = file("${rootDir}/keystore/patch-key.jks")
        keystorePassword = System.getenv("PATCH_KEYSTORE_PASSWORD") ?: "default"
        keyAlias = "patch"
        keyPassword = System.getenv("PATCH_KEY_PASSWORD") ?: "default"
    }
    
    // 使用自动引擎选择
    engine = "auto"
    
    // 使用完整 dex 模式
    patchMode = "full_dex"
    
    // 启用插件
    enabled = true
}

// �?assembleRelease 后自动生成补�?
tasks.named("assembleRelease").configure {
    finalizedBy("generateReleasePatch")
}
```

## CI/CD 集成

### GitHub Actions

```yaml
- name: Generate Patch
  run: ./gradlew generateReleasePatch
  env:
    PATCH_KEYSTORE_PASSWORD: ${{ secrets.PATCH_KEYSTORE_PASSWORD }}
    PATCH_KEY_PASSWORD: ${{ secrets.PATCH_KEY_PASSWORD }}

- name: Upload Patch
  uses: actions/upload-artifact@v3
  with:
    name: patch
    path: app/build/outputs/patch/*.patch
```

### Jenkins

```groovy
stage('Generate Patch') {
    steps {
        withCredentials([
            string(credentialsId: 'patch-keystore-password', variable: 'PATCH_KEYSTORE_PASSWORD'),
            string(credentialsId: 'patch-key-password', variable: 'PATCH_KEY_PASSWORD')
        ]) {
            sh './gradlew generateReleasePatch'
        }
    }
}
```

## 任务依赖

```
generateDebugPatch
└── assembleDebug

generateReleasePatch
└── assembleRelease
```

## 输出

补丁文件将输出到配置�?`outputDir` 目录�?

```
build/patch/
├── patch-debug-1.0.1.patch
└── patch-release-1.0.1.patch
```

## 许可�?

Apache License 2.0


## 发布信息

### Maven Central

```groovy
implementation 'io.github.706412584:patch-gradle-plugin:1.3.9'
```

- **Group ID**: `io.github.706412584`
- **Artifact ID**: `patch-gradle-plugin`
- **Latest Version**: `1.3.9`
- **Repository**: https://repo1.maven.org/maven2/io/github/706412584/patch-gradle-plugin/

### Gradle Plugin Portal

```groovy
plugins {
    id 'io.github.706412584.patch' version '1.3.9'
}
```

- **Plugin ID**: `io.github.706412584.patch`
- **Latest Version**: `1.3.9`
- **Plugin Page**: https://plugins.gradle.org/plugin/io.github.706412584.patch

### 发布指南

如果你是项目维护者，想要发布新版本，请参考：
- [发布指南](PUBLISH_GUIDE.md) - 详细的发布步骤和说明

## 相关链接

- **项目主页**: https://github.com/706412584/Android_hotupdate
- **Maven Central**: https://central.sonatype.com/artifact/io.github.706412584/patch-gradle-plugin
- **Gradle Plugin Portal**: https://plugins.gradle.org/plugin/io.github.706412584.patch
- **问题反馈**: https://github.com/706412584/Android_hotupdate/issues


