const express = require('express');
const router = express.Router();
const db = require('../models/database');

// 获取概览统计
router.get('/overview', async (req, res) => {
  try {
    // 补丁总数
    const { totalPatches } = await db.get(
      'SELECT COUNT(*) as totalPatches FROM patches'
    );

    // 总下载次数
    const { totalDownloads } = await db.get(
      'SELECT SUM(download_count) as totalDownloads FROM patches'
    );

    // 成功率
    const stats = await db.get(`
      SELECT
        SUM(success_count) as successCount,
        SUM(fail_count) as failCount
      FROM patches
    `);

    const successRate = stats.successCount + stats.failCount > 0
      ? (stats.successCount / (stats.successCount + stats.failCount) * 100).toFixed(2)
      : 0;

    // 活跃用户（最近 7 天）
    const { activeUsers } = await db.get(`
      SELECT COUNT(DISTINCT device_id) as activeUsers
      FROM downloads
      WHERE created_at >= datetime('now', '-7 days')
    `);

    res.json({
      totalPatches: totalPatches || 0,
      totalDownloads: totalDownloads || 0,
      successRate: parseFloat(successRate),
      activeUsers: activeUsers || 0
    });
  } catch (error) {
    console.error('获取统计失败:', error);
    res.status(500).json({ error: '获取统计失败' });
  }
});

// 获取下载趋势
router.get('/downloads-trend', async (req, res) => {
  try {
    const { days = 7 } = req.query;

    const trend = await db.query(`
      SELECT
        DATE(created_at) as date,
        COUNT(*) as count
      FROM downloads
      WHERE created_at >= datetime('now', '-${parseInt(days)} days')
      GROUP BY DATE(created_at)
      ORDER BY date ASC
    `);

    res.json(trend);
  } catch (error) {
    console.error('获取下载趋势失败:', error);
    res.status(500).json({ error: '获取下载趋势失败' });
  }
});

// 获取版本分布
router.get('/version-distribution', async (req, res) => {
  try {
    const distribution = await db.query(`
      SELECT
        app_version as version,
        COUNT(*) as count
      FROM downloads
      WHERE app_version IS NOT NULL
      GROUP BY app_version
      ORDER BY count DESC
      LIMIT 10
    `);

    res.json(distribution);
  } catch (error) {
    console.error('获取版本分布失败:', error);
    res.status(500).json({ error: '获取版本分布失败' });
  }
});

// 获取设备分布
router.get('/device-distribution', async (req, res) => {
  try {
    const distribution = await db.query(`
      SELECT
        device_model as model,
        COUNT(*) as count
      FROM downloads
      WHERE device_model IS NOT NULL
      GROUP BY device_model
      ORDER BY count DESC
      LIMIT 10
    `);

    res.json(distribution);
  } catch (error) {
    console.error('获取设备分布失败:', error);
    res.status(500).json({ error: '获取设备分布失败' });
  }
});

// 获取补丁详细统计
router.get('/patch/:id', async (req, res) => {
  try {
    const patch = await db.get(
      'SELECT * FROM patches WHERE id = ?',
      [req.params.id]
    );

    if (!patch) {
      return res.status(404).json({ error: '补丁不存在' });
    }

    // 下载记录
    const downloads = await db.query(`
      SELECT
        DATE(created_at) as date,
        COUNT(*) as count,
        SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as successCount,
        SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as failCount
      FROM downloads
      WHERE patch_id = ?
      GROUP BY DATE(created_at)
      ORDER BY date DESC
      LIMIT 30
    `, [patch.id]);

    res.json({
      patch,
      downloads
    });
  } catch (error) {
    console.error('获取补丁统计失败:', error);
    res.status(500).json({ error: '获取补丁统计失败' });
  }
});

module.exports = router;
