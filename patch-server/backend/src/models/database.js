const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../../database.db');

// 确保数据库目录存在
const dbDir = path.dirname(DB_PATH);
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
}

// 创建数据库连接
const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('数据库连接失败:', err);
  } else {
    console.log('✅ 数据库连接成功');
    initDatabase();
  }
});

// 初始化数据库表
function initDatabase() {
  db.serialize(() => {
    // 用户表
    db.run(`
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username VARCHAR(50) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        email VARCHAR(100),
        avatar VARCHAR(255),
        role VARCHAR(20) DEFAULT 'user',
        status VARCHAR(20) DEFAULT 'active',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `, async (err) => {
      if (err) {
        console.error('创建用户表失败:', err);
      } else {
        // 用户表创建成功后，初始化默认管理员
        await initDefaultAdmin();
      }
    });

    // 应用表
    db.run(`
      CREATE TABLE IF NOT EXISTS apps (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        app_id VARCHAR(100) UNIQUE NOT NULL,
        app_name VARCHAR(100) NOT NULL,
        package_name VARCHAR(255) NOT NULL,
        description TEXT,
        icon VARCHAR(255),
        owner_id INTEGER NOT NULL,
        status VARCHAR(20) DEFAULT 'active',
        review_status VARCHAR(20) DEFAULT 'approved',
        review_note TEXT,
        reviewed_by INTEGER,
        reviewed_at DATETIME,
        
        -- 安全配置
        require_signature BOOLEAN DEFAULT 0,
        require_encryption BOOLEAN DEFAULT 0,
        keystore_path VARCHAR(255),
        keystore_password VARCHAR(255),
        key_alias VARCHAR(100),
        key_password VARCHAR(255),
        
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (owner_id) REFERENCES users(id),
        FOREIGN KEY (reviewed_by) REFERENCES users(id)
      )
    `);

    // 补丁表
    db.run(`
      CREATE TABLE IF NOT EXISTS patches (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        app_id INTEGER NOT NULL,
        version VARCHAR(20) NOT NULL,
        patch_id VARCHAR(50) UNIQUE NOT NULL,
        base_version VARCHAR(20) NOT NULL,
        file_path VARCHAR(255) NOT NULL,
        file_name VARCHAR(255) NOT NULL,
        file_size INTEGER NOT NULL,
        md5 VARCHAR(32) NOT NULL,
        description TEXT,
        force_update BOOLEAN DEFAULT 0,
        rollout_percentage INTEGER DEFAULT 100,
        target_users TEXT,
        status VARCHAR(20) DEFAULT 'active',
        download_count INTEGER DEFAULT 0,
        success_count INTEGER DEFAULT 0,
        fail_count INTEGER DEFAULT 0,
        created_by INTEGER,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (app_id) REFERENCES apps(id),
        FOREIGN KEY (created_by) REFERENCES users(id)
      )
    `);

    // 下载记录表
    db.run(`
      CREATE TABLE IF NOT EXISTS downloads (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        patch_id INTEGER NOT NULL,
        device_id VARCHAR(100),
        app_version VARCHAR(20),
        device_model VARCHAR(100),
        os_version VARCHAR(20),
        ip_address VARCHAR(45),
        success BOOLEAN,
        error_message TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (patch_id) REFERENCES patches(id)
      )
    `);

    // 操作日志表
    db.run(`
      CREATE TABLE IF NOT EXISTS logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER,
        username VARCHAR(50),
        action VARCHAR(50) NOT NULL,
        resource_type VARCHAR(50),
        resource_id INTEGER,
        details TEXT,
        ip_address VARCHAR(45),
        user_agent TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // 通知表
    db.run(`
      CREATE TABLE IF NOT EXISTS notifications (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        type VARCHAR(50) NOT NULL,
        title VARCHAR(255) NOT NULL,
        message TEXT NOT NULL,
        link VARCHAR(255),
        data TEXT,
        is_read BOOLEAN DEFAULT 0,
        read_at DATETIME,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // 数据库迁移：为 apps 表添加审核相关字段（如果不存在）
    db.all("PRAGMA table_info(apps)", [], (err, columns) => {
      if (err) {
        console.error('检查 apps 表结构失败:', err);
        return;
      }
      
      const hasReviewStatus = columns.some(col => col.name === 'review_status');
      
      if (!hasReviewStatus) {
        console.log('🔄 迁移数据库：添加审核相关字段...');
        db.run(`ALTER TABLE apps ADD COLUMN review_status VARCHAR(20) DEFAULT 'approved'`);
        db.run(`ALTER TABLE apps ADD COLUMN review_note TEXT`);
        db.run(`ALTER TABLE apps ADD COLUMN reviewed_by INTEGER`);
        db.run(`ALTER TABLE apps ADD COLUMN reviewed_at DATETIME`);
        console.log('✅ 数据库迁移完成');
      }
    });
  });
}

// 初始化默认管理员
async function initDefaultAdmin() {
  try {
    const bcrypt = require('bcryptjs');
    
    const admin = await Database.get('SELECT id FROM users WHERE role = "admin"');
    
    if (!admin) {
      const username = process.env.ADMIN_USERNAME || 'admin';
      const password = process.env.ADMIN_PASSWORD || 'admin123';
      const email = process.env.ADMIN_EMAIL || 'admin@example.com';
      const hashedPassword = await bcrypt.hash(password, 10);
      
      await Database.run(
        'INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)',
        [username, hashedPassword, email, 'admin']
      );
      
      console.log('✅ 默认管理员创建成功');
      console.log(`   用户名: ${username}`);
      console.log(`   密码: ${password}`);
      console.log('   ⚠️  请尽快修改默认密码！');
    }
  } catch (error) {
    console.error('⚠️  管理员初始化失败:', error.message);
  }
}

// 数据库操作封装
const Database = {
  // 执行查询
  query: (sql, params = []) => {
    return new Promise((resolve, reject) => {
      db.all(sql, params, (err, rows) => {
        if (err) reject(err);
        else resolve(rows);
      });
    });
  },

  // 执行单条查询
  get: (sql, params = []) => {
    return new Promise((resolve, reject) => {
      db.get(sql, params, (err, row) => {
        if (err) reject(err);
        else resolve(row);
      });
    });
  },

  // 执行插入/更新/删除
  run: (sql, params = []) => {
    return new Promise((resolve, reject) => {
      db.run(sql, params, function(err) {
        if (err) reject(err);
        else resolve({ id: this.lastID, changes: this.changes });
      });
    });
  },

  // 关闭数据库
  close: () => {
    return new Promise((resolve, reject) => {
      db.close((err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }
};

module.exports = Database;
