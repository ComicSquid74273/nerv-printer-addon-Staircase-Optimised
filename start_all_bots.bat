@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Smart Prism Bot Launcher

REM -------------------------------
REM CONFIGURATION
REM -------------------------------
set "PRISM=C:\Users\TRUECLOUD\AppData\Local\Programs\PrismLauncher\prismlauncher.exe"
set "DATA=D:\PrismLauncher"
set "LOG_DIR=D:\PrismLauncher\logs"
set "LOG_FILE=%LOG_DIR%\start_all_bots.log"

REM Bot instances (no embedded quotes)
set "MAIN=Bot 003 1.0.0"
set "BOT1=Bot 001 1.0.0(1)"
set "BOT2=Bot 002 1.0.0(1)"

REM Timing (seconds)
set "PRISM_READY_TIMEOUT=120"
set "PRISM_READY_POLL=2"
set "PRISM_STABLE_WAIT=15"
set "CHECK_INTERVAL=5"
set "MAX_WAIT_PER_INSTANCE=300"

if not exist "%PRISM%" (
    call :log "ERROR: PrismLauncher not found at %PRISM%"
    exit /b 1
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

call :log "========================================"
call :log "Launcher script started."
call :log "Starting Prism Launcher..."
start "" "%PRISM%" --dir "%DATA%"
call :waitForPrismReady
if errorlevel 1 (
    call :log "ERROR: Prism Launcher did not become ready in time."
    exit /b 1
)

set "LAUNCH_FAILED=0"
call :launchAndWait "%MAIN%"
call :launchAndWait "%BOT1%"
call :launchAndWait "%BOT2%"

if "%LAUNCH_FAILED%"=="1" (
    call :log "Completed with warnings: at least one instance did not confirm in time."
    exit /b 1
)

call :log "All bot instances launched and confirmed running."
exit /b 0

:launchAndWait
set "INSTANCE=%~1"
call :log "Launching !INSTANCE!..."
"%PRISM%" --dir "%DATA%" -l "!INSTANCE!"

set /a waited=0
:waitLoop
call :isInstanceRunning "!INSTANCE!"
if not errorlevel 1 (
    call :log "Instance !INSTANCE! is running."
    call :waitOrSkip 8
    exit /b 0
)

if !waited! geq %MAX_WAIT_PER_INSTANCE% (
    call :log "WARNING: Instance !INSTANCE! not detected within %MAX_WAIT_PER_INSTANCE%s."
    set "LAUNCH_FAILED=1"
    exit /b 1
)

if !waited! equ 0 call :log "Still waiting for !INSTANCE! to appear..."
if !waited! gtr 0 (
    set /a mod=waited%%30
    if !mod! equ 0 call :log "Still waiting for !INSTANCE! (!waited!s elapsed)..."
)
call :waitOrSkip %CHECK_INTERVAL% silent
set /a waited+=CHECK_INTERVAL
goto waitLoop

:isInstanceRunning
set "INSTANCE_NAME=%~1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$name=$env:INSTANCE_NAME; $ok=$false; foreach($p in (Get-CimInstance Win32_Process)){ if($p.Name -eq 'javaw.exe' -and $p.CommandLine -and $p.CommandLine.ToLower().Contains($name.ToLower())) { $ok=$true; break } }; if($ok){exit 0}else{exit 1}" >nul 2>&1
exit /b %errorlevel%

:isPrismRunning
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=Get-Process prismlauncher -ErrorAction SilentlyContinue; if($p){exit 0}else{exit 1}" >nul 2>&1
exit /b %errorlevel%

:waitForPrismReady
set /a elapsed=0
call :log "Waiting up to %PRISM_READY_TIMEOUT%s for Prism process..."
:prismWaitLoop
call :isPrismRunning
if not errorlevel 1 goto prismReady
if !elapsed! geq %PRISM_READY_TIMEOUT% exit /b 1
timeout /t %PRISM_READY_POLL% /nobreak >nul
set /a elapsed+=PRISM_READY_POLL
goto prismWaitLoop

:prismReady
call :log "Prism process detected. Waiting %PRISM_STABLE_WAIT%s for stability..."
timeout /t %PRISM_STABLE_WAIT% /nobreak >nul
call :isPrismRunning
if errorlevel 1 exit /b 1
call :log "Prism is running and stable."
exit /b 0

:waitOrSkip
set "WAIT_SECS=%~1"
set "WAIT_MODE=%~2"
if /i not "%WAIT_MODE%"=="silent" call :log "Waiting %WAIT_SECS%s (press Enter to skip)..."
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=[int]$env:WAIT_SECS; $end=(Get-Date).AddSeconds($s); while((Get-Date)-lt $end){ if([Console]::KeyAvailable){ $k=[Console]::ReadKey($true); if($k.Key -eq [ConsoleKey]::Enter){ exit 0 } }; Start-Sleep -Milliseconds 120 }; exit 1" >nul 2>&1
if "%errorlevel%"=="0" if /i not "%WAIT_MODE%"=="silent" call :log "Wait skipped by Enter."
exit /b 0

:log
set "msg=%~1"
echo [%date% %time%] %msg%
>>"%LOG_FILE%" echo [%date% %time%] %msg%
exit /b 0