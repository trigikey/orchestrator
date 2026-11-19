@echo off
setlocal
title Orchestrator Frontend
cd /d "%~dp0frontend"

where npm >nul 2>nul
if errorlevel 1 (
    echo Node.js and npm are required but were not found in PATH.
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo Installing frontend dependencies...
    call npm install
    if errorlevel 1 (
        echo.
        echo npm install failed.
        pause
        exit /b 1
    )
)

echo Starting frontend on http://localhost:5173 ...
echo.

call npm run dev

if errorlevel 1 (
    echo.
    echo Frontend stopped with an error.
    pause
)

endlocal
