# 补丁合并功能说明

## 概述

补丁合并功能允许服务端智能地合并多个增量补丁，减少客户端的下载和应用次数。

## 功能特性

### 1. 补丁链查找
- 自动查找从当前版本到目标版本的补丁路径
- 使用广度优先搜索算法
- 支持复杂的版本依赖关系

### 2. 智能更新策略
服务端会根据情况返回不同的更新方案：

#### 方案 A: 单补丁更新
```
当前版本: v1.0.0
目标版本: v1.0.1
补丁链: [v1.0.0 → v1.0.1]
策略: 直接下载单个补丁
```

#### 方案 B: 合并补丁更新（推荐）
```
当前版本: v1.0.0
目标版本: v1.0.3
补丁链: [v1.0.0 → v1.0.1, v1.0.1 → v1.0.2, v1.0.2 → v1.0.3]
策略: 服务端合并为一个补丁 v1.0.0 → v1.0.3
优势: 
  - 客户端只需下载一次
  - 只需应用一次补丁
  - 减少出错概率
```

#### 方案 C: 补丁链更新
```
当前版本: v1.0.0
目标版本: v1.0.3
补丁链: [v1.0.0 → v1.0.1, v1.0.1 → v1.0.2, v1.0.2 → v1.0.3]
策略: 客户端依次下载并应用每个补丁
适用场景: 合并补丁未生成或客户端不支持
```

### 3. 缓存机制
- 已生成的合并补丁会被缓存
- 避免重复生成相同的合并补丁
- 支持缓存清理和统计

## API 接口

### 1. 检查更新（支持补丁链）

**接口**: `GET /api/patch-merge/check-update-with-chain`

**参数**:
- `appId`: 应用 ID（必填）
- `currentVersion`: 当前版本（必填）
- `deviceId`: 设备 ID（可选，用于灰度发布）
- `preferMerged`: 是否优先使用合并补丁（可选，默认 true）

**响应示例 - 有合并补丁**:
```json
{
  "hasUpdate": true,
  "updateType": "merged",
  "patch": {
    "fromVersion": "1.0.0",
    "toVersion": "1.0.3",
    "downloadUrl": "https://example.com/api/patch-merge/download-merged?appId=xxx&from=1.0.0&to=1.0.3",
    "fileSize": 5242880,
    "patchCount": 3,
    "description": "合并补丁：1.0.0 → 1.0.3（包含 3 个补丁）"
  },
  "securityConfig": {
    "requireSignature": true,
    "requireEncryption": true
  }
}
```

**响应示例 - 需要生成合并补丁**:
```json
{
  "hasUpdate": true,
  "updateType": "merged_pending",
  "message": "需要生成合并补丁",
  "patch": {
    "fromVersion": "1.0.0",
    "toVersion": "1.0.3",
    "patchCount": 3,
    "estimatedSize": 6291456,
    "generateUrl": "https://example.com/api/patch-merge/generate?appId=xxx&from=1.0.0&to=1.0.3",
    "description": "将合并 3 个补丁"
  }
}
```

**响应示例 - 补丁链**:
```json
{
  "hasUpdate": true,
  "updateType": "chain",
  "patchChain": [
    {
      "patchId": "patch_001",
      "fromVersion": "1.0.0",
      "toVersion": "1.0.1",
      "downloadUrl": "https://example.com/api/client/download/1",
      "fileSize": 2097152,
      "md5": "abc123..."
    },
    {
      "patchId": "patch_002",
      "fromVersion": "1.0.1",
      "toVersion": "1.0.2",
      "downloadUrl": "https://example.com/api/client/download/2",
      "fileSize": 2097152,
      "md5": "def456..."
    }
  ],
  "totalSize": 4194304
}
```

### 2. 生成合并补丁

**接口**: `POST /api/patch-merge/generate`

**权限**: 需要登录，且为应用所有者或管理员

**参数**:
- `appId`: 应用 ID
- `from`: 起始版本
- `to`: 目标版本

**响应**:
```json
{
  "message": "合并补丁已生成",
  "patchCount": 3,
  "downloadUrl": "https://example.com/api/patch-merge/download-merged?appId=xxx&from=1.0.0&to=1.0.3",
  "fileSize": 5242880
}
```

### 3. 下载合并补丁

**接口**: `GET /api/patch-merge/download-merged`

**参数**:
- `appId`: 应用 ID
- `from`: 起始版本
- `to`: 目标版本
- `deviceId`: 设备 ID（可选）

**响应**: 文件下载

### 4. 清理缓存

**接口**: `POST /api/patch-merge/clean-cache`

**权限**: 需要管理员权限

**参数**:
- `maxAge`: 保留天数（默认 7 天）

**响应**:
```json
{
  "message": "缓存清理完成",
  "cleanedCount": 5,
  "freedSpace": "125.50 MB"
}
```

### 5. 缓存统计

**接口**: `GET /api/patch-merge/cache-stats`

**权限**: 需要管理员权限

**响应**:
```json
{
  "totalFiles": 10,
  "totalSize": "250.75 MB",
  "cacheList": [
    {
      "file": "app1_1.0.0_to_1.0.3.zip",
      "size": 5242880,
      "created": "2026-01-20T10:00:00.000Z",
      "modified": "2026-01-20T10:00:00.000Z"
    }
  ]
}
```

## 客户端集成

### Android 客户端示例

```java
// 1. 检查更新
UpdateManager.getInstance().checkUpdateWithChain(new UpdateCallback() {
    @Override
    public void onCheckComplete(boolean hasUpdate, UpdateInfo updateInfo) {
        if (hasUpdate) {
            switch (updateInfo.getUpdateType()) {
                case "merged":
                    // 下载合并补丁
                    downloadMergedPatch(updateInfo.getPatch());
                    break;
                    
                case "merged_pending":
                    // 请求生成合并补丁
                    requestGenerateMergedPatch(updateInfo.getPatch());
                    break;
                    
                case "chain":
                    // 依次下载补丁链
                    downloadPatchChain(updateInfo.getPatchChain());
                    break;
            }
        }
    }
});

// 2. 下载合并补丁
private void downloadMergedPatch(PatchInfo patch) {
    UpdateManager.getInstance().downloadPatch(patch, new DownloadCallback() {
        @Override
        public void onProgress(long current, long total) {
            // 更新进度
        }
        
        @Override
        public void onSuccess(File file) {
            // 应用补丁
            UpdateManager.getInstance().applyPatch(patch);
        }
    });
}
```

## 实现原理

### 补丁链查找算法

使用广度优先搜索（BFS）查找最短补丁路径：

```
起始版本: v1.0.0
目标版本: v1.0.3

数据库中的补丁:
- v1.0.0 → v1.0.1
- v1.0.1 → v1.0.2
- v1.0.2 → v1.0.3
- v1.0.0 → v1.0.2 (跳跃补丁)

BFS 搜索结果:
路径1: v1.0.0 → v1.0.1 → v1.0.2 → v1.0.3 (3个补丁)
路径2: v1.0.0 → v1.0.2 → v1.0.3 (2个补丁) ✓ 最优

返回: 路径2
```

### 补丁合并策略

#### 策略 1: 单补丁直接复制
```
补丁链长度 = 1
操作: 直接复制到缓存目录
```

#### 策略 2: 从原始 APK 生成（推荐）
```
补丁链长度 > 1
需要: 基础版本 APK + 目标版本 APK
操作: 使用 patch-cli 生成完整补丁
优势: 补丁最小，质量最高
```

#### 策略 3: 依次应用后生成（未实现）
```
补丁链长度 > 1
操作: 
  1. 从基础 APK 开始
  2. 依次应用每个补丁
  3. 从最终 APK 生成完整补丁
挑战: 需要实现补丁应用逻辑
```

## 限制和注意事项

### 当前限制

1. **需要原始 APK**
   - 多补丁合并需要基础版本和目标版本的完整 APK
   - 如果没有 APK，只能返回补丁链

2. **patch-cli 依赖**
   - 需要 Java 11+ 环境
   - 需要 patch-cli.jar 工具

3. **存储空间**
   - 缓存的合并补丁会占用磁盘空间
   - 建议定期清理过期缓存

### 最佳实践

1. **版本管理**
   - 保留每个版本的完整 APK
   - 便于生成任意版本间的合并补丁

2. **缓存策略**
   - 预生成常见版本组合的合并补丁
   - 设置合理的缓存过期时间

3. **灰度发布**
   - 先对小部分用户推送合并补丁
   - 验证无问题后全量推送

4. **监控告警**
   - 监控合并补丁的生成成功率
   - 监控下载失败率和应用失败率

## 未来优化方向

1. **智能预生成**
   - 根据用户版本分布，预生成热门补丁组合
   - 减少用户等待时间

2. **增量合并**
   - 实现真正的补丁合并算法
   - 不需要完整 APK

3. **CDN 加速**
   - 将合并补丁上传到 CDN
   - 提高下载速度

4. **差异化策略**
   - 根据网络状况选择合并或链式更新
   - WiFi 环境下载合并补丁，移动网络下载增量链

## 总结

补丁合并功能显著提升了多版本跨越更新的用户体验：

- ✅ 减少下载次数（多个补丁 → 一个补丁）
- ✅ 减少应用次数（多次应用 → 一次应用）
- ✅ 降低失败概率（链式依赖 → 单次操作）
- ✅ 节省流量（合并后的补丁通常更小）
- ✅ 提升成功率（减少中间状态）

**实现难度**: ⭐⭐⭐ (中等)

服务端已实现基础框架，主要工作在于：
1. 保存每个版本的完整 APK
2. 配置 patch-cli 工具
3. 客户端适配新的更新流程
