FROM node:18-alpine

WORKDIR /app

# 复制 package.json
COPY backend/package*.json ./

# 安装依赖
RUN npm install --production

# 复制源代码
COPY backend/ ./

# 创建上传目录
RUN mkdir -p uploads

# 暴露端口
EXPOSE 3000

# 启动命令
CMD ["node", "server.js"]
