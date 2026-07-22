@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Minecraft Bot Watchdog (Stable)
for /f %%P in ('powershell -NoProfile -Command "$PID"') do set "THIS_PID=%%P"

REM -------------------------------
REM CONFIGURATION
REM -------------------------------
set "MAIN=Bot 003 1.0.0"
set "BOT1=Bot 001 1.0.0(1)"
set "BOT2=Bot 002 1.0.0(1)"
set "LAUNCH_SCRIPT=C:\Users\TRUECLOUD\Desktop\start_all_bots.bat"
set "LOG_DIR=D:\PrismLauncher\logs"
set "LOG_FILE=%LOG_DIR%\watchdog.log"
set "LOCK_DIR=%TEMP%\minecraft_bot_watchdog.lock"
set "LOCK_PID_FILE=%LOCK_DIR%\pid.txt"
set "TRACKED_PID_FILE=%LOCK_DIR%\tracked_pids.txt"

REM Timing
set "BOOT_WAIT=120"
set "CHECK_EVERY=45"
set "MISSING_THRESHOLD=3"
set "STARTUP_GRACE=360"
set "AUTO_START_ON_BOOT=1"

if not exist "%LAUNCH_SCRIPT%" (
    call :log "ERROR: Launch script not found at %LAUNCH_SCRIPT%"
    exit /b 1
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

call :acquireLock
if errorlevel 1 exit /b 0

call :log "========================================"
call :log "Watchdog script started."
call :log "Waiting for system startup (%BOOT_WAIT%s)..."
call :waitOrSkip %BOOT_WAIT%

set /a missesInARow=0
set /a cooldown=0
set "PID_MAIN="
set "PID_BOT1="
set "PID_BOT2="
set "LAST_PID_MAIN="
set "LAST_PID_BOT1="
set "LAST_PID_BOT2="

if "%AUTO_START_ON_BOOT%"=="1" (
    call :log "Bootstrapping bots after startup wait..."
    call :startBots
    set /a cooldown=%STARTUP_GRACE%
    call :log "Initial launch issued. Entering startup grace period (%STARTUP_GRACE%s)."
)

:loop
if !cooldown! gtr 0 (
    call :log "In startup grace period (!cooldown!s remaining)."
    call :waitOrSkip %CHECK_EVERY% noskip
    set /a cooldown-=CHECK_EVERY
    if !cooldown! lss 0 set /a cooldown=0
    goto loop
)

call :log "Checking bot instances..."
call :captureTrackedPids
set "missingList="
set /a missingCount=0

call :isTrackedPidHealthy "%MAIN%" "!PID_MAIN!"
if errorlevel 1 (
    call :addMissing "%MAIN%"
    call :logMissingReason "%MAIN%" "!PID_MAIN!"
)

call :isTrackedPidHealthy "%BOT1%" "!PID_BOT1!"
if errorlevel 1 (
    call :addMissing "%BOT1%"
    call :logMissingReason "%BOT1%" "!PID_BOT1!"
)

call :isTrackedPidHealthy "%BOT2%" "!PID_BOT2!"
if errorlevel 1 (
    call :addMissing "%BOT2%"
    call :logMissingReason "%BOT2%" "!PID_BOT2!"
)

if defined missingList (
    set /a missesInARow+=1
    call :log "Missing detected (!missesInARow!/%MISSING_THRESHOLD%):!missingList!"
) else (
    if !missesInARow! gtr 0 call :log "Recovery detected: all instances are running again."
    set /a missesInARow=0
    call :log "All instances running."
)

if !missesInARow! geq %MISSING_THRESHOLD% (
    call :log "Threshold reached. Restarting all bots..."
    call :killTrackedPids
    taskkill /f /im prismlauncher.exe >nul 2>&1
    call :waitOrSkip 10
    call :startBots
    set /a cooldown=%STARTUP_GRACE%
    set /a missesInARow=0
    call :log "Restart command issued. Entering startup grace period (%STARTUP_GRACE%s)."
)

call :waitOrSkip %CHECK_EVERY%
goto loop

:acquireLock
2>nul mkdir "%LOCK_DIR%"
if not errorlevel 1 (
    >"%LOCK_PID_FILE%" echo !THIS_PID!
    exit /b 0
)

if exist "%LOCK_PID_FILE%" (
    set /p EXISTING_PID=<"%LOCK_PID_FILE%"
    if defined EXISTING_PID (
        tasklist /fi "PID eq !EXISTING_PID!" 2>nul | find "!EXISTING_PID!" >nul
        if not errorlevel 1 (
            call :log "Another watchdog instance is already running (PID !EXISTING_PID!). Exiting duplicate."
            exit /b 1
        )
    )
)

rd /s /q "%LOCK_DIR%" >nul 2>&1
2>nul mkdir "%LOCK_DIR%"
if errorlevel 1 (
    call :log "Could not acquire watchdog lock. Exiting duplicate."
    exit /b 1
)
>"%LOCK_PID_FILE%" echo !THIS_PID!
exit /b 0

:captureTrackedPids
call :findInstancePid "%MAIN%" PID_MAIN
call :findInstancePid "%BOT1%" PID_BOT1
call :findInstancePid "%BOT2%" PID_BOT2
>"%TRACKED_PID_FILE%" (
    echo MAIN=!PID_MAIN!
    echo BOT1=!PID_BOT1!
    echo BOT2=!PID_BOT2!
)
if not "!PID_MAIN!"=="!LAST_PID_MAIN!" (
    call :log "Tracked PID update: MAIN (%MAIN%) = !PID_MAIN!"
    set "LAST_PID_MAIN=!PID_MAIN!"
)
if not "!PID_BOT1!"=="!LAST_PID_BOT1!" (
    call :log "Tracked PID update: BOT1 (%BOT1%) = !PID_BOT1!"
    set "LAST_PID_BOT1=!PID_BOT1!"
)
if not "!PID_BOT2!"=="!LAST_PID_BOT2!" (
    call :log "Tracked PID update: BOT2 (%BOT2%) = !PID_BOT2!"
    set "LAST_PID_BOT2=!PID_BOT2!"
)
call :log "Tracking now -> MAIN:!PID_MAIN! BOT1:!PID_BOT1! BOT2:!PID_BOT2!"
exit /b 0

:findInstancePid
set "INSTANCE_NAME=%~1"
set "PID_VAR=%~2"
set "FOUND_PID="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$name=$env:INSTANCE_NAME; $p=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'javaw.exe' -and $_.CommandLine -and $_.CommandLine.ToLower().Contains($name.ToLower()) } | Select-Object -First 1 -ExpandProperty ProcessId; if($p){Write-Output $p}"`) do set "FOUND_PID=%%P"
set "%PID_VAR%=%FOUND_PID%"
exit /b 0

:isTrackedPidHealthy
set "INSTANCE_NAME=%~1"
set "TRACK_PID=%~2"
if not defined TRACK_PID exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "$procId=[int]$env:TRACK_PID; $name=$env:INSTANCE_NAME; $p=Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $procId) -ErrorAction SilentlyContinue; if($p -and $p.Name -eq 'javaw.exe' -and $p.CommandLine -and $p.CommandLine.ToLower().Contains($name.ToLower())){exit 0}else{exit 1}" >nul 2>&1
exit /b %errorlevel%

:logMissingReason
set "INSTANCE_NAME=%~1"
set "TRACK_PID=%~2"
if not defined TRACK_PID (
    call :log "Missing reason: %INSTANCE_NAME% has no tracked PID."
    exit /b 0
)
for /f "usebackq delims=" %%R in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$procId=[int]$env:TRACK_PID; $name=$env:INSTANCE_NAME; $p=Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $procId) -ErrorAction SilentlyContinue; if(-not $p){'pid-not-found'} elseif($p.Name -ne 'javaw.exe'){ 'pid-not-javaw' } elseif(-not $p.CommandLine){ 'pid-no-commandline' } elseif(-not $p.CommandLine.ToLower().Contains($name.ToLower())){ 'cmdline-mismatch' } else { 'healthy' }"` ) do set "MISS_REASON=%%R"
call :log "Missing reason: %INSTANCE_NAME% PID=%TRACK_PID% -> %MISS_REASON%"
exit /b 0

:addMissing
set /a missingCount+=1
if !missingCount! equ 1 (
    set "missingList=%~1"
) else (
    set "missingList=!missingList!, %~1"
)
exit /b 0

:startBots
start "" "%LAUNCH_SCRIPT%"
exit /b 0

:killTrackedPids
call :log "Stopping tracked bot PIDs only: MAIN=!PID_MAIN!, BOT1=!PID_BOT1!, BOT2=!PID_BOT2!."
for %%P in (!PID_MAIN! !PID_BOT1! !PID_BOT2!) do (
    if not "%%P"=="" (
        taskkill /f /pid %%P >nul 2>&1
    )
)
exit /b 0

:waitOrSkip
set "WAIT_SECS=%~1"
set "WAIT_MODE=%~2"
if /i "%WAIT_MODE%"=="noskip" (
    call :log "Waiting %WAIT_SECS%s..."
    timeout /t %WAIT_SECS% /nobreak >nul
    exit /b 0
)
call :log "Waiting %WAIT_SECS%s (press Enter to skip)..."
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=[int]$env:WAIT_SECS; $end=(Get-Date).AddSeconds($s); while((Get-Date)-lt $end){ if([Console]::KeyAvailable){ $k=[Console]::ReadKey($true); if($k.Key -eq [ConsoleKey]::Enter){ exit 0 } }; Start-Sleep -Milliseconds 120 }; exit 1" >nul 2>&1
if "%errorlevel%"=="0" call :log "Wait skipped by Enter."
exit /b 0

:log
set "msg=%~1"
echo [%date% %time%] %msg%
>>"%LOG_FILE%" echo [%date% %time%] %msg%
exit /b 0