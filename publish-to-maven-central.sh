#!/bin/bash

# Maven Central 发布脚本

echo "======================================"
echo "发布到 Maven Central"
echo "======================================"
echo ""

# 检查凭证
if [ -z "$OSSRH_USERNAME" ] && [ ! -f "gradle.properties" ]; then
    echo "❌ 错误：未找到 Maven Central 凭证"
    echo ""
    echo "请设置环境变量或创建 gradle.properties 文件："
    echo "  export OSSRH_USERNAME=your_username"
    echo "  export OSSRH_PASSWORD=your_password"
    echo ""
    echo "或者在 gradle.properties 中添加："
    echo "  ossrhUsername=your_username"
    echo "  ossrhPassword=your_password"
    echo ""
    exit 1
fi

# 检查签名配置
if [ -z "$SIGNING_KEY_ID" ] && [ ! -f "gradle.properties" ]; then
    echo "⚠️  警告：未找到 GPG 签名配置"
    echo "发布到 Maven Central 需要 GPG 签名"
    echo ""
    echo "请设置环境变量或在 gradle.properties 中添加："
    echo "  signing.keyId=YOUR_KEY_ID"
    echo "  signing.password=YOUR_GPG_PASSWORD"
    echo "  signing.secretKeyRingFile=/path/to/secring.gpg"
    echo ""
    read -p "是否继续（不签名）？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "1️⃣  清理构建..."
./gradlew clean

echo ""
echo "2️⃣  构建项目..."
./gradlew build -x test

echo ""
echo "3️⃣  发布到 Sonatype Staging..."
./gradlew publishAllPublicationsToSonatypeRepository

echo ""
echo "======================================"
echo "✅ 发布完成！"
echo "======================================"
echo ""
echo "下一步："
echo "1. 访问 https://s01.oss.sonatype.org/"
echo "2. 登录并进入 'Staging Repositories'"
echo "3. 找到你的仓库（iogithub706412584-xxxx）"
echo "4. 点击 'Close' 进行验证"
echo "5. 验证通过后点击 'Release' 发布"
echo ""
echo "大约 10-30 分钟后会同步到 Maven Central"
echo ""
