@echo off
chcp 65001 >nul
REM Maven Central Publishing Shortcut
cd maven-central
call publish.bat
cd ..
