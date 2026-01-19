# Maven Central 发布指南

## 发布的模块

从 v1.3.2 开始，发布脚本包含以下 **5 个模块**：

1. **patch-core** - 核心补丁生成库（JAR）
2. **patch-native** - Native C++ 引擎（AAR，包含 SO 库）
3. **patch-generator-android** - Android 设备端补丁生成器（AAR）
4. **patch-cli** - 命令行工具（JAR，包含 fat JAR）
5. **update** - 热更新核心库（AAR）

## patch-native 发布说明

### 发布内容

patch-native 是 Native C++ 引擎，包含高性能的 BsDiff 算法实现。

**AAR 文件内容**：
- Java/Kotlin 接口代码
- Native SO 库（4 个架构）：
  - `arm64-v8a/libpatch-native.so`
  - `armeabi-v7a/libpatch-native.so`
  - `x86/libpatch-native.so`
  - `x86_64/libpatch-native.so`

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-native</artifactId>
    <version>1.3.2</version>
    <type>aar</type>
</dependency>
```

### Gradle 依赖

```gradle
implementation 'io.github.706412584:patch-native:1.3.2'
```

### 下载链接

- **AAR**: https://repo1.maven.org/maven2/io/github/706412584/patch-native/1.3.2/patch-native-1.3.2.aar
- **POM**: https://repo1.maven.org/maven2/io/github/706412584/patch-native/1.3.2/patch-native-1.3.2.pom

### 特性

- ✅ 高性能 BsDiff/BsPatch 算法
- ✅ 支持 4 个主流架构
- ✅ 自动降级到 Java 实现
- ✅ JNI 接口封装

## patch-cli 发布说明

### 发布内容

patch-cli 会发布两个 JAR 文件：

1. **patch-cli-{version}.jar** - 普通 JAR（不包含依赖）
   - 适用于作为库依赖使用
   - 需要手动管理依赖

2. **patch-cli-{version}-all.jar** - Fat JAR（包含所有依赖）
   - 适用于命令行直接运行
   - 包含所有依赖，可独立运行
   - **推荐用于 CI/CD 和命令行使用**

### Maven 坐标

```xml
<!-- 普通 JAR -->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-cli</artifactId>
    <version>1.3.2</version>
</dependency>

<!-- Fat JAR（推荐用于命令行） -->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-cli</artifactId>
    <version>1.3.2</version>
    <classifier>all</classifier>
</dependency>
```

### Gradle 依赖

```gradle
// 普通 JAR
implementation 'io.github.706412584:patch-cli:1.3.2'

// Fat JAR（推荐用于命令行）
implementation 'io.github.706412584:patch-cli:1.3.2:all'
```

### 下载链接

- **Fat JAR**: https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2-all.jar
- **普通 JAR**: https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2.jar
- **POM**: https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2.pom

## 使用发布脚本

### 快速发布（推荐）

```bash
# 运行发布脚本
publish-maven.bat

# 选择选项 1: Quick Publish
```

这会：
1. 编译 5 个模块
2. 发布到本地仓库
3. 创建 bundle.zip
4. 上传到 Maven Central

### 完整发布

```bash
# 运行发布脚本
publish-maven.bat

# 选择选项 2: Full Publish
```

这会：
1. 清理构建
2. 完整编译项目
3. 发布到本地仓库
4. 创建 bundle.zip
5. 上传到 Maven Central

## 发布后验证

### 1. 检查部署状态

```bash
# 运行发布脚本
publish-maven.bat

# 选择选项 3: Check deployment status
# 输入 deployment ID
```

### 2. 检查 Maven Central 同步

```bash
# 运行发布脚本
publish-maven.bat

# 选择选项 4: Check Maven Central sync status
```

或者手动检查：

```bash
# 检查 patch-cli
curl -I https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2-all.jar

# 应该返回 200 OK
```

### 3. 测试下载

```bash
# 下载 fat JAR
wget https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2-all.jar

# 测试运行
java -jar patch-cli-1.3.2-all.jar --version

# 应该输出版本信息
```

## 常见问题

### Q: 为什么需要发布 patch-native？

A: patch-native 提供高性能的 Native 引擎：
- BsDiff 算法的 C++ 实现，比 Java 快 2-3 倍
- 支持大文件的二进制差异计算
- 包含预编译的 SO 库，用户无需编译
- 自动降级机制，Native 不可用时使用 Java 实现

### Q: patch-native 包含哪些架构的 SO 库？

A: 包含 4 个主流架构：
- `arm64-v8a` - 64 位 ARM（主流手机）
- `armeabi-v7a` - 32 位 ARM（旧手机）
- `x86` - 32 位 x86（模拟器）
- `x86_64` - 64 位 x86（模拟器）

### Q: 为什么需要发布 patch-cli？

A: patch-cli 是独立的命令行工具，用户可以：
- 直接下载 JAR 文件使用
- 在 CI/CD 中集成
- 在服务器端生成补丁
- 不需要安装 Android SDK

### Q: fat JAR 和普通 JAR 有什么区别？

A: 
- **Fat JAR** (`-all.jar`): 包含所有依赖，可以直接运行
- **普通 JAR**: 只包含 patch-cli 代码，需要手动管理依赖

### Q: 如何验证 patch-cli 是否正确发布？

A: 
1. 访问 Maven Central 仓库链接
2. 下载 fat JAR 并运行 `--version` 命令
3. 使用 Maven/Gradle 依赖测试

### Q: 发布失败怎么办？

A: 
1. 检查 `gradle.properties` 中的凭证
2. 确保版本号正确
3. 查看 `deployment_response.json` 错误信息
4. 使用选项 5 清理失败的部署

## 发布检查清单

发布前检查：

- [ ] 更新版本号（`gradle.properties` 和 `maven-publish.gradle`）
- [ ] 更新 CHANGELOG
- [ ] 运行测试：`gradlew test`
- [ ] 编译 fat JAR：`gradlew :patch-cli:fatJar`
- [ ] 测试 fat JAR：`java -jar patch-cli/build/libs/patch-cli-*-all.jar --version`
- [ ] 检查 Maven 凭证

发布后检查：

- [ ] 访问 Maven Central 确认文件存在
- [ ] 下载并测试 fat JAR
- [ ] 更新 README 中的版本号
- [ ] 创建 GitHub Release
- [ ] 更新文档中的下载链接

## 版本历史

| 版本 | 发布日期 | 包含 patch-cli | 说明 |
|------|---------|---------------|------|
| 1.3.1 | 2026-01-18 | ❌ | 未包含 patch-cli |
| 1.3.2 | 2026-01-19 | ✅ | 添加 patch-cli 发布 |

## 相关文件

- `maven-central/publish.bat` - 发布脚本
- `patch-cli/build.gradle` - patch-cli 构建配置
- `maven-publish.gradle` - Maven 发布通用配置
- `gradle.properties` - 版本号和凭证配置

---

**最后更新**: 2026-01-19  
**版本**: 1.3.3
