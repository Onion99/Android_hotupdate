@echo off
REM Build JKS Test Program for Windows

echo Building JKS Test Program...

REM 设置编译器（需要安装MinGW或MSVC）
set CXX=g++

REM 编译所有源文件
%CXX% -std=c++11 -I. ^
    test_jks.cpp ^
    src/main/cpp/src/jks_parser.cpp ^
    src/main/cpp/src/crypto_impl.cpp ^
    src/main/cpp/src/des_impl.cpp ^
    -o test_jks.exe

if %ERRORLEVEL% EQU 0 (
    echo Build successful! Executable: test_jks.exe
    echo.
    echo Usage: test_jks.exe ^<jks_file^> ^<store_password^> ^<key_alias^> [key_password]
    echo Example: test_jks.exe ../app/smlieapp.jks 123123 smlieapp 123123
) else (
    echo Build failed!
    exit /b 1
)
