const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs').promises;
const db = require('../models/database');
const PatchMerger = require('../utils/patchMerger');
const { authenticateToken } = require('../middleware/auth');

// 初始化 PatchMerger
const patchCliPath = path.join(__dirname, '../../../tools/patch-cli.jar');
const patchMerger = new PatchMerger(patchCliPath);

// 合并补丁缓存目录
const MERGED_CACHE_DIR = path.join(process.env.UPLOAD_DIR || './uploads', 'merged');

/**
 * 确保缓存目录存在
 */
async function ensureCacheDir() {
  try {
    await fs.access(MERGED_CACHE_DIR);
  } catch {
    await fs.mkdir(MERGED_CACHE_DIR, { recursive: true });
  }
}

/**
 * 生成缓存键
 */
function getCacheKey(appId, fromVersion, toVersion) {
  return `${appId}_${fromVersion}_to_${toVersion}.zip`;
}

/**
 * 检查缓存是否存在
 */
async function checkCache(cacheKey) {
  const cachePath = path.join(MERGED_CACHE_DIR, cacheKey);
  try {
    await fs.access(cachePath);
    const stats = await fs.stat(cachePath);
    return {
      exists: true,
      path: cachePath,
      size: stats.size
    };
  } catch {
    return { exists: false };
  }
}

/**
 * 客户端检查更新（支持补丁链）
 * 
 * 查询参数：
 * - appId: 应用 ID
 * - currentVersion: 当前版本（应用版本或补丁版本）
 * - deviceId: 设备 ID（用于灰度发布）
 * - preferMerged: 是否优先使用合并补丁（默认 true）
 */
router.get('/check-update-with-chain', async (req, res) => {
  try {
    const { appId, currentVersion, deviceId, preferMerged = 'true' } = req.query;

    if (!appId || !currentVersion) {
      return res.status(400).json({ error: '应用 ID 和当前版本不能为空' });
    }

    // 获取应用信息
    const app = await db.get(`
      SELECT id, app_id, app_name, require_signature, require_encryption
      FROM apps
      WHERE app_id = ? AND status = 'active' AND review_status = 'approved'
    `, [appId]);

    if (!app) {
      return res.status(404).json({ error: '应用不存在或未激活' });
    }

    // 查找最新的可用版本
    const latestPatch = await db.get(`
      SELECT version, base_version
      FROM patches
      WHERE app_id = ? AND status = 'active'
      ORDER BY created_at DESC
      LIMIT 1
    `, [app.id]);

    if (!latestPatch) {
      return res.json({
        hasUpdate: false,
        message: '暂无可用补丁'
      });
    }

    const targetVersion = latestPatch.version;

    // 如果当前版本已是最新，无需更新
    if (currentVersion === targetVersion) {
      return res.json({
        hasUpdate: false,
        message: '当前已是最新版本'
      });
    }

    // 查找补丁链
    const patchChain = await patchMerger.findPatchChain(
      db,
      appId,
      currentVersion,
      targetVersion
    );

    if (!patchChain || patchChain.length === 0) {
      return res.json({
        hasUpdate: false,
        message: '未找到可用的更新路径',
        details: `无法从 ${currentVersion} 更新到 ${targetVersion}`
      });
    }

    // 计算补丁链总大小
    const totalSize = patchChain.reduce((sum, patch) => sum + patch.fileSize, 0);

    // 检查是否有缓存的合并补丁
    const cacheKey = getCacheKey(appId, currentVersion, targetVersion);
    const cache = await checkCache(cacheKey);

    // 决定返回策略
    const useMerged = preferMerged === 'true' && (cache.exists || patchChain.length > 1);

    if (useMerged && cache.exists) {
      // 返回缓存的合并补丁
      return res.json({
        hasUpdate: true,
        updateType: 'merged',
        patch: {
          fromVersion: currentVersion,
          toVersion: targetVersion,
          downloadUrl: `${req.protocol}://${req.get('host')}/api/patch-merge/download-merged?appId=${appId}&from=${currentVersion}&to=${targetVersion}`,
          fileSize: cache.size,
          patchCount: patchChain.length,
          description: `合并补丁：${currentVersion} → ${targetVersion}（包含 ${patchChain.length} 个补丁）`
        },
        securityConfig: {
          requireSignature: app.require_signature === 1,
          requireEncryption: app.require_encryption === 1
        }
      });
    } else if (useMerged && patchChain.length > 1) {
      // 需要生成合并补丁
      return res.json({
        hasUpdate: true,
        updateType: 'merged_pending',
        message: '需要生成合并补丁',
        patch: {
          fromVersion: currentVersion,
          toVersion: targetVersion,
          patchCount: patchChain.length,
          estimatedSize: totalSize,
          generateUrl: `${req.protocol}://${req.get('host')}/api/patch-merge/generate?appId=${appId}&from=${currentVersion}&to=${targetVersion}`,
          description: `将合并 ${patchChain.length} 个补丁`
        },
        securityConfig: {
          requireSignature: app.require_signature === 1,
          requireEncryption: app.require_encryption === 1
        }
      });
    } else {
      // 返回补丁链（客户端依次下载）
      return res.json({
        hasUpdate: true,
        updateType: 'chain',
        patchChain: patchChain.map(patch => ({
          patchId: patch.patchId,
          fromVersion: patch.baseVersion,
          toVersion: patch.version,
          downloadUrl: `${req.protocol}://${req.get('host')}/api/client/download/${patch.id}`,
          fileSize: patch.fileSize,
          md5: patch.md5
        })),
        totalSize,
        securityConfig: {
          requireSignature: app.require_signature === 1,
          requireEncryption: app.require_encryption === 1
        }
      });
    }
  } catch (error) {
    console.error('检查更新失败:', error);
    res.status(500).json({ error: '检查更新失败: ' + error.message });
  }
});

/**
 * 生成合并补丁
 * 
 * 查询参数：
 * - appId: 应用 ID
 * - from: 起始版本
 * - to: 目标版本
 */
router.post('/generate', authenticateToken, async (req, res) => {
  try {
    const { appId, from, to } = req.query;

    if (!appId || !from || !to) {
      return res.status(400).json({ error: '缺少必要参数' });
    }

    // 获取应用信息
    const app = await db.get(`
      SELECT id, app_id, owner_id
      FROM apps
      WHERE app_id = ? AND status = 'active'
    `, [appId]);

    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 检查权限（只有应用所有者或管理员可以生成）
    if (req.user.role !== 'admin' && app.owner_id !== req.user.id) {
      return res.status(403).json({ error: '无权操作此应用' });
    }

    // 检查缓存
    const cacheKey = getCacheKey(appId, from, to);
    const cache = await checkCache(cacheKey);

    if (cache.exists) {
      return res.json({
        message: '合并补丁已存在',
        cached: true,
        downloadUrl: `${req.protocol}://${req.get('host')}/api/patch-merge/download-merged?appId=${appId}&from=${from}&to=${to}`,
        fileSize: cache.size
      });
    }

    // 查找补丁链
    const patchChain = await patchMerger.findPatchChain(db, appId, from, to);

    if (!patchChain || patchChain.length === 0) {
      return res.status(404).json({ error: '未找到补丁链' });
    }

    // 如果只有一个补丁，直接复制
    if (patchChain.length === 1) {
      await ensureCacheDir();
      const cachePath = path.join(MERGED_CACHE_DIR, cacheKey);
      await fs.copyFile(patchChain[0].filePath, cachePath);

      const stats = await fs.stat(cachePath);
      return res.json({
        message: '补丁已缓存',
        patchCount: 1,
        downloadUrl: `${req.protocol}://${req.get('host')}/api/patch-merge/download-merged?appId=${appId}&from=${from}&to=${to}`,
        fileSize: stats.size
      });
    }

    // 需要原始 APK 文件来生成合并补丁
    // 这里需要应用提供基础 APK 和目标 APK
    return res.status(501).json({
      error: '多补丁合并需要原始 APK 文件',
      message: '请使用 generateMergedPatch 方法并提供基础 APK 和目标 APK',
      patchChain: patchChain.map(p => ({
        from: p.baseVersion,
        to: p.version,
        size: p.fileSize
      }))
    });

  } catch (error) {
    console.error('生成合并补丁失败:', error);
    res.status(500).json({ error: '生成合并补丁失败: ' + error.message });
  }
});

/**
 * 下载合并补丁
 */
router.get('/download-merged', async (req, res) => {
  try {
    const { appId, from, to } = req.query;

    if (!appId || !from || !to) {
      return res.status(400).json({ error: '缺少必要参数' });
    }

    const cacheKey = getCacheKey(appId, from, to);
    const cache = await checkCache(cacheKey);

    if (!cache.exists) {
      return res.status(404).json({ error: '合并补丁不存在，请先生成' });
    }

    // 记录下载（可选）
    const { deviceId } = req.query;
    if (deviceId) {
      // 记录到数据库...
    }

    // 发送文件
    res.download(cache.path, cacheKey, (err) => {
      if (err) {
        console.error('下载失败:', err);
      }
    });
  } catch (error) {
    console.error('下载合并补丁失败:', error);
    res.status(500).json({ error: '下载失败' });
  }
});

/**
 * 清理过期的合并补丁缓存
 */
router.post('/clean-cache', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }

    const { maxAge = 7 } = req.body; // 默认保留 7 天

    await ensureCacheDir();
    const files = await fs.readdir(MERGED_CACHE_DIR);
    
    let cleanedCount = 0;
    let freedSpace = 0;
    const now = Date.now();
    const maxAgeMs = maxAge * 24 * 60 * 60 * 1000;

    for (const file of files) {
      const filePath = path.join(MERGED_CACHE_DIR, file);
      const stats = await fs.stat(filePath);
      
      if (now - stats.mtimeMs > maxAgeMs) {
        freedSpace += stats.size;
        await fs.unlink(filePath);
        cleanedCount++;
      }
    }

    res.json({
      message: '缓存清理完成',
      cleanedCount,
      freedSpace: (freedSpace / 1024 / 1024).toFixed(2) + ' MB'
    });
  } catch (error) {
    console.error('清理缓存失败:', error);
    res.status(500).json({ error: '清理缓存失败' });
  }
});

/**
 * 获取缓存统计
 */
router.get('/cache-stats', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'admin') {
      return res.status(403).json({ error: '需要管理员权限' });
    }

    await ensureCacheDir();
    const files = await fs.readdir(MERGED_CACHE_DIR);
    
    let totalSize = 0;
    const cacheList = [];

    for (const file of files) {
      const filePath = path.join(MERGED_CACHE_DIR, file);
      const stats = await fs.stat(filePath);
      totalSize += stats.size;
      
      cacheList.push({
        file,
        size: stats.size,
        created: stats.birthtime,
        modified: stats.mtime
      });
    }

    res.json({
      totalFiles: files.length,
      totalSize: (totalSize / 1024 / 1024).toFixed(2) + ' MB',
      cacheList: cacheList.sort((a, b) => b.modified - a.modified)
    });
  } catch (error) {
    console.error('获取缓存统计失败:', error);
    res.status(500).json({ error: '获取缓存统计失败' });
  }
});

module.exports = router;
