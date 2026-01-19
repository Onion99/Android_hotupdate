const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const db = require('../models/database');

// 获取用户列表
router.get('/', async (req, res) => {
  try {
    const users = await db.query(`
      SELECT id, username, email, role, created_at
      FROM users
      ORDER BY created_at DESC
    `);

    res.json(users);
  } catch (error) {
    console.error('获取用户列表失败:', error);
    res.status(500).json({ error: '获取用户列表失败' });
  }
});

// 创建用户
router.post('/', async (req, res) => {
  try {
    const { username, password, email, role = 'user' } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: '用户名和密码不能为空' });
    }

    // 检查用户是否已存在
    const existing = await db.get(
      'SELECT id FROM users WHERE username = ?',
      [username]
    );

    if (existing) {
      return res.status(400).json({ error: '用户名已存在' });
    }

    // 加密密码
    const hashedPassword = await bcrypt.hash(password, 10);

    // 创建用户
    const result = await db.run(
      'INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)',
      [username, hashedPassword, email, role]
    );

    res.json({
      message: '创建成功',
      user: {
        id: result.id,
        username,
        email,
        role
      }
    });
  } catch (error) {
    console.error('创建用户失败:', error);
    res.status(500).json({ error: '创建用户失败' });
  }
});

// 删除用户
router.delete('/:id', async (req, res) => {
  try {
    await db.run('DELETE FROM users WHERE id = ?', [req.params.id]);
    res.json({ message: '删除成功' });
  } catch (error) {
    console.error('删除用户失败:', error);
    res.status(500).json({ error: '删除用户失败' });
  }
});

module.exports = router;
