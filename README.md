# Nerv Printer — 6b6t Staircase Optimization

This repository is a development fork of [Julflips/Nerv Printer](https://github.com/Julflips/nerv-printer-addon) focused on faster, safer, and more recoverable **staircased full-block map-art printing on 6b6t**.

The main work is in [`nerv-printer-addon`](nerv-printer-addon). It keeps Nerv Printer's autonomous build, map creation, teardown, material recycling, and multi-account workflow while adding a compact circular U route and stricter safety logic for laggy, anti-cheat-sensitive environments.

> **Development status:** this is an optimization branch, not a general upstream release. Test a new build under supervision before leaving bots unattended.

## Download

[**Download the Nerv Printer JAR**](nerv-printer-26.2.jar)

## What is optimized

- **Compact circular U traversal** — builds paired columns as one outbound leg, connector, and return leg instead of treating every line independently.
- **Reach-aware building** — compiles which blocks are safely reachable from each support cell and persists that topology for consistent routing.
- **Reach-optimized teardown** — assigns complete remote lanes to safe host routes, preserves walkable support, and keeps mining monotonic and ordered.
- **Connector and turn safety** — validates connector material, headroom, exit support, checkpoint handoff, and turnaround behavior before committing to a route.
- **Restart and reconnect recovery** — stores lifecycle checkpoints, waits for stable player/world snapshots, and reconstructs the active build or teardown cursor instead of replaying stale actions.
- **Sparse teardown recovery** — uses bounded temporary cobblestone scaffolds for verified leftovers rather than performing unordered reach scans.
- **TPS-aware action control** — caps placement and mining attempts, scales them with measured TPS, and can pause actions below a configured TPS floor.
- **Inventory and tool safety** — plans complete-U material demand, reserves teardown scaffolding, tracks durable tools, and waits for authoritative inventory acknowledgements.
- **Multi-bot coordination** — preserves whole-pair ownership and supports shared-file master/slave coordination with recovery-aware job state.
- **Bounded logistics detours** — can route around small obstacles while travelling to chests and workstations without changing the map traversal itself.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`nerv-printer-addon/`](nerv-printer-addon) | Main Nerv Printer source, tests, Gradle build, schematics, and documentation. |
| [`meteor-addon-template/`](meteor-addon-template) | Companion Comic Auto Portal/reconnect addon source and the multi-bot setup guide moved from the repository root. |
| [`ComicAutoPortal-0.1.0.jar`](ComicAutoPortal-0.1.0.jar) | Prebuilt companion reconnect addon. |
| [`start_all_bots.bat`](start_all_bots.bat) | Starts the configured Prism Launcher bot instances. |
| [`watchdog.bat`](watchdog.bat) | Monitors and restarts the configured bot stack. |

## Current development target

The checked-in Gradle configuration currently targets:

- Minecraft `26.2`
- Native Mojang names (Minecraft 26.2 is unobfuscated)
- Fabric Loader `0.19.3`
- Meteor Client `26.2-SNAPSHOT`
- Java toolchain `25`

These versions move with development. Treat [`gradle.properties`](nerv-printer-addon/gradle.properties) as the source of truth.

## Requirements and assumptions

- The server must permit placing blocks in the air; the staircased full-block workflow cannot run where that is blocked.
- Input must be a complete MapArtCraft-style NBT with a valid surface block for every map column. Invalid dimensions, entities, block entities, unsafe headroom, or incomplete shafts fail closed.
- The printer platform needs the north access walkway, clear player headroom, storage, tool return, cartography, and map handoff components described in the setup guide.
- Circular traversal is capacity-sensitive. Before teardown, the bot requires compatible pickaxes and an axe, registered used-tool storage, a cobblestone source, and the configured scaffold reserve.
- File-coordinated bots must use matching NBT/config identities, server and dimension, map area, account names, and a reliable shared directory.

## Build

Use JDK 25, then run the Gradle wrapper from the main addon directory. The
wrapper can provision the Java 25 compilation toolchain automatically when a
local JDK 25 installation is unavailable.

This repository is not a top-level Gradle aggregate, so run build commands inside `nerv-printer-addon`. If a Windows checkout fails with `Filename too long`, enable Git long-path support before cloning again:

```powershell
git config --global core.longpaths true
```

### Windows PowerShell

```powershell
cd nerv-printer-addon
.\gradlew.bat clean test build
```

### Linux or macOS

```bash
cd nerv-printer-addon
./gradlew clean test build
```

The built addon JAR is written to `nerv-printer-addon/build/libs/`.

## Install

1. Install Fabric Loader and a compatible Meteor Client build for the configured Minecraft version.
2. Build the project or obtain the intended Nerv Printer JAR.
3. Copy the Nerv Printer JAR into the Minecraft instance's `mods` folder alongside Meteor Client.
4. If reconnect automation is needed, also install `ComicAutoPortal-0.1.0.jar`.
5. Use the **same Nerv Printer build and configuration generation on every participating bot**.

## Configure and run

Start with the [Staircased/Fullblock Printer guide](nerv-printer-addon/Documentation/StaircasedGuide.md). It covers the platform, registered storage, NBT loading, building, map creation, teardown, and optional multi-user operation.

For file-based multi-instance control, see [Fullblock Printer file coordination](nerv-printer-addon/Documentation/FullblockFileCoordination.md). For Prism Launcher startup, reconnect, and watchdog setup, see the [multi-bot setup guide](meteor-addon-template/README.md).

A normal cycle is:

1. Load and validate the next NBT plan.
2. Restock the active compact U and required repair tools.
3. Build both legs and their connector using the continuous route.
4. Create and lock the finished map.
5. Verify tools and scaffold reserves, then tear down and recycle the structure.
6. Authoritatively scan for leftovers, recover them safely, and advance to the next map.

## 6b6t operating notes

- Begin with conservative `max-block-actions-per-second` and interaction-range values.
- Keep TPS scaling enabled when server performance is inconsistent, and choose a sensible minimum action TPS.
- Do not bypass a safety stop caused by missing support, obstructed headroom, an invalid connector, or an unstable recovery snapshot without checking the world state.
- Keep the shared sync path identical on every machine and start file-coordinated slaves before the master.
- Enable structured debug output while commissioning a route; repeated messages are rate-limited and coalesced.

## Recent optimization work

The latest feature commits leading into the current layout include:

- [`d4b8dd9`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/d4b8dd9) — introduced compact circular U traversal.
- [`225f1d4`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/225f1d4) — hardened server-authoritative coordination, action budgeting, refill/recovery behavior, and its test coverage.
- [`b8ea706`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/b8ea706) — added stable recovery snapshots and improved mining jump recovery.
- [`1ebdf47`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/1ebdf47) and [`5440055`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/5440055) — formalized ordered U movement and improved reach-optimized teardown host selection.
- [`324a224`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/324a224) — added persisted teardown topology, grounded support planning, and shared tool-durability policy.
- [`cb25432`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/cb25432) — added persisted circular build reach topology.
- [`9c59526`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/9c59526) — added connector handoff checkpoints and route rejoin planning.
- [`9a4941b`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/9a4941b) — tightened durable checkpoint-pair selection and recovery ownership.
- [`a78b5ac`](https://github.com/ComicSquid74273/nerv-printer-addon-Staircase-Optimised/commit/a78b5ac) — tightened turnaround handling and exit-support validation.

The implementation is backed by focused JUnit tests under [`nerv-printer-addon/src/test`](nerv-printer-addon/src/test), covering reach topology, traversal, recovery, inventory planning, coordination, and teardown safety.

## Documentation

- [Original Nerv Printer overview](nerv-printer-addon/README.md)
- [Staircased/Fullblock Printer guide](nerv-printer-addon/Documentation/StaircasedGuide.md)
- [File-coordination guide](nerv-printer-addon/Documentation/FullblockFileCoordination.md)
- [Carpet Printer guide](nerv-printer-addon/Documentation/CarpetGuide.md)
- [Companion reconnect and bot-automation setup](meteor-addon-template/README.md)

## Credits and license

Nerv Printer was created by [Julflips](https://github.com/Julflips). This repository contains a specialized optimization branch and companion automation maintained for the 6b6t workflow.

The main addon's license is available at [`nerv-printer-addon/LICENSE`](nerv-printer-addon/LICENSE).
