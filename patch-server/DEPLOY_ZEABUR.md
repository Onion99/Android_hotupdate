# 部署�?Zeabur 指南

## 前提条件

1. 注册 [Zeabur](https://zeabur.com) 账号
2. 安装 [Zeabur CLI](https://zeabur.com/docs/deploy/cli)（可选）

## 方式一：通过 Zeabur Dashboard 部署（推荐）

### 1. 创建项目

1. 登录 [Zeabur Dashboard](https://dash.zeabur.com)
2. 点击 "New Project"
3. 选择一个区域（推荐选择离你最近的区域�?

### 2. 连接 Git 仓库

1. 点击 "Add Service"
2. 选择 "Git"
3. 授权 GitHub/GitLab 访问
4. 选择你的仓库
5. 选择 `patch-server` 目录作为根目�?

### 3. 配置环境变量

�?"Variables" 标签页添加以下环境变量：

```
NODE_ENV=production
PORT=3000
JWT_SECRET=your-secret-key-here
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your-admin-password
```

可选环境变量：
```
CORS_ORIGIN=https://your-frontend-domain.com
DATABASE_PATH=./database.db
UPLOAD_DIR=./uploads
BACKUP_DIR=./backups
```

### 4. 部署

1. Zeabur 会自动检�?Dockerfile 并开始构�?
2. 等待构建完成（首次构建可能需�?3-5 分钟�?
3. 构建成功后，服务会自动启�?

### 5. 配置域名

1. �?"Domains" 标签页点�?"Generate Domain"
2. Zeabur 会自动生成一个域名（�?`xxx.zeabur.app`�?
3. 或者绑定自定义域名

### 6. 验证部署

访问 `https://your-domain.zeabur.app/health` 应该返回�?
```json
{
  "status": "ok",
  "timestamp": "2026-01-19T07:50:53.000Z",
  "uptime": 123.456
}
```

## 方式二：通过 Zeabur CLI 部署

### 1. 安装 CLI

```bash
npm install -g @zeabur/cli
# �?
curl -fsSL https://zeabur.com/install.sh | bash
```

### 2. 登录

```bash
zeabur auth login
```

### 3. 初始化项�?

```bash
cd patch-server
zeabur init
```

### 4. 部署

```bash
zeabur deploy
```

### 5. 查看日志

```bash
zeabur logs
```

## 方式三：通过 GitHub Actions 自动部署

创建 `.github/workflows/deploy-zeabur.yml`�?

```yaml
name: Deploy to Zeabur

on:
  push:
    branches:
      - main
    paths:
      - 'patch-server/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Deploy to Zeabur
        uses: zeabur/deploy-action@v1
        with:
          service-id: ${{ secrets.ZEABUR_SERVICE_ID }}
          api-key: ${{ secrets.ZEABUR_API_KEY }}
```

## 配置持久化存�?

Zeabur 默认不持久化容器数据，需要配置卷挂载�?

### 1. �?Dashboard 中配�?

1. 进入服务设置
2. 点击 "Volumes" 标签
3. 添加卷：
   - `/app/database.db` - 数据库文�?
   - `/app/uploads` - 上传的补丁文�?
   - `/app/backups` - 备份文件

### 2. 或在 zeabur.json 中配�?

```json
{
  "name": "patch-server",
  "build": {
    "dockerfile": "Dockerfile"
  },
  "deploy": {
    "env": {
      "NODE_ENV": "production",
      "PORT": "3000"
    },
    "volumes": [
      {
        "name": "database",
        "mount": "/app/database.db"
      },
      {
        "name": "uploads",
        "mount": "/app/uploads"
      },
      {
        "name": "backups",
        "mount": "/app/backups"
      }
    ]
  }
}
```

## 环境变量说明

### 必需变量

| 变量�?| 说明 | 示例 |
|--------|------|------|
| `NODE_ENV` | 运行环境 | `production` |
| `PORT` | 服务端口 | `3000` |
| `JWT_SECRET` | JWT 密钥 | `your-secret-key` |
| `ADMIN_USERNAME` | 管理员用户名 | `admin` |
| `ADMIN_PASSWORD` | 管理员密�?| `your-password` |

### 可选变�?

| 变量�?| 说明 | 默认�?|
|--------|------|--------|
| `CORS_ORIGIN` | CORS 允许的源 | `*` |
| `DATABASE_PATH` | 数据库文件路�?| `./database.db` |
| `UPLOAD_DIR` | 上传目录 | `./uploads` |
| `BACKUP_DIR` | 备份目录 | `./backups` |
| `MAX_FILE_SIZE` | 最大文件大�?| `100MB` |
| `LOG_LEVEL` | 日志级别 | `info` |

## 常见问题

### 1. 构建失败

**问题**：Zeabur 找不�?Dockerfile

**解决**�?
- 确保 `Dockerfile` �?`patch-server` 根目�?
- 检�?Git 仓库中是否包�?Dockerfile
- �?Zeabur Dashboard 中手动指�?Dockerfile 路径

### 2. 服务启动失败

**问题**：容器启动后立即退�?

**解决**�?
- 检查环境变量是否正确配�?
- 查看 Zeabur 日志：Dashboard �?Logs
- 确保端口号与 `PORT` 环境变量一�?

### 3. 数据丢失

**问题**：重新部署后数据丢失

**解决**�?
- 配置持久化卷（见上文�?
- 定期备份数据库文�?
- 使用外部数据库（�?PostgreSQL�?

### 4. 文件上传失败

**问题**：上传补丁文件失�?

**解决**�?
- 检�?`uploads` 目录权限
- 确保配置了持久化�?
- 检�?`MAX_FILE_SIZE` 环境变量

### 5. CORS 错误

**问题**：前端无法访�?API

**解决**�?
- 设置 `CORS_ORIGIN` 环境变量为前端域�?
- 或设置为 `*` 允许所有源（不推荐生产环境�?

## 性能优化

### 1. 启用 CDN

�?Zeabur Dashboard 中启�?CDN 加速静态资源：
- Settings �?CDN �?Enable

### 2. 配置缓存

�?`server.js` 中添加缓存头�?
```javascript
app.use('/downloads', express.static('uploads', {
  maxAge: '7d',
  etag: true
}));
```

### 3. 数据库优�?

使用 PostgreSQL 替代 SQLite（生产环境推荐）�?
```bash
# �?Zeabur 中添�?PostgreSQL 服务
# 然后更新环境变量
DATABASE_URL=postgresql://user:pass@host:5432/dbname
```

## 监控和日�?

### 查看实时日志

```bash
# 使用 CLI
zeabur logs -f

# 或在 Dashboard 中查�?
Dashboard �?Service �?Logs
```

### 配置日志级别

```bash
LOG_LEVEL=debug  # debug, info, warn, error
```

### 健康检�?

Zeabur 会自动调�?`/health` 端点进行健康检查�?

## 备份和恢�?

### 自动备份

服务端已内置定时备份功能，备份文件保存在 `backups` 目录�?

### 手动备份

```bash
# 下载数据库文�?
zeabur exec -- cat /app/database.db > backup.db

# 下载上传文件
zeabur exec -- tar -czf - /app/uploads > uploads.tar.gz
```

### 恢复备份

```bash
# 上传数据库文�?
zeabur exec -- sh -c 'cat > /app/database.db' < backup.db

# 上传文件
zeabur exec -- sh -c 'tar -xzf - -C /app' < uploads.tar.gz
```

## 扩展和升�?

### 垂直扩展

�?Zeabur Dashboard 中调整资源配置：
- Settings �?Resources �?CPU/Memory

### 水平扩展

Zeabur 支持自动扩展�?
- Settings �?Scaling �?Auto Scaling

### 零停机升�?

Zeabur 支持滚动更新，推送代码后自动部署�?
```bash
git push origin main
```

## 安全建议

1. **使用强密�?*：设置复杂的 `JWT_SECRET` �?`ADMIN_PASSWORD`
2. **限制 CORS**：生产环境设置具体的 `CORS_ORIGIN`
3. **启用 HTTPS**：Zeabur 自动提供 SSL 证书
4. **定期更新**：及时更新依赖包
5. **监控日志**：定期检查异常日�?

## 成本估算

Zeabur 定价（参考）�?
- **免费套餐**：适合测试和小型项�?
- **Pro 套餐**�?5/月起，适合生产环境
- **Enterprise**：联系销�?

详细定价：https://zeabur.com/pricing

## 技术支�?

- **Zeabur 文档**：https://zeabur.com/docs
- **Discord 社区**：https://discord.gg/zeabur
- **GitHub Issues**：https://github.com/zeabur/zeabur/issues

## 相关链接

- [Zeabur 官网](https://zeabur.com)
- [Zeabur 文档](https://zeabur.com/docs)
- [Zeabur CLI](https://zeabur.com/docs/deploy/cli)
- [Zeabur GitHub](https://github.com/zeabur/zeabur)

