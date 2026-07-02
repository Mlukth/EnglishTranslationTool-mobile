@echo off
chcp 65001 >nul
title English Translation Tool - Dev Server

cd /d "D:\photovoltaic\EnglishTranslationTool-mobile"

if not exist "node_modules\" (
    echo [npm install...]
    call npm install
    if errorlevel 1 (
        echo npm install failed
        pause
        exit /b 1
    )
)

echo ========================================
echo   English Translation Tool - Dev Server
echo   Local:  http://localhost:5175
echo   Press Ctrl+C to stop
echo ========================================

start "" http://localhost:5175
call npm run dev
pause
