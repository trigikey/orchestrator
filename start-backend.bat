@echo off
setlocal
title Orchestrator Backend
cd /d "%~dp0"

set "PORT=8080"
set "OLD_PID="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%PORT% " ^| findstr /I "LISTENING"') do if not defined OLD_PID set "OLD_PID=%%p"

if defined OLD_PID (
    echo Port %PORT% is already in use by process PID %OLD_PID%.
    echo It may be an already running backend instance.
    choice /C YN /N /M "Stop it and start the backend? [Y/N] "
    if errorlevel 2 (
        echo Start cancelled.
        pause
        exit /b 1
    )
    taskkill /PID %OLD_PID% /F >nul 2>nul
    timeout /t 1 >nul
)

echo Starting backend on http://localhost:%PORT% ...
echo.

call .\mvnw.cmd spring-boot:run

if errorlevel 1 (
    echo.
    echo Backend stopped with an error.
    pause
)

endlocal
