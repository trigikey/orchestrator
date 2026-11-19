@echo off
setlocal
title Orchestrator

start "Orchestrator Backend" cmd /k call "%~dp0start-backend.bat"
start "Orchestrator Frontend" cmd /k call "%~dp0start-frontend.bat"

echo Backend and frontend are starting in separate windows.
echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:5173
echo.
timeout /t 3 >nul

endlocal
