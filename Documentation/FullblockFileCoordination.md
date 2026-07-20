# Fullblock Printer File Coordination

The Fullblock Printer can coordinate several Minecraft clients through a shared
folder instead of sending `/w` or other direct-message commands. One client is
the **File Master** and every other client is a **File Slave**.

This mode changes only the coordination transport. Building, compact circular-U
validation, normal placement, mining assignments, connector clearing,
pause/resume commands, and post-mining finalization still use the Fullblock
Printer's existing protocol and movement logic.

File coordination currently applies to the **Fullblock Printer** module. Use the
normal **Chat** coordination mode for the existing direct-message workflow. For
a solo printer, leave the mode on **Chat** and do not register any slaves.

## Requirements

Before starting:

- Every client must be in the same world and use the same map platform.
- Every client must have the source NBT under the same filename in its own
  configured `nerv-printer` map folder.
- Those NBT files must have identical bytes. Readiness compares the source
  SHA-256 and a SHA-256 of the generated runtime compact plan.
- Every client must load the same saved Fullblock Printer configuration file.
  File mode compares the config-file SHA-256; manually registering equivalent
  positions is not enough.
- Every client must be on the same server, in the same dimension, within the
  configured horizontal map-area margin and generated vertical support range,
  and have block data for all chunks intersecting the 128x128 map area.
- All clients must be able to read and write the same coordination directory.
- Player names must be entered exactly, including capitalization.
- Computers sharing the directory should have reasonably synchronized clocks,
  because heartbeat age is calculated from wall-clock timestamps.

The shared folder transports state and commands; it does not copy NBTs or
printer configuration files between clients.

## Settings

The settings are under **File coordination (multi-instance)**.

| Setting | Used by | Meaning |
| --- | --- | --- |
| `coordination-mode` | All clients | Select `FileMaster` on the coordinator, `FileSlave` on workers, or `Chat` for the existing DM system. |
| `shared-sync-folder` | File Master and File Slaves | The same readable/writable directory on every client. If empty, the client uses `<map folder>/_staircased_sync`. Clients with different game directories normally need an explicit shared path. |
| `file-poll-ticks` | File Master and File Slaves | Number of client ticks between state-file polls and heartbeat writes. The default is 20 ticks, approximately one second while the client is ticking normally. |
| `slave-player-names` | File Master | Comma-separated exact player names for every File Slave. The master cannot appear in this list and duplicate names are rejected case-insensitively. At least one slave is required for `FileMaster`. |
| `master-player-name` | File Slave | Exact player name of the File Master. |
| `peer-timeout-seconds` | File Master | Maximum age of a slave state file while checking the start-ready gate. The default is 15 seconds. |
| `require-all-slaves-ready` | File Master | When enabled, the master does not start until all configured slaves have a fresh heartbeat, the correct NBT generation, a complete config, and an acknowledged interval. Keeping this enabled is strongly recommended. |
| `recover-active-file-job` | File Master | Reconstruct and resume an in-progress persisted generation after a full client/module restart. Enabled by default. |
| `recovery-margin-blocks` | File Master and File Slaves | Horizontal margin around the 128x128 map in which a bot may publish ready/resume. Vertical position is also bounded around the generated walkable support range. |

`slave-player-names` is an ordered comma-separated configuration list, but
column assignment does not depend on the typed order. The current interval
allocator sorts participating slave names case-insensitively so assignments are
deterministic.

The settings in **Multi User** for DM commands, incoming-message prefix/suffix,
chat delay, and anti-spam suffixes do not carry coordination traffic in file
mode. Local module information and warnings can still appear in the client's
chat HUD.

## Shared Folder Files

The File Master writes:

```text
master_state.json
```

Each File Slave writes only its own file:

```text
slave_<sanitized-player-name>_state.json
```

Each JSON document contains:

- a schema version and the node's master/slave identity;
- the configured peer identities;
- a wall-clock heartbeat timestamp;
- printer metadata such as job ID, generation, source NBT, cycle phase,
  exact file/plan/config hashes, world/map identity, map-handoff substage,
  source and locked map IDs, status, position, chunk readiness, and readiness;
- durable FIFO command envelopes;
- message counters and cumulative acknowledgements.

One process owns each file, so clients do not overwrite a common document.
Writes use a unique temporary file and an atomic replacement when the shared
filesystem supports it. Commands remain in the sender's persisted outbox until
the recipient publishes an acknowledgement. A command may therefore be
delivered again after an interruption, but task IDs and the existing printer
protocol prevent a valid duplicate from becoming a second assignment.

Do not manually edit these files while any printer client is running. Temporary
files whose names begin with a dot may appear briefly during an update.

## Startup Sequence

Use this sequence for a predictable start:

1. Put the exact same source NBT filename and content in every client's map
   folder.
2. Copy or load the correct Fullblock Printer configuration on every client.
3. Set the master to `FileMaster`, enter all slave names, and select the shared
   folder.
4. Set every worker to `FileSlave`, enter the exact master player name, and
   select that same shared folder.
5. Enable the Fullblock Printer on the master. It selects the next source NBT
   and publishes a new job generation.
6. Enable the Fullblock Printer on every slave. Each slave reads the master's
   advertised filename, loads that local NBT, validates/generates its compact
   plan, loads its platform config, and publishes `ready`.
7. Use the start block on the master. Slaves do not need a separate start-block
   click after they are ready; the master releases them through the file
   protocol.

The File Master status table is populated from `slave-player-names`; the
**Register** action is not used in file mode.

## Ready and Heartbeat Gate

With `require-all-slaves-ready` enabled, the master waits until each configured
slave satisfies all of these conditions:

1. Its state-file timestamp is newer than `peer-timeout-seconds`.
2. It identifies itself as a Fullblock Printer file slave for the current
   master.
3. Its job ID and generation match the master's current NBT cycle.
4. Its advertised source filename matches the master's source filename.
5. Its source bytes and generated compact runtime plan hashes match.
6. Its saved config hash, circular-traversal setting, server, dimension, and
   map corner match.
7. Its player is inside the permitted X/Y/Z map zone and all map-area chunks
   are loaded or retained in the printer's map cache.
8. It publishes `active=true` and `ready=true`.
9. It has acknowledged its assigned pair-safe interval.

The poll interval can make readiness take a few seconds even when everything is
correct. A paused game, stopped client ticks, an unavailable network share, or a
clock that is far behind the master's clock makes the heartbeat appear stale.

This check is a **start gate**, not a continuous mid-print failure monitor. If a
slave disappears after the build has started, its work is not automatically
reassigned. The master may wait for that slave's build, mining, or finalization
response.

Disabling `require-all-slaves-ready` allows the master itself to begin sooner,
but it does not make an unconfigured or missing slave safe. A File Slave still
defers normal work commands until it is ready, and the master can later wait for
that configured slave. Leave the gate enabled unless you are deliberately
diagnosing a setup.

## Pair-Safe Work Assignment

The compact map has 64 inseparable column pairs:

```text
(0,1), (2,3), ... (126,127)
```

The interval allocator divides those pairs between the master and slaves.
Every interval begins on an even column and ends on the matching odd column, so
no compact connector is split between two bots.

This ownership rule applies even when a bot cannot use a continuous U traversal
for a particular pair. Its normal inventory analysis can select independent
column fallback for that pair, but another bot is never assigned the other half.
Mining also reserves the whole U pair before issuing a paired task. Connector
completion reports, mining task IDs, and finalization acknowledgements use the
same proven protocol as chat mode, transported through the JSON outboxes.

## Pause and Membership Changes

The File Master can pause or resume a configured slave from the slave table.
Pausing stops releases to that slave; resuming resends its interval before
releasing work.

Pause only at a safe boundary when possible. Pausing a bot during active work
does not redistribute its in-progress pair, and the master can still need that
bot to finish or report its assignment.

File-mode membership is static for one module run:

- `Register` does not discover or add file slaves.
- The file-mode table does not dynamically remove configured slaves.
- To add, rename, or remove a slave, finish the current map cycle and
  stop all clients. Back up and reset the shared coordination state directory,
  change the exact membership on every client, then start the new group.
- Reordering the same exact names has no effect because assignment order is
  deterministic.
- Do not change membership during building, mining, or mining finalization.

The local state file records the exact configured peer set and fails closed if
the setting changes. Restarting only the master after editing
`slave-player-names` is intentionally rejected, even after an idle cycle,
because silently adopting a different ownership set can overlap work.

There is no automatic reassignment of an offline slave's active build or mining
work.

## Disconnects, Crashes, and Restart Recovery

The shared transport is durable: unacknowledged commands and acknowledgement
counters survive reopening the same state files. A normal disconnect/relog in a
still-running client also keeps the Fullblock Printer's in-memory map-cycle
state, rechecks the world, and resynchronizes outstanding task IDs before moving.

A full process restart is also recoverable when
`recover-active-file-job=true`:

1. The master restores the exact job ID, generation, lifecycle phase, timing
   timestamps, logical NBT names, archive result, hashes, and map-handoff state.
2. A new coordination epoch invalidates commands from the previous process.
3. Every slave loads the master's exact generation and saved config, publishes
   fresh world/position/chunk readiness, and acknowledges its deterministic
   interval.
4. The master sends a recovery token. Every slave stops movement, clears stale
   build/mining tasks, and acknowledges that quiescent barrier.
5. `BUILDING` is reconstructed by scanning the assigned world targets and
   rebuilding only missing work. `MINING` is reconstructed by rescanning and
   issuing fresh task IDs.
6. Map handoff resumes from a durable substage:
   `PREPARE_INVENTORY`, `NEED_SUPPLIES`, `SUPPLIES_CONFIRMED`,
   `SOURCE_MAP_CONFIRMED`, `LOCKED_MAP_CONFIRMED`,
   `DEPOSIT_REQUESTED`, or `DEPOSITED`.
   The source and locked map IDs are persisted separately because locking a map
   creates a new ID, and those IDs must be distinct. Before quick-moving the
   locked map, the coordinator durably saves `DEPOSIT_REQUESTED`.

   The registered finished-map chest may feed a hopper or sorter. Therefore,
   the map is not required to remain visible in that input chest. After a
   restart, if the exact locked map is still in the player's inventory, the bot
   returns to the station and retries the deposit. If it is absent only after
   `DEPOSIT_REQUESTED` was durably saved, recovery treats the request as
   accepted and continues; `LOCKED_MAP_CONFIRMED` without the map does not make
   that inference.
7. `VERIFIED_CLEAR` retries timing/archiving, while `POST_MINING` retries
   finalization without re-mining or re-archiving.

Recovery is fail-closed. It will not resume if the persisted source/config/plan,
server, dimension, map corner, circular setting, peer membership, map IDs, or
archive identity cannot be proven. Move/load the bots into the correct area and
reactivate after correcting the mismatch; do not delete one state file to force
progress.

File Master mode stores this ownership in `<shared-sync-folder>/master_state.json`.
Solo and Chat mode do not create that shared file; they use the equivalent local
checkpoint at `<map folder>/_staircased_state/<player>_active_cycle.json`.
The local file records the same lifecycle and identity boundaries plus the
current teardown pair/support hint. The current server world remains
authoritative for exact block progress after either kind of restart.

Archive recovery also preserves the logical source name for the whole
generation. If a finished-folder collision added a numeric suffix—or a process
ended between moving the original and generated files—the source SHA-256 and
paired suffix are used to locate/complete the exact archive without selecting a
different NBT.

Never clear only one bot's state file during an active cycle. If the
coordination directory must be reset, stop every involved client, confirm that
no map cycle is active, back up the directory, clear it, and then start the
master and slaves together.

## Mining Finalization and the Next NBT

At the end of one NBT:

1. The master verifies that all map supports and required connectors are clear.
2. The coordinator records the NBT timing summary.
3. If `move-to-finished-folder` is enabled, the coordinator archives the
   original source NBT and its generated compact NBT together.
4. The master sends the mining-finalization command.
5. Every slave returns its used tools and acknowledges finalization.
6. Only after all required acknowledgements does the master select and publish
   the next NBT generation.

If timing or NBT archiving fails, the coordinator stops advancing and retries.
It does not silently select the same source as a new map while completion is
unrecorded.

## NBT Timing Summary

The Fullblock Printer coordinator writes:

```text
<map folder>/nbt_timing_summary.json
```

This summary is also produced for a solo run or a chat-coordinated master. File
Slaves do not write duplicate rows.

One row represents the full wall-clock cycle from the moment building starts
through verified map clearing. It includes map creation/locking, restocks,
waiting for bots, mining, and disconnect or full-process-restart downtime for
that persisted logical cycle. It ends at verified clear, before post-mining
used-tool deposits and slave finalization acknowledgements. It is not a
"productive movement only" timer.

Each row contains:

| Field | Meaning |
| --- | --- |
| `cycleKey` | Unique `jobId:generation` identity used to prevent duplicate completion rows. |
| `jobId`, `generation` | Coordinator run and NBT-cycle identity. |
| `sourceNbt` | Source NBT filename used as the queue identity. |
| `printingNbt` | Generated/validated compact NBT filename, when one was saved separately. |
| `startedAtMs`, `startedAtUtc` | Build-start time as epoch milliseconds and UTC text. |
| `completedAtMs`, `completedAtUtc` | Verified-clear time as epoch milliseconds and UTC text. |
| `elapsedMs`, `elapsed` | Exact elapsed milliseconds and a readable duration. |
| `recovered` | Whether this logical cycle resumed after a disconnect or full persisted process/module recovery. Recovery downtime remains part of elapsed wall-clock time. |
| `coordinator` | Player name of the master or solo bot that completed the cycle. |
| `botCount` | Coordinator plus configured slaves recorded for that cycle. |

Writes are serialized with `.nbt_timing_summary.lock`, then published through a
temporary file and atomic replacement when supported. Retrying the same
`jobId:generation` with identical data does not add a duplicate row. A malformed
existing summary is not overwritten; the printer reports the failure and waits
so timing history is not silently destroyed.

Do not delete the `.lock` file while a printer is writing. If the JSON becomes
damaged, stop the printer, preserve a backup, repair or restore a valid
`schemaVersion: 1` summary, and then resume the completion retry.

## Troubleshooting

### Master keeps waiting for ready slaves

Check:

- every exact player name and the master's `slave-player-names` list;
- that every client points to the same physical shared directory;
- that `master_state.json` and every expected slave state file have updating
  timestamps;
- the source NBT basename and content on every client;
- that every slave has loaded a complete Fullblock Printer config;
- that the clients are unpaused and ticking;
- filesystem permissions and clock synchronization;
- whether `peer-timeout-seconds` is long enough for a slow network share.

The `metadata.status` value in a slave state file can identify states such as
`MISSING_SOURCE_NBT`, `INVALID_SOURCE_NBT`, or
`WAITING_TO_FINISH_PREVIOUS_CYCLE`.

### No coordination messages appear in chat

That is expected. File mode sends no DM coordination commands. Use file
timestamps, local Fullblock Printer status messages, and the slave table to
diagnose progress.

### Slave loaded the right filename but is not ready

Compare the source SHA-256, compact-plan SHA-256, config SHA-256, circular
setting, server/dimension/map corner, player position, and `mapDataLoaded`
metadata. The filename alone is deliberately insufficient.

### State files stop updating

Confirm the client is still ticking and the shared filesystem is online and
writable. Consumer cloud-sync folders that create conflict copies can behave
poorly as a low-latency coordination filesystem; a reliable local shared
directory or network share with replace/rename support is preferable.

### A slave went offline during active work

The printer does not steal or reassign that bot's reserved pair. Restore that
client with its same exact name/config/state directory. The group enters a
fresh recovery epoch, quiesces every bot, then rescans before assigning work.
Do not alter membership or delete its state file while other bots are moving.

### Completion waits on `nbt_timing_summary.json`

Check map-folder write permission and whether the existing JSON is malformed.
The coordinator intentionally refuses to clobber an invalid history file.
