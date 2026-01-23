# Maven Central 发布指南

## 快速开�?

运行发布工具�?

```bash
cd maven-central
publish.bat
```

选择操作�?
1. **快速发�?* - 构建并上�?patch-core �?patch-generator-android（推荐，�?20-30 秒）
2. **完整发布** - 清理、完整构建、上传（�?2-3 分钟�?
3. **检查部署状�?* - 查看部署验证状�?
4. **检�?Maven Central** - 查看是否已同步到 Maven Central
5. **清空所有部�?* - 删除所有未发布的部�?

## 发布流程

### 1. 运行发布脚本

```bash
cd maven-central
publish.bat
```

选择 "1" 进行快速发布�?

### 2. �?Central Portal 中发�?

1. 访问：https://central.sonatype.com/publishing/deployments
2. 找到刚上传的 deployment（名称：patch-core-1.3.0�?
3. 等待状态变�?"VALIDATED"（约 2-5 分钟�?
4. 点击 "Publish" 按钮
5. 确认发布

### 3. 等待同步

发布后约 15-30 分钟会同步到 Maven Central�?

使用选项 "4" 检查同步状态�?

## 配置信息

### 当前配置

- **Group ID**: `io.github.706412584`
- **Artifacts**: 
  - `patch-core` - 核心补丁算法
  - `patch-generator-android` - Android 补丁生成�?
  - `update` - 热更新核心库（推荐）
- **Version**: `1.3.0`
- **GPG 密钥 ID**: `94CEE4A6C60913C4`
- **密钥密码**: `706412584`

### 文件位置

- **Gradle 配置**: `../gradle.properties`
- **Maven 发布配置**: `../maven-publish.gradle`
- **GPG 私钥**: `../secring.gpg`

## 使用发布的库

### Gradle

```groovy
dependencies {
    // 热更新核心库（推�?- 包含完整功能�?
    implementation 'io.github.706412584:update:1.3.0'
    
    // 或者单独使用：
    
    // 核心补丁算法
    implementation 'io.github.706412584:patch-core:1.3.0'
    
    // Android 补丁生成�?
    implementation 'io.github.706412584:patch-generator-android:1.3.0'
}
```

### Maven

```xml
<!-- 热更新核心库（推�?- 包含完整功能�?-->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>update</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- 或者单独使用： -->

<!-- 核心补丁算法 -->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-core</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- Android 补丁生成�?-->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-generator-android</artifactId>
    <version>1.3.0</version>
</dependency>
```

## 发布新版�?

1. 更新版本号：
   - 编辑 `../maven-publish.gradle`
   - 修改 `pomVersion = '1.3.0'` 为新版本�?

2. 运行发布脚本�?
   ```bash
   cd maven-central
   publish.bat
   ```

3. �?Central Portal 中点�?"Publish"

4. 等待同步完成

## 查看发布的库

- **Central Portal**: 
  - https://central.sonatype.com/artifact/io.github.706412584/patch-core
  - https://central.sonatype.com/artifact/io.github.706412584/patch-generator-android
- **Maven Central**: 
  - https://repo1.maven.org/maven2/io/github/706412584/patch-core/
  - https://repo1.maven.org/maven2/io/github/706412584/patch-generator-android/

## 故障排除

### 签名验证失败

确保 GPG 公钥已上传到密钥服务器：
- keys.openpgp.org
- keyserver.ubuntu.com

检查密钥状态：
```bash
gpg --keyserver keys.openpgp.org --recv-keys 94CEE4A6C60913C4
```

### Bundle 创建失败

确保已成功构建：
```bash
cd ..
gradlew.bat :patch-core:publishMavenPublicationToLocalRepository
```

检查构建产物：
```bash
dir patch-core\build\repo\io\github\706412584\patch-core\1.3.0
```

### 上传失败

检查凭证配置：
- 打开 `../gradle.properties`
- 确认 `ossrhUsername` �?`ossrhPassword` 正确

## 技术细�?

### GPG 签名

使用的密钥：
- **密钥 ID**: `94CEE4A6C60913C4`
- **完整指纹**: `B2F807C073D34C5C6EB075B794CEE4A6C60913C4`
- **类型**: ed25519
- **邮箱**: xcwl <706412584@qq.com>

### Bundle 结构

Bundle 包含完整�?Maven 路径结构�?
```
io/
└── github/
    └── 706412584/
        └── patch-core/
            └── 1.3.0/
                ├── patch-core-1.3.0.jar
                ├── patch-core-1.3.0.jar.asc
                ├── patch-core-1.3.0.pom
                ├── patch-core-1.3.0.pom.asc
                ├── patch-core-1.3.0-sources.jar
                ├── patch-core-1.3.0-sources.jar.asc
                ├── patch-core-1.3.0-javadoc.jar
                ├── patch-core-1.3.0-javadoc.jar.asc
                └── 所有校验和文件 (.md5, .sha1, .sha256, .sha512)
```

### API 端点

- **上传 Bundle**: `https://central.sonatype.com/api/v1/publisher/upload`
- **检查状�?*: `https://central.sonatype.com/api/v1/publisher/status?id={deploymentId}`
- **发布部署**: `https://central.sonatype.com/api/v1/publisher/deployment/{deploymentId}`
- **删除部署**: `https://central.sonatype.com/api/v1/publisher/deployment/{deploymentId}` (DELETE)

## 参考资�?

- [Sonatype Central Portal](https://central.sonatype.com/)
- [Maven Central Repository](https://repo1.maven.org/maven2/)
- [GPG 签名要求](https://central.sonatype.org/publish/requirements/gpg/)
- [keys.openpgp.org](https://keys.openpgp.org/)

