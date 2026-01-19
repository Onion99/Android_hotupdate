const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const db = require('../models/database');
const { authenticateToken, requireRole } = require('../middleware/auth');

// 配置文件上传
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = path.join(__dirname, '../../uploads/versions');
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'version-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 500 * 1024 * 1024 }, // 500MB
  fileFilter: (req, file, cb) => {
    if (path.extname(file.originalname).toLowerCase() === '.apk') {
      cb(null, true);
    } else {
      cb(new Error('只支持 APK 文件'));
    }
  }
});

// 计算文件 MD5
function calculateMD5(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('md5');
    const stream = fs.createReadStream(filePath);
    stream.on('data', data => hash.update(data));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

// 获取应用的所有版本
router.get('/:appId', authenticateToken, async (req, res) => {
  try {
    const { appId } = req.params;
    const { page = 1, limit = 20 } = req.query;
    const offset = (page - 1) * limit;

    // 检查应用权限
    const app = await db.get(
      'SELECT * FROM apps WHERE app_id = ?',
      [appId]
    );

    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    // 非管理员只能查看自己的应用
    if (req.user.role !== 'admin' && app.owner_id !== req.user.id) {
      return res.status(403).json({ error: '无权访问此应用' });
    }

    // 获取版本列表
    const versions = await db.all(`
      SELECT 
        v.*,
        u.username as creator_name
      FROM app_versions v
      LEFT JOIN users u ON v.created_by = u.id
      WHERE v.app_id = ?
      ORDER BY v.version_code DESC, v.created_at DESC
      LIMIT ? OFFSET ?
    `, [app.id, limit, offset]);

    // 获取总数
    const { total } = await db.get(
      'SELECT COUNT(*) as total FROM app_versions WHERE app_id = ?',
      [app.id]
    );

    res.json({
      versions,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total
      }
    });
  } catch (error) {
    console.error('获取版本列表失败:', error);
    res.status(500).json({ error: '获取版本列表失败' });
  }
});

// 上传新版本
router.post('/:appId/upload', authenticateToken, upload.single('file'), async (req, res) => {
  try {
    const { appId } = req.params;
    const { versionName, versionCode, description, changelog, isForceUpdate, minSupportedVersion, downloadUrl } = req.body;

    if (!req.file) {
      return res.status(400).json({ error: '请上传 APK 文件' });
    }

    if (!versionName || !versionCode) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '版本名称和版本号不能为空' });
    }

    // 检查应用权限
    const app = await db.get(
      'SELECT * FROM apps WHERE app_id = ?',
      [appId]
    );

    if (!app) {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: '应用不存在' });
    }

    if (req.user.role !== 'admin' && app.owner_id !== req.user.id) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: '无权操作此应用' });
    }

    // 检查版本是否已存在
    const existingVersion = await db.get(
      'SELECT id FROM app_versions WHERE app_id = ? AND version_name = ?',
      [app.id, versionName]
    );

    if (existingVersion) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '该版本已存在' });
    }

    // 计算 MD5
    const md5 = await calculateMD5(req.file.path);

    // 插入版本记录
    const result = await db.run(`
      INSERT INTO app_versions (
        app_id, version_name, version_code, file_path, file_name,
        file_size, md5, download_url, description, changelog,
        is_force_update, min_supported_version, created_by
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      app.id,
      versionName,
      parseInt(versionCode),
      req.file.path,
      req.file.originalname,
      req.file.size,
      md5,
      downloadUrl || null,
      description || null,
      changelog || null,
      isForceUpdate === 'true' || isForceUpdate === true ? 1 : 0,
      minSupportedVersion || null,
      req.user.id
    ]);

    // 如果是强制更新，更新应用配置
    if (isForceUpdate === 'true' || isForceUpdate === true) {
      await db.run(`
        UPDATE apps 
        SET 
          force_update_enabled = 1,
          latest_version = ?,
          force_update_url = ?,
          force_update_message = ?
        WHERE id = ?
      `, [
        versionName,
        downloadUrl || `${req.protocol}://${req.get('host')}/api/versions/download/${result.lastID}`,
        `发现新版本 ${versionName}，请更新到最新版本`,
        app.id
      ]);
    }

    // 记录日志
    await db.run(`
      INSERT INTO logs (user_id, username, action, resource_type, resource_id, details, ip_address)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `, [
      req.user.id,
      req.user.username,
      'upload_version',
      'app_version',
      result.lastID,
      JSON.stringify({ appId, versionName, versionCode }),
      req.ip
    ]);

    res.json({
      message: '版本上传成功',
      version: {
        id: result.lastID,
        versionName,
        versionCode: parseInt(versionCode),
        fileSize: req.file.size,
        md5
      }
    });
  } catch (error) {
    console.error('上传版本失败:', error);
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }
    res.status(500).json({ error: '上传版本失败: ' + error.message });
  }
});

// 更新版本信息
router.put('/:id', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    const { description, changelog, isForceUpdate, minSupportedVersion, status, downloadUrl } = req.body;

    // 获取版本信息
    const version = await db.get(`
      SELECT v.*, a.owner_id, a.app_id
      FROM app_versions v
      JOIN apps a ON v.app_id = a.id
      WHERE v.id = ?
    `, [id]);

    if (!version) {
      return res.status(404).json({ error: '版本不存在' });
    }

    // 检查权限
    if (req.user.role !== 'admin' && version.owner_id !== req.user.id) {
      return res.status(403).json({ error: '无权操作此版本' });
    }

    // 更新版本信息
    await db.run(`
      UPDATE app_versions
      SET 
        description = ?,
        changelog = ?,
        is_force_update = ?,
        min_supported_version = ?,
        status = ?,
        download_url = ?,
        updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    `, [
      description || version.description,
      changelog || version.changelog,
      isForceUpdate !== undefined ? (isForceUpdate ? 1 : 0) : version.is_force_update,
      minSupportedVersion || version.min_supported_version,
      status || version.status,
      downloadUrl || version.download_url,
      id
    ]);

    // 如果是强制更新，更新应用配置
    if (isForceUpdate) {
      const finalDownloadUrl = downloadUrl || version.download_url || `${req.protocol}://${req.get('host')}/api/versions/download/${id}`;
      await db.run(`
        UPDATE apps 
        SET 
          force_update_enabled = 1,
          latest_version = ?,
          force_update_url = ?,
          force_update_message = ?
        WHERE id = ?
      `, [
        version.version_name,
        finalDownloadUrl,
        `发现新版本 ${version.version_name}，请更新到最新版本`,
        version.app_id
      ]);
    }

    // 记录日志
    await db.run(`
      INSERT INTO logs (user_id, username, action, resource_type, resource_id, details, ip_address)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `, [
      req.user.id,
      req.user.username,
      'update_version',
      'app_version',
      id,
      JSON.stringify({ isForceUpdate, status }),
      req.ip
    ]);

    res.json({ message: '版本更新成功' });
  } catch (error) {
    console.error('更新版本失败:', error);
    res.status(500).json({ error: '更新版本失败' });
  }
});

// 删除版本
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;

    // 获取版本信息
    const version = await db.get(`
      SELECT v.*, a.owner_id
      FROM app_versions v
      JOIN apps a ON v.app_id = a.id
      WHERE v.id = ?
    `, [id]);

    if (!version) {
      return res.status(404).json({ error: '版本不存在' });
    }

    // 检查权限
    if (req.user.role !== 'admin' && version.owner_id !== req.user.id) {
      return res.status(403).json({ error: '无权删除此版本' });
    }

    // 删除文件
    if (fs.existsSync(version.file_path)) {
      fs.unlinkSync(version.file_path);
    }

    // 删除数据库记录
    await db.run('DELETE FROM app_versions WHERE id = ?', [id]);

    // 记录日志
    await db.run(`
      INSERT INTO logs (user_id, username, action, resource_type, resource_id, details, ip_address)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `, [
      req.user.id,
      req.user.username,
      'delete_version',
      'app_version',
      id,
      JSON.stringify({ versionName: version.version_name }),
      req.ip
    ]);

    res.json({ message: '版本删除成功' });
  } catch (error) {
    console.error('删除版本失败:', error);
    res.status(500).json({ error: '删除版本失败' });
  }
});

// 下载版本（公开接口）
router.get('/download/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const version = await db.get(
      'SELECT * FROM app_versions WHERE id = ? AND status = ?',
      [id, 'active']
    );

    if (!version) {
      return res.status(404).json({ error: '版本不存在或已停用' });
    }

    if (!fs.existsSync(version.file_path)) {
      return res.status(404).json({ error: '文件不存在' });
    }

    // 更新下载计数
    await db.run(
      'UPDATE app_versions SET download_count = download_count + 1 WHERE id = ?',
      [id]
    );

    // 发送文件
    res.download(version.file_path, version.file_name);
  } catch (error) {
    console.error('下载版本失败:', error);
    res.status(500).json({ error: '下载版本失败' });
  }
});

// 获取最新版本（客户端接口）
router.get('/latest/:appId', async (req, res) => {
  try {
    const { appId } = req.params;

    const app = await db.get(
      'SELECT id FROM apps WHERE app_id = ? AND status = ?',
      [appId, 'active']
    );

    if (!app) {
      return res.status(404).json({ error: '应用不存在' });
    }

    const version = await db.get(`
      SELECT * FROM app_versions
      WHERE app_id = ? AND status = 'active'
      ORDER BY version_code DESC, created_at DESC
      LIMIT 1
    `, [app.id]);

    if (!version) {
      return res.json({ hasVersion: false });
    }

    const downloadUrl = version.download_url || 
      `${req.protocol}://${req.get('host')}/api/versions/download/${version.id}`;

    res.json({
      hasVersion: true,
      version: {
        versionName: version.version_name,
        versionCode: version.version_code,
        description: version.description,
        changelog: version.changelog,
        downloadUrl,
        fileSize: version.file_size,
        md5: version.md5,
        isForceUpdate: version.is_force_update === 1,
        minSupportedVersion: version.min_supported_version
      }
    });
  } catch (error) {
    console.error('获取最新版本失败:', error);
    res.status(500).json({ error: '获取最新版本失败' });
  }
});

module.exports = router;
