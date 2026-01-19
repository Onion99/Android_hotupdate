# 🚀 补丁服务端 - 自托管方案

完整的补丁管理服务端，支持补丁上传、版本管理、灰度发布、统计分析等功能。

## 📦 技术栈

### 后端
- **Node.js + Express** - RESTful API
- **SQLite/MySQL** - 数据库
- **Multer** - 文件上传
- **JWT** - 身份认证

### 前端管理后台
- **Vue 3 + Element Plus** - 管理界面
- **Vite** - 构建工具

### 部署
- **Docker** - 容器化部署
- **Nginx** - 反向代理
- 支持 Vercel、Railway、Render 等平台

## 🎯 功能特性

### 核心功能
- ✅ 补丁上传和管理
- ✅ 版本控制
- ✅ 灰度发布（按百分比、用户 ID、设备型号）
- ✅ 强制更新
- ✅ 补丁回滚
- ✅ MD5 校验

### 管理功能
- ✅ Web 管理后台
- ✅ 用户权限管理
- ✅ 操作日志
- ✅ 补丁审核流程

### 统计分析
- ✅ 下载统计
- ✅ 应用成功率
- ✅ 设备分布
- ✅ 版本分布

### 安全功能
- ✅ API 认证
- ✅ 文件签名验证
- ✅ 访问频率限制
- ✅ IP 白名单

## 📁 项目结构

```
patch-server/
├── backend/                 # 后端 API
│   ├── src/
│   │   ├── controllers/    # 控制器
│   │   ├── models/         # 数据模型
│   │   ├── routes/         # 路由
│   │   ├── middleware/     # 中间件
│   │   ├── services/       # 业务逻辑
│   │   └── utils/          # 工具函数
│   ├── uploads/            # 上传文件目录
│   ├── database.db         # SQLite 数据库
│   ├── package.json
│   └── server.js           # 入口文件
│
├── frontend/               # 前端管理后台
│   ├── src/
│   │   ├── views/         # 页面
│   │   ├── components/    # 组件
│   │   ├── api/           # API 调用
│   │   └── router/        # 路由
│   ├── package.json
│   └── vite.config.js
│
├── docker/                 # Docker 配置
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── nginx.conf
│
└── docs/                   # 文档
    ├── API.md             # API 文档
    ├── DEPLOY.md          # 部署文档
    └── DEVELOPMENT.md     # 开发文档
```

## 🚀 快速开始

### 本地开发

```bash
# 1. 克隆仓库
git clone https://github.com/706412584/Android_hotupdate.git
cd Android_hotupdate/patch-server

# 2. 安装后端依赖
cd backend
npm install

# 3. 启动后端
npm run dev
# 后端运行在 http://localhost:3000

# 4. 安装前端依赖
cd ../frontend
npm install

# 5. 启动前端
npm run dev
# 前端运行在 http://localhost:5173
```

### Docker 部署

```bash
# 构建并启动
docker-compose up -d

# 访问
# 管理后台: http://localhost:8080
# API: http://localhost:3000
```

### 云平台部署

#### Vercel（推荐）
```bash
# 安装 Vercel CLI
npm i -g vercel

# 部署后端
cd backend
vercel

# 部署前端
cd ../frontend
vercel
```

#### Railway
```bash
# 连接 GitHub 仓库
# 自动检测并部署
```

#### Render
```bash
# 创建 Web Service
# 连接 GitHub 仓库
# 自动部署
```

## 📖 API 文档

### 认证

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "role": "admin"
  }
}
```

### 补丁管理

#### 上传补丁
```http
POST /api/patches/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "file": <patch.zip>,
  "version": "1.4.1",
  "baseVersion": "1.4.0",
  "description": "修复 SIGBUS 崩溃",
  "forceUpdate": false
}
```

#### 获取补丁列表
```http
GET /api/patches?page=1&limit=10
Authorization: Bearer <token>

Response:
{
  "patches": [...],
  "total": 100,
  "page": 1,
  "limit": 10
}
```

#### 检查更新（客户端）
```http
GET /api/client/check-update?version=1.4.0&deviceId=xxx

Response:
{
  "hasUpdate": true,
  "patch": {
    "version": "1.4.1",
    "downloadUrl": "https://your-domain.com/downloads/patch-1.4.1.zip",
    "md5": "abc123...",
    "size": 1024000,
    "description": "修复说明",
    "forceUpdate": false
  }
}
```

#### 下载补丁
```http
GET /api/client/download/:patchId

Response: <binary file>
```

### 统计分析

```http
GET /api/stats/overview
Authorization: Bearer <token>

Response:
{
  "totalPatches": 10,
  "totalDownloads": 1000,
  "successRate": 98.5,
  "activeUsers": 500
}
```

## 🔧 配置

### 环境变量

创建 `.env` 文件：

```env
# 服务器配置
PORT=3000
NODE_ENV=production

# 数据库配置
DB_TYPE=sqlite
DB_PATH=./database.db
# 或使用 MySQL
# DB_TYPE=mysql
# DB_HOST=localhost
# DB_PORT=3306
# DB_USER=root
# DB_PASSWORD=password
# DB_NAME=patch_server

# JWT 配置
JWT_SECRET=your-secret-key
JWT_EXPIRES_IN=7d

# 文件存储
UPLOAD_DIR=./uploads
MAX_FILE_SIZE=100MB

# CDN 配置（可选）
CDN_URL=https://cdn.example.com

# 邮件通知（可选）
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASS=your-password
```

## 🔒 安全配置

### API 认证

所有管理 API 需要 JWT token：

```javascript
// 请求头
Authorization: Bearer <token>
```

### 频率限制

```javascript
// 每个 IP 每分钟最多 60 次请求
app.use(rateLimit({
  windowMs: 60 * 1000,
  max: 60
}));
```

### CORS 配置

```javascript
app.use(cors({
  origin: ['https://your-domain.com'],
  credentials: true
}));
```

## 📊 数据库设计

### patches 表
```sql
CREATE TABLE patches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version VARCHAR(20) NOT NULL,
  patch_id VARCHAR(50) UNIQUE NOT NULL,
  base_version VARCHAR(20) NOT NULL,
  file_path VARCHAR(255) NOT NULL,
  file_size INTEGER NOT NULL,
  md5 VARCHAR(32) NOT NULL,
  description TEXT,
  force_update BOOLEAN DEFAULT 0,
  rollout_percentage INTEGER DEFAULT 100,
  status VARCHAR(20) DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### downloads 表
```sql
CREATE TABLE downloads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  patch_id INTEGER NOT NULL,
  device_id VARCHAR(100),
  version VARCHAR(20),
  device_model VARCHAR(100),
  os_version VARCHAR(20),
  ip_address VARCHAR(45),
  success BOOLEAN,
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (patch_id) REFERENCES patches(id)
);
```

### users 表
```sql
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(100),
  role VARCHAR(20) DEFAULT 'user',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 🎨 管理后台功能

### 仪表板
- 补丁总数
- 下载统计
- 成功率
- 活跃用户

### 补丁管理
- 上传补丁
- 编辑补丁信息
- 删除补丁
- 启用/禁用补丁

### 灰度发布
- 设置发布百分比
- 指定目标用户
- 设备型号过滤
- 地区过滤

### 统计分析
- 下载趋势图
- 版本分布
- 设备分布
- 错误日志

### 系统设置
- 用户管理
- 权限配置
- 系统日志
- 备份恢复

## 🌐 部署建议

### 小型项目（< 1000 用户）
- **Vercel/Netlify** - 前端
- **Railway/Render** - 后端
- **SQLite** - 数据库
- **成本**: 免费

### 中型项目（1000-10000 用户）
- **Cloudflare Pages** - 前端
- **VPS (2核4G)** - 后端
- **MySQL** - 数据库
- **成本**: $5-10/月

### 大型项目（> 10000 用户）
- **CDN** - 静态资源
- **负载均衡** - 多实例
- **MySQL 集群** - 数据库
- **Redis** - 缓存
- **成本**: $50+/月

## 📞 技术支持

- 📖 [完整文档](./docs/)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- 💬 [讨论区](https://github.com/706412584/Android_hotupdate/discussions)
