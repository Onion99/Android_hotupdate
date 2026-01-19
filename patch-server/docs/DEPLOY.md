# 部署文档

## 🚀 部署方式

### 1. Docker 部署（推荐）

最简单的部署方式，适合生产环境。

```bash
# 1. 克隆仓库
git clone https://github.com/706412584/Android_hotupdate.git
cd Android_hotupdate/patch-server

# 2. 配置环境变量
cp backend/.env.example backend/.env
vim backend/.env  # 修改配置

# 3. 启动服务
cd docker
docker-compose up -d

# 4. 初始化数据库
docker-compose exec backend npm run init-db

# 5. 查看日志
docker-compose logs -f

# 访问
# API: http://localhost:3000
# 管理后台: http://localhost:8080
```

### 2. VPS 部署

适合有自己服务器的用户。

```bash
# 1. 安装 Node.js 18+
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# 2. 克隆仓库
git clone https://github.com/706412584/Android_hotupdate.git
cd Android_hotupdate/patch-server/backend

# 3. 安装依赖
npm install --production

# 4. 配置环境变量
cp .env.example .env
vim .env

# 5. 初始化数据库
npm run init-db

# 6. 使用 PM2 启动
npm install -g pm2
pm2 start server.js --name patch-server
pm2 save
pm2 startup

# 7. 配置 Nginx 反向代理
sudo vim /etc/nginx/sites-available/patch-server
```

Nginx 配置示例：
```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 100M;

    location /api/ {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /downloads/ {
        alias /path/to/patch-server/backend/uploads/;
        autoindex off;
    }
}
```

### 3. Vercel 部署

免费托管，适合小型项目。

```bash
# 1. 安装 Vercel CLI
npm i -g vercel

# 2. 登录
vercel login

# 3. 部署
cd patch-server/backend
vercel

# 4. 配置环境变量
vercel env add JWT_SECRET
vercel env add DB_TYPE
# ... 添加其他环境变量

# 5. 重新部署
vercel --prod
```

注意：Vercel 不支持文件上传持久化，需要配置外部存储（如 AWS S3）。

### 4. Railway 部署

一键部署，自动 HTTPS。

```bash
# 1. 连接 GitHub 仓库
# 访问 https://railway.app

# 2. 选择 patch-server/backend 目录

# 3. 配置环境变量
# 在 Railway 控制台添加环境变量

# 4. 自动部署
# Railway 会自动检测并部署
```

### 5. Render 部署

免费额度，适合测试。

```bash
# 1. 创建 Web Service
# 访问 https://render.com

# 2. 连接 GitHub 仓库

# 3. 配置
# Build Command: cd patch-server/backend && npm install
# Start Command: cd patch-server/backend && node server.js

# 4. 添加环境变量

# 5. 部署
```

## 🔧 配置说明

### 必需配置

```env
# JWT 密钥（必须修改）
JWT_SECRET=your-secret-key-change-this

# 数据库类型
DB_TYPE=sqlite
DB_PATH=./database.db

# 上传目录
UPLOAD_DIR=./uploads
```

### 可选配置

```env
# 端口
PORT=3000

# CORS
CORS_ORIGIN=https://your-domain.com

# CDN（如果使用）
CDN_URL=https://cdn.your-domain.com

# 邮件通知
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASS=your-password
```

## 🔒 安全建议

### 1. 修改默认密码

```bash
# 首次登录后立即修改
curl -X POST http://your-domain.com/api/auth/change-password \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"oldPassword":"admin123","newPassword":"new-secure-password"}'
```

### 2. 配置 HTTPS

使用 Let's Encrypt 免费证书：

```bash
# 安装 Certbot
sudo apt-get install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

### 3. 配置防火墙

```bash
# 只开放必要端口
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

### 4. 定期备份

```bash
# 备份数据库
cp database.db database.db.backup

# 备份上传文件
tar -czf uploads-backup.tar.gz uploads/

# 自动备份脚本
crontab -e
# 添加：0 2 * * * /path/to/backup.sh
```

## 📊 监控

### 1. 健康检查

```bash
curl http://your-domain.com/health
```

### 2. 日志查看

```bash
# Docker
docker-compose logs -f backend

# PM2
pm2 logs patch-server

# Nginx
tail -f /var/log/nginx/access.log
```

### 3. 性能监控

使用 PM2 Plus 或其他监控工具：

```bash
pm2 install pm2-server-monit
pm2 monit
```

## 🔄 更新

```bash
# Docker
cd patch-server/docker
git pull
docker-compose down
docker-compose build
docker-compose up -d

# VPS
cd patch-server/backend
git pull
npm install
pm2 restart patch-server
```

## 🐛 故障排查

### 数据库连接失败

```bash
# 检查数据库文件权限
ls -la database.db

# 重新初始化
npm run init-db
```

### 文件上传失败

```bash
# 检查上传目录权限
ls -la uploads/
chmod 755 uploads/

# 检查磁盘空间
df -h
```

### 端口被占用

```bash
# 查找占用端口的进程
lsof -i :3000

# 杀死进程
kill -9 PID
```

## 📞 技术支持

- 📖 [API 文档](./API.md)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- 💬 [讨论区](https://github.com/706412584/Android_hotupdate/discussions)
