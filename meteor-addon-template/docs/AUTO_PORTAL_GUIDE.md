# Auto Portal Addon Guide

This guide is for anyone who needs to install, configure, and use this addon without reading the source code.

## What This Addon Does

`Auto Portal Resume` automates reconnect flow on Meteor Client by:
- Detecting when you are near configured login/spawn portal areas.
- Running Baritone to route you into portal flow.
- Optionally pausing Carpet Printer during automation.
- Optionally restoring Carpet Printer at your base zone.
- Optionally auto-joining a server from the title screen.

## Requirements

- Java 21
- Minecraft/Fabric versions that match this project config
- Meteor Client compatible with this build
- Baritone available in your Meteor setup (required for path automation)

## Build And Install

From project root:

```powershell
.\gradlew.bat build
```

Output jar is created in `build/libs`.

Install by placing the built jar into your Minecraft `mods` folder together with Meteor Client (and other required mods for your setup).

## First-Time Setup In Meteor

1. Open Meteor modules and enable `auto-portal-resume` once to access settings.
2. Configure these groups first:
   - `Login portal zone`
   - `Spawn portal`
   - `Carpet printer resume`
   - `Title auto join` (optional)
3. Disable and re-enable the module after major setting changes.

## Quick Start Presets

### Preset A: Login Portal Only
Use this if you only need routing in your login portal box.

Recommended:
- `spawn-portal-enabled = false`
- Set `login-portal-x/y/z` and `login-portal-radius`
- Keep `login-portal-only-overworld = true`
- Keep `baritone-no-break = true`

### Preset B: Spawn Hub Portal Only
Use this if you route from your spawn hub/disk.

Recommended:
- `spawn-portal-enabled = true`
- `spawn-portal-require-login-chat = false` (or true if your server needs login confirmation)
- Set `spawn-disk-center-x/z` and `spawn-disk-radius`
- Set `spawn-waypoint-*` and `spawn-portal-target-*`

### Preset C: Full Flow + Printer Safety
Use this for full automation and controlled printer handling.

Recommended:
- Enable both login and spawn portal behavior
- `resume-carpet-printer = true`
- `auto-start-printer-at-base = true`
- Set `printer-restore-x/y/z`, `printer-restore-radius`, and dimension
- Set a safe `login-chat-trigger` that matches your server login success message

## Setting Reference (Important Ones)

### Auto Enable
- `auto-enable`: master switch for automatic re-enable near configured zones.
- `auto-enable-near-spawn-disk`: allow auto-enable in spawn disk area.
- `auto-enable-near-login-portal`: allow auto-enable in login portal box.

### Login Portal Zone
- `login-portal-x/y/z`, `login-portal-radius`: center and size of login portal box.
- `login-portal-only-overworld`: strongly recommended to keep on.
- `login-portal-thisway`: uses Baritone `thisway` + `path` instead of `goto nether_portal`.

### Spawn Portal
- `spawn-portal-enabled`: enables spawn-disk based portal automation.
- `spawn-portal-dimension`: `overworld`, `end`, `nether`, or `any`.
- `spawn-disk-center-x/z`, `spawn-disk-radius`: horizontal disk definition.
- `spawn-two-step-route`: waypoint then portal target.
- `spawn-portal-thisway`: alternate Baritone mode based on look direction.

### Baritone Safety/Recovery
- `baritone-no-break`: avoid block breaking while pathing.
- `baritone-restore-allow-break`: restore `allowBreak true` on stop/deactivate.
- `baritone-stuck-recovery`: retries when stuck after command dispatch.

### Carpet Printer Resume
- `resume-carpet-printer`: whether addon can pause printer during run.
- `auto-start-printer-at-base`: whether addon re-enables printer at base zone.
- `printer-restore-*`: base zone center/radius/dimension/delay.

### Title Auto Join
- `title-auto-join-enabled`: auto-connect from title screen.
- `title-auto-join-host`: server host.
- `title-auto-join-delay-ticks`: delay before join attempt.

## Common Workflow

1. Launch game with Meteor + addon.
2. Validate portal coordinates using your in-game position.
3. Toggle module on.
4. Watch Meteor chat logs for warnings/info from this addon.
5. Adjust radii and delays if you see early/late path behavior.

## Troubleshooting

### Nothing happens near portal
- Confirm module is enabled.
- Confirm coordinates and radius actually include your position.
- Confirm dimension requirements are satisfied.
- Check if `auto-enable` is disabled.

### Baritone commands do not run
- Ensure Baritone is available in your client.
- Check Baritone prefix is configured.
- Disable `baritone-no-break` temporarily to diagnose path constraints.

### Spawn portal never starts
- If `spawn-portal-require-login-chat = true`, ensure `login-chat-trigger` matches server success message exactly enough.
- Confirm you are inside configured spawn disk.
- Review `spawn-portal-wait-after-enter-ticks` and login delay settings.

### Printer is not restored
- Set `resume-carpet-printer = true` and `auto-start-printer-at-base = true`.
- Verify `printer-restore-*` zone includes your actual base position.
- Confirm printer module id (`printer-module-id`) matches your module if auto-detect fails.

### Auto-join from title does not connect
- Verify `title-auto-join-host` is correct.
- Ensure you are actually on title screen and not already connected.
- Reduce/increase join delay ticks as needed.

## Safety Notes

- Start with conservative radii and increase gradually.
- Keep `baritone-no-break` enabled unless testing a blocked route.
- Test with printer auto-start disabled first, then enable once zone is verified.

## Maintenance

If you update Minecraft/Fabric/Meteor versions:
- Update versions in `gradle/libs.versions.toml`.
- Rebuild and re-test this addon behavior in your target server flow.
- Re-check coordinate and dimension assumptions after update.
