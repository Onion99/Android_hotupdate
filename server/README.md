# 补丁服务端部署文档

## 📦 架构说明

本项目使用 **GitHub Releases** 作为补丁托管服务，具有以下优势：

- ✅ 完全免费
- ✅ 无限流量
- ✅ 全球 CDN 加速
- ✅ 版本管理
- ✅ 自动化发布

## 🚀 部署方式

### 方式 1: 自动发布（推荐）

使用 GitHub Actions 自动构建和发布补丁。

#### 手动触发发布

1. 进入 GitHub 仓库
2. 点击 **Actions** 标签
3. 选择 **Release Patch** workflow
4. 点击 **Run workflow**
5. 填写参数：
   - **version**: 补丁版本号（如 1.4.1）
   - **base_version**: 基础版本号（如 1.4.0）
   - **description**: 更新说明
6. 点击 **Run workflow** 开始发布

#### 标签触发发布

```bash
# 创建标签
git tag -a v1.4.1 -m "Release v1.4.1"

# 推送标签
git push origin v1.4.1
```

### 方式 2: 手动发布

1. **生成补丁文件**
   ```bash
   # 使用 patch-cli 生成补丁
   java -jar patch-cli/build/libs/patch-cli-1.3.2-all.jar \
     --old app-v1.4.0.apk \
     --new app-v1.4.1.apk \
     --output patch-v1.4.1.zip \
     --sign
   ```

2. **创建 Release**
   - 进入 GitHub 仓库
   - 点击 **Releases** → **Draft a new release**
   - 填写版本号（如 v1.4.1）
   - 上传补丁文件
   - 发布

3. **更新 version.json**
   ```bash
   # 编辑 version.json
   vim version.json
   
   # 提交更改
   git add version.json
   git commit -m "chore: update version.json for v1.4.1"
   git push
   ```

## 📋 version.json 格式

```json
{
  "latest_version": "1.4.1",
  "min_version": "1.4.0",
  "update_url": "https://github.com/706412584/Android_hotupdate/releases",
  "patches": [
    {
      "version": "1.4.1",
      "patch_id": "patch_1.4.1",
      "base_version": "1.4.0",
      "download_url": "https://github.com/.../patch-v1.4.1.zip",
      "md5": "abc123...",
      "size": 1024000,
      "description": "修复说明",
      "force_update": false,
      "create_time": "2025-01-19T10:00:00Z"
    }
  ]
}
```

## 🔌 客户端集成

### 1. 添加网络权限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. 检查更新

```kotlin
// 获取版本信息
val versionUrl = "https://raw.githubusercontent.com/706412584/Android_hotupdate/main/version.json"

val client = OkHttpClient()
val request = Request.Builder()
    .url(versionUrl)
    .build()

client.newCall(request).enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        val json = response.body?.string()
        val versionInfo = parseVersionJson(json)
        
        // 检查是否有新版本
        if (versionInfo.latestVersion > currentVersion) {
            // 下载补丁
            downloadPatch(versionInfo.patches[0].downloadUrl)
        }
    }
    
    override fun onFailure(call: Call, e: IOException) {
        Log.e("Update", "Failed to check update", e)
    }
})
```

### 3. 下载补丁

```kotlin
fun downloadPatch(url: String) {
    val request = Request.Builder()
        .url(url)
        .build()
    
    client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val patchFile = File(context.getExternalFilesDir(null), "patch.zip")
            patchFile.outputStream().use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
            
            // 应用补丁
            applyPatch(patchFile)
        }
        
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Update", "Failed to download patch", e)
        }
    })
}
```

## 🌐 CDN 加速（可选）

### 使用 jsDelivr CDN

```kotlin
// 原始 URL
val originalUrl = "https://raw.githubusercontent.com/706412584/Android_hotupdate/main/version.json"

// CDN 加速 URL
val cdnUrl = "https://cdn.jsdelivr.net/gh/706412584/Android_hotupdate@main/version.json"
```

### 使用 Cloudflare Pages

1. 登录 [Cloudflare Pages](https://pages.cloudflare.com/)
2. 连接 GitHub 仓库
3. 部署静态文件
4. 获得加速域名

## 📊 监控和统计

### GitHub Insights

- 查看 Release 下载次数
- 查看流量统计
- 查看用户地理分布

### 自定义统计（可选）

使用 Google Analytics 或其他统计工具：

```kotlin
// 记录补丁下载
analytics.logEvent("patch_download") {
    param("version", "1.4.1")
    param("source", "github")
}
```

## 🔒 安全建议

1. **补丁签名验证**
   - 使用 APK 签名验证补丁完整性
   - 检查 MD5/SHA256 哈希值

2. **HTTPS 传输**
   - 所有请求使用 HTTPS
   - 验证 SSL 证书

3. **版本控制**
   - 检查最小支持版本
   - 防止降级攻击

## 🚀 高级功能

### 灰度发布

```json
{
  "patches": [
    {
      "version": "1.4.1",
      "rollout_percentage": 10,
      "target_users": ["user_id_1", "user_id_2"]
    }
  ]
}
```

### A/B 测试

```json
{
  "experiments": [
    {
      "name": "new_feature",
      "variants": ["A", "B"],
      "percentage": [50, 50]
    }
  ]
}
```

## 📞 技术支持

- GitHub Issues: https://github.com/706412584/Android_hotupdate/issues
- 文档: https://github.com/706412584/Android_hotupdate/wiki
