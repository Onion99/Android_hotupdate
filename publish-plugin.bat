@echo off
chcp 65001 >nul
echo ========================================
echo 发布 patch-gradle-plugin
echo ========================================
echo.

:menu
echo 请选择发布目标：
echo 1. 发布到 Maven Central
echo 2. 发布到 Gradle Plugin Portal
echo 3. 同时发布到两个平台
echo 4. 退出
echo.
set /p choice=请输入选项 (1-4): 

if "%choice%"=="1" goto maven
if "%choice%"=="2" goto plugin_portal
if "%choice%"=="3" goto both
if "%choice%"=="4" goto end
echo 无效选项，请重新选择
goto menu

:maven
echo.
echo ========================================
echo 发布到 Maven Central
echo ========================================
echo.
call gradlew :patch-gradle-plugin:clean :patch-gradle-plugin:publishPluginMavenPublicationToSonatypeRepository
if %errorlevel% neq 0 (
    echo.
    echo ❌ 发布到 Maven Central 失败！
    pause
    goto end
)
echo.
echo ✅ 发布到 Maven Central 成功！
echo.
echo 📝 后续步骤：
echo 1. 登录 https://central.sonatype.com/
echo 2. 在 Deployments 中找到你的部署
echo 3. 点击 Publish 发布到 Maven Central
echo.
pause
goto end

:plugin_portal
echo.
echo ========================================
echo 发布到 Gradle Plugin Portal
echo ========================================
echo.
echo ⚠️  请确保已在 gradle.properties 中配置：
echo    gradle.publish.key=YOUR_KEY
echo    gradle.publish.secret=YOUR_SECRET
echo.
echo 如果还没有账号，请访问：https://plugins.gradle.org/
echo.
set /p confirm=是否继续？(y/n): 
if /i not "%confirm%"=="y" goto menu

call gradlew :patch-gradle-plugin:clean :patch-gradle-plugin:publishPlugins
if %errorlevel% neq 0 (
    echo.
    echo ❌ 发布到 Gradle Plugin Portal 失败！
    echo.
    echo 💡 常见问题：
    echo 1. 检查 gradle.properties 中的凭证是否正确
    echo 2. 确保插件 ID io.github.706412584.patch 没有被占用
    echo 3. 查看完整错误信息
    echo.
    pause
    goto end
)
echo.
echo ✅ 发布到 Gradle Plugin Portal 成功！
echo.
echo 📝 插件将在审核通过后可用：
echo    https://plugins.gradle.org/plugin/io.github.706412584.patch
echo.
echo ⏳ 首次发布需要 Gradle 工程师审核（通常几天内）
echo 📧 审核结果会通过邮件通知
echo.
pause
goto end

:both
echo.
echo ========================================
echo 同时发布到两个平台
echo ========================================
echo.

REM 先发布到 Maven Central
echo [1/2] 发布到 Maven Central...
call gradlew :patch-gradle-plugin:clean :patch-gradle-plugin:publishPluginMavenPublicationToSonatypeRepository
if %errorlevel% neq 0 (
    echo.
    echo ❌ 发布到 Maven Central 失败！
    pause
    goto end
)
echo ✅ Maven Central 发布成功
echo.

REM 再发布到 Gradle Plugin Portal
echo [2/2] 发布到 Gradle Plugin Portal...
echo.
echo ⚠️  请确保已在 gradle.properties 中配置：
echo    gradle.publish.key=YOUR_KEY
echo    gradle.publish.secret=YOUR_SECRET
echo.
set /p confirm=是否继续发布到 Gradle Plugin Portal？(y/n): 
if /i not "%confirm%"=="y" (
    echo.
    echo ℹ️  已跳过 Gradle Plugin Portal 发布
    echo.
    echo 📝 Maven Central 后续步骤：
    echo 1. 登录 https://central.sonatype.com/
    echo 2. 在 Deployments 中找到你的部署
    echo 3. 点击 Publish 发布到 Maven Central
    echo.
    pause
    goto end
)

call gradlew :patch-gradle-plugin:publishPlugins
if %errorlevel% neq 0 (
    echo.
    echo ❌ 发布到 Gradle Plugin Portal 失败！
    echo ✅ 但 Maven Central 已成功发布
    echo.
    pause
    goto end
)

echo.
echo ========================================
echo ✅ 全部发布成功！
echo ========================================
echo.
echo 📝 Maven Central 后续步骤：
echo 1. 登录 https://central.sonatype.com/
echo 2. 在 Deployments 中找到你的部署
echo 3. 点击 Publish 发布到 Maven Central
echo.
echo 📝 Gradle Plugin Portal：
echo    插件已提交，等待审核（通常几天内）
echo    审核通过后可用：https://plugins.gradle.org/plugin/io.github.706412584.patch
echo    审核结果会通过邮件通知
echo.
pause
goto end

:end
