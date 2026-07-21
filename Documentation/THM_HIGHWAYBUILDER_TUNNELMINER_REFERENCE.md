# THM HighwayBuilder and TunnelMiner implementation reference

## Purpose

This is the source-level reference used for the deliberately staged implementation in
`nerv-printer-addon`. It explains how THM performs high-rate mining and placement,
acts on blocks without aiming the visible camera at them, moves accurately, manages
the hotbar, transfers items between inventories, and overlaps mining with placement.

This document remains analysis, not copied implementation code. The Nerv adaptation
uses its own ordered U traversal, confirmation, inventory, and recovery contracts.

## Source provenance

The findings below are pinned to this local source revision:

- Repository: `THM-Addons`
- Upstream: `https://github.com/Leonn170709/THM-Addons`
- Branch: `1.21.11`
- Commit: `0ce5ce8a9ba17038af678f2713d5de52e472029f`
- Commit subject: `Fix im high not disabling`
- Commit date: 2026-07-16
- Target versions in the checked-out project: Minecraft/Yarn/Meteor `1.21.11`

Line numbers are tied to that commit. Re-audit them before using a later THM
revision.

Primary files:

- `src/main/java/xyz/thm/addon/modules/HighwayBuilderTHM.java`
- `src/main/java/xyz/thm/addon/modules/TunnelMinerModule.java`
- `src/main/java/xyz/thm/addon/modules/HotbarManager.java`
- `src/main/java/xyz/thm/addon/modules/PaketLimiter.java`
- `src/main/java/xyz/thm/addon/utils/PacketPlaceUtils.java`
- `src/main/java/xyz/thm/addon/utils/InventoryManager.java`
- `src/main/java/xyz/thm/addon/modules/Speedmine.java`
- `src/main/java/xyz/thm/addon/modules/Nuker.java`
- `src/main/java/xyz/thm/addon/utils/Enums.java`
- `src/main/java/xyz/thm/addon/modules/THMHwyMonitor.java`
- `src/main/java/xyz/thm/addon/mixin/HighwayBuilderCameraMixin.java`
- `src/main/java/xyz/thm/addon/mixin/HighwayBuilderEntityMixin.java`
- `src/main/java/xyz/thm/addon/mixin/HighwayBuilderBowMixin.java`

Meteor methods such as `BlockUtils.breakBlock`, `BlockUtils.place`,
`BlockUtils.interact`, `InvUtils.swap`, `InvUtils.move`, `AutoTool.getScore`, and
`TickRate.INSTANCE` are dependencies. This repository shows how THM calls them, but
not their complete implementations. Their exact behavior must be rechecked against
the Meteor version used by Nerv.

## Review method and limits

The two primary classes, plus the standalone THM `Speedmine` and `Nuker` modules,
were traced from activation through their live tick dispatchers and action helpers.
A project-wide symbol/call-site search then covered TPS access,
dig/interact/movement packets, selected-slot changes, inventory clicks, movement
ownership, FreeLook/camera hooks, packet limiting, and monitoring. Unrelated GUI,
cosmetics, combat-only, and rendering code was not treated as a printer reference.

This is static code analysis, not a live-server throughput benchmark. Any claim
about server acceptance, real BPS, or anti-cheat behavior must be measured on the
target server before enabling a Nerv optimization.

## Nerv adaptation implemented from this reference

The add-on deliberately does not reinterpret THM's `blocks-per-tick = 30` as a
safe direct port. Nerv exposes a hard maximum of 30 new block-action attempts per
real second, optionally scales it by `TPS / 20`, pauses below the configured TPS or
after a 1.5-second stale server-tick sample, and prevents idle credits from becoming
an unbounded burst.

Printing keeps the complete current two-column U as its mandatory inventory and
placement plan. Before the refill trip, dependency-closed nearby surface targets
are admitted only from remaining managed slot capacity. Mandatory U shortages
are filled first; optional shortages may use the remaining capacity and are
discarded if their registered sources cannot supply them. Runtime placement
still requires strict material surplus above the U reservation, but may use a
server-confirmed hotbar swap after primary U decisions consume the current
budget. Mandatory
route targets use one canonical smart-placement contract: a confirmed adjacent
face selects normal `BlockUtils` placement, while a replaceable in-range target
without a face selects THM-style direct air placement of that real map block.
Optional nearby targets are never air-placed. Both modes consume the Nerv action
budget and enter the same per-position server-confirmation/retry ledger. On every
active-U tick, the placement scheduler scans the complete ordered U rather than
stopping at the first missing target. It can therefore spend the current
TPS-scaled budget on all eligible targets inside the five-block eye-to-center
reach, including later forward targets on either U column. Pending mandatory
placement confirmations elsewhere in the U do not serialize all movement or
prevent strict-surplus nearby placement when no additional U target is currently
actionable. Here actionable means in reach, placement-eligible, materially
available, and not already pending; an acknowledged or in-flight U target cannot
accidentally suppress the nearby pass. Active repairs, confirmed hotbar swaps,
and reachable actionable U targets may still hold movement. Missing targets
outside the immediate travel step do not hold movement merely because they lack
a placement side. The exact next ordered support is different: it must be the expected
server-confirmed block with clear headroom before forward input is allowed. Nerv
builds this guard from the one-block-back alignment, outbound north walkway,
complete ordered U, and return north walkway, so it applies equally to the
outbound leg, connector, and return leg. Vertical motion retains the original
Nerv contract: checkpoint progress is horizontal, vanilla gravity owns lower
terrain, and the generic solid-ahead/headroom auto-jump owns higher terrain.
The ordered cursor can stay in its current horizontal cell or advance to its
immediate cardinal successor, independent of player Y, slope direction, or
`onGround`. Generated route geometry limits every adjacent height change to one
block. The cursor therefore cannot skip cells, attach to the nearby return leg,
or confuse another helix level. The newest authoritative server observation
also takes precedence over the slower area cache, preventing an acknowledged
block from being immediately resubmitted. Movement holds are logged as
transitions instead of once per tick. Healthy action-budget diagnostics are
likewise summarized once per second, while pause/recovery transitions are
immediate.

The same rule applies in reverse during interrupted-build egress. Nerv chooses
one safe, shortest north direction and advances the recovery cursor only when
the player enters the immediate horizontal cell. It does not chase a full 3D
block center, which could reverse yaw after horizontally crossing a descending
or jumping target. A truly missing outbound support enters this ordered recovery
contract instead of inserting a forward/backward checkpoint retry loop.

Nerv does not apply Meteor's `rotate-place` callback to every block. It
classifies the target block's vanilla state properties: only player-look-driven
`facing`, `horizontal_facing`, `orientation`, or standing `rotation` properties
may rotate. Ordinary full blocks go directly through THM-style air placement
even when an adjacent face exists, so they neither look at the target nor wait
for a rotation callback. When a facing-sensitive placement does use a deferred
callback, it binds and re-selects its planned slot at dispatch time; another
budgeted placement therefore cannot change its material first.

The circular alignment support is derived from the ordered route itself. Starting
at the selected north-walkway endpoint, Nerv takes the horizontal step toward the
first U support and extends that step once in the opposite direction. There is no
fixed north/south direction, relative-Z constant, or separate raised-entry route.
The resulting approach support must be solid with two air blocks of headroom.
From there, the normal forward input and generic solid-ahead/headroom auto-jump
own both level and `Y+1` entry transitions.

Recovery treats the alignment plus both north walkways as external direct-replan
supports. This is required because the five-block scheduler may confirm several
forward blocks while the player is still standing at the alignment. Restarting
there must rebuild the frozen pair plan; it must not attempt an in-U egress or
classify the alignment introduced by Nerv itself as an unknown location.
Printing and teardown now instantiate the same active ordered-U movement context.
That context alone resolves the current/next support, selects the end of the
current straight segment, steers, applies the shared sprint policy, and continues
the generic auto-jump. Endpoint entry follows the same ordered `approach -> north
walkway -> first U support` path; local recovery starts from the verified occupied
support. Teardown contributes only work released by entered support indices. It
does not own another steering goal, per-block checkpoint loop, centering rule,
landing rule, jump state, sprint state, velocity rule, or progress watchdog.
Unconfirmed next support and non-overlappable progressive break work use the same
movement hold path as blocking placement/repair work. A route plan is rejected if
any attached break target would remove a support needed later, including after a
recovery reversal. Tool-restock egress uses the same movement context. Teardown
does not use a spatial nearby breaker.

Active-U repair uses explicit per-position ownership and may batch multiple safe
true-instant repairs in the current U; ordered teardown owns exactly one route
target at a time. Meteor Speed Mine is snapshotted, configured only while Nerv
owns the applicable target, and restored exactly. `BlockUtils.canInstaBreak` is the only batch-instant
classification; Speed Mine's damage acceleration is treated as progressive work.
A break is complete only after a newer authoritative server observation reports
air. Ordered teardown does not turn that acknowledgement interval into a movement
barrier for true-instant or Speed-Mine-accelerated work: it keeps servicing the
same owned, in-reach target while moving through verified route supports. There
is no one-support acknowledgement boundary and multiple targets assigned to one
reach window do not create a checkpoint hold. The single teardown action owner
still prevents acquiring a second progressive target before authoritative air.
Every dispatch rechecks the actual grounded support and the current/future route
suffix; an underfoot target is deferred until it is behind the player, while a
target required by a future route cell is rejected. Ordinary slow progressive
mining without the owned THM Speed Mine lease continues to hold movement.
Movement overlap is also bounded by the next ordered support's conservative
reach window, so sprinting cannot outrun an acknowledged progressive target.
Unexpected live reach loss enters stable-ground local U recovery instead of
discarding the shared route and immediately starting endpoint navigation.
If remote teardown is interrupted after removing a neighboring U prefix, its
continuous remaining suffix stays eligible for reassignment to the next U with
a complete monotonic reach proof. It is forced into an opposite-end traversal
only when no other selected route can safely finish the remainder; the U under
the recovering player always retains mandatory local ownership.

Managed hotbar swaps, chest restocks, dump throws, and used-tool deposits are
bounded server-confirmed transactions. Teardown restocking counts usable worn tools
already carried but accepts only fresh, fully compatible chest tools for newly
missing worst-case durability. It also freezes a configurable two- or three-stack
cobblestone reserve (default three), merges refill into carried partial stacks
before charging new inventory slots, and confirms that reserve at every teardown
entry.

The final teardown verifier reads every target and connector directly from the
authoritative world cache; completion reports do not suppress this scan. Sparse
owned leftovers enter a dedicated ordered scaffold sortie rather than an unordered
nearby breaker. The planner compares both safe north endpoints, uses only the
closest unobstructed half of the U, direct-air-places the minimum cobblestone path
under the shared TPS-scaled budget, and waits for newer server confirmation before
walking onto each support. The terminal missed block is mined from the preceding
support. The shared THM teardown owner then clears missed blocks and scaffold behind
the returning player. A restart on that path is reconstructed from solid owned
supports and clear headroom: complete inventory resumes locally, while an inventory
shortfall produces a no-break egress over the intact path before restock.

Nerv now gives this transaction layer an explicit phase layout instead of
copying THM's opportunistic replacement scan. Circular printing plans against
the full managed inventory. Its first eight stack units are frozen into
material hotbar roles and the remaining planned stacks stay in main inventory
until their precomputed next use causes a server-confirmed silent swap. The
ninth hotbar slot is reserved for the active repair pickaxe or axe. Entry
preparation uses server-confirmed inventory `SWAP` packets without changing the
visible selected slot; the material or tool is selected only when its action is
dispatched. Pickaxe/axe changes target only the reserved tool slot, returning
the previous tool to the known source slot. Teardown independently freezes two
pickaxe assignments and one axe assignment after worst-case restocking. An
exhausted teardown tool can be replaced only in its preassigned same-item slot,
so neither phase has an arbitrary hotbar-eviction fallback.

Circular inventory lookahead has no fixed four-column cap. It reserves every
still-outstanding block in the active U and its required repair tools first,
then offers every missing target in later assigned U routes to the capacity
planner in exact outbound, connector, and return order. This explicitly
includes the cobblestone connector targets. Optional admission stops only when
the managed inventory is full or the assigned interval ends. A future target
that is already correct consumes no optional capacity; a wrong future target
is left for repair when its U becomes active.

At runtime, every planned missing surface or connector target that enters the
five-block reach is eligible for leftover placement budget after reachable
active-U work. Server-confirmed forward placements are remembered across pair
replans and deducted from the later U's strict restock demand while the newest
authoritative observation still matches. The inventory-plan log reports
occupied/usable slots, unused capacity, admitted/candidate forward targets, and
the number of included connectors.

At every safe north endpoint, Nerv now rebuilds the remaining traversal set
from the newest authoritative blocks. A completed upcoming U is removed from
the checkpoint queue. An incomplete U containing only missing air may also be
deferred to a later selected U, but only when every one of its remaining
surface and connector targets has a conservative support-center reach window
on that later ordered path and the combined active-plus-deferred material/tool
demand still fits the managed inventory. One unreachable target, a capacity
shortfall, or any wrong block keeps the U's normal traversal and repair
ownership.

Deferred targets are promoted from optional surplus to mandatory inventory and
placement demand for their destination U. The bot may place them whenever they
enter reach, and it holds at their final proven reach opportunity until the
server confirms the expected block. If a deferred target changes to a wrong
block, the bot returns through ordered recovery and replans that original U
instead of widening repair ownership.

Departure checks the complete frozen plan rather than only the active-U
reserve. If any active, deferred, repair-tool, or admitted forward amount is
missing, the dump/restock route is prepended while the bot is still at the safe
north endpoint. Hotbar preparation performs the same authoritative
precondition check; inventory drift schedules that complete restock transaction
instead of stopping with a missing managed-main-slot error.

## Contents

- [Important corrections before implementation](#important-corrections-before-implementation)
- [What "without looking" actually means](#what-without-looking-actually-means)
- [HighwayBuilderTHM](#highwaybuilderthm)
- [TunnelMinerModule](#tunnelminermodule)
- [Useful project-wide components](#useful-project-wide-components)
- [Recommended Nerv architecture](#recommended-nerv-architecture)
- [Verification checklist](#verification-checklist-for-the-future-implementation)

## Important corrections before implementation

### `30` is not a fixed 30 BPS limit

HighwayBuilder's setting is named `blocks-per-tick`, is a `double`, and has a range
of `1` through `30`. Its description says that it is the maximum number of mined
blocks **per client tick**, with fractional values allowed, and that it applies to
instamineable blocks (`HighwayBuilderTHM.java:850-857`).

Therefore:

- `30` means up to 30 scheduled mining action credits in one client tick.
- It does not mean a hard-coded 30 blocks per real second.
- At 20 client ticks/second, the setting alone represents a theoretical ceiling of
  600 direct action attempts/second, not 30.
- The actual confirmed BPS depends on client ticks, server TPS, reach, block
  hardness, packet acceptance, inventory swaps, world updates, and the number of
  valid targets.
- THM's optional TPS throttle can reduce the per-tick action rate.
- Packet Borer has a separate packet cap and is not bounded solely by this setting.

An observed result near 30 BPS may be a server/world outcome, but it is not the
meaning of the `30` setting.

### TPS scaling is optional and belongs to HighwayBuilder only

HighwayBuilder's `pause-on-lag` setting defaults to `false`
(`HighwayBuilderTHM.java:680-684`). If disabled, configured mining and placement
rates are used directly.

TunnelMiner does not read `TickRate` and has no equivalent TPS-rate controller.
Its live limits are integer `breaks-per-tick` and `places-per-tick` settings.

Even when HighwayBuilder's throttle is enabled, proportional TPS scaling applies
only to its normal mine/place budgets. Packet Borer, two-track mining concurrency,
Packet Build's unlimited placement budget, and walking speed are not
proportionally reduced. They only stop at the global low/invalid-TPS pause.

### "Instamine" does not identify one THM subsystem

The checked project contains several similarly named but independent paths:

- **HighwayBuilder's own instamine classification and scheduler.** It calls
  `BlockUtils.canInstaBreak`, adds a server-specific basalt/blackstone override,
  and owns the ordered highway mine queue (`HighwayBuilderTHM.java:6608-6642`,
  `12008-12101`).
- **Meteor `SpeedMine`.** This is the external
  `meteordevelopment.meteorclient.systems.modules.player.SpeedMine` class imported
  by HighwayBuilder. Autosetup enables/configures it, and HighwayBuilder consults
  its `instamine()` result while deciding whether a block belongs in the slow
  two-track miner (`4839-5037`, `12018-12029`).
- **THM `Speedmine`.** The lowercase `m` identifies THM's local
  `xyz.thm.addon.modules.Speedmine` packet miner. It is not the normal forward
  highway scheduler. HighwayBuilder uses it only in the `MineEnderChests` restock
  state when `new-breaking=true` (`13247-13277`, `13367-13392`).
- **THM `Nuker`.** This is a standalone spatial block scanner and breaker. Neither
  HighwayBuilder nor TunnelMiner calls or enables it.
- **Meteor `InstantRebreak`.** THM does not implement this class. HighwayBuilder
  only warns if the external module is active because HighwayBuilder already
  manages its own ender-chest rebreak cycle (`2148-2149`).

These names must remain distinct in any Nerv design. In particular, copying
Nuker's packet-mine switch would not reproduce HighwayBuilder's confirmed,
geometry-aware rolling scheduler.

### "Print and break at the same time" is conditional

In HighwayBuilder's default rolling scheduler, the same `TickEvent.Pre` can run:

1. mining work,
2. placement work,
3. conflict placement work, and
4. movement.

However:

- a placement at a position owned by a mine task is deferred;
- Packet Build skips the regular placement queue/lookahead while the active row
  still has mine tasks, to avoid the packet limiter dropping selected-slot or
  mining packets;
- conflict placement still runs after that conditional barrier, and if direct
  mining empties the queue immediately, regular packet placement can run in the
  same tick;
- timers, action budgets, reach, slow mining, safety placement, and missing items
  can prevent overlap.

TunnelMiner can mine and restore/place during one live tick, but close mining,
lava, placement delay, inventory delay, restore lag, and support-floor gates can
serialize those actions.

### Current TunnelMiner does not dispatch its old MINE/WALK/FILL methods

`onTick` routes all three enum phases `MINE`, `WALK`, and `FILL` to the unified
`stealthTick()` pipeline (`TunnelMinerModule.java:1953-1960`). The methods
`minePhase()`, `walkPhase()`, and `fillPhase()` remain in the class, but have no
live call sites at this revision.

Those old methods must not be used as the reference for current runtime behavior.

## What "without looking" actually means

THM combines several independent techniques:

1. **Explicit targets instead of crosshair selection.** Mining functions receive a
   `BlockPos`. Placement constructs a `BlockHitResult` for a calculated support
   face. The visible crosshair does not choose the block.
2. **Optional or absent server rotation.** Most live forward work runs with
   rotations disabled. Rotation is only sent when the selected branch explicitly
   calls `Rotations.rotate`.
3. **Silent/server-side hotbar changes.** A tool or block can be made active for
   the server while the client-visible selected slot is restored.
4. **Camera decoupling.** HighwayBuilder owns the player movement yaw but its
   camera/entity mixins let the user move the camera independently. TunnelMiner
   optionally enables Meteor FreeLook.

These mechanisms should remain separate in Nerv. A no-look block action does not
require the movement controller, camera, and inventory to share one mutable
"current target."

---

# HighwayBuilderTHM

## Defaults relevant to throughput

| Setting | Default | Source meaning |
| --- | ---: | --- |
| `legacy-mode` | `false` | Use the rolling row scheduler (`563-567`). |
| `rotation` | `None` | Do not rotate for mining or placement (`530-534`, enum at `233-245`). |
| `use-thm-speed` | `false` | Custom velocity/drift controller is opt-in (`545-550`). |
| `autosetup-modules` | `true` | Mutate Speed Mine, Reach, Velocity, and effective range (`742-750`, `4839-4933`). |
| `double-mine` | `true` | Maintain normal and packet mining tracks (`773-782`). |
| `fast-break` | `true` | Finish double-mine progress at `0.7` instead of `1.0` (`785-790`). |
| `break-delay` | `0` | No inter-action delay (`842-847`). |
| `blocks-per-tick` | `7.0` | Fractional instant-mine action rate, max setting `30` (`850-857`). |
| `packet-borer` | `false` | Separate whole-shape START/STOP spam (`886-890`). |
| `place-range` | `4.5` | Scheduler distance gate (`903-908`). |
| `place-delay` | `0` | No placement delay (`911-916`). |
| `packet-build` | `false` | Direct placement packets are opt-in (`926-930`). |
| `packet-build-lookahead` | `true` | Packet mode may process future rows (`941-946`). |
| `silent-forward-place-swap` | `true` | Restore visible slot after normal forward placement (`949-955`). |
| `silent-forward-tool-swap` | `true` | Restore visible slot for eligible instant mining (`957-962`). |
| `placements-per-tick` | `1.0` | Fractional placement action rate (`1011-1018`). |
| `pause-on-lag` | `false` | TPS throttle is opt-in (`680-684`). |

`packet-mode` is a convenience setting that enables Packet Build and Packet Borer
(`756-761`, activation helper at `11177-11180`).

## Activation and ownership

`onActivate()` begins at `HighwayBuilderTHM.java:2030`.

Important activation operations:

- Packet Build enables `PaketLimiter` if it is not already active (`2042-2045`).
- Working direction, left/right directions, and the straight/diagonal
  `BlockPosProvider` are resolved (`2070-2084`).
- Forward statistics and the rolling scheduler are reset and seeded (`2085-2092`).
- The initial runtime state is `Forward`, then normal activation routes through
  `Center` (`2091-2115`).
- TPS runtime and fractional action carries are reset (`2127-2133`).
- Warnings explain that rotation reduces multi-action throughput to one action per
  tick (`2141-2146`). These warnings describe the intended behavior, but they are
  inaccurate for the live rolling paths because those paths bypass or miswire the
  rotation setting; see "Rotation defects" below.
- HighwayBuilder enables `HotbarManager` when configured and validates its reserved
  items (`2177-2178`).
- It saves the previous player `Input`, then replaces `mc.player.input` with its
  `CustomPlayerInput` (`4802-4805`).

On deactivation it restores the prior `Input` (`2272`). It also toggles
`HotbarManager` off when the HighwayBuilder hotbar-manager setting is enabled
(`2278`).

Porting warning: HighwayBuilder does not retain a reliable "I enabled this module"
ownership flag for HotbarManager. If HotbarManager was already active before
HighwayBuilder, this deactivation branch can still turn it off. Nerv should use an
explicit ownership lease and restore only state it changed.

## Live tick order

The important dispatcher is `onTick(TickEvent.Pre)` at
`HighwayBuilderTHM.java:4199`.

After world/state, eating, safety, and pause checks, each normal tick does this:

1. Update optional TPS action throttling (`4245`).
2. If TPS handling owns the tick, stop or perform its safety behavior
   (`4252-4255`).
3. Reset the general action count and the separate forward mine/place counters
   (`4312-4314`).
4. Convert fractional rates into this tick's integer mine and place budgets
   (`4315-4316`).
5. Prune confirmation/stat-credit state (`4317-4318`).
6. Tick Packet Borer (`4326`).
7. Tick the two active double-mine tracks (`4327`).
8. Queue restock work and run restock watchdog logic (`4328-4347`).
9. Tick the current state, normally `Forward` (`4345`).
10. Decrement break/place timers (`4350-4351`).

This ordering explains the precision:

- active slow mining advances before new row work;
- the scheduler gets fresh per-tick budgets;
- mining and placement have independent counters;
- movement is applied after target work in the forward scheduler;
- timers are deterministic tick state, not sleeps.

## TPS action budget

Constants are at `HighwayBuilderTHM.java:201-207`:

- normal TPS: `20.0`;
- pause threshold: `10.0`;
- healthy sampling interval: `40` player ticks;
- startup/reenable settle period: `5,000 ms`;
- stale server tick threshold: `1.5 seconds`.

`updateTpsActionThrottle()` is at `5681-5774`.

When `pause-on-lag` is disabled:

```text
effectiveMineRate  = sanitize(configured blocks-per-tick)
effectivePlaceRate = sanitize(configured placements-per-tick)
```

When enabled and TPS is valid:

```text
clampedTPS = clamp(sampledTPS, 10, 20)
multiplier = clampedTPS / 20

effectiveMineRate  = roundToNearestTenth(configuredMineRate  * multiplier)
effectivePlaceRate = roundToNearestTenth(configuredPlaceRate * multiplier)
```

Examples:

| Configuration | Sampled TPS | Effective rate |
| --- | ---: | ---: |
| mine `7.0` | `20` | `7.0` actions/tick |
| mine `7.0` | `15` | `5.3` actions/tick after tenth rounding |
| place `1.0` | `15` | `0.8` actions/tick after tenth rounding |
| mine/place any | below `10` | paused |

It also pauses during settling, invalid/non-positive TPS, or a stale last-server-tick
sample (`5776-5784`). A pause zeroes both budgets, releases double-mining state,
and stops movement (`5823-5837`).

Healthy samples are normally retained for up to 40 client ticks. Paused or abnormal
states force more immediate sampling (`5708-5718`).

The pause is not necessarily packet-silent. With TPS safety enclosure enabled, the
safety path runs before the ordinary zero-budget pause handler and can send
air-placement packets during a low/unknown-TPS pause (`4252-4255`,
`5880-6118`).

### Fractional carry

The budget functions are at `6130-6180`.

They preserve the fractional remainder:

```text
available = effectiveRate + previousCarry
integerBudget = floor(available)
newCarry = available - integerBudget
```

Thus a rate of `0.8` yields, over time, approximately four one-action ticks for
every five client ticks instead of rounding permanently to either zero or one.

Mining is sanitized to at least `1.0` when active (`6138-6141`). Placement is
sanitized to at least `0.1` (`6143-6146`).

Packet Build is a special case: `computePlaceActionsThisTick()` returns
`Integer.MAX_VALUE` (`6167-6169`). The real bounds then become target count, reach,
support, inventory, timers, packet limiter behavior, and world/queue state. This is
not a safe default to copy into Nerv. It also means Packet Build ignores both
`placements-per-tick` and its TPS-scaled placement rate.

## Rolling scheduler

The rolling scheduler is the live default because `legacy-mode=false`.

### Window construction

`seedForwardSchedulerWindow()` creates five rows
(`HighwayBuilderTHM.java:11534-11550`; constant at `12262-12265`).

The first row is generated from the current `BlockPosProvider`; later rows are
translations of the prior row by the working direction (`11613-11620`).

The row contains typed tasks for:

- front mining;
- optional behind-front repair mining;
- floor mining/replacement;
- railing mining;
- mining above railings;
- liquid filling;
- corner placement;
- railing placement;
- floor placement;
- matching "behind" repair variants.

Task collection is at `11581-11610`, and the task enum is at `12178-12222`.

### Stable task ordering

`buildForwardOrderedQueue()` (`11640-11664`) sorts tasks:

1. nearest to the lateral center of the row;
2. lateral coordinate;
3. task type;
4. Y;
5. X;
6. Z.

It stores them in a `LinkedHashMap<BlockPos, ForwardTask>`, giving deterministic
ordering and one task per position per queue.

### Refresh and conflict ownership

The active row is refreshed from live world state every tick
(`11725-11790`):

- satisfied mine/place tasks are removed;
- newly observed relevant tasks are prepended in deterministic order;
- a placement whose position is still in the mine queue or owned by active
  double-mining is moved to `conflictQueue`;
- a conflict stays deferred until mining no longer owns that position.

This is the key rule that permits mine/place overlap without placing into a block
that is still being broken.

### Scheduler tick

`tickForwardScheduler()` is at `11937-12006`:

1. Handle special crystal-trap state.
2. Maintain the five-row window.
3. If recovering from a stall, backstep instead of doing normal work.
4. Refresh the active row.
5. Run mine work.
6. Run placement work unless Packet Build currently has active-row mine work or a
   safety placement already used the tick.
7. In Packet Build, optionally run placement for later look-ahead rows.
8. Run conflict placement.
9. Mark progress.
10. Start recovery if no progress has occurred for 30 seconds.
11. Apply forward/hold movement.

The comment at `11959-11961` is especially important: Packet Build deliberately
does not place while the active row's mine queue is non-empty, because selected-slot
and mining packets might otherwise be cancelled by `PaketLimiter`. More exactly,
this barrier covers the normal placement queue and lookahead only. Conflict work at
`11980` still runs. If direct mining empties the active mine queue during the same
tick, normal placement is no longer barred.

### Queue-refresh/lookahead quirk

Rows retain shifted templates, but `refreshForwardActiveRow()` also prepends the
provider's current live tasks (`11741-11749`). Row creation itself calls refresh
(`11623-11628`). Future rows can therefore contain shifted future positions plus
current positions.

In Packet Build lookahead, a raw placement may not update the client world
immediately. The same current target can then be attempted from multiple rows
during one tick. Nerv's route-window design must keep every route position owned by
exactly one window entry.

## Mining

### Direct no-look mining

`tryMineBlock()` (`6230-6253`) receives an explicit `BlockPos`, checks configured
range and `canBreak`, then calls `BlockUtils.breakBlock(pos, true)`.

If its `rotate` argument is true, it wraps the action in `Rotations.rotate`;
otherwise it acts directly on the supplied position. The rolling scheduler calls
it with `rotate=false` (`12079`). Therefore default rolling forward mining does not
need the camera/crosshair to face each target.

The helper uses the configured `place-range` for mining too
(`6226-6228`, `6235`). That is a source quirk. Nerv should use the player's actual
block interaction range, with any explicitly configured safe cap, rather than
reusing a placement setting.

### Rotation defects in the rolling path

The live default scheduler does not honor the rotation enum consistently:

- rolling direct mining hard-codes `rotate=false` (`12079`);
- rolling normal placement has no rotation branch (`6351-6397`);
- rolling packet placement checks `rotation.mine`, not `rotation.place`
  (`6400-6424`);
- double mine and Packet Borer send raw dig packets without rotation.

Consequences:

- `Rotation.Place` does not rotate normal forward placement;
- `Rotation.Mine` can unexpectedly rotate packet **placement**;
- the activation warnings that rotation restricts rolling throughput to one
  action/tick do not describe the live rolling implementation.

These are source defects, not behavior to reproduce.

### Direct queue behavior

`runForwardMineWork()` is at `12008-12101`.

For direct work it:

- stops at the per-tick mine budget or `breakTimer`;
- rechecks whether every task still needs mining;
- charges a task's `mineBudgetCost`;
- resolves a latched best-tool hotbar slot;
- permits multiple actions only for instant-break targets;
- swaps the tool;
- issues the break;
- restores a silent swap in `finally`;
- removes the task only after the local world state changes.

A non-instamine target causes the direct loop to stop after that action
(`12064`, `12097`). This prevents many slow-start actions from being treated like
confirmed instant breaks.

Default `instamine-bypass=false` manually treats basalt, smooth/polished basalt,
blackstone, and gilded blackstone as multi-break instant candidates
(`6616-6642`). That is server-specific and can desynchronize on a server that does
not accept those assumptions.

The hidden `instamine-override-blocks-per-tick` setting (`859-866`) has no live read
site. `effectiveInstamineOverrideBlocksPerTick()` mirrors the normal effective
rate, so `mineBudgetCost()` is effectively one in the current code. Do not design
Nerv around that hidden setting.

### Tool slot latch

The scheduler caches `forwardLatchedMineSlot` (`6318-6348`). Before reuse it checks:

- valid hotbar index;
- non-empty stack;
- Silk Touch exclusion when requested;
- durability guard;
- pickaxe reserve/restock policy;
- positive `AutoTool` score for the current block state.

The latch avoids full-inventory scanning and moving an item on every target. It is
invalidated when the stack is no longer suitable.

### Silent versus visible tool swap

`canUseSilentForwardToolSwap()` (`6645-6653`) permits a silent tool swap only when:

- silent tool swapping is enabled;
- the slot is valid;
- the target is not handled by the special basalt/blackstone override; and
- `BlockUtils.getBreakDelta(slot, state) >= 1.0`.

The direct loop then uses:

```text
silent eligible:
    InvUtils.swap(toolSlot, true)
    break
    InvUtils.swapBack() in finally

slow/not eligible:
    InvUtils.swap(toolSlot, false)
    keep it selected
```

Slow and double mining keep the tool selected because mining progress is calculated
from the currently selected slot. Restoring too early would change the server-side
tool and invalidate the predicted progress.

### Double mine

Double mine does not mean "mine and place." It means two overlapping slow mining
tracks:

- `normalMining`: the block currently progressing normally;
- `packetMining`: the preceding block retained as a packet/STOP track.

The scheduler first collects non-instamine, breakable candidates that are not
already owned (`12012-12029`). `State.Forward.doubleMine()`:

1. sends START for the first normal block;
2. when a second candidate is available, sends STOP for the old normal block and
   turns it into `packetMining`;
3. sends START for the new normal block.

The transition is at `16360-16383`. The packet class is at `18115-18173`.

Progress is:

```text
BlockUtils.getBreakDelta(currentSelectedSlot, originalBlockState)
    * (elapsedPlayerAges + 1)
```

With `fast-break=true`, STOP is considered ready at `0.7`; otherwise `1.0`
(`18156-18158`). A normal track is discarded if out of interaction range. Either
track times out after progress is already above `2` and more than 60 player ages
have passed (`18160-18167`).

`tickDoubleMine()` advances existing tracks before the scheduler creates more work
(`11222-11265`, called at `4327`).

Starting slow tracks is not debited from `forwardMineCount`; confirmations are.
Thus even `blocks-per-tick=1` can start two overlapping slow tracks. While either
track remains active, the direct-instamine loop for that row returns early
(`12039-12044`).

Active double mining is ticked globally before the current state. It is not
automatically released just because Forward transitions into restock or a trap
state. Those states can change the selected item while progress still uses the
currently selected slot.

This makes default silent placement important: it temporarily selects a block and
returns to the pickaxe. Disabling silent placement can leave a block selected and
make double-mine progress use the wrong item.

### Packet Borer

Packet Borer is separate from normal action budgeting
(`11182-11220`):

- scans a hard-coded six-column shape around the player rather than the configured
  scheduler width;
- for eligible blocks, sends START followed immediately by STOP;
- has an internal cap of 130 action packets, or at most 65 START/STOP pairs, per
  client tick;
- runs before current-state work and is not restricted to Forward;
- ignores normal break delay, BPT, scheduler ownership, and statistics.

Actual sent packets may be lower when `PaketLimiter` cancels them. Packet Borer
must not be interpreted as "130 confirmed blocks" or as a safe generic miner.

### Confirmation and statistics

The scheduler issues an action, then compares world state before and after. It
removes normal mine work only when the block changed (`12086-12095`).

THM also maintains forward break credits and counted-position sets to avoid
double-counting later confirmations. This distinction should be retained in Nerv:

- **action sent**;
- **client world changed**;
- **server-confirmed/accepted state**;
- **statistics counted**.

They must not be represented by one boolean.

## Placement

### Normal no-look forward placement

`tryForwardPlaceBlock()` is at `6351-6397`.

It:

1. verifies player/world, range, hotbar slot, `BlockItem`, and placeability;
2. asks `BlockUtils.getPlaceSide(pos)` for a support direction;
3. constructs the neighbor and exact face hit position;
4. silently or visibly selects the block;
5. calls `BlockUtils.interact` with the constructed `BlockHitResult`;
6. restores a silent swap in `finally`;
7. updates the placement timer and render trail.

The explicit `BlockHitResult` selects the target independently of the crosshair.
This live rolling path does not wrap the normal interaction in
`Rotations.rotate`.

If no support side is found, it falls back to an UP-facing hit at the target
itself (`6368-6373`). Whether the server accepts this depends on the world/server
placement rules.

### Packet Build placement

`tryForwardPlaceBlockPacket()` is at `6405-6433`, and the shared sender is
`PacketPlaceUtils.java:73-115`.

Packet placement:

- resolves a support side when possible;
- constructs a `PlayerInteractBlockC2SPacket`;
- uses the hard-coded interaction sequence value `0`;
- optionally treats the target itself as the neighbor for air placement;
- can silently swap and swap back, or keep the selected placement slot to reduce
  `UpdateSelectedSlot` packets;
- also creates a hand-swing packet, which the default packet limiter cancels.

The helper returns `true` after dispatching or scheduling the action, not after
world/server confirmation. If rotation is enabled, the rotation callback may run
later even though the caller has already counted the helper's return value as a
placement action.

Air-place modes:

- `Never`: require a support side;
- `Smart`: use support placement when possible, otherwise air-place;
- `Always`: permit air-place for every target.

Important source quirk: Packet Build passes `getRotateForMine()`, which tests
`rotation.mine`, to the placement helper (`6416-6423`). It does not test
`rotation.place`. This appears inconsistent and should not be copied.

Another mismatch exists around swapping. `PacketPlaceUtils` documents
`swapBack=false` as the packet-build optimization that keeps the placement slot
selected and avoids repeated selected-slot packets. HighwayBuilder instead passes
`silentForwardPlaceSwap`, whose default is `true`, as `swapBack` (`6409-6424`).
The default Packet Build path can therefore swap to the block and back for every
placement, consuming extra packet budget.

### Placement queue and confirmation

`runForwardPlaceQueue()` is at `12129-12175`.

It:

- sorts the current snapshot by eye distance;
- enforces the placement budget and timer;
- skips mine-owned/conflicting positions;
- enforces range;
- resolves the latched placement slot;
- sends the action;
- revalidates the slot;
- checks whether the world changed.

In Packet Build, a successfully dispatched packet consumes a placement action even
if the immediate local world cache has not changed (`12154-12163`). Queue removal
still depends on the live "keep task" predicate or an observed state change.

Only one conflict placement is attempted per pass (`12171-12172`), even when the
general placement budget is higher.

### Place slot latch

`forwardLatchedPlaceSlot` is reused while it still contains:

- any valid forward placement block for normal tasks; or
- a droppable trash `BlockItem` for liquid/corner tasks that prioritize trash.

Resolution and revalidation are at `6281-6305`. This avoids repeated inventory
movement during a continuous lane.

## Same-tick mining and placement

For the normal rolling scheduler, `runForwardMineWork()` and
`runForwardPlaceWork()` are separate calls with separate action counters. This is
the actual basis for same-tick break/place behavior.

Its intended conflict safety comes from:

- mine work runs first;
- each queue is refreshed against the live world;
- the same `BlockPos` cannot be independently owned by mine and place work;
- conflict placement waits for active normal/packet mining to release the position;
- movement happens after actions.

Overlap is not guaranteed because:

- Packet Build blocks the regular queue/lookahead while active-row mine work
  remains pending, although conflict work still runs;
- a slow mine holds the tool selected and can block direct work;
- timers may stop either side;
- missing inventory can transition into restock;
- world confirmation can keep a task in its queue;
- TPS or safety logic can own the entire tick.

## Precise walking

### Directional projections

The scheduler converts positions into forward and lateral scalar projections
(`11693-11722`). This works for straight and diagonal working directions without
using a fragile "compare only X" or "compare only Z" rule.

The active row computes a front boundary, normally the center of its non-behind
targets minus `1.5` projected blocks (`11667-11690`).

### Move/hold rule

`applyForwardSchedulerMovement()` (`11923-11934`) sets movement yaw and pitch, then:

- walks forward while the player has not crossed the active row boundary;
- holds position when the boundary is reached but the row is incomplete;
- keeps moving when there is no active work.

The bot therefore does not blindly walk and hope placement catches up.

### Lateral drift correction

The lane's target lateral projection is captured when entering forward state.
Correction starts at `0.125` blocks of lateral error and stops at `0.03`
(`11875-11920`, constants at `191-192`).

This hysteresis prevents a left/right control from oscillating at one threshold.
However, the correction function exits unless THM's speed controller is active,
and `use-thm-speed` defaults to `false` (`545-550`). It is therefore an opt-in
behavior, not the default movement path.

### Controlled movement speed

When THM speed ownership is active, `onPlayerMove` replaces horizontal movement
with a vector derived from movement input and yaw (`4354-4389`):

- configured forward speed is converted from blocks/second to blocks/tick;
- lateral correction uses its own fixed per-tick speed;
- vertical velocity is preserved;
- zero input zeroes horizontal movement.

### Centering

The `Center` state supports teleport and walk modes (`12285-12386`).

Walk centering:

- calculates distance from `.5` block centers independently on X and Z;
- applies directional input only on axes outside a `0.1` tolerance;
- sneaks when THM speed is not active;
- stops velocity and snaps exactly to the centered coordinates once within
  tolerance.

Teleport centering has blocking detection and a timeout rather than repeatedly
teleporting forever.

The default is named Teleport, but it is not a general long-range teleport. The
implementation first approaches a safe target center, snaps only when close, and
rejects targets more than four blocks away. It should not be used as a connector
travel primitive.

### Stall recovery

The rolling scheduler waits 30 seconds without progress, then:

- stops normal work;
- turns to the reverse working direction;
- walks back two projected blocks;
- reseeds the five-row window;
- transitions through centering.

The code declares a two-retry maximum, but the successful backstep calls
`seedForwardSchedulerWindow()`, whose reset path clears `recoveryRetries` to zero.
Repeated successful recovery cycles therefore never reach that maximum
(`11528-11535`, `11850-11872`, `12274-12280`).

There is also no timeout while `backstepping=true`: the scheduler calls recovery
and returns until it measures two blocks of reverse progress. If an obstruction
prevents reverse movement, HighwayBuilder can push against it indefinitely.
Both defects must be fixed rather than copied.

### Autosetup side effects

Default `autosetup-modules=true` configures Speed Mine, Reach, and Velocity and
changes `placeRange` to `5.4` (`4839-4933`). Because the same setting is used for
mine checks, the effective default activation range is usually 5.4 for both
actions, not the declared 4.5.

The default autosetup path does not consistently preserve ownership/snapshots for
every mutated external setting. Some Speed Mine/Reach/Velocity changes can remain
after deactivation. Nerv must use complete snapshots and ownership leases.

### Camera ownership

HighwayBuilder sets the real player yaw/pitch for deterministic movement. Two
mixins keep mouse view independent:

- `HighwayBuilderEntityMixin` intercepts the player's look input and applies it to
  the render camera instead of changing the controlled entity rotation.
- `HighwayBuilderCameraMixin` preserves that camera rotation while HighwayBuilder
  or its monitor owns integrated freelook.

Both yield to Meteor FreeLook or Freecam if either is active. They do not increase
mining or placement speed; they only separate the user's view from automation yaw.

`HighwayBuilderBowMixin` is unrelated to throughput. It prevents normal input
handling from releasing a bow while HighwayBuilder is deliberately drawing it.

## Hotbar and inventory handling

### HotbarManager's layout

`HotbarManager.java:63-74` defaults to:

| Client slot | Item |
| ---: | --- |
| 1-4 | unmanaged/air |
| 5 | enchanted golden apple |
| 6 | netherrack |
| 7 | ender chest |
| 8 | netherite pickaxe |
| 9 | obsidian |

On `TickEvent.Post`, it examines configured slots in order. If a target item is
missing, it finds that item in inventory slots `i..35` and calls
`InvUtils.move().from(result.slot()).to(i)` (`HotbarManager.java:108-130`).

After a move it sets `ticksLeft=delay`. Because the next loop iteration sees
`ticksLeft>0`, at most one move occurs before returning. The countdown subtracts
`serverTPS / 20` per client tick (`111`), so lower TPS makes replenishment occur
more slowly in client-tick terms.

`replace=false` preserves any non-air stack already in a managed slot.

Its search starts at the destination index `i` and ends at 35
(`HotbarManager.java:125`). An item already present in a lower-index hotbar slot is
therefore ignored when filling a higher managed slot. This is a simple exact-item
maintainer, not an optimal whole-hotbar arrangement algorithm.

### HighwayBuilder reservations

HighwayBuilder treats non-air HotbarManager targets as reserved
(`10280-10294`). It:

- avoids using them as generic replacement/trash slots;
- prefers the exact managed slot when moving that managed item into the hotbar;
- validates configured reserves at startup (`10343-10378`).

This is why it "knows how to arrange the hotbar": it has a persistent desired
layout plus per-action slot selection. The high-rate loop itself is not constantly
sorting all nine slots.

### Selecting a replacement hotbar slot

`State.findHotbarSlot()` is at `16548-16610`. It scans slots 0 through 8:

1. skip HotbarManager-reserved slots;
2. return immediately for an empty slot;
3. return immediately for a tool when tool replacement is allowed;
4. remember the latest droppable trash slot;
5. track the least-filled placement-block slot;
6. after the scan, return trash, then the least-filled block stack, then the first
   unreserved occupied slot.

This is scan-order dependent, not a global priority search. An early tool can be
returned before a later empty slot is examined. With tool replacement disabled,
the final occupied fallback can still overwrite a tool. The code comment says it
uses a building slot only when there is "more than 1," but the condition is
`slotsWithBlocks > 0`.

### Moving main inventory to hotbar

`findAndMoveToHotbar()` (`16623-16706`) does:

1. return an existing matching hotbar slot;
2. recover a matching cursor-held stack into hotbar if necessary;
3. search the main inventory;
4. choose an exact managed slot or a replacement slot;
5. call `InvUtils.move().from(inventorySlot).toHotbar(hotbarSlot)`;
6. clear any residual cursor stack to an empty slot or use a guarded safe-drop
   fallback.

This is a persistent inventory move, unlike `InvUtils.swap(slot, true)`, which is a
temporary selected-slot operation.

The method does not verify that `InvUtils.move` actually produced the desired
hotbar state before returning the selected index, and forward resolution has no
inventory acknowledgement delay. Multiple moves can be attempted during one Pre
tick. Nerv should validate the destination before issuing the dependent block
action.

### Best tool selection

`findAndMoveBestToolToHotbar()` is at `16744-16816`.

It scores every main-inventory stack using Meteor `AutoTool.getScore`, with:

- optional Silk Touch rejection;
- durability threshold rejection;
- pickaxe reserve/restock checks;
- exact managed-slot preference;
- fallback replacement-slot selection.

This is materially better than selecting only by item type or durability.

A latched stack needs only remain valid with an `AutoTool` score above zero; it is
not rescored against every other inventory tool on each use. That is the intended
performance tradeoff.

### Container-to-player transfers

HighwayBuilder's restock state begins at `14296`. It is a large transactional state
machine, not a direct "open chest and grab everything" loop.

It can source materials, pickaxes, food, and ender chests from:

1. matching shulkers already in player inventory;
2. loose items in the ender chest;
3. matching shulkers stored in the ender chest;
4. raw ender chests that can be mined into obsidian;
5. optional KitBot fallback.

It establishes/validates a restock blockade, centers, frees space, selects one
source, places/opens it, moves one useful item per delayed inventory action,
breaks/picks up the source, and either advances the queued task or tears down the
blockade.

The generic extraction path is at `15163-15217`:

- use task-specific predicates;
- prefer a selected food or loose-ender-chest slot;
- otherwise scan container slots;
- call `InvUtils.shiftClick().slotId(containerSlot)`;
- verify progress by comparing source item/count before and after;
- apply `inventoryDelay`;
- handle unexpected cursor contents.

Cursor recovery first tries a player-inventory empty slot
(`15219-15249`). Only after closing the screen and protecting useful stacks can a
drop fallback be attempted. This is important for disconnect/desync resilience.

For moving a selected shulker from player inventory to hotbar, it uses
`InvUtils.move().from(inventorySlot).toHotbar(hotbarSlot)` and then performs cursor
recovery (`15301-15317`).

Some extracted food/ender-chest shulkers are returned to the ender chest after use.
The return path:

- tracks the likely player inventory slot;
- reopens/places the ender chest;
- issues `SlotActionType.QUICK_MOVE` on the player menu slot;
- verifies that the stack moved or changed;
- waits 40 ticks and retries once;
- if still unsuccessful, keeps the shulker safely in inventory;
- closes and breaks the temporary source.

See food return at `15687-15775` and ender-chest-shulker return at
`15830-15918`.

The reusable lesson is not the size of this state machine. It is:

- one inventory mutation per acknowledged delay window;
- snapshot before mutation;
- verify state after mutation;
- retain source/target identity across ticks;
- reserve pickup space;
- never assume a click succeeded;
- make retries idempotent;
- prefer keeping an item over unsafe dropping.

## Packet limiter

`PaketLimiter` defaults to 23 counted outgoing packets per tick
(`PaketLimiter.java:21-27`).

Its preset bypasses essential movement, vehicle movement, teleport confirmation,
keepalive/pong, and client-command packet classes (`56-66`). It always cancels
`HandSwingC2SPacket` (`67-69`) and counts other non-bypassed packets. The count
resets on `TickEvent.Post` (`72-75`).

Once the limit is reached, later non-bypassed packets are cancelled
(`77-92`). This is why call ordering matters. A placement packet sent before the
tool-selection/mining packets could consume the remaining budget and leave the
client/server held item or mining state inconsistent.

Source quirk: `limit=0` returns before the `alwaysBlock` check, so it disables both
rate limiting and the configured "always block" behavior (`79-83`).

HighwayBuilder enables the limiter only during activation when Packet Build is
already enabled (`2042-2045`). Enabling Packet Build/Packet Mode after activation
does not run that activation branch. HighwayBuilder also does not restore limiter
ownership on deactivation, so a limiter it enabled normally remains active.

Packet Mode is especially risky because Packet Borer runs before double mine and
the scheduler. With the fresh 23-packet limit, Borer's 130 attempted dig packets
can consume every accepted non-bypass slot before tool-selection, mining, or
placement work. It can leave roughly eleven complete START/STOP pairs plus a lone
START before later packets are cancelled. A future Nerv executor must reserve
capacity before dispatch, not rely on cancellation after Borer-style spam.

Nerv should not simply add a packet limiter after implementing concurrent actions.
Its scheduler must reserve packet capacity by action type or submit actions to one
ordered packet queue.

---

# TunnelMinerModule

## Live settings relevant to this study

| Setting | Default | Live meaning |
| --- | ---: | --- |
| `auto-freelook` | `true` | Let mouse view move without disrupting path yaw (`618-622`). |
| `probe-distance` | `48` | Cached future path/restore horizon (`642-646`). |
| `mine-ahead-distance` | `4`, max `5` | Path steps actively mined ahead (`669-673`). |
| `close-mine-range` | `1.5` | Stop walking near pending/active mining (`675-679`). |
| `double-mine-ahead` | `true` | Use overlapping slow mine tracks (`681-685`). |
| `fast-break-ahead` | `true` | Ready threshold `0.7` (`687-692`). |
| `restore-lag-distance` | `4` | Maximum pending restore lag before a catch-up pause (`694-698`). |
| `strict-exact-restore` | `false` | Permit safe fallback blocks if the original is unavailable (`700-704`). |
| `use-shulkers` | `true` | Pickaxe restock source (`720-723`). |
| `use-ender-chest` | `false` | Optional second-stage restock source (`725-728`). |
| `min-pickaxes` | `2` | Restock threshold (`730-733`). |
| `breaks-per-tick` | `2`, max `5` | Direct break attempts per live tick (`735-737`). |
| `break-delay` | `0` | Ahead-mining timer (`739-743`). |
| `places-per-tick` | `1`, max `5` | Restore action loop bound (`745-747`). |
| `place-delay` | `0` | Global placement timer (`749-751`). |
| `inventory-delay` | `3` | Global inventory mutation timer (`753-755`). |

There is no TunnelMiner `30` setting and no server-TPS action-rate formula.

The class also exposes a hidden rotation setting as explicitly non-effective
(`TunnelMinerModule.java:468-476`). It has no read sites in the live action paths.

## Effective settings snapshot

On activation/API start, TunnelMiner builds an `EffectiveOptions` snapshot
(`1139-1180`). Runtime helpers read this snapshot rather than every GUI setting
directly. That prevents a half-updated group of related settings during one run.

This pattern is useful for Nerv: construct an immutable run plan at map start, and
apply dynamic settings only through explicit, validated updates.

## Live dispatcher

`onTick(TickEvent.Pre)` is at `1905-1989`.

Global ordering:

1. stop on missing player/world;
2. enforce fixed Y;
3. pause for eating;
4. if `placeTimer>0`, decrement it and return from the whole tick;
5. if `invTimer>0`, decrement it and return from the whole tick;
6. dispatch current phase.

For `MINE`, `WALK`, and `FILL`, dispatch is always `stealthTick()`
(`1953-1960`). Restock phases use separate handlers.

The global timer behavior is important: a nonzero placement or inventory delay
does not merely delay another placement/click. It pauses mining and movement too.
Nerv should probably use per-resource timers instead of copying this global stall.

## Unified mine/move/restore pipeline

`stealthTick()` is at `2249-2429`.

Its order is:

1. If loose pickaxes are below the threshold, clear active mining and enter
   restock.
2. Resolve current X/Z and active destination.
3. Decrement the mining timer.
4. obtain or build cached path steps;
5. cache original block states along the probe;
6. take up to `mine-ahead-distance` path steps;
7. collect reachable mine targets and sort them by eye distance;
8. make a pickaxe active;
9. advance existing double-mine tracks;
10. start/continue mining ahead;
11. fill lava if required;
12. stop movement if a close mine target, close active mine, or lava still blocks;
13. otherwise restore cached blocks behind;
14. enforce maximum restore lag;
15. guarantee support under the next path step;
16. move toward the next clear step;
17. at destination, drain all reachable restore work before finishing.

This is the main precision property: planning, mining, restoration, support, and
movement are one ordered pipeline over a shared path/cache model.

## Path planning and precise movement

### Cached path

`getOrBuildProbeSteps()` is at `3171-3364`.

The cache is invalidated by:

- different Y or destination;
- different radius/path mode;
- axis-state changes;
- changed air-gap policy;
- emergency fallback changes;
- start-position mismatch;
- newly observed avoidance markers.

Replanning is throttled. Empty results have their own cooldown. The implementation
can fall back from A* to wall-follow behavior after repeated failures, and has a
bounded emergency air-gap relaxation window.

This is more robust than recalculating a shortest path on every tick; repeated
replanning can alternate between equally valid first steps and create the visible
front/back motion previously seen in repair navigation.

Not all planning is asynchronous. The air-gap-safe probe path can execute
synchronous full-horizon A* in the tick path. That can explain a visible "thinking"
pause even though another detour path uses an executor.

### Hard-coded planner policy and hazards

Several displayed/hidden path-tuning settings are not authoritative in this
revision. Live helpers use hard-coded values, including a
`DiagonalThenAxis` path mode, replan/cooldown limits, A* failure threshold, bounded
wall following, and stall timeout (`TunnelMinerModule.java:67-103`,
`6796-6798`).

The planner avoids lava/adjacent lava, unsafe air-gap floors, unbreakable cells,
blocks whose state/drop cannot be reliably restored, and gravity/chain-reaction
areas. This conservative policy is part of the precision; shortest distance is not
the only edge cost.

The nominal hazard recovery window is misleading because
`HAZARD_RECOVERY_FORCE_HARD_FAIL` is true. A detected hazard takes the hard-failure
path immediately (`87-88`, `1808-1820`). That policy is unsuitable for Nerv's
recover-and-resume requirements.

### Committed step

`pickStealthMoveStep()` (`3063-3110`) retains a `committedMoveStep`.

It releases the commitment only when:

- the destination center is reached;
- the committed step becomes non-traversable; or
- a safe non-reversing plan takes over.

If a newly planned first step is the exact reverse of the current step while the
player is still between centers, the old step is retained. This is the explicit
anti-oscillation rule.

The center threshold is a squared distance of `0.09`, or `0.3` blocks
(`3112-3119`).

### Move action

`moveToward()` (`4423-4440`) computes yaw from the target center and presses only
the forward key. It stops within `0.2` blocks.

Before calling it, the live pipeline:

- verifies/places support below the next step;
- verifies the step profile is clear;
- checks extra clearance columns for a diagonal step to avoid corner clipping;
- commits the step;
- applies close-mine, lava, and restore-lag gates.

The function itself is simple; the precision comes from the gates and committed
path state around it.

### Fixed Y and Speed ownership

Any floor-Y change stops the module (`1920-1926`).

For stealth mode, TunnelMiner snapshots Meteor Speed settings, configures Vanilla
Speed `4.0`, timer `1.0`, and selected safety flags, then restores the exact
snapshot on stop (`1281-1360`). This is another ownership pattern Nerv should use
for any external movement module.

### FreeLook

On activation, TunnelMiner enables Meteor FreeLook only if requested and currently
inactive, records whether it enabled it, and disables it only when it owns that
change (`1633-1638`, `1691-1692`).

Unlike HighwayBuilder's integrated camera mixins, this is explicit external-module
ownership.

## Mining without looking

`collectMineTargets()` (`3545-3582`) derives blocks from path-step tunnel profiles,
filters them by interaction range and safety rules, and sorts them by squared eye
distance.

Direct mining at `6940-7065`:

- selects the pickaxe;
- calls `BlockUtils.breakBlock(pos, true)` on the explicit `BlockPos`;
- uses no `Rotations.rotate`;
- respects `breaks-per-tick` and `stealthBreakTimer`;
- returns after one non-instamine direct action.

The crosshair does not choose the target.

`breaks-per-tick` applies only to this direct fallback loop. When the double-mine
branch owns one or two slow targets, that branch does not debit the direct attempt
budget (`6954-6993`).

### Tunnel double mine

TunnelMiner duplicates the HighwayBuilder normal/packet two-track model
(`7067-7143`, class at `7555-7639`):

- START a normal track;
- convert it to a packet track with STOP when another target is available;
- START the next normal target;
- use selected-slot `BlockUtils.getBreakDelta` for progress;
- use `0.7` or `1.0` readiness;
- ABORT a removed/out-of-range normal track;
- apply the same 60-age timeout rule.

Unlike direct attempts, `blocksMined` is incremented when a double-mine track is
observed changed (`7078-7098`). The live direct branch does not increment that
stat. That is an observed statistics inconsistency, not a throughput mechanism.

Lava fill also increments `blocksMined` in the current source even though it is a
placement. Watchdog/progress statistics should therefore not be treated as exact
break counts.

## Restoration and placement

### Original-state cache

`probeAhead()` (`3532-3543`) records the original state of blocks that may be
changed. Restoration later compares the current state with that original state.

This is why TunnelMiner can mine and rebuild while moving: both actions operate
against one persistent per-position record.

### Restore loop

`restoreBehindFromCache()` is at `3708-3889`.

For each eligible cached position it:

- skips active probe/mine targets;
- normally skips the player's current/near column;
- checks interaction reach;
- considers original air versus original solid separately;
- breaks an unexpected current block before trying to restore;
- defers corrective breaking while double mining is active;
- prefers the exact original block item;
- optionally finds a local or any safe full-block replacement;
- respects strict-exact mode;
- performs no more than `places-per-tick` logical restore actions.

Breaking a wrong block and placing its replacement are separate states. It will
not assume a break packet immediately made the target air.

Strict exact restoration is effective only when stealth mode is enabled. Without
it, any non-air, non-lava current block can satisfy an originally solid cache
entry; orientation and other block-state properties are not necessarily restored.
This is structural parity, not exact state parity.

### Placement without looking

`tryAirPlaceAt()` (`4084-4099`) and `tryDirectPlaceAt()` (`4102-4116`) call
`BlockUtils.place` with `rotate=false` and an explicit `BlockPos`/hotbar slot.

`tryAirPlaceAt()` can make up to three internal placement calls:

1. configured entity-intersection policy;
2. retry with the same effective constraints;
3. retry with entity checking relaxed.

This means the number of actual placement attempts can exceed the outer
`places-per-tick` logical-action count. Nerv should account for every dispatched
packet, not only successful logical targets.

Support-floor work can place the current and next floors outside the restore
loop's shared logical-action count (`4015-4077`). Lava fill is also outside that
budget and does not set `placeTimer`. Consequently Tunnel's
`places-per-tick` is not a strict global placement or packet ceiling.

### Same-tick behavior

Mining runs before lava handling and restoration. If no close-mine/lava gate owns
the tick, restore work can run immediately afterward.

Therefore TunnelMiner can mine and place in one client tick, but it intentionally
pauses restoration/movement around dangerous nearby mining and lava. It is not an
unconditional parallel packet spammer.

## Tunnel hotbar selection

### Pickaxe policy

`equipBestPickaxe()` is at `7335-7357`.

Despite its name, it chooses the pickaxe with the **lowest remaining durability**:

```text
bestDurability starts at MAX_VALUE
replace best when durabilityLeft(candidate) < bestDurability
```

It does not compare mining speed, enchantments, or a minimum durability reserve.
This may be deliberate "consume used picks first" behavior, but it is not a
general best-tool algorithm and should not be copied as such.

`ensurePickaxe()` reuses `pickSlot` while that slot still contains a recognized
vanilla pickaxe; otherwise it rescans (`7360-7367`).

### Moving an item into hotbar

`toHotbar()` is at `7372-7396`.

If the item is already in slots 0-8, it returns directly. Otherwise it:

1. shift-clicks the source inventory slot;
2. snapshots hotbar before/after and identifies which slot changed;
3. if no hotbar slot accepted it, shift-clicks one existing hotbar stack back to
   main inventory;
4. does not free the cached `pickSlot`;
5. retries the original shift-click.

`shiftClickToHotbar()` is at `7398-7476`; `freeHotbarSlotByShiftClick()` is at
`7478-7504`.

The post-click comparison uses item and count only (`7520-7524`), not complete
components/NBT. Two otherwise identical component-bearing stacks could therefore
be misidentified. Nerv should use full item/component equality plus count.

TunnelMiner has no persistent reserved hotbar layout. It can displace any slot
except the cached pickaxe slot during the "free one slot" pass.

If moving the selected pickaxe into the hotbar fails, `equipBestPickaxe()` falls
back to slot 0 and selects it even though slot 0 has not been proven to contain a
pickaxe. That fallback must not be copied.

### Temporary slot changes

TunnelMiner calls `InvUtils.swap(hotbarSlot, true)` for tools and blocks. It does
not explicitly call `InvUtils.swapBack()` in its live placement helpers.

Do not infer more than the source proves here: the exact persistence of
`InvUtils.swap(..., true)` is Meteor-version behavior. Before porting, verify
whether this is a silent server slot change, whether a later swap implicitly
restores anything, and how it interacts with screen actions.

## Tunnel container restock

The live restock phase sequence is:

```text
RESTOCK_CLEAR
  -> RESTOCK_PLACE
  -> RESTOCK_WAIT
  -> RESTOCK_OPEN
  -> RESTOCK_LOOT
  -> RESTOCK_CLOSE
  -> RESTOCK_BREAK
  -> RESTOCK_PICKUP
```

### Clear and place

`restockClear()` (`4786-4868`) first preserves restore parity, reserves a container
column, breaks obstructions, then selects:

- a pickaxe-bearing inventory shulker;
- otherwise a configured ender chest;
- otherwise hard-fails.

`restockPlace()` (`4870-4956`) centers the player, guarantees floor support, moves
the selected container to hotbar, places it, immediately verifies its block type,
and retries through a bounded placement state if it did not appear.

Centering uses a tight `0.05` block threshold, stops velocity, and snaps to exact
`.5/.5` coordinates when close or after a bounded timeout (`4958-5037`).

### Open

Opening is one place where TunnelMiner deliberately rotates:

```text
Rotations.rotate(yawToContainer, pitchToContainer, interactBlock)
```

See `5089-5108`. Normal mine/restore actions remain no-rotation.

### Transfer between container and player

`restockLoot()` is at `5111-5294`.

It:

- keeps at least one free player slot for the broken container drop;
- stops when the loose-pickaxe target is reached;
- scans only the container section of the current screen handler;
- transfers one pickaxe with `InvUtils.shiftClick().slotId(i)`;
- sets `invTimer=inventoryDelay`;
- returns, allowing screen/server state to update before another click.

When using an ender chest, it can instead choose a shulker containing pickaxes.
Selection prefers:

- the smallest shulker that satisfies the remaining pickaxe requirement;
- otherwise the shulker with the largest useful pickaxe count.

It shift-clicks that shulker to player inventory, closes the ender chest, then
places/opens the extracted shulker in a later restock cycle.

### Cleanup and resume

It closes the screen, selects a pickaxe, breaks the temporary container, waits for
the dropped shulker/ender chest/obsidian, and moves toward the nearest drop every
other tick (`5296-5400`).

After pickup it either:

- runs another inventory-shulker stage;
- runs an ender-chest stage;
- fails if still under the minimum with no source; or
- chooses a pickaxe and resumes `Phase.MINE` (`5402-5484`).

This is a sound multi-tick transfer pattern, though it lacks HighwayBuilder's more
complete before/after component verification and cursor recovery.

## Dead/legacy Tunnel methods

The following remain in the file but are not called by the current dispatcher:

- `minePhase()` (`4124+`);
- `walkPhase()`;
- `fillPhase()` (`4445+`);
- their older `fillLog` sequencing helpers.

They can still contain useful historical ideas, but any behavior described only by
those methods is not current TunnelMiner behavior at the pinned commit.

---

# Useful project-wide components

## InventoryManager

`InventoryManager` is not the hotbar engine used by HighwayBuilder or TunnelMiner's
live loops. It is nevertheless a useful design reference for a future Nerv slot
lease service.

It tracks:

- client-selected hotbar slot;
- last server-known hotbar slot;
- outgoing and incoming selected-slot packets;
- desynchronization;
- priority levels for normal, totem, eating, surround, and pearl actions;
- recent server setback and a basic Grim detection probe.

Key methods are at `InventoryManager.java:64-209`.

### Full-inventory silent swap

`swapTo(slot, silent, inventory)` (`313-359`) supports:

- hotbar selection through `UpdateSelectedSlot`;
- full-inventory `SlotActionType.SWAP`;
- a hidden buffer slot (8, or 7 when 8 is the visible slot) for silent inventory
  use;
- restoration by sending the same SWAP again in `swapBack()` (`367-388`).

This is preferable to cursor pickup chains because one SWAP is self-reversing.

However, the implementation stores only one global `lastSwap...` record. It is not
nested, reentrant, or safe for two concurrent action owners. Nerv should implement
an explicit token/lease stack and restore in `finally`.

It also passes raw player-inventory slot numbers together with the current screen
handler's sync ID. Player slot IDs are mapped differently while a container screen
is open, so this utility must not be reused unchanged during chest/shulker
transactions. Its buffer slots 7/8 are not reserved from other modules.

### Silent tool action

`withSilentTool()` (`471-479`) chooses the fastest hotbar stack by mining-speed
multiplier, sends a sequenced slot update, runs an action, then restores the prior
slot.

It only searches hotbar and ignores enchantments/durability. HighwayBuilder's
`AutoTool` selection is the stronger scoring policy.

## PacketPlaceUtils

Use this as a reference for the mechanics of:

- support-side resolution;
- explicit hit-vector construction;
- no-look `PlayerInteractBlockC2SPacket`;
- optional air placement;
- silent swap versus retained placement slot;
- optional packet rotation.

Do not copy its always-swing behavior blindly when a packet limiter will cancel
swings anyway. Do not copy HighwayBuilder's incorrect use of the mining rotation
flag for placement.

## PaketLimiter

Useful concepts:

- essential protocol traffic bypass list;
- an explicit always-block list;
- reset at one known tick phase;
- cancel before packets reach the network.

Missing for Nerv:

- action-aware reservations;
- feedback to the caller that its packet was cancelled;
- atomic multi-packet bundles;
- separate budgets for inventory, mining, placement, and maintenance traffic.

Without those, a method can return "sent" even though the limiter cancelled the
packet.

## Speedmine

### Identity and actual call sites

`Speedmine.java` is THM's own module, registered as `speedmine` in
`THMAddon.java:188`. It is different from Meteor's `SpeedMine` class.

HighwayBuilder's regular forward excavation does **not** send work to THM
`Speedmine`. HighwayBuilder owns that work itself and separately configures/queries
Meteor `SpeedMine`. TunnelMiner does not call THM `Speedmine` at all.

There is one live integration: HighwayBuilder's `MineEnderChests` restock state
enables THM `Speedmine`, calls `requestBreak()` once for the reusable ender-chest
position, and then relies on `autoRebreak` as more ender chests are placed at that
same position (`HighwayBuilderTHM.java:13247-13277`, `13367-13392`).

### Important defaults

| Setting | Default | Runtime meaning |
| --- | ---: | --- |
| `auto-mine` | `false` | PvP phase/surround target discovery is off (`Speedmine.java:87-126`). |
| `enemy-range` | `10.0` | Maximum distance for PvP enemy discovery when auto mine is on (`93-98`). |
| `anti-phase` / `anti-surround` | `true` / `true` | Prefer hitbox-overlap blocks, then surrounding blocks (`100-112`, `458-462`). |
| `auto-double-mine` | `true` | Permit two auto targets when `double-break` is also enabled (`114-119`, `432-443`). |
| `mine-bedrock` | `false` | Disable the plugin-oriented swing/progress bedrock path (`121-126`, `517-544`). |
| `grim-bypass` | `true` | Prepend STOP before each START (`128-132`, `351-358`). |
| `double-break` | `true` | Permit primary plus secondary contexts (`134-138`). |
| `queue` | `true` | Queue requests after both contexts are occupied (`140-144`). |
| `break-threshold` | `0.7` | Primary predicted completion target (`146-151`, `665-671`). |
| `validate-break` | `true` | Do not force local AIR; it does not wait for an explicit acknowledgement (`153-157`, `379-393`). |
| `remove-slow-blocks` | `false` | When validation is off, do not spoof every slow block as AIR (`159-164`). |
| `auto-rebreak` | `true` | Re-send completion work if the last position becomes solid again (`166-170`, `259-267`). |
| `silent-swap` | `true` | Select the calculated hotbar tool server-side (`172-176`). |
| `tool-hold` | `true` | Keep that server-side slot through mining and release after idle (`178-182`, `276-281`). |
| `range` | `4.5` | Code tests center distance against `range + 0.5`, effectively `5.0` (`184-188`, `620-624`). |

### Request, queue, and two-context lifecycle

Manual mining enters through `StartBreakingBlockEvent` (`235-244`):

1. reject unbreakable or out-of-range targets;
2. cancel the normal event;
3. pass the explicit position into `handleBlockClick()`.

The public `requestBreak()` API only rejects null-world/air cases
(`607-612`). It does **not** repeat the manual path's `canBreak` or range checks.
Any Nerv-style caller would need to validate those invariants before requesting.

`handleBlockClick()` (`301-318`) behaves as follows:

1. With no primary, latch the block as primary and send its start.
2. With a primary but no secondary, STOP the old primary immediately, reconstruct
   it as a secondary context, create the new primary, and START the new primary.
3. With both occupied, append the position to the FIFO queue if queuing is enabled.

Reconstructing the secondary resets its start timestamp and changes its completion
target from `0.7` to `1.0`; it does not preserve the old primary's elapsed progress.
`drainQueue()` repeats the same transition (`330-347`).

On every Pre tick the module:

1. notices a visible client-slot change and drops its local server-slot-hold record;
2. runs optional PvP auto-targeting;
3. attempts an auto-rebreak before normal pruning/completion;
4. removes contexts that are locally AIR or out of configured range;
5. finishes secondary, then primary, when their predicted progress reaches one;
6. drains the queue;
7. releases a held server slot when mining has been idle for three ticks.

See `247-282`. There is no explicit ABORT when an active target is pruned.

### Exact destroy-packet ordering

`sendStart()` first selects the latched tool when silent swapping is enabled. It
then sends:

```text
grim-bypass=true:   STOP(target) -> START(target)
grim-bypass=false:  START(target)
```

The actions use THM's sequenced interaction-manager accessor and hard-code
`Direction.DOWN` (`351-376`, `572-581`).

At predicted completion:

- a context initially classified below vanilla instant-break sends another STOP;
- a context initially classified `instaBreak` suppresses that final STOP;
- `autoRebreak` builds a fresh temporary context and calls the same stop helper, so
  a non-instant target gets STOP-only rebreak attempts while a newly classified
  vanilla-instant target can emit no destroy action from that helper.

This matters because the source comment says a vanilla instant target already got
a START+STOP pair in `sendStart()`, but the implementation does not send that order:
with the default bypass it sends STOP-before-START, and without the bypass it sends
only START (`351-365`). Treat the behavior as server/exploit-specific and verify it
on the target server; do not promote the comment into a general protocol rule.

### Predicted progress is wall-clock based, not TPS based

Each `MineContext` captures hardness, a best-tool hotbar slot, wall-clock start
time, and two initial classifications (`653-663`). On each progress query it
recalculates the break delta and evaluates:

```text
elapsed20tps = max((currentMillis - startMillis) / 50 + 1, 1)
target       = primary ? configuredThreshold : 1.0
progress     = min(currentBreakDelta * elapsed20tps / target, 1)
```

See `665-671`.

The break-delta calculation manually includes hardness, tool suitability,
Efficiency, Haste, Mining Fatigue, submerged mining speed, and the airborne
penalty (`674-716`). It recalculates against the currently fastest hotbar stack,
not necessarily the `startSlot` latched into the context.

Consequences:

- There is no `TickRate` input and no server-TPS correction.
- `/ 50 ms` assumes a 20 TPS timeline even if the server is slower.
- A system-clock adjustment can jump or stall predicted progress.
- The primary can finish at 70% of predicted vanilla progress by default; the
  secondary waits for 100%.
- The source header describes a possible 20 BPS path, not a measured or enforced
  20/30 BPS scheduler.
- There is no global per-tick action or packet budget. Two contexts, queue
  transitions, auto-rebreak, and selected-slot packets can create several packets
  in one client tick.

If recalculated delta is non-positive, `progress()` returns `Double.MAX_VALUE`
rather than zero (`667-669`), which can incorrectly finish a nonnegative-hardness
target. The hand-maintained delta formula can also drift from Minecraft/Meteor's
canonical calculation as attributes change between versions.

### `validate-break` is not acknowledgement tracking

At predicted completion `finishBreak()` always sets `lastBrokenPos`, deactivates
the context, and removes it from primary/secondary (`379-393`).

With `validate-break=true`, it merely leaves the client world alone and waits for
normal server world updates to show what actually happened. It does not retain the
context until AIR, correlate a server response to the request, or retry a failed
ordinary target.

With validation disabled, the module can play a break event and force the client
state to AIR when the target was initially vanilla-instant, initially over the
configured threshold, or `remove-slow-blocks` is enabled (`384-387`). That is
unsafe for Nerv: a printer must never use client-forced AIR as proof that the
server accepted a repair or cleanup.

`autoRebreak` is not a general retry ledger:

- there is only one `lastBrokenPos`, so the last finishing context overwrites any
  other failed position;
- it runs when primary and secondary are empty even if the FIFO queue is not, then
  returns before queue draining, so a persistent reappeared block can starve queued
  work;
- its temporary true-instamine context suppresses STOP and never sends START, so
  that rebreak attempt can emit no destroy action;
- it has no attempt bound, deadline, server response correlation, or rollback.

Turning `queue=false` after entries exist also strands those entries: queue draining
stops, but the nonempty queue prevents the held tool from becoming idle.

### Tool selection and lifetime

The most reusable idea is the server-side tool lifetime:

- choose and latch a hotbar slot before START;
- issue `UpdateSelectedSlotC2SPacket` before destroy packets;
- keep that server-side slot selected while any context/queue remains;
- return to the user's visible slot only after three completely idle ticks
  (`548-568`).

This avoids an immediate silent swap-back racing a slow server-side break. It is a
better model than wrapping a slow START in a swap/action/swap-back lambda.

However, the implementation is not a complete Nerv tool policy:

- it searches only hotbar slots 0-8;
- it ranks only `getMiningSpeedMultiplier`;
- it has no durability floor, Silk Touch rule, item reservation, or full-inventory
  promotion;
- it does not include Efficiency in candidate ranking, so equal base-speed tools
  are decided by earlier hotbar position even though the later progress formula
  does include Efficiency;
- the progress calculator may choose a different fastest stack than the latched
  start slot;
- non-silent selection writes the client selected-slot field but does not itself
  send the explicit slot packet used by the silent path (`586-602`);
- `withSilentTool()` is a separate immediate swap/action/swap-back helper and does
  not provide the three-tick hold (`409-417`);
- that helper lacks `try/finally` and does not coordinate with an existing
  long-lived `heldSlot`, so an exception or nested use can leave its cached
  server-slot belief incorrect.

### Ender-chest rebreak integration and ownership defects

HighwayBuilder first attempts to put a non-Silk-Touch tool in hotbar, then calls
THM `Speedmine`. `Speedmine` independently scans all hotbar tools and can choose a
different, faster Silk Touch tool. That violates the ender-chest restock intent if
such a tool is present.

HighwayBuilder also does not record whether it activated THM `Speedmine`:

- state start turns it on only if inactive (`13276`);
- missing-tool and normal-completion paths turn it off whenever it is active
  (`13370`, `13458`).

Therefore a `Speedmine` module that the user already had enabled can be disabled by
the restock state. Nerv must use an ownership token and restore only state it
changed.

The optional ender-chest speed multiplier is a separate HighwayBuilder Timer/Speed
override with its own snapshot (`8130-8176`); it is not TPS-aware logic inside
`Speedmine`.

### Auto-targeting and bedrock are not printer mechanisms

With `auto-mine=true`, the module filters out self, spectators, friends, optionally
THM members, and enemies outside `enemy-range` (`447-456`). It first collects
breakable blocks intersecting enemy hitboxes. Only when that set is empty does it
collect horizontal feet/head rings plus the block over the head, then sorts by
distance from the local player's eyes (`458-503`).

The normal auto budget is one target, or two when both double settings are enabled.
Already-owned leading targets can consume that small discovery budget repeatedly,
so this is not a deep route-window scheduler.

Bedrock takes a separate `updateBlockBreakingProgress` plus hand-swing path every
tick. It does not tool-swap, and it checks whether `PaketLimiter` is blocking the
required swing (`517-544`). This is for server plugins that make bedrock breakable;
it cannot mine vanilla bedrock. The comment "like Nuker" is descriptive only:
`Speedmine` never calls the Nuker module.

### Packet limiting and test coverage

`Speedmine` does not reserve packets with `PaketLimiter` or receive feedback when
the limiter cancels a slot/START/STOP packet. It nevertheless advances its local
contexts and `heldSlot` belief. A cancelled tool packet or one half of a destroy
sequence can silently desynchronize all subsequent predictions. Only the special
bedrock path checks whether the limiter blocks hand swings (`519-533`).

No Speedmine-specific tests exist in the checked THM test tree. All protocol,
anti-cheat, timing, and low-TPS behavior therefore needs an instrumented server
test before reuse.

### What to reuse for Nerv

Reuse only the concepts:

- explicit `BlockPos` requests;
- tool-before-START ordering;
- a latched server-side tool lease across slow work;
- separate primary/secondary ownership;
- a bounded, deduplicated request queue.

Do not copy:

- wall-clock `/ 50 ms` prediction as confirmation;
- threshold-based client AIR spoofing;
- unchecked `requestBreak()` inputs;
- a global module toggle without ownership;
- raw-speed-only tool choice;
- the special STOP/START sequence without a server-specific test.

## Nuker

### Scope and independence

`Nuker.java` is registered as a standalone World module at
`THMAddon.java:179`. Project-wide call-site search finds no HighwayBuilder or
TunnelMiner lookup, toggle, or request into it. It provides no walking, NBT
geometry, placement, printer checkpoint, or chest-restock behavior.

It is still useful as a reference for target scanning, multiple break attempts per
tick, packet mining, and full-inventory tool swaps. It is not the source of
HighwayBuilder's precision or simultaneous mine/place scheduler.

### Important defaults

| Setting | Default | Runtime meaning |
| --- | ---: | --- |
| `shape` | `Sphere` | Scan a radius around the player (`Nuker.java:56-61`). |
| `mode` | `Flatten` | Ignore blocks whose center is below the player (`63-68`, `273`). |
| `range` | `4.0` | Visible-target shape radius; Cube uses six directional ranges (`70-86`). |
| `walls-range` | `4.0` | Maximum distance for a target hidden behind another collider (`86`, `574-582`). |
| `delay` | `0` | No target-change delay (`87`, `293-305`). |
| `max-blocks-per-tick` | `1` | Number of target attempts admitted by the main loop; it has no configured maximum or special cap at 30 (`88`, `308-321`). |
| `sort-mode` | `Closest` | Sort candidate centers by player-position distance (`90`, `286-291`). |
| `packet-mine` | `false` | Use Meteor `BlockUtils.breakBlock`, not raw START+STOP (`91`, `371-379`). |
| `only-suitable-tools` | `false` | Do not prefilter by currently held tool (`92`, `275`). |
| `interact` | `false` | Break rather than right-click targets (`93`, `329-333`). |
| `rotate` | `true` | Request server-side Meteor rotation before each action (`94`, `313-314`). |
| `swap-mode` | `None` | Do not choose a tool automatically (`96`). |
| `avoid-liquid-contact` | `false` | Do not reject a target touching water/lava (`97`, `486-497`). |
| `mine-bedrock` / `bedrock-range` | `false` / `10.0` | Disable the special vanilla-progress bedrock loop (`98-99`, `395-419`). |
| `double-mine` | `false` | Disable the two slow-block tracks (`100`, `421-460`). |
| `swing` | `true` | Show/send the configured hand swing for ordinary actions (`107`, `371-379`). |

### Main Pre-tick pipeline

The live order in `onTickPre()` is (`196-325`):

1. restore a one-tick hotbar-only silent swap if due;
2. progress the special bedrock target;
3. progress existing double-mine targets;
4. stop for the configured target-change delay;
5. construct the selected Sphere, UniformCube, or facing-relative Cube volume;
6. register a `BlockIterator` scan;
7. filter and collect candidate positions;
8. in the iterator's `after` callback, sort and attempt up to
   `max-blocks-per-tick`.

The filters include shape, Flatten/Smash mode, current-hand suitability,
breakability, liquid adjacency, wall visibility/range, whitelist/blacklist, and
already-interacted state (`258-284`).

The first three maintenance steps run even when the delay timer then returns.
Double-mine and bedrock work are consequently outside the normal target-attempt
budget and delay gate.

Details that matter:

- `Smash` accepts only hardness-zero states.
- `UniformCube` rounds `range` and writes the rounded value back to the setting
  every tick (`212`).
- Visible targets are accepted by the raycast test without a separate interaction
  range check; the shape usually bounds them, but large Cube/range settings can
  schedule server-unreachable actions.
- Occluded targets use `walls-range`.
- `only-suitable-tools` checks the **currently visible main-hand stack before**
  Nuker's later auto-swap. A suitable tool elsewhere in inventory does not make
  that candidate pass.
- Rotation callbacks are Meteor dependency behavior. Nuker increments its logical
  attempt count immediately after requesting the rotation, not after server
  confirmation.

When the first sorted target changes, `delay` can pause the entire main scan. Empty
scans eventually reset the "first block" state and clear interaction history
(`293-305`).

The delay return occurs before `blocks.clear()`, and activation/deactivation also
do not clear that list (`142-165`, `301-325`). A delayed target change can therefore
retain stale positions and allow duplicates to leak into a later scan.

Ordinary rotated actions run in a `Rotations.rotate(..., callback)` callback, so
their explicit target does not require moving the user's visible camera. However,
the loop charges the attempt, renders the target, and advances `lastBlockPos` when
it schedules that callback, without revalidating module state/range/block state at
the eventual callback.

With `interact=true`, the action becomes an explicit `BlockHitResult` right-click
and the position is remembered in `interacted`; those positions are skipped until
an empty scan clears the set (`281`, `293-296`, `329-333`).

### Direct, instamine, and packet-mine behavior

For every admitted candidate Nuker computes `BlockUtils.canInstaBreak(block)`
before acting (`308-320`).

That classification occurs before Nuker chooses/swaps to its best tool. A block
that becomes instant-break only with the planned tool is still classified using
the currently held item, affecting both loop continuation and double-mine routing.

With normal defaults:

- it calls `BlockUtils.breakBlock(block, swing)`;
- it can continue through several candidates only while each is classified
  instant-break;
- after the first non-instant candidate it stops the loop for that tick.

`max-blocks-per-tick` is therefore an **attempt budget**, not confirmed BPS.
The counter increments even when the later action fails, is only queued for double
mine, is cancelled by a packet limiter, or has not run yet because rotation was
deferred.

With `packet-mine=true`, each target instead receives raw:

```text
START_DESTROY_BLOCK -> optional client swing -> STOP_DESTROY_BLOCK
```

See `371-377`. The code attempts this even for blocks that
`BlockUtils.canInstaBreak` says are slow, and the main loop no longer stops after
the first slow block. The setting description "Attempt to instamine everything"
is literal packet speculation, not proof that the server accepted every break.

These packets do not use THM `Speedmine`'s sequenced accessor, queue, tool hold,
threshold progress, range API, Grim STOP-before-START path, or validation option.
There is no per-target acknowledgement ledger.

At highest event priority, active Nuker also sets Meteor's block-breaking cooldown
event to zero (`605-608`). This is a global behavior while the module owns the
event, not a per-Nerv-task confirmation mechanism.

### Tool-swap modes

The enum is defined in `Enums.java:5-10`:

| Mode | Search | Selection behavior | Restoration |
| --- | --- | --- | --- |
| `None` | none | Use current server/client item. | none |
| `Normal` | hotbar 0-8 | Change visible selected slot and send `UpdateSelectedSlot`. | not restored |
| `Silent` | hotbar 0-8 | Change `InventoryManager`'s server-known slot only. | sync to visible client slot at the start of the next Pre tick |
| `InventoryNormal` | inventory 0-35 | SWAP a main-inventory tool into the visible selected hotbar slot. | immediately reverse after the break call |
| `InventorySilent` | inventory 0-35 | SWAP into buffer hotbar slot 8, or 7 if 8 is visible, then select it server-side. | immediately reverse after the break call |

See `Nuker.java:345-383`, `InventoryManager.java:313-388`.

All Nuker tool rankings use only raw mining-speed multiplier (`499-532`). They do
not enforce remaining durability, Silk Touch, Mending, or a reserved printer slot.

The two inventory modes inherit `InventoryManager`'s limitations:

- one global, non-nested pending swap record;
- unreserved buffer slots 7/8;
- raw player-inventory slot IDs combined with the current screen handler's sync
  ID, which is unsafe to reuse unchanged while a container GUI is open;
- no `try/finally` around Nuker's swap/action/swap-back sequence;
- no server acknowledgement that either SWAP completed before the break and
  reversal packets.

Immediate inventory swap-back is suitable only for an action that the server
resolves with that packet bundle. It is not a safe slow-mining lease. The hotbar
`Silent` mode also restores at the next tick rather than holding the tool until
world confirmation.

Most importantly, the double-mine branch returns before all of this swap logic
(`335-343`). Nuker's configured swap mode is not applied when a slow target is
queued into `DoubleMineTarget`; its progress later uses whatever hotbar slot is
currently selected. Nuker also has no `HotbarManager` integration or durability
guard, so it neither arranges slots nor protects a nearly broken fastest tool.

### Double mine

When enabled, Nuker first forces `max-blocks-per-tick` to at least two and remembers
the previous value if it was lower (`421-430`, `462-473`). Disabling double mine or
the module restores the remembered value. A user change made while that remembered
value is owned can consequently be overwritten on restore.

Non-instant targets are deduplicated into `doubleMineQueue` during the main scan
(`335-342`, `476-484`). Existing targets are progressed at the start of a later
Pre tick:

1. START the first queued block as `normalMining`.
2. When another queued block is available, STOP the old normal block and retain it
   as `packetMining`.
3. START the new block as `normalMining`.
4. Re-send STOP for the normal block on every tick after predicted progress reaches
   one, until the client world state changes.
5. Keep the packet track until its block changes or it times out.

See `421-460` and `628-658`.

Progress is:

```text
BlockUtils.getBreakDelta(currentSelectedSlot, capturedState)
    * (playerAge - selectedStartAge + 1)
```

It is client-tick/player-age based and uses the current selected slot, not a
latched tool. There is no TPS input and no automatic swap before START/STOP.

The non-packet track is removed outside interaction range. The packet track ignores
distance. Timeout requires both more than 60 player ages and predicted progress
above `2.0`; a context whose progress never exceeds two can remain indefinitely
(`649-657`). Double-mine packets are raw, unsequenced, and do not swing.

No double-mine transition sends ABORT on range loss, timeout, disable, or module
deactivation. Queue promotion rechecks only whether the head is AIR or now
instant-break; it does not revalidate range, block list, liquid adjacency, or the
rest of the original candidate policy (`443-449`). START/STOP also bypass the
ordinary rotation callback, so default `rotate=true` does not correctly rotate
double-mine packets.

If `packet-mine` is enabled while double mine already owns contexts, new candidates
stop entering the double queue, but the existing normal/packet contexts are not
cleared merely because packet mode changed.

### Bedrock path

`mine-bedrock` is a separate plugin-oriented vanilla-progress path:

- find the nearest bedrock inside `bedrock-range`;
- optionally rotate;
- call `updateBlockBreakingProgress`;
- optionally swing every Pre tick (`395-419`, `535-568`).

It ignores Nuker's normal whitelist/blacklist, liquid-contact, shape, tool-swap,
packet-mine, and per-tick target budget. With the default range 10, the nearest
search can inspect a 21-by-21-by-21 cube whenever it needs a new target. The
bedrock action and the normal candidate pass can both run in one tick.

Unlike THM `Speedmine`'s bedrock helper, Nuker does not check whether `PaketLimiter`
is configured to block `HandSwingC2SPacket`.

Its rotation call has no action callback: Nuker requests rotation and immediately
calls `updateBlockBreakingProgress` (`414-418`). The mining action can therefore be
ordered before the rotation packet rather than being gated by it.

### TPS, throughput, and confirmation limits

Nuker never reads `TickRate`. Its main limit is integer attempts per client Pre
tick. `delay`, player age, rotation callbacks, the client timer, server TPS,
network acceptance, and local world updates all affect real throughput.

For example, setting the unrestricted attempt value to 30 at 20 client ticks per
second permits a theoretical 600 scheduled targets/second, not 30 confirmed BPS;
packet mode can add two destroy packets per target before slot/swing traffic.

It has no:

- fractional TPS-scaled credit bucket;
- shared placement/mining conflict scheduler;
- atomic packet-bundle reservation in `PaketLimiter`;
- stable tool lease for slow work;
- per-position request/acknowledgement state machine;
- confirmed-block statistic comparable to a printer ledger.

Therefore it does not substantiate a claim that THM's highway path achieves
"30 BPS based on server TPS." That behavior must be studied in HighwayBuilder's
own scheduler, and measured as confirmed world changes rather than Nuker attempts.

### What to reuse for Nerv

Potentially reusable ideas:

- precompute exact candidate positions, then sort and spend a bounded attempt
  budget;
- stop bulk direct work at the first non-instant target;
- separate ordinary instant work from owned slow-mine contexts;
- remove vanilla block-breaking cooldown only within an explicit owner;
- represent full-inventory tool promotion as a reversible transaction.

Do not reuse Nuker's spatial "break everything matching" target scanner for an NBT
printer. Nerv already has an exact desired-state model. Also do not reuse its
packet-mine speculation, immediate slow-tool swap-back, current-slot double-mine
progress, unbounded acknowledgement assumptions, or container-unsafe slot mapping.

## THMHwyMonitor

HighwayBuilder can enable/manage `THMHwyMonitor` when Baritone is installed
(`HighwayBuilderTHM.java:704-709`).

Useful concepts in `THMHwyMonitor`:

- true-center highway geometry;
- periodic alignment checks;
- a maximum correction distance;
- forward/center stall detection;
- a two-block repair backstep;
- pausing HighwayBuilder/Timer/Speed before recovery;
- Baritone correction;
- packet-desync/rubberband detection;
- checkpointed reconnect/resume.

This monitor is recovery infrastructure, not the primary smooth movement loop.
Nerv's short connector turns should remain in its own traversal controller; a
heavy Baritone recovery should be a bounded fallback.

## Camera and input mixins

The HighwayBuilder camera/entity mixins are useful only if Nerv must own entity yaw
while allowing the user to look around. They do not affect block action rate.

TunnelMiner's explicit FreeLook ownership is simpler and safer where the external
module is available.

## RotationUtils

This whole-project utility is not used by either primary class. It should not be
ported as-is:

- its movement correction casts player input to THM's `InputAccessor`;
- no implementing input mixin is registered in the checked mixin configuration;
- silent rotation immediately sends a full movement packet;
- its request selection has priority-edge behavior that can ignore priority zero.

HighwayBuilder's integrated camera mixins and TunnelMiner's owned FreeLook are the
actual references for this task.

## PacketLoggerTHM and monitor instrumentation

`PacketLoggerTHM` can record interact, dig, selected-slot, and movement packets.
`THMHwyMonitor` also observes outgoing forward destroy traffic. These are useful
for a Nerv validation mode that compares:

- scheduler decisions;
- packets admitted by the limiter;
- client world updates;
- confirmed/stat-counted blocks.

They are instrumentation references, not execution dependencies.

## PlacementUtils

`PlacementUtils` is a smaller packet-placement helper with strict support-direction
and entity checks. Its hit-vector construction is useful, but HighwayBuilder uses
`PacketPlaceUtils` for forward Packet Build and Meteor `BlockUtils` elsewhere.

Do not combine these helpers without defining one canonical placement contract;
their side-selection, entity, swing, and swap behavior differ.

## THMStashMover

For future bulk chest-to-chest movement, `THMStashMover` is a more relevant source
than the printer/miner loops. It maintains managed inventory slots, container
targets, retries, and `QUICK_MOVE` transfers. It is not involved in
HighwayBuilder/TunnelMiner throughput and should be studied separately before any
Nerv dumping-station rewrite.

---

# Recommended Nerv architecture

The THM mechanisms should be adapted, not dropped into `StaircasedPrinter` as one
large state branch.

## 1. Immutable traversal plan

At print start, derive an immutable run plan containing:

- original NBT and generated compact NBT identity;
- ordered U-route positions;
- traversal direction and reverse direction;
- lane and connector phase boundaries;
- expected blocks and repair rules;
- inventory demand for one-column and two-column modes;
- selected TPS/action policy;
- interaction range.

Do not infer the route again from current position every tick.

## 2. Ordered route-window scheduler

Translate HighwayBuilder's five "future rows" into a small window over ordered U
route indices:

```text
current route index
next N lane/connector indices
pending mine tasks by route index
pending place tasks by route index
same-position conflict tasks
```

The route order, not nearest Euclidean distance, must be authoritative. Eye distance
may order targets **within** an interaction-range batch, but must not send the bot
to the wrong end of a U.

## 3. Separate action budgets

Maintain:

- mine action rate and fractional carry;
- place action rate and fractional carry;
- inventory actions per delay window;
- packet-cost estimate per action;
- reserved protocol packet capacity.

TPS scaling should be optional and explicit:

```text
if TPS valid and >= minimum:
    scaledRate = configuredRate * clamp(TPS, minimum, 20) / 20
else:
    pause or use a documented fallback policy
```

Do not expose "30 BPS" if the setting is actions per tick.

## 4. Target ownership

Each block position should have exactly one current owner:

- mine;
- place;
- repair-break;
- repair-place;
- active slow mine;
- confirmed/satisfied.

Mine first, then place unrelated positions in the same tick. Move a same-position
place into a conflict queue until AIR/server confirmation is observed.

## 5. Reuse Nerv's existing placement and breaking primitives

The new scheduler should select **when and which position** to act on, then call
Nerv's already-proven normal printer placement/breaking functions.

Do not create a separate connector-only placement implementation. The connector
must receive the same:

- reach batching;
- support selection;
- retry/confirmation;
- jumping/walking coordination;
- hotbar preparation;
- error handling

as a normal traversal lane.

## 6. No-look action executor

Define one contract:

```text
attemptPlace(target, expectedBlock, slotLease) -> ActionResult
attemptBreak(target, toolLease)                -> ActionResult
```

`ActionResult` should distinguish:

- not actionable;
- packet rejected locally;
- packet submitted;
- client state changed;
- server state confirmed;
- retryable failure;
- terminal policy failure.

Construct target hit results directly. Rotation should be an independent option,
not coupled to camera yaw or route movement.

## 7. Hotbar reservation and leases

Combine the strongest THM ideas:

- configured/reserved slots like HotbarManager;
- latched placement/tool slots like HighwayBuilder;
- `AutoTool`-quality scoring;
- one reversible SWAP for main-inventory items like InventoryManager;
- explicit ownership tokens;
- `try/finally` restoration;
- no nested global "last swap" state.

Suggested reserved roles:

- primary print block;
- connector cobblestone/support;
- repair pickaxe retained at all times;
- food;
- XP bottles;
- optional emergency support.

The role may point to a preferred slot, while the scheduler latches the actual slot
until its stack is exhausted or invalid.

## 8. Inventory transfer transaction

Every chest/shulker transfer should be a multi-tick transaction:

1. confirm the expected screen/sync ID;
2. identify source and target slots;
3. snapshot item, components, and count;
4. reserve player capacity;
5. send one click/quick-move;
6. wait the configured acknowledgement window;
7. verify both source and destination effects;
8. retry idempotently or retain the item safely;
9. clear the cursor before closing.

Do not run inventory mutation and hotbar sorting concurrently.

## 9. Precise movement controller

For each U segment:

- commit to the next route step until its center/turn threshold is reached;
- reject an immediate reverse replan while between step centers;
- use forward and lateral projections for lane alignment;
- use hysteresis for correction;
- stop at the print boundary until required actions are satisfied;
- preflight floor/headroom before moving;
- keep the connector turn as ordered adjacent steps;
- invoke general pathfinding only after bounded local recovery fails.

This directly addresses front/back oscillation and attempts to walk straight to the
other connector endpoint.

## 10. Confirmation and recovery

Never use "method returned true" as the only completion signal.

Track:

- packet/action attempt age;
- expected state;
- last observed world state;
- retry count;
- current route index;
- forward/reverse access cost;
- inventory/tool requirements.

On repair:

- if material and pickaxe are already held, repair at the first reachable point;
- if a refill is required, preserve the suspended print route index;
- choose the shortest valid route to the repair target;
- restore the suspended print state after confirmation;
- do not replay a completed U only to rediscover the defect.

Nerv's circular support guard must classify the reason a route step is not
walkable. Missing or pending expected floor is placement work. A wrong floor is
break-and-replace repair. Either occupied player-headroom cell is clear-only
repair and completes only after a newer authoritative AIR observation. Treating
all four cases as one false `supportReady` result creates a silent permanent
movement wait after the floor itself has already been confirmed.

## 11. External module ownership

For FreeLook, Speed, Baritone, Meteor `SpeedMine`, THM `Speedmine`, Nuker, or
another helper:

- capture the complete prior state;
- record whether Nerv changed it;
- restore only when Nerv owns the change;
- persist recovery state if reconnect/resume can occur.

## Suggested implementation phases

1. Add metrics and action-result instrumentation to the existing Nerv primitives.
2. Add hotbar reservations/latches without changing print order.
3. Add a TPS/fractional budget around existing actions.
4. Add same-tick independent mine/place scheduling with conflict ownership.
5. Add committed route-step movement for lanes and connectors.
6. Add transactional inventory transfers.
7. Add packet-mode optimizations only after confirmation and limiter tests pass.

Each phase should keep a setting that falls back to the current known-good Nerv
behavior.

---

# Verification checklist for the future implementation

## Rate/TPS tests

- TPS throttle disabled uses configured rates unchanged.
- TPS `20`, `15`, `10`, below `10`, invalid, and stale tick samples.
- Fractional rates `0.1`, `0.8`, `1.5`, `7.0`, and `30.0`.
- Carry is preserved over many ticks without burst overflow.
- A pause clears active slow-mine ownership safely.
- Reported units say actions/tick versus confirmed blocks/second correctly.
- Slow-mine progress follows acknowledged server/world progress at low TPS; it does
  not assume one server tick per 50 ms.

## Scheduler tests

- Mine and unrelated place can both run in one tick.
- Same-position placement waits for mining.
- Active slow mine owns its position across ticks.
- Queue refresh removes already satisfied tasks.
- Route order is stable across equal-distance targets.
- Connector indices never collapse into a straight endpoint shortcut.
- Reverse repair route resumes the exact suspended print index.

## Packet tests

- Tool/slot packet precedes mining START/STOP.
- Every configured START/STOP/ABORT sequence is asserted exactly, including true
  instamine and rebreak cases.
- Placement packet cannot consume reserved mining capacity.
- Cancelled packet is visible to the scheduler.
- Multi-packet action is atomic or retryable as one unit.
- Packet Build does not mark an unconfirmed world state complete.
- Swing suppression does not suppress required interact/dig packets.

## Hotbar tests

- Reserved slots are never selected as generic trash/replacement slots.
- Main-inventory item can be leased and exactly restored.
- Nested/concurrent leases are rejected or safely stacked.
- Tool latch invalidates on durability, enchantment, item, count, or slot change.
- Instamine classification and slow-mine progress use the same leased tool that the
  server receives.
- A non-Silk-Touch role cannot silently switch to a faster Silk Touch pickaxe.
- Placement latch invalidates when a stack is exhausted.
- The repair pickaxe remains available throughout normal printing.
- User-owned external module state is not toggled off on shutdown.

## Inventory-transfer tests

- One click per acknowledgement window.
- Full item components/NBT and count are used for verification.
- At least one pickup slot is retained before breaking a container.
- Cursor stack is recovered on success, timeout, close, and disconnect.
- Shift-click failure does not advance the restock state.
- Extracted shulker return retries are idempotent.
- A failed return keeps the item; it never unsafe-drops it.

## Movement tests

- Straight and diagonal projection math.
- Center snapping around negative and positive coordinates.
- Drift hysteresis does not oscillate.
- Mid-step replan cannot reverse direction.
- Lane-to-connector and connector-to-lane turns remain adjacent and smooth.
- Movement stops at an incomplete print boundary.
- Support/headroom failure stops before walking off.
- Local recovery is bounded before general pathfinder fallback.

## World-confirmation tests

- delayed server block update;
- ghost block reappears;
- two simultaneous slow targets retain independent confirmations/retries;
- a persistent rebreak cannot starve unrelated queued work;
- break accepted but placement delayed;
- placement packet accepted with stale client cache;
- chunk unload/reload while action pending;
- death/reconnect with partially completed U traversal;
- incorrect block found during printing and repaired in place;
- missing repair material triggers refill and shortest-route return.

## Final rule

The safest reusable THM idea is not "send more packets." It is the combination of
an ordered target window, independent action budgets, explicit position ownership,
latched inventory roles, committed movement steps, and state confirmation. Those
pieces are what allow high throughput without turning every connector, repair, or
inventory delay into a new traversal bug.
