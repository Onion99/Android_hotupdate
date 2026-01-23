@echo off
chcp 65001 >nul
echo ========================================
echo 检查发布配置
echo ========================================
echo.

echo [1/5] 检查版本号...
echo.
findstr /C:"pomVersion = " maven-publish.gradle
findstr /C:"version = " build.gradle
echo.

echo [2/5] 检查 Maven Central 凭证...
echo.
findstr /C:"ossrhUsername=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ Maven Central 用户名已配置
) else (
    echo ❌ Maven Central 用户名未配置
)
findstr /C:"ossrhPassword=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ Maven Central 密码已配置
) else (
    echo ❌ Maven Central 密码未配置
)
echo.

echo [3/5] 检查 Gradle Plugin Portal 凭证...
echo.
findstr /C:"gradle.publish.key=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ Gradle Plugin Portal Key 已配置
) else (
    echo ❌ Gradle Plugin Portal Key 未配置
)
findstr /C:"gradle.publish.secret=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ Gradle Plugin Portal Secret 已配置
) else (
    echo ❌ Gradle Plugin Portal Secret 未配置
)
echo.

echo [4/5] 检查签名配置...
echo.
findstr /C:"signing.keyId=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ 签名 Key ID 已配置
) else (
    echo ❌ 签名 Key ID 未配置
)
findstr /C:"signing.password=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ 签名密码已配置
) else (
    echo ❌ 签名密码未配置
)
findstr /C:"signing.secretKeyRingFile=" gradle.properties | findstr /V "#"
if %errorlevel% equ 0 (
    echo ✅ 签名密钥文件已配置
) else (
    echo ❌ 签名密钥文件未配置
)
echo.

echo [5/5] 检查 patch-gradle-plugin 配置...
echo.
findstr /C:"id 'com.gradle.plugin-publish'" patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo ✅ plugin-publish 插件已配置
) else (
    echo ❌ plugin-publish 插件未配置
)
findstr /C:"'com.orange.patch'" patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo ✅ 插件 ID 已配置
) else (
    echo ❌ 插件 ID 未配置
)
findstr /C:"website = " patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo ✅ 插件网站已配置
) else (
    echo ❌ 插件网站未配置
)
echo.

echo ========================================
echo 配置检查完成
echo ========================================
echo.
echo 📝 发布模块列表：
echo    - patch-core
echo    - patch-native
echo    - patch-generator-android
echo    - update
echo    - patch-cli
echo    - patch-gradle-plugin （新增）
echo.
echo 📦 发布目标：
echo    - Maven Central: https://central.sonatype.com/
echo    - Gradle Plugin Portal: https://plugins.gradle.org/
echo.
echo 🚀 准备发布？运行：
echo    publish-maven.bat （发布所有模块到 Maven Central）
echo    publish-plugin.bat （发布 patch-gradle-plugin）
echo.
pause
