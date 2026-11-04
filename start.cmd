@echo off
setlocal
set "SAZARE_PWSH="

where.exe pwsh.exe >nul 2>&1
if not errorlevel 1 set "SAZARE_PWSH=pwsh.exe"
if not defined SAZARE_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "SAZARE_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined SAZARE_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "SAZARE_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"

if not defined SAZARE_PWSH (
    echo PowerShell 7.4 or newer was not found. Install PowerShell 7 and try again.
    pause
    exit /b 1
)

start "" "%SAZARE_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -PauseOnError
if errorlevel 1 (
    echo Failed to open PowerShell.
    pause
    exit /b 1
)
exit /b 0
