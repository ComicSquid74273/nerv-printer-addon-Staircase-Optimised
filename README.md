# Nerv Printer Setup Guide
## Auto Reconnect + Carpet Printer + Prism Launcher + Watchdog

This guide explains how to set up a shared **Nerv Printer** system so another person can install it, run it, and maintain it safely.

It covers:
- Nerv Auto Reconnect
- Carpet Printer master/slave coordination
- Shared sync folder requirements
- Recommended startup order
- Prism launcher automation
- `start_all_bots.bat`
- `watchdog.bat`
- Optional Windows startup automation

---

## 1. What This Setup Does

This setup is designed for a coordinated multi-bot carpet printing workflow.

Main goals:
- Auto reconnect bots after disconnects
- Run one **master** and one or more **slaves**
- Coordinate progress through shared state files
- Allow recovery after restart/crash
- Auto-launch all bot instances from Prism Launcher
- Monitor them continuously and restart the whole stack if needed
- Optionally start everything automatically when Windows starts

---

## 2. Required Mods and Software

### Minecraft side
- Meteor Client
- Nerv Printer addon
- Fabric loader
- Matching Minecraft version on all bots

### Launcher / OS side
- Prism Launcher
- PowerShell (used internally by the batch files)
- Windows Task Scheduler (optional, for auto-start on login/boot)

### Important rule
Use the **same Nerv Printer jar** on every bot instance.

Do **not** run mixed versions of the addon.

---

## 3. Auto Reconnect Setup

Inside Meteor modules:
- Enable **Nerv Auto Reconnect**

Recommended setting:
- `reconnect-delay-seconds`
  - `10 to 20` seconds for normal use
  - `20 to 40` seconds if the server has anti-spam or login throttling

### Important
- Use **Nerv Auto Reconnect**, not another conflicting reconnect module
- If reconnect loops too fast, increase the delay

---

## 4. Carpet Printer Roles

Assign one role per bot.

### Master
The master bot:
- Selects chests / start block / map settings
- Controls run state
- Controls next-map progression
- Waits for slave state before continuing when required

### Slave
Each slave bot:
- Waits for the master state file
- Loads the assigned map and interval
- Reports ready / finished status using shared state files

---

## 5. Shared Folder Requirements

All bots must use the **same shared sync folder path** for coordination files.

Typical files:
- `master_state.json`
- `slave_name_state.json`

### If bots are on the same machine
They still must point to the exact same folder path.

### If bots are on different machines
The path must refer to a shared network folder that all machines can access.

### Important
If the sync folder path differs between bots, coordination will break.

---

## 6. Recommended Startup Order

Use this order to avoid readiness race issues:

1. Start all slave clients first
2. Enable Carpet Printer on all slaves
3. Start the master client
4. Enable Carpet Printer on the master

This helps prevent:
- Slave readiness mismatch
- Delayed master recovery
- State-file race issues

---

## 7. Cartography Timing / Delay Setting

Use `post-build-delay-ticks` in Carpet Printer.

What it does:
- Adds a wait after finishing the build before continuing the map-finalization flow

Recommended usage:
- Set this on the **master**
- Slaves usually do not need this for cartography timing

Suggested values:
- `0 to 20` ticks = minimal pause
- `40 to 100` ticks = safer on laggy servers

---

## 8. Restart Behavior

If the master restarts mid-map:
- The master auto-arms and waits for recovery checks
- It only starts once the required slave states are acceptable
- Slaves must keep reporting correct ready / finished state in the shared files

### If the master gets stuck at `SelectingChests`
Common causes:
- State-file mismatch
- A slave is not marked ready / finished
- An old Nerv Printer jar is still running on one of the bots

---

## 9. Fast Troubleshooting Checklist

Before debugging anything deeper, confirm all of these:

1. All bots run the same latest Nerv Printer jar
2. All bots point to the same sync folder
3. There is only one master
4. Slaves are listed correctly in the master settings
5. `reconnect-delay-seconds` is not too low
6. Shared files are actually updating

Useful log phrases to look for:
- `Master automation armed`
- `Loaded map from master`
- `AwaitSlaveNextMap`
- `Not all slaves report ready`
- `No assigned interval`

---

## 10. Safe Default Profile

### Nerv Auto Reconnect
- ON
- `reconnect-delay-seconds = 15`

### Carpet Printer Master
- `require all slaves ready = ON`
- `post-build-delay-ticks = 40`

### Carpet Printer Slaves
- Same sync folder as master
- Same job/map folder settings
- Role set to `Slave`

---

## 11. Prism Launcher Bot Automation

This setup also includes two batch files:
- `start_all_bots.bat`
- `watchdog.bat`

Together, they handle:
- Launching all Prism instances
- Verifying the instances are really running
- Monitoring the stack continuously
- Restarting everything after repeated failures
- Preventing duplicate watchdog windows

---

## 12. Files and Logs

### Scripts
- `C:\Users\TRUECLOUD\Desktop\start_all_bots.bat`
- `C:\Users\TRUECLOUD\Desktop\watchdog.bat`

### Logs
- `D:\PrismLauncher\logs\start_all_bots.log`
- `D:\PrismLauncher\logs\watchdog.log`

---

## 13. What to Change in `start_all_bots.bat`

If someone else is using this setup on another machine, these values usually need to be edited.

### Required lines/settings to change

#### 1. Prism executable path
Change this to the correct Prism Launcher executable:
```bat
set "PRISM=C:\Users\TRUECLOUD\AppData\Local\Programs\PrismLauncher\prismlauncher.exe"
```

#### 2. Prism data directory
Change this if the Prism data folder is somewhere else:
```bat
set "DATA=D:\PrismLauncher"
```

#### 3. Log directory
Change if you want logs stored somewhere else:
```bat
set "LOG_DIR=D:\PrismLauncher\logs"
```

#### 4. Instance names
These must match the exact Prism instance names:
```bat
set "MAIN=Bot 003 1.0.0"
set "BOT1=Bot 001 1.0.0(1)"
set "BOT2=Bot 002 1.0.0(1)"
```

If your instances have different names, change them here.

### Optional timing values you may change
These do not always need editing, but they control launch behavior:
- `PRISM_READY_TIMEOUT`
- `PRISM_READY_POLL`
- `PRISM_STABLE_WAIT`
- `CHECK_INTERVAL`
- `MAX_WAIT_PER_INSTANCE`

### When to change them
- Increase timeouts if Prism opens slowly
- Increase waits if Minecraft instances take longer to fully appear
- Increase max wait if the PC is weak or heavily loaded

---

## 14. What to Change in `watchdog.bat`

If someone else is using the watchdog on another machine, these settings usually need to be edited.

### Required lines/settings to change

#### 1. Instance names
These must match the exact Prism instances the watchdog is checking:
```bat
set "MAIN=Bot 003 1.0.0"
set "BOT1=Bot 001 1.0.0(1)"
set "BOT2=Bot 002 1.0.0(1)"
```

#### 2. Launch script path
This must point to the correct `start_all_bots.bat`:
```bat
set "LAUNCH_SCRIPT=C:\Users\TRUECLOUD\Desktop\start_all_bots.bat"
```

#### 3. Log directory
Change if logs are stored elsewhere:
```bat
set "LOG_DIR=D:\PrismLauncher\logs"
```

#### 4. Lock directory
This is used to prevent duplicate watchdog windows:
```bat
set "LOCK_DIR=%TEMP%\minecraft_bot_watchdog.lock"
```

You can keep this as-is unless you have a reason to move it.

### Timing values you may change
- `BOOT_WAIT`
- `CHECK_EVERY`
- `MISSING_THRESHOLD`
- `STARTUP_GRACE`
- `AUTO_START_ON_BOOT`

### What they do
- `BOOT_WAIT` = delay before watchdog begins checking
- `CHECK_EVERY` = seconds between health checks
- `MISSING_THRESHOLD` = how many failed checks before restart
- `STARTUP_GRACE` = recovery time after relaunch before strict checking resumes
- `AUTO_START_ON_BOOT` = whether watchdog launches bots automatically on first start

### Recommended usage
- Increase `MISSING_THRESHOLD` if you want fewer aggressive restarts
- Increase `STARTUP_GRACE` if the game takes a long time to load
- Leave `AUTO_START_ON_BOOT=1` if you want full automation

---

## 15. Normal Operation

### One-time manual launch
1. Run `start_all_bots.bat`
2. Wait until the log confirms all instances are running
3. If anything fails, read `start_all_bots.log`

### Continuous monitoring
1. Run `watchdog.bat`
2. Let it wait through boot delay
3. Let it launch bots automatically if enabled
4. Leave it running in its own terminal window

### Recommended daily usage
Use **only** `watchdog.bat`.

That way:
- watchdog launches the stack
- watchdog monitors the stack
- watchdog restarts the stack after repeated failures

---

## 16. Watchdog Restart Logic

Every `CHECK_EVERY` seconds, watchdog checks the tracked processes.

A bot is considered healthy only if:
- the PID exists
- the process is `javaw.exe`
- the command line still matches the configured Prism instance name

If bots are missing for multiple checks in a row and the count reaches `MISSING_THRESHOLD`, watchdog will:
1. Kill tracked bot PIDs
2. Kill `prismlauncher.exe`
3. Run `start_all_bots.bat`
4. Wait through `STARTUP_GRACE`
5. Resume strict health checking

---

## 17. Log Messages to Watch

### In `start_all_bots.log`
Good signs:
- `Prism is running and stable.`
- `Instance <name> is running.`
- `All bot instances launched and confirmed running.`

Warning signs:
- `Prism Launcher did not become ready in time.`
- `Instance <name> not detected within ...`

### In `watchdog.log`
Good signs:
- `Tracking now -> MAIN:... BOT1:... BOT2:...`
- `All instances running.`

Useful warnings:
- `Missing detected (x/y): ...`
- `Threshold reached. Restarting all bots...`
- `Missing reason: <instance> PID=<pid> -> <reason>`

Common reasons:
- `pid-not-found`
- `pid-not-javaw`
- `pid-no-commandline`
- `cmdline-mismatch`

---

## 18. Common Issues and Fixes

### Prism cannot be found
- Verify the `PRISM` path in `start_all_bots.bat`
- Confirm Prism is installed there

### Instance never gets detected
- Verify instance names exactly match Prism
- Confirm the instance launches manually in Prism
- Confirm command line visibility is not blocked by security tools

### Watchdog restarts too aggressively
- Increase `MISSING_THRESHOLD`
- Increase `STARTUP_GRACE`
- Increase `CHECK_EVERY`

### Duplicate watchdog windows
Delete the stale lock folder if needed:
```text
%TEMP%\minecraft_bot_watchdog.lock
```

---

## 19. Optional: Start Everything Automatically When the PC Starts

You can add the watchdog to Windows startup so the printer system starts automatically after reboot.

### Recommended method: Task Scheduler

1. Open **Task Scheduler**
2. Click **Create Task**
3. On the **General** tab:
   - Name: `Minecraft Bot Watchdog`
   - Enable **Run with highest privileges**
4. On the **Triggers** tab:
   - Add a trigger for **At log on**
   - or **At startup** if you want it before login
5. On the **Actions** tab:
   - Program/script:
     ```text
     C:\Windows\System32\cmd.exe
     ```
   - Add arguments:
     ```text
     /c "C:\Users\TRUECLOUD\Desktop\watchdog.bat"
     ```
6. On the **Conditions** tab:
   - Uncheck `Start the task only if the computer is on AC power` if needed
7. Save the task
8. Test it manually once

### Result
After Windows starts:
- Task Scheduler starts `watchdog.bat`
- watchdog launches bots
- bots reconnect automatically
- Carpet Printer resumes automatically if the setup is valid

---

## 20. Safe Change Policy

When changing names, paths, timing, or automation:

1. Edit one script carefully
2. Test `start_all_bots.bat` first
3. Confirm all instances launch correctly
4. Then test `watchdog.bat`
5. Keep a backup copy of the previous working scripts

---

## 21. Quick Handover Checklist

Before handing this setup to another operator, confirm:
- Prism path is correct
- Data/log directories exist and are writable
- Instance names match exactly
- All bots use the same Nerv Printer jar
- All bots use the same shared sync folder
- `start_all_bots.bat` works by itself
- `watchdog.bat` works without duplicate-lock errors
- Windows startup task is tested if auto-start is desired

---

## 22. Final Summary

This system is meant to be a mostly self-recovering printing stack:

1. Windows starts
2. Task Scheduler starts watchdog (optional)
3. watchdog launches all Prism bot instances
4. bots reconnect automatically if disconnected
5. master/slave Carpet Printer coordinates through shared files
6. if something fails repeatedly, watchdog restarts the full stack

This gives you a practical unattended bot-printing workflow with recovery after disconnects, crashes, or reboots.
