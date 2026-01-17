# 发布到 Maven Central 指南

## 前提条件

### 1. 注册 Sonatype 账号
1. 访问 https://central.sonatype.com/
2. 点击右上角 "Sign Up" 注册账号
3. 验证邮箱

### 2. 创建命名空间
1. 登录后，点击 "Namespaces"
2. 添加命名空间：`io.github.706412584`
3. 验证 GitHub 所有权（会要求你创建一个特定的 GitHub 仓库或在现有仓库添加文件）

### 3. 生成 GPG 密钥（用于签名）

```bash
# 生成密钥
gpg --gen-key

# 列出密钥
gpg --list-keys

# 导出公钥到密钥服务器
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# 导出私钥（用于签名）
gpg --export-secret-keys YOUR_KEY_ID > secring.gpg
```

### 4. 获取用户令牌
1. 登录 https://central.sonatype.com/
2. 点击右上角头像 → "View Account"
3. 点击 "Generate User Token"
4. 保存 Username 和 Password（这是你的 OSSRH 凭证）

## 配置

### 方式 1：使用 gradle.properties（推荐用于本地开发）

在项目根目录或 `~/.gradle/gradle.properties` 中添加：

```properties
# Sonatype 凭证（从 User Token 获取）
ossrhUsername=YOUR_USERNAME
ossrhPassword=YOUR_PASSWORD

# GPG 签名配置
signing.keyId=YOUR_KEY_ID_LAST_8_CHARS
signing.password=YOUR_GPG_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

**注意**：不要将 `gradle.properties` 提交到 Git！已添加到 `.gitignore`。

### 方式 2：使用环境变量（推荐用于 CI/CD）

```bash
export OSSRH_USERNAME=your_username
export OSSRH_PASSWORD=your_password
export SIGNING_KEY_ID=your_key_id
export SIGNING_PASSWORD=your_gpg_password
export SIGNING_SECRET_KEY_RING_FILE=/path/to/secring.gpg
```

## 发布步骤

### 1. 更新版本号
在 `maven-publish.gradle` 中更新 `pomVersion`：
```gradle
pomVersion = '1.2.8'
```

### 2. 构建并发布
```bash
# 清理并构建
./gradlew clean build

# 发布到 Maven Central Staging
./gradlew publishAllPublicationsToSonatypeRepository

# 或者发布单个模块
./gradlew :patch-core:publishMavenPublicationToSonatypeRepository
./gradlew :update:publishReleasePublicationToSonatypeRepository
```

### 3. 在 Sonatype 中发布

#### 使用 Web 界面：
1. 登录 https://s01.oss.sonatype.org/
2. 点击左侧 "Staging Repositories"
3. 找到你的仓库（通常以 `iogithub706412584-` 开头）
4. 选中后点击 "Close"（会进行验证）
5. 验证通过后点击 "Release"

#### 或使用 Gradle 插件（需要额外配置）：
```bash
./gradlew closeAndReleaseRepository
```

### 4. 等待同步
- 发布后，大约 10-30 分钟会同步到 Maven Central
- 可以在 https://central.sonatype.com/artifact/io.github.706412584/patch-core 查看

## 使用发布的库

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("io.github.706412584:patch-core:1.2.8")
    implementation("io.github.706412584:update:1.2.8")
}
```

### Gradle (Groovy)
```groovy
dependencies {
    implementation 'io.github.706412584:patch-core:1.2.8'
    implementation 'io.github.706412584:update:1.2.8'
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-core</artifactId>
    <version>1.2.8</version>
</dependency>
```

## 常见问题

### 1. 签名失败
- 确保 GPG 密钥已正确生成和导出
- 检查 `signing.keyId` 是否正确（应该是密钥 ID 的最后 8 位）
- 确保 `secring.gpg` 文件路径正确

### 2. 认证失败
- 确保使用的是 User Token 的 Username 和 Password，不是网站登录密码
- 检查凭证是否正确配置在 `gradle.properties` 或环境变量中

### 3. 命名空间验证失败
- 确保已在 Sonatype 中验证了 GitHub 所有权
- 命名空间必须是 `io.github.YOUR_GITHUB_USERNAME`

### 4. POM 验证失败
- 确保 POM 包含所有必需字段：name, description, url, licenses, developers, scm
- 检查 `maven-publish.gradle` 中的配置是否完整

## 参考资源

- [Sonatype Central 官方文档](https://central.sonatype.org/publish/publish-guide/)
- [Maven Central 发布指南](https://central.sonatype.org/publish/publish-gradle/)
- [GPG 签名指南](https://central.sonatype.org/publish/requirements/gpg/)
