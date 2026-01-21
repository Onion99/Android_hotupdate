const express = require('express');
const router = express.Router();
const path = require('path');
const db = require('../models/database');

// 检查更新
router.get('/check-update', async (req, res) => {
  try {
    const { version, deviceId, deviceModel, osVersion, appId, currentPatchVersion } = req.query;

    if (!version) {
      return res.status(400).json({ error: '版本号不能为空' });
    }

    // 获取应用配置（如果提供了 appId）
    let appConfig = null;
    if (appId) {
      appConfig = await db.get(`
        SELECT 
          require_signature, 
          require_encryption,
          force_update_enabled,
          latest_version,
          force_update_url,
          force_update_message
        FROM apps
        WHERE app_id = ? AND status = 'active'
      `, [appId]);
      
      // 检查是否需要强制大版本更新
      if (appConfig && appConfig.force_update_enabled === 1 && appConfig.latest_version) {
        if (compareVersion(version, appConfig.latest_version) < 0) {
          return res.json({
            code: 0,
            message: '发现新版本',
            data: {
              hasUpdate: false,
              forceUpdate: true,
              latestVersion: appConfig.latest_version,
              downloadUrl: appConfig.force_update_url || '',
              message: appConfig.force_update_message || '发现新版本，请更新到最新版本',
              securityConfig: {
                requireSignature: appConfig.require_signature === 1,
                requireEncryption: appConfig.require_encryption === 1
              }
            }
          });
        }
      }
    }

    // 确定当前版本（优先使用补丁版本，否则使用应用版本）
    const currentVersion = currentPatchVersion || version;

    console.log('🔍 检查更新请求参数:');
    console.log('  - version:', version);
    console.log('  - currentPatchVersion:', currentPatchVersion);
    console.log('  - currentVersion:', currentVersion);
    console.log('  - appId:', appId);
    console.log('  - deviceId:', deviceId);

    // 查找最新的可用补丁
    let query = `
      SELECT p.* FROM patches p
      WHERE p.status = 'active'
        AND p.base_version = ?
    `;
    const params = [currentVersion];

    // 如果提供了 appId，只查找该应用的补丁
    if (appId && appConfig) {
      // 使用 JOIN 来关联 apps 表
      query = `
        SELECT p.* FROM patches p
        INNER JOIN apps a ON p.app_id = a.id
        WHERE p.status = 'active'
          AND p.base_version = ?
          AND a.app_id = ?
      `;
      params.push(appId);
      
      console.log('📋 应用配置信息:');
      console.log('  - require_signature:', appConfig.require_signature);
      console.log('  - require_encryption:', appConfig.require_encryption);
    }

    query += ` ORDER BY p.created_at DESC LIMIT 1`;
    
    console.log('🔎 查询补丁 SQL:', query);
    console.log('📝 查询参数:', params);

    const patch = await db.get(query, params);
    
    console.log('📦 查询结果:', patch ? `找到补丁 ID: ${patch.id}, patch_id: ${patch.patch_id}` : '未找到补丁');
    
    // 额外调试：查看所有可用补丁
    const allPatches = await db.query(`
      SELECT p.id, p.patch_id, p.version, p.base_version, p.status, a.app_id, a.app_name
      FROM patches p
      INNER JOIN apps a ON p.app_id = a.id
      WHERE p.status = 'active'
    `);
    console.log('📊 数据库中所有活跃补丁:', JSON.stringify(allPatches, null, 2));

    if (!patch) {
      return res.json({
        code: 0,
        message: '当前已是最新版本',
        data: {
          hasUpdate: false,
          securityConfig: appConfig ? {
            requireSignature: appConfig.require_signature === 1,
            requireEncryption: appConfig.require_encryption === 1
          } : null
        }
      });
    }

    // 检查灰度发布
    if (patch.rollout_percentage < 100) {
      // 简单的灰度策略：基于设备 ID 的哈希值
      if (deviceId) {
        const hash = parseInt(deviceId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0));
        const percentage = hash % 100;
        if (percentage >= patch.rollout_percentage) {
          return res.json({
            code: 0,
            message: '当前已是最新版本',
            data: {
              hasUpdate: false,
              securityConfig: appConfig ? {
                requireSignature: appConfig.require_signature === 1,
                requireEncryption: appConfig.require_encryption === 1
              } : null
            }
          });
        }
      }
    }

    // 构建下载 URL
    const downloadUrl = `${req.protocol}://${req.get('host')}/api/client/download/${patch.id}`;

    res.json({
      code: 0,
      message: '发现新版本',
      data: {
        hasUpdate: true,
        patchInfo: {
          patchId: patch.patch_id,
          patchVersion: patch.version,
          targetAppVersion: patch.version,
          packageName: '', // 可选字段
          downloadUrl,
          fileSize: patch.file_size,
          md5: patch.md5,
          createTime: new Date(patch.created_at).getTime(),
          description: patch.description || ''
        },
        securityConfig: appConfig ? {
          requireSignature: appConfig.require_signature === 1,
          requireEncryption: appConfig.require_encryption === 1
        } : null
      }
    });
  } catch (error) {
    console.error('检查更新失败:', error);
    res.status(500).json({ 
      code: -1,
      message: '检查更新失败',
      data: null
    });
  }
});

// 版本比较函数
function compareVersion(v1, v2) {
  const parts1 = v1.split('.').map(Number);
  const parts2 = v2.split('.').map(Number);
  
  for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
    const part1 = parts1[i] || 0;
    const part2 = parts2[i] || 0;
    
    if (part1 < part2) return -1;
    if (part1 > part2) return 1;
  }
  
  return 0;
}

// 下载补丁
router.get('/download/:id', async (req, res) => {
  try {
    const patch = await db.get(
      'SELECT * FROM patches WHERE id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    if (patch.status !== 'active') {
      return res.status(403).json({ error: '补丁不可用' });
    }

    // 记录下载
    const { deviceId, appVersion, deviceModel, osVersion } = req.query;
    const ipAddress = req.ip || req.connection.remoteAddress;

    await db.run(`
      INSERT INTO downloads (
        patch_id, device_id, app_version, device_model,
        os_version, ip_address, success
      ) VALUES (?, ?, ?, ?, ?, ?, 1)
    `, [
      patch.id,
      deviceId || null,
      appVersion || null,
      deviceModel || null,
      osVersion || null,
      ipAddress
    ]);

    // 更新下载计数
    await db.run(
      'UPDATE patches SET download_count = download_count + 1 WHERE id = ?',
      [patch.id]
    );

    // 发送文件
    res.download(patch.file_path, patch.file_name, (err) => {
      if (err) {
        console.error('下载失败:', err);
      }
    });
  } catch (error) {
    console.error('下载补丁失败:', error);
    res.status(500).json({ error: '下载补丁失败' });
  }
});

// 上报应用结果
router.post('/report', async (req, res) => {
  try {
    const { patchId, success, errorMessage, deviceId } = req.body;

    if (!patchId) {
      return res.status(400).json({ error: '补丁 ID 不能为空' });
    }

    // 查找补丁
    const patch = await db.get(
      'SELECT id FROM patches WHERE patch_id = ?',
      [patchId]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 更新统计
    if (success) {
      await db.run(
        'UPDATE patches SET success_count = success_count + 1 WHERE id = ?',
        [patch.id]
      );
    } else {
      await db.run(
        'UPDATE patches SET fail_count = fail_count + 1 WHERE id = ?',
        [patch.id]
      );
    }

    // 记录详细信息
    await db.run(`
      UPDATE downloads
      SET success = ?, error_message = ?
      WHERE patch_id = ? AND device_id = ?
      ORDER BY created_at DESC
      LIMIT 1
    `, [success ? 1 : 0, errorMessage || null, patch.id, deviceId]);

    res.json({ message: '上报成功' });
  } catch (error) {
    console.error('上报失败:', error);
    res.status(500).json({ error: '上报失败' });
  }
});

// 获取当前补丁信息
router.get('/current-patch', async (req, res) => {
  try {
    const { appId, deviceId } = req.query;

    if (!appId || !deviceId) {
      return res.status(400).json({ error: '应用 ID 和设备 ID 不能为空' });
    }

    // 查找该设备最近成功应用的补丁
    const download = await db.get(`
      SELECT 
        d.patch_id,
        d.app_version,
        d.created_at as applied_at,
        p.patch_id as patch_identifier,
        p.version as patch_version,
        p.base_version,
        p.description,
        p.file_size,
        p.md5
      FROM downloads d
      JOIN patches p ON d.patch_id = p.id
      WHERE d.device_id = ?
        AND p.app_id = (SELECT id FROM apps WHERE app_id = ?)
        AND d.success = 1
      ORDER BY d.created_at DESC
      LIMIT 1
    `, [deviceId, appId]);

    if (!download) {
      return res.json({
        hasAppliedPatch: false,
        message: '未找到已应用的补丁'
      });
    }

    res.json({
      hasAppliedPatch: true,
      patch: {
        patchId: download.patch_identifier,
        patchVersion: download.patch_version,
        baseVersion: download.base_version,
        appVersion: download.app_version,
        description: download.description,
        fileSize: download.file_size,
        md5: download.md5,
        appliedAt: download.applied_at
      }
    });
  } catch (error) {
    console.error('获取当前补丁信息失败:', error);
    res.status(500).json({ error: '获取当前补丁信息失败' });
  }
});

// 调试端点：查看所有补丁和应用信息
router.get('/debug/patches', async (req, res) => {
  try {
    const patches = await db.query(`
      SELECT 
        p.id as patch_db_id,
        p.patch_id,
        p.version,
        p.base_version,
        p.status,
        p.app_id as patch_app_id,
        a.id as app_db_id,
        a.app_id,
        a.app_name,
        a.status as app_status
      FROM patches p
      LEFT JOIN apps a ON p.app_id = a.id
      ORDER BY p.created_at DESC
    `);
    
    const apps = await db.query(`
      SELECT id, app_id, app_name, status
      FROM apps
    `);
    
    res.json({
      patches,
      apps,
      summary: {
        totalPatches: patches.length,
        activePatches: patches.filter(p => p.status === 'active').length,
        totalApps: apps.length,
        activeApps: apps.filter(a => a.app_status === 'active').length
      }
    });
  } catch (error) {
    console.error('调试查询失败:', error);
    res.status(500).json({ error: '调试查询失败', details: error.message });
  }
});

module.exports = router;
