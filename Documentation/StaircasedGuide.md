# Fullblock Printer

The Fullblock Printer builds flat & staircased fullblock maps line by line without any user interaction.
The bot mines all placed blocks again to recycle all used materials.
The printer uses a mapart platform to collect all mined blocks and feeds them into an item sorter on the north side of the map.
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
- The common row immediately north of the visible map must be a single, flat cobblestone walkway with two blocks of headroom. It does not have to share the NBT's virtual `Z=0` Y, but it must be within one walkable step of every first visible block. The add-on resolves this physical row without shifting the rendered map.
- Keep three rows south of the visible map clear. Compact circular connectors use at most these three rows.
- Components should be connected by flat, walkable lanes. With **logistics-obstacle-detours** enabled, a small wall or similar obstruction may be bypassed locally. If a complete local bypass cannot rejoin the route, the bot makes one safety-checked one- or two-block sidestep left or right and retries the destination; this is not a general long-distance pathfinder.

---

## Platform Components

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
After mining, each registered tool type is deposited into its matching single chest; the initially selected Used Pickaxe Chest is used as the fallback.

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
Place as many `1×1` NBT files as you like into this folder.

The input must be a complete MapArtCraft-style `128×height×129` structure:

- `Z=0` is the northern reference row.
- `Z=1..128` contains the visible map.
- Every X/Z shaft must contain a surface block.

The type of staircasing used in the converter (`Valley` / `Classic`) does not matter. The add-on validates the complete input, normalizes every height difference to `-1`, `0`, or `+1`, and generates the compact circular layout itself.

Columns are analyzed in pairs: `(0,1), (2,3), ... (126,127)`. The generated compact NBT contains the exact minimum-edge connector for every pair. Large endpoint differences use a two-column circular helix extending no more than three blocks south of the map.

With **save-compact-nbt** enabled, a raw source is transformed and the exact printable structure is written to `_generated_compact`. It is first written to a temporary file, reloaded, and compared against the validated plan before publication. The status display shows this compact output while the raw file remains the queue identity. When **move-to-finished-folder** is enabled, the original and its exact generated compact NBT are moved together into `_finished_maps`; existing finished files are preserved with a shared numeric suffix. Completion waits and retries if this paired move fails, so the source cannot silently be selected again. A generated compact NBT can also be selected directly: it is recognized, reconstructed, and compared against canonical geometry instead of being transformed a second time. Invalid, incomplete, entity-containing, block-entity-containing, colliding, or obstructed structures are rejected instead of being printed.

NBT files are processed in alphabetical order.

---

## Workflow

Follow these four steps:

1. Register important blocks
2. Build map
3. Create map item
4. Mine map

---

### 1. Register Important Blocks

The module prompts you to interact with all special blocks. Chests only need to be selected once, even if the rendered box highlights only half of the chest.

When finished, interact with one of the start blocks specified in the **start-blocks** setting (default: all buttons) to begin printing.
Inventory slots containing nothing or a registered material are marked for future materials.
All other slots are ignored.

---

### 2. Build Map

By default, the bot builds each column independently and returns to the north side after each one.

Enable **circular-u-traversal** to use continuous two-column U routes when safe. Before assigning a U route, the bot counts every remaining material required for both columns and their connector. A pair uses circular traversal only when the complete requirement fits in the usable inventory slots. The bot then refills before entering the pair and verifies that all counted materials are present.

If a complete pair does not fit, the connector is not built. Both columns are built independently, with a return to the north side after each column. The bot never starts a U traversal that would require a mid-route restock.

The two 128-block legs and every connector block use the printer's same normal continuous placement pipeline: ordered next-block selection, interaction-range checking, hotbar swapping, held auto-jumps, and normal block placement. The first connector step may be placed while the bot approaches the outbound endpoint. Inside the connector, placement is capped at the current hidden walking step so `rotate-place` cannot pull movement toward a later turn or the return leg. A U pair has only four unique structural checkpoints: outbound north start, outbound far/connector start, connector end/return far, and return north end. Compact helix turns use a connector-only center zone: it is wide enough for continuous movement but always smaller than the distance from a block edge to its center, even when the normal long-line checkpoint buffer is larger. Hidden turn goals are not added to or rendered as hundreds of normal checkpoints, and they do not stop or snap the player. Connector height layer, material, and headroom are still verified before each step.

If a U build is interrupted while the bot is inside the route, it follows only the already-built continuous segment back to a validated north endpoint before replanning. Recovery steering is also internal, so an interruption does not recreate a per-block checkpoint queue. If neither endpoint can be reached safely, the printer stops for manual intervention.

Changes to a multi-user build interval are deferred until the bot reaches a north endpoint, so ownership cannot change in the middle of a pair.
New slave registration is rejected while coordinated building, mining, or mining finalization is active. Removing a slave during an active build is also rejected; make normal membership changes before the next build starts.

When a material runs out:
1. The bot dumps unnecessary items into the DumpStation.
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

Before entering any U or independent fallback line, the bot checks the actual remaining durability of every required tool. The check assumes the worst case of one durability per block, applies the configured durability buffer, ignores the probabilistic benefit of Unbreaking, and preloads enough fresh tools to finish the assigned route. Empty or insufficient tool chests stop the bot before it enters the map.

When the complete U is continuously walkable, the bot:

1. Enters from the north-side starting endpoint.
2. Walks along the first column while breaking each block safely behind itself.
3. Continues over the temporary connector.
4. Returns along the second column and exits onto the north walkway.

The connector is therefore preserved until the pair is mined and is removed as part of the same continuous traversal.

Interrupted routes are recovered from world state. If a bot dies after breaking the beginning of a U, it enters from the opposite north endpoint, walks through the intact remainder to the existing break frontier, reverses, and mines safely back to that endpoint. The same logic works symmetrically if the other end was already broken.

U recovery requires one continuous remaining segment connected to at least one north endpoint. A pair without a connector uses the old independent-column mining path when each column still has a continuous expected surface, clear headroom, and a safe north entry. A partial connector, unexpected support, blocked headroom, or internal gap is not walked: the printer stops and reports the position for repair.

Mining assignments reserve both columns of a selected U, including in multi-user mode, so two bots cannot be assigned opposite sides of the same pair.

Active build and mining assignments are preserved across a normal relog and re-evaluated from current world state before movement resumes. Mining task IDs reject delayed stale commands. After mining, each slave deposits its used tools and acknowledges finalization; the master does not select the next NBT until every acknowledgement arrives.

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

