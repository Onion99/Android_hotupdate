@echo off
setlocal enabledelayedexpansion

echo ======================================
echo 发布到 Maven Central
echo ======================================
echo.

REM 检查凭证
if "%OSSRH_USERNAME%"=="" (
    if not exist "gradle.properties" (
        echo ❌ 错误：未找到 Maven Central 凭证
        echo.
        echo 请设置环境变量或创建 gradle.properties 文件：
        echo   set OSSRH_USERNAME=your_username
        echo   set OSSRH_PASSWORD=your_password
        echo.
        echo 或者在 gradle.properties 中添加：
        echo   ossrhUsername=your_username
        echo   ossrhPassword=your_password
        echo.
        exit /b 1
    )
)

REM 检查签名配置
if "%SIGNING_KEY_ID%"=="" (
    if not exist "gradle.properties" (
        echo ⚠️  警告：未找到 GPG 签名配置
        echo 发布到 Maven Central 需要 GPG 签名
        echo.
        echo 请设置环境变量或在 gradle.properties 中添加：
        echo   signing.keyId=YOUR_KEY_ID
        echo   signing.password=YOUR_GPG_PASSWORD
        echo   signing.secretKeyRingFile=/path/to/secring.gpg
        echo.
        set /p CONTINUE="是否继续（不签名）？(y/N) "
        if /i not "!CONTINUE!"=="y" exit /b 1
    )
)

echo 1️⃣  清理构建...
call gradlew.bat clean

echo.
echo 2️⃣  构建项目...
call gradlew.bat build -x test

echo.
echo 3️⃣  发布到 Sonatype Staging...
call gradlew.bat publishAllPublicationsToSonatypeRepository

echo.
echo ======================================
echo ✅ 发布完成！
echo ======================================
echo.
echo 下一步：
echo 1. 访问 https://s01.oss.sonatype.org/
echo 2. 登录并进入 'Staging Repositories'
echo 3. 找到你的仓库（iogithub706412584-xxxx）
echo 4. 点击 'Close' 进行验证
echo 5. 验证通过后点击 'Release' 发布
echo.
echo 大约 10-30 分钟后会同步到 Maven Central
echo.

pause
