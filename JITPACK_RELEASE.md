# JitPack 发布指南

本文档说明如何将项目发布到 JitPack，让其他开发者可以通过 Gradle 依赖使用。

## 📋 前置要求

- GitHub 仓库已公开
- 项目已配置 `maven-publish` 插件
- 已创建 Git 标签（tag）

## 🚀 发布步骤

### 1. 确认配置

确保所有库模块的 `build.gradle` 已配置 `maven-publish`：

```groovy
plugins {
    id 'maven-publish'
}

publishing {
    publications {
        release(MavenPublication) {
            from components.release
            groupId = 'com.orange.patch'
            artifactId = 'module-name'
            version = '1.2.0'
        }
    }
}
```

### 2. 创建 Git 标签

```bash
# 创建标签
git tag -a v1.2.0 -m "Release version 1.2.0"

# 推送标签到 GitHub
git push origin v1.2.0
```

### 3. 触发 JitPack 构建

访问 JitPack 页面触发构建：

```
https://jitpack.io/#706412584/Android_hotupdate/v1.2.0
```

或者直接访问：
```
https://jitpack.io/#706412584/Android_hotupdate
```

点击「Get it」按钮开始构建。

### 4. 等待构建完成

- 构建通常需要 2-5 分钟
- 可以查看构建日志排查问题
- 构建成功后会显示绿色的「Get it」按钮

### 5. 使用依赖

构建成功后，其他开发者可以这样使用：

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// app/build.gradle
dependencies {
    implementation 'com.github.706412584.Android_hotupdate:patch-generator-android:1.2.0'
    implementation 'com.github.706412584.Android_hotupdate:update:1.2.0'
    implementation 'com.github.706412584.Android_hotupdate:patch-core:1.2.0'
}
```

## 📦 可用模块

| 模块 | Artifact ID | 说明 |
|------|-------------|------|
| **patch-generator-android** | `patch-generator-android` | Android SDK，设备端补丁生成 |
| **update** | `update` | 热更新 SDK，补丁应用和加载 |
| **patch-core** | `patch-core` | 核心引擎，APK 解析、差异比较 |
| **patch-native** | `patch-native` | Native SO 库，BsDiff 算法 |

## 🔧 JitPack 配置

项目根目录的 `jitpack.yml` 配置：

```yaml
jdk:
  - openjdk17
before_install:
  - sdk install java 17.0.2-open
  - sdk use java 17.0.2-open
install:
  - echo "Running custom install command"
  - ./gradlew clean build publishToMavenLocal -x test
```

**说明：**
- 使用 Java 17（Gradle 9.1.0 要求）
- 跳过测试以加快构建速度
- 发布到本地 Maven 仓库

## ❗ 常见问题

### Q: 构建失败，提示 Java 版本不匹配？
**A:** 确保 `jitpack.yml` 中配置了正确的 Java 版本：
```yaml
jdk:
  - openjdk17
```

### Q: 构建失败，提示找不到模块？
**A:** 检查 `settings.gradle` 中是否包含了所有模块：
```groovy
include ':patch-core'
include ':patch-native'
include ':patch-generator-android'
include ':update'
```

### Q: 如何查看构建日志？
**A:** 在 JitPack 页面点击版本号旁边的「Log」按钮。

### Q: 如何删除已发布的版本？
**A:** JitPack 不支持删除版本，但可以：
1. 删除 GitHub 上的标签
2. 发布新版本覆盖

### Q: 如何发布快照版本？
**A:** 使用分支名代替标签：
```groovy
implementation 'com.github.706412584.Android_hotupdate:update:main-SNAPSHOT'
```

## 📝 版本规范

建议使用语义化版本号：

- **主版本号 (Major)**: 不兼容的 API 变更
- **次版本号 (Minor)**: 向下兼容的功能新增
- **修订号 (Patch)**: 向下兼容的问题修正

示例：
- `v1.0.0` - 初始版本
- `v1.1.0` - 新增功能
- `v1.1.1` - 修复 Bug
- `v2.0.0` - 重大更新

## 🔄 更新流程

发布新版本的完整流程：

```bash
# 1. 更新版本号
# 修改各模块 build.gradle 中的 version

# 2. 提交代码
git add .
git commit -m "chore: bump version to 1.3.0"
git push origin main

# 3. 创建标签
git tag -a v1.3.0 -m "Release version 1.3.0"
git push origin v1.3.0

# 4. 触发 JitPack 构建
# 访问 https://jitpack.io/#706412584/Android_hotupdate

# 5. 更新文档
# 更新 README.md 中的版本号
```

## 📊 构建状态

可以在 README 中添加 JitPack 徽章：

```markdown
[![JitPack](https://jitpack.io/v/706412584/Android_hotupdate.svg)](https://jitpack.io/#706412584/Android_hotupdate)
```

效果：
[![JitPack](https://jitpack.io/v/706412584/Android_hotupdate.svg)](https://jitpack.io/#706412584/Android_hotupdate)

## 🔗 相关链接

- **JitPack 主页**: https://jitpack.io
- **项目 JitPack 页面**: https://jitpack.io/#706412584/Android_hotupdate
- **JitPack 文档**: https://jitpack.io/docs/
- **GitHub 仓库**: https://github.com/706412584/Android_hotupdate

---

**返回**: [主文档](README.md)
