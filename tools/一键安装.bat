@echo off
rem =====================================================================
rem  txhlock 一键安装器（Windows）
rem  双击即可。所有逻辑在 install.ps1 里，这里只负责用正确的参数调用它。
rem =====================================================================
chcp 65001 >nul 2>&1
title 答题锁屏守护 - 一键安装
cd /d "%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
set RC=%ERRORLEVEL%

if not "%RC%"=="0" (
    echo.
    echo 安装没有完成，退出码 %RC%。
    echo 日志：%TEMP%\txhlock-install.log
    echo.
    pause
)
exit /b %RC%
