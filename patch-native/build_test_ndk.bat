@echo off
REM Build JKS Test Program using Android NDK

echo Building JKS Test Program with Android NDK...

REM 查找NDK路径
set NDK_PATH=
if exist "%ANDROID_SDK_ROOT%\ndk" (
    for /d %%i in ("%ANDROID_SDK_ROOT%\ndk\*") do set NDK_PATH=%%i
)
if exist "%ANDROID_HOME%\ndk" (
    for /d %%i in ("%ANDROID_HOME%\ndk\*") do set NDK_PATH=%%i
)
if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\ndk" (
    for /d %%i in ("C:\Users\%USERNAME%\AppData\Local\Android\Sdk\ndk\*") do set NDK_PATH=%%i
)

if "%NDK_PATH%"=="" (
    echo Error: Android NDK not found!
    echo Please set ANDROID_SDK_ROOT or ANDROID_HOME environment variable
    exit /b 1
)

echo Using NDK: %NDK_PATH%

REM 设置编译器
set CXX=%NDK_PATH%\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe

if not exist "%CXX%" (
    echo Error: Compiler not found: %CXX%
    exit /b 1
)

REM 编译所有源文件
"%CXX%" -std=c++11 -I. ^
    -target x86_64-pc-windows-msvc ^
    test_jks.cpp ^
    src/main/cpp/src/jks_parser.cpp ^
    src/main/cpp/src/crypto_impl.cpp ^
    src/main/cpp/src/des_impl.cpp ^
    -o test_jks.exe

if %ERRORLEVEL% EQU 0 (
    echo Build successful! Executable: test_jks.exe
    echo.
    echo Usage: test_jks.exe ^<jks_file^> ^<store_password^> ^<key_alias^> [key_password]
    echo Example: test_jks.exe ..\app\smlieapp.jks 123123 smlieapp 123123
) else (
    echo Build failed!
    exit /b 1
)
