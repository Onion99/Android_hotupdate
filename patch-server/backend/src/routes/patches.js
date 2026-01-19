const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const db = require('../models/database');

// 配置文件上传
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'patch-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: parseInt(process.env.MAX_FILE_SIZE) || 100 * 1024 * 1024 // 100MB
  },
  fileFilter: (req, file, cb) => {
    if (file.mimetype === 'application/zip' || file.originalname.endsWith('.zip')) {
      cb(null, true);
    } else {
      cb(new Error('只支持 ZIP 文件'));
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

// 上传补丁
router.post('/upload', upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '请上传文件' });
    }

    const { version, baseVersion, description, forceUpdate = false } = req.body;

    if (!version || !baseVersion) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '版本号和基础版本号不能为空' });
    }

    // 计算 MD5
    const md5 = await calculateMD5(req.file.path);

    // 生成补丁 ID
    const patchId = `patch_${Date.now()}`;

    // 保存到数据库
    const result = await db.run(`
      INSERT INTO patches (
        version, patch_id, base_version, file_path, file_name,
        file_size, md5, description, force_update
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      version,
      patchId,
      baseVersion,
      req.file.path,
      req.file.filename,
      req.file.size,
      md5,
      description || '',
      forceUpdate ? 1 : 0
    ]);

    res.json({
      message: '补丁上传成功',
      patch: {
        id: result.id,
        version,
        patchId,
        baseVersion,
        fileName: req.file.filename,
        fileSize: req.file.size,
        md5,
        description,
        forceUpdate
      }
    });
  } catch (error) {
    console.error('上传补丁失败:', error);
    // 删除已上传的文件
    if (req.file) {
      fs.unlinkSync(req.file.path);
    }
    res.status(500).json({ error: '上传补丁失败: ' + error.message });
  }
});

// 获取补丁列表
router.get('/', async (req, res) => {
  try {
    const { page = 1, limit = 10, status, version } = req.query;
    const offset = (page - 1) * limit;

    let sql = 'SELECT * FROM patches WHERE 1=1';
    const params = [];

    if (status) {
      sql += ' AND status = ?';
      params.push(status);
    }

    if (version) {
      sql += ' AND version LIKE ?';
      params.push(`%${version}%`);
    }

    sql += ' ORDER BY created_at DESC LIMIT ? OFFSET ?';
    params.push(parseInt(limit), offset);

    const patches = await db.query(sql, params);

    // 获取总数
    let countSql = 'SELECT COUNT(*) as total FROM patches WHERE 1=1';
    const countParams = [];

    if (status) {
      countSql += ' AND status = ?';
      countParams.push(status);
    }

    if (version) {
      countSql += ' AND version LIKE ?';
      countParams.push(`%${version}%`);
    }

    const { total } = await db.get(countSql, countParams);

    res.json({
      patches,
      pagination: {
        total,
        page: parseInt(page),
        limit: parseInt(limit),
        pages: Math.ceil(total / limit)
      }
    });
  } catch (error) {
    console.error('获取补丁列表失败:', error);
    res.status(500).json({ error: '获取补丁列表失败' });
  }
});

// 获取单个补丁详情
router.get('/:id', async (req, res) => {
  try {
    const patch = await db.get(
      'SELECT * FROM patches WHERE id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    res.json(patch);
  } catch (error) {
    console.error('获取补丁详情失败:', error);
    res.status(500).json({ error: '获取补丁详情失败' });
  }
});

// 更新补丁信息
router.put('/:id', async (req, res) => {
  try {
    const { description, forceUpdate, rolloutPercentage, status } = req.body;

    const updates = [];
    const params = [];

    if (description !== undefined) {
      updates.push('description = ?');
      params.push(description);
    }

    if (forceUpdate !== undefined) {
      updates.push('force_update = ?');
      params.push(forceUpdate ? 1 : 0);
    }

    if (rolloutPercentage !== undefined) {
      updates.push('rollout_percentage = ?');
      params.push(rolloutPercentage);
    }

    if (status !== undefined) {
      updates.push('status = ?');
      params.push(status);
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: '没有要更新的字段' });
    }

    updates.push('updated_at = CURRENT_TIMESTAMP');
    params.push(req.params.id);

    await db.run(
      `UPDATE patches SET ${updates.join(', ')} WHERE id = ?`,
      params
    );

    res.json({ message: '更新成功' });
  } catch (error) {
    console.error('更新补丁失败:', error);
    res.status(500).json({ error: '更新补丁失败' });
  }
});

// 删除补丁
router.delete('/:id', async (req, res) => {
  try {
    const patch = await db.get(
      'SELECT file_path FROM patches WHERE id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 删除文件
    if (fs.existsSync(patch.file_path)) {
      fs.unlinkSync(patch.file_path);
    }

    // 删除数据库记录
    await db.run('DELETE FROM patches WHERE id = ?', [req.params.id]);

    res.json({ message: '删除成功' });
  } catch (error) {
    console.error('删除补丁失败:', error);
    res.status(500).json({ error: '删除补丁失败' });
  }
});

module.exports = router;
