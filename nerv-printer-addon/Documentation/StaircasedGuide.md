# Fullblock Printer

The Fullblock Printer builds flat and staircased fullblock maps line by line without user interaction.
The default printing-only workflow leaves the completed map in place. The old map-item handoff and teardown cycle is available only when **printing-only** is disabled.
This module will not work on servers where placing blocks in the air is disabled.

Blocks that require support blocks (e.g., carpets) are not supported by the printer.
The only two colors not supported are two “water colors,” which require different water depths.
One of the three water colors can be supported by using glass and the same depth level in the mapart platform.
The schematic linked below uses the shallow water color.

---

## Platform Setup

A Litematica file with an example map area can be found [here](StaircasedPrinter.litematic).

You can design your own platform as long as it fulfills the following criteria:
- The setup must be north of the map area.
- The platform must be placed in a Mushroom Fields biome to avoid mob spawning.
- To build all possible maps, the main platform must have 128 blocks of empty space above and below.
    - Minimum Y-level: `-60 + 129 = 69`
    - Maximum Y-level: `320 - 129 = 191`
    - You will likely need to dig a perimeter to clear enough space.
- In printing-only mode, build one `128x1` section of the cobblestone row immediately north of the visible map (`relative Z=-1`) and leave two air blocks above it. This section must occupy one real Minecraft map tile: its west X is `128n-64`, and the map's north Z is also `128n-64`. For an odd-width grid this is the middle tile. For an even-width grid it is either middle tile; stand near the tile end that touches the intended chest seam before enabling the module. The bot preserves the canonical map grid and extends this row to the complete NBT width before entering outer columns. The resolved flat height must remain within one walkable step of every first visible block. Legacy mode still requires its complete north walkway.
- Keep three rows south of the visible map clear. Compact circular connectors use at most these three rows.
- Components should be connected by flat, walkable lanes. With **logistics-obstacle-detours** enabled, a small wall or similar obstruction may be bypassed locally. If a complete local bypass cannot rejoin the route, the bot makes one safety-checked one- or two-block sidestep left or right and retries the destination; this is not a general long-distance pathfinder.

---

## Platform Components

### Default printing-only setup

Only the DumpStation, optional bed, and one shulker-line anchor are selected manually. The map area is detected from the canonical `128x1` north cobblestone map tile; right-click map-area registration is disabled in printing-only mode. The shulker anchor is simply the first shulker at either end of the placed supply line. Cartography tables, finished-map chests, map-material chests, used-tool chests, anvils, ender chests, crafting tables, and every remaining material/tool shulker are not manually registered.

After the DumpStation and optional bed are selected, click the first shulker at either end of the supply line. That click both registers the anchor box and centers discovery. The bot searches loaded chunks within **automatic-shulker-scan-radius** of that anchor and strictly from anchor `Y-2` through `Y+2`; it never performs an all-height scan. The default radius is `80`, and it can be set to `64` when the line is shorter. It finds the other placed shulkers from loaded block-entity data and walks down the line. For every shulker, only its exact south-side interaction position is registered: the same X and Y as the shulker and world `Z+2`, leaving one complete empty block at `Z+1`. This offset applies only to shulkers. Every standing position requires a solid floor, two blocks of headroom, and interaction reach. The bot automatically registers only supplies relevant to the loaded NBT: required build items, tools, end rods, and golden apples. Either a normal golden apple or an enchanted golden apple satisfies the apple requirement. If a required source is still missing, the same bounded scan repeats every 40 ticks, and known stations are periodically reopened to refresh changed contents. Clicking a configured Start Block while registration is incomplete forces that bounded rescan immediately. The diagnostic reports loaded-chunk coverage and exact inaccessible shulker coordinates. At the end, the bot reports required and currently visible counts and refuses to start if a required material type, usable pickaxe, usable axe, or either accepted golden-apple variant was not found. Stock hidden inside a dispenser cannot be counted, so the initial check proves source types rather than the total quantity held by dispenser backups.

Once that complete check succeeds, the bot atomically saves the detected map corner, DumpStation, optional bed, shulker-line anchor, and every discovered shulker/open position to `<map folder>/_configs/printing-only-shulker-line.json`. On every activation it redetects the current map corner and height from the canonical cobblestone map tile; with the same server, dimension, and map-grid dimensions it then loads the saved logistics positions and revisits every saved shulker to rebuild the item/tool registry from current server container snapshots. Old item counts are never trusted, so emptied or replaced boxes are detected before a new NBT starts. If the server, dimension, or grid size differs, the bot asks only for the unsaved logistics selections after automatic map detection. Use **Reset Printing Config** in the module widget to delete the saved setup; an active session remains usable, but the next activation automatically detects the map and then asks for the dump station, optional bed, and first shulker again.

Keep one replaceable supply type per shulker station. A material may have multiple stations. The bot always keeps one usable registered pickaxe and one usable registered axe in its printing inventory, obtaining either from its shulker source when absent. A pickaxe or axe below **minimum-tool-durability** is not considered usable.

Each replaceable station may have a dispenser arranged to place its next full shulker at exactly the same coordinate. After a server-authoritative inventory snapshot proves that the current shulker is completely empty, the bot closes it, equips its usable pickaxe, breaks the empty box, and waits up to **shulker-replacement-timeout** ticks for a server block update confirming the replacement. It then reopens that exact station and continues refilling. The dispenser/redstone mechanism must trigger from the break and must not move the replacement to another coordinate. The bot never breaks a mixed or partially filled shulker merely because one requested item is absent.

### Legacy handoff/teardown components

The components below are used only when **printing-only** is disabled.

### DumpStation
The bot throws all blocks it no longer needs at this position.
Ideally, use a water stream to pipe them back into the sorter.

### Cartography Table
Used to lock created maps.

### FinishedMapChest
The bot puts finished maps here.

### MapMaterialChest
This chest should contain empty maps and glass panes for locking new maps.

### ToolChests
Chests containing the tools required to mine the built map.
Registration records the strongest Efficiency level found for each tool item. During printing, the baseline carry plan takes one registered pickaxe and one registered axe. An exact-item tool already carried or present in its source chest is reusable while at least the configured **minimum-tool-durability** remains (`10%`, and never configurable below that floor). If a wrong active-U block is already known, the selected repair tool must also match that block and the saved Efficiency profile.

Recommended:

- Pickaxe
- Axe
- Shovel

Optional (rarely used):

- Shears
- Hoe

### UsedToolChests
Single chests containing one tool type can be registered as typed used-tool destinations.
For example, a single chest containing shears is registered as the Shears Used Tool Chest.
Double chests containing tools remain source Tool Chests.
Before a build refill, reusable tools reserve their managed slots and are never sent to the material DumpStation. A carried tool is replaced only when it falls below **minimum-tool-durability**. That exact slot is deposited into its matching single used-tool chest; the initially selected Used Pickaxe Chest is a compatibility destination only for pickaxes, while axes and other tools require their matching typed used-tool chest. Deposits are slot-specific, so a critical and a healthy tool of the same type are not moved together. The printing carry plan requests only one pickaxe and one axe, unless a wrong active-U block requires another registered tool type. After mining, all remaining registered tools are returned to used-tool storage. Tool stacks are excluded both by the dump planner and by the authoritative dump transaction itself, so a tool is never thrown into the material DumpStation.

### BuildMaterialChests
These chests contain the actual mapart materials used for building.
They are connected to a sorter, which should be efficient and include overflow protection (e.g., a dolphin).

### Optional Utility Blocks
During chest registration, interact with an Anvil, Ender Chest, or Crafting Table to save its position in the configuration.
These positions are reserved for utility workflows such as future filled-map naming.

### Bed (Optional)
Used by bots when starting a new map to avoid phantom spawning.

---

## Loading NBT Files

When the module is started for the first time, a `nerv-printer` folder is created in your Minecraft directory.
Place one combined NBT to print in this folder. The printer detects its complete grid and updates **map-columns** and **map-rows** automatically; the settings no longer need to be matched manually before loading.

The input must be one complete, contiguous structure. For a grid with `C` map columns and `R` map rows, its declared size can be either the exact visible footprint or the legacy MapArtCraft reference-row form:

- X/width: `128 × C`
- Exact Z/depth: `128 × R`
- Legacy Z/depth: `128 × R + 1`, containing one shared northern reference row
- For example, a `2×2` single NBT can be `256×height×256` or `256×height×257`; a `5×5` single NBT can be `640×height×640` or `640×height×641`.

- In exact-size input, every row is visible; the loader preserves the first row and synthesizes a copy as the internal northern reference row.
- In legacy input, `Z=0` is the northern reference row and `Z=1..(128 × R)` is the visible mosaic.
- Every X/Z shaft must contain a surface block.

The type of staircasing used in the converter (`Valley` / `Classic`) does not matter. The add-on validates the complete input, normalizes every height difference to `-1`, `0`, or `+1`, and generates the compact circular layout itself.

For `1×1`, columns are analyzed in pairs and may use compact U connectors. A multi-map mosaic is retained after printing, so it uses restock-safe independent columns from the common north walkway and requires **printing-only**.

### Printing-only and station alignment

**printing-only** is enabled by default. After the final authoritative print/repair check, the bot stops and disables itself. It does not create or lock a map item, deposit a finished map, tear down the structure, archive the NBT, or select the next NBT. The source NBT remains in the input folder; move it manually before enabling the module again if it should not be selected again.

The bot derives the north-west edge of the entire contiguous mosaic from the canonical `128x1` cobblestone map tile. Center the north chest/station section at:

`station world X = detected north-west map X + (128 × map-columns) / 2`

Every individual map tile is always `128×128`; the division by two is used only to find the center of the complete chest-facing edge.

For an odd width such as `5×7`, the anchor is the middle map column and the station is at its center. For an even width such as `6×10`, the anchor is one of the two middle map columns and the station is at the adjoining middle seam. Stand near that adjoining end so the detector chooses the intended canonical half. The automatic-detection message reports the exact world X. Chest contents, material/tool registration, restocking, dumping, and repair inventory behavior are otherwise unchanged.

### Suspended end-rod lighting

Every generated compact/U-traversal schematic includes upright end rods suspended exactly four blocks above their local map-surface block. In the default **printing-only** workflow, these rods are normal required build targets: they are included in material demand, shulker restocking, authoritative placement confirmation, and the final repair scan. Provide an automatically discoverable end-rod shulker station before starting the print. As with the other map blocks, the server must permit the add-on's direct placement into air.

End rods emit block light level `14`. On flat terrain, the generator uses a `10×10`-block spacing pattern, which accounts for the three-block vertical light path from each suspended rod to the spawn-space block directly above the map. Staircases and cliffs can make a simple horizontal grid unsafe, so the generator tests a clear three-dimensional air path to every one of the map's `128×128` cells per tile and inserts extra rods only where needed. Generation then exhaustively rejects the plan unless every surface spawn-space cell is guaranteed to receive at least block light level `1`; no cell is allowed to remain at block light `0`.

This prevents ordinary darkness-dependent hostile spawning on the map surface. It does not change spawning rules for entities that ignore block light, and nearby outside structures can still need their own spawn-proofing.

With **save-compact-nbt** enabled, a raw source is transformed and the exact printable structure is written to `_generated_compact`. It is first written to a temporary file, reloaded, and compared against the validated plan before publication. The status display shows this compact output while the raw file remains the queue identity. When **move-to-finished-folder** is enabled, the original and its exact generated compact NBT are moved together into `_finished_maps`; existing finished files are preserved with a shared numeric suffix. Completion waits and retries if this paired move fails, so the source cannot silently be selected again. A generated compact NBT can also be selected directly: it is recognized, reconstructed, and compared against canonical geometry instead of being transformed a second time. Invalid, incomplete, entity-containing, block-entity-containing, colliding, or obstructed structures are rejected instead of being printed.

NBT files are processed in alphabetical order.

---

## Workflow

With the default **printing-only** mode, only Steps 1 and 2 run. Disable it only for the legacy `1×1` handoff-and-teardown workflow.

Follow these four steps:

1. Register important blocks
2. Build map
3. Create map item
4. Mine map

---

### 1. Register Important Blocks

In the default printing-only mode, stand near the intended station/seam end of the canonical `128x1` north cobblestone map tile and enable the module. The bot detects the full map area without a click. Throw an item at the DumpStation prompt, select the bed only if sleeping is enabled, and click the first shulker at either end of the supply line. That is the only shulker selected manually; the bot discovers, visits, opens, and registers the rest of the line automatically. Do not register a cartography table, map chest, finished-map chest, used-tool chest, anvil, or individual material chest. When the scan reports complete coverage, interact with a configured start block (all buttons by default) to begin.

In legacy mode, the module prompts you to interact with its special blocks and chests as before.

When finished, interact with one of the start blocks specified in the **start-blocks** setting (default: all buttons) to begin printing.
Inventory slots containing nothing, a registered material, or a compatible registered repair tool are marked for the managed printing inventory.
All other slots are ignored.

---

### 2. Build Map

By default, **circular-u-traversal** builds a complete two-column U route. Before entering a U, the bot plans every remaining block for both columns and their connector, plus one usable instance of every tool needed by an already detected wrong block. It also carries one registered pickaxe and axe for repairs discovered after refill. At every safe north endpoint it rebuilds the future traversal set: already-complete U routes are omitted, while a sparse air-only U may be assigned to a later U only when every remaining target is reachable from that later ordered support path and the combined mandatory demand still fits. It consolidates and refills the usable inventory before leaving the endpoint, then rechecks the live inventory at entry. It never enters a U that would require a mid-route material restock.

With the default **require-complete-u-inventory** setting, the printer stops safely when the complete U guarantee cannot fit instead of silently starting a weaker plan. Disable that setting only if independent-column fallback is wanted for a pair that cannot fit. A partial connector is never treated as a valid fallback.

Printing and ordered teardown share a real-time action policy controlled by **max-block-actions-per-second** (maximum `30`). With **scale-block-rate-with-tps** enabled, the ceiling is multiplied by measured server TPS divided by `20`; for example, `15 TPS` permits at most `22.5` new block-action attempts per second over time. Printing, in-route repair, and ordered teardown pause below **minimum-block-action-tps**, on invalid TPS, or when the latest server tick is more than 1.5 seconds old. The limit covers placement plus new/retried repair and teardown break dispatches. The scheduler can submit up to three actions after a delayed client tick so short client-tick jitter does not discard valid real-time credit; it still cannot exceed the TPS-scaled rate over time or accumulate an unbounded burst. Confirmed BPS can be lower when fewer independently placeable targets are inside the five-block reach or the server has not accepted them.

The server remains authoritative. A placement is not considered complete until a newer block update confirms the expected block. During printing, managed hotbar swaps and container transfers deliberately request a complete authoritative inventory resynchronization; the printer does not use the result until that newer server snapshot confirms it. Swap confirmation compares canonical item/component snapshots structurally, with count and monotonic tool damage checked separately, so equivalent component maps received in different packets cannot turn a successful exchange into a false conflict. Transfers are performed one at a time and recalculated after each confirmation. Every shulker slot is rescanned and partial stackable sources are left alone while the supplier finishes filling them; source selection rotates across ready full-stack or non-stackable slots. Restock progress is confirmed from the compatible item count that actually reached the managed player inventory. A completely empty auto-registered shulker enters the break-and-replacement sequence described above. If the box is not empty but no requested source slot is ready, the bot waits without another inventory click, observes live slot updates, and periodically reopens that exact station with only one probe in flight. The advanced **restock-refill-timeout** setting controls this bounded wait before another registered source is tried; `0` disables waiting.

Printing requires all nine managed hotbar slots. Before the refill trip, the frozen plan reserves the ordered U demand and any earlier sparse-U targets assigned to this traversal, then fills remaining managed inventory capacity with forward work. Its first eight ordered material-stack units are assigned to the material hotbar slots; additional planned stacks remain in their exact main-inventory slots until the acknowledged silent-swap controller needs them. The ninth hotbar slot is reserved exclusively for a compatible pickaxe or axe; when no repair tool is required, inventory capacity still reserves the empty tool slot. At the pair-entry checkpoint, the bot preserves already-correct stacks and performs only the planned main-inventory-to-hotbar `SWAP` packets. These preparation swaps do not change the visibly selected slot, and each exchange must be confirmed by a newer authoritative inventory snapshot before the next exchange is sent. The selected slot changes only when the corresponding planned material or repair tool is actually used. Switching between a pickaxe and axe exchanges the required tool into the reserved tool slot and returns the previous tool to that exact source inventory slot. Material replacement never targets the tool slot. A missing planned source detected in the authoritative entry snapshot prepends the complete dump/restock transaction and retries preparation instead of evicting an arbitrary hotbar item or stopping the module.

Enable the advanced **debug-prints** setting for structured `[Debug][Category]` diagnostics. The messages include the client tick and module state, with detailed categories for state changes, checkpoints, interactions, action-budget health, placement and block acknowledgements, hotbar swaps, tool durability, restocking, dump transfers, used-tool deposits, repairs, teardown, and temporary Speed Mine ownership. Restock messages report the handler sync ID, requested item, every non-empty container slot classified as ready/partial/ignored, rotating source cursor, player demand and capacity, before/after confirmation counts, retry attempt, refill deadline and probes, handler rebinding, timeout, and cleanup. Repeated active-U movement waits are coalesced into one hold message and one later resume or jump-transition message instead of printing every tick. Healthy action-budget diagnostics are summarized once per second and include measured submitted and confirmed BPS for placements, repairs, and teardown, their cumulative totals, and a per-action breakdown, while pause/recovery changes print immediately. Debug mode remains disabled by default.

The active U is always the primary placement and inventory plan. **interaction-range** is capped at the bot's real five-block reach and is measured from its eyes to the target block center. With **nearby-range-placement** enabled, there is no fixed four-column inventory cap. After reserving the complete active U, any mandatory targets deferred from a skipped sparse U, its repairs, and retained-tool slots, the frozen plan offers every genuinely missing target from every later assigned route—in exact outbound, connector, and return order—to the slot-capacity planner. Already-correct future blocks consume no optional capacity, wrong future blocks remain reserved for repair when their U becomes primary, and missing cobblestone connector blocks participate like every other forward target. Admission stops only when the managed slots are full or no forward target remains. The plan log reports occupied/usable slots, unused slots, admitted/candidate forward targets, connector targets, and mandatory earlier-U targets. Departure compares authoritative on-hand counts with this complete frozen demand, not only the active U; any shortfall prepends restocking before travel toward the selected later route. Mandatory shortages are restocked first and remaining capacity is filled for forward work. An unavailable optional source is abandoned after the bounded chest search while an unavailable mandatory material still stops safely.

During printing, the scheduler scans the complete ordered U and spends the current TPS-scaled budget on every independently placeable U target inside reach, including later forward targets when an earlier target is not yet anchored. U decisions always consume the tick budget first, followed by mandatory earlier-U targets assigned to this traversal, then optional forward surplus. A target counts as actionable only when it is in reach, placement-eligible, backed by an available material, and not already in the confirmation ledger. Therefore already-submitted U packets neither suppress the strict-surplus forward pass nor recreate a global movement wait. If no additional mandatory target is currently actionable, any inventory-backed forward surface or connector target currently inside reach may use the leftover budget, even while an earlier U packet awaits acknowledgement, and can consume only strict material surplus above the complete mandatory reservation. A server-confirmed forward placement is remembered for the rest of that build run. When its route later becomes primary, that exact target is removed from strict refill demand only while the newest observation still contains the expected block; a changed or missing target immediately re-enters demand. Primary and forward placement acknowledgements normally never zero horizontal velocity, and movement and held auto-jumps continue while those independent packets are pending. A skipped sparse U is different: each of its mandatory remaining targets has a precomputed conservative reach window. Its pending acknowledgement does not hold movement anywhere earlier in that window; the bot waits only on entry to the final conservative reach support, leaving one support of acknowledgement margin before the target can be lost. This prevents an unrelated pending target from canceling an in-progress stair jump and forcing a second jump at the same block. Moving beyond a fresh optional packet does not cause immediate recovery: reach is checked again only when its bounded retry becomes due. An optional out-of-reach retry is returned to normal planning, while a required U retry enters ordered recovery. A mandatory repair or material-swap transaction may hold movement, and the exact next ordered U support must still be server-confirmed before the bot can step onto it. An inventory containing exactly the mandatory requirement cannot place an extra-route block. The newest authoritative server observation is also used ahead of the slower area cache so an already-confirmed placement is not immediately submitted again.

The circular start keeps the master's distinct start/end checkpoint contract but adds a dedicated approach position. The initial `preparePair` alignment is one block north of the validated cobblestone walkway: relative `Z = -2` for the alignment, `Z = -1` for the walkway, and `Z = 0` for the first print target. The alignment block must be solid and both headroom blocks must be air; otherwise the printer stops before activating the pair. The return endpoint remains the exact north walkway support on the other U column.

Raised and descending route blocks use the real map-block placement pipeline; the printer never creates a temporary support. Ordinary full blocks whose vanilla placement state does not depend on player yaw or pitch—such as concrete, deepslate, cobblestone, glass, leaves, and vertical logs—use the THM-style direct air-placement packet even when an adjacent face exists. They do not rotate, enter a rotation callback, or wait for the bot to look at the target. Vanilla states carrying player-facing placement properties (`facing`, `horizontal_facing`, `orientation`, or standing `rotation`) retain the adjacent placement path when a confirmed face exists and may rotate when **rotate-place** is enabled. Frozen, inventory-backed targets without a face may use the same direct packet path; arbitrary unplanned targets may not. Both modes consume the same TPS-scaled action budget, use the same managed material slot, and remain pending until a newer server block update confirms the expected map block. A rotation callback re-selects its own planned material immediately before interaction, so another budgeted placement cannot change it first. The placement scheduler remains active during the normal held auto-jump interval, so climbing never creates five ticks with unused placement capacity. A pending placement elsewhere in the U no longer serializes walking, but the exact next ordered walking support always must contain the expected server-confirmed block with clear headroom before forward input is allowed. Support readiness distinguishes a missing or pending floor from a wrong floor and from each of the two blocked headroom cells. A headroom obstruction that appears after pair preflight is claimed as clear-only active-U repair, mined through the same owned THM controller, and must receive a newer authoritative air update before movement resumes. If its registered tool was not carried by the frozen plan, the bot returns to a safe endpoint, includes it in the rebuilt tool demand, and restocks. It never converts a permanent headroom obstruction into an unbounded `NEXT_ROUTE_SUPPORT_CONFIRMATION` wait. This support guard covers the approach walkway, outbound leg, connector, return leg, and exit walkway, so the bot cannot place one block and continue over an unconfirmed void.

Vertical walking follows the original Nerv printer model instead of classifying individual slopes. Ordered route progress is determined from the player's horizontal map cell only; player Y and `onGround` do not decide route progress. Vanilla gravity follows any lower next cell, while the normal solid-ahead plus two-block-headroom rule owns auto-jumping onto any higher next cell. The cursor may remain in its current cell or advance to its immediate cardinal successor, whose generated height can differ by at most one block; it cannot skip cells or jump to the nearby return leg or another helix level. Once a raised first block is confirmed, the same normal held auto-jump behavior handles it.

Restart and reconnect recovery recognize the one-block-back alignment and both north walkways as external safe replanning supports. If the bot restarts after preprinting several reachable blocks while it is still standing on the alignment, it rebuilds the frozen inventory and checkpoint plan directly instead of treating the new alignment as an unknown location. A normal server position correction that leaves the bot over the same outbound or return U cell resumes from the retained horizontal route cursor, even if the correction reported another Y; missing support is then handled by the normal confirmed placement pipeline. A full restart or inventory-loss recovery from an actual U block still uses the bounded, already-built egress path before replanning.

When **repair-current-u-pair** is enabled, a wrong support block is repaired only if it belongs to the U currently being printed. Unexpected blocks occupying that active U's required two-block player headroom are clear-only repair targets: they are mined and confirmed as air but are not replaced. The bot keeps one compatible registered tool while it remains at or above **minimum-tool-durability**, waits for the server to confirm that a mined block became air, and replaces only wrong support targets through the normal confirmed placement pipeline. With **thm-instant-repair**, the printer temporarily snapshots and configures Meteor Speed Mine for THM-style Damage/instamine behavior on that repair target, then restores every prior Speed Mine setting when the repair, recovery, pause, disconnect, or module run ends. If true instamine is not valid for the selected tool and block, normal progressive mining continues safely.

The two 128-block legs and every connector block use the printer's same normal continuous placement pipeline: ordered next-block selection, interaction-range checking, hotbar swapping, held auto-jumps, and normal block placement. The first connector step may be placed while the bot approaches the outbound endpoint. Inside the connector, placement is capped at the current hidden walking step so `rotate-place` cannot pull movement toward a later turn or the return leg. A U pair has only four unique structural checkpoints: outbound north start, outbound far/connector start, connector end/return far, and return north end. Compact helix turns use a connector-only center zone: it is wide enough for continuous movement but always smaller than the distance from a block edge to its center, even when the normal long-line checkpoint buffer is larger. Hidden turn goals are not added to or rendered as hundreds of normal checkpoints, and they do not stop or snap the player. Connector height layer, material, and headroom are still verified before each step.

If a U build is interrupted while the bot is inside the route and cannot safely resume the retained outbound/return cell, it chooses the shorter safe continuous segment once and follows that single direction to its validated north endpoint before replanning. Recovery uses the same horizontal immediate-cell cursor as normal printing, so gravity and generic auto-jump handle mixed heights without a 3D point goal reversing after the player crosses a block center. Recovery steering is internal and does not recreate a per-block checkpoint queue. A missing outbound support cannot insert an ad-hoc forward/backward checkpoint loop; it enters this same ordered recovery contract. If neither endpoint can be reached safely, the printer stops for manual intervention.

Changes to a multi-user build interval are deferred until the bot reaches a north endpoint, so ownership cannot change in the middle of a pair.
New slave registration is rejected while coordinated building, mining, or mining finalization is active. Removing a slave during an active build is also rejected; make normal membership changes before the next build starts.

When a material runs out:

1. The bot routes insufficient tools to used-tool storage and dumps only unnecessary non-tool items into the DumpStation.
2. Restocks needed block.
3. Continues building

#### Logistics obstacle detours

With **logistics-obstacle-detours** enabled, the bot watches its progress while travelling to registered chests, the dump station, bed, cartography table, finished-map chest, and the north access path. If it remains grounded but makes no meaningful horizontal progress for about 30 ticks, it searches within **logistics-detour-radius** for a short fixed-height route around the obstruction. A left bypass wins an otherwise equal choice; the right side is used when needed.

Only currently loaded, solid, non-hazardous cells with clear player headroom are considered. The map footprint and its safety margin are excluded, so this feature never changes the exact U-shaped printing or mining traversal. If the bypass changes while it is being followed, the bot replans once. It then stops with an error instead of wandering if no bounded safe route exists.

---

### 3. Create Map Item

When the map is finished:

1. The bot takes an empty map and a glass pane from the MapMaterialChest.
2. It creates a map in the center of the Map Area.
3. It locks the map at the Cartography Table.

---

### 4. Mine Map

For every pair, the bot checks the actual blocks immediately before assigning its mining route.

The nearby placement planner and active-U repair planner apply only during Step 2. Step 4 teardown keeps its separate ordered U/independent mining traversal and tool-restock logic; it does not expand mining to arbitrary blocks within five-block reach.

Immediately before entering any U or independent fallback line, the bot verifies two compatible registered pickaxes and one compatible registered axe. Carried and chest tools use the same **minimum-tool-durability** floor (`10%`): the exact boundary is accepted, and only a missing or below-floor tool creates a replacement demand. Every below-floor pickaxe or axe is first transferred from its exact inventory slot to the matching Used Pickaxe or Used Axe Chest; its newly freed slot is then used by restock. A 60%-remaining tool therefore remains in service. Once the U is entered, this percentage threshold is frozen for that traversal: the bot does not issue durability warnings, retire a tool, or request another tool in the middle of the U. It may silently switch between the two preloaded pickaxes and the axe as blocks require, and it checks the 10% entry contract again only before the next traversal. Teardown also reserves **teardown-scaffold-stacks** of cobblestone (`3` by default, configurable from `2` to `3`). Partial carried stacks count toward that exact reserve and their free stack capacity is used before the capacity planner requests another inventory slot. A registered cobblestone chest and enough managed capacity are required before the bot enters teardown, so sparse recovery never begins by hoping that material remains.

After the strict tool restock is server-confirmed, the entry checkpoint assigns the two pickaxes and axe to three concrete hotbar slots and silently fills missing assignments with the same acknowledged packet-swap controller used during printing. Existing correct tools remain in place. During teardown, the block-specific tool selector uses the frozen compatible loadout and may swap only within its preassigned same-item slots. Falling below 10% during that U does not trigger a replacement; threshold retirement is deferred until the next traversal entry plan. There is no arbitrary first-slot eviction path.

Ordered teardown uses the same **max-block-actions-per-second** TPS-scaled ceiling as printing, but it never adds an unordered spatial breaking scan. At every safe assignment boundary it proves a complete monotonic support schedule from every candidate walking U to every target in every other unfinished U, both earlier and later in map order. Host selection is global: it first keeps any mandatory recovery U, then repeatedly chooses the U covering the greatest number of still-uncovered lanes and removes a previously selected nonmandatory host if later selections make it redundant. Thus U 1 may clear U 2 and U 3, U 2 may instead win because it clears both U 1 and U 3, or a later U may win when it covers the most lanes. Every skipped U is assigned wholly to exactly one selected host; partial reach never qualifies. The proof retains 0.20 blocks of positional margin, preserves endpoint-safe removal order, and every scheduled action still passes the live five-block range and underfoot/future-support checks. A partially completed remote route remains an ordered prefix/suffix recovery shape rather than arbitrary missing blocks.

Exactly one selected teardown block is owned at a time. With **thm-instant-teardown**, the bot snapshots Meteor Speed Mine once for the active teardown assignment, keeps the lease active between consecutive targets, and changes its whitelist only when the required block type changes. Only a true vanilla instant break is treated as batch-instant; Speed Mine acceleration remains an owned progressive mine. Initial and retry dispatches consume rate credit, progressive continuation keeps the same target, and the route advances only after a strictly newer authoritative server update confirms air. The previous Speed Mine configuration is restored on TPS pause, assignment completion, recovery, disconnect, phase change, or deactivation.

Mixed-height teardown movement retains the ordered segment direction after crossing a support center, so gravity can finish a one-block descent without a 180-degree correction toward the point just crossed. Its forward steering point remains ahead of the player even after a large checkpoint overshoot, so the committed segment cannot turn into a target behind the bot. Auto-jump is evaluated against that exact ordered support rather than whichever obstacle happens to be in the current yaw direction. An ordered teardown jump remains inside the normal tick pipeline: steering, block acknowledgements, mining ownership, and the movement watchdog continue while jump input is held, matching the continuous build scheduler. A one-block ascent keeps forward/jump movement until the player is stably supported by the raised route cell.

A server-confirmed vanilla-instant break behind the bot, including a reach-scheduled target, may await its authoritative air acknowledgement while movement continues toward the next verified support. If that acknowledgement is still absent at the next incomplete route boundary, movement holds there rather than abandoning ordered safety. The final ordered support is always a hard horizontal stop: forward, sprint, and jump input are released and horizontal velocity is zeroed while the last progressive break or air acknowledgement completes, so the bot cannot run beyond the U endpoint. A one-support server position correction is reconciled backward only when that exact previous support is still authoritative and safe. Any larger route mismatch automatically enters stable-ground teardown recovery instead of deactivating the module; unsafe positions still fail closed during that recovery. Progressive mining retains its movement barrier because moving could abandon the owned mine. If a grounded route step makes no meaningful progress for two seconds, the bot logs the player, previous support, target support, and queue size, then reconstructs the remaining path from the authoritative U support under the player. It does not remain indefinitely in `MiningUTraversal` with no diagnostic.

After ordinary assignments finish, the master performs a fresh authoritative scan of every map target and connector; an earlier local or slave “mined” report cannot hide a block that reappeared or whose air acknowledgement was lost. A sparse leftover does not become an unordered five-block breaker. The planner compares both safe north endpoints, chooses the shortest unobstructed half-U sortie, and uses carried cobblestone only for air cells before the farthest reachable missed block. The terminal missed block is always broken from the preceding confirmed support, never from under the player. Cobblestone is placed through the same direct, non-rotating air-placement pipeline and TPS-scaled action budget as ordinary printing, with bounded retries and newer server-update confirmation. At the target, the route reverses and the normal THM teardown owner clears the missed original blocks and every temporary support behind the bot while it walks back to the north walkway. If more sparse groups remain, the authoritative scan plans another bounded sortie.

Sparse recovery is restart-safe from world state. Existing cobblestone on the planned half-U is treated as an owned temporary support only when the NBT has a target at that cell, the block is solid, and both headroom cells are clear. If Start / Continue finds the bot grounded on that scaffold with the complete reserve and tools, it resumes from that exact support; when grounded on the terminal block it first steps backward and then clears it. If tools or material need restocking, it walks only backward across the intact scaffold to the selected north endpoint before replanning. It never steers diagonally from a temporary support toward a logistics checkpoint.

When the complete U is continuously walkable, the bot:

1. Enters from the north-side starting endpoint.
2. Walks along the first column while breaking each block safely behind itself.
3. Continues over the temporary connector.
4. Returns along the second column and exits onto the north walkway.

The connector is therefore preserved until the pair is mined and is removed as part of the same continuous traversal.

Interrupted routes are recovered from world state. If a bot dies after breaking the beginning of a U, it enters from the opposite north endpoint, walks through the intact remainder to the existing break frontier, reverses, and mines safely back to that endpoint. The same logic works symmetrically if the other end was already broken.

Normal U recovery requires one continuous remaining segment connected to at least one north endpoint. A pair without a connector uses the old independent-column mining path when each column still has a continuous expected surface, clear headroom, and a safe north entry. An unexpected foreign support or blocked headroom is never walked. Internal air gaps left after the normal routes are complete are handled only by the bounded cobblestone scaffold recovery described above; they are not accepted as ordinary U supports.

Mining assignments reserve both columns of a selected U, including in multi-user mode, so two bots cannot be assigned opposite sides of the same pair.

Active build and mining assignments are preserved across a normal relog and re-evaluated from current world and inventory state before movement resumes. Persisted teardown recovery waits for two consecutive grounded observations of the same support before deciding between verified local-U continuation and endpoint entry; a transient first client tick therefore cannot send a bot away from the U support under it. Any in-flight placement, break, swap, restock, dump, or tool-deposit ownership is abandoned first and rebuilt from newer server snapshots. A death or reconnect during map handoff waits for a newer full player-inventory snapshot before rebuilding the durable stage. A teleport or manual resume first opens the exact registered finished-map chest as a read-only authoritative inventory probe, so a locally predicted map transfer can never advance handoff or start teardown. An interrupted post-mining tool deposit restores the exact popped chest checkpoint before continuing. Mining task IDs reject delayed stale commands. After mining, each slave deposits used tools one at a time and waits for an authoritative source-slot change after every transfer; the master does not select the next NBT until every acknowledgement arrives. Dump-station throws are likewise whole-stack, forced-full-sync transactions and are not considered complete until the server confirms that exact player slot is clear.

Solo and chat-coordinated runs also write an atomic local lifecycle checkpoint at `<map folder>/_staircased_state/<player>_active_cycle.json`. It records the logical job/generation, source/config/compact-plan hashes, server, dimension, map corner, lifecycle phase (`BUILDING`, map handoff/deposit, `MINING`, verified clear, or post-mining), exact map-handoff IDs/stage, runtime state, and the active teardown pair/support index when one is authoritative. The checkpoint is refreshed at phase/assignment boundaries and once per second while active, and is removed only after post-mining cleanup commits the cycle to `IDLE`. A malformed or mismatched checkpoint fails closed; it cannot silently fall through into a new build.

On restart, the persisted phase owns recovery before the HUD Start / Continue action can start new work. Transient movement and packet actions are never replayed from disk. The bot freezes horizontal input, loads the exact NBT/config identity, then reconstructs the current teardown cursor from the verified support under the player and the current server block states. If a pre-checkpoint legacy run is already half torn down, it may be adopted only when the ordered world shape proves teardown—an air prefix followed by one continuous remaining U suffix with the player standing on that suffix. A built prefix followed by air is construction state and is never reclassified as teardown.

Slave removal is a two-phase operation. The first click sends an immediate removal request but keeps any mining assignment reserved until that slave acknowledges that it has stopped. A slave that is definitely offline can be force-released by clicking remove again after the 10-second safety delay. Its removal record remains stored, so it will still be detached if it reconnects later. Do not force-release a bot that may still be online and mining.

When multiple bots are used:
- They request the next leftmost line to mine from the master bot.
- This prevents item loss caused by items falling onto neighboring lines.

After the entire map is mined, the process returns to **Step 2**.

Demo video:

[![Staircased Printer](https://img.youtube.com/vi/SLwqRpoV7jY/0.jpg)](https://www.youtube.com/watch?v=SLwqRpoV7jY)

---

## Optional Features

### Save and Load Configurations

In the default printing-only workflow, setup is saved automatically after the complete NBT material/tool check. Use **Reset Printing Config** to discard it. The manual controls below are for the legacy workflow when **printing-only** is disabled.

To save a configuration:
1. Register blocks as usual.
2. Press **Save Config** in the module settings.
3. Select a file to overwrite or choose a new file name.
It will be a JSON file.

To load a configuration:
1. Press **Load**.
2. Select a config file.
3. Start the print as usual.

---

### Multi-User Printing

The printer can coordinate multiple accounts to print on the same map area.

For multi-instance coordination through shared JSON files instead of direct
messages, see [Fullblock Printer File Coordination](FullblockFileCoordination.md).
The setup below describes the original chat/DM mode.

- One bot acts as the **master**.
- Other bots act as **slaves**.
- Communication occurs via direct messages.

#### Setup

1. Adjust the prefix and suffix in the settings.
Most servers use third-party DM plugins with varying syntax.  
Configure the Multi-User settings accordingly.
Incoming DMs should follow this format: `(prefix)(sender's name)(suffix)(message)`
2. Enable the module and load the configuration on **every** bot.
3. Move all slave bots within render distance of the master.
4. Press **Register** using the master account.
An **Accept** message should appear for each slave.
5. Start the print as usual.

Column assignments are always aligned to complete even/odd pairs, so a compact connector is never split between two bots. Each bot performs the inventory-fit decision for its own assigned pairs.

