const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const path = require('path');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// 中间件
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  credentials: true
}));
app.use(compression());
app.use(morgan('combined'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 频率限制
const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 分钟
  max: 60, // 最多 60 次请求
  message: '请求过于频繁，请稍后再试'
});
app.use('/api/', limiter);

// 静态文件服务（补丁下载）
app.use('/downloads', express.static(path.join(__dirname, 'uploads')));

// 路由
app.use('/api/auth', require('./src/routes/auth'));
app.use('/api/patches', require('./src/routes/patches'));
app.use('/api/client', require('./src/routes/client'));
app.use('/api/stats', require('./src/routes/stats'));
app.use('/api/users', require('./src/routes/users'));

// 健康检查
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// 404 处理
app.use((req, res) => {
  res.status(404).json({
    error: 'Not Found',
    message: `路径 ${req.path} 不存在`
  });
});

// 错误处理
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(err.status || 500).json({
    error: err.message || 'Internal Server Error',
    ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
  });
});

// 启动服务器
app.listen(PORT, () => {
  console.log(`🚀 补丁服务端运行在 http://localhost:${PORT}`);
  console.log(`📊 环境: ${process.env.NODE_ENV}`);
  console.log(`📁 上传目录: ${process.env.UPLOAD_DIR}`);
});

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('收到 SIGTERM 信号，正在关闭服务器...');
  process.exit(0);
});
