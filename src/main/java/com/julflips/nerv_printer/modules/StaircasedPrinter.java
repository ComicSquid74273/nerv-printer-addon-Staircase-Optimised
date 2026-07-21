package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.interfaces.IClientPlayerInteractionManager;
import com.julflips.nerv_printer.interfaces.MapPrinter;
import com.julflips.nerv_printer.utils.*;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StaircasedPrinter extends Module implements MapPrinter {
    private static final String FILE_META_MODULE = "module";
    private static final String FILE_META_JOB_ID = "jobId";
    private static final String FILE_META_GENERATION = "generation";
    private static final String FILE_META_SOURCE_NBT = "sourceNbt";
    private static final String FILE_META_SOURCE_SHA256 = "sourceSha256";
    private static final String FILE_META_PRINTING_NBT = "printingNbt";
    private static final String FILE_META_ARCHIVED_SOURCE_NBT =
        "archivedSourceNbt";
    private static final String FILE_META_ARCHIVED_PRINTING_NBT =
        "archivedPrintingNbt";
    private static final String FILE_META_CONFIG_SHA256 = "configSha256";
    private static final String FILE_META_PLAN_SHA256 = "compactPlanSha256";
    private static final String FILE_META_CIRCULAR = "circularTraversal";
    private static final String FILE_META_SERVER = "server";
    private static final String FILE_META_DIMENSION = "dimension";
    private static final String FILE_META_MAP_CORNER = "mapCorner";
    private static final String FILE_META_PLAYER_X = "playerX";
    private static final String FILE_META_PLAYER_Y = "playerY";
    private static final String FILE_META_PLAYER_Z = "playerZ";
    private static final String FILE_META_MAP_DATA_LOADED = "mapDataLoaded";
    private static final String FILE_META_PHASE = "phase";
    private static final String FILE_META_RUNNING = "running";
    private static final String FILE_META_ACTIVE = "active";
    private static final String FILE_META_READY = "ready";
    private static final String FILE_META_STATUS = "status";
    private static final String FILE_META_STARTED_AT = "startedAtMs";
    private static final String FILE_META_COMPLETED_AT = "completedAtMs";
    private static final String FILE_META_HANDOFF_STAGE = "handoffStage";
    private static final String FILE_META_HANDOFF_SOURCE_MAP_ID =
        "handoffSourceMapId";
    private static final String FILE_META_HANDOFF_LOCKED_MAP_ID =
        "handoffLockedMapId";
    private static final String FILE_MODULE_VALUE = "staircased";
    private static final Set<String> LOGISTICS_TRAVEL_ACTIONS = Set.of(
        "dump",
        "refill",
        "sleep",
        "mapMaterialChest",
        "cartographyTable",
        "finishedMapChest",
        "mapHandoffRecoveryProbe",
        "usedToolChest",
        "walkRestock"
    );
    private static final int MAX_LOGISTICS_DETOUR_ATTEMPTS = 2;
    private static final int WORK_ACTION_BURST_CAP = 3;
    private static final double MINIMUM_STALE_SERVER_TICK_SECONDS = 1.5;
    private static final int TPS_SAMPLE_WARMUP_TICKS = 80;
    private static final int PLACEMENT_RETRY_TICKS = 8;
    private static final int PLACEMENT_MAX_RETRIES = 2;
    private static final int INVENTORY_RECOVERY_MAX_WAIT_TICKS = 200;
    private static final int PLACEMENT_MAX_PENDING_TICKS = 80;
    private static final int HOTBAR_SWAP_TIMEOUT_TICKS = 20;
    private static final int HOTBAR_SWAP_MAX_ATTEMPTS = 3;
    private static final int
        RESTOCK_MAX_CONSECUTIVE_NO_PROGRESS_ATTEMPTS = 3;
    private static final int RESTOCK_MIN_REFILL_PROBE_INTERVAL_TICKS = 20;
    private static final int INVENTORY_TRANSACTION_MAX_ATTEMPTS = 3;
    private static final int TEARDOWN_BREAK_RETRY_TICKS = 4;
    private static final int TEARDOWN_BREAK_MAX_ATTEMPTS = 20;
    private static final int TEARDOWN_BREAK_MAX_PENDING_TICKS = 1200;
    private static final double
        TEARDOWN_REACH_POSITION_TOLERANCE = 0.20;
    private static final double
        TEARDOWN_STANDING_EYE_HEIGHT = 1.62;
    private static final int MINING_RECOVERY_STABLE_SNAPSHOT_TICKS = 2;
    private static final int DEFAULT_DEBUG_PRINT_INTERVAL_TICKS = 200;
    private static final int LOCAL_CYCLE_HEARTBEAT_TICKS = 20;
    private static final double MINIMUM_TOOL_DURABILITY_FRACTION =
        0.10;
    private static final int BUILD_MATERIAL_HOTBAR_SLOT_COUNT = 8;
    private static final int BUILD_REQUIRED_MANAGED_HOTBAR_SLOTS = 9;
    private static final int TEARDOWN_PICKAXE_HOTBAR_COUNT = 2;
    private static final int TEARDOWN_AXE_HOTBAR_COUNT = 1;
    private static final int HOTBAR_SLOT_PENDING = -1;
    private static final int HOTBAR_ITEM_UNAVAILABLE = -2;

    private final CategorizedDebugLogLimiter debugLogLimiter =
        new CategorizedDebugLogLimiter();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgFileCoord =
        settings.createGroup("File coordination (multi-instance)", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range")
        .description("Maximum fullblock placement range. Never exceeds the bot's five-block reach.")
        .defaultValue(5)
        .min(1)
        .max(5)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> maxBlockActionsPerSecond = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-block-actions-per-second")
        .description("Hard real-time ceiling for build placement/repair attempts and ordered teardown break attempts. Confirmed BPS can be lower.")
        .defaultValue(30)
        .min(1)
        .max(30)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Boolean> scaleBlockRateWithTps = sgGeneral.add(new BoolSetting.Builder()
        .name("scale-block-rate-with-tps")
        .description("Scale build and ordered teardown action ceilings proportionally to measured server TPS.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minimumBlockActionTps = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-block-action-tps")
        .description("Pause building, in-route repair, and ordered teardown below this server TPS.")
        .defaultValue(10)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(scaleBlockRateWithTps::get)
        .build()
    );

    private final Setting<Boolean> nearbyRangePlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("nearby-range-placement")
        .description("Before restocking, reserve the complete active U first, then fill remaining managed capacity for dependency-safe nearby surface blocks. Nearby work never displaces the U.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> repairCurrentUPair = sgGeneral.add(new BoolSetting.Builder()
        .name("repair-current-u-pair")
        .description("Break and replace wrong blocks only inside the two-column U currently being printed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> thmInstantRepair = sgGeneral.add(new BoolSetting.Builder()
        .name("thm-instant-repair")
        .description("Temporarily configures Meteor Speed Mine like THM (Damage mode with instamine) only while repairing the active U, then restores its prior state.")
        .defaultValue(true)
        .visible(repairCurrentUPair::get)
        .onChanged(enabled -> {
            if (!enabled) releaseBuildRepairSpeedMine();
        })
        .build()
    );

    private final Setting<Boolean> thmInstantTeardown = sgGeneral.add(new BoolSetting.Builder()
        .name("thm-instant-teardown")
        .description("Temporarily configures Meteor Speed Mine for the one ordered teardown target owned by the U/column traversal, then restores its prior state.")
        .defaultValue(true)
        .onChanged(enabled -> {
            if (!enabled) releaseTeardownSpeedMine();
        })
        .build()
    );

    private final Setting<Integer> teardownScaffoldStacks = sgGeneral.add(
        new IntSetting.Builder()
            .name("teardown-scaffold-stacks")
            .description(
                "Keep two or three cobblestone stacks during teardown. "
                    + "After the fast pass, sparse server-missed blocks "
                    + "are reached by the closest safe temporary U scaffold."
            )
            .defaultValue(3)
            .min(2)
            .max(3)
            .sliderRange(2, 3)
            .build()
    );

    private final Setting<Double> maxMiningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-mining-range")
        .description("How far the ordered teardown may move from its current owned block before waiting for confirmation.")
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 2)
        .build()
    );

    private final Setting<List<Block>> startBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-blocks")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.Off)
        .build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Disable if the bot should continue after reconnecting to the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotatePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place")
        .description(
            "Rotate only for blocks whose placed state depends on "
                + "player facing. Ordinary full blocks always use "
                + "direct air placement."
        )
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sleep = sgGeneral.add(new BoolSetting.Builder()
        .name("sleep")
        .description("Sleep in bed when starting a map to avoid Phantoms.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customFolderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-folder-path")
        .description("Allows to set a custom path to the nbt folder.")
        .defaultValue(false)
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("nerv-printer-folder-path")
        .description("The path to your nerv-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\nerv-printer")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> customFolderPath.get())
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    private final Setting<Boolean> useDefaultConfigFile = sgGeneral.add(new BoolSetting.Builder()
        .name("use-default-config-file")
        .description("Load a config file when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> configFileName = sgGeneral.add(new StringSetting.Builder()
        .name("config-file-name")
        .description("The config file that is loaded  when the module is enabled.")
        .defaultValue("carpet-printer-config.json")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> useDefaultConfigFile.get())
        .build()
    );

    //Advanced

    private final Setting<Integer> preRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-restock-delay")
        .description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> invActionDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("inventory-action-delay")
        .description("How many ticks to wait between each inventory action (moving a stack).")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> postRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-restock-delay")
        .description("How many ticks to wait after restocking.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> postSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-swap-delay")
        .description("How many ticks to wait after swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> retryInteractTimer = sgAdvanced.add(new IntSetting.Builder()
        .name("retry-interact-timer")
        .description("How many ticks to wait for chest response before interacting with it again.")
        .defaultValue(80)
        .min(1)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> posResetTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("pos-reset-timeout")
        .description("How many ticks to wait after the player position was reset by the server.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> jumpCoolDown = sgAdvanced.add(new IntSetting.Builder()
        .name("jump-timeout")
        .description("How many ticks to wait after jumping before jumping again.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> mineLineEndTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("mine-line-end-timeout")
        .description("How many ticks to wait after mining a line to collect items that fell on the platform.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 30)
        .build()
    );

    private final Setting<Double> minimumToolDurability = sgAdvanced.add(new DoubleSetting.Builder()
        .name("minimum-tool-durability")
        .description("Keep a compatible carried or chest tool while at least this fraction of its durability remains. Tools below it are replaced.")
        .defaultValue(MINIMUM_TOOL_DURABILITY_FRACTION)
        .min(MINIMUM_TOOL_DURABILITY_FRACTION)
        .max(1)
        .sliderRange(MINIMUM_TOOL_DURABILITY_FRACTION, 1)
        .build()
    );

    private final Setting<Double> mineLineEndOffset = sgAdvanced.add(new DoubleSetting.Builder()
        .name("mine-LineEndOffset")
        .description("The offset to the Map Area when mining the last block of a row.")
        .defaultValue(1)
        .min(0.4)
        .sliderRange(0.5, 3)
        .build()
    );

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> snapToCheckpoints = sgAdvanced.add(new BoolSetting.Builder()
        .name("snap-to-checkpoints")
        .description("Snap to checkpoints when getting close.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgAdvanced.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the nerv-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgAdvanced.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayMaxRequirements = sgAdvanced.add(new BoolSetting.Builder()
        .name("print-max-requirements")
        .description("Print the maximum amount of material needed for all maps in the map-folder.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> saveGeneratedNbt = sgAdvanced.add(new BoolSetting.Builder()
        .name("save-compact-nbt")
        .description("When a raw NBT is loaded, write and reload-validate its exact compact circular form in _generated_compact. Printing always uses the validated compact plan.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> circularTraversal = sgAdvanced.add(new BoolSetting.Builder()
        .name("circular-u-traversal")
        .description("Build and mine a compact U route only when the complete joined pair is safe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restockRefillTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("restock-refill-timeout")
        .description("How many ticks to wait without another inventory transfer while an exhausted registered material or tool chest is refilled before trying another chest. Zero disables waiting.")
        .defaultValue(200)
        .min(0)
        .max(2400)
        .sliderRange(0, 1200)
        .build()
    );

    private final Setting<Boolean> requireCompleteUInventory = sgAdvanced.add(new BoolSetting.Builder()
        .name("require-complete-u-inventory")
        .description("Stop at the north endpoint instead of silently downgrading when the complete U and its repair tools cannot fit.")
        .defaultValue(true)
        .visible(circularTraversal::get)
        .build()
    );

    private final Setting<Boolean> logisticsObstacleDetours = sgAdvanced.add(new BoolSetting.Builder()
        .name("logistics-obstacle-detours")
        .description("Use a small bounded left or right bypass when travel to a chest, dump station, bed, or workstation is obstructed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> logisticsDetourRadius = sgAdvanced.add(new IntSetting.Builder()
        .name("logistics-detour-radius")
        .description("Maximum fixed-height search radius for logistics obstacle bypasses.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 10)
        .visible(logisticsObstacleDetours::get)
        .build()
    );

    private final Setting<Boolean> debugPrints = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug-prints")
        .description("Print structured state, movement, inventory, restock, transfer-confirmation, refill-wait, placement, and checkpoint diagnostics.")
        .defaultValue(false)
        .onChanged(enabled -> {
            if (!enabled) debugLogLimiter.clear();
        })
        .build()
    );

    private final Setting<Integer> debugPrintInterval = sgAdvanced.add(new IntSetting.Builder()
        .name("debug-print-interval")
        .description("Minimum ticks between debug messages in the same category. Repeated messages are coalesced and the latest state is printed.")
        .defaultValue(DEFAULT_DEBUG_PRINT_INTERVAL_TICKS)
        .min(20)
        .max(500)
        .sliderRange(20, 500)
        .visible(debugPrints::get)
        .onChanged(interval -> debugLogLimiter.clear())
        .build()
    );

    //Multi User

    private final Setting<String> directMessageCommand = sgMultiUser.add(new StringSetting.Builder()
        .name("direct-message-command")
        .description("The command used to send direct messages between master and slaves.")
        .defaultValue("w")
        .onChanged((value) -> SlaveSystem.directMessageCommand = value)
        .build()
    );

    private final Setting<String> senderPrefix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-prefix")
        .description("The text that always comes before the name of sender of every direct message.")
        .defaultValue("")
        .onChanged((value) -> SlaveSystem.senderPrefix = value)
        .build()
    );

    private final Setting<String> senderSuffix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-suffix")
        .description("The text that is always between the name of the sender and the actual message.")
        .defaultValue(" whispers: ")
        .onChanged((value) -> SlaveSystem.senderSuffix = value)
        .build()
    );

    private final Setting<Integer> commandDelay = sgMultiUser.add(new IntSetting.Builder()
        .name("chat-message-delay")
        .description("How many ticks to wait between sending chat messages (for multi-user printing).")
        .defaultValue(50)
        .min(1)
        .sliderRange(1, 100)
        .onChanged((value) -> SlaveSystem.commandDelay = value)
        .build()
    );

    private final Setting<Integer> randomSuffix = sgMultiUser.add(new IntSetting.Builder()
        .name("random-suffix-length")
        .description("Generate a randomized suffix to circumvent anti-spam plugins.")
        .defaultValue(0)
        .min(0)
        .max(36)
        .sliderRange(0, 10)
        .onChanged((value) -> SlaveSystem.randomLength = value)
        .build()
    );

    private final Setting<SlaveSystem.CoordinationMode> coordinationMode =
        sgFileCoord.add(
            new EnumSetting.Builder<SlaveSystem.CoordinationMode>()
                .name("coordination-mode")
                .description(
                    "Chat keeps the existing registration system. File Master/Slave "
                        + "uses durable shared JSON files and sends no coordination chat."
                )
                .defaultValue(SlaveSystem.CoordinationMode.Chat)
                .build()
        );

    private final Setting<String> fileMasterPlayerName = sgFileCoord.add(
        new StringSetting.Builder()
            .name("master-player-name")
            .description("Exact master account name used by this File Slave.")
            .defaultValue("")
            .visible(
                () -> coordinationMode.get()
                    == SlaveSystem.CoordinationMode.FileSlave
            )
            .build()
    );

    private final Setting<String> fileSlavePlayerNames = sgFileCoord.add(
        new StringSetting.Builder()
            .name("slave-player-names")
            .description(
                "Comma-separated exact slave account names configured on the File Master."
            )
            .defaultValue("")
            .wide()
            .visible(
                () -> coordinationMode.get()
                    == SlaveSystem.CoordinationMode.FileMaster
            )
            .build()
    );

    private final Setting<String> sharedSyncFolder = sgFileCoord.add(
        new StringSetting.Builder()
            .name("shared-sync-folder")
            .description(
                "Folder containing master_state.json and slave state files. "
                    + "Empty uses <nerv-printer>/_staircased_sync."
            )
            .defaultValue("")
            .wide()
            .visible(
                () -> coordinationMode.get()
                    != SlaveSystem.CoordinationMode.Chat
            )
            .build()
    );

    private final Setting<Integer> filePollTicks = sgFileCoord.add(
        new IntSetting.Builder()
            .name("file-poll-ticks")
            .description("How often coordination state files are read and written.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 200)
            .visible(
                () -> coordinationMode.get()
                    != SlaveSystem.CoordinationMode.Chat
            )
            .build()
    );

    private final Setting<Integer> filePeerTimeoutSeconds = sgFileCoord.add(
        new IntSetting.Builder()
            .name("peer-timeout-seconds")
            .description(
                "Maximum age of a slave heartbeat before the File Master waits."
            )
            .defaultValue(15)
            .min(2)
            .sliderRange(2, 120)
            .visible(
                () -> coordinationMode.get()
                    == SlaveSystem.CoordinationMode.FileMaster
            )
            .build()
    );

    private final Setting<Boolean> requireFileSlavesReady = sgFileCoord.add(
        new BoolSetting.Builder()
            .name("require-all-slaves-ready")
            .description(
                "Wait until every configured slave has loaded the same NBT and config."
            )
            .defaultValue(true)
            .visible(
                () -> coordinationMode.get()
                    == SlaveSystem.CoordinationMode.FileMaster
            )
            .build()
    );

    private final Setting<Boolean> recoverActiveFileJob = sgFileCoord.add(
        new BoolSetting.Builder()
            .name("recover-active-file-job")
            .description(
                "Resume the persisted master job/generation after a client or process restart."
            )
            .defaultValue(true)
            .visible(
                () -> coordinationMode.get()
                    == SlaveSystem.CoordinationMode.FileMaster
            )
            .build()
    );

    private final Setting<Integer> fileRecoveryMarginBlocks = sgFileCoord.add(
        new IntSetting.Builder()
            .name("recovery-margin-blocks")
            .description(
                "Bots must be within this X/Z margin of the 128x128 map area before a coordinated start or recovery."
            )
            .defaultValue(32)
            .min(0)
            .sliderRange(0, 256)
            .visible(
                () -> coordinationMode.get()
                    != SlaveSystem.CoordinationMode.Chat
            )
            .build()
    );

    //Error Handling

    private final Setting<Boolean> logErrors = sgError.add(new BoolSetting.Builder()
        .name("log-errors")
        .description("Prints warning when a misplacement is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ErrorAction> errorAction = sgError.add(new EnumSetting.Builder<ErrorAction>()
        .name("error-action")
        .description("What to do when a misplacement is detected.")
        .defaultValue(ErrorAction.Ignore)
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderMap = sgRender.add(new BoolSetting.Builder()
        .name("render-map")
        .description("Highlights the position of the map blocks.")
        .defaultValue(false)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderChestPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-chest-positions")
        .description("Highlights the selected chests.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderOpenPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-open-positions")
        .description("Indicate the position the bot will go to in order to interact with the chest.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderCheckpoints = sgRender.add(new BoolSetting.Builder()
        .name("render-checkpoints")
        .description("Indicate the checkpoints the bot will traverse.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderSpecialInteractions = sgRender.add(new BoolSetting.Builder()
        .name("render-special-interactions")
        .description("Indicate the position where the reset button and cartography table will be used.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Double> indicatorSize = sgRender.add(new DoubleSetting.Builder()
        .name("indicator-size")
        .description("How big the rendered indicator will be.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The render color.")
        .defaultValue(new SettingColor(22, 230, 206, 155))
        .visible(() -> render.get())
        .build()
    );

    int timeoutTicks;
    int jumpTimeout;
    int interactTimeout;
    int miningFinalizationRetryTicks;
    int nextMapSyncTicks;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    State debugPreviousState;
    State resumeAfterRestockState;
    Pair<Integer, Integer> workingInterval;     //Interval the bot should work in 0-127
    Pair<Integer, Integer> trueInterval;        //Stores the actual interval in case the old one is temporarily overwritten while repairing
    Pair<Integer, Integer> pendingInterval;
    Pair<BlockPos, Vec3d> usedToolChest;
    Pair<BlockPos, Vec3d> cartographyTable;
    Pair<BlockPos, Vec3d> finishedMapChest;
    Pair<BlockPos, Vec3d> bed;
    Pair<BlockPos, Vec3d> anvil;
    Pair<BlockPos, Vec3d> enderChest;
    Pair<BlockPos, Vec3d> craftingTable;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    Pair<Vec3d, Pair<Float, Float>> dumpStation;                    //Pos, Yaw, Pitch
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    BlockPos activeUsedToolDepositChest;
    BlockPos miningPos;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;      //Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    HashMap<Item, Pair<BlockPos, Vec3d>> usedToolChests;          //Maps a used tool type to its single chest
    HashMap<BlockPos, Set<Item>> usedToolDepositPlan;
    HashMap<BlockPos, Set<Integer>> usedToolDepositSlotPlan;
    Set<Item> currentUsedToolDepositItems;
    Set<Integer> currentUsedToolDepositSlots;
    Set<ItemStack> toolSet;                                       //Set of all registered tool item stacks
    HashMap<Item, Integer> registeredToolMinimumEfficiency;
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<RestockDemand<Item>> restockList;
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    HashMap<Item, Integer> restockMandatoryTargets;
    ArrayList<BlockPos> knownErrors;
    boolean tempChestIsSingle;
    Pair<Block, Integer>[][] map;
    CompactCircularNbtPlan.Result compactPlan;
    Integer northWalkwayRelativeY;
    LinkedHashMap<BlockPos, Block> buildTargets;
    ArrayList<BlockPos> orderedBuildTargets;
    HashMap<Item, Integer> plannedRepairToolDemand;
    HashMap<Item, Integer> plannedPrimaryMaterialDemand;
    HashMap<Item, Integer> confirmedPrimaryMaterialUses;
    HashMap<Item, Integer> plannedOptionalMaterialDemand;
    HashMap<Item, Integer> plannedRepairMinimumEfficiency;
    HashMap<Item, List<MiningToolRequirement>>
        plannedRepairToolCompatibilityRequirements;
    HashSet<Integer> plannedRepairToolKeepSlots;
    HashSet<Integer> plannedBuildToolKeepSlots;
    HashSet<Integer> plannedBuildUsedToolDepositSlots;
    HashSet<BlockPos> plannedRepairTargets;
    HashSet<BlockPos> plannedClearOnlyRepairTargets;
    ArrayList<Item> plannedBuildHotbarStackItems;
    ArrayList<Integer> plannedBuildMaterialHotbarSlots;
    int plannedBuildToolHotbarSlot;
    int plannedBuildHotbarPair;
    LinkedHashMap<Integer, Item> plannedBuildHotbarAssignments;
    LinkedHashMap<Integer, Item> plannedTeardownHotbarAssignments;
    PendingRestockTransfer pendingRestockTransfer;
    ServerInventoryTransferSnapshot restockInventorySnapshot;
    long restockSnapshotUpdateSequence;
    RestockConfirmationPhase restockConfirmationPhase;
    Optional<RestockRefillWaitPolicy.State> restockRefillWaitState;
    int restockNextNoProgressAttempt;
    int restockLastObservedCompatiblePlayerCount;
    int restockSourceSearchAfterSlot;
    int restockHandlerLeaseRecoveryAttempts;
    MiningToolInventoryPlan<
        Item,
        ItemStack,
        MiningToolRequirement
    > strictMiningInventoryPlan;
    PendingDumpTransfer pendingDumpTransfer;
    PendingUsedToolDeposit pendingUsedToolDeposit;
    ArrayList<BlockPos> plannedOptionalBuildOrder;
    HashSet<BlockPos> plannedOptionalBuildTargets;
    ArrayList<BlockPos> plannedDeferredMandatoryBuildOrder;
    HashSet<BlockPos> plannedDeferredMandatoryBuildTargets;
    HashMap<BlockPos, BlockReachWindow.Window>
        plannedDeferredReachWindows;
    HashSet<Integer> optimizedCircularTraversalPairs;
    HashMap<Integer, List<BlockPos>> optimizedDeferredBuildTargets;
    HashMap<Integer, Integer> optimizedDeferredRouteAssignments;
    HashSet<Integer> optimizedCircularMiningTraversalPairs;
    HashMap<
        Integer,
        List<
            ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
        >
    > optimizedDeferredMiningTargets;
    HashMap<Integer, Integer>
        optimizedDeferredMiningRouteAssignments;
    boolean circularMiningOptimizationReady;
    CircularTeardownReachTopology.Snapshot
        circularTeardownReachTopology;
    Path circularTeardownReachTopologyFile;
    HashMap<BlockPos, CircularTeardownTargetReference>
        circularTeardownTargetReferences;
    int preferredRecoveredMiningPair;
    DurableTeardownRecoveryCursor.Cursor retainedTeardownRecoveryCursor;
    HashSet<BlockPos> confirmedBuildTargetsThisRun;
    HashSet<BlockPos> optionalPendingPlacements;
    TpsScaledActionBudget workActionBudget;
    PendingPlacementLedger<BlockPos, Block> pendingPlacementLedger;
    PendingPlacementLedger<BlockPos, Block>
        teardownScaffoldPlacementLedger;
    HashMap<BlockPos, Long>
        teardownScaffoldSubmissionBlockSequences;
    ConfirmedHotbarSwap<Item> confirmedBuildHotbarSwap;
    boolean pendingBuildHotbarSwapMandatory;
    HashSet<Item> rejectedOptionalSwapMaterials;
    ConfirmedHotbarSwap<MiningToolIdentity> confirmedMiningHotbarSwap;
    MiningHotbarSwapContext miningHotbarSwapContext;
    HashMap<Integer, RepairToolShadow> repairToolShadows;
    PendingInventoryMetadataSwap pendingInventoryMetadataSwap;
    RepairToolSwapStaging repairToolSwapStaging;
    RepairMineController<BlockPos> buildRepairController;
    boolean buildMovementBlockedThisTick;
    CircularBuildMovementPolicy.HoldReason
        buildMovementHoldReasonThisTick;
    BlockPos buildMovementRequiredSupportThisTick;
    int activeCircularRouteSupportIndex;
    String lastActiveBuildMovementDebugState;
    SpeedMineSettingsSnapshot ownedSpeedMineSnapshot;
    SpeedMineOwner speedMineOwner;
    Block ownedSpeedMineConfiguredBlock;
    OrderedTeardownMineController<BlockPos, Block>
        teardownMineController;
    boolean teardownMovementOverlapAllowed;
    List<ContinuousTeardownRoutePlan.Stage<BlockPos>>
        activeContinuousTeardownStages;
    int activeContinuousTeardownPair;
    int activeContinuousTeardownStageIndex;
    boolean activeContinuousTeardownArmed;
    boolean activeContinuousTeardownRecoveryExit;
    TeardownScaffoldPhase teardownScaffoldPhase;
    ActiveTeardownScaffoldRecovery activeTeardownScaffoldRecovery;
    int activeTeardownScaffoldHotbarSlot;
    long teardownScaffoldPlacementAttempts;
    long confirmedTeardownScaffoldPlacements;
    long teardownMineFirstDispatchTick;
    long teardownMineLastDispatchTick;
    TpsScaledActionBudget.PauseReason lastPrintPauseReason;
    TpsScaledActionBudget.PauseReason lastMiningPauseReason;
    int plannedCircularBuildPair;
    int activeCircularPlacementCursor;
    long clientActionTick;
    long printActionTick;
    long miningActionTick;
    long placementAttempts;
    long confirmedPlacements;
    long repairBreakAttempts;
    long confirmedRepairBreaks;
    long teardownBreakAttempts;
    long confirmedTeardownBreaks;
    long lastActionBudgetDebugTick;
    long lastActionBudgetDebugNanos;
    long lastActionBudgetPlacementAttempts;
    long lastActionBudgetConfirmedPlacements;
    int minimumRelativeSupportY;
    int maximumRelativeSupportY;
    HashSet<BlockPos> connectorTargets;
    HashSet<BlockPos> activeConnectorTargets;
    HashMap<Integer, Boolean> circularPairModes;
    HashSet<Integer> reservedMiningLines;
    HashSet<Integer> currentMiningLines;
    HashMap<String, MiningAssignment> slaveMiningAssignments;
    HashMap<String, Long> slaveMiningTaskIds;
    HashMap<String, Long> completedSlaveMiningTaskIds;
    HashMap<String, Long> pendingSlaveMiningFinalizations;
    HashSet<Integer> reportedMinedLines;
    HashSet<Integer> reportedClearedConnectorPairs;
    ArrayDeque<Integer> pendingIndependentMiningLines;
    int activeMiningLine;
    int activeCircularBuildPair;
    int activeCircularConnectorIndex;
    int circularBuildRecoveryDirection;
    CircularBuildPhase circularBuildPhase;
    List<BlockPos> activeCircularConnectorSteps;
    List<BlockPos> activeCircularRecoveryTargets;
    boolean currentMiningPaired;
    boolean strictMiningRestockActive;
    boolean miningRecoveryPending;
    boolean miningRecoveryNeedsTools;
    boolean miningAssignmentsActive;
    boolean buildRecoveryPending;
    boolean buildRecoveryNeedsInventory;
    boolean buildRecoveryRestockAfterEgress;
    boolean buildingActive;
    boolean circularTraversalForCurrentMap;
    boolean reconnectRecoveryPending;
    long reconnectPlayerInventorySnapshotBaseline = -1L;
    InventoryLogisticsRecovery inventoryLogisticsRecovery =
        InventoryLogisticsRecovery.NONE;
    BlockPos recoveringUsedToolChest;
    long inventoryLogisticsRecoveryAfterSnapshot = -1L;
    long inventoryLogisticsRecoveryStartedTick = -1L;
    final ConcurrentLinkedQueue<Packet<?>> receivedPackets =
        new ConcurrentLinkedQueue<>();
    MapCyclePhase mapCyclePhase = MapCyclePhase.IDLE;
    boolean printingComplete;
    long nextMiningTaskId;
    long currentSlaveMiningTaskId;
    long highestSlaveMiningTaskId;
    long currentMiningSessionId;
    long lastFinalizedMiningSessionId;
    long pendingMiningFinalizationAck;
    boolean slaveAwaitingNextMapRelease;
    String pendingSlaveMiningCompletion;
    final LogisticsProgressWatchdog<LogisticsTerminal> logisticsProgressWatchdog =
        new LogisticsProgressWatchdog<>();
    final StableRecoverySnapshotGate<BlockPos>
        miningRecoverySnapshotGate =
            new StableRecoverySnapshotGate<>(
                MINING_RECOVERY_STABLE_SNAPSHOT_TICKS
            );
    LogisticsTerminal activeLogisticsTerminal;
    double logisticsDetourStandingY;
    int logisticsDetourAttempts;
    boolean logisticsSidestepUsed;
    File mapFolder;
    File mapFile;
    File generatedMapFile;
    boolean currentMapArchived;
    String activeMapName;
    String coordinationJobId;
    long coordinationGeneration;
    long cycleStartedAtMs;
    long cycleCompletedAtMs;
    boolean cycleRecovered;
    boolean cycleTimingRecorded;
    MapHandoffStage mapHandoffStage;
    Integer handoffSourceMapId;
    Integer handoffLockedMapId;
    long serverInventoryUpdateSequence;
    long serverPlayerInventorySnapshotSequence;
    long serverBlockUpdateSequence;
    long[] serverHotbarUpdateSequences;
    long[] serverHotbarSwapAckSequences;
    Item[] serverHotbarObservedItems;
    MiningToolIdentity[] serverHotbarObservedTools;
    HashMap<BlockPos, ServerBlockObservation> serverBlockObservations;
    HashMap<BlockPos, Long> placementSubmissionBlockSequences;
    HashMap<BlockPos, Long> repairSubmissionBlockSequences;
    long handoffConfirmationAfterSequence;
    int handoffConfirmationAttempts;
    int handoffMapHotbarSlot;
    boolean waitingForFilePeersNotice;
    String loadedFileCycleKey;
    String logicalSourceName;
    String logicalPrintingName;
    String archivedSourceName;
    String archivedPrintingName;
    boolean fileMasterRecoveryLoaded;
    MapCyclePhase fileMasterRecoveredPhase;
    String fileMasterRecoveredSourceName;
    String fileMasterRecoveredSourceSha256;
    String fileMasterRecoveredPrintingName;
    String fileMasterRecoveredArchivedSourceName;
    String fileMasterRecoveredArchivedPrintingName;
    String fileMasterRecoveredConfigSha256;
    String fileMasterRecoveredPlanSha256;
    String fileMasterRecoveredCircular;
    MapHandoffStage fileMasterRecoveredHandoffStage;
    Integer fileMasterRecoveredSourceMapId;
    Integer fileMasterRecoveredLockedMapId;
    String activeSourceSha256;
    String activeConfigSha256;
    String rejectedFileCycleKey;
    String activeCompactPlanSha256;
    long fileRecoveryToken;
    long lastPreparedFileRecoveryToken;
    int fileRecoveryRetryTicks;
    HashSet<String> fileRecoveryAcknowledgements;
    boolean waitingForFileMasterAvailability;
    String fileMasterRecoveredServer;
    String fileMasterRecoveredDimension;
    String fileMasterRecoveredMapCorner;
    boolean fileRecoveryIdentityWarning;
    boolean miningRecoverySnapshotWaitLogged;
    StaircasedCycleCheckpointStore localCycleCheckpointStore;
    boolean localCycleRecoveryCandidate;
    int recoveredActiveMiningPair;
    int recoveredActiveMiningTargetIndex;
    long lastLocalCycleCheckpointTick;
    boolean startContinueActivationRequested;

    public StaircasedPrinter() {
        super(Addon.CATEGORY, "fullblock-printer", "Automatically builds fullblock maps with optional staircasing from nbt files.");
    }

    @Override
    public void onActivate() {
        debugLogLimiter.clear();
        boolean preserveForStartContinue =
            startContinueActivationRequested;
        startContinueActivationRequested = false;
        freezeForRecoveryClassification();
        cancelLogisticsDetour();
        if (beginReconnectRecoveryIfPending()) return;
        if ((preserveForStartContinue
                || !activationReset.get())
            && checkpoints != null) {
            switch (activeRecoveryOwner()) {
                case BUILD -> beginBuildRecovery(false);
                case MINING -> beginMiningRecovery(false);
                case LOGISTICS ->
                    beginInventoryLogisticsRecoveryForCurrentPhase(
                        InventoryRecoveryAuthority
                            .REGISTERED_HANDLER_PROBE
                    );
            }
            resyncMiningProtocol();
            return;
        }
        materialDict = new HashMap<>();
        usedToolChests = new HashMap<>();
        usedToolDepositPlan = new HashMap<>();
        usedToolDepositSlotPlan = new HashMap<>();
        currentUsedToolDepositItems = new HashSet<>();
        currentUsedToolDepositSlots = new HashSet<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        pendingRestockTransfer = null;
        restockInventorySnapshot =
            new ServerInventoryTransferSnapshot(36);
        restockSnapshotUpdateSequence = -1L;
        restockConfirmationPhase = RestockConfirmationPhase.NONE;
        restockRefillWaitState = Optional.empty();
        resetRestockTransferTracking();
        restockHandlerLeaseRecoveryAttempts = 0;
        strictMiningInventoryPlan = null;
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        toolSet = new HashSet<>();
        registeredToolMinimumEfficiency = new HashMap<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        restockMandatoryTargets = new HashMap<>();
        knownErrors = new ArrayList<>();
        buildTargets = new LinkedHashMap<>();
        orderedBuildTargets = new ArrayList<>();
        plannedRepairToolDemand = new HashMap<>();
        plannedPrimaryMaterialDemand = new HashMap<>();
        confirmedPrimaryMaterialUses = new HashMap<>();
        plannedOptionalMaterialDemand = new HashMap<>();
        plannedRepairMinimumEfficiency = new HashMap<>();
        plannedRepairToolCompatibilityRequirements = new HashMap<>();
        plannedRepairToolKeepSlots = new HashSet<>();
        plannedBuildToolKeepSlots = new HashSet<>();
        plannedBuildUsedToolDepositSlots = new HashSet<>();
        plannedRepairTargets = new HashSet<>();
        plannedClearOnlyRepairTargets = new HashSet<>();
        plannedBuildHotbarStackItems = new ArrayList<>();
        plannedBuildMaterialHotbarSlots = new ArrayList<>();
        plannedBuildToolHotbarSlot = -1;
        plannedBuildHotbarPair = -1;
        plannedBuildHotbarAssignments = new LinkedHashMap<>();
        plannedTeardownHotbarAssignments = new LinkedHashMap<>();
        plannedOptionalBuildOrder = new ArrayList<>();
        plannedOptionalBuildTargets = new HashSet<>();
        plannedDeferredMandatoryBuildOrder = new ArrayList<>();
        plannedDeferredMandatoryBuildTargets = new HashSet<>();
        plannedDeferredReachWindows = new HashMap<>();
        optimizedCircularTraversalPairs = new HashSet<>();
        optimizedDeferredBuildTargets = new HashMap<>();
        optimizedDeferredRouteAssignments = new HashMap<>();
        optimizedCircularMiningTraversalPairs = new HashSet<>();
        optimizedDeferredMiningTargets = new HashMap<>();
        optimizedDeferredMiningRouteAssignments = new HashMap<>();
        circularMiningOptimizationReady = false;
        circularTeardownReachTopology = null;
        circularTeardownReachTopologyFile = null;
        circularTeardownTargetReferences = new HashMap<>();
        preferredRecoveredMiningPair = -1;
        retainedTeardownRecoveryCursor = null;
        confirmedBuildTargetsThisRun = new HashSet<>();
        optionalPendingPlacements = new HashSet<>();
        workActionBudget = createWorkActionBudget();
        pendingPlacementLedger = new PendingPlacementLedger<>(
            PLACEMENT_RETRY_TICKS,
            PLACEMENT_MAX_RETRIES
        );
        teardownScaffoldPlacementLedger =
            new PendingPlacementLedger<>(
                PLACEMENT_RETRY_TICKS,
                PLACEMENT_MAX_RETRIES
            );
        teardownScaffoldSubmissionBlockSequences = new HashMap<>();
        confirmedBuildHotbarSwap = new ConfirmedHotbarSwap<>();
        pendingBuildHotbarSwapMandatory = false;
        rejectedOptionalSwapMaterials = new HashSet<>();
        confirmedMiningHotbarSwap = new ConfirmedHotbarSwap<>();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        repairToolShadows = new HashMap<>();
        pendingInventoryMetadataSwap = null;
        repairToolSwapStaging = null;
        buildRepairController = new RepairMineController<>(
            new RepairMineController.RetryPolicy(2, 20, 80, 1200)
        );
        buildMovementBlockedThisTick = false;
        buildMovementHoldReasonThisTick =
            CircularBuildMovementPolicy.HoldReason.NONE;
        buildMovementRequiredSupportThisTick = null;
        activeCircularRouteSupportIndex = -1;
        lastActiveBuildMovementDebugState = null;
        ownedSpeedMineSnapshot = null;
        speedMineOwner = SpeedMineOwner.NONE;
        ownedSpeedMineConfiguredBlock = null;
        teardownMineController =
            new OrderedTeardownMineController<>();
        teardownMovementOverlapAllowed = false;
        activeContinuousTeardownStages = List.of();
        activeContinuousTeardownPair = -1;
        activeContinuousTeardownStageIndex = -1;
        activeContinuousTeardownArmed = false;
        activeContinuousTeardownRecoveryExit = false;
        teardownScaffoldPhase = TeardownScaffoldPhase.NONE;
        activeTeardownScaffoldRecovery = null;
        activeTeardownScaffoldHotbarSlot = -1;
        teardownMineFirstDispatchTick = -1L;
        teardownMineLastDispatchTick = -1L;
        lastPrintPauseReason = TpsScaledActionBudget.PauseReason.NONE;
        lastMiningPauseReason =
            TpsScaledActionBudget.PauseReason.NONE;
        plannedCircularBuildPair = -1;
        activeCircularPlacementCursor = -1;
        clientActionTick = 0L;
        printActionTick = 0L;
        miningActionTick = 0L;
        placementAttempts = 0L;
        confirmedPlacements = 0L;
        teardownScaffoldPlacementAttempts = 0L;
        confirmedTeardownScaffoldPlacements = 0L;
        repairBreakAttempts = 0L;
        confirmedRepairBreaks = 0L;
        teardownBreakAttempts = 0L;
        confirmedTeardownBreaks = 0L;
        lastActionBudgetDebugTick =
            -DEFAULT_DEBUG_PRINT_INTERVAL_TICKS;
        lastActionBudgetDebugNanos = 0L;
        lastActionBudgetPlacementAttempts = 0L;
        lastActionBudgetConfirmedPlacements = 0L;
        minimumRelativeSupportY = Integer.MAX_VALUE;
        maximumRelativeSupportY = Integer.MIN_VALUE;
        connectorTargets = new HashSet<>();
        activeConnectorTargets = new HashSet<>();
        circularPairModes = new HashMap<>();
        reservedMiningLines = new HashSet<>();
        currentMiningLines = new HashSet<>();
        slaveMiningAssignments = new HashMap<>();
        slaveMiningTaskIds = new HashMap<>();
        completedSlaveMiningTaskIds = new HashMap<>();
        pendingSlaveMiningFinalizations = new HashMap<>();
        reportedMinedLines = new HashSet<>();
        reportedClearedConnectorPairs = new HashSet<>();
        pendingIndependentMiningLines = new ArrayDeque<>();
        activeMiningLine = -1;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        activeCircularConnectorSteps = List.of();
        activeCircularRecoveryTargets = List.of();
        currentMiningPaired = false;
        strictMiningRestockActive = false;
        miningRecoveryPending = false;
        miningRecoveryNeedsTools = false;
        miningAssignmentsActive = false;
        buildRecoveryPending = false;
        buildRecoveryNeedsInventory = false;
        buildRecoveryRestockAfterEgress = false;
        buildingActive = false;
        circularTraversalForCurrentMap = circularTraversal.get();
        reconnectRecoveryPending = false;
        reconnectPlayerInventorySnapshotBaseline = -1L;
        inventoryLogisticsRecovery =
            InventoryLogisticsRecovery.NONE;
        recoveringUsedToolChest = null;
        inventoryLogisticsRecoveryAfterSnapshot = -1L;
        inventoryLogisticsRecoveryStartedTick = -1L;
        mapCyclePhase = MapCyclePhase.IDLE;
        printingComplete = false;
        nextMiningTaskId = Math.max(1L, System.currentTimeMillis());
        currentSlaveMiningTaskId = -1;
        highestSlaveMiningTaskId = -1;
        currentMiningSessionId = -1;
        lastFinalizedMiningSessionId = -1;
        pendingMiningFinalizationAck = -1;
        slaveAwaitingNextMapRelease = false;
        pendingSlaveMiningCompletion = null;
        activeLogisticsTerminal = null;
        logisticsDetourStandingY = Double.NaN;
        logisticsDetourAttempts = 0;
        logisticsSidestepUsed = false;
        logisticsProgressWatchdog.reset();
        compactPlan = null;
        circularTeardownReachTopology = null;
        circularTeardownReachTopologyFile = null;
        if (circularTeardownTargetReferences != null) {
            circularTeardownTargetReferences.clear();
        }
        northWalkwayRelativeY = null;
        generatedMapFile = null;
        currentMapArchived = false;
        activeMapName = null;
        coordinationJobId = UUID.randomUUID().toString();
        coordinationGeneration = 0;
        cycleStartedAtMs = -1;
        cycleCompletedAtMs = -1;
        cycleRecovered = false;
        cycleTimingRecorded = false;
        mapHandoffStage = MapHandoffStage.NONE;
        handoffSourceMapId = null;
        handoffLockedMapId = null;
        serverInventoryUpdateSequence = 0L;
        serverPlayerInventorySnapshotSequence = 0L;
        serverBlockUpdateSequence = 0L;
        serverHotbarUpdateSequences = new long[9];
        serverHotbarSwapAckSequences = new long[9];
        serverHotbarObservedItems = new Item[9];
        Arrays.fill(serverHotbarObservedItems, Items.AIR);
        serverHotbarObservedTools = new MiningToolIdentity[9];
        Arrays.fill(
            serverHotbarObservedTools,
            miningToolIdentity(ItemStack.EMPTY)
        );
        serverBlockObservations = new HashMap<>();
        placementSubmissionBlockSequences = new HashMap<>();
        repairSubmissionBlockSequences = new HashMap<>();
        handoffConfirmationAfterSequence = -1L;
        handoffConfirmationAttempts = 0;
        handoffMapHotbarSlot = -1;
        waitingForFilePeersNotice = false;
        loadedFileCycleKey = null;
        logicalSourceName = null;
        logicalPrintingName = null;
        archivedSourceName = null;
        archivedPrintingName = null;
        fileMasterRecoveryLoaded = false;
        fileMasterRecoveredPhase = null;
        fileMasterRecoveredSourceName = null;
        fileMasterRecoveredSourceSha256 = null;
        fileMasterRecoveredPrintingName = null;
        fileMasterRecoveredArchivedSourceName = null;
        fileMasterRecoveredArchivedPrintingName = null;
        fileMasterRecoveredConfigSha256 = null;
        fileMasterRecoveredPlanSha256 = null;
        fileMasterRecoveredCircular = null;
        fileMasterRecoveredHandoffStage = null;
        fileMasterRecoveredSourceMapId = null;
        fileMasterRecoveredLockedMapId = null;
        activeSourceSha256 = null;
        activeConfigSha256 = null;
        rejectedFileCycleKey = null;
        activeCompactPlanSha256 = null;
        fileRecoveryToken = -1L;
        lastPreparedFileRecoveryToken = -1L;
        fileRecoveryRetryTicks = 0;
        fileRecoveryAcknowledgements = new HashSet<>();
        waitingForFileMasterAvailability = false;
        fileMasterRecoveredServer = null;
        fileMasterRecoveredDimension = null;
        fileMasterRecoveredMapCorner = null;
        fileRecoveryIdentityWarning = false;
        miningRecoverySnapshotGate.reset();
        miningRecoverySnapshotWaitLogged = false;
        localCycleCheckpointStore = null;
        localCycleRecoveryCandidate = false;
        recoveredActiveMiningPair = -1;
        recoveredActiveMiningTargetIndex = -1;
        lastLocalCycleCheckpointTick =
            -LOCAL_CYCLE_HEARTBEAT_TICKS;
        usedToolChest = null;
        mapCorner = null;
        lastInteractedChest = null;
        activeUsedToolDepositChest = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        bed = null;
        anvil = null;
        enderChest = null;
        craftingTable = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        jumpTimeout = 0;
        interactTimeout = 0;
        miningFinalizationRetryTicks = 0;
        nextMapSyncTicks = 0;
        oldState = null;
        debugPreviousState = null;
        resumeAfterRestockState = null;
        pendingInterval = null;
        tempChestIsSingle = false;

        setInterval(new Pair<>(0, 127));
        // Initialize Slave System settings
        SlaveSystem.setupSlaveSystem(this, commandDelay.get(), directMessageCommand.get(), senderPrefix.get(), senderSuffix.get(), randomSuffix.get());

        if (!customFolderPath.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory(), "nerv-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createFolders(mapFolder)) {
            toggle();
            return;
        }
        if (!configureFileCoordination()) {
            toggle();
            return;
        }
        if (!configureLocalCycleCheckpointing()) {
            toggle();
            return;
        }

        if (displayMaxRequirements.get() && !SlaveSystem.isFileMode()) {
            HashMap<Block, Integer> materialCountDict = new HashMap<>();
            for (File file : mapFolder.listFiles()) {
                if (!file.isFile()) continue;
                if (!prepareNextMapFile()) return;
                for (Pair<Block, Integer> material : blockPaletteDict.values()) {
                    if (!materialCountDict.containsKey(material.getLeft())) {
                        materialCountDict.put(material.getLeft(), material.getRight());
                    } else {
                        materialCountDict.put(material.getLeft(), Math.max(materialCountDict.get(material.getLeft()), material.getRight()));
                    }
                }
            }
            info("§aMaterial needed for all files:");
            for (Block block : materialCountDict.keySet()) {
                float shulkerAmount = (float) Math.ceil((float) materialCountDict.get(block) / (float) (27 * 64) * 10) / (float) 10;
                if (shulkerAmount == 0) continue;
                info(block.getName().getString() + ": " + shulkerAmount + " shulker");
            }
            startedFiles.clear();
        }

        if (SlaveSystem.isFileSlave()) {
            synchronizeFileSlaveCycle();
        } else if (!prepareNextMapFile()) {
            return;
        }

        state = State.SelectingMapArea;
        if (useDefaultConfigFile.get()) {
            File configFolder = new File(mapFolder, "_configs");
            if (!loadConfig(new File(configFolder, configFileName.get()))) {
                info("Select the §aMap Building Area (128x128). (Right-click the edge from the inside)");
            }
        } else {
            info("Select the §aMap Building Area (128x128). (Right-click the edge from the inside)");
        }
    }

    private boolean beginReconnectRecoveryIfPending() {
        if (!reconnectRecoveryPending || checkpoints == null) {
            return false;
        }
        reconnectRecoveryPending = false;
        boolean requireFreshPlayerInventory =
            serverPlayerInventorySnapshotSequence
                <= reconnectPlayerInventorySnapshotBaseline;
        reconnectPlayerInventorySnapshotBaseline = -1L;
        switch (activeRecoveryOwner()) {
            case BUILD -> {
                buildRecoveryNeedsInventory = false;
                beginBuildRecovery(requireFreshPlayerInventory);
            }
            case MINING -> {
                miningRecoveryNeedsTools = false;
                beginMiningRecovery(requireFreshPlayerInventory);
            }
            case LOGISTICS ->
                beginInventoryLogisticsRecoveryForCurrentPhase(
                    requireFreshPlayerInventory
                        ? InventoryRecoveryAuthority.PLAYER_SNAPSHOT
                        : InventoryRecoveryAuthority.NONE
                );
        }
        resyncMiningProtocol();
        return true;
    }

    @Override
    public void onDeactivate() {
        freezeForRecoveryClassification();
        if (mapCyclePhase != null
            && mapCyclePhase.isInProgress()
            && !fileMasterRecoveryLoaded
            && !localCycleRecoveryCandidate) {
            persistLocalCycleCheckpoint("module-deactivate");
        }
        cancelLogisticsDetour();
        lastActiveBuildMovementDebugState = null;
        receivedPackets.clear();
        releaseAnyOwnedSpeedMine();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        clearInventoryLogisticsRecoveryMarker();
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        if (workActionBudget != null) workActionBudget.reset();
        if (pendingPlacementLedger != null) pendingPlacementLedger.reset();
        if (optionalPendingPlacements != null) optionalPendingPlacements.clear();
        if (placementSubmissionBlockSequences != null) {
            placementSubmissionBlockSequences.clear();
        }
        if (repairSubmissionBlockSequences != null) {
            repairSubmissionBlockSequences.clear();
        }
        if (confirmedBuildHotbarSwap != null) confirmedBuildHotbarSwap.clear();
        if (confirmedMiningHotbarSwap != null) {
            confirmedMiningHotbarSwap.clear();
        }
        if (repairToolShadows != null) repairToolShadows.clear();
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        if (buildRepairController != null) buildRepairController.reset();
        SlaveSystem.setFileMetadata(FILE_META_ACTIVE, "false");
        SlaveSystem.setFileMetadata(FILE_META_READY, "false");
        if (SlaveSystem.isFileMode()) {
            try {
                SlaveSystem.flushFileCoordinationNow();
            } catch (IOException failure) {
                warning(
                    "Could not persist deactivated file status: "
                        + failure.getMessage()
                );
            }
        }
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onGameLeft(GameLeftEvent event) {
        freezeForRecoveryClassification();
        if (mapCyclePhase != null
            && mapCyclePhase.isInProgress()
            && !fileMasterRecoveryLoaded
            && !localCycleRecoveryCandidate) {
            persistLocalCycleCheckpoint("game-left");
        }
        cancelLogisticsDetour();
        receivedPackets.clear();
        releaseAnyOwnedSpeedMine();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        clearInventoryLogisticsRecoveryMarker();
        if (workActionBudget != null) workActionBudget.reset();
        if (pendingPlacementLedger != null) pendingPlacementLedger.reset();
        if (optionalPendingPlacements != null) optionalPendingPlacements.clear();
        if (placementSubmissionBlockSequences != null) {
            placementSubmissionBlockSequences.clear();
        }
        if (repairSubmissionBlockSequences != null) {
            repairSubmissionBlockSequences.clear();
        }
        if (confirmedBuildHotbarSwap != null) confirmedBuildHotbarSwap.clear();
        if (confirmedMiningHotbarSwap != null) {
            confirmedMiningHotbarSwap.clear();
        }
        if (repairToolShadows != null) repairToolShadows.clear();
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        if (buildRepairController != null) buildRepairController.reset();
        SlaveSystem.setFileMetadata(FILE_META_ACTIVE, "false");
        SlaveSystem.setFileMetadata(FILE_META_READY, "false");
        SlaveSystem.setFileMetadata(FILE_META_STATUS, "DISCONNECTED");
        try {
            SlaveSystem.flushFileCoordinationNow();
        } catch (IOException failure) {
            warning(
                "Could not persist disconnected file status: "
                    + failure.getMessage()
            );
        }
        reconnectRecoveryPending =
            mapCyclePhase.isInProgress()
                || SlaveSystem.hasRelationship();
        reconnectPlayerInventorySnapshotBaseline =
            serverPlayerInventorySnapshotSequence;
        if (mapCyclePhase.isInProgress()) cycleRecovered = true;
        switch (activeRecoveryOwner()) {
            case BUILD -> {
                buildRecoveryPending = true;
                buildRecoveryNeedsInventory = true;
            }
            case MINING -> {
                miningRecoveryPending = true;
                miningRecoveryNeedsTools = true;
            }
            case LOGISTICS -> {
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM) {
            dumpStation = new Pair<>(mc.player.getEntityPos(), new Pair<>(mc.player.getYaw(), mc.player.getPitch()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the §aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                northWalkwayRelativeY = null;
                resetMapAreaCache();
                state = State.SelectingTable;
                info("Map Area selected. Select the §aCartography Table.");
                break;
            case SelectingTable:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Cartography Table selected. Throw an item into the §aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Finished Map Chest selected. Select the §aUsed Pickaxe Chest.");
                    state = State.SelectingUsedPickaxeChest;
                }
                break;
            case SelectingUsedPickaxeChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState usedToolChestState = MapAreaCache.getCachedBlockState(blockPos);
                if (usedToolChestState.getBlock() instanceof ChestBlock) {
                    usedToolChest = new Pair<>(blockPos, mc.player.getEntityPos());
                    tempChestPos = blockPos;
                    tempChestIsSingle = usedToolChestState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE;
                    state = State.AwaitUsedToolRegistrationResponse;
                }
                break;
            case SelectingBed:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof BedBlock) {
                    bed = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Bed selected. Select all §aMaterial-, Tool-, and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlocks.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                Block block = blockState.getBlock();
                if (block instanceof AnvilBlock) {
                    anvil = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Registered §aAnvil");
                } else if (block.equals(Blocks.ENDER_CHEST)) {
                    enderChest = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Registered §aEnder Chest");
                } else if (block.equals(Blocks.CRAFTING_TABLE)) {
                    craftingTable = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Registered §aCrafting Table");
                } else if (block instanceof ChestBlock) {
                    tempChestPos = blockPos;
                    tempChestIsSingle = blockState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlocks.get().contains(blockState.getBlock())) {
                    if (validateStartRequirements()) startBuilding();
                }
                break;
        }
    }

    private boolean validateStartRequirements() {
        if (materialDict.isEmpty()) {
            warning("No Material Chests selected!");
            return false;
        }
        if (toolSet.isEmpty()) {
            warning("No Tool Chests selected!");
            return false;
        }
        if (mapMaterialChests.isEmpty()) {
            warning("No Map Chests selected!");
            return false;
        }
        return true;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        receivedPackets.add(event.packet);
    }

    private void drainReceivedPackets() {
        Packet<?> packet;
        while ((packet = receivedPackets.poll()) != null) {
            handleReceivePacket(packet);
        }
    }

    private void handleReceivePacket(Packet<?> receivedPacket) {
        if (state == null) return;

        if (receivedPacket instanceof PlayerPositionLookS2CPacket) {
            freezeForRecoveryClassification();
            cancelLogisticsDetour();
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) {
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
            }
            switch (activeRecoveryOwner()) {
                case BUILD -> beginBuildRecovery(false);
                case MINING -> beginMiningRecovery(false);
                case LOGISTICS ->
                    beginInventoryLogisticsRecoveryForCurrentPhase(
                        InventoryRecoveryAuthority
                            .REGISTERED_HANDLER_PROBE
                    );
            }
        }

        if (receivedPacket instanceof BlockUpdateS2CPacket packet) {
            recordServerBlockObservation(
                packet.getPos(),
                packet.getState()
            );
        } else if (receivedPacket
            instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::recordServerBlockObservation);
        }

        if (receivedPacket instanceof InventoryS2CPacket packet) {
            serverInventoryUpdateSequence++;
            if (packet.syncId() == 0) {
                serverPlayerInventorySnapshotSequence =
                    serverInventoryUpdateSequence;
            }
            if (!acknowledgePendingInventoryMetadataSwap(packet)) {
                return;
            }
            recordFullInventoryHotbarObservations(packet);
            recordRestockFullInventorySnapshot(packet);
            refreshPlannedRepairToolKeepSlots(packet);
            recordDumpFullInventoryObservation(packet);
        } else if (receivedPacket
            instanceof ScreenHandlerSlotUpdateS2CPacket packet) {
            serverInventoryUpdateSequence++;
            recordSlotHotbarObservation(packet);
            recordRestockSlotObservation(packet);
        } else if (receivedPacket
            instanceof SetPlayerInventoryS2CPacket packet) {
            serverInventoryUpdateSequence++;
            recordPlayerInventoryHotbarObservation(packet);
        }
        refreshPendingInventoryMetadataCapture();
        if (!(receivedPacket instanceof InventoryS2CPacket packet)) return;

        if (state.equals(State.AwaitUsedToolRegistrationResponse)) {
            registerSelectedUsedToolChest(packet);
            continueAfterUsedToolChestSelection();
            return;
        }

        if (state.equals(State.AwaitRegisterResponse)) {
            Item foundItem = null;
            ItemStack foundItemStack = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.contents().size() - 36; i++) {
                ItemStack stack = packet.contents().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    if (foundItemStack == null
                        || (ToolUtils.isTool(stack)
                            && (!ToolUtils.isTool(foundItemStack)
                                || getEfficiencyLevel(stack)
                                    > getEfficiencyLevel(
                                        foundItemStack
                                    )))) {
                        foundItemStack = stack;
                    }
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered §aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.getEntityPos());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (tempChestIsSingle && ToolUtils.isTool(foundItemStack)) {
                if (isMixedContent) {
                    warning("Different tools found in single chest. Please only have one tool type in a used-tool chest.");
                    state = State.SelectingChests;
                    return;
                }
                usedToolChests.put(foundItem, new Pair<>(tempChestPos, mc.player.getEntityPos()));
                info("Registered §a" + foundItemStack.getName().getString() + " Used Tool Chest");
                state = State.SelectingChests;
                return;
            }
            if (ToolUtils.isTool(foundItemStack)) {
                toolSet.add(foundItemStack.copy());
                registeredToolMinimumEfficiency.merge(
                    foundItemStack.getItem(),
                    getEfficiencyLevel(foundItemStack),
                    Math::max
                );
            }
            info("Registered item: §a" + foundItem.getName().getString());
            if (!materialDict.containsKey(foundItem)) materialDict.put(foundItem, new ArrayList<>());
            ArrayList<Pair<BlockPos, Vec3d>> oldList = materialDict.get(foundItem);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getEntityPos());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
            return;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse,
            State.AwaitFinishedMapDepositConfirmation,
            State.AwaitMapHandoffRecoveryProbeResponse,
            State.AwaitUsedToolChestResponse);
        if (allowedStates.contains(state)) {
            if (state == State.AwaitRestockResponse
                && (!isCurrentRestockHandler(packet.syncId())
                    || packet.contents().size() < 36)) {
                return;
            }
            if (state == State.AwaitUsedToolChestResponse
                && !isExpectedUsedToolHandler(packet)) {
                return;
            }
            if ((state == State.AwaitFinishedMapChestResponse
                    || state
                        == State.AwaitFinishedMapDepositConfirmation
                    || state
                        == State.AwaitMapHandoffRecoveryProbeResponse)
                && !isExpectedFinishedMapHandler(packet)) {
                return;
            }
            if (state == State.AwaitRestockResponse) {
                // Stop the chest-open retry clock as soon as the matching
                // server response arrives, even while its configured
                // pre-action delay is still running.
                interactTimeout = 0;
            }
            if (state == State.AwaitUsedToolChestResponse
                || state
                    == State.AwaitMapHandoffRecoveryProbeResponse) {
                interactTimeout = 0;
            }
            toBeHandledInvPacket = packet;
            timeoutTicks = state == State.AwaitRestockResponse
                && isAwaitingRestockRefill()
                ? 0
                : preRestockDelay.get();
            if (state == State.AwaitRestockResponse) {
                debugRestock(
                    "queued full packet sync=" + packet.syncId()
                        + " sequence=" + serverInventoryUpdateSequence
                        + " handlingDelay=" + timeoutTicks
                );
            }
        }
    }

    private void registerSelectedUsedToolChest(InventoryS2CPacket packet) {
        Item foundItem = null;
        ItemStack foundStack = null;
        boolean isMixedContent = false;
        for (int i = 0; i < packet.contents().size() - 36; i++) {
            ItemStack stack = packet.contents().get(i);
            if (!ToolUtils.isTool(stack)) continue;
            if (foundItem != null && foundItem != stack.getItem()) isMixedContent = true;
            foundItem = stack.getItem();
            foundStack = stack;
        }

        if (foundItem == null) {
            warning("Used Pickaxe Chest contains no tools; it will only be used as the fallback chest.");
        } else if (isMixedContent) {
            warning("Used Pickaxe Chest contains different tool types; it will only be used as the fallback chest.");
        } else if (!tempChestIsSingle) {
            warning("Used Pickaxe Chest is a double chest; it will only be used as the fallback chest.");
        } else {
            usedToolChests.put(foundItem, new Pair<>(tempChestPos, mc.player.getEntityPos()));
            info("Registered §a" + foundStack.getName().getString() + " Used Tool Chest");
        }
    }

    private void continueAfterUsedToolChestSelection() {
        if (sleep.get()) {
            info("Used Pickaxe Chest selected. Select the §abed used for sleeping.");
            state = State.SelectingBed;
        } else {
            info("Used Pickaxe Chest selected. Select all §aMaterial-, Tool-, and Map-Chests.");
            state = State.SelectingChests;
        }
    }

    private void handleInventoryPacket(InventoryS2CPacket packet) {
        debugLog(
            "Inventory",
            "handling full packet sync=" + packet.syncId()
                + " slots=" + packet.contents().size()
        );
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                handleRestockInventoryPacket(packet);
                break;
            case AwaitMapChestResponse:
                if (mapHandoffStage != MapHandoffStage.NEED_SUPPLIES) {
                    failMapHandoff(
                        "Map-material chest opened in unexpected stage "
                            + mapHandoffStage + "."
                    );
                    return;
                }
                int mapSlot = -1;
                int paneSlot = -1;
                boolean playerHasMap = packetPlayerItemCount(
                    packet,
                    Items.MAP
                ) == 1;
                boolean playerHasPane = packetPlayerItemCount(
                    packet,
                    Items.GLASS_PANE
                ) == 1;
                for (int slot = 0; slot < packet.contents().size() - 36; slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if ((!playerHasMap && mapSlot == -1)
                    || (!playerHasPane && paneSlot == -1)) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                if (!playerHasMap) {
                    Utils.getOneItem(
                        mapSlot,
                        false,
                        availableSlots,
                        availableHotBarSlots,
                        packet
                    );
                }
                if (!playerHasPane) {
                    Utils.getOneItem(
                        paneSlot,
                        true,
                        availableSlots,
                        availableHotBarSlots,
                        packet
                    );
                }
                timeoutTicks = Math.max(10, postRestockDelay.get());
                awaitServerInventoryUpdate();
                state = State.AwaitMapSuppliesConfirmation;
                break;
            case AwaitCartographyResponse:
                if (mapHandoffStage
                    != MapHandoffStage.SOURCE_MAP_CONFIRMED) {
                    failMapHandoff(
                        "Cartography table opened in unexpected stage "
                            + mapHandoffStage + "."
                    );
                    return;
                }
                interactTimeout = 0;
                int playerStart = packet.contents().size() - 36;
                int sourceMapSlot = -1;
                int playerPaneSlot = -1;
                for (int slot = playerStart;
                     slot < packet.contents().size();
                     slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (sourceMapSlot == -1
                        && mapIdEquals(stack, handoffSourceMapId)
                        && !isLockedMap(stack)) {
                        sourceMapSlot = slot;
                    }
                    if (playerPaneSlot == -1
                        && stack.getItem() == Items.GLASS_PANE) {
                        playerPaneSlot = slot;
                    }
                }
                if (sourceMapSlot < 0 || playerPaneSlot < 0) {
                    failMapHandoff(
                        "The cartography table was opened without both the "
                            + "exact unlocked source map and one glass pane."
                    );
                    return;
                }
                if (sourceMapSlot != -1) {
                    mc.interactionManager.clickSlot(
                        packet.syncId(),
                        sourceMapSlot,
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player
                    );
                }
                if (playerPaneSlot != -1) {
                    mc.interactionManager.clickSlot(
                        packet.syncId(),
                        playerPaneSlot,
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player
                    );
                }
                mc.interactionManager.clickSlot(packet.syncId(), 2, 0, SlotActionType.QUICK_MOVE, mc.player);
                timeoutTicks = Math.max(10, postRestockDelay.get());
                awaitServerInventoryUpdate();
                state = State.AwaitCartographyOutputConfirmation;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                if (!isExpectedFinishedMapHandler(packet)) {
                    closeNextInvPacket = false;
                    return;
                }
                if (mapHandoffStage
                        != MapHandoffStage.LOCKED_MAP_CONFIRMED
                    && mapHandoffStage
                        != MapHandoffStage.DEPOSIT_REQUESTED) {
                    failMapHandoff(
                        "Finished-map chest opened in unexpected stage "
                            + mapHandoffStage + "."
                    );
                    return;
                }
                if (!mapHandoffStage.hasValidMapIds(
                    handoffSourceMapId,
                    handoffLockedMapId
                )) {
                    failMapHandoff(
                        "Finished-map deposit is missing distinct source "
                            + "and locked map IDs."
                    );
                    return;
                }
                int lockedMapSlot = packetPlayerMapSlot(
                    packet,
                    handoffLockedMapId
                );
                int expectedPlayerMapCount = lockedMapSlot < 0
                    ? 0
                    : packet.contents().get(lockedMapSlot).getCount();
                FinishedMapDepositRecoveryPolicy.Decision depositDecision =
                    FinishedMapDepositRecoveryPolicy.decide(
                        mapHandoffStage,
                        lockedMapSlot >= 0,
                        packetContainsChestMap(
                            packet,
                            handoffLockedMapId
                        ),
                        packetPlayerFilledMapCount(packet)
                            > expectedPlayerMapCount,
                        packetPlayerItemCount(packet, Items.MAP) != 0
                            || packetPlayerItemCount(
                                packet,
                                Items.GLASS_PANE
                            ) != 0
                    );
                if (depositDecision
                    == FinishedMapDepositRecoveryPolicy.Decision.FAIL) {
                    failMapHandoff(
                        "The finished-map inventory snapshot is ambiguous "
                            + "or the exact locked map disappeared before "
                            + "a durable deposit request."
                    );
                    return;
                }
                if (depositDecision
                    == FinishedMapDepositRecoveryPolicy.Decision.COMPLETE) {
                    completeMapDeposit();
                    break;
                }
                if (mapHandoffStage
                    == MapHandoffStage.LOCKED_MAP_CONFIRMED) {
                    mapHandoffStage =
                        MapHandoffStage.DEPOSIT_REQUESTED;
                    if (!persistFileCoordinationCheckpoint(
                        "finished-map-deposit-requested"
                    )) {
                        return;
                    }
                }
                state = State.AwaitFinishedMapDepositConfirmation;
                awaitServerInventoryUpdate();
                Utils.performAuthoritativeInventoryClick(
                    packet.syncId(),
                    lockedMapSlot,
                    0,
                    SlotActionType.QUICK_MOVE
                );
                timeoutTicks = Math.max(10, postRestockDelay.get());
                interactTimeout = retryInteractTimer.get();
                break;
            case AwaitFinishedMapDepositConfirmation:
                interactTimeout = 0;
                if (!isExpectedFinishedMapHandler(packet)) {
                    closeNextInvPacket = false;
                    return;
                }
                if (mapHandoffStage
                    != MapHandoffStage.DEPOSIT_REQUESTED) {
                    failMapHandoff(
                        "Finished-map deposit confirmation arrived in "
                            + "unexpected stage " + mapHandoffStage + "."
                    );
                    return;
                }
                if (!mapHandoffStage.hasValidMapIds(
                    handoffSourceMapId,
                    handoffLockedMapId
                )) {
                    failMapHandoff(
                        "Finished-map deposit confirmation lost its exact "
                            + "map identities."
                    );
                    return;
                }
                int retryLockedMapSlot = packetPlayerMapSlot(
                    packet,
                    handoffLockedMapId
                );
                int retryExpectedPlayerMapCount =
                    retryLockedMapSlot < 0
                        ? 0
                        : packet.contents()
                            .get(retryLockedMapSlot).getCount();
                FinishedMapDepositRecoveryPolicy.Decision retryDecision =
                    FinishedMapDepositRecoveryPolicy.decide(
                        mapHandoffStage,
                        retryLockedMapSlot >= 0,
                        packetContainsChestMap(
                            packet,
                            handoffLockedMapId
                        ),
                        packetPlayerFilledMapCount(packet)
                            > retryExpectedPlayerMapCount,
                        packetPlayerItemCount(packet, Items.MAP) != 0
                            || packetPlayerItemCount(
                                packet,
                                Items.GLASS_PANE
                            ) != 0
                    );
                if (retryDecision
                    == FinishedMapDepositRecoveryPolicy.Decision.FAIL) {
                    failMapHandoff(
                        "The finished-map deposit confirmation contains "
                            + "unexpected map items."
                    );
                    return;
                }
                if (retryDecision
                    == FinishedMapDepositRecoveryPolicy.Decision.COMPLETE) {
                    // The map may already have travelled through the
                    // connected destination storage.
                    completeMapDeposit();
                    break;
                }
                awaitServerInventoryUpdate();
                Utils.performAuthoritativeInventoryClick(
                    packet.syncId(),
                    retryLockedMapSlot,
                    0,
                    SlotActionType.QUICK_MOVE
                );
                timeoutTicks = Math.max(
                    10,
                    postRestockDelay.get()
                );
                interactTimeout = retryInteractTimer.get();
                break;
            case AwaitMapHandoffRecoveryProbeResponse:
                interactTimeout = 0;
                if (!isExpectedFinishedMapHandler(packet)) {
                    closeNextInvPacket = false;
                    return;
                }
                mc.player.closeHandledScreen();
                resumeMapHandoffFromCheckpoint();
                break;
            case AwaitUsedToolChestResponse:
                interactTimeout = 0;
                handleUsedToolDepositSnapshot(packet);
                break;
        }
    }

    private void handleUsedToolDepositSnapshot(
        InventoryS2CPacket packet
    ) {
        if (!isExpectedUsedToolHandler(packet)) {
            debugLog(
                "UsedToolDeposit",
                "ignored handler snapshot sync=" + packet.syncId()
                    + " slots=" + packet.contents().size()
                    + " activeChest=" + activeUsedToolDepositChest
                    + " interactedChest=" + lastInteractedChest
            );
            closeNextInvPacket = false;
            return;
        }
        debugLog(
            "UsedToolDeposit",
            "accepted handler snapshot sync=" + packet.syncId()
                + " slots=" + packet.contents().size()
                + " sequence=" + serverInventoryUpdateSequence
                + " activeChest=" + activeUsedToolDepositChest
                + " plannedItems=" + currentUsedToolDepositItems
        );

        if (pendingUsedToolDeposit != null) {
            PendingUsedToolDeposit pending =
                pendingUsedToolDeposit;
            if (pending.syncId() != packet.syncId()) {
                failInventoryTransaction(
                    "Used-tool deposit screen changed before the "
                        + "server confirmed the transfer."
                );
                return;
            }
            if (serverInventoryUpdateSequence
                <= pending.submittedAfterSequence()) {
                debugLog(
                    "UsedToolDeposit",
                    "waiting for newer confirmation sequence current="
                        + serverInventoryUpdateSequence
                        + " submittedAfter="
                        + pending.submittedAfterSequence()
                        + " handlerSlot=" + pending.handlerSlot()
                        + " attempt=" + pending.attempts()
                );
                closeNextInvPacket = false;
                return;
            }
            int playerStart = packet.contents().size() - 36;
            if (pending.handlerSlot() < playerStart
                || pending.handlerSlot()
                    >= packet.contents().size()) {
                failInventoryTransaction(
                    "Used-tool deposit source is no longer a player "
                        + "inventory slot in the authoritative handler."
                );
                return;
            }

            ItemStack observed =
                packet.contents().get(pending.handlerSlot());
            boolean sourceChanged = observed.isEmpty()
                || !inventoryStackIdentity(observed)
                    .equals(pending.before());
            debugLog(
                "UsedToolDeposit",
                "confirmation handlerSlot=" + pending.handlerSlot()
                    + " before={" + pending.before() + "}"
                    + " observed={" + inventoryStackIdentity(observed)
                    + "} changed=" + sourceChanged
                    + " attempt=" + pending.attempts()
            );
            if (!sourceChanged) {
                if (pending.attempts()
                    >= INVENTORY_TRANSACTION_MAX_ATTEMPTS) {
                    failInventoryTransaction(
                        "Server rejected "
                            + INVENTORY_TRANSACTION_MAX_ATTEMPTS
                            + " used-tool deposit attempts."
                    );
                    return;
                }
                debugLog(
                    "UsedToolDeposit",
                    "server made no progress; retrying handlerSlot="
                        + pending.handlerSlot()
                        + " nextAttempt=" + (pending.attempts() + 1)
                );
                submitUsedToolDeposit(
                    packet.syncId(),
                    pending.handlerSlot(),
                    observed,
                    pending.attempts() + 1
                );
                return;
            }
            debugLog(
                "UsedToolDeposit",
                "server confirmed transfer from handlerSlot="
                    + pending.handlerSlot()
            );
            pendingUsedToolDeposit = null;
        }

        int playerStart = packet.contents().size() - 36;
        for (int slot = playerStart;
             slot < packet.contents().size();
             slot++) {
            ItemStack stack = packet.contents().get(slot);
            int playerSlot =
                handlerPlayerSlot(slot, playerStart);
            if (ToolUtils.isTool(stack)
                && (currentUsedToolDepositSlots.contains(playerSlot)
                    || (currentUsedToolDepositSlots.isEmpty()
                        && currentUsedToolDepositItems.contains(
                            stack.getItem()
                        )))) {
                debugLog(
                    "UsedToolDeposit",
                    "selected next player handlerSlot=" + slot
                        + " playerSlot=" + playerSlot
                        + " item="
                        + Registries.ITEM.getId(stack.getItem())
                        + " count=" + stack.getCount()
                        + " damage=" + stack.getDamage()
                );
                submitUsedToolDeposit(
                    packet.syncId(),
                    slot,
                    stack,
                    1
                );
                return;
            }
        }

        pendingUsedToolDeposit = null;
        debugLog(
            "UsedToolDeposit",
            "chest plan complete chest="
                + activeUsedToolDepositChest
                + " remainingCheckpoints=" + checkpoints.size()
        );
        usedToolDepositPlan.remove(activeUsedToolDepositChest);
        usedToolDepositSlotPlan.remove(activeUsedToolDepositChest);
        activeUsedToolDepositChest = null;
        currentUsedToolDepositItems.clear();
        currentUsedToolDepositSlots.clear();
        if (checkpoints.isEmpty()) {
            state = State.AwaitNBTFile;
            debugLog(
                "UsedToolDeposit",
                "all used-tool plans complete; resuming AwaitNBTFile"
            );
            completeSlavePostMiningCleanup();
        } else {
            timeoutTicks = postRestockDelay.get();
            state = State.Walking;
            debugLog(
                "UsedToolDeposit",
                "resuming checkpoint travel after delay="
                    + timeoutTicks
            );
        }
    }

    private void submitUsedToolDeposit(
        int syncId,
        int handlerSlot,
        ItemStack before,
        int attempts
    ) {
        pendingUsedToolDeposit =
            new PendingUsedToolDeposit(
                syncId,
                handlerSlot,
                inventoryStackIdentity(before),
                serverInventoryUpdateSequence,
                clientActionTick,
                attempts
            );
        closeNextInvPacket = false;
        debugLog(
            "UsedToolDeposit",
            "dispatching QUICK_MOVE sync=" + syncId
                + " handlerSlot=" + handlerSlot
                + " item=" + Registries.ITEM.getId(before.getItem())
                + " count=" + before.getCount()
                + " damage=" + before.getDamage()
                + " submittedAfter="
                    + serverInventoryUpdateSequence
                + " attempt=" + attempts
        );
        Utils.performAuthoritativeInventoryClick(
            syncId,
            handlerSlot,
            0,
            SlotActionType.QUICK_MOVE
        );
    }

    private void recordDumpFullInventoryObservation(
        InventoryS2CPacket packet
    ) {
        if (state != State.Dumping
            || pendingDumpTransfer == null) {
            return;
        }
        if (packet.syncId() != 0
            || packet.contents().size() <= 44) {
            debugLog(
                "Dump",
                "ignored non-player full snapshot sync="
                    + packet.syncId()
                    + " slots=" + packet.contents().size()
            );
            return;
        }
        if (serverInventoryUpdateSequence
            <= pendingDumpTransfer.submittedAfterSequence()) {
            debugLog(
                "Dump",
                "waiting for newer confirmation sequence current="
                    + serverInventoryUpdateSequence
                    + " submittedAfter="
                    + pendingDumpTransfer.submittedAfterSequence()
                    + " playerSlot="
                    + pendingDumpTransfer.playerSlot()
            );
            return;
        }

        PendingDumpTransfer pending = pendingDumpTransfer;
        ItemStack observed = playerInventoryStack(
            packet,
            pending.playerSlot()
        );
        debugLog(
            "Dump",
            "confirmation playerSlot=" + pending.playerSlot()
                + " before={" + pending.before() + "}"
                + " observed={" + inventoryStackIdentity(observed)
                + "} attempt=" + pending.attempts()
        );
        if (observed.isEmpty()) {
            debugLog(
                "Dump",
                "server confirmed playerSlot="
                    + pending.playerSlot() + " is clear"
            );
            pendingDumpTransfer = null;
            timeoutTicks = invActionDelay.get();
            return;
        }
        if (!inventoryStackIdentity(observed)
            .equals(pending.before())) {
            // The authoritative slot changed independently. Re-run the dump
            // planner instead of dropping a newly arrived stack under an old
            // transaction identity.
            debugLog(
                "Dump",
                "slot identity changed independently; abandoning old "
                    + "transaction and replanning playerSlot="
                    + pending.playerSlot()
            );
            pendingDumpTransfer = null;
            return;
        }
        if (pending.attempts()
            >= INVENTORY_TRANSACTION_MAX_ATTEMPTS) {
            failInventoryTransaction(
                "Server rejected "
                    + INVENTORY_TRANSACTION_MAX_ATTEMPTS
                    + " authoritative dump attempts for slot "
                    + pending.playerSlot() + "."
            );
            return;
        }
        debugLog(
            "Dump",
            "server made no progress; staging retry playerSlot="
                + pending.playerSlot()
                + " nextAttempt=" + (pending.attempts() + 1)
        );
        pendingDumpTransfer = new PendingDumpTransfer(
            pending.playerSlot(),
            inventoryStackIdentity(observed),
            serverInventoryUpdateSequence,
            -1L,
            pending.attempts() + 1
        );
    }

    private void submitDumpTransfer(
        int playerSlot,
        ItemStack before,
        int attempts
    ) {
        if (ToolUtils.isTool(before)) {
            pendingDumpTransfer = null;
            warning(
                "Refusing to send "
                    + before.getName().getString()
                    + " to the material DumpStation; tools may only "
                    + "be transferred to registered used-tool chests."
            );
            debugLog(
                "Dump",
                "blocked tool transfer playerSlot=" + playerSlot
                    + " item="
                        + Registries.ITEM.getId(before.getItem())
                    + " damage=" + before.getDamage()
            );
            return;
        }
        if (mc.player.currentScreenHandler.syncId != 0) {
            failInventoryTransaction(
                "Cannot submit an authoritative dump while a container "
                    + "screen handler is still open."
            );
            return;
        }
        pendingDumpTransfer = new PendingDumpTransfer(
            playerSlot,
            inventoryStackIdentity(before),
            serverInventoryUpdateSequence,
            clientActionTick,
            attempts
        );
        int handlerSlot = playerSlot < 9
            ? 36 + playerSlot
            : playerSlot;
        debugLog(
            "Dump",
            "dispatching THROW sync=0 playerSlot=" + playerSlot
                + " handlerSlot=" + handlerSlot
                + " item=" + Registries.ITEM.getId(before.getItem())
                + " count=" + before.getCount()
                + " damage=" + before.getDamage()
                + " submittedAfter="
                    + serverInventoryUpdateSequence
                + " attempt=" + attempts
        );
        Utils.performAuthoritativeInventoryClick(
            0,
            handlerSlot,
            1,
            SlotActionType.THROW
        );
    }

    private boolean tickPendingDumpTransfer() {
        PendingDumpTransfer pending = pendingDumpTransfer;
        if (pending == null) return false;
        if (pending.submittedAtTick() < 0) {
            ItemStack stack = mc.player.getInventory().getStack(
                pending.playerSlot()
            );
            if (stack.isEmpty()) {
                debugLog(
                    "Dump",
                    "staged retry no longer needed; local playerSlot="
                        + pending.playerSlot() + " is empty"
                );
                pendingDumpTransfer = null;
                return false;
            }
            if (ToolUtils.isTool(stack)) {
                debugLog(
                    "Dump",
                    "cancelled staged retry because playerSlot="
                        + pending.playerSlot()
                        + " now contains tool="
                        + Registries.ITEM.getId(stack.getItem())
                );
                pendingDumpTransfer = null;
                return false;
            }
            debugLog(
                "Dump",
                "dispatching staged retry playerSlot="
                    + pending.playerSlot()
                    + " attempt=" + pending.attempts()
            );
            submitDumpTransfer(
                pending.playerSlot(),
                stack,
                pending.attempts()
            );
            return true;
        }
        if (clientActionTick - pending.submittedAtTick()
            >= retryInteractTimer.get()) {
            debugLog(
                "Dump",
                "confirmation timeout playerSlot="
                    + pending.playerSlot()
                    + " submittedTick=" + pending.submittedAtTick()
                    + " deadlineTicks=" + retryInteractTimer.get()
            );
            failInventoryTransaction(
                "Server did not confirm the authoritative dump before "
                    + "the bounded timeout."
            );
        }
        return true;
    }

    private void failInventoryTransaction(String reason) {
        debugLog(
            "Inventory",
            "fatal transaction failure reason=" + reason
                + " pendingDump=" + pendingDumpTransfer
                + " pendingUsedTool=" + pendingUsedToolDeposit
                + " activeUsedToolChest="
                    + activeUsedToolDepositChest
        );
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        activeUsedToolDepositChest = null;
        closeNextInvPacket = true;
        error(reason);
        stopMovement();
        toggle();
    }

    private void debugLog(String category, String message) {
        if (!debugPrints.get()) {
            debugLogLimiter.clear();
            return;
        }
        Optional<CategorizedDebugLogLimiter.Emission> emission =
            debugLogLimiter.submit(
                clientActionTick,
                debugPrintInterval.get(),
                category,
                message
            );
        if (emission.isEmpty()) return;

        CategorizedDebugLogLimiter.Emission emitted =
            emission.orElseThrow();
        String coalesced = emitted.suppressedMessages() == 0
            ? ""
            : " | coalesced=" + emitted.suppressedMessages()
                + " intermediate " + category + " messages";
        info(
            "%s",
            "[Debug][" + category + "] tick=" + clientActionTick
                + " state=" + state + " | " + emitted.message()
                + coalesced
        );
    }

    private void debugRestock(String message) {
        debugLog(
            "Restock",
            "phase=" + restockConfirmationPhase + " | " + message
        );
    }

    private void debugRestockPacket(
        String origin,
        InventoryS2CPacket packet
    ) {
        if (!debugPrints.get()) return;
        if (restockList.isEmpty() || packet.contents().size() < 36) {
            debugRestock(
                origin + " sync=" + packet.syncId()
                    + " slots=" + packet.contents().size()
                    + " plan=empty"
            );
            return;
        }

        Item requestedItem = restockList.getFirst().item();
        int readyCount = readyRestockSourceCount(requestedItem);
        int containerSlots = packet.contents().size() - 36;
        StringBuilder contents = new StringBuilder();
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = packet.contents().get(slot);
            if (stack.isEmpty()) continue;
            ServerInventoryTransferSnapshot.SlotState observation =
                restockSlotState(requestedItem, stack, -1);
            if (contents.length() > 0) contents.append(", ");
            contents.append(slot).append('=')
                .append(Registries.ITEM.getId(stack.getItem()))
                .append('x').append(stack.getCount()).append(':');
            if (observation.compatibleCount() <= 0) {
                contents.append("ignored");
            } else if (observation.compatibleCount() >= readyCount) {
                contents.append("ready");
            } else {
                contents.append("partial");
            }
        }
        if (contents.length() == 0) contents.append("empty");
        debugRestock(
            origin + " sync=" + packet.syncId()
                + " requested=" + Registries.ITEM.getId(requestedItem)
                + " readyCount=" + readyCount
                + " container={" + contents + "}"
        );
    }

    private void debugRestockSnapshot(String origin) {
        if (!debugPrints.get()) return;
        if (restockList.isEmpty()
            || !restockInventorySnapshot.initialized()) {
            debugRestock(origin + " snapshot=unavailable");
            return;
        }

        RestockDemand<Item> demand = restockList.getFirst();
        int readyCount = readyRestockSourceCount(demand.item());
        StringBuilder sources = new StringBuilder();
        for (int slot = 0;
             slot < restockInventorySnapshot.containerSlotCount();
             slot++) {
            int count =
                restockInventorySnapshot.compatibleCountAt(slot);
            if (count <= 0) continue;
            if (sources.length() > 0) sources.append(", ");
            sources.append(slot).append('=').append(count)
                .append(count >= readyCount ? ":ready" : ":partial");
        }
        if (sources.length() == 0) sources.append("none");

        PendingRestockTransfer pending = pendingRestockTransfer;
        String pendingDescription = pending == null
            ? "none"
            : "slot=" + pending.sourceSlot()
                + ",beforeSource=" + pending.beforeSourceCount()
                + ",beforePlayer=" + pending.beforePlayerCount()
                + ",revision=" + pending.inventoryRevision()
                + ",submittedTick=" + pending.submittedAtTick()
                + ",attempt="
                    + pending.consecutiveNoProgressAttempts();
        debugRestock(
            origin + " sync=" + restockInventorySnapshot.syncId()
                + " item=" + Registries.ITEM.getId(demand.item())
                + " player="
                    + restockInventorySnapshot.compatiblePlayerCount()
                + " target=" + demand.targetCompatiblePlayerCount()
                + " remaining=" + demand.remainingAmount()
                + " capacity="
                    + restockInventorySnapshot.playerHasCapacity()
                + " readyCount=" + readyCount
                + " cursorAfter=" + restockSourceSearchAfterSlot
                + " sources={" + sources + "}"
                + " pending={" + pendingDescription + "}"
        );
    }

    private void handleRestockInventoryPacket(
        InventoryS2CPacket packet
    ) {
        if (restockList.isEmpty()) {
            debugRestock(
                "discarding full packet because the logical plan is empty"
            );
            pendingRestockTransfer = null;
            restockInventorySnapshot.clear();
            restockSnapshotUpdateSequence = -1L;
            restockConfirmationPhase = RestockConfirmationPhase.NONE;
            resetRestockRefillWait();
            resetRestockTransferTracking();
            return;
        }

        int currentHandlerSyncId =
            mc.player == null
                    || mc.player.currentScreenHandler == null
                ? -1
                : mc.player.currentScreenHandler.syncId;
        ServerInventoryTransferSnapshot.HandlerDisposition disposition =
            restockInventorySnapshot.handlerDisposition(
                packet.syncId(),
                currentHandlerSyncId
            );
        if (disposition
            == ServerInventoryTransferSnapshot.HandlerDisposition.REJECTED) {
            debugRestock(
                "full packet validation failed packetSync="
                    + packet.syncId() + " snapshotSync="
                    + restockInventorySnapshot.syncId()
                    + " currentSync=" + currentHandlerSyncId
                    + " disposition=" + disposition
            );
            failRestockTransfer(
                "The restock response did not match the authoritative "
                    + "screen-handler snapshot."
            );
            return;
        }
        if (disposition
            == ServerInventoryTransferSnapshot.HandlerDisposition
                .ACCEPTED_HANDLER_NOT_CURRENT) {
            recoverAcceptedRestockResponseHandler(
                packet.syncId(),
                currentHandlerSyncId
            );
            return;
        }
        restockHandlerLeaseRecoveryAttempts = 0;
        debugRestockSnapshot("handling authoritative full response");
        processRestockInventorySnapshot(true);
    }

    /**
     * Reacquires the exact registered chest when a full response passed the
     * receive-time handler check but its screen closed during the configured
     * pre-action delay.
     *
     * <p>The accepted response is not treated as a rejected transfer. No click
     * is sent against the stale handler either: the snapshot is discarded and
     * a fresh handler-bound snapshot is required before source selection or
     * confirmation continues.</p>
     */
    private void recoverAcceptedRestockResponseHandler(
        int acceptedSyncId,
        int currentHandlerSyncId
    ) {
        if (restockList.isEmpty()
            || !isRegisteredRestockChest(
                restockList.getFirst().item(),
                lastInteractedChest
            )) {
            failRestockTransfer(
                "The accepted restock screen closed and its registered chest "
                    + "could not be reopened safely."
            );
            return;
        }
        if (restockHandlerLeaseRecoveryAttempts
            >= INVENTORY_TRANSACTION_MAX_ATTEMPTS) {
            failRestockTransfer(
                "The registered restock chest closed "
                    + INVENTORY_TRANSACTION_MAX_ATTEMPTS
                    + " times before its delayed inventory action."
            );
            return;
        }

        restockHandlerLeaseRecoveryAttempts++;
        boolean awaitingSubmittedTransfer =
            pendingRestockTransfer != null
                && pendingRestockTransfer.submittedAtTick() >= 0;
        debugRestock(
            "accepted full response lost live handler acceptedSync="
                + acceptedSyncId + " currentSync=" + currentHandlerSyncId
                + " chest=" + lastInteractedChest.toShortString()
                + " recoveryAttempt="
                    + restockHandlerLeaseRecoveryAttempts
                + " awaitingSubmittedTransfer="
                    + awaitingSubmittedTransfer
                + "; reopening exact registered chest"
        );

        if (currentHandlerSyncId > 0) {
            closeCurrentContainerHandler();
        }
        restockInventorySnapshot.clear();
        restockSnapshotUpdateSequence = -1L;
        restockBacklogSlots.clear();
        if (awaitingSubmittedTransfer) {
            restockConfirmationPhase =
                RestockConfirmationPhase.AWAITING_REOPEN_SNAPSHOT;
        } else {
            pendingRestockTransfer = null;
            restockConfirmationPhase =
                RestockConfirmationPhase
                    .AWAITING_HANDLER_REOPEN_SNAPSHOT;
        }
        closeNextInvPacket = false;
        interactWithBlock(lastInteractedChest);
    }

    /**
     * Advances one restock transaction from the latest server-authoritative
     * screen snapshot. Printer-owned QUICK_MOVE clicks deliberately force a
     * full snapshot; individual corrective slot updates remain useful while a
     * bounded close/reopen confirmation is in progress.
     */
    private boolean processRestockInventorySnapshot(
        boolean fullSnapshot
    ) {
        if (restockList.isEmpty()
            || !restockInventorySnapshot.initialized()) {
            debugRestock(
                "cannot process snapshot full=" + fullSnapshot
                    + " planEmpty=" + restockList.isEmpty()
                    + " initialized="
                        + restockInventorySnapshot.initialized()
            );
            return false;
        }

        RestockDemand<Item> demand = restockList.getFirst();
        Item requestedItem = demand.item();
        int confirmedPlayerCount =
            restockInventorySnapshot.compatiblePlayerCount();
        boolean provisionalTransfer =
            pendingRestockTransfer != null
                && pendingRestockTransfer.submittedAtTick() < 0;
        if (pendingRestockTransfer == null || provisionalTransfer) {
            observeRestockPlayerCount(confirmedPlayerCount);
        }
        RestockDemand<Item> reconciled =
            demand.reconcile(confirmedPlayerCount);
        restockList.set(0, reconciled);
        debugRestockSnapshot(
            "processing " + (fullSnapshot ? "full" : "slot")
                + " observation"
        );
        if (pendingRestockTransfer != null) {
            PendingRestockTransfer pending = pendingRestockTransfer;
            if (pending.item() != requestedItem) {
                failRestockTransfer(
                    "Restock transaction item changed from "
                        + pending.item().getName().getString() + " to "
                        + requestedItem.getName().getString() + "."
                );
                return true;
            }
            if (pending.syncId() != restockInventorySnapshot.syncId()) {
                failRestockTransfer(
                    "Restock transaction screen changed from "
                        + pending.syncId() + " to "
                        + restockInventorySnapshot.syncId() + "."
                );
                return true;
            }
            if (pending.submittedAtTick() < 0
                || restockSnapshotUpdateSequence
                    <= pending.inventoryRevision()) {
                debugRestock(
                    "pending transfer not yet dispatch-confirmable"
                        + " submittedTick=" + pending.submittedAtTick()
                        + " snapshotSequence="
                            + restockSnapshotUpdateSequence
                        + " requiredAfter="
                            + pending.inventoryRevision()
                );
                closeNextInvPacket = false;
                return false;
            }
            boolean confirmedProgress = fullSnapshot
                ? restockInventorySnapshot
                    .confirmsCompatiblePlayerProgress(
                        pending.beforePlayerCount()
                    )
                : restockInventorySnapshot.confirmsTransfer(
                    pending.sourceSlot(),
                    pending.beforeSourceCount(),
                    pending.beforePlayerCount()
                );
            debugRestock(
                "confirmation evaluated mode="
                    + (fullSnapshot ? "full-player-progress"
                        : "paired-slot-updates")
                    + " sourceSlot=" + pending.sourceSlot()
                    + " source=" + pending.beforeSourceCount()
                    + "->"
                        + restockInventorySnapshot.compatibleCountAt(
                            pending.sourceSlot()
                        )
                    + " player=" + pending.beforePlayerCount()
                    + "->" + confirmedPlayerCount
                    + " confirmed=" + confirmedProgress
                    + " remaining=" + reconciled.remainingAmount()
                    + " attempt="
                        + pending.consecutiveNoProgressAttempts()
            );
            if (reconciled.remainingAmount() == 0) {
                debugRestock(
                    "absolute target reached; completing item demand"
                );
                pendingRestockTransfer = null;
                restockConfirmationPhase =
                    RestockConfirmationPhase.NONE;
                resetRestockRefillWait();
                closeNextInvPacket = true;
                endRestocking();
                return true;
            }
            if (!confirmedProgress) {
                // An individual update can expose only one half of the
                // transfer. A newer forced full snapshot is coherent, but an
                // auto-supplier may already have restored the source slot; in
                // that case compatible player progress is the proof.
                if (!fullSnapshot) {
                    debugRestock(
                        "partial observation has only one transfer half;"
                            + " waiting for the forced full snapshot"
                    );
                    return false;
                }
                if (pending.consecutiveNoProgressAttempts()
                    >= RESTOCK_MAX_CONSECUTIVE_NO_PROGRESS_ATTEMPTS) {
                    failRestockTransfer(
                        RESTOCK_MAX_CONSECUTIVE_NO_PROGRESS_ATTEMPTS
                            + " consecutive authoritative snapshots "
                            + "showed no compatible "
                            + requestedItem.getName().getString()
                            + " player-inventory progress."
                    );
                    return true;
                }
                restockNextNoProgressAttempt =
                    pending.consecutiveNoProgressAttempts() + 1;
                restockLastObservedCompatiblePlayerCount =
                    confirmedPlayerCount;
                debugRestock(
                    "no compatible player progress; nextAttempt="
                        + restockNextNoProgressAttempt
                );
            } else {
                restockNextNoProgressAttempt = 1;
                restockLastObservedCompatiblePlayerCount =
                    confirmedPlayerCount;
                debugRestock(
                    "authoritative player progress confirmed;"
                        + " no-progress streak reset"
                );
            }
            pendingRestockTransfer = null;
            restockConfirmationPhase = RestockConfirmationPhase.NONE;
        }

        if (reconciled.remainingAmount() == 0) {
            debugRestock(
                "demand reached by the latest authoritative inventory"
                    + " observation"
            );
            resetRestockRefillWait();
            closeNextInvPacket = true;
            endRestocking();
            return true;
        }

        if (!restockInventorySnapshot.playerHasCapacity()) {
            debugRestock(
                "no managed player capacity; scheduling dump consolidation"
            );
            resetRestockRefillWait();
            warning(
                "No player slot can receive "
                    + requestedItem.getName().getString()
                    + "; consolidating the circular inventory plan."
            );
            pendingRestockTransfer = null;
            closeNextInvPacket = true;
            checkpoints.add(
                0,
                new Pair(
                    dumpStation.getLeft(),
                    new Pair("dump", null)
                )
            );
            state = State.Walking;
            return true;
        }

        int sourceSlot =
            restockInventorySnapshot.nextCompatibleContainerSlot(
                restockSourceSearchAfterSlot,
                readyRestockSourceCount(requestedItem)
            );
        debugRestock(
            "source scan item=" + Registries.ITEM.getId(requestedItem)
                + " readyCount="
                    + readyRestockSourceCount(requestedItem)
                + " cursorAfter=" + restockSourceSearchAfterSlot
                + " selectedSlot=" + sourceSlot
        );
        if (sourceSlot < 0) {
            debugRestock(
                "no ready compatible source in any container slot;"
                    + " entering or continuing refill wait"
            );
            return beginOrContinueRestockRefillWait(requestedItem);
        }
        resetRestockRefillWait();
        pendingRestockTransfer = new PendingRestockTransfer(
            requestedItem,
            restockInventorySnapshot.syncId(),
            sourceSlot,
            restockInventorySnapshot.compatibleCountAt(sourceSlot),
            confirmedPlayerCount,
            serverInventoryUpdateSequence,
            -1L,
            restockNextNoProgressAttempt
        );
        restockConfirmationPhase = RestockConfirmationPhase.NONE;
        restockBacklogSlots.add(sourceSlot);
        closeNextInvPacket = false;
        debugRestock(
            "queued provisional transfer item="
                + Registries.ITEM.getId(requestedItem)
                + " sourceSlot=" + sourceSlot
                + " sourceCount="
                    + restockInventorySnapshot.compatibleCountAt(
                        sourceSlot
                    )
                + " beforePlayer=" + confirmedPlayerCount
                + " attempt=" + restockNextNoProgressAttempt
        );
        return true;
    }

    private boolean beginOrContinueRestockRefillWait(
        Item requestedItem
    ) {
        int timeout = restockRefillTimeout.get();
        if (timeout <= 0) {
            finishRestockRefillWaitTimeout(requestedItem);
            return true;
        }
        RestockRefillWaitPolicy.Decision decision =
            RestockRefillWaitPolicy.evaluate(
                restockRefillWaitState == null
                    ? Optional.empty()
                    : restockRefillWaitState,
                false,
                clientActionTick,
                timeout,
                restockRefillProbeIntervalTicks()
            );
        debugRestock(
            "refill wait evaluated action=" + decision.action()
                + " timeout=" + timeout
                + " waitState=" + decision.state().map(wait ->
                    "started=" + wait.startedAtTick()
                        + ",nextProbe=" + wait.nextProbeAtTick()
                ).orElse("none")
        );
        return applyRestockRefillWaitDecision(
            requestedItem,
            decision
        );
    }

    private boolean tickRestockRefillWait() {
        boolean waiting =
            restockConfirmationPhase
                    == RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL
                || restockConfirmationPhase
                    == RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL_SNAPSHOT;
        if (state != State.AwaitRestockResponse
            || !waiting
            || toBeHandledInvPacket != null
            || restockList.isEmpty()
            || !restockInventorySnapshot.initialized()) {
            return false;
        }

        Item requestedItem = restockList.getFirst().item();
        boolean sourceAvailable =
            restockInventorySnapshot
                .nextCompatibleContainerSlot(
                    restockSourceSearchAfterSlot,
                    readyRestockSourceCount(requestedItem)
                ) >= 0;
        int timeout = restockRefillTimeout.get();
        if (timeout <= 0) {
            finishRestockRefillWaitTimeout(requestedItem);
            return true;
        }
        RestockRefillWaitPolicy.Decision decision =
            RestockRefillWaitPolicy.evaluate(
                restockRefillWaitState == null
                    ? Optional.empty()
                    : restockRefillWaitState,
                sourceAvailable,
                clientActionTick,
                timeout,
                restockRefillProbeIntervalTicks(),
                restockConfirmationPhase
                    == RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL_SNAPSHOT
            );
        if (decision.action() != RestockRefillWaitPolicy.Action.WAIT) {
            debugRestock(
                "refill tick action=" + decision.action()
                    + " sourceAvailable=" + sourceAvailable
                    + " waitState=" + decision.state().map(wait ->
                        "started=" + wait.startedAtTick()
                            + ",nextProbe=" + wait.nextProbeAtTick()
                    ).orElse("none")
            );
        }
        if (decision.action()
            == RestockRefillWaitPolicy.Action.SOURCE_AVAILABLE) {
            debugRestock(
                "ready source observed; leaving refill wait and rescanning"
            );
            resetRestockRefillWait();
            restockConfirmationPhase =
                RestockConfirmationPhase.NONE;
            return processRestockInventorySnapshot(false);
        }
        return applyRestockRefillWaitDecision(
            requestedItem,
            decision
        );
    }

    private boolean applyRestockRefillWaitDecision(
        Item requestedItem,
        RestockRefillWaitPolicy.Decision decision
    ) {
        restockRefillWaitState = decision.state();
        switch (decision.action()) {
            case SOURCE_AVAILABLE -> {
                throw new IllegalStateException(
                    "Available refill sources must be replanned directly."
                );
            }
            case START_WAIT -> {
                debugRestock(
                    "refill wait started for item="
                        + Registries.ITEM.getId(requestedItem)
                        + " timeout=" + restockRefillTimeout.get()
                );
                restockConfirmationPhase =
                    RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL;
                closeNextInvPacket = false;
                stopMovement();
                return true;
            }
            case WAIT -> {
                // Preserve the outstanding-probe phase until its full
                // handler snapshot arrives or the bounded policy schedules
                // another exact-chest probe.
                if (restockConfirmationPhase
                    != RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL_SNAPSHOT) {
                    restockConfirmationPhase =
                        RestockConfirmationPhase
                            .AWAITING_SOURCE_REFILL;
                }
                closeNextInvPacket = false;
                stopMovement();
                return true;
            }
            case PROBE -> {
                if (!isRegisteredRestockChest(
                    requestedItem,
                    lastInteractedChest
                )) {
                    failRestockTransfer(
                        "Cannot probe the exact registered "
                            + requestedItem.getName().getString()
                            + " chest while waiting for its refill."
                    );
                    return true;
                }
                closeCurrentContainerHandler();
                debugRestock(
                    "opening exact registered chest probe chest="
                        + lastInteractedChest.toShortString()
                        + " item="
                            + Registries.ITEM.getId(requestedItem)
                );
                restockConfirmationPhase =
                    RestockConfirmationPhase
                        .AWAITING_SOURCE_REFILL_SNAPSHOT;
                closeNextInvPacket = false;
                interactWithBlock(lastInteractedChest);
                // Refill probes have their own anchored retry clock. The
                // generic chest-open retry must not issue an independent
                // second probe.
                interactTimeout = 0;
                return true;
            }
            case TIMED_OUT -> {
                debugRestock(
                    "refill wait reached anchored deadline"
                );
                finishRestockRefillWaitTimeout(requestedItem);
                return true;
            }
        }
        throw new IllegalStateException(
            "Unhandled restock refill wait decision."
        );
    }

    private void finishRestockRefillWaitTimeout(Item requestedItem) {
        int waitedTicks = restockRefillTimeout.get();
        debugRestock(
            "refill timeout item=" + Registries.ITEM.getId(requestedItem)
                + " waitedTicks=" + waitedTicks
                + " chest=" + lastInteractedChest
        );
        warning(
            "No " + requestedItem.getName().getString()
                + " source refilled within " + waitedTicks
                + " ticks; trying another registered chest."
        );
        resetRestockRefillWait();
        restockConfirmationPhase = RestockConfirmationPhase.NONE;
        closeNextInvPacket = true;
        endRestocking();
    }

    private boolean isRegisteredRestockChest(
        Item item,
        BlockPos chest
    ) {
        if (item == null || chest == null) return false;
        return materialDict.getOrDefault(item, new ArrayList<>())
            .stream()
            .anyMatch(registered ->
                registered.getLeft().equals(chest)
            );
    }

    private boolean isCurrentRestockHandler(int syncId) {
        return syncId > 0
            && mc.player != null
            && mc.player.currentScreenHandler != null
            && mc.player.currentScreenHandler.syncId == syncId;
    }

    private boolean isAwaitingRestockRefill() {
        return restockConfirmationPhase
                == RestockConfirmationPhase.AWAITING_SOURCE_REFILL
            || restockConfirmationPhase
                == RestockConfirmationPhase
                    .AWAITING_SOURCE_REFILL_SNAPSHOT;
    }

    private int restockRefillProbeIntervalTicks() {
        return Math.max(
            RESTOCK_MIN_REFILL_PROBE_INTERVAL_TICKS,
            retryInteractTimer.get()
        );
    }

    private int readyRestockSourceCount(Item requestedItem) {
        return Math.max(1, Utils.maximumStackSize(requestedItem));
    }

    private void resetRestockRefillWait() {
        if (restockRefillWaitState != null
            && restockRefillWaitState.isPresent()) {
            RestockRefillWaitPolicy.State wait =
                restockRefillWaitState.orElseThrow();
            debugRestock(
                "resetting refill wait started=" + wait.startedAtTick()
                    + " nextProbe=" + wait.nextProbeAtTick()
            );
        }
        restockRefillWaitState = Optional.empty();
    }

    private void resetRestockTransferTracking() {
        if (restockNextNoProgressAttempt > 1
            || restockLastObservedCompatiblePlayerCount >= 0
            || restockSourceSearchAfterSlot >= 0) {
            debugRestock(
                "resetting transfer tracking nextAttempt="
                    + restockNextNoProgressAttempt
                    + " lastPlayer="
                        + restockLastObservedCompatiblePlayerCount
                    + " cursorAfter=" + restockSourceSearchAfterSlot
            );
        }
        restockNextNoProgressAttempt = 1;
        restockLastObservedCompatiblePlayerCount = -1;
        restockSourceSearchAfterSlot = -1;
    }

    private void observeRestockPlayerCount(int compatiblePlayerCount) {
        if (compatiblePlayerCount < 0) {
            throw new IllegalArgumentException(
                "Compatible player count cannot be negative."
            );
        }
        int previousCount =
            restockLastObservedCompatiblePlayerCount;
        if (previousCount >= 0
            && compatiblePlayerCount
                > previousCount) {
            restockNextNoProgressAttempt = 1;
        }
        restockLastObservedCompatiblePlayerCount =
            compatiblePlayerCount;
        if (previousCount != compatiblePlayerCount) {
            debugRestock(
                "observed compatible player count " + previousCount
                    + "->" + compatiblePlayerCount
                    + " nextAttempt="
                        + restockNextNoProgressAttempt
            );
        }
    }

    private void closeCurrentContainerHandler() {
        if (mc.player != null
            && mc.player.currentScreenHandler != null
            && mc.player.currentScreenHandler.syncId != 0) {
            debugRestock(
                "closing container handler sync="
                    + mc.player.currentScreenHandler.syncId
            );
            mc.player.closeHandledScreen();
        }
    }

    private boolean tickPendingRestockTransferDispatch() {
        if (restockBacklogSlots.isEmpty()) return false;
        if (state != State.AwaitRestockResponse) {
            failRestockTransfer(
                "A restock transfer remained queued outside the "
                    + "authoritative restock state."
            );
            return true;
        }
        // Let the newest full handler packet replan this provisional click
        // before dispatching against an older source observation.
        if (toBeHandledInvPacket != null) return false;
        if (pendingRestockTransfer == null
            || restockList.isEmpty()
            || !restockInventorySnapshot.initialized()) {
            failRestockTransfer(
                "The queued restock transfer lost its authoritative plan."
            );
            return true;
        }

        PendingRestockTransfer pending = pendingRestockTransfer;
        RestockDemand<Item> demand = restockList.getFirst();
        int sourceSlot = restockBacklogSlots.getFirst();
        if (pending.item() != demand.item()
            || pending.sourceSlot() != sourceSlot) {
            failRestockTransfer(
                "The queued restock source no longer matches its demand."
            );
            return true;
        }
        if (mc.player.currentScreenHandler.syncId
                != pending.syncId()
            || restockInventorySnapshot.syncId()
                != pending.syncId()) {
            failRestockTransfer(
                "The open screen changed before the confirmed restock "
                    + "transfer could be submitted."
            );
            return true;
        }

        int latestPlayerCount =
            restockInventorySnapshot.compatiblePlayerCount();
        observeRestockPlayerCount(latestPlayerCount);
        RestockDemand<Item> reconciled =
            demand.reconcile(latestPlayerCount);
        restockList.set(0, reconciled);
        boolean sourceAvailable =
            restockInventorySnapshot
                .compatibleCountAt(sourceSlot)
                    >= readyRestockSourceCount(demand.item());
        debugRestock(
            "dispatch preflight sourceSlot=" + sourceSlot
                + " sourceCount="
                    + restockInventorySnapshot.compatibleCountAt(
                        sourceSlot
                    )
                + " requiredSourceCount="
                    + readyRestockSourceCount(demand.item())
                + " player=" + latestPlayerCount
                + " remaining=" + reconciled.remainingAmount()
                + " capacity="
                    + restockInventorySnapshot.playerHasCapacity()
                + " ready=" + sourceAvailable
        );
        if (reconciled.remainingAmount() == 0
            || !restockInventorySnapshot.playerHasCapacity()
            || !sourceAvailable) {
            debugRestock(
                "canceling provisional transfer before click reason="
                    + (reconciled.remainingAmount() == 0
                        ? "target-reached"
                        : !restockInventorySnapshot.playerHasCapacity()
                            ? "no-player-capacity"
                            : "source-not-ready")
            );
            restockBacklogSlots.clear();
            pendingRestockTransfer = null;
            restockConfirmationPhase =
                RestockConfirmationPhase.NONE;
            return processRestockInventorySnapshot(false);
        }

        restockBacklogSlots.removeFirst();
        pendingRestockTransfer = new PendingRestockTransfer(
            pending.item(),
            pending.syncId(),
            sourceSlot,
            restockInventorySnapshot.compatibleCountAt(sourceSlot),
            latestPlayerCount,
            serverInventoryUpdateSequence,
            clientActionTick,
            restockNextNoProgressAttempt
        );
        restockSourceSearchAfterSlot = sourceSlot;
        debugRestock(
            "dispatching authoritative QUICK_MOVE sync="
                + pending.syncId() + " sourceSlot=" + sourceSlot
                + " beforeSource="
                    + restockInventorySnapshot.compatibleCountAt(
                        sourceSlot
                    )
                + " beforePlayer=" + latestPlayerCount
                + " attempt=" + restockNextNoProgressAttempt
                + " nextCursorAfter=" + restockSourceSearchAfterSlot
        );
        Utils.performAuthoritativeInventoryClick(
            pending.syncId(),
            sourceSlot,
            1,
            SlotActionType.QUICK_MOVE
        );
        restockConfirmationPhase =
            RestockConfirmationPhase.REOPEN_PENDING;
        timeoutTicks = invActionDelay.get();
        return true;
    }

    private void recordRestockFullInventorySnapshot(
        InventoryS2CPacket packet
    ) {
        if (state != State.AwaitRestockResponse) return;
        if (restockList.isEmpty()
            || packet.syncId() <= 0
            || !isCurrentRestockHandler(packet.syncId())
            || packet.contents().size() < 36) {
            int currentSync = mc.player == null
                || mc.player.currentScreenHandler == null
                ? -1
                : mc.player.currentScreenHandler.syncId;
            debugRestock(
                "ignored full packet sync=" + packet.syncId()
                    + " currentSync=" + currentSync
                    + " slots=" + packet.contents().size()
                    + " planEmpty=" + restockList.isEmpty()
            );
            return;
        }
        Item requestedItem = restockList.getFirst().item();
        debugRestockPacket("received full snapshot", packet);
        ArrayList<ServerInventoryTransferSnapshot.SlotState> contents =
            new ArrayList<>(packet.contents().size());
        int playerStart = packet.contents().size() - 36;
        for (int slot = 0; slot < packet.contents().size(); slot++) {
            contents.add(
                restockSlotState(
                    requestedItem,
                    packet.contents().get(slot),
                    slot < playerStart
                        ? -1
                        : handlerPlayerSlot(
                            slot,
                            playerStart
                        )
                )
            );
        }
        if (restockConfirmationPhase
                == RestockConfirmationPhase
                    .AWAITING_SOURCE_REFILL_SNAPSHOT) {
            debugRestock(
                "refill probe snapshot acknowledged sync="
                    + packet.syncId()
            );
            if (restockRefillWaitState != null
                && restockRefillWaitState.isPresent()) {
                restockRefillWaitState = Optional.of(
                    RestockRefillWaitPolicy.rescheduleProbeAfterSnapshot(
                        restockRefillWaitState.orElseThrow(),
                        clientActionTick,
                        restockRefillProbeIntervalTicks()
                    )
                );
            }
            restockConfirmationPhase =
                RestockConfirmationPhase.AWAITING_SOURCE_REFILL;
        } else if (restockConfirmationPhase
                == RestockConfirmationPhase
                    .AWAITING_HANDLER_REOPEN_SNAPSHOT) {
            debugRestock(
                "lost-handler reopen snapshot acknowledged sync="
                    + packet.syncId()
            );
            restockConfirmationPhase =
                RestockConfirmationPhase.NONE;
        } else if (restockConfirmationPhase
                == RestockConfirmationPhase.AWAITING_REOPEN_SNAPSHOT
            && pendingRestockTransfer != null) {
            PendingRestockTransfer pending = pendingRestockTransfer;
            debugRestock(
                "confirmation reopen rebound pending transfer from sync="
                    + pending.syncId() + " to sync=" + packet.syncId()
            );
            pendingRestockTransfer = new PendingRestockTransfer(
                pending.item(),
                packet.syncId(),
                pending.sourceSlot(),
                pending.beforeSourceCount(),
                pending.beforePlayerCount(),
                pending.inventoryRevision(),
                pending.submittedAtTick(),
                pending.consecutiveNoProgressAttempts()
            );
            restockConfirmationPhase =
                RestockConfirmationPhase.NONE;
        }
        restockInventorySnapshot.replace(packet.syncId(), contents);
        restockSnapshotUpdateSequence =
            serverInventoryUpdateSequence;
        debugRestockSnapshot("stored full snapshot");
    }

    private void recordRestockSlotObservation(
        ScreenHandlerSlotUpdateS2CPacket packet
    ) {
        if (state != State.AwaitRestockResponse) return;
        if (restockList.isEmpty()
            || !restockInventorySnapshot.initialized()
            || !isCurrentRestockHandler(packet.getSyncId())) {
            debugRestock(
                "ignored slot update sync=" + packet.getSyncId()
                    + " slot=" + packet.getSlot()
                    + " snapshotInitialized="
                        + restockInventorySnapshot.initialized()
                    + " planEmpty=" + restockList.isEmpty()
            );
            return;
        }
        Item requestedItem = restockList.getFirst().item();
        int playerInventorySlot =
            packet.getSlot()
                    < restockInventorySnapshot.containerSlotCount()
                || packet.getSlot()
                    >= restockInventorySnapshot.containerSlotCount() + 36
                ? -1
                : handlerPlayerSlot(
                    packet.getSlot(),
                    restockInventorySnapshot.containerSlotCount()
                );
        ServerInventoryTransferSnapshot.SlotState observation =
            restockSlotState(
                requestedItem,
                packet.getStack(),
                playerInventorySlot
            );
        if (restockInventorySnapshot.updateSlot(
            packet.getSyncId(),
            packet.getSlot(),
            observation
        )) {
            restockSnapshotUpdateSequence =
                serverInventoryUpdateSequence;
            String stackDescription = packet.getStack().isEmpty()
                ? "empty"
                : Registries.ITEM.getId(packet.getStack().getItem())
                    + "x" + packet.getStack().getCount();
            debugRestock(
                "stored slot update sync=" + packet.getSyncId()
                    + " handlerSlot=" + packet.getSlot()
                    + " playerSlot=" + playerInventorySlot
                    + " stack=" + stackDescription
                    + " compatible=" + observation.compatibleCount()
                    + " canReceive=" + observation.canReceive()
                    + " sequence=" + restockSnapshotUpdateSequence
            );
        }
    }

    private ServerInventoryTransferSnapshot.SlotState restockSlotState(
        Item requestedItem,
        ItemStack stack,
        int playerInventorySlot
    ) {
        boolean managedPlayerSlot =
            playerInventorySlot >= 0
                && availableSlots.contains(playerInventorySlot);
        boolean compatible =
            !stack.isEmpty()
                && (playerInventorySlot < 0
                    || managedPlayerSlot)
                && stack.getItem() == requestedItem
                && isCompatibleRestockStack(
                    requestedItem,
                    stack,
                    playerInventorySlot >= 0
                );
        int compatibleCount = compatible ? stack.getCount() : 0;
        int maximumStackSize = stack.isEmpty()
            ? Utils.maximumStackSize(requestedItem)
            : stack.getMaxCount();
        boolean canReceive = managedPlayerSlot
            && (stack.isEmpty()
                || (compatible
                    && stack.getCount() < maximumStackSize));
        return new ServerInventoryTransferSnapshot.SlotState(
            compatibleCount,
            canReceive
        );
    }

    private int handlerPlayerSlot(
        int handlerSlot,
        int playerStart
    ) {
        int offset = handlerSlot - playerStart;
        if (offset < 0 || offset >= 36) {
            throw new IllegalArgumentException(
                "Handler slot is outside the player inventory."
            );
        }
        return offset < 27 ? offset + 9 : offset - 27;
    }

    private boolean isCompatibleRestockStack(
        Item requestedItem,
        ItemStack stack,
        boolean playerSlot
    ) {
        if (plannedRepairToolDemand.containsKey(requestedItem)) {
            return isCompatiblePlannedRepairTool(stack)
                && hasMinimumToolDurability(stack);
        }
        if (strictMiningRestockActive
            && requestedItem == Items.COBBLESTONE) {
            return true;
        }
        if (strictMiningRestockActive
            && strictMiningInventoryPlan != null) {
            MiningToolInventoryPlan.Tool<Item, ItemStack> tool =
                miningInventoryTool(stack, 0);
            return playerSlot
                ? strictMiningInventoryPlan
                    .isUsableCompatiblePlayerTool(tool)
                : strictMiningInventoryPlan
                    .isUsableCompatibleChestCandidate(tool);
        }
        return true;
    }

    private void failRestockTransfer(String reason) {
        debugRestockSnapshot("failure snapshot");
        debugRestock("failing restock transaction reason=" + reason);
        abandonRestockSession(true);
        closeNextInvPacket = true;
        error(reason);
        stopMovement();
        toggle();
    }

    private void abandonRestockSession(boolean discardLogicalPlan) {
        debugRestock(
            "abandoning session discardLogicalPlan=" + discardLogicalPlan
                + " demands="
                    + (restockList == null ? -1 : restockList.size())
                + " backlog="
                    + (restockBacklogSlots == null
                        ? -1 : restockBacklogSlots.size())
                + " pending=" + (pendingRestockTransfer != null)
                + " deferredPacket=" + (toBeHandledInvPacket != null)
        );
        pendingRestockTransfer = null;
        if (restockInventorySnapshot != null) {
            restockInventorySnapshot.clear();
        }
        restockSnapshotUpdateSequence = -1L;
        restockConfirmationPhase = RestockConfirmationPhase.NONE;
        resetRestockRefillWait();
        resetRestockTransferTracking();
        restockHandlerLeaseRecoveryAttempts = 0;
        if (restockBacklogSlots != null) restockBacklogSlots.clear();
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        interactTimeout = 0;
        lastInteractedChest = null;
        if (!discardLogicalPlan) return;
        if (restockList != null) restockList.clear();
        if (restockMandatoryTargets != null) {
            restockMandatoryTargets.clear();
        }
        if (checkedChests != null) checkedChests.clear();
        resumeAfterRestockState = null;
        strictMiningRestockActive = false;
        strictMiningInventoryPlan = null;
    }

    private int packetPlayerItemCount(
        InventoryS2CPacket packet,
        Item item
    ) {
        int count = 0;
        for (int slot = packet.contents().size() - 36;
             slot < packet.contents().size();
             slot++) {
            ItemStack stack = packet.contents().get(slot);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private int packetPlayerMapSlot(
        InventoryS2CPacket packet,
        Integer expectedMapId
    ) {
        for (int slot = packet.contents().size() - 36;
             slot < packet.contents().size();
             slot++) {
            ItemStack stack = packet.contents().get(slot);
            if (mapIdEquals(stack, expectedMapId)) {
                return slot;
            }
        }
        return -1;
    }

    private int packetPlayerFilledMapCount(
        InventoryS2CPacket packet
    ) {
        int count = 0;
        for (int slot = packet.contents().size() - 36;
             slot < packet.contents().size();
             slot++) {
            ItemStack stack = packet.contents().get(slot);
            if (stack.getItem() == Items.FILLED_MAP) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean packetContainsChestMap(
        InventoryS2CPacket packet,
        Integer expectedMapId
    ) {
        for (int slot = 0; slot < packet.contents().size() - 36; slot++) {
            ItemStack stack = packet.contents().get(slot);
            if (mapIdEquals(stack, expectedMapId)) return true;
        }
        return false;
    }

    private boolean isExpectedFinishedMapHandler(
        InventoryS2CPacket packet
    ) {
        int totalSlots = packet.contents().size();
        return finishedMapChest != null
            && lastInteractedChest != null
            && lastInteractedChest.equals(finishedMapChest.getLeft())
            && mc.player != null
            && mc.player.currentScreenHandler != null
            && packet.syncId() != 0
            && packet.syncId()
                == mc.player.currentScreenHandler.syncId
            && (totalSlots == 63 || totalSlots == 90);
    }

    private boolean isExpectedUsedToolHandler(
        InventoryS2CPacket packet
    ) {
        if (mc.player == null
            || mc.player.currentScreenHandler == null) {
            return false;
        }
        return UsedToolDepositRecoveryPolicy
            .acceptsHandlerSnapshot(
                activeUsedToolDepositChest,
                lastInteractedChest,
                usedToolDepositPlan.keySet(),
                currentUsedToolDepositItems,
                packet.syncId(),
                mc.player.currentScreenHandler.syncId,
                packet.contents().size()
            );
    }

    private void completeMapDeposit() {
        mapHandoffStage = MapHandoffStage.DEPOSITED;
        mapCyclePhase = MapCyclePhase.MAP_DEPOSITED;
        if (!persistFileCoordinationCheckpoint("map-deposited-confirmed")) {
            return;
        }
        startMining();
    }

    private void failMapHandoff(String reason) {
        error("Map handoff stopped safely: " + reason);
        SlaveSystem.setFileMetadata(
            FILE_META_STATUS,
            "MAP_HANDOFF_REQUIRES_ATTENTION"
        );
        stopMovement();
        if (isActive()) toggle();
    }

    private static Integer stackMapId(ItemStack stack) {
        if (stack == null || stack.getItem() != Items.FILLED_MAP) {
            return null;
        }
        MapIdComponent component = stack.get(DataComponentTypes.MAP_ID);
        return component == null ? null : component.id();
    }

    private static boolean mapIdEquals(
        ItemStack stack,
        Integer expectedMapId
    ) {
        return expectedMapId != null
            && expectedMapId.equals(stackMapId(stack));
    }

    private boolean isLockedMap(ItemStack stack) {
        return Boolean.TRUE.equals(mapLockedState(stack));
    }

    private Boolean mapLockedState(ItemStack stack) {
        if (stack == null
            || stack.getItem() != Items.FILLED_MAP
            || mc.world == null) {
            return null;
        }
        MapState mapState = FilledMapItem.getMapState(stack, mc.world);
        return mapState == null ? null : mapState.locked;
    }

    private int playerItemCount(Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private ArrayList<ItemStack> playerFilledMaps() {
        ArrayList<ItemStack> maps = new ArrayList<>();
        if (mc.player == null) return maps;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.FILLED_MAP) {
                for (int count = 0; count < stack.getCount(); count++) {
                    maps.add(stack);
                }
            }
        }
        return maps;
    }

    private boolean playerHasAnyHandoffItem() {
        return playerItemCount(Items.MAP) > 0
            || playerItemCount(Items.GLASS_PANE) > 0
            || !playerFilledMaps().isEmpty();
    }

    private void scheduleMapSupplyChest() {
        Pair<BlockPos, Vec3d> bestChest =
            getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(
            new Pair<>(
                bestChest.getRight(),
                new Pair<>("mapMaterialChest", bestChest.getLeft())
            )
        );
        state = State.Walking;
    }

    private void scheduleMapFill() {
        BlockPos centerBlockPos = mapCorner.add(
            map.length / 2 - 1,
            map[map.length / 2 - 1][map[0].length / 2 - 1]
                .getRight(),
            map[0].length / 2 - 1
        );
        Vec3d center = centerBlockPos.toCenterPos().add(0, 0.5, 0);
        Vec3d centerEdge = walkingPosition(
            northWalkwaySupport(map.length / 2 - 1)
        );
        checkpoints.add(
            new Pair<>(centerEdge, new Pair<>("walkRestock", null))
        );
        checkpoints.add(
            new Pair<>(center, new Pair<>("fillMap", null))
        );
        checkpoints.add(
            new Pair<>(centerEdge, new Pair<>("walkRestock", null))
        );
        state = State.Walking;
    }

    private int singlePlayerItemSlot(Item item) {
        if (mc.player == null) return -1;
        int foundSlot = -1;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (stack.getItem() != item) continue;
            count += stack.getCount();
            foundSlot = slot;
        }
        return count == 1 ? foundSlot : -1;
    }

    private int chooseHandoffHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }
        for (int slot : availableHotBarSlots) {
            if (slot >= 0 && slot < 9) return slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            Item item =
                mc.player.getInventory().getStack(slot).getItem();
            if (item != Items.GLASS_PANE
                && item != Items.FILLED_MAP
                && item != Items.MAP) {
                return slot;
            }
        }
        return 0;
    }

    /**
     * Uses the printer's existing screen-handler swap path to put the one
     * empty map in the hotbar. A process restart can invalidate the old
     * available-slot list, so the exact live inventory is inspected here.
     */
    private boolean prepareEmptyMapForFill() {
        int mapSlot = singlePlayerItemSlot(Items.MAP);
        if (mapSlot < 0) {
            failMapHandoff(
                "Exactly one empty map is required before filling."
            );
            return false;
        }
        if (mapSlot < 9) {
            handoffMapHotbarSlot = mapSlot;
            mc.player.getInventory().setSelectedSlot(mapSlot);
            return true;
        }

        handoffMapHotbarSlot = chooseHandoffHotbarSlot();
        awaitServerInventoryUpdate();
        Utils.performAuthoritativeSwap(
            mapSlot,
            handoffMapHotbarSlot
        );
        state = State.AwaitMapHotbarSwapConfirmation;
        timeoutTicks = Math.max(1, postSwapDelay.get());
        return false;
    }

    private void scheduleCartographyTable() {
        checkpoints.add(
            new Pair<>(
                cartographyTable.getRight(),
                new Pair<>("cartographyTable", null)
            )
        );
        state = State.Walking;
    }

    private void scheduleFinishedMapChest() {
        checkpoints.add(
            new Pair<>(
                finishedMapChest.getRight(),
                new Pair<>("finishedMapChest", null)
            )
        );
        state = State.Walking;
    }

    private void scheduleMapHandoffRecoveryProbe() {
        if (finishedMapChest == null) {
            failMapHandoff(
                "The registered finished-map chest is unavailable for "
                    + "authoritative recovery."
            );
            return;
        }
        checkpoints.clear();
        checkpoints.add(
            new Pair<>(
                finishedMapChest.getRight(),
                new Pair<>("mapHandoffRecoveryProbe", null)
            )
        );
        state = State.Walking;
        stopMovement();
    }

    private boolean persistRecoveredHandoffAdvance(
        MapHandoffStage stage,
        Integer sourceMapId,
        Integer lockedMapId,
        String checkpoint
    ) {
        mapHandoffStage = stage;
        handoffSourceMapId = sourceMapId;
        handoffLockedMapId = lockedMapId;
        return persistFileCoordinationCheckpoint(checkpoint);
    }

    private void resumeMapHandoffFromCheckpoint() {
        releaseTransientBuildOwners();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        mapCyclePhase = MapCyclePhase.MAP_HANDOFF;
        buildingActive = false;
        checkpoints.clear();

        if (mapHandoffStage == MapHandoffStage.SKIPPED) {
            beginMapMining(true);
            return;
        }
        if (mapHandoffStage == MapHandoffStage.PREPARE_INVENTORY) {
            Pair<BlockPos, Vec3d> bestChest =
                getBestChest(Items.CARTOGRAPHY_TABLE);
            checkpoints.add(
                new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null))
            );
            checkpoints.add(
                new Pair<>(
                    bestChest.getRight(),
                    new Pair<>("mapMaterialChest", bestChest.getLeft())
                )
            );
            state = State.Walking;
            return;
        }

        int emptyMaps = playerItemCount(Items.MAP);
        int panes = playerItemCount(Items.GLASS_PANE);
        ArrayList<ItemStack> filledMaps = playerFilledMaps();
        if (emptyMaps > 1 || panes > 1 || filledMaps.size() > 1) {
            failMapHandoff(
                "Restart recovery found multiple map handoff items."
            );
            return;
        }
        Boolean recoveredMapLocked = filledMaps.size() == 1
            ? mapLockedState(filledMaps.getFirst())
            : null;
        boolean durableLockedMapProof =
            mapHandoffStage
                == MapHandoffStage.LOCKED_MAP_CONFIRMED
                || mapHandoffStage
                    == MapHandoffStage.DEPOSIT_REQUESTED;
        if (filledMaps.size() == 1
            && recoveredMapLocked == null
            && !durableLockedMapProof) {
            state = State.AwaitHandoffMapState;
            timeoutTicks = 20;
            return;
        }

        if (mapHandoffStage == MapHandoffStage.NEED_SUPPLIES) {
            if (emptyMaps == 0 && panes == 0 && filledMaps.isEmpty()) {
                scheduleMapSupplyChest();
                return;
            }
            if (filledMaps.isEmpty()
                && ((emptyMaps == 1 && panes == 0)
                    || (emptyMaps == 0 && panes == 1))) {
                // The process may have stopped between the two one-item
                // transfers. The chest handler takes only what is missing.
                scheduleMapSupplyChest();
                return;
            }
            if (emptyMaps == 1 && panes == 1 && filledMaps.isEmpty()) {
                if (persistRecoveredHandoffAdvance(
                    MapHandoffStage.SUPPLIES_CONFIRMED,
                    null,
                    null,
                    "recovered-map-supplies"
                )) {
                    scheduleMapFill();
                }
                return;
            }
            if (emptyMaps == 0
                && panes == 1
                && filledMaps.size() == 1
                && Boolean.FALSE.equals(recoveredMapLocked)) {
                Integer sourceId = stackMapId(filledMaps.getFirst());
                if (sourceId != null
                    && persistRecoveredHandoffAdvance(
                        MapHandoffStage.SOURCE_MAP_CONFIRMED,
                        sourceId,
                        null,
                        "recovered-source-map"
                    )) {
                    scheduleCartographyTable();
                    return;
                }
            }
            failMapHandoff(
                "Restart recovery could not prove the NEED_SUPPLIES "
                    + "inventory state."
            );
            return;
        }

        if (mapHandoffStage
            == MapHandoffStage.SUPPLIES_CONFIRMED) {
            if (emptyMaps == 1 && panes == 1 && filledMaps.isEmpty()) {
                scheduleMapFill();
                return;
            }
            if (emptyMaps == 0
                && panes == 1
                && filledMaps.size() == 1
                && Boolean.FALSE.equals(recoveredMapLocked)) {
                Integer sourceId = stackMapId(filledMaps.getFirst());
                if (sourceId != null
                    && persistRecoveredHandoffAdvance(
                        MapHandoffStage.SOURCE_MAP_CONFIRMED,
                        sourceId,
                        null,
                        "recovered-source-map"
                    )) {
                    scheduleCartographyTable();
                    return;
                }
            }
            failMapHandoff(
                "Restart recovery could not prove the acquired map supplies."
            );
            return;
        }

        if (mapHandoffStage
            == MapHandoffStage.SOURCE_MAP_CONFIRMED) {
            if (filledMaps.isEmpty()) {
                failMapHandoff(
                    "The persisted source map is absent after restart."
                );
                return;
            }
            ItemStack candidate = filledMaps.getFirst();
            Integer candidateId = stackMapId(candidate);
            if (mapIdEquals(candidate, handoffSourceMapId)
                && Boolean.FALSE.equals(recoveredMapLocked)
                && emptyMaps == 0
                && panes == 1) {
                scheduleCartographyTable();
                return;
            }
            if (candidateId != null
                && !candidateId.equals(handoffSourceMapId)
                && Boolean.TRUE.equals(recoveredMapLocked)
                && emptyMaps == 0
                && panes == 0) {
                if (persistRecoveredHandoffAdvance(
                    MapHandoffStage.LOCKED_MAP_CONFIRMED,
                    handoffSourceMapId,
                    candidateId,
                    "recovered-locked-map"
                )) {
                    scheduleFinishedMapChest();
                }
                return;
            }
            failMapHandoff(
                "Restart recovery found the wrong source/locked map ID."
            );
            return;
        }

        if (mapHandoffStage
                == MapHandoffStage.LOCKED_MAP_CONFIRMED
            || mapHandoffStage
                == MapHandoffStage.DEPOSIT_REQUESTED) {
            boolean expectedMapInPlayer =
                filledMaps.size() == 1
                    && mapIdEquals(
                        filledMaps.getFirst(),
                        handoffLockedMapId
                    )
                    && !Boolean.FALSE.equals(recoveredMapLocked);
            FinishedMapDepositRecoveryPolicy.Decision decision =
                FinishedMapDepositRecoveryPolicy.decide(
                    mapHandoffStage,
                    expectedMapInPlayer,
                    false,
                    !filledMaps.isEmpty() && !expectedMapInPlayer,
                    emptyMaps != 0 || panes != 0
                );
            if (decision
                == FinishedMapDepositRecoveryPolicy.Decision.COMPLETE) {
                // The exact locked map left the player after the durable
                // request and may be beyond the input chest in a sorter.
                completeMapDeposit();
                return;
            }
            if (decision
                == FinishedMapDepositRecoveryPolicy.Decision.RETRY_DEPOSIT) {
                // The disconnect happened before the server accepted the
                // quick-move. Return to the destination and retry.
                scheduleFinishedMapChest();
                return;
            }
            failMapHandoff(
                "Restart recovery cannot safely reconcile the exact "
                    + "finished map at stage " + mapHandoffStage + "."
            );
            return;
        }

        failMapHandoff(
            "Restart recovery encountered unsupported handoff stage "
                + mapHandoffStage + "."
        );
    }

    private boolean sendFillMapInteraction() {
        int mapSlot = singlePlayerItemSlot(Items.MAP);
        if (mapSlot < 0 || mapSlot >= 9) {
            failMapHandoff(
                "The exact empty map is not ready in the hotbar."
            );
            return false;
        }
        handoffMapHotbarSlot = mapSlot;
        mc.player.getInventory().setSelectedSlot(mapSlot);
        mc.getNetworkHandler().sendPacket(
            new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND,
                Utils.getNextInteractID(),
                mc.player.getYaw(),
                mc.player.getPitch()
            )
        );
        awaitServerInventoryUpdate();
        return true;
    }

    private void awaitServerInventoryUpdate() {
        handoffConfirmationAfterSequence =
            serverInventoryUpdateSequence;
        handoffConfirmationAttempts = 0;
    }

    private void waitForAnotherServerInventoryUpdate() {
        handoffConfirmationAfterSequence =
            serverInventoryUpdateSequence;
        handoffConfirmationAttempts = 0;
        timeoutTicks = 10;
    }

    private boolean tickMapHandoffConfirmation() {
        if (state == State.AwaitMapHotbarSwapConfirmation) {
            if (serverInventoryUpdateSequence
                <= handoffConfirmationAfterSequence) {
                handoffConfirmationAttempts++;
                if (handoffConfirmationAttempts >= 5) {
                    handoffConfirmationAttempts = 0;
                    if (!prepareEmptyMapForFill()) return true;
                }
                timeoutTicks = 10;
                return true;
            }
            if (handoffMapHotbarSlot >= 0
                && handoffMapHotbarSlot < 9
                && mc.player.getInventory()
                    .getStack(handoffMapHotbarSlot)
                    .getItem() == Items.MAP) {
                mc.player.getInventory().setSelectedSlot(
                    handoffMapHotbarSlot
                );
                if (!sendFillMapInteraction()) return true;
                state = State.AwaitFilledMapConfirmation;
                timeoutTicks = 10;
                return true;
            }
            handoffConfirmationAttempts++;
            if (handoffConfirmationAttempts >= 5) {
                handoffConfirmationAttempts = 0;
                prepareEmptyMapForFill();
            } else {
                handoffConfirmationAfterSequence =
                    serverInventoryUpdateSequence;
                timeoutTicks = 10;
            }
            return true;
        }
        if (state == State.AwaitHandoffMapState) {
            for (ItemStack map : playerFilledMaps()) {
                if (mapLockedState(map) == null) {
                    timeoutTicks = 20;
                    return true;
                }
            }
            resumeMapHandoffFromCheckpoint();
            return true;
        }
        boolean awaitingConfirmation =
            state == State.AwaitMapSuppliesConfirmation
                || state == State.AwaitFilledMapConfirmation
                || state
                    == State.AwaitCartographyOutputConfirmation;
        if (awaitingConfirmation
            && serverInventoryUpdateSequence
                <= handoffConfirmationAfterSequence) {
            handoffConfirmationAttempts++;
            if (handoffConfirmationAttempts >= 5) {
                handoffConfirmationAttempts = 0;
                if (state
                    == State.AwaitMapSuppliesConfirmation) {
                    scheduleMapSupplyChest();
                } else if (state
                    == State.AwaitFilledMapConfirmation) {
                    if (!sendFillMapInteraction()) return true;
                    timeoutTicks = 10;
                } else {
                    scheduleCartographyTable();
                }
                return true;
            }
            timeoutTicks = 10;
            return true;
        }
        handoffConfirmationAttempts = 0;
        if (state == State.AwaitMapSuppliesConfirmation) {
            int emptyMaps = playerItemCount(Items.MAP);
            int panes = playerItemCount(Items.GLASS_PANE);
            ArrayList<ItemStack> filledMaps = playerFilledMaps();
            if (emptyMaps == 1 && panes == 1 && filledMaps.isEmpty()) {
                mapHandoffStage = MapHandoffStage.SUPPLIES_CONFIRMED;
                if (!persistFileCoordinationCheckpoint(
                    "map-supplies-confirmed"
                )) {
                    return true;
                }
                scheduleMapFill();
                return true;
            }
            if (emptyMaps <= 1 && panes <= 1 && filledMaps.isEmpty()) {
                waitForAnotherServerInventoryUpdate();
                return true;
            }
            failMapHandoff(
                "The player inventory contains ambiguous map supplies."
            );
            return true;
        }

        if (state == State.AwaitFilledMapConfirmation) {
            ArrayList<ItemStack> filledMaps = playerFilledMaps();
            if (filledMaps.size() == 1
                && playerItemCount(Items.MAP) == 0
                && playerItemCount(Items.GLASS_PANE) == 1) {
                ItemStack sourceMap = filledMaps.getFirst();
                Integer sourceMapId = stackMapId(sourceMap);
                Boolean sourceLocked = mapLockedState(sourceMap);
                if (sourceLocked == null) {
                    timeoutTicks = 20;
                    return true;
                }
                if (sourceMapId == null || sourceLocked) {
                    failMapHandoff(
                        "The filled source map has no usable unlocked map ID."
                    );
                    return true;
                }
                handoffSourceMapId = sourceMapId;
                mapHandoffStage =
                    MapHandoffStage.SOURCE_MAP_CONFIRMED;
                if (!persistFileCoordinationCheckpoint(
                    "source-map-confirmed"
                )) {
                    return true;
                }
                scheduleCartographyTable();
                return true;
            }
            if (filledMaps.isEmpty()
                && playerItemCount(Items.MAP) == 1
                && playerItemCount(Items.GLASS_PANE) == 1) {
                if (!prepareEmptyMapForFill()) return true;
                if (!sendFillMapInteraction()) return true;
                timeoutTicks = 20;
                return true;
            }
            failMapHandoff(
                "The source-map fill produced an ambiguous inventory state."
            );
            return true;
        }

        if (state == State.AwaitCartographyOutputConfirmation) {
            ArrayList<ItemStack> filledMaps = playerFilledMaps();
            if (filledMaps.size() == 1) {
                ItemStack candidate = filledMaps.getFirst();
                Integer candidateId = stackMapId(candidate);
                Boolean candidateLocked = mapLockedState(candidate);
                if (candidateLocked == null) {
                    timeoutTicks = 20;
                    return true;
                }
                if (candidateId != null
                    && !candidateId.equals(handoffSourceMapId)
                    && candidateLocked) {
                    handoffLockedMapId = candidateId;
                    mapHandoffStage =
                        MapHandoffStage.LOCKED_MAP_CONFIRMED;
                    if (!persistFileCoordinationCheckpoint(
                        "locked-map-confirmed"
                    )) {
                        return true;
                    }
                    scheduleFinishedMapChest();
                    return true;
                }
                if (candidateId != null
                    && !candidateId.equals(handoffSourceMapId)) {
                    // The output ID can arrive before its MapState. Wait for
                    // the client map-state packet instead of feeding it back
                    // into the table as though it were the source map.
                    timeoutTicks = 20;
                    return true;
                }
            }
            if (filledMaps.isEmpty()
                || (filledMaps.size() == 1
                    && mapIdEquals(
                        filledMaps.getFirst(),
                        handoffSourceMapId
                    ))) {
                waitForAnotherServerInventoryUpdate();
                return true;
            }
            failMapHandoff(
                "The cartography output contains an ambiguous map ID."
            );
            return true;
        }
        return false;
    }

    private void recordServerBlockObservation(
        BlockPos position,
        BlockState state
    ) {
        serverBlockUpdateSequence++;
        serverBlockObservations.put(
            new BlockPos(position),
            new ServerBlockObservation(
                serverBlockUpdateSequence,
                state.getBlock()
            )
        );
        if (mapCorner != null
            && buildTargets != null
            && confirmedBuildTargetsThisRun != null) {
            BlockPos relative = position.subtract(mapCorner);
            Block expected = buildTargets.get(relative);
            if (expected != null && state.getBlock() != expected) {
                confirmedBuildTargetsThisRun.remove(relative);
            }
        }
        if (debugPrints.get()) {
            boolean placementTracked =
                pendingPlacementLedger != null
                    && pendingPlacementLedger.isPending(position);
            boolean repairTracked =
                buildRepairController != null
                    && buildRepairController.phaseOf(position).isPresent();
            boolean teardownTracked =
                teardownMineController != null
                    && teardownMineController.target()
                        .map(target -> target.key().equals(position))
                        .orElse(false);
            if (placementTracked
                || repairTracked
                || teardownTracked) {
                debugLog(
                    "BlockAck",
                    "position=" + position.toShortString()
                        + " block="
                        + Registries.BLOCK.getId(state.getBlock())
                        + " sequence=" + serverBlockUpdateSequence
                        + " owners={placement=" + placementTracked
                        + ",repair=" + repairTracked
                        + ",teardown=" + teardownTracked + "}"
                );
            }
        }
    }

    private void recordFullInventoryHotbarObservations(
        InventoryS2CPacket packet
    ) {
        if (packet.syncId() != 0 || packet.contents().size() <= 44) {
            return;
        }
        for (int playerSlot = 9; playerSlot < 36; playerSlot++) {
            recordServerPlayerInventoryObservation(
                playerSlot,
                packet.contents().get(playerSlot)
            );
        }
        for (int playerSlot = 0; playerSlot < 9; playerSlot++) {
            recordServerPlayerInventoryObservation(
                playerSlot,
                packet.contents().get(36 + playerSlot)
            );
        }
    }

    private void recordSlotHotbarObservation(
        ScreenHandlerSlotUpdateS2CPacket packet
    ) {
        int playerSlot = -1;
        if (packet.getSyncId() == -2
            && packet.getSlot() >= 0
            && packet.getSlot() < 36) {
            playerSlot = packet.getSlot();
        } else if (packet.getSyncId() == 0
            && packet.getSlot() >= 9
            && packet.getSlot() < 36) {
            playerSlot = packet.getSlot();
        } else if (packet.getSyncId() == 0
            && packet.getSlot() >= 36
            && packet.getSlot() < 45) {
            playerSlot = packet.getSlot() - 36;
        }
        if (playerSlot >= 0) {
            recordServerPlayerInventoryObservation(
                playerSlot,
                packet.getStack()
            );
        }
    }

    private void recordPlayerInventoryHotbarObservation(
        SetPlayerInventoryS2CPacket packet
    ) {
        if (packet.slot() >= 0 && packet.slot() < 36) {
            recordServerPlayerInventoryObservation(
                packet.slot(),
                packet.contents()
            );
        }
    }

    private void recordServerPlayerInventoryObservation(
        int playerSlot,
        ItemStack stack
    ) {
        if (playerSlot < 0 || playerSlot >= 36) return;
        PendingInventoryMetadataSwap pendingSwap =
            pendingInventoryMetadataSwap;
        boolean pendingSwapSlot = pendingSwap != null
            && (pendingSwap.sourceSlot() == playerSlot
                || pendingSwap.targetHotbarSlot() == playerSlot);
        if (pendingSwapSlot || repairToolShadows.containsKey(playerSlot)) {
            debugLog(
                "InventoryAck",
                "playerSlot=" + playerSlot
                    + " item="
                    + (stack.isEmpty()
                        ? "minecraft:air"
                        : Registries.ITEM.getId(stack.getItem()))
                    + " count=" + stack.getCount()
                    + " damage=" + stack.getDamage()
                    + " sequence=" + serverInventoryUpdateSequence
                    + " pendingSwap=" + pendingSwapSlot
                    + " durabilityShadow="
                        + repairToolShadows.get(playerSlot)
            );
        }
        if (playerSlot < 9) {
            serverHotbarUpdateSequences[playerSlot] =
                serverInventoryUpdateSequence;
            serverHotbarObservedItems[playerSlot] = stack.isEmpty()
                ? Items.AIR
                : stack.getItem();
            serverHotbarObservedTools[playerSlot] =
                miningToolIdentity(stack);
        }

        RepairToolShadow shadow = repairToolShadows.get(playerSlot);
        if (shadow != null
            && serverInventoryUpdateSequence
                > shadow.inventoryRevision()) {
            int observedRemaining = remainingToolDurability(stack);
            int acknowledgedDamage = Math.max(
                0,
                shadow.observedRemainingDurability()
                    - observedRemaining
            );
            int remainingDebits = Math.max(
                0,
                shadow.unacknowledgedUses() - acknowledgedDamage
            );
            debugLog(
                "ToolDurability",
                "ack playerSlot=" + playerSlot
                    + " observedRemaining=" + observedRemaining
                    + " acknowledgedDamage=" + acknowledgedDamage
                    + " previousDebits="
                        + shadow.unacknowledgedUses()
                    + " remainingDebits=" + remainingDebits
                    + " sequence=" + serverInventoryUpdateSequence
            );
            if (remainingDebits == 0) {
                repairToolShadows.remove(playerSlot);
            } else {
                repairToolShadows.put(
                    playerSlot,
                    new RepairToolShadow(
                        observedRemaining,
                        remainingDebits,
                        serverInventoryUpdateSequence
                    )
                );
            }
        }
    }

    private boolean acknowledgePendingInventoryMetadataSwap(
        InventoryS2CPacket packet
    ) {
        PendingInventoryMetadataSwap pending =
            pendingInventoryMetadataSwap;
        if (pending == null) {
            return true;
        }
        if (packet.syncId() != 0
            || packet.contents().size() <= 44) {
            debugLog(
                "HotbarSwap",
                "ignored non-player full snapshot owner="
                    + pending.owner() + " sync=" + packet.syncId()
                    + " slots=" + packet.contents().size()
            );
            return true;
        }
        if (serverInventoryUpdateSequence
            <= pending.submittedAfterSequence()) {
            debugLog(
                "HotbarSwap",
                "waiting for newer full snapshot owner="
                    + pending.owner() + " currentSequence="
                    + serverInventoryUpdateSequence
                    + " submittedAfter="
                    + pending.submittedAfterSequence()
            );
            return true;
        }

        InventoryStackIdentity source =
            inventoryStackIdentity(
                playerInventoryStack(packet, pending.sourceSlot())
            );
        InventoryStackIdentity target =
            inventoryStackIdentity(
                playerInventoryStack(packet, pending.targetHotbarSlot())
            );
        boolean exchanged =
            target.equals(pending.beforeSource())
                && source.matchesMonotonicDamage(
                    pending.beforeTarget()
                );
        debugLog(
            "HotbarSwap",
            "authoritative observation owner=" + pending.owner()
                + " sourceSlot=" + pending.sourceSlot()
                + " targetHotbarSlot="
                    + pending.targetHotbarSlot()
                + " beforeSource={" + pending.beforeSource() + "}"
                + " beforeTarget={" + pending.beforeTarget() + "}"
                + " observedSource={" + source + "}"
                + " observedTarget={" + target + "}"
                + " exchanged=" + exchanged
                + " sequence=" + serverInventoryUpdateSequence
        );
        if (exchanged) {
            pending.metadata().applyTo(
                repairToolShadows,
                plannedRepairToolKeepSlots
            );
            serverHotbarSwapAckSequences[
                pending.targetHotbarSlot()
            ] = serverInventoryUpdateSequence;
            debugLog(
                "HotbarSwap",
                "confirmed owner=" + pending.owner()
                    + " targetHotbarSlot="
                        + pending.targetHotbarSlot()
                    + " ackSequence="
                        + serverInventoryUpdateSequence
            );
            pendingInventoryMetadataSwap = null;
            return true;
        }

        boolean stillBefore =
            source.equals(pending.beforeSource())
                && target.matchesMonotonicDamage(
                    pending.beforeTarget()
                );
        if (stillBefore) {
            debugLog(
                "HotbarSwap",
                "server still reports pre-swap state owner="
                    + pending.owner() + "; keeping transaction pending"
            );
            return true;
        }

        String owner = pending.owner();
        MiningHotbarSwapContext failedContext =
            miningHotbarSwapContext;
        boolean failedDuringBuild =
            buildingActive
                && (confirmedBuildHotbarSwap.isPending()
                    || failedContext
                        == MiningHotbarSwapContext.BUILD_REPAIR);
        debugLog(
            "HotbarSwap",
            "conflicting authoritative state owner=" + owner
                + " failedContext=" + failedContext
                + " failedDuringBuild=" + failedDuringBuild
        );
        clearPendingInventorySwapState();
        confirmedBuildHotbarSwap.clear();
        confirmedMiningHotbarSwap.clear();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        error(
            "Authoritative inventory state conflicted with the pending "
                + owner + " swap."
        );
        if (failedDuringBuild) {
            stopBuildForAction();
        } else {
            stopForMiningHotbarSwap(failedContext);
        }
        toggle();
        return false;
    }

    private ItemStack playerInventoryStack(
        InventoryS2CPacket packet,
        int playerSlot
    ) {
        int packetSlot = playerSlot < 9
            ? 36 + playerSlot
            : playerSlot;
        return packet.contents().get(packetSlot);
    }

    private void refreshPendingInventoryMetadataCapture() {
        PendingInventoryMetadataSwap pending =
            pendingInventoryMetadataSwap;
        if (pending == null) return;
        pendingInventoryMetadataSwap =
            pending.withMetadata(
                InventorySlotMetadataSwap.capture(
                    pending.sourceSlot(),
                    pending.targetHotbarSlot(),
                    repairToolShadows,
                    plannedRepairToolKeepSlots
                )
            );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        drainReceivedPackets();
        if (state == null) return;
        clientActionTick++;
        buildMovementBlockedThisTick = false;
        buildMovementHoldReasonThisTick =
            CircularBuildMovementPolicy.HoldReason.NONE;
        buildMovementRequiredSupportThisTick = null;
        refreshWorkActionBudget();
        if (SlaveSystem.isFileSlave()) synchronizeFileSlaveCycle();
        publishFileCoordinationState();
        if (localCycleCheckpointStore != null
            && mapCyclePhase != null
            && mapCyclePhase.isInProgress()
            && !fileMasterRecoveryLoaded
            && !localCycleRecoveryCandidate
            && clientActionTick - lastLocalCycleCheckpointTick
                >= LOCAL_CYCLE_HEARTBEAT_TICKS
            && !persistLocalCycleCheckpoint(
                "runtime-heartbeat"
            )) {
            return;
        }
        if (SlaveSystem.isFileSlave()) {
            boolean masterAvailable = SlaveSystem.isFileMasterAvailable(
                filePeerTimeoutSeconds.get() * 1000L
            );
            if (!masterAvailable && mapCyclePhase.isInProgress()) {
                waitingForFileMasterAvailability = true;
                SlaveSystem.setFileMetadata(
                    FILE_META_STATUS,
                    "WAITING_FOR_FILE_MASTER"
                );
                if (state != State.AwaitSlaveContinue) pause();
                stopMovement();
                return;
            }
            if (masterAvailable && waitingForFileMasterAvailability) {
                waitingForFileMasterAvailability = false;
                SlaveSystem.queueMasterDM("sync");
                stopMovement();
                return;
            }
        }
        if (mc.player == null || mc.player.isDead()) {
            cancelLogisticsDetour();
            switch (activeRecoveryOwner()) {
                case BUILD -> beginBuildRecovery(true);
                case MINING -> beginMiningRecovery(true);
                case LOGISTICS ->
                    beginInventoryLogisticsRecoveryForCurrentPhase(
                        InventoryRecoveryAuthority.PLAYER_SNAPSHOT
                    );
            }
            stopMovement();
            return;
        }
        if (beginReconnectRecoveryIfPending()) return;
        if (inventoryLogisticsRecovery
            != InventoryLogisticsRecovery.NONE) {
            resumeInventoryLogisticsRecovery();
            return;
        }
        if (buildRecoveryPending) {
            if (!mc.player.isOnGround()) {
                stopMovement();
                return;
            }
            boolean inventoryWasLost = buildRecoveryNeedsInventory;
            beginBuildRecovery(inventoryWasLost);
            buildRecoveryPending = false;
            buildRecoveryNeedsInventory = false;
            if (!recoverCircularBuildTraversal(inventoryWasLost)) return;
        }
        if (miningRecoveryPending) {
            if (!hasStableGroundedMiningRecoverySnapshot(
                "runtime teardown recovery"
            )) {
                stopMovement();
                return;
            }
            boolean inventoryWasLost = miningRecoveryNeedsTools;
            beginMiningRecovery(inventoryWasLost);
            miningRecoveryPending = false;
            miningRecoveryNeedsTools = false;
            restartCurrentMiningAssignment();
            return;
        }
        if (!reconcilePendingBuildPlacements()) return;
        if (!reconcileTeardownScaffoldPlacements()) return;
        if (!SlaveSystem.isSlave()
            && mapCyclePhase == MapCyclePhase.MAP_DEPOSITED
            && !miningAssignmentsActive
            && !fileMasterRecoveryLoaded) {
            // The filled map is already safely deposited. A disconnect in the
            // tiny gap before startMining must continue with clearing instead
            // of attempting the map handoff a second time.
            startMining();
            return;
        }
        if (fileMasterRecoveryLoaded
            && state != State.AwaitFileRecovery
            && isPrinterConfigurationReady()
            && !buildingActive
            && !miningAssignmentsActive) {
            state = State.AwaitFileRecovery;
        }
        if (!state.equals(debugPreviousState)) {
            State previousState = debugPreviousState;
            debugPreviousState = state;
            debugLog(
                "State",
                "changed " + previousState + " -> " + state
            );
        }

        if (state == State.AwaitNbtArchive) {
            if (timeoutTicks > 0) {
                if (mc.player.isOnGround()) timeoutTicks--;
                stopMovement();
                return;
            }
            if (recordCurrentCycleTiming() && archiveCurrentNbtFiles()) {
                finishMiningAfterArchive();
            } else {
                timeoutTicks = 100;
            }
            return;
        }

        if (state == State.AwaitFileMaster) {
            stopMovement();
            synchronizeFileSlaveCycle();
            if (mapFile != null && isPrinterConfigurationReady()) {
                state = State.AwaitMasterNextMap;
            }
            return;
        }

        if (state == State.AwaitFileSlaves) {
            stopMovement();
            if (allFileSlavesReady()) {
                waitingForFilePeersNotice = false;
                startBuilding();
            }
            return;
        }

        if (state == State.AwaitFileRecovery) {
            stopMovement();
            resumeRecoveredFileMasterCycle();
            return;
        }

        if (state.equals(State.AwaitMasterAllBuilt)) {
            if (SlaveSystem.allSlavesFinished()) {
                if (!endBuilding()) return;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllBuiltSkip)) {
            if (SlaveSystem.allSlavesFinished()) {
                startMining();
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitManualRepair)) {
            // Refresh known errors
            ArrayList<BlockPos> previouslyReportedErrors = new ArrayList<>(knownErrors);
            knownErrors.clear();
            knownErrors.addAll(getInvalidPlacements());
            for (BlockPos errorPos : previouslyReportedErrors) {
                Block expected = buildTargets.get(errorPos.subtract(mapCorner));
                BlockState current = MapAreaCache.getCachedBlockState(errorPos);
                if (expected != null
                    && (current.isAir() || current.getBlock() != expected)
                    && !knownErrors.contains(errorPos)) {
                    knownErrors.add(errorPos);
                }
            }
            if (knownErrors.isEmpty()) {
                checkpoints.add(new Pair(mc.player.getEntityPos(), new Pair("lineEnd", null)));
                state = State.Walking;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllMined)) {
            if (SlaveSystem.allSlavesFinished()) {
                boolean masterAssigned = startNextMasterMiningAssignment();
                for (String slave : SlaveSystem.slaves) assignNextMiningTask(slave);
                if (!masterAssigned) {
                    if (SlaveSystem.allSlavesFinished()) finishMiningIfComplete();
                    return;
                }
            } else {
                return;
            }
        }

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                debugLog(
                    "Interaction",
                    "response timeout; retrying target="
                        + (state == State.AwaitCartographyResponse
                            ? cartographyTable.getLeft()
                            : lastInteractedChest)
                );
                if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getLeft());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        boolean continuingOrderedRouteJump =
            jumpTimeout > 0 && hasActiveOrderedUMovement();
        if (jumpTimeout > 0) {
            jumpTimeout--;
            if (!continuingOrderedRouteJump) {
                Utils.setJumpPressed(true);
                return;
            }
        }

        if (state == State.AwaitRestockResponse
            && pendingRestockTransfer != null
            && pendingRestockTransfer.submittedAtTick() >= 0) {
            if (toBeHandledInvPacket == null
                && restockSnapshotUpdateSequence
                    > pendingRestockTransfer.inventoryRevision()
                && processRestockInventorySnapshot(false)) {
                return;
            }
            if (toBeHandledInvPacket == null
                && restockConfirmationPhase
                    != RestockConfirmationPhase.REOPEN_PENDING
                && clientActionTick
                    - pendingRestockTransfer.submittedAtTick()
                    >= retryInteractTimer.get()) {
                failRestockTransfer(
                    "Server did not confirm the inventory transfer for "
                        + pendingRestockTransfer.item()
                            .getName()
                            .getString()
                        + " before the bounded timeout."
                );
                return;
            }
        }
        if (state == State.AwaitUsedToolChestResponse
            && pendingUsedToolDeposit != null
            && toBeHandledInvPacket == null
            && clientActionTick
                - pendingUsedToolDeposit.submittedAtTick()
                >= retryInteractTimer.get()) {
            failInventoryTransaction(
                "Server did not confirm the used-tool deposit before "
                    + "the bounded timeout."
            );
            return;
        }

        if (timeoutTicks > 0) {
            if (mc.player.isOnGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }

        if (tickRestockRefillWait()) return;

        if (state == State.AwaitRestockResponse
            && restockConfirmationPhase
                == RestockConfirmationPhase.REOPEN_PENDING
            && toBeHandledInvPacket == null
            && pendingRestockTransfer != null
            && pendingRestockTransfer.submittedAtTick() >= 0) {
            if (lastInteractedChest == null) {
                failRestockTransfer(
                    "Cannot reopen the registered chest for authoritative "
                        + "restock confirmation."
                );
                return;
            }
            closeCurrentContainerHandler();
            PendingRestockTransfer pending =
                pendingRestockTransfer;
            debugRestock(
                "forced full response not yet available after action delay;"
                    + " reopening chest=" + lastInteractedChest
                    + " sourceSlot=" + pending.sourceSlot()
                    + " attempt="
                        + pending.consecutiveNoProgressAttempts()
            );
            pendingRestockTransfer =
                new PendingRestockTransfer(
                    pending.item(),
                    pending.syncId(),
                    pending.sourceSlot(),
                    pending.beforeSourceCount(),
                    pending.beforePlayerCount(),
                    serverInventoryUpdateSequence,
                    clientActionTick,
                    pending.consecutiveNoProgressAttempts()
                );
            restockConfirmationPhase =
                RestockConfirmationPhase.AWAITING_REOPEN_SNAPSHOT;
            interactWithBlock(lastInteractedChest);
            return;
        }

        if (handleConfirmedMiningHotbarSwap()) return;
        if (handleConfirmedBuildHotbarSwap()) return;

        if (tickPendingRestockTransferDispatch()) return;

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && miningPos != null) {
            Block expected = teardownMineController.target()
                .filter(target -> target.key().equals(miningPos))
                .map(
                    OrderedTeardownMineController.Target::expectedBlock
                )
                .orElseGet(() ->
                    MapAreaCache.getCachedBlockState(miningPos)
                        .getBlock());
            TeardownBreakStatus breakStatus =
                driveOrderedTeardownBreak(miningPos, expected);
            if (breakStatus == TeardownBreakStatus.FAILED) return;
            if (breakStatus == TeardownBreakStatus.CLEARED) {
                miningPos = null;
                state = State.Mining;
            } else {
                if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }

                if (state.equals(State.AwaitBlockBreak)) {
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(false);
                    Utils.setJumpPressed(false);
                    return;
                }
            }
        }

        if ((state == State.AwaitUBlockBreak
            || (state == State.MiningUTraversal
                && miningPos != null
                && teardownMineController.hasOwnedTarget()))
            && miningPos != null) {
            Block expected = activeTeardownExpectedBlock(miningPos);
            if (expected == null) {
                failTeardownMining(
                    "U mining target has no active teardown ownership at "
                        + miningPos.toShortString() + "."
                );
                return;
            }
            TeardownBreakStatus breakStatus =
                driveOrderedTeardownBreak(miningPos, expected);
            if (breakStatus == TeardownBreakStatus.FAILED) return;
            if (breakStatus == TeardownBreakStatus.CLEARED) {
                miningPos = null;
                teardownMovementOverlapAllowed = false;
                state = State.MiningUTraversal;
            } else if (!ownedTeardownMayOverlapMovement()) {
                teardownMovementOverlapAllowed = false;
                state = State.AwaitUBlockBreak;
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
                Utils.setJumpPressed(false);
                return;
            }
        }

        if (state == State.AwaitSlaveFinalization) {
            if (pendingSlaveMiningFinalizations.isEmpty()) {
                state = State.AwaitNBTFile;
            } else {
                if (miningFinalizationRetryTicks <= 0) {
                    resendPendingMiningFinalizations();
                    miningFinalizationRetryTicks = 100;
                } else {
                    miningFinalizationRetryTicks--;
                }
            }
            return;
        }

        if (state == State.AwaitMasterNextMap) {
            if (nextMapSyncTicks <= 0) {
                SlaveSystem.queueMasterDM("sync");
                nextMapSyncTicks = 100;
            } else {
                nextMapSyncTicks--;
            }
            stopMovement();
            return;
        }

        if (state == State.AwaitCompactWorkspace) {
            if (timeoutTicks > 0) {
                timeoutTicks--;
                return;
            }
            if (validateCompactWorkspace()) {
                startBuilding();
            } else {
                timeoutTicks = 100;
            }
            return;
        }

        if (state == State.AwaitSlaveRemoval) {
            stopMovement();
            if (!SlaveSystem.hasPendingRemoval()) startBuilding();
            return;
        }

        if (state.equals(State.Mining)) {
            if (!teardownMineController.hasOwnedTarget()
                && activeMiningLine >= 0
                && isLineMined(activeMiningLine)) {
                miningPos = null;
                timeoutTicks = mineLineEndTimeout.get();
                Utils.setBackwardPressed(false);
                completeCurrentMiningAssignment();
                return;
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            if (tickPendingDumpTransfer()) return;
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                if (!SlaveSystem.isSlave()
                    && mapCyclePhase == MapCyclePhase.MAP_HANDOFF
                    && mapHandoffStage
                        == MapHandoffStage.PREPARE_INVENTORY) {
                    if (playerHasAnyHandoffItem()) {
                        failMapHandoff(
                            "Map/pane items remain after inventory "
                                + "preparation; clear reserved inventory "
                                + "slots before retrying."
                        );
                        return;
                    }
                    mapHandoffStage =
                        MapHandoffStage.NEED_SUPPLIES;
                    if (!persistFileCoordinationCheckpoint(
                        "map-inventory-prepared"
                    )) {
                        return;
                    }
                }
                state = State.Walking;
                if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                    state = State.AwaitSlaveMineLine;
                    SlaveSystem.queueMasterDM("finished");
                    return;
                } else {
                    HashMap<Item, Integer> requiredItems = getRequiredItems();
                    Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(
                        authoritativeBuildingOnHandCounts(
                            invInformation.getRight()
                        )
                    );
                }
            } else {
                debugLog(
                    "Dump",
                    "submitting "
                        + mc.player.getInventory().getStack(dumpSlot)
                            .getName().getString()
                        + " from playerSlot=" + dumpSlot
                );
                submitDumpTransfer(
                    dumpSlot,
                    mc.player.getInventory().getStack(dumpSlot),
                    1
                );
                return;
            }
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
            if (SlaveSystem.isFileSlave()) {
                state = State.AwaitMasterNextMap;
                synchronizeFileSlaveCycle();
                stopMovement();
                return;
            }
            if (!SlaveSystem.isSlave()
                && !pendingSlaveMiningFinalizations.isEmpty()) {
                state = State.AwaitSlaveFinalization;
                miningFinalizationRetryTicks = 0;
                warning(
                    "Waiting for " + pendingSlaveMiningFinalizations.size()
                        + " slave mining-finalization acknowledgement(s). "
                        + "Reconnect or remove an unavailable slave."
                );
                return;
            }
            if (mapCyclePhase == MapCyclePhase.POST_MINING) {
                mapCyclePhase = MapCyclePhase.IDLE;
                if (!clearLocalCycleCheckpoint(
                    "post-mining-complete"
                )) {
                    return;
                }
            }
            if (!prepareNextMapFile()) {
                if (!SlaveSystem.isSlave()) {
                    boolean firstCompletionNotice = !printingComplete;
                    printingComplete = true;
                    if (SlaveSystem.isFileMaster()) {
                        mapCyclePhase = MapCyclePhase.IDLE;
                        mapFile = null;
                        generatedMapFile = null;
                        activeMapName = null;
                        logicalSourceName = null;
                        logicalPrintingName = null;
                        archivedSourceName = null;
                        archivedPrintingName = null;
                        loadedFileCycleKey = null;
                        persistFileCoordinationCheckpoint(
                            "all-nbts-complete"
                        );
                    } else if (firstCompletionNotice) {
                        for (String slave : SlaveSystem.slaves) {
                            SlaveSystem.queueAssignedInterval(slave);
                            if (SlaveSystem.isIntervalAcknowledged(slave)) {
                                SlaveSystem.releaseSlave(slave);
                            }
                        }
                    }
                }
                return;
            }
            printingComplete = false;
            startBuilding();
        }

        // Handle Block Entity interaction response
        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            if (mc.currentScreen != null) {
                debugLog(
                    "Inventory",
                    "closing handled screen sync="
                        + mc.player.currentScreenHandler.syncId
                );
                mc.player.closeHandledScreen();
            }
            closeNextInvPacket = false;
        }
        if (tickMapHandoffConfirmation()) return;

        // Main Loop for Building & Mining

        if (state == State.Walking
            && circularBuildPhase == CircularBuildPhase.CONNECTOR) {
            if (!updateCircularConnectorTraversal()) return;
        }
        if (state == State.Walking
            && circularBuildPhase == CircularBuildPhase.RECOVERY) {
            tickCircularBuildRecovery();
            return;
        }
        if (state == State.Walking
            && !checkpoints.isEmpty()
            && circularBuildPhase != CircularBuildPhase.CONNECTOR
            && isCircularBuildCheckpoint(checkpoints.getFirst())) {
            String circularBuildAction =
                checkpoints.getFirst().getRight().getLeft();
            boolean alignmentCheckpoint =
                circularBuildAction.equals("preparePair");
            boolean exitAlignmentCheckpoint =
                circularBuildAction.equals("finishPair");
            boolean walkwayCheckpoint =
                circularBuildAction.equals("uBuildRecoveryExit");
            boolean continuousLegCheckpoint =
                circularBuildAction.equals("uBuildOutboundEnd");
            BlockPos requiredSupport =
                supportBelowCheckpoint(checkpoints.getFirst().getLeft());
            if (alignmentCheckpoint) {
                BlockPos pairMarker =
                    checkpoints.getFirst().getRight().getRight();
                int pairIndex =
                    pairMarker == null ? -1 : pairMarker.getX();
                if (pairIndex < 0
                    || pairIndex >= compactPlan.pairRoutes().size()) {
                    error(
                        "Circular build alignment lost its pair index."
                    );
                    toggle();
                    return;
                }
                CompactCircularNbtPlan.PairRoute route =
                    compactPlan.pairRoutes().get(pairIndex);
                BlockPos expectedAlignment =
                    circularBuildAlignmentSupport(route);
                if (!requiredSupport.equals(expectedAlignment)
                    || !isSafeCircularBuildAlignment(route)) {
                    error(
                        "Circular build alignment changed unexpectedly at "
                            + expectedAlignment.toShortString() + "."
                    );
                    toggle();
                    return;
                }
            } else if (exitAlignmentCheckpoint) {
                int pairIndex = activeCircularBuildPair;
                if (pairIndex < 0
                    || pairIndex >= compactPlan.pairRoutes().size()) {
                    error(
                        "Circular build exit lost its active pair index."
                    );
                    toggle();
                    return;
                }
                CompactCircularNbtPlan.PairRoute route =
                    compactPlan.pairRoutes().get(pairIndex);
                BlockPos expectedExit =
                    circularBuildExitAlignmentSupport(route);
                if (!requiredSupport.equals(expectedExit)
                    || !isSafeCircularBuildExitAlignment(route)) {
                    error(
                        "Circular build exit alignment changed unexpectedly at "
                            + expectedExit.toShortString() + "."
                    );
                    toggle();
                    return;
                }
            } else if (walkwayCheckpoint) {
                BlockPos relativeSupport = requiredSupport.subtract(mapCorner);
                if (relativeSupport.getZ() != -1
                    || northWalkwayRelativeY == null
                    || relativeSupport.getY() != northWalkwayRelativeY
                    || !isSafeNorthWalkway(relativeSupport.getX())) {
                    error(
                        "Circular north walkway changed unexpectedly at "
                            + requiredSupport.toShortString() + "."
                    );
                    toggle();
                    return;
                }
            } else if (!continuousLegCheckpoint) {
                BlockPos relativeSupport = requiredSupport.subtract(mapCorner);
                Block expectedSupport = buildTargets.get(relativeSupport);
                BlockState currentSupport =
                    MapAreaCache.getCachedBlockState(requiredSupport);
                if (expectedSupport == null
                    || (!currentSupport.isAir()
                        && currentSupport.getBlock() != expectedSupport)
                    || !MapAreaCache.getCachedBlockState(requiredSupport.up()).isAir()
                    || !MapAreaCache.getCachedBlockState(requiredSupport.up(2)).isAir()) {
                    error(
                        "Circular build support changed unexpectedly at "
                            + requiredSupport.toShortString() + "."
                    );
                    toggle();
                    return;
                }
                if (currentSupport.isAir()) {
                    stopMovement();
                    error(
                        "Circular structural endpoint is missing at "
                            + requiredSupport.toShortString() + "."
                    );
                    toggle();
                    return;
                }
            }
        }
        boolean persistedMiningRecoveryCheckpoint =
            state == State.Walking
                && !checkpoints.isEmpty()
                && isPersistedMiningRecoveryCheckpoint(
                    checkpoints.getFirst()
                );
        if (persistedMiningRecoveryCheckpoint) {
            Pair<Vec3d, Pair<String, BlockPos>> checkpoint =
                checkpoints.getFirst();
            BlockPos requiredSupport = supportBelowCheckpoint(
                checkpoint.getLeft()
            );
            if (checkpoint.getRight().getRight() == null
                || !requiredSupport.equals(
                    checkpoint.getRight().getRight()
                )
                || !isWalkableExteriorRecoverySupport(
                    requiredSupport
                )) {
                stopMovement();
                error(
                    "The verified exterior teardown recovery path "
                        + "changed at "
                        + requiredSupport.toShortString() + "."
                );
                toggle();
                return;
            }
        }
        ActiveOrderedUTraversal activeOrderedUTraversal =
            activeOrderedUTraversal();
        if (state == State.MiningUTraversal
            && activeContinuousTeardownArmed
            && activeOrderedUTraversal == null) {
            failTeardownMining(
                "Active teardown lost its ordered U movement plan."
            );
            return;
        }
        boolean activeOrderedUMovement =
            activeOrderedUTraversal != null;
        boolean activeCircularBuildMovement =
            activeOrderedUMovement
                && activeOrderedUTraversal.owner()
                    == OrderedUTraversalOwner.PRINTING;
        boolean activeContinuousTeardownMovement =
            activeOrderedUMovement
                && activeOrderedUTraversal.owner()
                    != OrderedUTraversalOwner.PRINTING;
        if (!activeOrderedUMovement) {
            lastActiveBuildMovementDebugState = null;
            activeCircularRouteSupportIndex = -1;
        }
        if (activeOrderedUMovement) {
            if (activeCircularBuildMovement) {
                runBuildActionScheduler();
            } else if (activeOrderedUTraversal.owner()
                == OrderedUTraversalOwner.TEARDOWN_SCAFFOLD
                && teardownScaffoldPhase
                    == TeardownScaffoldPhase.BUILDING_OUTBOUND) {
                runTeardownScaffoldPlacementScheduler();
            }
            if (!buildMovementBlockedThisTick) {
                ensureActiveOrderedUNextSupport(
                    activeOrderedUTraversal
                );
            }
            if (activeContinuousTeardownMovement
                && !buildMovementBlockedThisTick
                && !serviceContinuousTeardownWork()) {
                return;
            }
            if (buildMovementBlockedThisTick) {
                debugActiveOrderedUMovementTransition(
                    "hold",
                    "holding shared ordered U movement reason="
                        + buildMovementHoldReasonThisTick
                        + " owner="
                            + activeOrderedUTraversal.owner()
                        + (activeCircularBuildMovement
                            ? " pair=" + activeCircularBuildPair
                            : "")
                        + (activeCircularBuildMovement
                            ? " phase=" + circularBuildPhase
                            : " routeCursor="
                                + activeCircularRouteSupportIndex)
                        + (buildMovementRequiredSupportThisTick == null
                            ? ""
                            : " requiredSupport="
                                + buildMovementRequiredSupportThisTick
                                    .toShortString())
                );
                return;
            }
            if (continuingOrderedRouteJump) {
                debugActiveOrderedUMovementTransition(
                    "jump",
                    "continuing shared route jump owner="
                        + activeOrderedUTraversal.owner()
                        + " remainingJumpTicks="
                        + jumpTimeout
                );
            } else if (activeCircularRouteSupportIndex
                == activeOrderedUTraversal.supports().size() - 1) {
                debugActiveOrderedUMovementTransition(
                    "route-end",
                    "holding final shared ordered U support owner="
                        + activeOrderedUTraversal.owner()
                        + " cursor="
                        + activeCircularRouteSupportIndex
                        + " while remaining work receives server "
                        + "confirmation"
                );
            } else {
                debugActiveOrderedUMovementTransition(
                    "moving",
                    "resuming shared ordered U movement owner="
                        + activeOrderedUTraversal.owner()
                        + " supportCursor="
                            + activeCircularRouteSupportIndex
                );
            }
        }

        boolean activeOrderedRouteComplete =
            activeOrderedUMovement
                && activeCircularRouteSupportIndex
                    == activeOrderedUTraversal.supports().size() - 1;
        if ((state.equals(State.Walking)
                || state.equals(State.MiningUTraversal))
            && !activeOrderedRouteComplete) {
            Utils.setForwardPressed(true);
            Utils.setBackwardPressed(false);
        } else if (activeOrderedRouteComplete) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
        } else if (state.equals(State.Mining)) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(true);
        } else {
            return;
        }
        Utils.setJumpPressed(
            continuingOrderedRouteJump
                && !activeOrderedRouteComplete
        );
        if (checkpoints.isEmpty()) {
            if (state.equals(State.MiningUTraversal)) {
                restartCurrentMiningAssignment();
                return;
            }
            checkpoints.add(new Pair(mc.player.getEntityPos(), new Pair<>("lineEnd", null)));
        }
        boolean followingCircularConnector =
            state == State.Walking
                && circularBuildPhase == CircularBuildPhase.CONNECTOR;
        Vec3d checkpointGoal = followingCircularConnector
            ? currentCircularConnectorGoal()
            : checkpoints.get(0).getLeft();
        Vec3d movementGoal = activeOrderedUMovement
            ? currentActiveOrderedUMovementGoal(
                activeOrderedUTraversal
            )
            : checkpointGoal;
        if (activeOrderedUMovement
            || followingCircularConnector
            || state == State.MiningUTraversal
            || activeCircularBuildPair >= 0
            || persistedMiningRecoveryCheckpoint) {
            steerTowardGoal(movementGoal);
        }

        // AutoJump logic
        boolean followingLogisticsDetour = !checkpoints.isEmpty()
            && isLogisticsDetourCheckpoint(checkpoints.getFirst());
        if (!followingLogisticsDetour
            && !continuingOrderedRouteJump
            && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed())
            && jumpTimeout <= 0) {
            Direction direction =
                Direction.fromHorizontalDegrees(
                    mc.player.getYaw()
                );
            if (mc.options.backKey.isPressed()) {
                direction = direction.getOpposite();
            }
            BlockPos target =
                mc.player.getBlockPos().offset(direction);
            if (target != null
                && mc.player.isOnGround()
                && !MapAreaCache.getCachedBlockState(target).isAir()
                && MapAreaCache.getCachedBlockState(target.up(1)).isAir() && MapAreaCache.getCachedBlockState(target.up(2)).isAir()) {
                jumpTimeout = jumpCoolDown.get();
                Utils.setJumpPressed(true);
                debugLog(
                    "Movement",
                    "started auto-jump obstacle="
                        + target.toShortString()
                        + " sharedOrderedU="
                            + hasActiveOrderedUMovement()
                        + " raisedUEntry="
                            + isRaisedCircularBuildEntry(target)
                        + " yaw=" + mc.player.getYaw()
                        + " holdTicks=" + jumpTimeout
                );
            }
        }
        if (state == State.MiningUTraversal
            && isUTraversalCheckpoint(checkpoints.get(0))
            && !isSafeUCheckpoint(checkpoints.get(0))) {
            miningRecoveryPending = true;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }
        boolean preciseCircularBuildCheckpoint =
            isPreciseCircularBuildCheckpoint(checkpoints.get(0));
        boolean usesThreeDimensionalCheckpoint =
            followingCircularConnector
                || state.equals(State.MiningUTraversal)
                || preciseCircularBuildCheckpoint
                || persistedMiningRecoveryCheckpoint
                || isLogisticsDetourCheckpoint(checkpoints.get(0));
        double checkpointDistance = usesThreeDimensionalCheckpoint
            ? PlayerUtils.distanceTo(checkpointGoal)
            : PlayerUtils.distanceTo(
                checkpointGoal.add(
                    0,
                    mc.player.getY() - checkpointGoal.y,
                    0
                )
            );
        if (!followingCircularConnector
            && handleLogisticsNavigation(checkpointGoal)) return;
        boolean circularRouteCheckpoint =
            followingCircularConnector
                || (state == State.MiningUTraversal && isUTraversalCheckpoint(checkpoints.get(0)))
                || preciseCircularBuildCheckpoint
                || persistedMiningRecoveryCheckpoint
                || isLogisticsDetourCheckpoint(checkpoints.get(0));
        String currentCheckpointAction =
            checkpoints.get(0).getRight().getLeft();
        boolean connectorHandoffCheckpoint =
            activeCircularBuildPair >= 0
                && (currentCheckpointAction.equals("uBuildOutboundEnd")
                    || currentCheckpointAction.equals("uBuildConnectorEnd"));
        double requiredCheckpointBuffer = connectorHandoffCheckpoint
            ? circularBuildConnectorBuffer()
            : circularRouteCheckpoint
                ? CircularTraversalSafety.checkpointBuffer(
                    checkpointBuffer.get()
                )
                : checkpointBuffer.get();
        Pair<Vec3d, Pair<String, BlockPos>> currentCheckpoint =
            checkpoints.getFirst();
        boolean uEndpoint =
            state == State.MiningUTraversal
                && isUTraversalEndpoint(currentCheckpoint);
        OrderedUTraversalMovement.EndpointProgress
            printingEndpointProgress = activeCircularBuildMovement
                ? OrderedUTraversalMovement.endpointProgress(
                    activeOrderedRouteComplete
                )
                : OrderedUTraversalMovement.EndpointProgress.APPROACHING;
        if (uEndpoint
            && !activeContinuousTeardownMovement
            && isHorizontallyOverCheckpointSupport(checkpointGoal)
            && !isGroundedOnCheckpointSupport(checkpointGoal)) {
            stopCircularMiningMotion();
            return;
        }
        if (state == State.Walking
            && (preciseCircularBuildCheckpoint
                || persistedMiningRecoveryCheckpoint)
            && printingEndpointProgress
                != OrderedUTraversalMovement.EndpointProgress.REACHED
            && isHorizontallyOverCheckpointSupport(checkpointGoal)
            && !isGroundedOnCheckpointSupport(checkpointGoal)) {
            stopMovement();
            return;
        }
        boolean reachedCheckpoint;
        if (uEndpoint) {
            reachedCheckpoint =
                !activeContinuousTeardownMovement
                    && isGroundedOnCheckpointSupport(checkpointGoal);
        } else if (printingEndpointProgress
            == OrderedUTraversalMovement.EndpointProgress.REACHED) {
            reachedCheckpoint = true;
        } else {
            reachedCheckpoint =
                checkpointDistance < requiredCheckpointBuffer;
        }
        if (state == State.MiningUTraversal
            && reachedCheckpoint
            && miningPos != null
            && teardownMineController.hasOwnedTarget()) {
            stopCircularMiningMotion();
            return;
        }
        if (!followingCircularConnector
            && reachedCheckpoint) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            debugLog(
                "Checkpoint",
                "reached action=" + checkpointAction.getLeft()
                    + " target=" + checkpointAction.getRight()
                    + " goal=" + checkpointGoal
                    + " remainingQueue=" + checkpoints.size()
            );
            if (snapToCheckpoints.get()) {
                mc.player.setPosition(
                    checkpointGoal.x,
                    mc.player.getY(),
                    checkpointGoal.z
                );
            }
            checkpoints.remove(0);
            if (!checkpointAction.getLeft().equals("logisticsDetour")) {
                clearLogisticsTracking();
            }
            switch (checkpointAction.getLeft()) {
                case "":
                    if (state == State.MiningUTraversal) {
                        stopMovement();
                        return;
                    }
                    break;
                case "persistedMiningRecoveryStep":
                    break;
                case "resumePersistedMiningFromWalkway": {
                    BlockPos walkway = checkpointAction.getRight();
                    BlockPos relativeWalkway = walkway == null
                        ? null
                        : walkway.subtract(mapCorner);
                    if (relativeWalkway == null
                        || relativeWalkway.getZ() != -1
                        || northWalkwayRelativeY == null
                        || relativeWalkway.getY()
                            != northWalkwayRelativeY
                        || !isSafeNorthWalkway(
                            relativeWalkway.getX()
                        )
                        || !isPlayerStandingOnSupport(walkway)) {
                        stopMovement();
                        error(
                            "Persisted teardown recovery did not reach "
                                + "its verified north-walkway support."
                        );
                        toggle();
                        return;
                    }
                    debugLog(
                        "Recovery",
                        "verified exterior recovery handoff support="
                            + walkway.toShortString()
                            + " retainedPair="
                                + recoveredActiveMiningPair
                            + " retainedSupportIndex="
                                + recoveredActiveMiningTargetIndex
                    );
                    stopMovement();
                    beginMapMining(true, true);
                    return;
                }
                case "lineEnd":
                    activeCircularBuildPair = -1;
                    activeCircularConnectorIndex = -1;
                    activeCircularPlacementCursor = -1;
                    circularBuildRecoveryDirection = 0;
                    circularBuildPhase = CircularBuildPhase.NONE;
                    releaseBuildRepairSpeedMine();
                    buildRepairController.reset();
                    calculateBuildingPath(false);
                    if (circularTraversalForCurrentMap
                        && !prepareNextCircularBuildInventoryPlan()
                        && requireCompleteUInventory.get()) {
                        stopMovement();
                        toggle();
                        return;
                    }
                    ArrayList<BlockPos> newErrors = getInvalidPlacements();
                    for (BlockPos errorPos : newErrors) {
                        BlockPos relativePos = errorPos.subtract(mapCorner);
                        if (logErrors.get()) {
                            Block expectedBlock = buildTargets.get(relativePos);
                            info("Error at: " + errorPos.toShortString() + ". Is: "
                                + MapAreaCache.getCachedBlockState(errorPos).getBlock().getName().getString()
                                + ". Should be: " + (expectedBlock == null
                                ? "no compact build target"
                                : expectedBlock.getName().getString()));
                        }
                        if (SlaveSystem.isSlave()) {
                            SlaveSystem.queueMasterDM(
                                "error:" + relativePos.getX() + ":" + relativePos.getY() + ":" + relativePos.getZ()
                            );
                        }
                    }
                    knownErrors.addAll(newErrors);
                    break;
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    if (mapHandoffStage
                        != MapHandoffStage.SUPPLIES_CONFIRMED) {
                        failMapHandoff(
                            "Fill-map checkpoint reached in unexpected stage "
                                + mapHandoffStage + "."
                        );
                        return;
                    }
                    if (!prepareEmptyMapForFill()) return;
                    if (!sendFillMapInteraction()) return;
                    state = State.AwaitFilledMapConfirmation;
                    timeoutTicks = 10;
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "mapHandoffRecoveryProbe":
                    if (mapCyclePhase != MapCyclePhase.MAP_HANDOFF) {
                        failMapHandoff(
                            "The authoritative recovery probe was reached "
                                + "outside map handoff."
                        );
                        return;
                    }
                    state =
                        State.AwaitMapHandoffRecoveryProbeResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "preparePair": {
                    int pairIndex = checkpointAction.getRight().getX();
                    CompactCircularNbtPlan.PairRoute pairRoute =
                        compactPlan.pairRoutes().get(pairIndex);
                    BlockPos pairEntry =
                        northWalkwaySupport(pairRoute.outboundX());
                    BlockPos pairAlignment =
                        circularBuildAlignmentSupport(pairRoute);
                    if (!isPlayerStandingOnSupport(pairAlignment)
                        || !isSafeCircularBuildAlignment(pairRoute)
                        || !isSafeNorthWalkway(pairRoute.outboundX())) {
                        error(
                            "Circular pair " + pairIndex
                                + " was not entered from its one-block-back "
                                + "alignment checkpoint."
                        );
                        toggle();
                        return;
                    }
                    if (pendingInterval != null) {
                        stopMovement();
                        if (!replanCircularBuildFromSafeArea(false)) return;
                        return;
                    }
                    if (hasEarlierMissingBuildTarget(pairRoute)) {
                        stopMovement();
                        calculateBuildingPath(false);
                        if (!prepareNextCircularBuildInventoryPlan()
                            && requireCompleteUInventory.get()) {
                            toggle();
                        }
                        return;
                    }
                    if (!validateCircularPairWorkspace(pairRoute, true)) {
                        toggle();
                        return;
                    }
                    if (plannedCircularBuildPair != pairIndex
                        && !prepareCircularBuildInventoryPlan(pairRoute)) {
                        if (requireCompleteUInventory.get()) {
                            stopMovement();
                            toggle();
                            return;
                        }
                        if (hasPartialConnector(pairRoute)) {
                            error(
                                "Circular pair " + pairIndex
                                    + " has a partial connector but no longer fits the usable inventory."
                            );
                            toggle();
                            return;
                        }
                        circularPairModes.put(pairIndex, false);
                        rebuildActiveBuildTargets();
                        stopMovement();
                        calculateBuildingPath(false);
                        if (!prepareNextCircularBuildInventoryPlan()
                            && requireCompleteUInventory.get()) {
                            toggle();
                            return;
                        }
                        info(
                            "Pair " + pairIndex
                                + " no longer fits the usable inventory; using two independent columns because strict U inventory is disabled."
                        );
                        return;
                    }
                    debugLog(
                        "InventoryPlan",
                        "using pre-refill frozen plan pair=" + pairIndex
                            + " optionalTargets="
                                + plannedOptionalBuildOrder.size()
                    );
                    if (!hasSufficientPairMaterials(pairIndex)) {
                        BlockPos pairStart =
                            circularBuildAlignmentSupport(pairRoute);
                        checkpoints.add(0, new Pair<>(
                            walkingPosition(pairStart),
                            new Pair<>("preparePair", checkpointAction.getRight())
                        ));
                        checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
                        prependPlannedBuildUsedToolDeposits();
                    } else {
                        HotbarPreparation hotbarPreparation =
                            prepareBuildHotbarAtPairEntry(
                                pairRoute
                            );
                        if (hotbarPreparation
                            != HotbarPreparation.READY) {
                            if (hotbarPreparation
                                == HotbarPreparation.WAITING) {
                                checkpoints.add(0, new Pair<>(
                                    walkingPosition(pairAlignment),
                                    new Pair<>(
                                        "preparePair",
                                        checkpointAction.getRight()
                                    )
                                ));
                            } else if (hotbarPreparation
                                == HotbarPreparation
                                    .RESTOCK_REQUIRED) {
                                plannedBuildMaterialHotbarSlots.clear();
                                plannedBuildToolHotbarSlot = -1;
                                plannedBuildHotbarPair = -1;
                                plannedBuildHotbarAssignments.clear();
                                checkpoints.add(0, new Pair<>(
                                    walkingPosition(pairAlignment),
                                    new Pair<>(
                                        "preparePair",
                                        checkpointAction.getRight()
                                    )
                                ));
                                checkpoints.add(
                                    0,
                                    new Pair<>(
                                        dumpStation.getLeft(),
                                        new Pair<>("dump", null)
                                    )
                                );
                                prependPlannedBuildUsedToolDeposits();
                                debugLog(
                                    "InventoryPlan",
                                    "authoritative hotbar precondition "
                                        + "changed; restocking frozen pair="
                                        + pairIndex
                                );
                            } else if (isActive()) {
                                stopMovement();
                                toggle();
                            }
                            return;
                        }
                        activeCircularBuildPair = pairIndex;
                        activeCircularConnectorIndex = 0;
                        activeCircularPlacementCursor = 0;
                        activeCircularRouteSupportIndex = 0;
                        releaseBuildRepairSpeedMine();
                        buildRepairController.reset();
                        circularBuildPhase = CircularBuildPhase.OUTBOUND;
                        BlockPos firstTarget = mapCorner.add(
                            surfaceRuntimePosition(
                                pairRoute.outboundX(),
                                1
                            )
                        );
                        debugLog(
                            "Movement",
                            "activated circular pair=" + pairIndex
                                + " alignmentSupport="
                                    + pairAlignment.toShortString()
                                + " entrySupport="
                                    + pairEntry.toShortString()
                                + " firstTarget="
                                    + firstTarget.toShortString()
                                + " exitSupport="
                                    + circularBuildExitAlignmentSupport(
                                        pairRoute
                                    ).toShortString()
                                + " raisedEntry="
                                    + (firstTarget.getY()
                                        == pairEntry.getY() + 1)
                                + "; normal placement and auto-jump own "
                                    + "the transition"
                        );
                    }
                    stopMovement();
                    return;
                }
                case "uBuildOutboundEnd": {
                    if (activeCircularBuildPair < 0
                        || activeCircularBuildPair >= compactPlan.pairRoutes().size()) {
                        error("Circular build lost its active pair at the outbound endpoint.");
                        toggle();
                        return;
                    }
                    CompactCircularNbtPlan.PairRoute route =
                        compactPlan.pairRoutes().get(activeCircularBuildPair);
                    if (!isCircularSurfaceLegComplete(route.outboundX())) {
                        BlockPos unexpected =
                            firstUnexpectedCircularSurfaceBlock(
                                route.outboundX()
                            );
                        if (unexpected != null) {
                            error(
                                "Circular pair " + activeCircularBuildPair
                                    + " has an unexpected outbound block at "
                                    + unexpected.toShortString() + "."
                            );
                            toggle();
                            return;
                        }
                        info(
                            "Circular pair " + activeCircularBuildPair
                                + " has an unconfirmed outbound support; "
                                + "leaving through one ordered recovery "
                                + "direction before replanning it."
                        );
                        buildRecoveryPending = true;
                        stopMovement();
                        return;
                    }
                    activeCircularConnectorIndex = 0;
                    CircularBuildCheckpointPlan.Plan<BlockPos> connectorPlan =
                        circularBuildCheckpointPlan(route);
                    activeCircularConnectorSteps =
                        connectorPlan.connectorTraversalSteps();
                    circularBuildPhase = CircularBuildPhase.CONNECTOR;
                    break;
                }
                case "uBuildConnectorEnd":
                    if (circularBuildPhase != CircularBuildPhase.RETURN) {
                        error("Circular connector did not reach its validated return endpoint.");
                        toggle();
                        return;
                    }
                    break;
                case "uBuildRecoveryExit":
                    int recoveryExitX = checkpointAction.getRight().getX();
                    BlockPos recoveryWalkway = northWalkwaySupport(recoveryExitX);
                    if (!isSafeNorthWalkway(recoveryExitX)
                        || !isPlayerStandingOnSupport(recoveryWalkway)) {
                        error(
                            "Interrupted circular build did not reach its validated north endpoint."
                        );
                        toggle();
                        return;
                    }
                    boolean restockAfterEgress = buildRecoveryRestockAfterEgress;
                    buildRecoveryRestockAfterEgress = false;
                    if (!replanCircularBuildFromSafeArea(restockAfterEgress)) return;
                    stopMovement();
                    return;
                case "finishPair":
                    int finishedPairIndex = activeCircularBuildPair;
                    if (finishedPairIndex >= 0
                        && circularBuildPhase != CircularBuildPhase.RETURN) {
                        error(
                            "Circular pair " + finishedPairIndex
                                + " reached its north exit outside the return phase."
                        );
                        toggle();
                        return;
                    }
                    if (finishedPairIndex >= 0) {
                        CompactCircularNbtPlan.PairRoute exitingRoute =
                            compactPlan.pairRoutes().get(finishedPairIndex);
                        BlockPos exitAlignment =
                            circularBuildExitAlignmentSupport(exitingRoute);
                        if (!isSafeCircularBuildExitAlignment(exitingRoute)
                            || !isHorizontallyOverCheckpointSupport(
                                exitAlignment
                            )) {
                            error(
                                "Circular pair " + finishedPairIndex
                                    + " did not enter its exact one-block-back exit alignment."
                            );
                            toggle();
                            return;
                        }
                    }
                    activeCircularBuildPair = -1;
                    activeCircularConnectorIndex = -1;
                    activeCircularPlacementCursor = -1;
                    circularBuildRecoveryDirection = 0;
                    circularBuildPhase = CircularBuildPhase.NONE;
                    releaseBuildRepairSpeedMine();
                    buildRepairController.reset();
                    if (finishedPairIndex >= 0) {
                        CompactCircularNbtPlan.PairRoute finishedRoute =
                            compactPlan.pairRoutes().get(finishedPairIndex);
                        List<BlockPos> completedMandatoryTargets =
                            mandatoryCircularBuildTargets(
                                finishedRoute,
                                plannedCircularBuildPair
                                        == finishedPairIndex
                                    ? plannedDeferredMandatoryBuildOrder
                                    : List.of()
                            );
                        boolean complete =
                            completedMandatoryTargets.stream()
                            .allMatch(relative -> {
                                Block expected =
                                    buildTargets.get(relative);
                                return expected != null
                                    && latestKnownBuildBlock(
                                        mapCorner.add(relative)
                                    ) == expected;
                            });
                        if (!complete) {
                            warning(
                                "Circular pair " + finishedPairIndex
                                    + " reached its north exit with "
                                    + "unresolved mandatory work; rebuilding "
                                    + "the forward traversal plan."
                            );
                        }
                    }
                    if (pendingInterval != null) {
                        if (!replanCircularBuildFromSafeArea(false)) return;
                        stopMovement();
                        return;
                    }
                    calculateBuildingPath(false);
                    if (!prepareNextCircularBuildInventoryPlan()
                        && requireCompleteUInventory.get()) {
                        stopMovement();
                        toggle();
                        return;
                    }
                    if (nextCircularPlanNeedsRestock()
                        && !checkpoints.isEmpty()) {
                        debugLog(
                            "InventoryPlan",
                            "prepending restock before departure for pair="
                                + plannedCircularBuildPair
                        );
                        checkpoints.add(
                            0,
                            new Pair<>(
                                dumpStation.getLeft(),
                                new Pair<>("dump", null)
                            )
                        );
                        prependPlannedBuildUsedToolDeposits();
                    }
                    stopMovement();
                    return;
                case "independentColumnEnd":
                    stopMovement();
                    if (pendingInterval != null
                        && !replanCircularBuildFromSafeArea(false)) {
                        return;
                    }
                    return;
                case "logisticsDetour":
                    logisticsProgressWatchdog.reset();
                    mc.player.setSprinting(false);
                    stopMovement();
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setForwardPressed(false);
                    mc.player.setYaw(dumpStation.getRight().getLeft());
                    mc.player.setPitch(dumpStation.getRight().getRight());
                    return;
                case "sleep":
                    interactWithBlock(bed.getLeft());
                    interactTimeout = 0;
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SLEEPING));
                    return;
                case "refill":
                    debugRestock(
                        "opening scheduled chest="
                            + checkpointAction.getRight().toShortString()
                            + " resumeState=" + state
                            + " demands=" + restockList.size()
                    );
                    resumeAfterRestockState = state;
                    restockHandlerLeaseRecoveryAttempts = 0;
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getRight());
                    return;
                case "startMine":
                    state = State.Mining;
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(true);
                    break;
                case "miningLineEnd":
                    Utils.setBackwardPressed(false);
                    checkpoints.add(new Pair(mc.player.getEntityPos(), new Pair<>("miningLineEnd", null)));
                    break;
                case "verifyUTools":
                case "resumeUTools":
                    int miningPairIndex = checkpointAction.getRight().getX();
                    boolean enforceUEntryDurability =
                        currentCheckpointAction.equals("verifyUTools");
                    if (enforceUEntryDurability) {
                        CompactCircularNbtPlan.PairRoute miningRoute =
                            compactPlan.pairRoutes().get(miningPairIndex);
                        HashMap<Item, Integer> stillMissingTools =
                            missingCircularMiningTools(miningRoute);
                        if (stillMissingTools == null) {
                            error(
                                "Could not authoritatively reconcile the "
                                    + "teardown tools at the U entry for pair "
                                    + miningPairIndex + "."
                            );
                            toggle();
                            return;
                        }
                        if (!stillMissingTools.isEmpty()
                            || !hasCompleteTeardownScaffoldReserve()) {
                            info(
                                "Teardown inventory changed before pair "
                                    + miningPairIndex
                                    + " entry; re-planning tool and "
                                    + "cobblestone scaffold restock."
                            );
                            restartCurrentMiningAssignment();
                            return;
                        }
                    }
                    if (!prepareTeardownCheckpointHotbar(
                        currentCheckpointAction,
                        checkpointGoal,
                        checkpointAction.getRight(),
                        enforceUEntryDurability,
                        false
                    )) {
                        return;
                    }
                    armContinuousTeardownRoute(
                        "Movement",
                        "shared teardown U movement",
                        miningPairIndex
                    );
                    stopMovement();
                    return;
                case "verifyTeardownScaffold":
                case "resumeTeardownScaffold":
                    ActiveTeardownScaffoldRecovery scaffoldRecovery =
                        activeTeardownScaffoldRecovery;
                    int scaffoldPair =
                        checkpointAction.getRight().getX();
                    if (scaffoldRecovery == null
                        || scaffoldRecovery.pairIndex()
                            != scaffoldPair
                        || (teardownScaffoldPhase
                                != TeardownScaffoldPhase
                                    .BUILDING_OUTBOUND
                            && teardownScaffoldPhase
                                != TeardownScaffoldPhase
                                    .CLEANING_RETURN)) {
                        failTeardownMining(
                            "Sparse teardown scaffold lost its frozen "
                                + "pair plan at entry."
                        );
                        return;
                    }
                    boolean enforceScaffoldEntryDurability =
                        currentCheckpointAction.equals(
                            "verifyTeardownScaffold"
                        );
                    if (enforceScaffoldEntryDurability) {
                        HashMap<Item, Integer> missingScaffoldTools =
                            missingMiningTools(
                                scaffoldRecovery.plannedToolStates()
                                    .keySet(),
                                scaffoldRecovery.plannedToolStates()
                            );
                        if (missingScaffoldTools == null) {
                            failTeardownMining(
                                "Could not authoritatively reconcile sparse "
                                    + "teardown scaffold tools at entry."
                            );
                            return;
                        }
                        if (!missingScaffoldTools.isEmpty()
                            || !hasCompleteTeardownScaffoldReserve()) {
                            info(
                                "Sparse teardown scaffold inventory changed "
                                    + "before pair " + scaffoldPair
                                    + " entry; re-planning tool and "
                                    + "cobblestone restock."
                            );
                            restartCurrentMiningAssignment();
                            return;
                        }
                    }
                    if (!prepareTeardownCheckpointHotbar(
                        currentCheckpointAction,
                        checkpointGoal,
                        checkpointAction.getRight(),
                        enforceScaffoldEntryDurability,
                        true
                    )) {
                        return;
                    }
                    armContinuousTeardownRoute(
                        "TeardownScaffold",
                        "scaffold route phase="
                            + teardownScaffoldPhase
                            + " cobblestoneSlot="
                            + activeTeardownScaffoldHotbarSlot,
                        scaffoldPair
                    );
                    stopMovement();
                    return;
                case "verifyIndependentTools":
                    int independentLine = checkpointAction.getRight().getX();
                    HashMap<Item, Integer> missingIndependentTools =
                        missingMiningTools(independentMiningTargets(independentLine));
                    if (missingIndependentTools == null) {
                        error(
                            "Could not authoritatively reconcile the "
                                + "teardown tools at independent line "
                                + independentLine + "."
                        );
                        toggle();
                        return;
                    }
                    if (!missingIndependentTools.isEmpty()
                        || !hasCompleteTeardownScaffoldReserve()) {
                        info(
                            "Teardown inventory changed before independent "
                                + "line " + independentLine
                                + "; re-planning tool and cobblestone "
                                + "scaffold restock."
                        );
                        restartCurrentMiningAssignment();
                        return;
                    }
                    HotbarPreparation independentHotbarPreparation =
                        prepareTeardownHotbarLoadout();
                    if (independentHotbarPreparation
                        != HotbarPreparation.READY) {
                        if (independentHotbarPreparation
                            == HotbarPreparation.WAITING) {
                            checkpoints.add(0, new Pair<>(
                                checkpointGoal,
                                new Pair<>(
                                    "verifyIndependentTools",
                                    checkpointAction.getRight()
                                )
                            ));
                        } else if (isActive()) {
                            toggle();
                        }
                        stopMovement();
                        return;
                    }
                    strictMiningRestockActive = false;
                    strictMiningInventoryPlan = null;
                    stopMovement();
                    return;
                case "uMiningTaskEnd":
                    timeoutTicks = mineLineEndTimeout.get();
                    completeCurrentMiningAssignment();
                    return;
                case "uMiningRecoveryExit":
                    info(
                        "Reached the safe U endpoint; replanning "
                            + "teardown and tool restock."
                    );
                    restartCurrentMiningAssignment();
                    return;
                case "usedToolChest":
                    BlockPos usedToolChestPos = checkpointAction.getRight();
                    if (usedToolChestPos == null) usedToolChestPos = usedToolChest.getLeft();
                    pendingUsedToolDeposit = null;
                    Set<Item> plannedDepositItems =
                        usedToolDepositPlan.get(usedToolChestPos);
                    Set<Integer> plannedDepositSlots =
                        usedToolDepositSlotPlan.get(
                            usedToolChestPos
                        );
                    boolean hasPlannedItems =
                        plannedDepositItems != null
                            && !plannedDepositItems.isEmpty();
                    boolean hasPlannedSlots =
                        plannedDepositSlots != null
                            && !plannedDepositSlots.isEmpty();
                    if (!hasPlannedItems && !hasPlannedSlots) {
                        failInventoryTransaction(
                            "Used-tool checkpoint has no matching "
                                + "non-empty deposit plan at "
                                + usedToolChestPos.toShortString() + "."
                        );
                        return;
                    }
                    currentUsedToolDepositItems =
                        hasPlannedItems
                            ? new HashSet<>(plannedDepositItems)
                            : new HashSet<>();
                    currentUsedToolDepositSlots =
                        hasPlannedSlots
                            ? new HashSet<>(plannedDepositSlots)
                            : new HashSet<>();
                    activeUsedToolDepositChest =
                        new BlockPos(usedToolChestPos);
                    state = State.AwaitUsedToolChestResponse;
                    interactWithBlock(usedToolChestPos);
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (state.equals(State.Walking)) {
                    // Done Building
                    if (SlaveSystem.isSlave()) {
                        checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                    } else {
                        if (SlaveSystem.allSlavesFinished()) {
                            if (!endBuilding()) return;
                        } else {
                            info("Waiting for slaves to finish placing...");
                            state = State.AwaitMasterAllBuilt;
                            Utils.setForwardPressed(false);
                            return;
                        }
                    }
                } else if (state.equals(State.MiningUTraversal)) {
                    restartCurrentMiningAssignment();
                    return;
                }
            }
            followingCircularConnector =
                state == State.Walking
                    && circularBuildPhase == CircularBuildPhase.CONNECTOR;
            checkpointGoal = followingCircularConnector
                ? currentCircularConnectorGoal()
                : checkpoints.get(0).getLeft();
            activeOrderedUTraversal = activeOrderedUTraversal();
            activeOrderedUMovement =
                activeOrderedUTraversal != null;
            movementGoal = activeOrderedUMovement
                ? currentActiveOrderedUMovementGoal(
                    activeOrderedUTraversal
                )
                : checkpointGoal;
        }

        //Set yaw rotation
        steerTowardGoal(movementGoal);

        // Set print mode
        String nextAction = checkpoints.get(0).getRight().getLeft();
        if (activeOrderedUMovement) {
            mc.player.setSprinting(
                shouldSprintActiveOrderedU(
                    activeOrderedUTraversal
                )
            );
        } else if (state.equals(State.MiningUTraversal)) {
            mc.player.setSprinting(false);
        } else if (circularBuildPhase == CircularBuildPhase.CONNECTOR
            || circularBuildPhase == CircularBuildPhase.RECOVERY
            || circularBuildPhase == CircularBuildPhase.RECOVERY_EXIT
            || nextAction.equals("logisticsDetour")) {
            mc.player.setSprinting(false);
        } else if ((nextAction.isEmpty()
            || nextAction.equals("lineEnd")
            || nextAction.equals("preparePair")
            || nextAction.equals("uBuildOutboundEnd")
            || nextAction.equals("independentColumnEnd")
            || nextAction.equals("finishPair"))
            && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList(
            "",
            "lineEnd",
            "sprint",
            "miningLineEnd",
            "uBuildOutboundEnd",
            "uBuildConnectorEnd",
            "independentColumnEnd",
            "finishPair"
        );
        if (!allowPlaceActions.contains(nextAction)) return;
        if (state.equals(State.MiningUTraversal)) return;
        if (activeCircularBuildPair >= 0
            && circularBuildPhase != CircularBuildPhase.OUTBOUND
            && circularBuildPhase != CircularBuildPhase.CONNECTOR
            && circularBuildPhase != CircularBuildPhase.RETURN) {
            return;
        }

        if (state.equals(State.Walking)) {
            if (miningPos == null
                && activeCircularBuildPair < 0) {
                runBuildActionScheduler();
            }
        } else {
            BlockPos nextBlockPos = getNextBlockPos(true);
            if (miningPos != null || nextBlockPos == null) return;
            Vec3d centerPos = nextBlockPos.toCenterPos();
            if (centerPos.getZ() - mc.player.getZ() > 0.5) {
                miningPos = nextBlockPos;
                BlockState blockState = MapAreaCache.getCachedBlockState(miningPos);
                state = State.Mining;
                TeardownBreakStatus breakStatus =
                    driveOrderedTeardownBreak(
                        miningPos,
                        blockState.getBlock()
                    );
                if (breakStatus == TeardownBreakStatus.FAILED) {
                    return;
                }
                if (breakStatus == TeardownBreakStatus.CLEARED) {
                    miningPos = null;
                    return;
                }
                if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }
            }
        }
    }

    // Restocking

    private Pair<BlockPos, Vec3d> getBestChest(Item item) {
        Vec3d bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Pair<BlockPos, Vec3d>> list = new ArrayList<>();
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + item.getName().getString());
            toggle();
            return new Pair<>(new BlockPos(0, 0, 0), new Vec3d(0, 0, 0));
        }
        //Get nearest chest
        for (Pair<BlockPos, Vec3d> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getLeft())) {
                debugLog(
                    "ChestSelect",
                    "skip checked item=" + Registries.ITEM.getId(item)
                        + " chest=" + p.getLeft().toShortString()
                );
                continue;
            }
            double distance = PlayerUtils.distanceTo(p.getRight());
            debugLog(
                "ChestSelect",
                "candidate item=" + Registries.ITEM.getId(item)
                    + " chest=" + p.getLeft().toShortString()
                    + " distance=" + distance
            );
            if (bestPos == null
                || distance < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            debugLog(
                "ChestSelect",
                "all registered chests checked for item="
                    + Registries.ITEM.getId(item)
                    + "; clearing checked set and rescanning"
            );
            checkedChests.clear();
            return getBestChest(item);
        }
        debugLog(
            "ChestSelect",
            "selected item=" + Registries.ITEM.getId(item)
                + " chest=" + bestChestPos.toShortString()
                + " openPos=" + bestPos
        );
        return new Pair(bestChestPos, bestPos);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        strictMiningRestockActive = false;
        strictMiningInventoryPlan = null;
        abandonRestockSession(true);
        HashMap<Item, Integer> requiredItems =
            getRequiredRestockItems();
        HashMap<Item, Integer> mandatoryItems =
            getRequiredPrimaryRestockItems();
        debugRestock(
            "building restock planning requiredItems="
                + requiredItems.size() + " onHandItems="
                + invMaterial.size()
        );

        ArrayList<Item> orderedRestockItems = new ArrayList<>(
            requiredItems.keySet()
        );
        orderedRestockItems.sort(
            Comparator
                .comparingInt((Item item) ->
                    Math.max(
                        0,
                        mandatoryItems.getOrDefault(item, 0)
                            - invMaterial.getOrDefault(item, 0)
                    ) > 0 ? 0 : 1)
                .thenComparing(item ->
                    Registries.ITEM.getId(item).toString())
        );
        for (Item item : orderedRestockItems) {
            int targetAmount = requiredItems.getOrDefault(item, 0);
            int mandatoryTarget =
                mandatoryItems.getOrDefault(item, 0);
            int onHand = invMaterial.getOrDefault(item, 0);
            int missing = Math.max(0, targetAmount - onHand);
            if (missing == 0) {
                debugRestock(
                    "demand already satisfied item="
                        + Registries.ITEM.getId(item)
                        + " target=" + targetAmount
                        + " onHand=" + onHand
                );
                continue;
            }
            ArrayList<Pair<BlockPos, Vec3d>> sources =
                materialDict.get(item);
            if (sources == null || sources.isEmpty()) {
                PrioritizedRestockPolicy.Shortfall shortfall =
                    PrioritizedRestockPolicy.classify(
                        mandatoryTarget,
                        targetAmount,
                        onHand
                    );
                if (shortfall
                    == PrioritizedRestockPolicy.Shortfall
                        .OPTIONAL_ONLY) {
                    trimUnavailableOptionalBuildDemand(
                        item,
                        onHand,
                        mandatoryTarget
                    );
                    warning(
                        "No registered chest can supply optional nearby "
                            + item.getName().getString()
                            + "; keeping the complete U reservation and "
                            + "skipping that unavailable surplus."
                    );
                    continue;
                }
                error(
                    "No registered chest can supply required active-U "
                        + item.getName().getString() + "."
                );
                stopBuildForAction();
                toggle();
                return;
            }
            RestockDemand<Item> demand =
                RestockDemand.fromOnHandAndMissing(
                    item,
                    onHand,
                    missing
                );
            int stacks = demand.remainingStacks(
                Utils.maximumStackSize(item)
            );
            info(
                "Restocking §a" + stacks + " stacks "
                    + item.getName().getString() + " (" + missing + ")"
            );
            restockList.add(demand);
            restockMandatoryTargets.put(
                item,
                mandatoryTarget
            );
            debugRestock(
                "planned demand item=" + Registries.ITEM.getId(item)
                    + " onHand=" + onHand
                    + " target="
                        + demand.targetCompatiblePlayerCount()
                    + " remaining=" + demand.remainingAmount()
                    + " mandatoryTarget=" + mandatoryTarget
                    + " stacks=" + stacks
                    + " registeredChests=" + sources.size()
            );
        }
        debugRestock(
            "building restock plan complete demands="
                + restockList.size()
        );
        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        RestockDemand<Item> closestEntry = null;
        Pair<BlockPos, Vec3d> restockPos = null;
        for (RestockDemand<Item> entry : restockList) {
            Pair<BlockPos, Vec3d> bestRestockPos =
                getBestChest(entry.item());
            if (bestRestockPos.getLeft() == null) {
                warning(
                    "No chest found for "
                        + entry.item().getName().getString()
                );
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getRight());
            debugRestock(
                "checkpoint candidate item="
                    + Registries.ITEM.getId(entry.item())
                    + " remaining=" + entry.remainingAmount()
                    + " chest="
                        + bestRestockPos.getLeft().toShortString()
                    + " distance=" + chestDistance
            );
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        //Set closest material as first and as checkpoint
        restockList.remove(closestEntry);
        restockList.add(0, closestEntry);
        checkpoints.add(0, new Pair(restockPos.getRight(), new Pair("refill", restockPos.getLeft())));
        debugRestock(
            "scheduled next demand item="
                + Registries.ITEM.getId(closestEntry.item())
                + " remaining=" + closestEntry.remainingAmount()
                + " chest=" + restockPos.getLeft().toShortString()
                + " openPos=" + restockPos.getRight()
        );
    }

    private void endRestocking() {
        debugRestockSnapshot("ending chest session");
        RestockDemand<Item> endingDemand =
            restockList.isEmpty() ? null : restockList.getFirst();
        debugRestock(
            "end chest session chest=" + lastInteractedChest
                + " item="
                    + (endingDemand == null
                        ? "none"
                        : Registries.ITEM.getId(endingDemand.item()))
                + " remaining="
                    + (endingDemand == null
                        ? -1 : endingDemand.remainingAmount())
        );
        pendingRestockTransfer = null;
        restockInventorySnapshot.clear();
        restockSnapshotUpdateSequence = -1L;
        restockConfirmationPhase = RestockConfirmationPhase.NONE;
        resetRestockRefillWait();
        resetRestockTransferTracking();
        restockHandlerLeaseRecoveryAttempts = 0;
        interactTimeout = 0;
        if (restockList.getFirst().remainingAmount() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedChest);
            Item requestedItem = restockList.getFirst().item();
            ArrayList<Pair<BlockPos, Vec3d>> registeredChests =
                materialDict.getOrDefault(requestedItem, new ArrayList<>());
            boolean uncheckedChestExists = registeredChests.stream()
                .anyMatch(chest -> !checkedChests.contains(chest.getLeft()));
            int observedAmount =
                restockList.getFirst()
                    .targetCompatiblePlayerCount()
                    - restockList.getFirst().remainingAmount();
            int mandatoryTarget =
                restockMandatoryTargets.getOrDefault(
                    requestedItem,
                    restockList.getFirst()
                        .targetCompatiblePlayerCount()
                );
            PrioritizedRestockPolicy.Shortfall shortfall =
                PrioritizedRestockPolicy.classify(
                    mandatoryTarget,
                    restockList.getFirst()
                        .targetCompatiblePlayerCount(),
                    observedAmount
                );
            if (strictMiningRestockActive && !uncheckedChestExists) {
                error(
                    "Registered chests do not contain enough "
                        + requestedItem.getName().getString()
                        + " for the verified mining traversal."
                );
                strictMiningRestockActive = false;
                pendingRestockTransfer = null;
                restockList.clear();
                toggle();
                return;
            }
            if (!uncheckedChestExists
                && buildingActive
                && plannedCircularBuildPair >= 0
                && shortfall
                    == PrioritizedRestockPolicy.Shortfall
                        .MANDATORY) {
                error(
                    "Registered chests do not contain enough "
                        + requestedItem.getName().getString()
                        + " to guarantee circular pair "
                        + plannedCircularBuildPair + "."
                );
                pendingRestockTransfer = null;
                restockList.clear();
                stopBuildForAction();
                toggle();
                return;
            }
            if (!uncheckedChestExists
                && buildingActive
                && plannedCircularBuildPair >= 0
                && shortfall
                    == PrioritizedRestockPolicy.Shortfall
                        .OPTIONAL_ONLY) {
                trimUnavailableOptionalBuildDemand(
                    requestedItem,
                    observedAmount,
                    mandatoryTarget
                );
                warning(
                    "Optional nearby "
                        + requestedItem.getName().getString()
                        + " was not fully available after checking every "
                        + "registered chest; continuing with the complete "
                        + "U reservation."
                );
                debugRestock(
                    "dropping optional-only shortfall item="
                        + Registries.ITEM.getId(requestedItem)
                        + " observed=" + observedAmount
                        + " mandatoryTarget=" + mandatoryTarget
                        + " desiredTarget="
                            + restockList.getFirst()
                                .targetCompatiblePlayerCount()
                );
                checkedChests.clear();
                restockList.remove(0);
                restockMandatoryTargets.remove(requestedItem);
                addClosestRestockCheckpoint();
                if (SlaveSystem.isSlave()
                    && checkpoints.isEmpty()) {
                    state = State.AwaitSlaveMineLine;
                    SlaveSystem.queueMasterDM("finished");
                    return;
                }
                timeoutTicks = postRestockDelay.get();
                state = resumeAfterRestockState == null
                    ? State.Walking
                    : resumeAfterRestockState;
                resumeAfterRestockState = null;
                return;
            }
            if (!uncheckedChestExists) {
                error(
                    "Registered chests do not contain enough "
                        + requestedItem.getName().getString()
                        + " for the current inventory plan."
                );
                pendingRestockTransfer = null;
                restockList.clear();
                toggle();
                return;
            }
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(requestedItem);
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
            debugRestock(
                "scheduled alternate chest="
                    + bestRestockPos.getLeft().toShortString()
                    + " item=" + Registries.ITEM.getId(requestedItem)
            );
        } else {
            checkedChests.clear();
            Item completedItem =
                restockList.getFirst().item();
            restockList.remove(0);
            restockMandatoryTargets.remove(completedItem);
            debugRestock(
                "completed demand; remainingDemandCount="
                    + restockList.size()
            );
            addClosestRestockCheckpoint();
            if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                // Finish building as slave
                state = State.AwaitSlaveMineLine;
                SlaveSystem.queueMasterDM("finished");
                return;
            }
        }
        timeoutTicks = postRestockDelay.get();
        state = resumeAfterRestockState == null ? State.Walking : resumeAfterRestockState;
        debugRestock(
            "leaving chest session resumeState=" + state
                + " postDelay=" + timeoutTicks
        );
        resumeAfterRestockState = null;
    }

    private Item getMaterialFromPos(BlockPos pos) {
        for (Item item : materialDict.keySet()) {
            for (Pair<BlockPos, Vec3d> p : materialDict.get(item)) {
                if (p.getLeft().equals(pos)) return item;
            }
        }
        warning("Could not find material for chest position : " + pos.toShortString());
        toggle();
        return null;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos chestPos) {
        debugLog(
            "Interaction",
            "interacting block=" + chestPos.toShortString()
                + " currentSync="
                    + mc.player.currentScreenHandler.syncId
                + " retryTicks=" + retryInteractTimer.get()
        );
        Utils.setForwardPressed(false);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setYaw((float) Rotations.getYaw(chestPos.toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(chestPos.toCenterPos()));

        BlockHitResult hitResult = new BlockHitResult(chestPos.toCenterPos(), Utils.getInteractionSide(chestPos), chestPos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);

        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private TpsScaledActionBudget createWorkActionBudget() {
        return new TpsScaledActionBudget(
            maxBlockActionsPerSecond.get(),
            minimumBlockActionTps.get(),
            MINIMUM_STALE_SERVER_TICK_SECONDS,
            WORK_ACTION_BURST_CAP
        );
    }

    private void refreshWorkActionBudget() {
        if (!buildingActive && !miningAssignmentsActive) return;
        if (workActionBudget == null
            || Double.compare(
                workActionBudget.maximumActionsPerSecond(),
                maxBlockActionsPerSecond.get()
            ) != 0
            || Double.compare(
                workActionBudget.minimumTps(),
                minimumBlockActionTps.get()
            ) != 0) {
            workActionBudget = createWorkActionBudget();
        }

        boolean tpsSampleWarmingUp =
            scaleBlockRateWithTps.get()
                && (mc.player == null
                    || mc.player.age < TPS_SAMPLE_WARMUP_TICKS);
        double sampledTps = scaleBlockRateWithTps.get()
            ? (tpsSampleWarmingUp
                ? Double.NaN
                : TickRate.INSTANCE.getTickRate())
            : TpsScaledActionBudget.NORMAL_SERVER_TPS;
        double serverTickAge = scaleBlockRateWithTps.get()
            ? (tpsSampleWarmingUp
                ? 0.0
                : TickRate.INSTANCE.getTimeSinceLastTick())
            : 0.0;
        TpsScaledActionBudget.PauseReason previousPauseReason =
            workActionBudget.pauseReason();
        long budgetNowNanos = System.nanoTime();
        workActionBudget.beginTick(
            budgetNowNanos,
            sampledTps,
            serverTickAge
        );
        boolean pauseChanged =
            previousPauseReason != workActionBudget.pauseReason();
        boolean periodicSummaryDue =
            workActionBudget.grantedThisTick() > 0
                && clientActionTick - lastActionBudgetDebugTick
                    >= debugPrintInterval.get();
        if (pauseChanged || periodicSummaryDue) {
            lastActionBudgetDebugTick = clientActionTick;
            double elapsedSeconds = lastActionBudgetDebugNanos <= 0L
                ? 0.0
                : (budgetNowNanos - lastActionBudgetDebugNanos)
                    / 1_000_000_000.0;
            long submittedActions =
                placementAttempts
                    + teardownScaffoldPlacementAttempts
                    + repairBreakAttempts
                    + teardownBreakAttempts;
            long confirmedActions =
                confirmedPlacements
                    + confirmedTeardownScaffoldPlacements
                    + confirmedRepairBreaks
                    + confirmedTeardownBreaks;
            double submittedBps = elapsedSeconds <= 0.0
                ? 0.0
                : (submittedActions
                    - lastActionBudgetPlacementAttempts)
                    / elapsedSeconds;
            double confirmedBps = elapsedSeconds <= 0.0
                ? 0.0
                : (confirmedActions
                    - lastActionBudgetConfirmedPlacements)
                    / elapsedSeconds;
            debugLog(
                "ActionBudget",
                "sampledTps=" + sampledTps
                    + " serverTickAge=" + serverTickAge
                    + " configuredBps="
                        + workActionBudget.maximumActionsPerSecond()
                    + " granted="
                        + workActionBudget.grantedThisTick()
                    + " carry=" + workActionBudget.fractionalCarry()
                    + " pause=" + workActionBudget.pauseReason()
                    + " submittedBps="
                        + String.format(
                            Locale.ROOT,
                            "%.2f",
                            submittedBps
                        )
                    + " confirmedBps="
                        + String.format(
                            Locale.ROOT,
                            "%.2f",
                            confirmedBps
                        )
                    + " submittedTotal=" + submittedActions
                    + " confirmedTotal=" + confirmedActions
                    + " breakdown={placement="
                        + placementAttempts + "/"
                        + confirmedPlacements
                    + ",repair=" + repairBreakAttempts + "/"
                        + confirmedRepairBreaks
                    + ",scaffold="
                        + teardownScaffoldPlacementAttempts + "/"
                        + confirmedTeardownScaffoldPlacements
                    + ",teardown=" + teardownBreakAttempts + "/"
                        + confirmedTeardownBreaks + "}"
            );
            lastActionBudgetDebugNanos = budgetNowNanos;
            lastActionBudgetPlacementAttempts = submittedActions;
            lastActionBudgetConfirmedPlacements = confirmedActions;
        }
        if (!workActionBudget.paused()) {
            if (buildingActive) printActionTick++;
            if (miningAssignmentsActive) miningActionTick++;
        }
    }

    private boolean reconcilePendingBuildPlacements() {
        if (pendingPlacementLedger == null
            || pendingPlacementLedger.isEmpty()
            || mc.player == null
            || mc.world == null) {
            return true;
        }

        for (PendingPlacementLedger.PendingAttempt<BlockPos, Block> attempt
            : pendingPlacementLedger.pendingAttempts()) {
            BlockPos world = attempt.key();
            ServerBlockObservation observed =
                serverBlockObservations.get(world);
            long submittedAfter =
                placementSubmissionBlockSequences.getOrDefault(
                    world,
                    Long.MAX_VALUE
                );
            if (observed == null
                || observed.sequence() <= submittedAfter
                || observed.block() == Blocks.AIR) {
                pendingPlacementLedger.observeUnresolved(world);
                if (printActionTick - attempt.firstSubmittedTick()
                    >= PLACEMENT_MAX_PENDING_TICKS) {
                    if (optionalPendingPlacements.remove(world)) {
                        pendingPlacementLedger.remove(world);
                        placementSubmissionBlockSequences.remove(world);
                        warning(
                            "Nearby placement at " + world.toShortString()
                                + " was not server-confirmed; returning it "
                                + "to normal ordered planning."
                        );
                        continue;
                    }
                    error(
                        "Placement acknowledgement timed out at "
                            + world.toShortString()
                            + " before the server confirmed the expected "
                            + attempt.expected().getName().getString() + "."
                    );
                    stopBuildForAction();
                    toggle();
                    return false;
                }
                continue;
            }
            if (observed.block() == attempt.expected()) {
                debugLog(
                    "Placement",
                    "authoritative expected block position="
                        + world.toShortString()
                        + " block="
                        + Registries.BLOCK.getId(observed.block())
                        + " sequence=" + observed.sequence()
                        + " submittedAfter=" + submittedAfter
                        + " attempts=" + attempt.totalAttempts()
                        + " optional="
                            + optionalPendingPlacements.contains(world)
                );
                PendingPlacementLedger.Observation<BlockPos, Block> result =
                    pendingPlacementLedger.observePresent(
                        world,
                        observed.block()
                    );
                if (result.status()
                    == PendingPlacementLedger.ObservationStatus.CONFIRMED) {
                    boolean optionalPlacement =
                        optionalPendingPlacements.contains(world);
                    confirmedPlacements++;
                    if (mapCorner != null) {
                        BlockPos relative = world.subtract(mapCorner);
                        if (buildTargets.get(relative)
                            == observed.block()) {
                            confirmedBuildTargetsThisRun.add(relative);
                        }
                    }
                    debugLog(
                        "Placement",
                        "confirmed position=" + world.toShortString()
                            + " confirmedTotal="
                                + confirmedPlacements
                    );
                    if (!optionalPlacement
                        && isCurrentMandatoryBuildWorldTarget(world)) {
                        recordConfirmedPrimaryMaterialUse(
                            observed.block().asItem()
                        );
                    }
                    optionalPendingPlacements.remove(world);
                    placementSubmissionBlockSequences.remove(world);
                    buildRepairController.observe(
                        world,
                        RepairMineController.Observation.EXPECTED,
                        printActionTick
                    );
                    repairSubmissionBlockSequences.remove(world);
                }
                continue;
            }

            debugLog(
                "Placement",
                "authoritative mismatch position="
                    + world.toShortString()
                    + " expected="
                    + Registries.BLOCK.getId(attempt.expected())
                    + " observed="
                    + Registries.BLOCK.getId(observed.block())
                    + " sequence=" + observed.sequence()
                    + " repairEligible="
                        + (isCurrentActivePairWorldTarget(world)
                            && repairCurrentUPair.get())
            );
            pendingPlacementLedger.remove(world);
            optionalPendingPlacements.remove(world);
            placementSubmissionBlockSequences.remove(world);
            if (!isCurrentActivePairWorldTarget(world)
                || !repairCurrentUPair.get()) {
                if (!knownErrors.contains(world)) knownErrors.add(world);
            }
        }

        for (PendingPlacementLedger.TimeoutDecision<BlockPos, Block> decision
            : pendingPlacementLedger.advance(printActionTick, 0)) {
            if (decision.action()
                != PendingPlacementLedger.TimeoutAction.EXPIRED) {
                continue;
            }
            BlockPos world = decision.attempt().key();
            placementSubmissionBlockSequences.remove(world);
            if (optionalPendingPlacements.remove(world)) {
                warning(
                    "Nearby placement at " + world.toShortString()
                        + " was not confirmed; it will return to the normal "
                        + "ordered build path."
                );
                continue;
            }
            error(
                "Placement at " + world.toShortString()
                    + " was not confirmed after "
                    + decision.attempt().totalAttempts()
                    + " bounded attempts."
            );
            stopBuildForAction();
            toggle();
            return false;
        }
        return true;
    }

    private boolean reconcileTeardownScaffoldPlacements() {
        if (teardownScaffoldPlacementLedger == null
            || teardownScaffoldPlacementLedger.isEmpty()) {
            return true;
        }
        if (teardownScaffoldPhase
                != TeardownScaffoldPhase.BUILDING_OUTBOUND
            || activeTeardownScaffoldRecovery == null) {
            teardownScaffoldPlacementLedger.reset();
            teardownScaffoldSubmissionBlockSequences.clear();
            return true;
        }

        for (PendingPlacementLedger.PendingAttempt<BlockPos, Block>
            attempt : teardownScaffoldPlacementLedger
                .pendingAttempts()) {
            BlockPos world = attempt.key();
            ServerBlockObservation observed =
                serverBlockObservations.get(world);
            long submittedAfter =
                teardownScaffoldSubmissionBlockSequences.getOrDefault(
                    world,
                    Long.MAX_VALUE
                );
            if (observed == null
                || observed.sequence() <= submittedAfter
                || observed.block() == Blocks.AIR) {
                teardownScaffoldPlacementLedger.observeUnresolved(world);
                if (miningActionTick - attempt.firstSubmittedTick()
                    >= PLACEMENT_MAX_PENDING_TICKS) {
                    return failTeardownScaffoldPlacement(
                        "Temporary cobblestone at "
                            + world.toShortString()
                            + " was not server-confirmed before the "
                            + "bounded placement window."
                    );
                }
                continue;
            }
            if (observed.block() != Blocks.COBBLESTONE) {
                return failTeardownScaffoldPlacement(
                    "Temporary scaffold target changed to "
                        + observed.block().getName().getString()
                        + " at " + world.toShortString() + "."
                );
            }
            PendingPlacementLedger.Observation<BlockPos, Block>
                confirmation =
                    teardownScaffoldPlacementLedger.observePresent(
                        world,
                        observed.block()
                    );
            if (confirmation.status()
                == PendingPlacementLedger.ObservationStatus.CONFIRMED) {
                confirmedTeardownScaffoldPlacements++;
                teardownScaffoldSubmissionBlockSequences.remove(world);
                debugLog(
                    "TeardownScaffold",
                    "placement confirmed position="
                        + world.toShortString()
                        + " sequence=" + observed.sequence()
                        + " confirmed="
                            + confirmedTeardownScaffoldPlacements
                );
            }
        }
        for (PendingPlacementLedger.TimeoutDecision<BlockPos, Block>
            timeout : teardownScaffoldPlacementLedger.advance(
                miningActionTick,
                0
            )) {
            if (timeout.action()
                == PendingPlacementLedger.TimeoutAction.EXPIRED) {
                return failTeardownScaffoldPlacement(
                    "Temporary scaffold placement expired at "
                        + timeout.attempt().key().toShortString() + "."
                );
            }
        }
        return true;
    }

    private boolean failTeardownScaffoldPlacement(String reason) {
        error(reason);
        debugLog(
            "TeardownScaffold",
            "placement failure reason=" + reason
                + " phase=" + teardownScaffoldPhase
                + " pending="
                    + teardownScaffoldPlacementLedger
                        .pendingAttempts()
        );
        resetTeardownMiningActionState();
        stopMovement();
        toggle();
        return false;
    }

    private void runTeardownScaffoldPlacementScheduler() {
        if (teardownScaffoldPhase
                != TeardownScaffoldPhase.BUILDING_OUTBOUND
            || activeTeardownScaffoldRecovery == null
            || !activeContinuousTeardownArmed
            || workActionBudget == null
            || mc.player == null
            || mc.world == null) {
            return;
        }
        if (!dispatchDueTeardownScaffoldPlacementRetries()) return;
        if (buildMovementBlockedThisTick) return;

        int slot = -1;
        for (BlockPos target :
            activeTeardownScaffoldRecovery.scaffoldTargets()) {
            Block current = latestKnownBuildBlock(target);
            if (current == Blocks.COBBLESTONE
                || teardownScaffoldPlacementLedger.isPending(target)) {
                continue;
            }
            if (current != Blocks.AIR) {
                failTeardownScaffoldPlacement(
                    "Temporary scaffold target is occupied by "
                        + current.getName().getString() + " at "
                        + target.toShortString() + "."
                );
                return;
            }
            if (!isBuildPlacementInReach(target)) continue;
            BuildPlacementPolicy.Mode mode =
                teardownScaffoldPlacementMode(target);
            if (mode == BuildPlacementPolicy.Mode.BLOCKED) continue;
            if (slot < 0) {
                slot = ensureTeardownScaffoldHotbarSlot();
                if (slot == HOTBAR_SLOT_PENDING) {
                    stopActiveOrderedUForAction(
                        CircularBuildMovementPolicy.HoldReason
                            .HOTBAR_SWAP_CONFIRMATION
                    );
                    return;
                }
                if (slot == HOTBAR_ITEM_UNAVAILABLE) return;
            }
            if (!workActionBudget.tryConsume()) return;
            if (!submitPlacement(
                target,
                slot,
                mode,
                Blocks.COBBLESTONE
            )) {
                continue;
            }
            teardownScaffoldPlacementAttempts++;
            boolean tracked = teardownScaffoldPlacementLedger.submit(
                target,
                Blocks.COBBLESTONE,
                miningActionTick
            );
            if (tracked) {
                teardownScaffoldSubmissionBlockSequences.put(
                    new BlockPos(target),
                    serverBlockUpdateSequence
                );
            }
            debugLog(
                "TeardownScaffold",
                "placement submitted position="
                    + target.toShortString()
                    + " slot=" + slot + " mode=" + mode
                    + " tracked=" + tracked
                    + " attemptTotal="
                        + teardownScaffoldPlacementAttempts
            );
        }
    }

    private boolean dispatchDueTeardownScaffoldPlacementRetries() {
        for (PendingPlacementLedger.PendingAttempt<BlockPos, Block>
            attempt : teardownScaffoldPlacementLedger
                .pendingAttempts()) {
            if (miningActionTick - attempt.lastAttemptTick()
                < teardownScaffoldPlacementLedger.retryAfterTicks()) {
                continue;
            }
            BlockPos target = attempt.key();
            if (!isBuildPlacementInReach(target)) {
                failTeardownScaffoldPlacement(
                    "Temporary scaffold retry left live reach at "
                        + target.toShortString() + "."
                );
                return false;
            }
            int slot = ensureTeardownScaffoldHotbarSlot();
            if (slot == HOTBAR_SLOT_PENDING) {
                stopActiveOrderedUForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .HOTBAR_SWAP_CONFIRMATION
                );
                return false;
            }
            if (slot == HOTBAR_ITEM_UNAVAILABLE
                || !workActionBudget.tryConsume()) {
                return false;
            }
            BuildPlacementPolicy.Mode mode =
                teardownScaffoldPlacementMode(target);
            if (mode == BuildPlacementPolicy.Mode.BLOCKED
                || !submitPlacement(
                    target,
                    slot,
                    mode,
                    Blocks.COBBLESTONE
                )) {
                continue;
            }
            teardownScaffoldPlacementAttempts++;
            Optional<PendingPlacementLedger.TimeoutDecision<BlockPos, Block>>
                reserved =
                    teardownScaffoldPlacementLedger.reserveRetry(
                        target,
                        miningActionTick
                    );
            if (reserved.isPresent()
                && reserved.orElseThrow().action()
                    == PendingPlacementLedger.TimeoutAction.EXPIRED) {
                failTeardownScaffoldPlacement(
                    "Temporary scaffold retries were exhausted at "
                        + target.toShortString() + "."
                );
                return false;
            }
            teardownScaffoldSubmissionBlockSequences.put(
                new BlockPos(target),
                serverBlockUpdateSequence
            );
            debugLog(
                "TeardownScaffold",
                "placement retry submitted position="
                    + target.toShortString()
                    + " totalAttempts="
                        + (reserved.isEmpty()
                            ? attempt.totalAttempts()
                            : reserved.orElseThrow().attempt()
                                .totalAttempts())
            );
        }
        return true;
    }

    private BuildPlacementPolicy.Mode
        teardownScaffoldPlacementMode(BlockPos world) {
        Direction side = BlockUtils.getPlaceSide(world);
        boolean adjacentPending = side != null
            && teardownScaffoldPlacementLedger.isPending(
                world.offset(side)
            );
        return BuildPlacementPolicy.select(
            isBuildPlacementInReach(world),
            latestKnownBuildBlock(world) == Blocks.AIR,
            BlockUtils.canPlace(world),
            side != null,
            adjacentPending,
            true,
            false
        );
    }

    private boolean handleConfirmedMiningHotbarSwap() {
        if (confirmedMiningHotbarSwap == null
            || !confirmedMiningHotbarSwap.isPending()) {
            return false;
        }
        if (mc.player == null || mc.player.isDead()) {
            confirmedMiningHotbarSwap.clear();
            clearPendingInventorySwapState();
            miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
            return false;
        }

        MiningHotbarSwapContext swapContext =
            miningHotbarSwapContext;
        long swapTick = swapContext.clockTick(
            clientActionTick,
            printActionTick
        );
        MiningToolIdentity expected =
            confirmedMiningHotbarSwap.expected();
        int targetSlot =
            confirmedMiningHotbarSwap.targetHotbarSlot();
        ItemStack destination =
            mc.player.getInventory().getStack(targetSlot);
        MiningToolIdentity localDestination =
            miningToolIdentity(destination);
        ConfirmedHotbarSwap.Observation observation =
            confirmedMiningHotbarSwap.observe(
                serverHotbarObservedTools[targetSlot],
                serverHotbarSwapAckSequences[targetSlot],
                swapTick,
                HOTBAR_SWAP_TIMEOUT_TICKS,
                HOTBAR_SWAP_MAX_ATTEMPTS
            );

        if (observation == ConfirmedHotbarSwap.Observation.IDLE) {
            return false;
        }
        if (observation == ConfirmedHotbarSwap.Observation.CONFIRMED) {
            debugLog(
                "HotbarSwap",
                "mining-tool controller confirmed targetHotbarSlot="
                    + targetSlot + " expected=" + expected
                    + " context=" + swapContext
            );
            if (repairToolSwapStaging != null
                && !repairToolSwapStaging.finalSubmitted()) {
                debugLog(
                    "HotbarSwap",
                    "staging swap confirmed; submitting desired repair "
                        + "tool finalization"
                );
                return submitFinalStagedRepairToolSwap(
                    repairToolSwapStaging,
                    swapTick
                );
            }
            repairToolSwapStaging = null;
            miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
            stopForMiningHotbarSwap(swapContext);
            return true;
        }
        if (observation == ConfirmedHotbarSwap.Observation.WAITING) {
            stopForMiningHotbarSwap(swapContext);
            return true;
        }
        if (observation == ConfirmedHotbarSwap.Observation.FAILED) {
            debugLog(
                "HotbarSwap",
                "mining-tool controller exhausted attempts="
                    + HOTBAR_SWAP_MAX_ATTEMPTS
                    + " targetHotbarSlot=" + targetSlot
                    + " expected=" + expected
                    + " serverObserved="
                        + serverHotbarObservedTools[targetSlot]
            );
            return failMiningHotbarSwap(expected);
        }

        if (localDestination.equals(expected)) {
            // The predicted click already has the intended destination. Wait
            // for a slot-specific server revision instead of issuing a SWAP
            // that could reverse the accepted click.
            confirmedMiningHotbarSwap.markRetried(
                serverHotbarSwapAckSequences[targetSlot],
                swapTick
            );
            debugLog(
                "HotbarSwap",
                "mining-tool retry suppressed because client prediction "
                    + "already matches expected targetHotbarSlot="
                    + targetSlot + " nextAttempt="
                    + confirmedMiningHotbarSwap.attempts()
                    + " ackSequence="
                        + serverHotbarSwapAckSequences[targetSlot]
            );
            stopForMiningHotbarSwap(swapContext);
            return true;
        }

        int sourceSlot;
        if (repairToolSwapStaging != null) {
            sourceSlot = repairToolSwapStaging.finalSubmitted()
                ? repairToolSwapStaging.desiredSourceSlot()
                : repairToolSwapStaging.stagingSourceSlot();
            ItemStack source =
                mc.player.getInventory().getStack(sourceSlot);
            if (source.isEmpty()
                || !miningToolIdentity(source).equals(expected)) {
                return failMiningHotbarSwap(expected);
            }
        } else {
            sourceSlot = findMatchingMiningMainInventorySlot(
                expected,
                targetSlot
            );
        }
        if (sourceSlot < 0) return failMiningHotbarSwap(expected);

        if (!dispatchConfirmedInventorySwap(
            sourceSlot,
            targetSlot,
            "mining-tool retry",
            true
        )) {
            return failMiningHotbarSwap(expected);
        }
        confirmedMiningHotbarSwap.markRetried(
            serverHotbarSwapAckSequences[targetSlot],
            swapTick
        );
        debugLog(
            "HotbarSwap",
            "mining-tool retry dispatched sourceSlot=" + sourceSlot
                + " targetHotbarSlot=" + targetSlot
                + " attempt=" + confirmedMiningHotbarSwap.attempts()
                + " context=" + swapContext
        );
        stopForMiningHotbarSwap(swapContext);
        return true;
    }

    private void stopForMiningHotbarSwap(
        MiningHotbarSwapContext swapContext
    ) {
        if (swapContext == MiningHotbarSwapContext.BUILD_REPAIR) {
            stopBuildForAction();
        } else {
            stopMovement();
        }
    }

    private boolean submitFinalStagedRepairToolSwap(
        RepairToolSwapStaging staging,
        long swapTick
    ) {
        ItemStack desiredSource =
            mc.player.getInventory().getStack(
                staging.desiredSourceSlot()
            );
        if (desiredSource.isEmpty()
            || !miningToolIdentity(desiredSource).equals(
                staging.desiredIdentity()
            )) {
            return failMiningHotbarSwap(
                staging.desiredIdentity()
            );
        }

        repairToolSwapStaging = staging.markFinalSubmitted();
        debugLog(
            "HotbarSwap",
            "beginning staged repair-tool finalization desiredSourceSlot="
                + staging.desiredSourceSlot()
                + " targetHotbarSlot="
                    + staging.targetHotbarSlot()
                + " expected=" + staging.desiredIdentity()
        );
        confirmedMiningHotbarSwap.begin(
            staging.targetHotbarSlot(),
            staging.desiredIdentity(),
            serverHotbarSwapAckSequences[
                staging.targetHotbarSlot()
            ],
            swapTick
        );
        if (!dispatchConfirmedInventorySwap(
            staging.desiredSourceSlot(),
            staging.targetHotbarSlot(),
            "staged repair-tool finalization",
            false
        )) {
            return failMiningHotbarSwap(
                staging.desiredIdentity()
            );
        }
        stopBuildForAction();
        return true;
    }

    private boolean failMiningHotbarSwap(MiningToolIdentity expected) {
        MiningHotbarSwapContext failedContext =
            miningHotbarSwapContext;
        debugLog(
            "HotbarSwap",
            "fatal mining-tool swap failure expected=" + expected
                + " context=" + failedContext
                + " pendingMetadata="
                    + pendingInventoryMetadataSwap
        );
        confirmedMiningHotbarSwap.clear();
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        error(
            "Server did not confirm the required mining-tool swap for "
                + expected.item().getName().getString() + "."
        );
        stopForMiningHotbarSwap(failedContext);
        toggle();
        return true;
    }

    private boolean handleConfirmedBuildHotbarSwap() {
        if (confirmedBuildHotbarSwap == null
            || !confirmedBuildHotbarSwap.isPending()) {
            return false;
        }
        if (!buildingActive || mc.player == null) {
            confirmedBuildHotbarSwap.clear();
            pendingBuildHotbarSwapMandatory = false;
            clearPendingInventorySwapState();
            return false;
        }

        Item expected = confirmedBuildHotbarSwap.expected();
        boolean mandatory = pendingBuildHotbarSwapMandatory;
        int targetSlot = confirmedBuildHotbarSwap.targetHotbarSlot();
        ItemStack destination =
            mc.player.getInventory().getStack(targetSlot);
        Item localDestinationItem = destination.isEmpty()
            ? Items.AIR
            : destination.getItem();
        Item serverDestinationItem =
            serverHotbarObservedItems[targetSlot];
        ConfirmedHotbarSwap.Observation observation =
            confirmedBuildHotbarSwap.observe(
                serverDestinationItem,
                serverHotbarSwapAckSequences[targetSlot],
                printActionTick,
                HOTBAR_SWAP_TIMEOUT_TICKS,
                HOTBAR_SWAP_MAX_ATTEMPTS
            );

        if (observation == ConfirmedHotbarSwap.Observation.IDLE) {
            return false;
        }
        if (observation == ConfirmedHotbarSwap.Observation.CONFIRMED) {
            debugLog(
                "HotbarSwap",
                "build-material controller confirmed targetHotbarSlot="
                    + targetSlot + " expected="
                    + Registries.ITEM.getId(expected)
            );
            pendingBuildHotbarSwapMandatory = false;
            if (mandatory) stopBuildForAction();
            return mandatory;
        }
        if (observation == ConfirmedHotbarSwap.Observation.WAITING) {
            if (mandatory) stopBuildForAction();
            return mandatory;
        }
        if (observation == ConfirmedHotbarSwap.Observation.FAILED) {
            debugLog(
                "HotbarSwap",
                "build-material controller exhausted attempts="
                    + HOTBAR_SWAP_MAX_ATTEMPTS
                    + " targetHotbarSlot=" + targetSlot
                    + " expected=" + Registries.ITEM.getId(expected)
                    + " serverObserved="
                        + Registries.ITEM.getId(
                            serverDestinationItem
                        )
            );
            return failBuildHotbarSwap(expected);
        }

        if (localDestinationItem.equals(expected)) {
            // The client prediction is already correct. Do not issue a second
            // SWAP that could reverse a server-accepted first click while its
            // acknowledgement is delayed.
            confirmedBuildHotbarSwap.markRetried(
                serverHotbarSwapAckSequences[targetSlot],
                printActionTick
            );
            debugLog(
                "HotbarSwap",
                "build-material retry suppressed because client "
                    + "prediction already matches expected "
                    + "targetHotbarSlot=" + targetSlot
                    + " nextAttempt="
                        + confirmedBuildHotbarSwap.attempts()
                    + " ackSequence="
                        + serverHotbarSwapAckSequences[targetSlot]
            );
            if (mandatory) stopBuildForAction();
            return mandatory;
        }

        int sourceSlot = findBestMainInventorySlot(
            expected,
            targetSlot
        );
        if (sourceSlot < 0) return failBuildHotbarSwap(expected);

        if (!dispatchConfirmedInventorySwap(
            sourceSlot,
            targetSlot,
            "build-material retry",
            true
        )) {
            return failBuildHotbarSwap(expected);
        }
        confirmedBuildHotbarSwap.markRetried(
            serverHotbarSwapAckSequences[targetSlot],
            printActionTick
        );
        debugLog(
            "HotbarSwap",
            "build-material retry dispatched sourceSlot=" + sourceSlot
                + " targetHotbarSlot=" + targetSlot
                + " attempt=" + confirmedBuildHotbarSwap.attempts()
                + " expected=" + Registries.ITEM.getId(expected)
        );
        if (mandatory) stopBuildForAction();
        return mandatory;
    }

    private boolean failBuildHotbarSwap(Item expected) {
        boolean mandatory = pendingBuildHotbarSwapMandatory;
        debugLog(
            "HotbarSwap",
            "fatal build-material swap failure expected="
                + Registries.ITEM.getId(expected)
                + " mandatory=" + mandatory
                + " pendingMetadata=" + pendingInventoryMetadataSwap
        );
        confirmedBuildHotbarSwap.clear();
        pendingBuildHotbarSwapMandatory = false;
        clearPendingInventorySwapState();
        if (!mandatory) {
            rejectedOptionalSwapMaterials.add(expected);
            warning(
                "Optional nearby hotbar swap was not confirmed for "
                    + expected.getName().getString()
                    + "; abandoning that surplus action without "
                    + "weakening the active U."
            );
            return false;
        }
        error(
            "Server did not confirm the required hotbar swap for "
                + expected.getName().getString() + "."
        );
        stopBuildForAction();
        toggle();
        return true;
    }

    private void runBuildActionScheduler() {
        if (!buildingActive
            || workActionBudget == null
            || mc.player == null
            || mc.world == null) {
            return;
        }

        CompactCircularNbtPlan.PairRoute activeRoute =
            activeCircularBuildRoute();
        if (!repairCurrentUPair.get()
            && buildRepairController.size() > 0) {
            error(
                "Active-U repair was disabled while a wrong block was "
                    + "still owned. Stopping before movement resumes."
            );
            stopBuildForAction();
            toggle();
            return;
        }
        List<BlockPos> activeRelativeTargets = activeRoute == null
            ? List.of()
            : activeCircularPriorityTargets(activeRoute);
        List<BlockPos> deferredMandatoryTargets =
            activeRoute != null
                && plannedCircularBuildPair
                    == activeRoute.pairIndex()
                ? plannedDeferredMandatoryBuildOrder
                : List.of();

        if (activeRoute != null) {
            if (backOffWrongActiveUSupport(activeRelativeTargets)) {
                return;
            }
            if (!synchronizeActiveCircularSupportObstructions(
                activeRoute
            )) {
                return;
            }
            observeActivePairRepairs(activeRelativeTargets);
            if (buildRecoveryPending) {
                stopBuildForAction();
                return;
            }
            if (buildRepairController.hasExpiredTargets()) {
                error(
                    "A wrong block in circular pair "
                        + activeRoute.pairIndex()
                        + " did not reach confirmed air within the bounded "
                        + "repair window."
                );
                stopBuildForAction();
                toggle();
                return;
            }
            if (!hasBreakingBuildRepair()) {
                releaseBuildRepairSpeedMine();
            }
        } else {
            releaseBuildRepairSpeedMine();
            if (buildRepairController.size() > 0) {
                buildRepairController.reset();
            }
        }

        if (workActionBudget.paused()) {
            releaseBuildRepairSpeedMine();
            if (lastPrintPauseReason != workActionBudget.pauseReason()) {
                warning(
                    "Printing paused by the server-TPS guard: "
                        + workActionBudget.pauseReason() + "."
                );
            }
            lastPrintPauseReason = workActionBudget.pauseReason();
            stopBuildForAction();
            return;
        }
        if (lastPrintPauseReason
            != TpsScaledActionBudget.PauseReason.NONE) {
            info("Server TPS recovered; resuming rate-limited printing.");
            lastPrintPauseReason =
                TpsScaledActionBudget.PauseReason.NONE;
        }
        if (activeRoute != null) {
            if (!dispatchActivePairRepairBreaks(activeRelativeTargets)) return;
            if (hasBreakingBuildRepair()) {
                stopBuildForAction();
                return;
            }
            releaseBuildRepairSpeedMine();
        }

        List<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
            primaryTargets = buildPrimaryPlacementTargets(
                activeRelativeTargets,
                deferredMandatoryTargets
            );
        HashSet<BlockPos> currentPrimaryWorld = primaryTargets.stream()
            .map(PrioritizedPlacementPlanner.Target::key)
            .collect(Collectors.toCollection(HashSet::new));
        optionalPendingPlacements.removeAll(currentPrimaryWorld);

        HashMap<Item, Integer> primaryReserve =
            activeRoute == null
                ? new HashMap<>()
                : activeCircularPrimaryMaterialReserve(activeRoute);
        if (activeRoute != null
            && recoverFromActiveCircularMaterialShortfall(activeRoute)) {
            return;
        }
        if (!dispatchDuePlacementRetries(
            currentPrimaryWorld,
            primaryReserve,
            false
        )) {
            return;
        }
        if (confirmedBuildHotbarSwap.isPending()) {
            if (pendingBuildHotbarSwapMandatory) {
                stopBuildForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .HOTBAR_SWAP_CONFIRMATION
                );
            }
            return;
        }

        int planningBudget = workActionBudget.remainingThisTick();
        PrioritizedPlacementPlanner.Plan<BlockPos, Item> primaryPlan =
            PrioritizedPlacementPlanner.plan(
                primaryTargets,
                List.of(),
                planningBudget,
                usableInventoryCounts(),
                primaryReserve,
                target -> isBuildPlacementEligible(target.key(), true),
                pendingPlacementLedger::isPending
            );
        if (!executePlacementPlan(primaryPlan)) return;

        boolean reachableSelectablePrimary =
            hasReachableSelectablePrimaryTarget(primaryTargets);
        List<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
            optionalTargets = buildOptionalPlacementTargets(
                activeRoute
            );
        if (!reachableSelectablePrimary
            && !confirmedBuildHotbarSwap.isPending()
            && workActionBudget.remainingThisTick() > 0) {
            if (!dispatchDuePlacementRetries(
                currentPrimaryWorld,
                primaryReserve,
                true
            )) {
                return;
            }
            int optionalBudget =
                workActionBudget.remainingThisTick();
            PrioritizedPlacementPlanner.Plan<BlockPos, Item> optionalPlan =
                PrioritizedPlacementPlanner.plan(
                    List.of(),
                    optionalTargets,
                    optionalBudget,
                    usableInventoryCounts(),
                    primaryReserve,
                    target -> isBuildPlacementEligible(
                        target.key(),
                        false
                    ),
                    pendingPlacementLedger::isPending
                );
            if (!executePlacementPlan(optionalPlan)) return;
        }

        if (activeRoute != null
            && enforceDeferredMandatoryCoverage(activeRoute)) {
            return;
        }
        CircularBuildMovementPolicy.HoldReason holdReason =
            CircularBuildMovementPolicy.holdReason(
                buildRepairController.size(),
                confirmedBuildHotbarSwap.isPending()
                    && pendingBuildHotbarSwapMandatory
            );
        if (holdReason
            != CircularBuildMovementPolicy.HoldReason.NONE) {
            stopBuildForAction(holdReason);
        }
    }

    private boolean hasReachableSelectablePrimaryTarget(
        List<PrioritizedPlacementPlanner.Target<BlockPos, Item>> targets
    ) {
        return PrioritizedPlacementPlanner.hasSelectableTarget(
            targets,
            usableInventoryCounts(),
            target ->
                isBuildPlacementInReach(target.key())
                    && isBuildPlacementEligible(target.key(), true),
            pendingPlacementLedger::isPending
        );
    }

    private boolean executePlacementPlan(
        PrioritizedPlacementPlanner.Plan<BlockPos, Item> plan
    ) {
        if (!plan.decisions().isEmpty()) {
            debugLog(
                "Placement",
                "executing plan decisions=" + plan.decisions().size()
                    + " remainingBudget="
                        + workActionBudget.remainingThisTick()
                    + " remainingOnHand="
                        + plan.remainingOnHand()
                    + " remainingPrimaryReserve="
                        + plan.remainingPrimaryReserve()
            );
        }
        for (PrioritizedPlacementPlanner.Decision<BlockPos, Item> decision
            : plan.decisions()) {
            boolean mandatory =
                decision.tier() == PrioritizedPlacementPlanner.Tier.PRIMARY;
            int slot = ensureBuildHotbarSlot(
                decision.material(),
                mandatory
            );
            if (slot == HOTBAR_SLOT_PENDING) {
                debugLog(
                    "Placement",
                    "waiting for hotbar swap position="
                        + decision.key().toShortString()
                        + " material="
                        + Registries.ITEM.getId(decision.material())
                        + " tier=" + decision.tier()
                );
                return false;
            }
            if (slot == HOTBAR_ITEM_UNAVAILABLE) {
                debugLog(
                    "Placement",
                    "material unavailable position="
                        + decision.key().toShortString()
                        + " material="
                        + Registries.ITEM.getId(decision.material())
                        + " tier=" + decision.tier()
                );
                if (mandatory) return false;
                continue;
            }
            if (!workActionBudget.tryConsume()) {
                debugLog(
                    "Placement",
                    "action budget exhausted before position="
                        + decision.key().toShortString()
                );
                return true;
            }

            BuildPlacementPolicy.Mode placementMode =
                buildPlacementMode(decision.key(), mandatory);
            boolean submitted = submitBuildPlacement(
                decision.key(),
                slot,
                placementMode
            );
            if (!submitted) {
                debugLog(
                    "Placement",
                    "client rejected placement position="
                        + decision.key().toShortString()
                        + " material="
                        + Registries.ITEM.getId(decision.material())
                        + " slot=" + slot
                        + " tier=" + decision.tier()
                        + " mode=" + placementMode
                );
                continue;
            }
            placementAttempts++;

            Block expected = buildTargets.get(
                decision.key().subtract(mapCorner)
            );
            boolean tracked = pendingPlacementLedger.submit(
                decision.key(),
                expected,
                printActionTick
            );
            if (tracked) {
                placementSubmissionBlockSequences.put(
                    new BlockPos(decision.key()),
                    serverBlockUpdateSequence
                );
            }
            if (tracked && !mandatory) {
                optionalPendingPlacements.add(decision.key());
            }
            debugLog(
                "Placement",
                "submitted position=" + decision.key().toShortString()
                    + " expected="
                        + (expected == null
                            ? "missing"
                            : Registries.BLOCK.getId(expected))
                    + " material="
                        + Registries.ITEM.getId(decision.material())
                    + " slot=" + slot
                    + " tier=" + decision.tier()
                    + " mode=" + placementMode
                    + " tracked=" + tracked
                    + " blockSequence="
                        + serverBlockUpdateSequence
                    + " attemptTotal=" + placementAttempts
            );
        }
        return true;
    }

    private CompactCircularNbtPlan.PairRoute activeCircularBuildRoute() {
        if (activeCircularBuildPair < 0
            || compactPlan == null
            || activeCircularBuildPair
                >= compactPlan.pairRoutes().size()) {
            return null;
        }
        return compactPlan.pairRoutes().get(activeCircularBuildPair);
    }

    private boolean hasActiveOrderedUMovement() {
        return activeOrderedUTraversal() != null;
    }

    private ActiveOrderedUTraversal activeOrderedUTraversal() {
        boolean printing =
            state == State.Walking
                && activeCircularBuildPair >= 0
                && (circularBuildPhase
                        == CircularBuildPhase.OUTBOUND
                    || circularBuildPhase
                        == CircularBuildPhase.CONNECTOR
                    || circularBuildPhase
                        == CircularBuildPhase.RETURN);
        if (printing) {
            CompactCircularNbtPlan.PairRoute route =
                activeCircularBuildRoute();
            if (route == null) return null;
            return new ActiveOrderedUTraversal(
                OrderedUTraversalOwner.PRINTING,
                route,
                activeCircularBuildSupportPath(route)
            );
        }
        if (state == State.MiningUTraversal
            && activeContinuousTeardownArmed) {
            if (compactPlan == null
                || activeContinuousTeardownPair < 0
                || activeContinuousTeardownPair
                    >= compactPlan.pairRoutes().size()) {
                return null;
            }
            return new ActiveOrderedUTraversal(
                teardownScaffoldPhase == TeardownScaffoldPhase.NONE
                    ? OrderedUTraversalOwner.TEARDOWN
                    : OrderedUTraversalOwner.TEARDOWN_SCAFFOLD,
                compactPlan.pairRoutes().get(
                    activeContinuousTeardownPair
                ),
                activeContinuousTeardownStages.stream()
                    .map(ContinuousTeardownRoutePlan.Stage::support)
                    .toList()
            );
        }
        return null;
    }

    private boolean ensureActiveOrderedUNextSupport(
        ActiveOrderedUTraversal traversal
    ) {
        List<BlockPos> supportPath = traversal.supports();
        if (supportPath.isEmpty() || mc.player == null) {
            return true;
        }
        if (activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex >= supportPath.size()) {
            stopActiveOrderedUForAction(
                CircularBuildMovementPolicy.HoldReason
                    .NEXT_ROUTE_SUPPORT_CONFIRMATION
            );
            error(
                "Active ordered U traversal lost its horizontal route cursor; "
                    + "stopping before unsafe forward movement."
            );
            toggle();
            return false;
        }
        OrderedUTraversalMovement.Progress progress =
            OrderedUTraversalMovement.resolve(
                supportPath,
                activeCircularRouteSupportIndex,
                mc.player.getX(),
                mc.player.getZ(),
                support -> isConfirmedActiveOrderedUSupport(
                    traversal,
                    support
                )
            );
        if (progress.movement().status()
            == OrderedUTraversalMovement.MovementStatus.OFF_PATH) {
            BlockPos cursorSupport = supportPath.get(
                activeCircularRouteSupportIndex
            );
            stopActiveOrderedUForAction(
                CircularBuildMovementPolicy.HoldReason
                    .NEXT_ROUTE_SUPPORT_CONFIRMATION
            );
            warning(
                "Active ordered U traversal could not reconcile its "
                    + "horizontal route "
                    + "near " + mc.player.getBlockPos().toShortString()
                    + " from cursor="
                    + activeCircularRouteSupportIndex
                    + " support=" + cursorSupport.toShortString()
                    + "; rebuilding from the authoritative support "
                    + "under the player."
            );
            if (traversal.owner()
                == OrderedUTraversalOwner.PRINTING) {
                beginBuildRecovery(false);
            } else {
                beginMiningRecovery(false);
            }
            return false;
        }
        int nextResolvedIndex = progress.currentIndex();
        if (nextResolvedIndex != activeCircularRouteSupportIndex) {
            BlockPos enteredSupport =
                supportPath.get(nextResolvedIndex);
            if (!isConfirmedActiveOrderedUSupport(
                traversal,
                enteredSupport
            )) {
                stopActiveOrderedUForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .NEXT_ROUTE_SUPPORT_CONFIRMATION
                );
                buildMovementRequiredSupportThisTick =
                    new BlockPos(enteredSupport);
                return false;
            }
            int previousSupportIndex =
                activeCircularRouteSupportIndex;
            activeCircularRouteSupportIndex = nextResolvedIndex;
            if (nextResolvedIndex < previousSupportIndex) {
                debugLog(
                    "Movement",
                    "reconciled server position correction owner="
                        + traversal.owner() + " cursor="
                        + previousSupportIndex + "->"
                        + nextResolvedIndex + " support="
                        + enteredSupport.toShortString()
                );
            }
        }
        if (traversal.owner() == OrderedUTraversalOwner.TEARDOWN) {
            BlockPos confirmedSupport = supportPath.get(
                activeCircularRouteSupportIndex
            );
            if (isConfirmedActiveOrderedUSupport(
                traversal,
                confirmedSupport
            )) {
                rememberConfirmedTeardownSupport(
                    traversal,
                    confirmedSupport
                );
            }
        }
        OrderedUTraversalMovement.MovementDecision<BlockPos> decision =
            progress.movement();
        if (decision.status()
            == OrderedUTraversalMovement.MovementStatus.COMPLETE) {
            // The final support is a hard movement boundary. Teardown may
            // still be waiting for its last authoritative air update, but
            // sprinting beyond this support would leave the ordered U.
            stopMovement();
            Utils.setJumpPressed(false);
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(0, velocity.y, 0);
            return true;
        }
        if (decision.mayMove()) return true;
        stopActiveOrderedUForAction(
            CircularBuildMovementPolicy.HoldReason
                .NEXT_ROUTE_SUPPORT_CONFIRMATION
        );
        if (decision.status()
            == OrderedUTraversalMovement.MovementStatus.OFF_PATH) {
            throw new IllegalStateException(
                "A validated horizontal route cursor became invalid."
            );
        }
        buildMovementRequiredSupportThisTick =
            new BlockPos(decision.requiredSupport());
        return false;
    }

    private boolean isConfirmedActiveOrderedUSupport(
        ActiveOrderedUTraversal traversal,
        BlockPos support
    ) {
        if (traversal.owner()
            == OrderedUTraversalOwner.TEARDOWN_SCAFFOLD) {
            return isConfirmedTeardownScaffoldSupport(support);
        }
        if (traversal.owner() == OrderedUTraversalOwner.TEARDOWN) {
            if (isSafeUCheckpointSupport(walkingPosition(support))) {
                return true;
            }
            if (activeContinuousTeardownStages.isEmpty()
                || !support.equals(
                    activeContinuousTeardownStages.getFirst().support()
                )
                || mc.world == null) {
                return false;
            }
            BlockState state = MapAreaCache.getCachedBlockState(support);
            return !state.isAir()
                && state.isSolidBlock(mc.world, support)
                && MapAreaCache.getCachedBlockState(support.up()).isAir()
                && MapAreaCache.getCachedBlockState(support.up(2)).isAir();
        }
        return isConfirmedCircularBuildSupport(
            traversal.route(),
            support
        );
    }

    private void stopActiveOrderedUForAction(
        CircularBuildMovementPolicy.HoldReason reason
    ) {
        if (reason == null
            || reason
                == CircularBuildMovementPolicy.HoldReason.NONE) {
            throw new IllegalArgumentException(
                "An ordered U hold requires a reason."
            );
        }
        buildMovementHoldReasonThisTick = reason;
        buildMovementBlockedThisTick = true;
        stopMovement();
        if (mc.player != null) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(0, velocity.y, 0);
        }
    }

    private List<BlockPos> activeCircularBuildSupportPath(
        CompactCircularNbtPlan.PairRoute route
    ) {
        List<BlockPos> worldTargets = circularPairTargets(route).stream()
            .map(mapCorner::add)
            .toList();
        return CircularBuildSupportPath.create(
            circularBuildAlignmentSupport(route),
            northWalkwaySupport(route.outboundX()),
            worldTargets,
            northWalkwaySupport(route.returnX()),
            circularBuildExitAlignmentSupport(route)
        );
    }

    private boolean isConfirmedCircularBuildSupport(
        CompactCircularNbtPlan.PairRoute route,
        BlockPos support
    ) {
        if (support.equals(circularBuildAlignmentSupport(route))) {
            return isSafeCircularBuildAlignment(route);
        }
        if (support.equals(circularBuildExitAlignmentSupport(route))) {
            return isSafeCircularBuildExitAlignment(route);
        }
        if (support.equals(northWalkwaySupport(route.outboundX()))) {
            return isSafeNorthWalkway(route.outboundX());
        }
        if (support.equals(northWalkwaySupport(route.returnX()))) {
            return isSafeNorthWalkway(route.returnX());
        }

        return circularBuildSupportReadiness(support).ready();
    }

    private CircularSupportReadiness.Assessment
        circularBuildSupportReadiness(BlockPos support) {
        BlockPos relative = support.subtract(mapCorner);
        Block expected = buildTargets.get(relative);
        Block current = latestKnownBuildBlock(support);
        return CircularSupportReadiness.assess(
            current == Blocks.AIR,
            expected != null && current == expected,
            pendingPlacementLedger.isPending(support),
            MapAreaCache.getCachedBlockState(support.up()).isAir(),
            MapAreaCache.getCachedBlockState(support.up(2)).isAir()
        );
    }

    private boolean synchronizeActiveCircularSupportObstructions(
        CompactCircularNbtPlan.PairRoute route
    ) {
        List<BlockPos> supportPath =
            activeCircularBuildSupportPath(route);
        if (activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex >= supportPath.size()) {
            return true;
        }

        int lastIndex = Math.min(
            supportPath.size() - 1,
            activeCircularRouteSupportIndex + 1
        );
        for (int index = activeCircularRouteSupportIndex;
             index <= lastIndex;
             index++) {
            BlockPos support = supportPath.get(index);
            BlockPos supportRelative = support.subtract(mapCorner);
            if (!buildTargets.containsKey(supportRelative)) {
                if (!isConfirmedCircularBuildSupport(route, support)) {
                    error(
                        "Circular route endpoint became unsafe at "
                            + support.toShortString() + "."
                    );
                    stopBuildForAction();
                    toggle();
                    return false;
                }
                continue;
            }

            CircularSupportReadiness.Assessment readiness =
                circularBuildSupportReadiness(support);
            if (!readiness.repairRequired()) {
                continue;
            }
            if (readiness.obstructionOffset() == 0) {
                if (!repairCurrentUPair.get()) {
                    error(
                        "Circular route support changed unexpectedly at "
                            + support.toShortString()
                            + " while active-U repair is disabled."
                    );
                    stopBuildForAction();
                    toggle();
                    return false;
                }
                continue;
            }

            BlockPos obstruction =
                support.up(readiness.obstructionOffset());
            BlockPos obstructionRelative =
                obstruction.subtract(mapCorner);
            BlockState obstructionState =
                MapAreaCache.getCachedBlockState(obstruction);
            if (obstructionState.isAir()) continue;
            if (buildTargets.containsKey(obstructionRelative)) {
                error(
                    "Circular route headroom conflicts with an expected "
                        + "build target at "
                        + obstruction.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            if (!repairCurrentUPair.get()) {
                error(
                    "Circular route headroom became blocked at "
                        + obstruction.toShortString()
                        + " while active-U repair is disabled."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            ItemStack registeredTool =
                getBestRegisteredTool(obstructionState);
            if (registeredTool == null
                || !BlockUtils.canBreak(
                    obstruction,
                    obstructionState
                )) {
                error(
                    "Circular route headroom cannot be repair-mined at "
                        + obstruction.toShortString() + " ("
                        + obstructionState.getBlock()
                            .getName().getString() + ")."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            if (findBestMiningInventorySlot(
                    registeredTool,
                    obstructionState,
                    -1,
                    true
                ) < 0) {
                warning(
                    "A new active-U headroom obstruction at "
                        + obstruction.toShortString()
                        + " needs "
                        + registeredTool.getName().getString()
                        + " that is not in the frozen inventory; "
                        + "returning to a safe endpoint to include and "
                        + "restock it."
                );
                buildRecoveryPending = true;
                stopBuildForAction();
                return true;
            }

            if (plannedClearOnlyRepairTargets.add(
                obstructionRelative
            )) {
                warning(
                    "A new obstruction appeared in the active U "
                        + "headroom at "
                        + obstruction.toShortString()
                        + "; clearing it with server-confirmed THM "
                        + "repair before movement resumes."
                );
                debugLog(
                    "Support",
                    "claimed dynamic clear-only obstruction pair="
                        + route.pairIndex()
                        + " support=" + support.toShortString()
                        + " obstruction="
                            + obstruction.toShortString()
                        + " status=" + readiness.status()
                        + " block="
                            + Registries.BLOCK.getId(
                                obstructionState.getBlock()
                            )
                );
            }
            if (isBuildPlacementInReach(obstruction)) {
                buildRepairController.observe(
                    obstruction,
                    RepairMineController.Observation.WRONG,
                    printActionTick
                );
            }
        }
        return true;
    }

    private List<BlockPos> activeCircularPriorityTargets(
        CompactCircularNbtPlan.PairRoute route
    ) {
        List<BlockPos> pairTargets = circularPairTargets(route);
        if (circularBuildPhase != CircularBuildPhase.OUTBOUND
            && circularBuildPhase != CircularBuildPhase.CONNECTOR
            && circularBuildPhase != CircularBuildPhase.RETURN) {
            return List.of();
        }

        int endIndexExclusive = pairTargets.size();
        int firstUnconfirmed = 0;
        while (firstUnconfirmed < endIndexExclusive) {
            BlockPos relative = pairTargets.get(firstUnconfirmed);
            Block expected = buildTargets.get(relative);
            BlockPos world = mapCorner.add(relative);
            if (expected == null
                || latestKnownBuildBlock(world) != expected) {
                break;
            }
            firstUnconfirmed++;
        }
        activeCircularPlacementCursor = firstUnconfirmed;
        return List.copyOf(
            pairTargets.subList(0, endIndexExclusive)
        );
    }

    private boolean backOffWrongActiveUSupport(
        List<BlockPos> activeRelativeTargets
    ) {
        for (BlockPos relative : activeRelativeTargets) {
            Block expected = buildTargets.get(relative);
            if (expected == null) continue;
            BlockPos world = mapCorner.add(relative);
            BlockState current =
                MapAreaCache.getCachedBlockState(world);
            if (!current.isAir()
                && current.getBlock() != expected
                && isPlayerStandingOnSupport(world)) {
                releaseBuildRepairSpeedMine();
                buildRepairController.reset();
                repairSubmissionBlockSequences.clear();
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(true);
                Utils.setJumpPressed(false);
                return true;
            }
        }
        return false;
    }

    private void observeActivePairRepairs(
        List<BlockPos> activeRelativeTargets
    ) {
        if (!repairCurrentUPair.get()) return;
        for (BlockPos relative : activeRelativeTargets) {
            Block expected = buildTargets.get(relative);
            if (expected == null) continue;
            BlockPos world = mapCorner.add(relative);
            Optional<RepairMineController.Phase> phase =
                buildRepairController.phaseOf(world);
            if (phase.isPresent()) {
                ServerBlockObservation authoritative =
                    serverBlockObservations.get(world);
                long submittedAfter =
                    repairSubmissionBlockSequences.getOrDefault(
                        world,
                        Long.MAX_VALUE
                    );
                if (authoritative != null
                    && authoritative.sequence() > submittedAfter) {
                    if (authoritative.block() == expected) {
                        debugLog(
                            "Repair",
                            "authoritative expected replacement position="
                                + world.toShortString()
                                + " block="
                                + Registries.BLOCK.getId(expected)
                                + " sequence="
                                    + authoritative.sequence()
                                + " submittedAfter=" + submittedAfter
                        );
                        buildRepairController.observe(
                            world,
                            RepairMineController.Observation.EXPECTED,
                            printActionTick
                        );
                        repairSubmissionBlockSequences.remove(world);
                    } else if (authoritative.block() == Blocks.AIR
                        && phase.get()
                            == RepairMineController.Phase.BREAKING) {
                        debugLog(
                            "Repair",
                            "authoritative air confirmed position="
                                + world.toShortString()
                                + " sequence="
                                    + authoritative.sequence()
                                + " submittedAfter=" + submittedAfter
                        );
                        buildRepairController.observe(
                            world,
                            RepairMineController.Observation.AIR,
                            printActionTick
                        );
                        plannedRepairTargets.remove(relative);
                        confirmedRepairBreaks++;
                        debugLog(
                            "Repair",
                            "break confirmed position="
                                + world.toShortString()
                                + " confirmedBreakTotal="
                                    + confirmedRepairBreaks
                        );
                        repairSubmissionBlockSequences.put(
                            new BlockPos(world),
                            authoritative.sequence()
                        );
                    } else if (authoritative.block() != Blocks.AIR) {
                        debugLog(
                            "Repair",
                            "authoritative wrong block remains position="
                                + world.toShortString()
                                + " observed="
                                + Registries.BLOCK.getId(
                                    authoritative.block()
                                )
                                + " expected="
                                + Registries.BLOCK.getId(expected)
                                + " sequence="
                                    + authoritative.sequence()
                        );
                        if (!plannedRepairTargets.contains(relative)) {
                            scheduleUnplannedActiveURepair(
                                world
                            );
                            return;
                        }
                        buildRepairController.observe(
                            world,
                            RepairMineController.Observation.WRONG,
                            printActionTick
                        );
                        repairSubmissionBlockSequences.put(
                            new BlockPos(world),
                            authoritative.sequence()
                        );
                    } else {
                        repairSubmissionBlockSequences.put(
                            new BlockPos(world),
                            authoritative.sequence()
                        );
                    }
                }
                continue;
            }

            BlockState observed =
                MapAreaCache.getCachedBlockState(world);
            if (observed.isAir()
                || observed.getBlock() == expected) {
                continue;
            }
            if (isBuildPlacementInReach(world)
                && !isPlayerStandingOnSupport(world)) {
                if (!plannedRepairTargets.contains(relative)) {
                    scheduleUnplannedActiveURepair(world);
                    return;
                }
                buildRepairController.observe(
                    world,
                    RepairMineController.Observation.WRONG,
                    printActionTick
                );
            }
        }
        observeClearOnlyActivePairRepairs();
        buildRepairController.advance(printActionTick);
    }

    private void observeClearOnlyActivePairRepairs() {
        ArrayList<BlockPos> cleared = new ArrayList<>();
        for (BlockPos relative :
            plannedClearOnlyRepairTargets) {
            BlockPos world = mapCorner.add(relative);
            Optional<RepairMineController.Phase> phase =
                buildRepairController.phaseOf(world);
            ServerBlockObservation authoritative =
                serverBlockObservations.get(world);
            long submittedAfter =
                repairSubmissionBlockSequences.getOrDefault(
                    world,
                    Long.MAX_VALUE
                );
            if (phase.isPresent()
                && authoritative != null
                && authoritative.sequence() > submittedAfter) {
                if (authoritative.block() == Blocks.AIR) {
                    buildRepairController.observe(
                        world,
                        RepairMineController.Observation.EXPECTED,
                        printActionTick
                    );
                    repairSubmissionBlockSequences.remove(world);
                    confirmedRepairBreaks++;
                    cleared.add(relative);
                    debugLog(
                        "Repair",
                        "authoritative clear-only air confirmed position="
                            + world.toShortString()
                            + " sequence="
                                + authoritative.sequence()
                            + " submittedAfter=" + submittedAfter
                            + " confirmedBreakTotal="
                                + confirmedRepairBreaks
                    );
                } else {
                    buildRepairController.observe(
                        world,
                        RepairMineController.Observation.WRONG,
                        printActionTick
                    );
                    repairSubmissionBlockSequences.put(
                        new BlockPos(world),
                        authoritative.sequence()
                    );
                }
                continue;
            }

            BlockState observed =
                MapAreaCache.getCachedBlockState(world);
            if (observed.isAir()) {
                if (phase.isEmpty()) {
                    cleared.add(relative);
                }
                continue;
            }
            if (isBuildPlacementInReach(world)) {
                buildRepairController.observe(
                    world,
                    RepairMineController.Observation.WRONG,
                    printActionTick
                );
            }
        }
        plannedClearOnlyRepairTargets.removeAll(cleared);
    }

    private void scheduleUnplannedActiveURepair(BlockPos world) {
        warning(
            "An unbudgeted wrong block appeared in the active U at "
                + world.toShortString()
                + "; returning to a safe north endpoint to replan its "
                + "material and tool budget."
        );
        buildRecoveryPending = true;
        stopBuildForAction();
    }

    private boolean dispatchActivePairRepairBreaks(
        List<BlockPos> activeRelativeTargets
    ) {
        if (!repairCurrentUPair.get()) return true;
        ArrayList<BlockPos> orderedRepairTargets =
            new ArrayList<>(activeRelativeTargets);
        for (BlockPos relative :
            plannedClearOnlyRepairTargets) {
            if (!orderedRepairTargets.contains(relative)) {
                orderedRepairTargets.add(relative);
            }
        }
        for (BlockPos relative : orderedRepairTargets) {
            BlockPos world = mapCorner.add(relative);
            boolean clearOnly =
                plannedClearOnlyRepairTargets.contains(relative);
            if (buildRepairController.phaseOf(world)
                .filter(phase ->
                    phase == RepairMineController.Phase.BREAKING)
                .isEmpty()) {
                continue;
            }
            if (!isBuildPlacementInReach(world)) {
                error(
                    "Active U repair target moved out of reach at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            if (isPlayerStandingOnSupport(world)) {
                stopBuildForAction();
                return true;
            }

            BlockState targetState =
                MapAreaCache.getCachedBlockState(world);
            Block expected = buildTargets.get(relative);
            if (targetState.isAir()
                || (!clearOnly
                    && expected != null
                    && targetState.getBlock() == expected)) {
                continue;
            }
            if (!BlockUtils.canBreak(world, targetState)) {
                error(
                    "Owned active-U repair obstruction cannot be broken at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }

            int toolSlot = ensureBuildRepairTool(
                world,
                targetState
            );
            if (toolSlot == HOTBAR_SLOT_PENDING) {
                stopBuildForAction();
                return true;
            }
            if (toolSlot == HOTBAR_ITEM_UNAVAILABLE) return false;

            if (thmInstantRepair.get()
                && !acquireBuildRepairSpeedMine(
                    targetState.getBlock()
                )) {
                error(
                    "Meteor Speed Mine is unavailable for THM-style "
                        + "active-U repair."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            SpeedMine speedMine = Modules.get().get(SpeedMine.class);
            RepairMiningClassification miningClassification =
                RepairMiningClassification.classify(
                    BlockUtils.canInstaBreak(world),
                    thmInstantRepair.get()
                        && speedMineOwner == SpeedMineOwner.BUILD_REPAIR
                        && ownedSpeedMineSnapshot != null
                        && speedMine != null
                        && speedMine.instamine(),
                    speedMine != null
                        && speedMine.filter(targetState.getBlock()),
                    targetState.calcBlockBreakingDelta(
                        mc.player,
                        mc.world,
                        world
                    )
                );
            boolean trueInstant =
                miningClassification.allowsBatchDispatch();
            RepairMineController.TargetSnapshot<BlockPos> snapshot =
                buildRepairController.snapshot(world).orElseThrow();
            debugLog(
                "Repair",
                "classified position=" + world.toShortString()
                    + " observedBlock="
                        + Registries.BLOCK.getId(
                            targetState.getBlock()
                        )
                    + " toolSlot=" + toolSlot
                    + " classification=" + miningClassification
                    + " phase=" + snapshot.phase()
                    + " previousAttempts=" + snapshot.attempts()
                    + " budgetRemaining="
                        + workActionBudget.remainingThisTick()
            );
            if (miningClassification.requiresProgressiveContinuation()
                && snapshot.attempts() > 0) {
                ((IClientPlayerInteractionManager) mc.interactionManager)
                    .setBlockBreakingCooldown(0);
                mc.player.setPitch((float) Rotations.getPitch(world));
                if (!BlockUtils.breakBlock(world, true)) {
                    error(
                        "Owned slow active-U repair could not continue at "
                            + world.toShortString() + "."
                    );
                    stopBuildForAction();
                    toggle();
                    return false;
                }
                if (!buildRepairController.recordSlowProgress(
                    world,
                    printActionTick
                )) {
                    error(
                        "Owned slow active-U repair lost its controller "
                            + "lease at " + world.toShortString() + "."
                    );
                    stopBuildForAction();
                    toggle();
                    return false;
                }
                debugLog(
                    "Repair",
                    "continued owned progressive break position="
                        + world.toShortString()
                        + " printTick=" + printActionTick
                );
                return true;
            }

            RepairMineController.BreakBatch<BlockPos> batch =
                buildRepairController.planBreakBatch(
                    List.of(
                        new RepairMineController.BreakCandidate<>(
                            world,
                            trueInstant
                        )
                    ),
                    workActionBudget.remainingThisTick(),
                    printActionTick
                );
            if (batch.stopReason()
                == RepairMineController.StopReason.EXPIRED_TARGET) {
                error(
                    "Repair mining expired at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            if (batch.decisions().isEmpty()) {
                if (!trueInstant) return true;
                continue;
            }
            if (!workActionBudget.tryConsume()) {
                return true;
            }

            RepairMineController.BreakDecision<BlockPos> decision =
                batch.decisions().getFirst();
            ((IClientPlayerInteractionManager) mc.interactionManager)
                .setBlockBreakingCooldown(0);
            mc.player.setPitch((float) Rotations.getPitch(world));
            boolean dispatched = BlockUtils.breakBlock(world, true);
            if (!dispatched
                || !buildRepairController.recordBreakDispatched(
                    decision,
                    printActionTick
                )) {
                error(
                    "Could not dispatch the owned active-U repair at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            repairBreakAttempts++;
            reserveToolUseShadow(toolSlot);
            repairSubmissionBlockSequences.put(
                new BlockPos(world),
                serverBlockUpdateSequence
            );
            debugLog(
                "Repair",
                "dispatched break position=" + world.toShortString()
                    + " trueInstant=" + trueInstant
                    + " toolSlot=" + toolSlot
                    + " blockSequence="
                        + serverBlockUpdateSequence
                    + " attemptTotal=" + repairBreakAttempts
            );
            if (!trueInstant) return true;
        }
        return true;
    }

    private boolean acquireBuildRepairSpeedMine(Block repairBlock) {
        return acquireOwnedSpeedMine(
            SpeedMineOwner.BUILD_REPAIR,
            repairBlock
        );
    }

    private boolean acquireTeardownSpeedMine(Block targetBlock) {
        return acquireOwnedSpeedMine(
            SpeedMineOwner.TEARDOWN,
            targetBlock
        );
    }

    @SuppressWarnings("unchecked")
    private boolean acquireOwnedSpeedMine(
        SpeedMineOwner requestedOwner,
        Block targetBlock
    ) {
        Objects.requireNonNull(requestedOwner, "requestedOwner");
        Objects.requireNonNull(targetBlock, "targetBlock");
        if (requestedOwner == SpeedMineOwner.NONE) {
            throw new IllegalArgumentException(
                "Speed Mine requires a concrete owner."
            );
        }
        if (speedMineOwner != SpeedMineOwner.NONE
            && speedMineOwner != requestedOwner) {
            debugLog(
                "SpeedMine",
                "acquire rejected requestedOwner=" + requestedOwner
                    + " currentOwner=" + speedMineOwner
                    + " target="
                        + Registries.BLOCK.getId(targetBlock)
            );
            return false;
        }

        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        if (speedMine == null) {
            debugLog(
                "SpeedMine",
                "module unavailable requestedOwner=" + requestedOwner
                    + " target="
                        + Registries.BLOCK.getId(targetBlock)
            );
            return false;
        }

        Setting<SpeedMine.ListMode> blocksFilter =
            speedMine.settings.get(
                "blocks-filter",
                SpeedMine.ListMode.class
            );
        Setting<List<Block>> blocksSetting =
            (Setting<List<Block>>) speedMine.settings.get("blocks");
        Setting<Boolean> instamineSetting =
            (Setting<Boolean>) speedMine.settings.get("instamine");
        Setting<Boolean> grimBypassSetting =
            (Setting<Boolean>) speedMine.settings.get("grim-bypass");
        if (blocksFilter == null
            || blocksSetting == null
            || instamineSetting == null
            || grimBypassSetting == null) {
            debugLog(
                "SpeedMine",
                "required settings unavailable requestedOwner="
                    + requestedOwner
            );
            return false;
        }

        if (ownedSpeedMineSnapshot == null) {
            ownedSpeedMineSnapshot =
                new SpeedMineSettingsSnapshot(
                    speedMine.isActive(),
                    speedMine.mode.get(),
                    blocksFilter.get(),
                    List.copyOf(blocksSetting.get()),
                    instamineSetting.get(),
                    grimBypassSetting.get()
                );
            speedMineOwner = requestedOwner;
            debugLog(
                "SpeedMine",
                "acquired owner=" + requestedOwner
                    + " target="
                        + Registries.BLOCK.getId(targetBlock)
                    + " snapshot={active="
                        + ownedSpeedMineSnapshot.wasActive()
                        + ",mode=" + ownedSpeedMineSnapshot.mode()
                        + ",filter="
                            + ownedSpeedMineSnapshot.blocksFilter()
                        + ",blocks="
                            + ownedSpeedMineSnapshot.blocks()
                        + ",instamine="
                            + ownedSpeedMineSnapshot.instamine()
                        + ",grimBypass="
                            + ownedSpeedMineSnapshot.grimBypass()
                        + "}"
            );
        }

        boolean alreadyConfigured =
            ownedSpeedMineConfiguredBlock == targetBlock
                && speedMine.isActive()
                && speedMine.mode.get() == SpeedMine.Mode.Damage
                && blocksFilter.get()
                    == SpeedMine.ListMode.Whitelist
                && blocksSetting.get().equals(
                    List.of(targetBlock)
                )
                && instamineSetting.get()
                && !grimBypassSetting.get();
        if (alreadyConfigured) return true;

        if (!speedMine.isActive()) speedMine.toggle();
        speedMine.mode.set(SpeedMine.Mode.Damage);
        blocksFilter.set(SpeedMine.ListMode.Whitelist);
        blocksSetting.set(new ArrayList<>(List.of(targetBlock)));
        instamineSetting.set(true);
        grimBypassSetting.set(false);
        ownedSpeedMineConfiguredBlock = targetBlock;
        debugLog(
            "SpeedMine",
            "configured owner=" + requestedOwner
                + " mode=Damage filter=Whitelist target="
                    + Registries.BLOCK.getId(targetBlock)
                + " instamine=true grimBypass=false"
        );
        return true;
    }

    private void releaseBuildRepairSpeedMine() {
        releaseOwnedSpeedMine(SpeedMineOwner.BUILD_REPAIR);
    }

    private void releaseTeardownSpeedMine() {
        releaseOwnedSpeedMine(SpeedMineOwner.TEARDOWN);
    }

    private void releaseAnyOwnedSpeedMine() {
        if (speedMineOwner == SpeedMineOwner.NONE) return;
        releaseOwnedSpeedMine(speedMineOwner);
    }

    @SuppressWarnings("unchecked")
    private void releaseOwnedSpeedMine(SpeedMineOwner requestedOwner) {
        if (speedMineOwner != requestedOwner
            || ownedSpeedMineSnapshot == null) {
            return;
        }
        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        SpeedMineSettingsSnapshot snapshot =
            ownedSpeedMineSnapshot;
        debugLog(
            "SpeedMine",
            "releasing owner=" + requestedOwner
                + " restore={active=" + snapshot.wasActive()
                + ",mode=" + snapshot.mode()
                + ",filter=" + snapshot.blocksFilter()
                + ",blocks=" + snapshot.blocks()
                + ",instamine=" + snapshot.instamine()
                + ",grimBypass=" + snapshot.grimBypass()
                + "}"
        );
        ownedSpeedMineSnapshot = null;
        speedMineOwner = SpeedMineOwner.NONE;
        ownedSpeedMineConfiguredBlock = null;
        if (speedMine == null) return;

        Setting<SpeedMine.ListMode> blocksFilter =
            speedMine.settings.get(
                "blocks-filter",
                SpeedMine.ListMode.class
            );
        Setting<List<Block>> blocksSetting =
            (Setting<List<Block>>) speedMine.settings.get("blocks");
        Setting<Boolean> instamineSetting =
            (Setting<Boolean>) speedMine.settings.get("instamine");
        Setting<Boolean> grimBypassSetting =
            (Setting<Boolean>) speedMine.settings.get("grim-bypass");

        speedMine.mode.set(snapshot.mode());
        if (blocksFilter != null) {
            blocksFilter.set(snapshot.blocksFilter());
        }
        if (blocksSetting != null) {
            blocksSetting.set(new ArrayList<>(snapshot.blocks()));
        }
        if (instamineSetting != null) {
            instamineSetting.set(snapshot.instamine());
        }
        if (grimBypassSetting != null) {
            grimBypassSetting.set(snapshot.grimBypass());
        }
        if (speedMine.isActive() != snapshot.wasActive()) {
            speedMine.toggle();
        }
    }

    private boolean hasBreakingBuildRepair() {
        return buildRepairController.snapshots().stream()
            .anyMatch(snapshot ->
                snapshot.phase()
                    == RepairMineController.Phase.BREAKING);
    }

    private List<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
        buildPrimaryPlacementTargets(
            List<BlockPos> activeRelativeTargets,
            List<BlockPos> deferredMandatoryTargets
        ) {
        ArrayList<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
            targets = new ArrayList<>();
        if (!activeRelativeTargets.isEmpty()) {
            for (BlockPos relative : activeRelativeTargets) {
                Block expected = buildTargets.get(relative);
                if (expected == null) continue;
                BlockPos world = mapCorner.add(relative);
                if (latestKnownBuildBlock(world) == Blocks.AIR) {
                    targets.add(
                        new PrioritizedPlacementPlanner.Target<>(
                            world,
                            expected.asItem()
                        )
                    );
                }
            }
        }
        if (!deferredMandatoryTargets.isEmpty()) {
            for (BlockPos relative : deferredMandatoryTargets) {
                Block expected = buildTargets.get(relative);
                if (expected == null) continue;
                BlockPos world = mapCorner.add(relative);
                if (latestKnownBuildBlock(world) == Blocks.AIR) {
                    targets.add(
                        new PrioritizedPlacementPlanner.Target<>(
                            world,
                            expected.asItem()
                        )
                    );
                }
            }
        }
        if (!activeRelativeTargets.isEmpty()
            || !deferredMandatoryTargets.isEmpty()) {
            return List.copyOf(targets);
        }

        BlockPos next = getNextBlockPos(false);
        if (next != null) {
            Block expected =
                buildTargets.get(next.subtract(mapCorner));
            if (expected != null) {
                targets.add(
                    new PrioritizedPlacementPlanner.Target<>(
                        next,
                        expected.asItem()
                    )
                );
            }
        }
        return List.copyOf(targets);
    }

    private boolean enforceDeferredMandatoryCoverage(
        CompactCircularNbtPlan.PairRoute activeRoute
    ) {
        if (plannedCircularBuildPair != activeRoute.pairIndex()
            || plannedDeferredMandatoryBuildOrder.isEmpty()) {
            return false;
        }

        for (BlockPos relative :
            plannedDeferredMandatoryBuildOrder) {
            Block expected = buildTargets.get(relative);
            BlockPos world = mapCorner.add(relative);
            Block current = latestKnownBuildBlock(world);
            if (expected != null && current == expected) continue;
            if (current != Blocks.AIR) {
                warning(
                    "A deferred earlier-U target changed to an unexpected "
                        + "block at " + world.toShortString()
                        + "; returning to a north endpoint so that U can "
                        + "receive normal repair ownership."
                );
                buildRecoveryPending = true;
                stopBuildForAction();
                return true;
            }

            BlockReachWindow.Window window =
                plannedDeferredReachWindows.get(relative);
            if (window == null) {
                error(
                    "Deferred earlier-U target lost its reach deadline at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return true;
            }

            boolean inReach = isBuildPlacementInReach(world);
            boolean pending =
                pendingPlacementLedger.isPending(world);
            boolean atReachDeadline =
                CircularBuildMovementPolicy
                    .requiresDeferredPlacementHold(
                        activeCircularRouteSupportIndex,
                        window.lastSupportIndex(),
                        inReach
                    );
            if (atReachDeadline) {
                stopBuildForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .DEFERRED_U_PLACEMENT_CONFIRMATION
                );
                debugLog(
                    "TraversalPlan",
                    "holding pair=" + activeRoute.pairIndex()
                        + " for deferredTarget="
                            + world.toShortString()
                        + " pending=" + pending
                        + " supportCursor="
                            + activeCircularRouteSupportIndex
                        + " lastReachSupport="
                            + window.lastSupportIndex()
                        + " deadlineEntry="
                            + Math.max(
                                0,
                                window.lastSupportIndex() - 1
                            )
                );
                return true;
            }
            if (activeCircularRouteSupportIndex
                    >= window.lastSupportIndex()
                && !inReach) {
                warning(
                    "Circular pair " + activeRoute.pairIndex()
                        + " passed the proven reach window for deferred "
                        + "target " + world.toShortString()
                        + "; returning to a safe endpoint and replanning."
                );
                buildRecoveryPending = true;
                stopBuildForAction();
                return true;
            }
        }
        return false;
    }

    private List<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
        buildOptionalPlacementTargets(
            CompactCircularNbtPlan.PairRoute activeRoute
        ) {
        if (activeRoute == null
            || !nearbyRangePlacement.get()
            || plannedCircularBuildPair != activeRoute.pairIndex()) {
            return List.of();
        }

        ArrayList<PrioritizedPlacementPlanner.Target<BlockPos, Item>>
            targets = new ArrayList<>();
        for (BlockPos relative : plannedOptionalBuildOrder) {
            Block expected = buildTargets.get(relative);
            if (expected == null) continue;
            BlockPos world = mapCorner.add(relative);
            if (latestKnownBuildBlock(world) == Blocks.AIR
                && isBuildPlacementInReach(world)) {
                targets.add(
                    new PrioritizedPlacementPlanner.Target<>(
                        world,
                        expected.asItem()
                    )
                );
            }
        }
        return List.copyOf(targets);
    }

    private boolean dispatchDuePlacementRetries(
        Set<BlockPos> currentPrimaryWorld,
        Map<Item, Integer> primaryReserve,
        boolean optionalTier
    ) {
        ArrayList<PendingPlacementLedger.PendingAttempt<BlockPos, Block>>
            attempts = new ArrayList<>(
                pendingPlacementLedger.pendingAttempts()
            );
        attempts.sort(
            Comparator.comparingInt(attempt ->
                optionalPendingPlacements.contains(attempt.key())
                    ? 1
                    : 0)
        );

        for (PendingPlacementLedger.PendingAttempt<BlockPos, Block> attempt
            : attempts) {
            BlockPos world = attempt.key();
            boolean optional =
                optionalPendingPlacements.contains(world)
                    && !currentPrimaryWorld.contains(world);
            if (!optional) optionalPendingPlacements.remove(world);
            if (optional != optionalTier) continue;
            boolean retryDue =
                printActionTick - attempt.lastAttemptTick()
                    >= pendingPlacementLedger.retryAfterTicks();
            if (!isBuildPlacementInReach(world)) {
                // Movement is intentionally continuous while a fresh packet
                // awaits its normal acknowledgement. Reach matters only once
                // a retry is actually due.
                if (!retryDue) continue;
                if (optional) {
                    debugLog(
                        "Placement",
                        "dropping out-of-reach optional retry position="
                            + world.toShortString()
                    );
                    pendingPlacementLedger.remove(world);
                    optionalPendingPlacements.remove(world);
                    placementSubmissionBlockSequences.remove(world);
                    continue;
                }
                buildRecoveryPending = true;
                stopBuildForAction();
                return false;
            }
            if (!retryDue) continue;
            if (attempt.retriesUsed()
                >= pendingPlacementLedger.maximumRetries()) {
                pendingPlacementLedger.remove(world);
                placementSubmissionBlockSequences.remove(world);
                if (optional) {
                    optionalPendingPlacements.remove(world);
                    continue;
                }
                error(
                    "Required placement retry expired at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            if (latestKnownBuildBlock(world) != Blocks.AIR) continue;
            if (workActionBudget.remainingThisTick() <= 0) break;

            Item material = attempt.expected().asItem();
            if (optional) {
                HashMap<Item, Integer> onHand =
                    usableInventoryCounts();
                if (onHand.getOrDefault(material, 0)
                    <= primaryReserve.getOrDefault(material, 0)) {
                    debugLog(
                        "Placement",
                        "deferring optional retry to protect U reserve "
                            + "position=" + world.toShortString()
                            + " material="
                                + Registries.ITEM.getId(material)
                            + " onHand="
                                + onHand.getOrDefault(material, 0)
                            + " reserved="
                                + primaryReserve.getOrDefault(
                                    material,
                                    0
                                )
                    );
                    continue;
                }
            }

            int slot = ensureBuildHotbarSlot(material, !optional);
            if (slot == HOTBAR_SLOT_PENDING) return true;
            if (slot == HOTBAR_ITEM_UNAVAILABLE) {
                if (!optional) return false;
                continue;
            }
            if (!workActionBudget.tryConsume()) return true;

            BuildPlacementPolicy.Mode placementMode =
                buildPlacementMode(world, !optional);
            boolean submitted = submitBuildPlacement(
                world,
                slot,
                placementMode
            );
            if (!submitted) {
                debugLog(
                    "Placement",
                    "client rejected retry position="
                        + world.toShortString()
                        + " material="
                            + Registries.ITEM.getId(material)
                        + " nextAttempt="
                            + (attempt.totalAttempts() + 1)
                        + " optional=" + optional
                        + " mode=" + placementMode
                );
                continue;
            }
            placementAttempts++;

            Optional<
                PendingPlacementLedger.TimeoutDecision<BlockPos, Block>
            > reserved = pendingPlacementLedger.reserveRetry(
                world,
                printActionTick
            );
            if (reserved.isEmpty()) continue;
            if (reserved.get().action()
                == PendingPlacementLedger.TimeoutAction.EXPIRED) {
                placementSubmissionBlockSequences.remove(world);
                if (optional) {
                    optionalPendingPlacements.remove(world);
                    continue;
                }
                error(
                    "Required placement retry expired at "
                        + world.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return false;
            }
            placementSubmissionBlockSequences.put(
                new BlockPos(world),
                serverBlockUpdateSequence
            );
            debugLog(
                "Placement",
                "submitted retry position=" + world.toShortString()
                    + " material=" + Registries.ITEM.getId(material)
                    + " retriesUsed="
                        + reserved.get().attempt().retriesUsed()
                    + " optional=" + optional
                    + " mode=" + placementMode
                    + " blockSequence="
                        + serverBlockUpdateSequence
                    + " attemptTotal=" + placementAttempts
            );
        }
        return true;
    }

    private boolean isBuildPlacementEligible(
        BlockPos world,
        boolean mandatory
    ) {
        return buildPlacementMode(world, mandatory)
            != BuildPlacementPolicy.Mode.BLOCKED;
    }

    private BuildPlacementPolicy.Mode buildPlacementMode(
        BlockPos world,
        boolean mandatory
    ) {
        if (mapCorner == null) {
            return BuildPlacementPolicy.Mode.BLOCKED;
        }
        Block targetBlock =
            buildTargets.get(world.subtract(mapCorner));
        if (targetBlock == null) {
            return BuildPlacementPolicy.Mode.BLOCKED;
        }
        boolean inReach = isBuildPlacementInReach(world);
        boolean targetReplaceable =
            latestKnownBuildBlock(world) == Blocks.AIR;
        Direction placementSide = BlockUtils.getPlaceSide(world);
        boolean adjacentSupportPending =
            placementSide != null
                && pendingPlacementLedger.isPending(
                    world.offset(placementSide)
                );
        boolean frozenNearbyTarget =
            !mandatory
                && mapCorner != null
                && plannedOptionalBuildTargets.contains(
                    world.subtract(mapCorner)
                );
        return BuildPlacementPolicy.select(
            inReach,
            targetReplaceable,
            BlockUtils.canPlace(world),
            placementSide != null,
            adjacentSupportPending,
            mandatory || frozenNearbyTarget,
            PlacementRotationPolicy.requiresPlayerRotation(
                targetBlock.getDefaultState()
            )
        );
    }

    private boolean submitBuildPlacement(
        BlockPos world,
        int hotbarSlot,
        BuildPlacementPolicy.Mode mode
    ) {
        if (mode == null
            || mode == BuildPlacementPolicy.Mode.BLOCKED) {
            return false;
        }
        if (mapCorner == null) return false;
        Block targetBlock =
            buildTargets.get(world.subtract(mapCorner));
        if (targetBlock == null) return false;
        return submitPlacement(
            world,
            hotbarSlot,
            mode,
            targetBlock
        );
    }

    private boolean submitPlacement(
        BlockPos world,
        int hotbarSlot,
        BuildPlacementPolicy.Mode mode,
        Block targetBlock
    ) {
        Objects.requireNonNull(targetBlock, "targetBlock");
        if (mode == null
            || mode == BuildPlacementPolicy.Mode.BLOCKED) {
            return false;
        }
        boolean shouldRotate =
            rotatePlace.get()
                && PlacementRotationPolicy
                    .requiresPlayerRotation(
                        targetBlock.getDefaultState()
                    );
        if (mode == BuildPlacementPolicy.Mode.ADJACENT) {
            return BlockUtils.place(
                world,
                Hand.MAIN_HAND,
                hotbarSlot,
                shouldRotate,
                50,
                true,
                true,
                false
            );
        }
        if (mc.player == null
            || mc.player.networkHandler == null
            || hotbarSlot < 0
            || hotbarSlot > 8
            || !BlockUtils.canPlace(world)) {
            return false;
        }

        Vec3d hitPosition = world.toCenterPos();
        Runnable submit = () -> {
            if (mc.player == null
                || mc.player.networkHandler == null
                || (mc.player.getInventory().getSelectedSlot()
                    != hotbarSlot
                    && !InvUtils.swap(hotbarSlot, false))) {
                return;
            }
            mc.player.networkHandler.sendPacket(
                new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND,
                    new BlockHitResult(
                        hitPosition,
                        Direction.UP,
                        world,
                        false
                    ),
                    0
                )
            );
            mc.player.networkHandler.sendPacket(
                new HandSwingC2SPacket(Hand.MAIN_HAND)
            );
        };
        if (shouldRotate) {
            Rotations.rotate(
                Rotations.getYaw(hitPosition),
                Rotations.getPitch(hitPosition),
                50,
                submit
            );
        } else {
            if (mc.player.getInventory().getSelectedSlot() != hotbarSlot
                && !InvUtils.swap(hotbarSlot, false)) {
                return false;
            }
            submit.run();
        }
        return true;
    }

    private boolean isBuildPlacementInReach(BlockPos world) {
        double range = effectiveBuildInteractionRange();
        return mc.player.getEyePos().squaredDistanceTo(
            world.toCenterPos()
        ) <= range * range;
    }

    private double effectiveBuildInteractionRange() {
        return Math.min(5.0, interactionRange.get());
    }

    private HotbarPreparation prepareBuildHotbarAtPairEntry(
        CompactCircularNbtPlan.PairRoute route
    ) {
        if (confirmedBuildHotbarSwap.isPending()
            || confirmedMiningHotbarSwap.isPending()) {
            stopBuildForAction();
            return HotbarPreparation.WAITING;
        }
        if (plannedBuildHotbarStackItems.size()
            != BUILD_MATERIAL_HOTBAR_SLOT_COUNT) {
            error(
                "Circular pair " + route.pairIndex()
                    + " reached its entry without an eight-slot "
                    + "material hotbar plan."
            );
            return HotbarPreparation.FAILED;
        }

        if (plannedBuildHotbarPair != route.pairIndex()) {
            PhaseHotbarPlan.BuildLayout baseLayout;
            try {
                baseLayout = PhaseHotbarPlan.buildLayout(
                    availableHotBarSlots,
                    BUILD_MATERIAL_HOTBAR_SLOT_COUNT
                );
            } catch (IllegalArgumentException exception) {
                error(exception.getMessage());
                return HotbarPreparation.FAILED;
            }

            int toolSlot = baseLayout.toolSlot();
            for (int slot : availableHotBarSlots) {
                if (isCompatiblePlannedRepairTool(
                    mc.player.getInventory().getStack(slot)
                )) {
                    toolSlot = slot;
                    break;
                }
            }
            plannedBuildToolHotbarSlot = toolSlot;
            int reservedToolSlot = toolSlot;
            plannedBuildMaterialHotbarSlots.clear();
            availableHotBarSlots.stream()
                .filter(slot -> slot != reservedToolSlot)
                .sorted()
                .limit(BUILD_MATERIAL_HOTBAR_SLOT_COUNT)
                .forEach(plannedBuildMaterialHotbarSlots::add);
            plannedBuildHotbarPair = route.pairIndex();
            plannedBuildHotbarAssignments.clear();
            debugLog(
                "HotbarPlan",
                "initialized build pair=" + route.pairIndex()
                    + " materialSlots="
                        + plannedBuildMaterialHotbarSlots
                    + " toolSlot=" + plannedBuildToolHotbarSlot
                    + " requiredStacks="
                        + plannedBuildHotbarStackItems.stream()
                            .map(item ->
                                Registries.ITEM.getId(item).toString())
                            .toList()
            );
        }

        ItemStack reservedTool =
            mc.player.getInventory().getStack(
                plannedBuildToolHotbarSlot
            );
        if (!plannedRepairToolDemand.isEmpty()
            && !isCompatiblePlannedRepairTool(reservedTool)) {
            Item preferredTool = preferredBuildRepairTool(route);
            int sourceSlot =
                findBestCompatibleBuildRepairMainSlot(
                    preferredTool
                );
            if (sourceSlot < 0) {
                warning(
                    "The frozen circular inventory plan has no "
                        + "compatible repair tool available for its "
                        + "reserved hotbar slot; restocking it before "
                        + "departure."
                );
                return HotbarPreparation.RESTOCK_REQUIRED;
            }
            return beginConfirmedBuildHotbarSwap(
                sourceSlot,
                plannedBuildToolHotbarSlot,
                mc.player.getInventory().getStack(sourceSlot)
                    .getItem(),
                "build-tool preload",
                true
            )
                ? HotbarPreparation.WAITING
                : HotbarPreparation.FAILED;
        }
        if (plannedRepairToolDemand.isEmpty()
            && !reservedTool.isEmpty()) {
            int emptySource = findBestMainInventorySlot(
                Items.AIR,
                -1
            );
            if (emptySource < 0) {
                warning(
                    "The reserved build-tool hotbar slot cannot be "
                        + "cleared because no managed main-inventory "
                        + "slot is empty; rebuilding inventory before "
                        + "departure."
                );
                return HotbarPreparation.RESTOCK_REQUIRED;
            }
            return beginConfirmedBuildHotbarSwap(
                emptySource,
                plannedBuildToolHotbarSlot,
                Items.AIR,
                "build-tool-slot clear",
                true
            )
                ? HotbarPreparation.WAITING
                : HotbarPreparation.FAILED;
        }

        if (plannedBuildHotbarAssignments.isEmpty()) {
            LinkedHashMap<Integer, Item> current =
                new LinkedHashMap<>();
            for (int slot : plannedBuildMaterialHotbarSlots) {
                ItemStack stack =
                    mc.player.getInventory().getStack(slot);
                current.put(
                    slot,
                    stack.isEmpty()
                        ? Items.AIR
                        : stack.getItem()
                );
            }
            plannedBuildHotbarAssignments.putAll(
                PhaseHotbarPlan.assignRequiredItems(
                    plannedBuildMaterialHotbarSlots,
                    plannedBuildHotbarStackItems,
                    current
                )
            );
        }

        for (Map.Entry<Integer, Item> assignment
            : plannedBuildHotbarAssignments.entrySet()) {
            int targetSlot = assignment.getKey();
            Item expected = assignment.getValue();
            ItemStack target =
                mc.player.getInventory().getStack(targetSlot);
            Item current = target.isEmpty()
                ? Items.AIR
                : target.getItem();
            if (current.equals(expected)) continue;

            int sourceSlot =
                findBestMainInventorySlot(expected, -1);
            if (sourceSlot < 0) {
                warning(
                    "The frozen hotbar plan cannot find "
                        + expected.getName().getString()
                        + " in a managed main-inventory slot; "
                        + "restocking the complete frozen plan."
                );
                return HotbarPreparation.RESTOCK_REQUIRED;
            }
            return beginConfirmedBuildHotbarSwap(
                sourceSlot,
                targetSlot,
                expected,
                "build-material preload",
                true
            )
                ? HotbarPreparation.WAITING
                : HotbarPreparation.FAILED;
        }

        debugLog(
            "HotbarPlan",
            "build hotbar ready pair=" + route.pairIndex()
                + " materialSlots="
                    + plannedBuildMaterialHotbarSlots
                + " toolSlot=" + plannedBuildToolHotbarSlot
        );
        return HotbarPreparation.READY;
    }

    private Item preferredBuildRepairTool(
        CompactCircularNbtPlan.PairRoute route
    ) {
        for (BlockPos relative : circularPairTargets(route)) {
            Block expected = buildTargets.get(relative);
            BlockState current =
                MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                );
            if (expected == null
                || current.isAir()
                || current.getBlock() == expected) {
                continue;
            }
            ItemStack tool = getBestRegisteredTool(current);
            if (tool != null
                && plannedRepairToolDemand.containsKey(
                    tool.getItem()
                )) {
                return tool.getItem();
            }
        }
        return plannedRepairToolDemand.keySet().stream()
            .sorted(Comparator
                .comparingInt((Item item) ->
                    new ItemStack(item).isIn(ItemTags.PICKAXES)
                        ? 0
                        : new ItemStack(item).isIn(ItemTags.AXES)
                            ? 1
                            : 2)
                .thenComparing(item ->
                    Registries.ITEM.getId(item).toString()))
            .findFirst()
            .orElse(Items.AIR);
    }

    private int findBestCompatibleBuildRepairMainSlot(
        Item preferredItem
    ) {
        int bestSlot = -1;
        int bestPreference = Integer.MAX_VALUE;
        int bestDurability = -1;
        for (int slot : availableSlots) {
            if (slot < 9 || slot >= 36) continue;
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!isCompatiblePlannedRepairTool(stack)) continue;
            int preference =
                stack.getItem().equals(preferredItem) ? 0 : 1;
            int durability = remainingToolDurability(stack);
            if (preference < bestPreference
                || (preference == bestPreference
                    && (durability > bestDurability
                        || (durability == bestDurability
                            && (bestSlot < 0
                                || slot < bestSlot))))) {
                bestSlot = slot;
                bestPreference = preference;
                bestDurability = durability;
            }
        }
        return bestSlot;
    }

    private boolean beginConfirmedBuildHotbarSwap(
        int sourceSlot,
        int targetSlot,
        Item expected,
        String owner,
        boolean mandatory
    ) {
        confirmedBuildHotbarSwap.begin(
            targetSlot,
            expected,
            serverHotbarSwapAckSequences[targetSlot],
            printActionTick
        );
        pendingBuildHotbarSwapMandatory = mandatory;
        if (!dispatchConfirmedInventorySwap(
            sourceSlot,
            targetSlot,
            owner,
            false
        )) {
            failBuildHotbarSwap(expected);
            return false;
        }
        if (mandatory) {
            stopBuildForAction(
                CircularBuildMovementPolicy.HoldReason
                    .HOTBAR_SWAP_CONFIRMATION
            );
        }
        return true;
    }

    private int ensureBuildRepairTool(
        BlockPos target,
        BlockState targetState
    ) {
        ItemStack bestTool = getBestRegisteredTool(targetState);
        if (bestTool == null) {
            error(
                "No registered tool can repair-mine "
                    + targetState.getBlock().getName().getString() + "."
            );
            stopBuildForAction();
            toggle();
            return HOTBAR_ITEM_UNAVAILABLE;
        }

        if (plannedBuildToolHotbarSlot < 0
            || !availableHotBarSlots.contains(
                plannedBuildToolHotbarSlot
            )) {
            error(
                "The active U has no reserved repair-tool hotbar slot."
            );
            stopBuildForAction();
            toggle();
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        ItemStack reserved =
            mc.player.getInventory().getStack(
                plannedBuildToolHotbarSlot
            );
        if (isCompatibleMiningTool(
                reserved,
                bestTool,
                targetState
            )
            && remainingToolDurability(reserved)
                >= minimumReusableToolDurability(reserved)) {
            if (!InvUtils.swap(
                plannedBuildToolHotbarSlot,
                false
            )) {
                error(
                    "Could not select the reserved repair tool for "
                        + target.toShortString() + "."
                );
                stopBuildForAction();
                toggle();
                return HOTBAR_ITEM_UNAVAILABLE;
            }
            return plannedBuildToolHotbarSlot;
        }

        int bestInventorySlot = findBestMiningInventorySlot(
            bestTool,
            targetState,
            -1,
            true,
            true
        );
        if (confirmedMiningHotbarSwap.isPending()) {
            return HOTBAR_SLOT_PENDING;
        }
        if (bestInventorySlot < 0) {
            error(
                "Required active-U repair tool is missing: "
                    + bestTool.getName().getString() + "."
            );
            stopBuildForAction();
            toggle();
            return HOTBAR_ITEM_UNAVAILABLE;
        }

        int targetHotbarSlot = plannedBuildToolHotbarSlot;
        MiningToolIdentity expectedIdentity = miningToolIdentity(
            mc.player.getInventory().getStack(bestInventorySlot)
        );
        boolean requiresStaging =
            repairToolShadows.containsKey(targetHotbarSlot)
                && inventoryStackIdentity(
                    mc.player.getInventory().getStack(
                        bestInventorySlot
                    )
                ).equals(
                    inventoryStackIdentity(
                        mc.player.getInventory().getStack(
                            targetHotbarSlot
                        )
                    )
                );
        if (requiresStaging) {
            int stagingSourceSlot =
                findRepairToolStagingSlot(
                    target,
                    bestInventorySlot,
                    targetHotbarSlot
                );
            if (stagingSourceSlot < 0) {
                error(
                    "Cannot stage the indistinguishable active-U repair "
                        + "tool because its reserved replacement material "
                        + "is not available in a distinct main slot."
                );
                stopBuildForAction();
                toggle();
                return HOTBAR_ITEM_UNAVAILABLE;
            }
            MiningToolIdentity stagingIdentity =
                miningToolIdentity(
                    mc.player.getInventory().getStack(
                        stagingSourceSlot
                    )
                );
            repairToolSwapStaging =
                new RepairToolSwapStaging(
                    bestInventorySlot,
                    stagingSourceSlot,
                    targetHotbarSlot,
                    expectedIdentity,
                    false
                );
            confirmedMiningHotbarSwap.begin(
                targetHotbarSlot,
                stagingIdentity,
                serverHotbarSwapAckSequences[
                    targetHotbarSlot
                ],
                printActionTick
            );
            miningHotbarSwapContext =
                MiningHotbarSwapContext.BUILD_REPAIR;
            if (!dispatchConfirmedInventorySwap(
                stagingSourceSlot,
                targetHotbarSlot,
                "repair-tool staging",
                false
            )) {
                failMiningHotbarSwap(expectedIdentity);
                return HOTBAR_ITEM_UNAVAILABLE;
            }
            stopBuildForAction();
            return HOTBAR_SLOT_PENDING;
        }

        confirmedMiningHotbarSwap.begin(
            targetHotbarSlot,
            expectedIdentity,
            serverHotbarSwapAckSequences[targetHotbarSlot],
            printActionTick
        );
        miningHotbarSwapContext =
            MiningHotbarSwapContext.BUILD_REPAIR;
        if (!dispatchConfirmedInventorySwap(
            bestInventorySlot,
            targetHotbarSlot,
            "repair-tool",
            false
        )) {
            failMiningHotbarSwap(expectedIdentity);
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        stopBuildForAction();
        return HOTBAR_SLOT_PENDING;
    }

    private int findRepairToolStagingSlot(
        BlockPos repairTarget,
        int desiredSourceSlot,
        int targetHotbarSlot
    ) {
        if (mapCorner == null
            || plannedCircularBuildPair < 0) {
            return -1;
        }
        Block replacement =
            buildTargets.get(repairTarget.subtract(mapCorner));
        if (replacement == null) return -1;

        InventoryStackIdentity targetIdentity =
            inventoryStackIdentity(
                mc.player.getInventory().getStack(
                    targetHotbarSlot
                )
            );
        Set<Integer> keptMaterialSlots = new HashSet<>(
            currentCircularMaterialAllocation().keptSlots()
        );
        for (int slot : keptMaterialSlots) {
            if (slot < 9
                || slot >= 36
                || slot == desiredSourceSlot) {
                continue;
            }
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                && stack.getItem() == replacement.asItem()
                && !inventoryStackIdentity(stack).equals(
                    targetIdentity
                )) {
                return slot;
            }
        }
        return -1;
    }

    private int ensureBuildHotbarSlot(
        Item item,
        boolean mandatory
    ) {
        int hotbarSlot = findBestHotbarSlot(item);
        if (hotbarSlot >= 0) {
            if (!InvUtils.swap(hotbarSlot, false)) {
                if (mandatory) {
                    error(
                        "Could not select required hotbar item "
                            + item.getName().getString() + "."
                    );
                    stopBuildForAction();
                    toggle();
                }
                return HOTBAR_ITEM_UNAVAILABLE;
            }
            return hotbarSlot;
        }
        if (!mandatory
            && rejectedOptionalSwapMaterials.contains(item)) {
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        if (confirmedBuildHotbarSwap.isPending()) {
            return HOTBAR_SLOT_PENDING;
        }

        int sourceSlot = findBestMainInventorySlot(item, -1);
        if (sourceSlot < 0) {
            if (mandatory) {
                error(
                    "Required active-U inventory item is missing: "
                        + item.getName().getString() + "."
                );
                stopBuildForAction();
                toggle();
            }
            return HOTBAR_ITEM_UNAVAILABLE;
        }

        int targetSlot = selectBuildHotbarDestination();
        if (targetSlot < 0) {
            if (mandatory) {
                error("No reserved hotbar destination is available.");
                stopBuildForAction();
                toggle();
            }
            return HOTBAR_ITEM_UNAVAILABLE;
        }

        if (!beginConfirmedBuildHotbarSwap(
            sourceSlot,
            targetSlot,
            item,
            "build-material",
            mandatory
        )) {
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        return HOTBAR_SLOT_PENDING;
    }

    private int findBestHotbarSlot(Item item) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot : activeBuildMaterialHotbarSlots()) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !stack.getItem().equals(item)) {
                continue;
            }
            int score = stack.getMaxDamage() > 0
                ? stack.getMaxDamage() - stack.getDamage()
                : stack.getCount();
            if (stack.getMaxDamage() > 0 && score <= 1) continue;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int findBestMainInventorySlot(
        Item item,
        int excludedSlot
    ) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot : availableSlots) {
            if (slot == excludedSlot
                || availableHotBarSlots.contains(slot)) {
                continue;
            }
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (item == Items.AIR) {
                if (stack.isEmpty()) return slot;
                continue;
            }
            if (stack.isEmpty() || !stack.getItem().equals(item)) {
                continue;
            }
            int score = stack.getMaxDamage() > 0
                ? stack.getMaxDamage() - stack.getDamage()
                : stack.getCount();
            if (stack.getMaxDamage() > 0 && score <= 1) continue;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int selectBuildHotbarDestination() {
        return selectBuildHotbarDestination(repairToolShadows.keySet());
    }

    private int selectBuildHotbarDestination(
        Set<Integer> excludedSlots
    ) {
        List<Integer> materialSlots =
            activeBuildMaterialHotbarSlots();
        if (materialSlots.isEmpty()) return -1;
        for (int slot : materialSlots) {
            if (excludedSlots.contains(slot)) continue;
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }

        List<Item> priority = currentBuildItemPriority();
        int selected = -1;
        int farthestNextUse = Integer.MIN_VALUE;
        for (int slot : materialSlots) {
            if (excludedSlots.contains(slot)) continue;
            Item item =
                mc.player.getInventory().getStack(slot).getItem();
            int nextUse = priority.indexOf(item);
            if (nextUse < 0) return slot;
            if (nextUse > farthestNextUse) {
                farthestNextUse = nextUse;
                selected = slot;
            }
        }
        return selected;
    }

    private List<Integer> activeBuildMaterialHotbarSlots() {
        if (plannedBuildMaterialHotbarSlots.size()
            == BUILD_MATERIAL_HOTBAR_SLOT_COUNT) {
            return plannedBuildMaterialHotbarSlots;
        }
        return List.of();
    }

    private List<Item> currentBuildItemPriority() {
        ArrayList<Item> priority = new ArrayList<>();
        CompactCircularNbtPlan.PairRoute route =
            activeCircularBuildRoute();
        if (route != null) {
            for (BlockPos relative : circularPairTargets(route)) {
                Block expected = buildTargets.get(relative);
                if (expected != null) priority.add(expected.asItem());
            }
            for (BlockPos relative :
                plannedDeferredMandatoryBuildOrder) {
                Block expected = buildTargets.get(relative);
                if (expected != null) priority.add(expected.asItem());
            }
            for (BlockPos relative : plannedOptionalBuildOrder) {
                Block expected = buildTargets.get(relative);
                if (expected != null) priority.add(expected.asItem());
            }
            priority.addAll(plannedRepairToolDemand.keySet());
            return priority;
        }
        for (BlockPos relative : orderedBuildTargets) {
            Block expected = buildTargets.get(relative);
            if (expected != null) priority.add(expected.asItem());
        }
        return priority;
    }

    private boolean isCurrentActivePairWorldTarget(BlockPos world) {
        CompactCircularNbtPlan.PairRoute route =
            activeCircularBuildRoute();
        return route != null
            && circularPairTargets(route).contains(
                world.subtract(mapCorner)
            );
    }

    private boolean isCurrentMandatoryBuildWorldTarget(
        BlockPos world
    ) {
        if (isCurrentActivePairWorldTarget(world)) return true;
        return mapCorner != null
            && plannedCircularBuildPair == activeCircularBuildPair
            && plannedDeferredMandatoryBuildTargets.contains(
                world.subtract(mapCorner)
            );
    }

    private void stopBuildForAction() {
        if (buildMovementHoldReasonThisTick
            == CircularBuildMovementPolicy.HoldReason.NONE) {
            buildMovementHoldReasonThisTick =
                CircularBuildMovementPolicy.HoldReason.OTHER_BUILD_ACTION;
        }
        stopBuildMovement();
    }

    private void stopBuildForAction(
        CircularBuildMovementPolicy.HoldReason reason
    ) {
        if (reason == null
            || reason == CircularBuildMovementPolicy.HoldReason.NONE) {
            throw new IllegalArgumentException(
                "A blocking build action requires a hold reason."
            );
        }
        buildMovementHoldReasonThisTick = reason;
        stopBuildMovement();
    }

    private void stopBuildMovement() {
        buildMovementBlockedThisTick = true;
        stopMovement();
        if (mc.player != null && activeCircularBuildPair >= 0) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(0, velocity.y, 0);
        }
    }

    private void debugActiveOrderedUMovementTransition(
        String stateKey,
        String message
    ) {
        if (!debugPrints.get()
            || Objects.equals(
                lastActiveBuildMovementDebugState,
                stateKey
            )) {
            return;
        }
        boolean hadPreviousState =
            lastActiveBuildMovementDebugState != null;
        lastActiveBuildMovementDebugState = stateKey;
        if (!hadPreviousState && stateKey.equals("moving")) return;
        debugLog("Movement", message);
    }

    private void beginBuildRecovery(boolean inventoryLost) {
        if (!buildingActive
            || activeRecoveryOwner()
                != RecoveryOwnerPolicy.Owner.BUILD) {
            return;
        }
        freezeForRecoveryClassification();
        releaseTransientBuildOwners();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        if (inventoryLost && repairToolShadows != null) {
            repairToolShadows.clear();
        }
        buildRecoveryPending = true;
        buildRecoveryNeedsInventory |= inventoryLost;
        stopMovement();
    }

    private boolean beginInventoryLogisticsRecoveryForCurrentPhase(
        InventoryRecoveryAuthority authority
    ) {
        InventoryLogisticsRecovery recovery =
            switch (mapCyclePhase) {
                case MAP_HANDOFF -> authority
                        == InventoryRecoveryAuthority.REGISTERED_HANDLER_PROBE
                    ? InventoryLogisticsRecovery.MAP_HANDOFF_PROBE
                    : InventoryLogisticsRecovery.MAP_HANDOFF;
                case POST_MINING ->
                    InventoryLogisticsRecovery.POST_MINING_USED_TOOLS;
                default -> InventoryLogisticsRecovery.NONE;
            };
        if (recovery == InventoryLogisticsRecovery.NONE) {
            return false;
        }
        if (inventoryLogisticsRecovery != InventoryLogisticsRecovery.NONE) {
            return true;
        }

        BlockPos interruptedUsedToolChest = null;
        if (recovery
            == InventoryLogisticsRecovery.POST_MINING_USED_TOOLS) {
            boolean transactionInFlight =
                state == State.AwaitUsedToolChestResponse
                    || pendingUsedToolDeposit != null
                    || toBeHandledInvPacket != null;
            UsedToolDepositRecoveryPolicy.DestinationResolution<BlockPos>
                resolution =
                    UsedToolDepositRecoveryPolicy.resolveDestination(
                        usedToolDepositPlan.keySet(),
                        queuedUsedToolDepositChests(),
                        Optional.ofNullable(
                            activeUsedToolDepositChest != null
                                ? activeUsedToolDepositChest
                                : lastInteractedChest
                        ),
                        transactionInFlight
                    );
            if (resolution.status()
                == UsedToolDepositRecoveryPolicy
                    .ResolutionStatus.AMBIGUOUS) {
                error(
                    "Post-mining recovery could not uniquely identify "
                        + "the interrupted used-tool chest."
                );
                stopMovement();
                if (isActive()) toggle();
                return true;
            }
            interruptedUsedToolChest =
                resolution.destination().orElse(null);
        }

        inventoryLogisticsRecovery = recovery;
        recoveringUsedToolChest = interruptedUsedToolChest == null
            ? null
            : new BlockPos(interruptedUsedToolChest);
        inventoryLogisticsRecoveryAfterSnapshot =
            recovery == InventoryLogisticsRecovery.MAP_HANDOFF
                && authority
                    == InventoryRecoveryAuthority.PLAYER_SNAPSHOT
                ? serverPlayerInventorySnapshotSequence
                : -1L;
        inventoryLogisticsRecoveryStartedTick = clientActionTick;

        releaseAnyOwnedSpeedMine();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        activeUsedToolDepositChest = null;
        currentUsedToolDepositItems.clear();
        currentUsedToolDepositSlots.clear();
        closeNextInvPacket = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        if (confirmedBuildHotbarSwap != null) {
            confirmedBuildHotbarSwap.clear();
        }
        if (confirmedMiningHotbarSwap != null) {
            confirmedMiningHotbarSwap.clear();
        }
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        stopMovement();
        return true;
    }

    private Set<BlockPos> queuedUsedToolDepositChests() {
        HashSet<BlockPos> queued = new HashSet<>();
        for (Pair<Vec3d, Pair<String, BlockPos>> checkpoint
            : checkpoints) {
            Pair<String, BlockPos> action = checkpoint.getRight();
            if ("usedToolChest".equals(action.getLeft())
                && action.getRight() != null) {
                queued.add(action.getRight());
            }
        }
        return Set.copyOf(queued);
    }

    private Vec3d usedToolDepositStandingPosition(BlockPos chest) {
        if (usedToolChest != null
            && usedToolChest.getLeft().equals(chest)) {
            return usedToolChest.getRight();
        }
        for (Pair<BlockPos, Vec3d> destination
            : usedToolChests.values()) {
            if (destination.getLeft().equals(chest)) {
                return destination.getRight();
            }
        }
        return null;
    }

    private void resumeInventoryLogisticsRecovery() {
        if (inventoryLogisticsRecovery
                == InventoryLogisticsRecovery.MAP_HANDOFF
            && inventoryLogisticsRecoveryAfterSnapshot >= 0
            && serverPlayerInventorySnapshotSequence
                <= inventoryLogisticsRecoveryAfterSnapshot) {
            if (clientActionTick
                    - inventoryLogisticsRecoveryStartedTick
                >= INVENTORY_RECOVERY_MAX_WAIT_TICKS) {
                inventoryLogisticsRecovery =
                    InventoryLogisticsRecovery.NONE;
                failMapHandoff(
                    "No fresh authoritative player-inventory snapshot "
                        + "arrived after reconnect or respawn."
                );
            } else {
                stopMovement();
            }
            return;
        }

        InventoryLogisticsRecovery recovery =
            inventoryLogisticsRecovery;
        BlockPos interruptedChest = recoveringUsedToolChest;
        inventoryLogisticsRecovery =
            InventoryLogisticsRecovery.NONE;
        recoveringUsedToolChest = null;
        inventoryLogisticsRecoveryAfterSnapshot = -1L;
        inventoryLogisticsRecoveryStartedTick = -1L;

        if (recovery
            == InventoryLogisticsRecovery.MAP_HANDOFF_PROBE) {
            scheduleMapHandoffRecoveryProbe();
            return;
        }
        if (recovery == InventoryLogisticsRecovery.MAP_HANDOFF) {
            resumeMapHandoffFromCheckpoint();
            return;
        }
        if (recovery
            != InventoryLogisticsRecovery.POST_MINING_USED_TOOLS) {
            return;
        }

        if (interruptedChest != null) {
            Set<Item> plannedItems =
                usedToolDepositPlan.get(interruptedChest);
            Vec3d standingPosition =
                usedToolDepositStandingPosition(interruptedChest);
            if (plannedItems == null
                || plannedItems.isEmpty()
                || standingPosition == null) {
                failInventoryTransaction(
                    "Post-mining recovery lost the registered used-tool "
                        + "destination or its non-empty item plan."
                );
                return;
            }
            checkpoints.add(
                0,
                new Pair<>(
                    standingPosition,
                    new Pair<>(
                        "usedToolChest",
                        interruptedChest
                    )
                )
            );
        }

        if (checkpoints.isEmpty()) {
            if (!usedToolDepositPlan.isEmpty()) {
                failInventoryTransaction(
                    "Post-mining recovery found deposit work without "
                        + "a corresponding used-tool checkpoint."
                );
                return;
            }
            state = State.AwaitNBTFile;
            completeSlavePostMiningCleanup();
            return;
        }
        state = State.Walking;
        stopMovement();
    }

    private void clearInventoryLogisticsRecoveryMarker() {
        inventoryLogisticsRecovery =
            InventoryLogisticsRecovery.NONE;
        recoveringUsedToolChest = null;
        inventoryLogisticsRecoveryAfterSnapshot = -1L;
        inventoryLogisticsRecoveryStartedTick = -1L;
    }

    private void beginMiningRecovery(boolean inventoryLost) {
        if (activeRecoveryOwner()
            == RecoveryOwnerPolicy.Owner.MINING) {
            buildingActive = false;
            buildRecoveryPending = false;
            buildRecoveryNeedsInventory = false;
            buildRecoveryRestockAfterEgress = false;
            activeCircularBuildPair = -1;
            activeCircularConnectorIndex = -1;
            circularBuildRecoveryDirection = 0;
            circularBuildPhase = CircularBuildPhase.NONE;
        }
        freezeForRecoveryClassification();
        resetTeardownMiningActionState();
        releaseBuildRepairSpeedMine();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        confirmedMiningHotbarSwap.clear();
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        if (inventoryLost && repairToolShadows != null) {
            repairToolShadows.clear();
        }
        miningRecoveryPending = true;
        miningRecoveryNeedsTools |= inventoryLost;
        stopMovement();
    }

    private void releaseTransientBuildOwners() {
        releaseBuildRepairSpeedMine();
        if (buildRepairController != null) buildRepairController.reset();
        if (confirmedBuildHotbarSwap != null) {
            confirmedBuildHotbarSwap.clear();
        }
        if (confirmedMiningHotbarSwap != null) {
            confirmedMiningHotbarSwap.clear();
        }
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        if (pendingPlacementLedger != null) {
            pendingPlacementLedger.reset();
        }
        if (optionalPendingPlacements != null) {
            optionalPendingPlacements.clear();
        }
        if (placementSubmissionBlockSequences != null) {
            placementSubmissionBlockSequences.clear();
        }
        if (repairSubmissionBlockSequences != null) {
            repairSubmissionBlockSequences.clear();
        }
    }

    private void clearPendingInventorySwapState() {
        pendingInventoryMetadataSwap = null;
        repairToolSwapStaging = null;
        pendingBuildHotbarSwapMandatory = false;
    }

    private BlockPos getNextBlockPos(boolean mining) {
        int relativeX = mc.player.getBlockX() - mapCorner.getX();
        if (!mining) {
            if (activeCircularBuildPair >= 0
                && activeCircularBuildPair < compactPlan.pairRoutes().size()) {
                return getNextCircularBuildBlock();
            }
            return ContinuousBuildTargetSelector.firstMissing(
                orderedBuildTargets,
                0,
                this::isInWorkingInterval,
                relative -> MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                ).isAir()
            ).map(mapCorner::add).orElse(null);
        }

        if (relativeX < 0 || relativeX >= map.length) return null;
        for (int x = relativeX; x <= relativeX; x++) {
            for (int z = 0; z < 128; z++) {
                int adjustedZ = 127 - z;
                BlockPos blockPos = mapCorner.add(x, map[x][adjustedZ].getRight(), adjustedZ);
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (!blockState.isAir()) return blockPos;
            }
        }
        return null;
    }

    private BlockPos getNextCircularBuildBlock() {
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(activeCircularBuildPair);
        List<BlockPos> pairTargets = circularPairTargets(route);
        int endIndexExclusive;
        if (circularBuildPhase == CircularBuildPhase.OUTBOUND) {
            // The first connector step may be placed while approaching the
            // outbound endpoint, but placement must not rotate around a later
            // connector turn.
            endIndexExclusive = Math.min(
                CompactCircularNbtPlan.VISIBLE_ROWS + 1,
                pairTargets.size()
            );
        } else if (circularBuildPhase == CircularBuildPhase.CONNECTOR) {
            // Keep normal placement on the same target as the hidden walking
            // cursor. This prevents rotate-place from steering toward a later
            // helix turn or the return leg.
            endIndexExclusive = Math.min(
                CompactCircularNbtPlan.VISIBLE_ROWS
                    + activeCircularConnectorIndex
                    + 1,
                pairTargets.size()
            );
        } else if (circularBuildPhase == CircularBuildPhase.RETURN) {
            endIndexExclusive = pairTargets.size();
        } else {
            return null;
        }

        return ContinuousBuildTargetSelector.firstMissing(
            pairTargets,
            0,
            endIndexExclusive,
            ignored -> true,
            relative -> MapAreaCache.getCachedBlockState(
                mapCorner.add(relative)
            ).isAir()
        ).map(mapCorner::add).orElse(null);
    }

    // Path and Building Management

    private void calculateBuildingPath(boolean sprintFirst) {
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        activeCircularPlacementCursor = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        releaseBuildRepairSpeedMine();
        buildRepairController.reset();
        activeCircularConnectorSteps = List.of();
        activeCircularRecoveryTargets = List.of();
        checkpoints.clear();
        refreshCircularTraversalOptimization();
        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }

            if (circularPairModes.getOrDefault(route.pairIndex(), false)) {
                if (!optimizedCircularTraversalPairs.contains(
                    route.pairIndex()
                )) {
                    continue;
                }

                CircularBuildCheckpointPlan.Plan<BlockPos> traversal =
                    circularBuildCheckpointPlan(route);
                List<BlockPos> structural = traversal.structuralCheckpoints();
                checkpoints.add(new Pair<>(
                    walkingPosition(
                        circularBuildAlignmentSupport(route)
                    ),
                    new Pair<>("preparePair", new BlockPos(route.pairIndex(), 0, 0))
                ));

                checkpoints.add(new Pair<>(
                    walkingPosition(structural.get(1)),
                    new Pair<>("uBuildOutboundEnd", null)
                ));
                checkpoints.add(new Pair<>(
                    walkingPosition(structural.get(2)),
                    new Pair<>("uBuildConnectorEnd", null)
                ));
                checkpoints.add(new Pair<>(
                    walkingPosition(structural.get(3)),
                    new Pair<>("finishPair", null)
                ));
            } else {
                addIndependentColumnPath(route.outboundX());
                addIndependentColumnPath(route.returnX());
            }
        }

        if (!checkpoints.isEmpty()) {
            Pair<Vec3d, Pair<String, BlockPos>> last = checkpoints.getLast();
            checkpoints.add(new Pair<>(last.getLeft(), new Pair<>("lineEnd", null)));
        }
        if (checkpoints.size() > 0
            && sprintFirst
            && !checkpoints.getFirst().getRight().getLeft().equals("preparePair")) {
            Pair<Vec3d, Pair<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair<>(firstPoint.getLeft(), new Pair<>("sprint", null)));
        }
    }

    private void refreshCircularTraversalOptimization() {
        optimizedCircularTraversalPairs.clear();
        optimizedDeferredBuildTargets.clear();
        optimizedDeferredRouteAssignments.clear();
        if (!circularTraversalForCurrentMap
            || compactPlan == null
            || mapCorner == null
            || mc.player == null) {
            return;
        }

        ArrayList<
            ForwardCircularTraversalPlan.Route<BlockPos>
        > routes = new ArrayList<>();
        for (CompactCircularNbtPlan.PairRoute route
            : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()
                || !circularPairModes.getOrDefault(
                    route.pairIndex(),
                    false
                )) {
                continue;
            }

            ArrayList<BlockPos> missing = new ArrayList<>();
            boolean containsWrong = false;
            for (BlockPos relative : circularPairTargets(route)) {
                Block expected = buildTargets.get(relative);
                Block current = latestKnownBuildBlock(
                    mapCorner.add(relative)
                );
                if (expected != null && current == expected) continue;
                if (current == Blocks.AIR) {
                    missing.add(relative);
                } else {
                    containsWrong = true;
                }
            }
            for (BlockPos relative : circularPairTargets(route)) {
                BlockPos world = mapCorner.add(relative);
                if (!MapAreaCache.getCachedBlockState(
                        world.up()
                    ).isAir()
                    || !MapAreaCache.getCachedBlockState(
                        world.up(2)
                    ).isAir()) {
                    containsWrong = true;
                    break;
                }
            }
            routes.add(
                new ForwardCircularTraversalPlan.Route<>(
                    route.pairIndex(),
                    missing,
                    containsWrong
                )
            );
        }

        ForwardCircularTraversalPlan.Plan<BlockPos> plan =
            ForwardCircularTraversalPlan.create(
                routes,
                (relative, destinationPair) ->
                    deferredReachWindow(
                        relative,
                        compactPlan.pairRoutes().get(destinationPair)
                    ).isPresent(),
                (destinationPair, deferredTargets) ->
                    createCircularInventoryPlan(
                        compactPlan.pairRoutes().get(destinationPair),
                        false,
                        deferredTargets,
                        false
                    ).plan().primaryFits()
            );
        optimizedCircularTraversalPairs.addAll(
            plan.traversalRouteIndices()
        );
        plan.deferredTargetsByTraversal().forEach(
            (pair, targets) ->
                optimizedDeferredBuildTargets.put(
                    pair,
                    List.copyOf(targets)
                )
        );
        optimizedDeferredRouteAssignments.putAll(
            plan.deferredRouteAssignments()
        );

        int deferredTargetCount =
            optimizedDeferredBuildTargets.values().stream()
                .mapToInt(List::size)
                .sum();
        if (!optimizedDeferredRouteAssignments.isEmpty()) {
            info(
                "Traversal optimization: walking "
                    + optimizedCircularTraversalPairs.size()
                    + " circular U routes, skipping "
                    + plan.completedRouteIndices().size()
                    + " completed routes, deferring "
                    + optimizedDeferredRouteAssignments.size()
                    + " sparse routes (" + deferredTargetCount
                    + " mandatory blocks) to later reachable routes."
            );
        }
        debugLog(
            "TraversalPlan",
            "selected="
                + plan.traversalRouteIndices()
                + " completed="
                + plan.completedRouteIndices()
                + " deferredAssignments="
                + plan.deferredRouteAssignments()
        );
    }

    private Optional<BlockReachWindow.Window> deferredReachWindow(
        BlockPos relativeTarget,
        CompactCircularNbtPlan.PairRoute traversalRoute
    ) {
        BlockPos worldTarget = mapCorner.add(relativeTarget);
        ArrayList<BlockReachWindow.Cell> supports = new ArrayList<>();
        for (BlockPos support :
            activeCircularBuildSupportPath(traversalRoute)) {
            supports.add(
                new BlockReachWindow.Cell(
                    support.getX(),
                    support.getY(),
                    support.getZ()
                )
            );
        }
        double standingEyeHeight =
            mc.player.getEyePos().y - mc.player.getY();
        return BlockReachWindow.find(
            new BlockReachWindow.Cell(
                worldTarget.getX(),
                worldTarget.getY(),
                worldTarget.getZ()
            ),
            supports,
            standingEyeHeight,
            effectiveBuildInteractionRange()
        );
    }

    private boolean hasSufficientPairMaterials(int pairIndex) {
        if (pairIndex < 0 || pairIndex >= compactPlan.pairRoutes().size()) return false;
        if (plannedCircularBuildPair != pairIndex) {
            error(
                "Circular pair " + pairIndex
                    + " reached its entry without a frozen pre-refill "
                    + "inventory plan."
            );
            return false;
        }

        HashMap<Item, Integer> required =
            getRequiredItems();
        HashMap<Item, Integer> available = usableInventoryCounts();
        Map<Item, Integer> missing =
            InventoryDemandSatisfaction.missingAmounts(
                required,
                available
            );
        if (!missing.isEmpty()) {
            debugLog(
                "InventoryPlan",
                "full frozen-plan shortfall pair=" + pairIndex
                    + " missing=" + missing.entrySet().stream()
                        .collect(Collectors.toMap(
                            entry ->
                                Registries.ITEM.getId(
                                    entry.getKey()
                                ).toString(),
                            Map.Entry::getValue,
                            Integer::sum,
                            LinkedHashMap::new
                        ))
            );
            return false;
        }
        HashMap<Item, Integer> compatibleRepairTools = new HashMap<>();
        for (int slot : availableSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (isCompatiblePlannedRepairTool(stack)) {
                compatibleRepairTools.merge(
                    stack.getItem(),
                    1,
                    Integer::sum
                );
            }
        }
        for (Map.Entry<Item, Integer> entry
            : plannedRepairToolDemand.entrySet()) {
            if (compatibleRepairTools.getOrDefault(
                entry.getKey(),
                0
            ) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean nextCircularPlanNeedsRestock() {
        return plannedCircularBuildPair >= 0
            && !hasSufficientPairMaterials(
                plannedCircularBuildPair
            );
    }

    private HashMap<Item, Integer>
        authoritativeBuildingOnHandCounts(
            Map<Item, Integer> genericCounts
        ) {
        HashMap<Item, Integer> counts =
            new HashMap<>(genericCounts);
        if (!buildingActive
            || plannedRepairToolDemand.isEmpty()) {
            return counts;
        }

        for (Item item : plannedRepairToolDemand.keySet()) {
            counts.put(item, 0);
        }
        for (int slot : availableSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (isCompatiblePlannedRepairTool(stack)) {
                counts.merge(
                    stack.getItem(),
                    1,
                    Integer::sum
                );
            }
        }
        return counts;
    }

    private HashMap<Item, Integer> remainingPrimaryBuildDemand(
        CompactCircularNbtPlan.PairRoute route
    ) {
        HashMap<Item, Integer> required = new HashMap<>();
        for (BlockPos relative :
            currentMandatoryBuildTargets(route)) {
            Block expected = buildTargets.get(relative);
            BlockPos world = mapCorner.add(relative);
            if (expected != null
                && latestKnownBuildBlock(world) != expected) {
                required.merge(expected.asItem(), 1, Integer::sum);
            }
        }
        return required;
    }

    private HashMap<Item, Integer> activeCircularPrimaryMaterialReserve(
        CompactCircularNbtPlan.PairRoute route
    ) {
        if (route == null) return new HashMap<>();
        if (plannedCircularBuildPair != route.pairIndex()) {
            return remainingPrimaryBuildDemand(route);
        }

        HashMap<Item, Integer> remaining = new HashMap<>();
        for (Map.Entry<Item, Integer> entry
            : plannedPrimaryMaterialDemand.entrySet()) {
            int amount = entry.getValue()
                - confirmedPrimaryMaterialUses.getOrDefault(
                    entry.getKey(),
                    0
                );
            if (amount > 0) {
                remaining.put(entry.getKey(), amount);
            }
        }
        return remaining;
    }

    private void recordConfirmedPrimaryMaterialUse(Item material) {
        int planned =
            plannedPrimaryMaterialDemand.getOrDefault(material, 0);
        if (planned <= 0) return;

        int confirmed =
            confirmedPrimaryMaterialUses.getOrDefault(material, 0);
        if (confirmed >= planned) return;
        confirmedPrimaryMaterialUses.put(material, confirmed + 1);
    }

    private boolean recoverFromActiveCircularMaterialShortfall(
        CompactCircularNbtPlan.PairRoute route
    ) {
        HashMap<Item, Integer> outstanding =
            outstandingUnsubmittedPrimaryMaterialDemand(route);
        HashMap<Item, Integer> onHand = usableInventoryCounts();
        for (Map.Entry<Item, Integer> entry : outstanding.entrySet()) {
            int available = onHand.getOrDefault(entry.getKey(), 0);
            if (available >= entry.getValue()) continue;

            warning(
                "Circular pair " + route.pairIndex() + " needs "
                    + entry.getValue() + " more "
                    + entry.getKey().getName().getString()
                    + " but only " + available
                    + " remain; safely leaving the U to refill and "
                    + "resume its missing blocks."
            );
            debugLog(
                "InventoryPlan",
                "active-U shortfall pair=" + route.pairIndex()
                    + " item=" + Registries.ITEM.getId(entry.getKey())
                    + " outstanding=" + entry.getValue()
                    + " onHand=" + available
                    + " frozenReserve="
                        + activeCircularPrimaryMaterialReserve(route)
                            .getOrDefault(entry.getKey(), 0)
            );
            stopBuildForAction(
                CircularBuildMovementPolicy.HoldReason
                    .NEXT_ROUTE_SUPPORT_CONFIRMATION
            );
            beginBuildRecovery(true);
            return true;
        }
        return false;
    }

    private HashMap<Item, Integer>
        outstandingUnsubmittedPrimaryMaterialDemand(
            CompactCircularNbtPlan.PairRoute route
        ) {
        HashMap<Item, Integer> outstanding = new HashMap<>();
        for (BlockPos relative :
            currentMandatoryBuildTargets(route)) {
            Block expected = buildTargets.get(relative);
            if (expected == null) continue;
            BlockPos world = mapCorner.add(relative);
            if (latestKnownBuildBlock(world) == expected
                || pendingPlacementLedger.isPending(world)) {
                continue;
            }
            outstanding.merge(expected.asItem(), 1, Integer::sum);
        }
        return outstanding;
    }

    private List<BlockPos> currentMandatoryBuildTargets(
        CompactCircularNbtPlan.PairRoute route
    ) {
        List<BlockPos> deferred =
            plannedCircularBuildPair == route.pairIndex()
                ? plannedDeferredMandatoryBuildOrder
                : optimizedDeferredBuildTargets.getOrDefault(
                    route.pairIndex(),
                    List.of()
                );
        return mandatoryCircularBuildTargets(route, deferred);
    }

    private Block latestKnownBuildBlock(BlockPos world) {
        ServerBlockObservation observed =
            serverBlockObservations.get(world);
        return observed == null
            ? MapAreaCache.getCachedBlockState(world).getBlock()
            : observed.block();
    }

    private HashMap<Item, Integer> usableInventoryCounts() {
        HashMap<Item, Integer> available = new HashMap<>();
        for (int slot : availableSlots) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            if (stack.getMaxDamage() > 0
                && stack.getMaxDamage() - stack.getDamage() <= 1) {
                continue;
            }
            available.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return available;
    }

    private boolean isCompatiblePlannedRepairTool(ItemStack stack) {
        if (stack.isEmpty()
            || !plannedRepairToolDemand.containsKey(stack.getItem())
            || !hasMinimumToolDurability(stack)) {
            return false;
        }
        Integer minimumEfficiency =
            plannedRepairMinimumEfficiency.get(stack.getItem());
        List<MiningToolRequirement> requirements =
            plannedRepairToolCompatibilityRequirements.getOrDefault(
                stack.getItem(),
                List.of()
            );
        if (requirements.isEmpty()) {
            // Baseline pickaxe/axe carry is item-based. It must not inherit
            // an enchantment or block-compatibility requirement when there
            // is no currently observed repair target for that tool.
            return true;
        }
        return minimumEfficiency != null
            && getEfficiencyLevel(stack) >= minimumEfficiency
            && requirements.stream().allMatch(requirement ->
                isCompatibleMiningTool(
                    stack,
                    requirement.registeredTemplate(),
                    requirement.targetState()
                )
            );
    }

    private void refreshPlannedRepairToolKeepSlots(
        InventoryS2CPacket packet
    ) {
        if (plannedRepairToolDemand.isEmpty()
            || packet.contents().size() < 36) {
            return;
        }

        HashSet<Integer> refreshed = new HashSet<>();
        int playerStart = packet.contents().size() - 36;
        for (Map.Entry<Item, Integer> demand
            : plannedRepairToolDemand.entrySet()) {
            ArrayList<Integer> candidates = new ArrayList<>();
            HashMap<Integer, Integer> remainingBySlot =
                new HashMap<>();
            for (int offset = 0; offset < 36; offset++) {
                ItemStack stack =
                    packet.contents().get(playerStart + offset);
                if (stack.getItem() != demand.getKey()
                    || !isCompatiblePlannedRepairTool(stack)) {
                    continue;
                }
                int playerSlot =
                    offset < 27 ? offset + 9 : offset - 27;
                candidates.add(playerSlot);
                remainingBySlot.put(
                    playerSlot,
                    remainingToolDurability(stack)
                );
            }
            candidates.sort(
                Comparator
                    .comparingInt(
                        (Integer slot) ->
                            remainingBySlot.getOrDefault(slot, 0)
                    )
                    .reversed()
                    .thenComparingInt(Integer::intValue)
            );
            for (int index = 0;
                 index < Math.min(
                     demand.getValue(),
                     candidates.size()
                 );
                 index++) {
                refreshed.add(candidates.get(index));
            }
        }
        plannedRepairToolKeepSlots.clear();
        plannedRepairToolKeepSlots.addAll(refreshed);
    }

    private boolean hasEarlierMissingBuildTarget(CompactCircularNbtPlan.PairRoute route) {
        BlockPos firstTarget = surfaceRuntimePosition(route.outboundX(), 1);
        int pairStart = orderedBuildTargets.indexOf(firstTarget);
        if (pairStart < 0) return false;
        Set<BlockPos> assignedEarlierTargets = new HashSet<>(
            optimizedDeferredBuildTargets.getOrDefault(
                route.pairIndex(),
                List.of()
            )
        );
        for (int index = 0; index < pairStart; index++) {
            BlockPos relative = orderedBuildTargets.get(index);
            Block expected = buildTargets.get(relative);
            if (expected != null
                && latestKnownBuildBlock(mapCorner.add(relative))
                    != expected
                && !assignedEarlierTargets.contains(relative)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCircularBuildCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        if (!buildingActive) return false;
        String action = checkpoint.getRight().getLeft();
        if (action.equals("preparePair")) return true;
        return activeCircularBuildPair >= 0
            && (action.equals("uBuildOutboundEnd")
                || action.equals("uBuildConnectorEnd")
                || action.equals("uBuildRecoveryExit")
                || action.equals("finishPair"));
    }

    private boolean isPreciseCircularBuildCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        if (!isCircularBuildCheckpoint(checkpoint)) return false;
        String action = checkpoint.getRight().getLeft();
        return action.equals("preparePair")
            || action.equals("uBuildRecoveryExit")
            || action.equals("finishPair");
    }

    private boolean isPersistedMiningRecoveryCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        String action = checkpoint.getRight().getLeft();
        return action.equals("persistedMiningRecoveryStep")
            || action.equals("resumePersistedMiningFromWalkway");
    }

    /**
     * Keeps only the connector's ordered, three-dimensional walking cursor.
     * Block selection, range checking, hotbar swapping, and placement all run
     * through the normal Nerv printing loop below this method.
     */
    private boolean updateCircularConnectorTraversal() {
        if (activeCircularBuildPair < 0
            || activeCircularBuildPair >= compactPlan.pairRoutes().size()
            || checkpoints.isEmpty()
            || !checkpoints.getFirst().getRight().getLeft()
                .equals("uBuildConnectorEnd")) {
            error("Circular connector traversal lost its structural endpoints.");
            toggle();
            return false;
        }

        List<BlockPos> connectorSteps = activeCircularConnectorSteps;
        if (activeCircularConnectorIndex < 0
            || activeCircularConnectorIndex >= connectorSteps.size()) {
            error("Circular connector traversal index became invalid.");
            toggle();
            return false;
        }

        BlockPos world = connectorSteps.get(activeCircularConnectorIndex);
        BlockPos relative = world.subtract(mapCorner);
        Block expected = buildTargets.get(relative);
        if (expected == null) {
            error(
                "Circular connector references no compact target at "
                    + world.toShortString() + "."
            );
            toggle();
            return false;
        }

        BlockState current = MapAreaCache.getCachedBlockState(world);
        if (!current.isAir() && current.getBlock() != expected) {
            if (!repairCurrentUPair.get()
                || !BlockUtils.canBreak(world, current)) {
                error(
                    "Circular connector changed unexpectedly at "
                        + world.toShortString() + "."
                );
                toggle();
                return false;
            }
            // The printing scheduler owns active-pair repair and will stop
            // movement until this exact support reaches confirmed air and is
            // replaced with the expected block.
            return true;
        }
        if (!MapAreaCache.getCachedBlockState(world.up()).isAir()
            || !MapAreaCache.getCachedBlockState(world.up(2)).isAir()) {
            error(
                "Circular connector headroom is blocked at "
                    + world.toShortString() + "."
            );
            toggle();
            return false;
        }

        Vec3d goal = walkingPosition(world);
        double horizontalDistance = PlayerUtils.distanceTo(
            goal.add(0, mc.player.getY() - goal.y, 0)
        );
        double connectorBuffer = circularBuildConnectorBuffer();
        if (current.isAir()
            || current.getBlock() != expected
            || !CircularTraversalSafety.isConnectorStepHeightReachable(
                mc.player.getY(),
                goal.y
            )
            || horizontalDistance >= connectorBuffer) {
            return true;
        }

        activeCircularConnectorIndex++;
        if (activeCircularConnectorIndex >= connectorSteps.size()) {
            activeCircularConnectorSteps = List.of();
            circularBuildPhase = CircularBuildPhase.RETURN;
        }
        return true;
    }

    private Vec3d currentCircularConnectorGoal() {
        return walkingPosition(
            activeCircularConnectorSteps.get(activeCircularConnectorIndex)
        );
    }

    private Vec3d currentActiveOrderedUMovementGoal(
        ActiveOrderedUTraversal traversal
    ) {
        List<BlockPos> supports = traversal.supports();
        if (supports.isEmpty()
            || activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex >= supports.size()) {
            throw new IllegalStateException(
                "Active ordered U traversal has no movement goal."
            );
        }
        int goalIndex = OrderedUTraversalMovement.steeringGoalIndex(
            supports,
            activeCircularRouteSupportIndex
        );
        return walkingPosition(supports.get(goalIndex));
    }

    private boolean shouldSprintActiveOrderedU(
        ActiveOrderedUTraversal traversal
    ) {
        if (sprinting.get() == SprintMode.Off
            || sprinting.get() == SprintMode.NotPlacing) {
            return false;
        }
        return !isActiveOrderedUConnectorSegment(traversal);
    }

    private boolean isActiveOrderedUConnectorSegment(
        ActiveOrderedUTraversal traversal
    ) {
        if (activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex
                >= traversal.supports().size()) {
            return true;
        }

        Set<BlockPos> connectorSupports =
            traversal.route().relativePath().stream()
            .map(this::connectorRuntimePosition)
            .map(mapCorner::add)
            .collect(Collectors.toSet());
        BlockPos current = traversal.supports().get(
            activeCircularRouteSupportIndex
        );
        if (!connectorSupports.contains(current)) return false;
        int nextIndex = activeCircularRouteSupportIndex + 1;
        return nextIndex < traversal.supports().size()
            && connectorSupports.contains(
                traversal.supports().get(nextIndex)
            );
    }

    private void tickCircularBuildRecovery() {
        if (activeCircularBuildPair < 0
            || activeCircularBuildPair >= compactPlan.pairRoutes().size()
            || circularBuildRecoveryDirection == 0
            || checkpoints.isEmpty()
            || !checkpoints.getFirst().getRight().getLeft()
                .equals("uBuildRecoveryExit")) {
            error("Circular build recovery lost its north exit.");
            toggle();
            return;
        }

        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(activeCircularBuildPair);
        List<BlockPos> supports = activeCircularRecoveryTargets;
        if (supports.isEmpty()) {
            error("Circular build recovery lost its validated support path.");
            toggle();
            return;
        }
        if (CircularBuildRecoveryCursor.complete(
            activeCircularConnectorIndex,
            supports.size()
        )) {
            circularBuildRecoveryDirection = 0;
            activeCircularRecoveryTargets = List.of();
            circularBuildPhase = CircularBuildPhase.RECOVERY_EXIT;
            stopMovement();
            return;
        }

        OptionalInt resolvedIndex =
            OrderedRouteProgressResolver.resolve(
                supports,
                activeCircularConnectorIndex,
                circularBuildRecoveryDirection,
                mc.player.getX(),
                mc.player.getZ()
            );
        if (resolvedIndex.isEmpty()) {
            BlockPos cursorSupport =
                supports.get(activeCircularConnectorIndex);
            stopMovement();
            error(
                "Interrupted circular build left its ordered recovery route "
                    + "near " + mc.player.getBlockPos().toShortString()
                    + " from support="
                    + cursorSupport.toShortString() + "."
            );
            toggle();
            return;
        }

        int nextResolvedIndex = resolvedIndex.getAsInt();
        BlockPos currentSupport = supports.get(nextResolvedIndex);
        if (!isConfirmedCircularBuildSupport(route, currentSupport)) {
            stopMovement();
            error(
                "Interrupted circular build recovery support changed at "
                    + currentSupport.toShortString() + "."
            );
            toggle();
            return;
        }
        activeCircularConnectorIndex = nextResolvedIndex;

        int nextIndex =
            activeCircularConnectorIndex
                + circularBuildRecoveryDirection;
        if (nextIndex < 0 || nextIndex >= supports.size()) {
            debugLog(
                "Recovery",
                "reached ordered north egress support="
                    + currentSupport.toShortString()
                    + " pair=" + activeCircularBuildPair
            );
            circularBuildRecoveryDirection = 0;
            activeCircularRecoveryTargets = List.of();
            circularBuildPhase = CircularBuildPhase.RECOVERY_EXIT;
            stopMovement();
            return;
        }

        BlockPos nextSupport = supports.get(nextIndex);
        if (!isConfirmedCircularBuildSupport(route, nextSupport)) {
            stopMovement();
            error(
                "Interrupted circular build recovery next support changed at "
                    + nextSupport.toShortString() + "."
            );
            toggle();
            return;
        }
        moveAlongCircularSupport(walkingPosition(nextSupport));
    }

    private void moveAlongCircularSupport(Vec3d goal) {
        mc.player.setSprinting(false);
        mc.player.setYaw((float) Rotations.getYaw(goal));
        Utils.setForwardPressed(true);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);

        if (jumpTimeout <= 0) {
            Direction direction =
                Direction.fromHorizontalDegrees(mc.player.getYaw());
            BlockPos step = mc.player.getBlockPos().offset(direction);
            if (mc.player.isOnGround()
                && !MapAreaCache.getCachedBlockState(step).isAir()
                && MapAreaCache.getCachedBlockState(step.up()).isAir()
                && MapAreaCache.getCachedBlockState(step.up(2)).isAir()) {
                jumpTimeout = jumpCoolDown.get();
                Utils.setJumpPressed(true);
            }
        }
    }

    private CircularBuildCheckpointPlan.Plan<BlockPos> circularBuildCheckpointPlan(
        CompactCircularNbtPlan.PairRoute route
    ) {
        List<BlockPos> connectorPath = route.relativePath().stream()
            .map(this::connectorRuntimePosition)
            .map(mapCorner::add)
            .toList();
        BlockPos expectedOutboundEnd = mapCorner.add(
            surfaceRuntimePosition(
                route.outboundX(),
                CompactCircularNbtPlan.FAR_Z
            )
        );
        BlockPos expectedReturnEnd = mapCorner.add(
            surfaceRuntimePosition(
                route.returnX(),
                CompactCircularNbtPlan.FAR_Z
            )
        );
        if (!connectorPath.getFirst().equals(expectedOutboundEnd)
            || !connectorPath.getLast().equals(expectedReturnEnd)) {
            throw new IllegalStateException(
                "Compact connector endpoints do not match their printed columns."
            );
        }
        return CircularBuildCheckpointPlan.create(
            northWalkwaySupport(route.outboundX()),
            connectorPath,
            circularBuildExitAlignmentSupport(route)
        );
    }

    private boolean isCircularSurfaceLegComplete(int x) {
        for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
            BlockPos relative = surfaceRuntimePosition(x, nbtZ);
            BlockPos world = mapCorner.add(relative);
            if (latestKnownBuildBlock(world)
                != buildTargets.get(relative)) {
                return false;
            }
        }
        return true;
    }

    private BlockPos firstUnexpectedCircularSurfaceBlock(int x) {
        for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
            BlockPos relative = surfaceRuntimePosition(x, nbtZ);
            BlockPos world = mapCorner.add(relative);
            Block current = latestKnownBuildBlock(world);
            if (current != Blocks.AIR
                && current != buildTargets.get(relative)) {
                return world;
            }
        }
        return null;
    }

    private boolean recoverCircularBuildTraversal(boolean inventoryLost) {
        miningPos = null;
        stopMovement();
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        if (inventoryLost && !setupSlots()) return false;
        resetMapAreaCache();

        int interruptedPair = activeCircularBuildPair;
        if (interruptedPair >= 0
            && interruptedPair < compactPlan.pairRoutes().size()) {
            CompactCircularNbtPlan.PairRoute route =
                compactPlan.pairRoutes().get(interruptedPair);
            ArrayList<BlockPos> targets = circularPairTargets(route);
            BlockPos supportUnderPlayer = supportBelowCheckpoint(mc.player.getEntityPos());
            List<BlockPos> supportPath =
                activeCircularBuildSupportPath(route);
            if (!inventoryLost
                && activeCircularRouteSupportIndex >= 0
                && activeCircularRouteSupportIndex
                    < supportPath.size()) {
                OptionalInt correctedSupportIndex =
                    CircularBuildRecoveryCursor.resolveHorizontalSupport(
                        supportPath,
                        activeCircularRouteSupportIndex,
                        mc.player.getX(),
                        mc.player.getZ()
                    );
                if (correctedSupportIndex.isPresent()) {
                    int supportIndex =
                        correctedSupportIndex.getAsInt();
                    int targetIndex = supportIndex - 2;
                    if (targetIndex >= 0
                        && targetIndex < targets.size()) {
                        CircularBuildPhase recoveredPhase =
                            circularBuildPhaseForTargetIndex(
                                route,
                                targetIndex
                            );
                        if (recoveredPhase
                                == CircularBuildPhase.OUTBOUND
                            || recoveredPhase
                                == CircularBuildPhase.RETURN) {
                            activeCircularRouteSupportIndex =
                                supportIndex;
                            activeCircularPlacementCursor =
                                targetIndex;
                            circularBuildPhase = recoveredPhase;
                            state = State.Walking;
                            debugLog(
                                "Recovery",
                                "resumed corrected horizontal U cell pair="
                                    + interruptedPair
                                    + " targetIndex=" + targetIndex
                                    + " expectedSupport="
                                        + supportPath.get(supportIndex)
                                            .toShortString()
                                    + " observedGround="
                                        + supportUnderPlayer.toShortString()
                                    + " phase=" + recoveredPhase
                            );
                            info(
                                "Server position correction remained on "
                                    + "the ordered U; resuming placement "
                                    + "and repairing any missing support."
                            );
                            return true;
                        }
                    }
                }
            }
            if (CircularBuildSupportPath.isDirectReplanSupport(
                supportPath,
                supportUnderPlayer
            )) {
                if (!isPlayerStandingOnSupport(supportUnderPlayer)
                    || !isConfirmedCircularBuildSupport(
                        route,
                        supportUnderPlayer
                    )) {
                    error(
                        "Interrupted circular build restart support is no "
                            + "longer safe at "
                            + supportUnderPlayer.toShortString() + "."
                    );
                    toggle();
                    return false;
                }
                debugLog(
                    "Recovery",
                    "directly replanning from external circular support="
                        + supportUnderPlayer.toShortString()
                        + " pair=" + interruptedPair
                );
                return replanCircularBuildFromSafeArea(inventoryLost);
            }
            int currentIndex = targets.indexOf(supportUnderPlayer.subtract(mapCorner));
            if (currentIndex >= 0) {
                if (!isPlayerStandingOnSupport(supportUnderPlayer)) {
                    error(
                        "Interrupted circular build recovery cannot identify a stable support under the player."
                    );
                    toggle();
                    return false;
                }
                boolean localInventoryPlanReady =
                    !inventoryLost
                        || prepareCircularBuildInventoryPlan(route);
                if (localInventoryPlanReady
                    && resumeCircularBuildFromLocalSurfaceSupport(
                        route,
                        supportPath,
                        currentIndex,
                        supportUnderPlayer
                    )) {
                    state = State.Walking;
                    info(
                        "Recovered the active U cursor from the support "
                            + "under the player; continuing circular "
                            + "placement in place."
                    );
                    return true;
                }
                if (!planCircularBuildRecoveryEgress(
                    route,
                    targets,
                    currentIndex,
                    inventoryLost
                )) {
                    toggle();
                    return false;
                }
                state = State.Walking;
                info(
                    "Safely backing out of interrupted circular pair "
                        + interruptedPair + " before replanning"
                );
                return true;
            }

            BlockPos relativeSupport = supportUnderPlayer.subtract(mapCorner);
            boolean overInterruptedRoute = targets.stream().anyMatch(
                target -> target.getX() == relativeSupport.getX()
                    && target.getZ() == relativeSupport.getZ()
            );
            if (overInterruptedRoute) {
                error(
                    "Interrupted circular build is not standing on its expected U support. "
                        + "Stopping instead of cutting diagonally across the map."
                );
                toggle();
                return false;
            }
        }

        if (!isAtKnownSafeBuildRecoveryLocation()) {
            error(
                "Interrupted circular build is not on its U route, north walkway, "
                    + "or a registered safe station. Stopping instead of walking diagonally."
            );
            toggle();
            return false;
        }
        return replanCircularBuildFromSafeArea(inventoryLost);
    }

    private boolean resumeCircularBuildFromLocalSurfaceSupport(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> supportPath,
        int currentIndex,
        BlockPos supportUnderPlayer
    ) {
        CircularBuildPhase recoveredPhase =
            circularBuildPhaseForTargetIndex(
                route,
                currentIndex
            );
        if (recoveredPhase != CircularBuildPhase.OUTBOUND
            && recoveredPhase != CircularBuildPhase.RETURN) {
            return false;
        }
        int supportIndex = currentIndex + 2;
        if (supportIndex < 0
            || supportIndex >= supportPath.size()
            || !supportPath.get(supportIndex).equals(
                supportUnderPlayer
            )
            || !isConfirmedCircularBuildSupport(
                route,
                supportUnderPlayer
            )) {
            return false;
        }

        activeCircularRouteSupportIndex = supportIndex;
        activeCircularPlacementCursor = currentIndex;
        activeCircularConnectorIndex = -1;
        activeCircularConnectorSteps = List.of();
        activeCircularRecoveryTargets = List.of();
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = recoveredPhase;
        debugLog(
            "Recovery",
            "reconstructed local build cursor pair="
                + route.pairIndex()
                + " targetIndex=" + currentIndex
                + " supportIndex=" + supportIndex
                + " support="
                    + supportUnderPlayer.toShortString()
                + " phase=" + recoveredPhase
        );
        return true;
    }

    private CircularBuildPhase circularBuildPhaseForTargetIndex(
        CompactCircularNbtPlan.PairRoute route,
        int targetIndex
    ) {
        int connectorStart =
            CompactCircularNbtPlan.VISIBLE_ROWS;
        int returnStart =
            connectorStart + route.relativeInterior().size();
        if (targetIndex < 0
            || targetIndex >= circularPairTargets(route).size()) {
            throw new IllegalArgumentException(
                "Circular target index is outside its U."
            );
        }
        if (targetIndex < connectorStart) {
            return CircularBuildPhase.OUTBOUND;
        }
        if (targetIndex < returnStart) {
            return CircularBuildPhase.CONNECTOR;
        }
        return CircularBuildPhase.RETURN;
    }

    private boolean planCircularBuildRecoveryEgress(
        CompactCircularNbtPlan.PairRoute route,
        ArrayList<BlockPos> targets,
        int currentIndex,
        boolean inventoryLost
    ) {
        boolean prefixSafe = isCircularBuildSegmentSafe(targets, 0, currentIndex)
            && isSafeNorthWalkway(route.outboundX());
        boolean suffixSafe = isCircularBuildSegmentSafe(
            targets,
            currentIndex,
            targets.size() - 1
        ) && isSafeNorthWalkway(route.returnX());
        if (!prefixSafe && !suffixSafe) {
            error(
                "Interrupted circular pair " + route.pairIndex()
                    + " has no continuous built route to either north endpoint."
            );
            return false;
        }
        int egressDirection =
            CircularBuildRecoveryCursor.chooseDirection(
                prefixSafe,
                currentIndex + 1,
                suffixSafe,
                targets.size() - currentIndex
            );

        checkpoints.clear();
        buildRecoveryRestockAfterEgress |= inventoryLost;
        circularBuildPhase = CircularBuildPhase.RECOVERY;
        activeCircularConnectorSteps = List.of();
        if (egressDirection < 0) {
            circularBuildRecoveryDirection = -1;
            BlockPos walkway = northWalkwaySupport(route.outboundX());
            ArrayList<BlockPos> recoverySupports =
                new ArrayList<>(currentIndex + 2);
            recoverySupports.add(walkway);
            for (int index = 0; index <= currentIndex; index++) {
                recoverySupports.add(mapCorner.add(targets.get(index)));
            }
            activeCircularRecoveryTargets =
                List.copyOf(recoverySupports);
            activeCircularConnectorIndex =
                activeCircularRecoveryTargets.size() - 1;
            checkpoints.add(new Pair<>(
                walkingPosition(walkway),
                new Pair<>(
                    "uBuildRecoveryExit",
                    new BlockPos(route.outboundX(), 0, 0)
                )
            ));
        } else {
            circularBuildRecoveryDirection = 1;
            BlockPos walkway = northWalkwaySupport(route.returnX());
            ArrayList<BlockPos> recoverySupports =
                new ArrayList<>(targets.size() - currentIndex + 1);
            for (int index = currentIndex;
                 index < targets.size();
                 index++) {
                recoverySupports.add(mapCorner.add(targets.get(index)));
            }
            recoverySupports.add(walkway);
            activeCircularRecoveryTargets =
                List.copyOf(recoverySupports);
            activeCircularConnectorIndex = 0;
            checkpoints.add(new Pair<>(
                walkingPosition(walkway),
                new Pair<>(
                    "uBuildRecoveryExit",
                    new BlockPos(route.returnX(), 0, 0)
                )
            ));
        }
        debugLog(
            "Recovery",
            "planned monotonic egress pair=" + route.pairIndex()
                + " from="
                    + activeCircularRecoveryTargets
                        .get(activeCircularConnectorIndex)
                        .toShortString()
                + " to="
                    + (egressDirection < 0
                        ? activeCircularRecoveryTargets.getFirst()
                        : activeCircularRecoveryTargets.getLast())
                        .toShortString()
                + " direction=" + circularBuildRecoveryDirection
                + " supports="
                    + activeCircularRecoveryTargets.size()
        );
        return true;
    }

    private boolean isCircularBuildSegmentSafe(
        List<BlockPos> targets,
        int startInclusive,
        int endInclusive
    ) {
        for (int index = startInclusive; index <= endInclusive; index++) {
            BlockPos relative = targets.get(index);
            BlockPos world = mapCorner.add(relative);
            Block expected = buildTargets.get(relative);
            BlockState state = MapAreaCache.getCachedBlockState(world);
            if (expected == null
                || state.getBlock() != expected
                || !MapAreaCache.getCachedBlockState(world.up()).isAir()
                || !MapAreaCache.getCachedBlockState(world.up(2)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlayerStandingOnSupport(BlockPos support) {
        if (!supportBelowCheckpoint(mc.player.getEntityPos()).equals(support)) return false;
        return Math.abs(mc.player.getY() - (support.getY() + 1.0)) <= 0.25;
    }

    private boolean isRaisedCircularBuildEntry(BlockPos obstacle) {
        if (state != State.Walking
            || circularBuildPhase != CircularBuildPhase.OUTBOUND
            || activeCircularBuildPair < 0
            || activeCircularBuildPair
                >= compactPlan.pairRoutes().size()) {
            return false;
        }
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(activeCircularBuildPair);
        BlockPos walkway =
            northWalkwaySupport(route.outboundX());
        BlockPos firstTarget = mapCorner.add(
            surfaceRuntimePosition(route.outboundX(), 1)
        );
        return obstacle.equals(firstTarget)
            && firstTarget.getY() == walkway.getY() + 1
            && isPlayerStandingOnSupport(walkway);
    }

    private boolean isAtKnownSafeBuildRecoveryLocation() {
        BlockPos support = supportBelowCheckpoint(mc.player.getEntityPos());
        BlockPos relative = support.subtract(mapCorner);
        if (relative.getZ() == -1
            && northWalkwayRelativeY != null
            && relative.getY() == northWalkwayRelativeY
            && relative.getX() >= 0
            && relative.getX() < map.length
            && isSafeNorthWalkway(relative.getX())
            && isPlayerStandingOnSupport(support)) {
            return true;
        }

        if (compactPlan != null && northWalkwayRelativeY != null) {
            for (CompactCircularNbtPlan.PairRoute route
                : compactPlan.pairRoutes()) {
                if (route.outboundX() < workingInterval.getLeft()
                    || route.returnX() > workingInterval.getRight()) {
                    continue;
                }
                BlockPos alignment =
                    circularBuildAlignmentSupport(route);
                if (support.equals(alignment)
                    && isSafeCircularBuildAlignment(route)
                    && isPlayerStandingOnSupport(alignment)) {
                    return true;
                }
                BlockPos exitAlignment =
                    circularBuildExitAlignmentSupport(route);
                if (support.equals(exitAlignment)
                    && isSafeCircularBuildExitAlignment(route)
                    && isPlayerStandingOnSupport(exitAlignment)) {
                    return true;
                }
            }
        }

        if (dumpStation != null && isNearRegisteredPosition(dumpStation.getLeft())) {
            return true;
        }
        for (Pair<BlockPos, Vec3d> station : Arrays.asList(
            cartographyTable,
            finishedMapChest,
            usedToolChest,
            bed,
            anvil,
            enderChest,
            craftingTable
        )) {
            if (station != null && isNearRegisteredPosition(station.getRight())) return true;
        }
        for (Pair<BlockPos, Vec3d> station : mapMaterialChests) {
            if (isNearRegisteredPosition(station.getRight())) return true;
        }
        for (ArrayList<Pair<BlockPos, Vec3d>> stations : materialDict.values()) {
            for (Pair<BlockPos, Vec3d> station : stations) {
                if (isNearRegisteredPosition(station.getRight())) return true;
            }
        }
        for (Pair<BlockPos, Vec3d> station : usedToolChests.values()) {
            if (isNearRegisteredPosition(station.getRight())) return true;
        }
        return false;
    }

    /**
     * Reconnect recovery may begin on the exterior logistics platform rather
     * than on the exact north-walkway row. Accepting that location as merely
     * "safe" would still allow the normal checkpoint walker to cut diagonally
     * across an unverified gap. Instead, prove a cardinal, grounded path back
     * to any intact north-walkway support and enqueue every support in order.
     */
    private boolean schedulePersistedMiningExteriorRecovery() {
        Optional<GroundedSupportPathPlanner.Plan> planned =
            planPersistedMiningExteriorRecovery();
        if (planned.isEmpty()) {
            BlockPos support = mc.player == null || mapCorner == null
                ? null
                : supportBelowCheckpoint(
                    mc.player.getEntityPos()
                );
            debugLog(
                "Recovery",
                "no connected grounded exterior approach from support="
                    + (support == null
                        ? "unknown"
                        : support.toShortString())
                    + " to the verified north walkway"
            );
            return false;
        }

        GroundedSupportPathPlanner.Plan plan = planned.orElseThrow();
        checkpoints.clear();
        List<GroundedSupportPathPlanner.Cell> path = plan.path();
        for (int index = 1; index < path.size(); index++) {
            BlockPos support = exteriorRecoveryWorld(path.get(index));
            boolean endpoint = index == path.size() - 1;
            checkpoints.add(new Pair<>(
                walkingPosition(support),
                new Pair<>(
                    endpoint
                        ? "resumePersistedMiningFromWalkway"
                        : "persistedMiningRecoveryStep",
                    support
                )
            ));
        }
        if (checkpoints.isEmpty()) return false;

        BlockPos start = exteriorRecoveryWorld(path.getFirst());
        BlockPos endpoint = exteriorRecoveryWorld(plan.endpoint());
        state = State.Walking;
        stopMovement();
        info(
            "Recovered teardown is on a connected exterior support; "
                + "following the verified grounded approach to the "
                + "north walkway before resuming pair "
                + recoveredActiveMiningPair + "."
        );
        debugLog(
            "Recovery",
            "planned persisted teardown exterior approach start="
                + start.toShortString()
                + " endpoint=" + endpoint.toShortString()
                + " supports=" + path.size()
                + " retainedPair=" + recoveredActiveMiningPair
                + " retainedSupportIndex="
                    + recoveredActiveMiningTargetIndex
        );
        return true;
    }

    private Optional<GroundedSupportPathPlanner.Plan>
        planPersistedMiningExteriorRecovery() {
        if (mc.player == null
            || mc.world == null
            || mapCorner == null
            || map == null
            || workingInterval == null
            || northWalkwayRelativeY == null) {
            return Optional.empty();
        }

        BlockPos startWorld = supportBelowCheckpoint(
            mc.player.getEntityPos()
        );
        if (!isPlayerStandingOnSupport(startWorld)
            || !isWalkableExteriorRecoverySupport(startWorld)) {
            return Optional.empty();
        }
        BlockPos startRelative = startWorld.subtract(mapCorner);
        // Z=-1 is the canonical walkway itself. Z>=0 is inside the map and
        // must only be recovered through an owned U route.
        if (startRelative.getZ() >= -1) return Optional.empty();

        int minimumX = Math.max(0, workingInterval.getLeft());
        int maximumX = Math.min(
            map.length - 1,
            workingInterval.getRight()
        );
        if (startRelative.getX() < minimumX
            || startRelative.getX() > maximumX) {
            return Optional.empty();
        }

        HashSet<GroundedSupportPathPlanner.Cell> goals =
            new HashSet<>();
        for (int x = minimumX; x <= maximumX; x++) {
            if (isSafeNorthWalkway(x)) {
                goals.add(new GroundedSupportPathPlanner.Cell(
                    x,
                    northWalkwayRelativeY,
                    -1
                ));
            }
        }
        if (goals.isEmpty()) return Optional.empty();

        GroundedSupportPathPlanner.Cell start =
            new GroundedSupportPathPlanner.Cell(
                startRelative.getX(),
                startRelative.getY(),
                startRelative.getZ()
            );
        int horizontalDepth = -1 - startRelative.getZ();
        int minimumY = Math.min(
            startRelative.getY(),
            northWalkwayRelativeY
        ) - horizontalDepth;
        int maximumY = Math.max(
            startRelative.getY(),
            northWalkwayRelativeY
        ) + horizontalDepth;
        long domainSize = (long) (maximumX - minimumX + 1)
            * (horizontalDepth + 1L)
            * (maximumY - minimumY + 1L);
        int nodeCap = (int) Math.min(
            16_384L,
            Math.max(64L, domainSize)
        );

        return GroundedSupportPathPlanner.findPath(
            start,
            goals,
            candidate ->
                candidate.x() >= minimumX
                    && candidate.x() <= maximumX
                    && candidate.z() >= startRelative.getZ()
                    && candidate.z() <= -1
                    && candidate.y() >= minimumY
                    && candidate.y() <= maximumY,
            candidate -> isWalkableExteriorRecoverySupport(
                exteriorRecoveryWorld(candidate)
            ),
            nodeCap
        );
    }

    private BlockPos exteriorRecoveryWorld(
        GroundedSupportPathPlanner.Cell cell
    ) {
        return mapCorner.add(cell.x(), cell.y(), cell.z());
    }

    private boolean isWalkableExteriorRecoverySupport(
        BlockPos support
    ) {
        if (mc.world == null) return false;
        BlockState state = MapAreaCache.getCachedBlockState(support);
        return !state.isAir()
            && state.isSolidBlock(mc.world, support)
            && MapAreaCache.getCachedBlockState(support.up()).isAir()
            && MapAreaCache.getCachedBlockState(support.up(2)).isAir();
    }

    private boolean isNearRegisteredPosition(Vec3d position) {
        return PlayerUtils.distanceTo(position) <= 1.0;
    }

    private boolean replanCircularBuildFromSafeArea(boolean inventoryLost) {
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        activeCircularPlacementCursor = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        releaseBuildRepairSpeedMine();
        buildRepairController.reset();
        confirmedBuildHotbarSwap.clear();
        confirmedMiningHotbarSwap.clear();
        clearPendingInventorySwapState();
        if (inventoryLost) repairToolShadows.clear();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        pendingPlacementLedger.reset();
        optionalPendingPlacements.clear();
        placementSubmissionBlockSequences.clear();
        repairSubmissionBlockSequences.clear();
        workActionBudget.reset();
        applyPendingInterval();
        resetMapAreaCache();
        configurePairTraversalModes();
        if (!validateCompactWorkspace()) {
            toggle();
            return false;
        }
        calculateBuildingPath(false);
        if (circularTraversalForCurrentMap
            && !prepareNextCircularBuildInventoryPlan()
            && requireCompleteUInventory.get()) {
            toggle();
            return false;
        }
        if ((inventoryLost || nextCircularPlanNeedsRestock())
            && !checkpoints.isEmpty()) {
            checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
            prependPlannedBuildUsedToolDeposits();
        }
        state = State.Walking;
        info("Replanned the interrupted circular build from a safe north endpoint");
        return true;
    }

    private void addIndependentColumnPath(int x) {
        boolean lineFinished = true;
        for (int z = 0; z < 128; z++) {
            BlockPos relative = new BlockPos(x, map[x][z].getRight(), z);
            if (MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) {
                lineFinished = false;
                break;
            }
        }
        if (lineFinished) return;

        BlockPos startWalkway = northWalkwaySupport(x);
        BlockPos farEnd = mapCorner.add(x, map[x][127].getRight(), 127);
        checkpoints.add(new Pair<>(walkingPosition(startWalkway), new Pair<>("", null)));
        checkpoints.add(new Pair<>(walkingPosition(farEnd), new Pair<>("", null)));
        checkpoints.add(new Pair<>(
            walkingPosition(startWalkway),
            new Pair<>("independentColumnEnd", null)
        ));
    }

    private Vec3d walkingPosition(BlockPos supportingBlock) {
        return supportingBlock.toCenterPos().add(0, 0.5, 0);
    }

    private double circularBuildConnectorBuffer() {
        return CircularTraversalSafety.connectorCheckpointBuffer(
            checkpointBuffer.get()
        );
    }

    private void steerTowardGoal(Vec3d goal) {
        double lookX = goal.x;
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ()
                + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        Vec3d lookPos = new Vec3d(lookX, goal.y, lookZ);
        if (state.equals(State.Walking)
            || state.equals(State.MiningUTraversal)) {
            mc.player.setYaw((float) Rotations.getYaw(lookPos));
        } else {
            mc.player.setYaw((float) Rotations.getYaw(lookPos) + 180f);
        }
    }

    private void stopMovement() {
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
    }

    private RecoveryOwnerPolicy.Owner activeRecoveryOwner() {
        boolean miningRuntimeActive =
            miningAssignmentsActive
                || (currentMiningLines != null
                    && !currentMiningLines.isEmpty())
                || isCircularMiningOrRestockState();
        return RecoveryOwnerPolicy.decide(
            mapCyclePhase == null
                ? MapCyclePhase.IDLE
                : mapCyclePhase,
            buildingActive,
            miningRuntimeActive
        );
    }

    private boolean hasStableGroundedMiningRecoverySnapshot(
        String recovery
    ) {
        BlockPos support = mc.player == null
            ? null
            : supportBelowCheckpoint(
                mc.player.getEntityPos()
            );
        boolean stable =
            miningRecoverySnapshotGate.observe(
                support,
                mc.player != null
                    && mc.player.isOnGround()
            );
        if (!stable && !miningRecoverySnapshotWaitLogged) {
            debugLog(
                "Recovery",
                recovery
                    + " is waiting for consecutive grounded "
                    + "observations of one support; support="
                    + support
                    + " grounded="
                    + (mc.player != null
                        && mc.player.isOnGround())
                    + " observations="
                    + miningRecoverySnapshotGate
                        .observations()
            );
            miningRecoverySnapshotWaitLogged = true;
        }
        return stable;
    }

    private void freezeForRecoveryClassification() {
        stopMovement();
        jumpTimeout = 0;
        miningRecoverySnapshotGate.reset();
        miningRecoverySnapshotWaitLogged = false;
        if (mc.player == null) return;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(0, velocity.y, 0);
    }

    private boolean handleLogisticsNavigation(Vec3d goal) {
        if (!logisticsObstacleDetours.get()
            || (state != State.Walking && state != State.MiningUTraversal)
            || checkpoints.isEmpty()
            || mc.player == null) {
            boolean removedActiveDetour = checkpoints != null
                && !checkpoints.isEmpty()
                && isLogisticsDetourCheckpoint(checkpoints.getFirst());
            cancelLogisticsDetour();
            if (removedActiveDetour) stopMovement();
            return removedActiveDetour;
        }

        Pair<Vec3d, Pair<String, BlockPos>> checkpoint = checkpoints.getFirst();
        String action = checkpoint.getRight().getLeft();
        double horizontalDistance = Math.hypot(
            goal.x - mc.player.getX(),
            goal.z - mc.player.getZ()
        );

        if (action.equals("logisticsDetour")) {
            if (activeLogisticsTerminal == null) {
                error("A logistics detour lost its original destination. Stopping safely.");
                stopMovement();
                toggle();
                return true;
            }
            if (!isLogisticsSegmentWalkable(goal)) {
                return attemptLogisticsDetour(
                    "the planned bypass changed before it was reached"
                );
            }
            boolean stalled = logisticsProgressWatchdog.observe(
                activeLogisticsTerminal,
                horizontalDistance,
                true,
                mc.player.isOnGround()
            );
            if (stalled) {
                return attemptLogisticsDetour(
                    "the bot stopped progressing along the planned bypass"
                );
            }
            return false;
        }

        LogisticsDetourPlanner.Point currentCell = currentLogisticsCell();
        boolean eligible = LOGISTICS_TRAVEL_ACTIONS.contains(action)
            && activeCircularBuildPair < 0
            && !isProtectedMapCell(currentCell);
        if (!eligible) {
            clearLogisticsTracking();
            return false;
        }

        LogisticsTerminal terminal = new LogisticsTerminal(
            action,
            goal.x,
            goal.y,
            goal.z,
            checkpoint.getRight().getRight()
        );
        if (!terminal.equals(activeLogisticsTerminal)) {
            clearLogisticsTracking();
            activeLogisticsTerminal = terminal;
        }

        boolean stalled = logisticsProgressWatchdog.observe(
            terminal,
            horizontalDistance,
            true,
            mc.player.isOnGround()
        );
        if (!stalled) return false;
        return attemptLogisticsDetour(
            "straight-line travel to " + readableLogisticsAction(action)
                + " made no progress"
        );
    }

    private boolean attemptLogisticsDetour(String reason) {
        stopMovement();
        if (mc.player != null) mc.player.setSprinting(false);
        removeLeadingLogisticsDetourCheckpoints();

        if (activeLogisticsTerminal == null
            || checkpoints.isEmpty()
            || !LOGISTICS_TRAVEL_ACTIONS.contains(
                checkpoints.getFirst().getRight().getLeft()
            )) {
            error("Cannot safely recover logistics travel because its destination is missing.");
            toggle();
            return true;
        }
        Pair<Vec3d, Pair<String, BlockPos>> exposedTerminal =
            checkpoints.getFirst();
        LogisticsTerminal currentTerminal = new LogisticsTerminal(
            exposedTerminal.getRight().getLeft(),
            exposedTerminal.getLeft().x,
            exposedTerminal.getLeft().y,
            exposedTerminal.getLeft().z,
            exposedTerminal.getRight().getRight()
        );
        if (!activeLogisticsTerminal.equals(currentTerminal)) {
            error("The logistics destination changed while planning a bypass.");
            toggle();
            return true;
        }

        if (logisticsDetourAttempts >= MAX_LOGISTICS_DETOUR_ATTEMPTS) {
            error(
                "Two local bypass attempts were exhausted while travelling to "
                    + readableLogisticsAction(activeLogisticsTerminal.action())
                    + ". Clear the route before resuming."
            );
            toggle();
            return true;
        }
        logisticsDetourAttempts++;
        logisticsDetourStandingY = mc.player.getY();
        int radius = logisticsDetourRadius.get();
        LogisticsDetourPlanner.Point start = currentLogisticsCell();
        net.minecraft.util.math.Box actualStartBox =
            mc.player.getBoundingBox().offset(
                0,
                logisticsDetourStandingY - mc.player.getY(),
                0
            );
        LogisticsDetourPlanner.Direction forward =
            logisticsDirectionToward(activeLogisticsTerminal);
        LogisticsDetourPlanner.Point terminal = new LogisticsDetourPlanner.Point(
            (int) Math.floor(activeLogisticsTerminal.x()),
            (int) Math.floor(activeLogisticsTerminal.z())
        );

        HashSet<LogisticsDetourPlanner.Point> passable = new HashSet<>();
        for (int x = start.x() - radius; x <= start.x() + radius; x++) {
            for (int z = start.z() - radius; z <= start.z() + radius; z++) {
                LogisticsDetourPlanner.Point candidate =
                    new LogisticsDetourPlanner.Point(x, z);
                if (isLogisticsCellWalkable(candidate, logisticsDetourStandingY)) {
                    passable.add(candidate);
                }
            }
        }
        // The occupied cell is a valid search origin even when the player's
        // current bounding box slightly overlaps the obstacle that stopped it.
        passable.add(start);
        // A confirmed stall blocks only the edge directly ahead. If that cell
        // is otherwise walkable, a side route may still safely enter it.
        LogisticsDetourPlanner.Point blockedForward = start.offset(forward);
        java.util.function.BiPredicate<
            LogisticsDetourPlanner.Point,
            LogisticsDetourPlanner.Point
        > traversableEdge = (from, to) -> {
            if (from.equals(start)) {
                return !to.equals(blockedForward)
                    && isLogisticsSweptBoxClear(
                        actualStartBox,
                        logisticsCellBox(to, logisticsDetourStandingY)
                    );
            }
            return isLogisticsTransitionWalkable(
                from,
                to,
                logisticsDetourStandingY
            );
        };

        int nodeCap = (radius * 2 + 1) * (radius * 2 + 1);
        Optional<LogisticsDetourPlanner.Plan> planned =
            LogisticsDetourPlanner.findBypass(
                passable,
                start,
                forward,
                terminal,
                traversableEdge,
                radius,
                nodeCap
            );
        boolean usingSidestep = false;
        String sidestepSide = "";
        int sidestepDistance = 0;
        if ((planned.isEmpty() || planned.get().waypoints().isEmpty())
            && !logisticsSidestepUsed) {
            Optional<LogisticsDetourPlanner.Plan> sidestep =
                LogisticsDetourPlanner.findSidestep(
                    passable,
                    start,
                    forward,
                    traversableEdge,
                    2
                );
            if (sidestep.isPresent()) {
                planned = sidestep;
                logisticsSidestepUsed = true;
                logisticsDetourAttempts = Math.min(
                    logisticsDetourAttempts,
                    MAX_LOGISTICS_DETOUR_ATTEMPTS - 1
                );
                usingSidestep = true;
                sidestepDistance = planned.get().path().size() - 1;
                LogisticsDetourPlanner.Point firstStep =
                    planned.get().path().get(1);
                sidestepSide = firstStep.equals(start.offset(forward.left()))
                    ? "left"
                    : "right";
            }
        }
        if (planned.isEmpty() || planned.get().waypoints().isEmpty()) {
            if (logisticsDetourAttempts < MAX_LOGISTICS_DETOUR_ATTEMPTS) {
                warning(
                    "Could not find a loaded fixed-height bypass after " + reason
                        + "; continuing straight once before retrying."
                );
                logisticsProgressWatchdog.observe(
                    activeLogisticsTerminal,
                    Math.hypot(
                        activeLogisticsTerminal.x() - mc.player.getX(),
                        activeLogisticsTerminal.z() - mc.player.getZ()
                    ),
                    false,
                    mc.player.isOnGround()
                );
                logisticsProgressWatchdog.startCooldown();
                return true;
            }
            error(
                "No safe bounded bypass was found after two attempts while travelling to "
                    + readableLogisticsAction(activeLogisticsTerminal.action())
                    + ". Clear the obstacle or widen the route."
            );
            toggle();
            return true;
        }

        List<LogisticsDetourPlanner.Point> waypoints = planned.get().waypoints();
        int supportY = (int) Math.floor(logisticsDetourStandingY - 0.01);
        for (int index = waypoints.size() - 1; index >= 0; index--) {
            LogisticsDetourPlanner.Point waypoint = waypoints.get(index);
            checkpoints.add(0, new Pair<>(
                new Vec3d(
                    waypoint.x() + 0.5,
                    logisticsDetourStandingY,
                    waypoint.z() + 0.5
                ),
                new Pair<>(
                    "logisticsDetour",
                    new BlockPos(waypoint.x(), supportY, waypoint.z())
                )
            ));
        }
        logisticsProgressWatchdog.reset();
        if (usingSidestep) {
            info(
                "No complete local bypass fit; stepping " + sidestepDistance
                    + (sidestepDistance == 1 ? " block " : " blocks ")
                    + sidestepSide + " before retrying "
                    + readableLogisticsAction(activeLogisticsTerminal.action()) + "."
            );
        } else {
            info(
                "Using a " + waypoints.size() + "-corner local bypass because "
                    + reason + "."
            );
        }
        return true;
    }

    private LogisticsDetourPlanner.Direction logisticsDirectionToward(
        LogisticsTerminal terminal
    ) {
        double dx = terminal.x() - mc.player.getX();
        double dz = terminal.z() - mc.player.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0
                ? LogisticsDetourPlanner.Direction.EAST
                : LogisticsDetourPlanner.Direction.WEST;
        }
        return dz >= 0
            ? LogisticsDetourPlanner.Direction.SOUTH
            : LogisticsDetourPlanner.Direction.NORTH;
    }

    private LogisticsDetourPlanner.Point currentLogisticsCell() {
        return new LogisticsDetourPlanner.Point(
            (int) Math.floor(mc.player.getX()),
            (int) Math.floor(mc.player.getZ())
        );
    }

    private boolean isLogisticsSegmentWalkable(Vec3d goal) {
        if (!Double.isFinite(logisticsDetourStandingY)
            || Math.abs(mc.player.getY() - logisticsDetourStandingY) > 0.2) {
            return false;
        }
        LogisticsDetourPlanner.Point start = currentLogisticsCell();
        LogisticsDetourPlanner.Point end = new LogisticsDetourPlanner.Point(
            (int) Math.floor(goal.x),
            (int) Math.floor(goal.z)
        );
        int dx = Integer.compare(end.x(), start.x());
        int dz = Integer.compare(end.z(), start.z());
        if (dx != 0 && dz != 0) return false;
        if (start.equals(end)) {
            return isLogisticsCellWalkable(end, logisticsDetourStandingY)
                && isLogisticsSweptBoxClear(
                    mc.player.getBoundingBox().offset(
                        0,
                        logisticsDetourStandingY - mc.player.getY(),
                        0
                    ),
                    logisticsCellBox(end, logisticsDetourStandingY)
                );
        }
        if (!isLogisticsSweptBoxClear(
            mc.player.getBoundingBox().offset(
                0,
                logisticsDetourStandingY - mc.player.getY(),
                0
            ),
            logisticsCellBox(end, logisticsDetourStandingY)
        )) {
            return false;
        }

        LogisticsDetourPlanner.Point current = start;
        while (!current.equals(end)) {
            current = new LogisticsDetourPlanner.Point(
                current.x() + dx,
                current.z() + dz
            );
            if (!isLogisticsCellWalkable(current, logisticsDetourStandingY)) {
                return false;
            }
        }
        return true;
    }

    private boolean isLogisticsCellWalkable(
        LogisticsDetourPlanner.Point cell,
        double standingY
    ) {
        if (mc.world == null || mc.player == null || isProtectedMapCell(cell)) {
            return false;
        }
        int chunkX = cell.x() >> 4;
        int chunkZ = cell.z() >> 4;
        if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }

        int supportY = (int) Math.floor(standingY - 0.01);
        BlockPos supportPos = new BlockPos(cell.x(), supportY, cell.z());
        BlockState support = mc.world.getBlockState(supportPos);
        if (!support.getFluidState().isEmpty()) return false;
        var supportShape = support.getCollisionShape(mc.world, supportPos);
        if (supportShape.isEmpty()) return false;
        double playerHalfWidth =
            (mc.player.getBoundingBox().maxX - mc.player.getBoundingBox().minX) / 2;
        double minimumFootprint = 0.5 - playerHalfWidth + 0.01;
        double maximumFootprint = 0.5 + playerHalfWidth - 0.01;
        double localStandingY = standingY - supportPos.getY();
        boolean fullFootprintSupported = supportShape.getBoundingBoxes().stream()
            .anyMatch(box ->
                box.minX <= minimumFootprint
                    && box.maxX >= maximumFootprint
                    && box.minZ <= minimumFootprint
                    && box.maxZ >= maximumFootprint
                    && Math.abs(box.maxY - localStandingY) <= 0.05
            );
        if (!fullFootprintSupported) return false;
        if (support.isOf(Blocks.MAGMA_BLOCK)
            || support.isOf(Blocks.CAMPFIRE)
            || support.isOf(Blocks.SOUL_CAMPFIRE)) {
            return false;
        }

        BlockPos feetPos = BlockPos.ofFloored(
            cell.x() + 0.5,
            standingY + 0.01,
            cell.z() + 0.5
        );
        int maximumOccupiedY = (int) Math.floor(
            standingY + mc.player.getHeight() - 0.001
        );
        for (int y = feetPos.getY(); y <= maximumOccupiedY; y++) {
            BlockState occupied = mc.world.getBlockState(
                new BlockPos(cell.x(), y, cell.z())
            );
            if (!occupied.getFluidState().isEmpty()
                || isLogisticsHazard(occupied)) {
                return false;
            }
        }

        var candidateBox = logisticsCellBox(cell, standingY);
        return mc.world.isSpaceEmpty(mc.player, candidateBox);
    }

    private boolean isLogisticsTransitionWalkable(
        LogisticsDetourPlanner.Point from,
        LogisticsDetourPlanner.Point to,
        double standingY
    ) {
        if (from.manhattanDistance(to) != 1) return false;
        return isLogisticsSweptBoxClear(
            logisticsCellBox(from, standingY),
            logisticsCellBox(to, standingY)
        );
    }

    private boolean isLogisticsSweptBoxClear(
        net.minecraft.util.math.Box from,
        net.minecraft.util.math.Box to
    ) {
        net.minecraft.util.math.Box swept = new net.minecraft.util.math.Box(
            Math.min(from.minX, to.minX),
            Math.min(from.minY, to.minY),
            Math.min(from.minZ, to.minZ),
            Math.max(from.maxX, to.maxX),
            Math.max(from.maxY, to.maxY),
            Math.max(from.maxZ, to.maxZ)
        );
        return mc.world.isSpaceEmpty(mc.player, swept);
    }

    private net.minecraft.util.math.Box logisticsCellBox(
        LogisticsDetourPlanner.Point cell,
        double standingY
    ) {
        return mc.player.getBoundingBox().offset(
            cell.x() + 0.5 - mc.player.getX(),
            standingY - mc.player.getY(),
            cell.z() + 0.5 - mc.player.getZ()
        );
    }

    private boolean isLogisticsHazard(BlockState state) {
        return state.isOf(Blocks.COBWEB)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE);
    }

    private boolean isProtectedMapCell(LogisticsDetourPlanner.Point cell) {
        if (mapCorner == null) return false;
        int relativeX = cell.x() - mapCorner.getX();
        int relativeZ = cell.z() - mapCorner.getZ();
        int maximumRelativeZ = compactPlan == null
            ? 127
            : compactPlan.sizeZ() - 2;
        return relativeX >= -1
            && relativeX <= 128
            && relativeZ >= -2
            && relativeZ <= maximumRelativeZ + 1;
    }

    private boolean isLogisticsDetourCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        return checkpoint != null
            && checkpoint.getRight() != null
            && "logisticsDetour".equals(checkpoint.getRight().getLeft());
    }

    private void removeLeadingLogisticsDetourCheckpoints() {
        if (checkpoints == null) return;
        while (!checkpoints.isEmpty()
            && isLogisticsDetourCheckpoint(checkpoints.getFirst())) {
            checkpoints.removeFirst();
        }
    }

    private void cancelLogisticsDetour() {
        removeLeadingLogisticsDetourCheckpoints();
        clearLogisticsTracking();
    }

    private void clearLogisticsTracking() {
        logisticsProgressWatchdog.reset();
        activeLogisticsTerminal = null;
        logisticsDetourStandingY = Double.NaN;
        logisticsDetourAttempts = 0;
        logisticsSidestepUsed = false;
    }

    private String readableLogisticsAction(String action) {
        return switch (action) {
            case "dump" -> "the dump station";
            case "refill" -> "a refill chest";
            case "sleep" -> "the bed";
            case "mapMaterialChest" -> "the map-material chest";
            case "cartographyTable" -> "the cartography table";
            case "finishedMapChest" -> "the finished-map chest";
            case "mapHandoffRecoveryProbe" ->
                "the map-handoff recovery probe chest";
            case "usedToolChest" -> "a used-tool chest";
            case "walkRestock" -> "the map access path";
            default -> "the logistics destination";
        };
    }

    private BlockPos supportBelowCheckpoint(Vec3d checkpoint) {
        return BlockPos.ofFloored(checkpoint.x, checkpoint.y - 0.01, checkpoint.z);
    }

    private boolean isInWorkingInterval(BlockPos relative) {
        return relative.getX() >= workingInterval.getLeft()
            && relative.getX() <= workingInterval.getRight();
    }

    private void resetMapAreaCache() {
        int maximumRelativeZ = compactPlan == null
            ? 127
            : compactPlan.sizeZ() - 2;
        MapAreaCache.reset(mapCorner, 0, 127, -1, maximumRelativeZ);
    }

    private boolean calculateIndependentMiningPath(int line) {
        checkpoints.clear();
        activeMiningLine = line;
        if (!isSafeNorthWalkway(line)) {
            error(
                "Line " + line + " has no safe cobblestone north-walkway entry at "
                    + northWalkwaySupport(line).toShortString() + "."
            );
            return false;
        }
        Vec3d cp1 = walkingPosition(northWalkwaySupport(line)).add(
            0,
            0,
            1.0 - mineLineEndOffset.get()
        );
        BlockPos entrySupport = supportBelowCheckpoint(cp1);
        if (MapAreaCache.getCachedBlockState(entrySupport).isAir()
            || !MapAreaCache.getCachedBlockState(entrySupport.up()).isAir()
            || !MapAreaCache.getCachedBlockState(entrySupport.up(2)).isAir()) {
            error(
                "Line " + line + " has no safe old-style mining entry at "
                    + entrySupport.toShortString() + "."
            );
            return false;
        }
        boolean foundGap = false;
        int lastWalkableZ = -1;
        for (int z = 0; z < map[line].length; z++) {
            BlockPos support = mapCorner.add(line, map[line][z].getRight(), z);
            BlockState supportState = MapAreaCache.getCachedBlockState(support);
            if (supportState.isAir()) {
                foundGap = true;
                continue;
            }
            if (foundGap
                || supportState.getBlock() != map[line][z].getLeft()
                || !MapAreaCache.getCachedBlockState(support.up()).isAir()
                || !MapAreaCache.getCachedBlockState(support.up(2)).isAir()) {
                error(
                    "Line " + line
                        + " is not a continuous, north-reachable old-style mining path at "
                        + support.toShortString() + "."
                );
                return false;
            }
            lastWalkableZ = z;
        }
        if (lastWalkableZ < 0) return false;

        int standingZ = Math.max(0, lastWalkableZ - 1);
        Vec3d cp2 = mapCorner.toCenterPos().add(
            line,
            map[line][standingZ].getRight() + 0.5,
            standingZ
        );
        checkpoints.add(new Pair<>(
            cp1,
            new Pair<>("verifyIndependentTools", new BlockPos(line, 0, 0))
        ));
        checkpoints.add(new Pair(cp2, new Pair("startMine", null)));
        checkpoints.add(new Pair(cp1, new Pair("miningLineEnd", null)));
        return true;
    }

    private CircularMiningRecoveryPlan.Result analyzeCircularMiningRoute(
        CompactCircularNbtPlan.PairRoute route
    ) {
        ArrayList<CircularMiningRecoveryPlan.Cell> cells = new ArrayList<>();
        for (BlockPos relative : circularPairTargets(route)) {
            BlockPos world = mapCorner.add(relative);
            BlockState state = MapAreaCache.getCachedBlockState(world);
            if (state.isAir()) {
                cells.add(CircularMiningRecoveryPlan.Cell.AIR);
                continue;
            }

            Block expected = buildTargets.get(relative);
            boolean expectedSupport = expected != null && state.getBlock() == expected;
            boolean clearHeadroom = MapAreaCache.getCachedBlockState(world.up()).isAir()
                && MapAreaCache.getCachedBlockState(world.up(2)).isAir();
            cells.add(
                expectedSupport && clearHeadroom
                    ? CircularMiningRecoveryPlan.Cell.WALKABLE
                    : CircularMiningRecoveryPlan.Cell.BLOCKED
            );
        }
        CircularMiningRecoveryPlan.Result recovery =
            CircularMiningRecoveryPlan.analyze(cells);
        boolean safeEndpoints = switch (recovery.mode()) {
            case COMPLETE, FALLBACK -> true;
            case FORWARD ->
                isSafeNorthWalkway(route.outboundX())
                    && isSafeNorthWalkway(route.returnX());
            case RECOVER_FROM_START -> isSafeNorthWalkway(route.outboundX());
            case RECOVER_FROM_END -> isSafeNorthWalkway(route.returnX());
        };
        return safeEndpoints
            ? recovery
            : new CircularMiningRecoveryPlan.Result(
                CircularMiningRecoveryPlan.Mode.FALLBACK,
                recovery.firstWalkable(),
                recovery.lastWalkable()
            );
    }

    private void refreshCircularMiningTraversalOptimization() {
        refreshCircularMiningTraversalOptimization(
            preferredRecoveredMiningPair
        );
    }

    private void refreshCircularMiningTraversalOptimization(
        int mandatoryLocalPair
    ) {
        optimizedCircularMiningTraversalPairs.clear();
        optimizedDeferredMiningTargets.clear();
        optimizedDeferredMiningRouteAssignments.clear();
        circularMiningOptimizationReady = false;
        if (!circularTraversalForCurrentMap
            || compactPlan == null
            || mapCorner == null
            || mc.player == null) {
            return;
        }
        if (!ensureCircularTeardownReachTopology()) {
            error(
                "The compiled circular teardown reach plan is unavailable; "
                    + "stopping before route ownership is assigned."
            );
            return;
        }

        ArrayList<
            ReachOptimizedTeardownPlan.Route<BlockPos>
        > routes = new ArrayList<>();
        for (CompactCircularNbtPlan.PairRoute route
            : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }
            CircularMiningRecoveryPlan.Result recovery =
                analyzeCircularMiningRoute(route);
            ArrayList<BlockPos> remaining = new ArrayList<>();
            for (BlockPos relative : circularPairTargets(route)) {
                if (!MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                ).isAir()) {
                    remaining.add(relative);
                }
            }
            boolean preferredLocalRoute =
                route.pairIndex()
                    == mandatoryLocalPair;
            CircularTeardownRouteEligibility.Result eligibility =
                CircularTeardownRouteEligibility.classify(
                    recovery.mode(),
                    preferredLocalRoute
                );
            List<BlockPos> safeRemoteOrder =
                CircularRemoteTeardownOrder.create(
                    remaining,
                    recovery.mode()
                );
            routes.add(
                new ReachOptimizedTeardownPlan.Route<>(
                    route.pairIndex(),
                    eligibility.complete()
                        ? List.of()
                        : safeRemoteOrder,
                    eligibility.mustTraverse(),
                    eligibility.canHostRemoteTeardown()
                )
            );
        }

        // Remote pair ownership is local to one bot. Until the assignment
        // protocol carries those extra owned pairs, coordinated mining keeps
        // the original one-pair-per-bot traversal contract.
        if (SlaveSystem.isSlave()
            || !SlaveSystem.slaves.isEmpty()) {
            for (ReachOptimizedTeardownPlan.Route<BlockPos> route
                : routes) {
                if (!route.complete()) {
                    optimizedCircularMiningTraversalPairs.add(
                        route.routeIndex()
                    );
                }
            }
            circularMiningOptimizationReady = true;
            debugLog(
                "TeardownPlan",
                "cross-U optimization disabled for coordinated "
                    + "multi-bot mining; selected="
                    + optimizedCircularMiningTraversalPairs
            );
            return;
        }

        if (useCompiledIntactCircularTeardownPlan(
            routes,
            mandatoryLocalPair
        )) {
            return;
        }

        ReachOptimizedTeardownPlan.Plan<BlockPos> plan =
            ReachOptimizedTeardownPlan.create(
                routes,
                (sourceTargets, destinationPair) ->
                    cachedTeardownReachSchedule(
                        sourceTargets,
                        destinationPair
                    )
            );
        optimizedCircularMiningTraversalPairs.addAll(
            plan.traversalRouteIndices()
        );
        plan.scheduledTargetsByTraversal().forEach(
            (pair, targets) ->
                optimizedDeferredMiningTargets.put(
                    pair,
                    List.copyOf(targets)
                )
        );
        optimizedDeferredMiningRouteAssignments.putAll(
            plan.routeAssignments()
        );
        circularMiningOptimizationReady = true;

        int remoteTargets =
            optimizedDeferredMiningTargets.values().stream()
                .mapToInt(List::size)
                .sum();
        debugLog(
            "TeardownPlan",
            "walking "
                + optimizedCircularMiningTraversalPairs.size()
                + " circular U routes, skipping "
                + plan.completedRouteIndices().size()
                + " already clear routes, and assigning "
                + optimizedDeferredMiningRouteAssignments.size()
                + " fully reachable U remainders (" + remoteTargets
                + " blocks) to other traversals in endpoint-safe "
                + "removal order."
        );
        debugLog(
            "TeardownPlan",
            "selected=" + plan.traversalRouteIndices()
                + " completed=" + plan.completedRouteIndices()
                + " remoteAssignments="
                    + plan.routeAssignments()
        );
    }

    private boolean useCompiledIntactCircularTeardownPlan(
        List<ReachOptimizedTeardownPlan.Route<BlockPos>> routes,
        int mandatoryLocalPair
    ) {
        if (circularTeardownReachTopology == null
            || mandatoryLocalPair >= 0
            || routes.size()
                != circularTeardownReachTopology.targetCounts().size()) {
            return false;
        }
        for (ReachOptimizedTeardownPlan.Route<BlockPos> route : routes) {
            int routeIndex = route.routeIndex();
            if (routeIndex < 0
                || routeIndex
                    >= circularTeardownReachTopology.targetCounts().size()
                || route.mustTraverse()
                || !route.canHostRemoteTeardown()
                || route.orderedTargets().size()
                    != circularTeardownReachTopology.targetCounts().get(
                        routeIndex
                    )) {
                return false;
            }
        }

        HashMap<Integer, ReachOptimizedTeardownPlan.Route<BlockPos>>
            routeByIndex = new HashMap<>();
        routes.forEach(route ->
            routeByIndex.put(route.routeIndex(), route)
        );
        optimizedCircularMiningTraversalPairs.addAll(
            circularTeardownReachTopology.fullMapTraversalRoutes()
        );
        for (CircularTeardownReachTopology.RouteAssignment assignment
             : circularTeardownReachTopology
                .fullMapRouteAssignments()) {
            ReachOptimizedTeardownPlan.Route<BlockPos> source =
                routeByIndex.get(assignment.sourceRouteIndex());
            CircularTeardownReachTopology.Relation relation =
                circularTeardownReachTopology.relation(
                    assignment.sourceRouteIndex(),
                    assignment.destinationRouteIndex()
                ).orElseThrow(() -> new IllegalStateException(
                    "The compiled intact teardown assignment lost its "
                        + "reach relation."
                ));
            if (!relation.preserveStartFullyReachable()
                || relation.preserveStartDestinationSupports().size()
                    != source.orderedTargets().size()) {
                throw new IllegalStateException(
                    "The compiled intact teardown assignment is incomplete."
                );
            }
            ArrayList<
                ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
            > scheduled = new ArrayList<>(source.orderedTargets().size());
            for (int targetIndex = 0;
                 targetIndex < source.orderedTargets().size();
                 targetIndex++) {
                scheduled.add(
                    new ReachOptimizedTeardownPlan.ScheduledTarget<>(
                        assignment.sourceRouteIndex(),
                        source.orderedTargets().get(targetIndex),
                        relation.preserveStartDestinationSupports().get(
                            targetIndex
                        ),
                        targetIndex
                    )
                );
            }
            optimizedDeferredMiningTargets.computeIfAbsent(
                assignment.destinationRouteIndex(),
                ignored -> new ArrayList<>()
            ).addAll(scheduled);
            optimizedDeferredMiningRouteAssignments.put(
                assignment.sourceRouteIndex(),
                assignment.destinationRouteIndex()
            );
        }
        optimizedDeferredMiningTargets.replaceAll((pair, targets) -> {
            ArrayList<
                ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
            > ordered = new ArrayList<>(targets);
            ordered.sort((left, right) -> {
                int support = Integer.compare(
                    left.destinationSupportIndex(),
                    right.destinationSupportIndex()
                );
                if (support != 0) return support;
                int source = Integer.compare(
                    left.sourceRouteIndex(),
                    right.sourceRouteIndex()
                );
                if (source != 0) return source;
                return Integer.compare(
                    left.sourceTargetIndex(),
                    right.sourceTargetIndex()
                );
            });
            return List.copyOf(ordered);
        });
        circularMiningOptimizationReady = true;
        debugLog(
            "TeardownPlan",
            "using persisted intact-map topology file="
                + (circularTeardownReachTopologyFile == null
                    ? "unknown"
                    : circularTeardownReachTopologyFile.getFileName())
                + " selected="
                + circularTeardownReachTopology.fullMapTraversalRoutes()
                + " remoteAssignments="
                + optimizedDeferredMiningRouteAssignments
        );
        return true;
    }

    private Optional<List<Integer>>
        cachedTeardownReachSchedule(
            List<BlockPos> orderedSourceTargets,
            int destinationPair
        ) {
        if (circularTeardownReachTopology == null
            || orderedSourceTargets.isEmpty()) {
            return Optional.empty();
        }
        int sourcePair = -1;
        ArrayList<Integer> sourceTargetIndices = new ArrayList<>(
            orderedSourceTargets.size()
        );
        for (BlockPos relativeTarget : orderedSourceTargets) {
            CircularTeardownTargetReference reference =
                circularTeardownTargetReferences.get(relativeTarget);
            if (reference == null) {
                throw new IllegalStateException(
                    "The compiled teardown topology cannot identify target "
                        + relativeTarget.toShortString() + "."
                );
            }
            if (sourcePair < 0) {
                sourcePair = reference.pairIndex();
            } else if (sourcePair != reference.pairIndex()) {
                throw new IllegalArgumentException(
                    "A remote teardown schedule cannot mix source U routes."
                );
            }
            sourceTargetIndices.add(reference.targetIndex());
        }
        return circularTeardownReachTopology.monotonicSchedule(
            sourcePair,
            sourceTargetIndices,
            destinationPair
        );
    }

    private List<
        ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
    > remoteMiningTargets(
        CompactCircularNbtPlan.PairRoute route
    ) {
        if (!circularMiningOptimizationReady) return List.of();
        return optimizedDeferredMiningTargets.getOrDefault(
            route.pairIndex(),
            List.of()
        );
    }

    private ArrayList<BlockPos> circularMiningInventoryTargets(
        CompactCircularNbtPlan.PairRoute route
    ) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>(
            circularPairTargets(route)
        );
        if (analyzeCircularMiningRoute(route).mode()
            == CircularMiningRecoveryPlan.Mode.FORWARD) {
            for (ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
                scheduled : remoteMiningTargets(route)) {
                targets.add(scheduled.target());
            }
        }
        return new ArrayList<>(targets);
    }

    private Optional<CircularMiningLocalSupport>
        circularMiningLocalSupport() {
        if (!circularTraversalForCurrentMap
            || compactPlan == null
            || mapCorner == null
            || mc.player == null
            || !mc.player.isOnGround()) {
            return Optional.empty();
        }
        BlockPos support =
            supportBelowCheckpoint(mc.player.getEntityPos());
        if (!isPlayerStandingOnSupport(support)
            || !isSafeUCheckpointSupport(
                walkingPosition(support)
            )) {
            return Optional.empty();
        }
        BlockPos relative = support.subtract(mapCorner);
        for (CompactCircularNbtPlan.PairRoute route
            : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }
            ArrayList<BlockPos> targets =
                circularPairTargets(route);
            int targetIndex = targets.indexOf(relative);
            if (targetIndex < 0) continue;

            CircularMiningRecoveryPlan.Result recovery =
                analyzeCircularMiningRoute(route);
            if (CircularMiningLocalResumePlan.create(
                targets.size(),
                recovery,
                targetIndex
            ).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(
                new CircularMiningLocalSupport(
                    route.pairIndex(),
                    targetIndex,
                    support
                )
            );
        }
        return Optional.empty();
    }

    private Optional<CircularMiningLocalSupport>
        circularMiningLocalSupport(
            CompactCircularNbtPlan.PairRoute route
        ) {
        return circularMiningLocalSupport().filter(
            local -> local.pairIndex() == route.pairIndex()
        );
    }

    private DurableTeardownRecoveryCursor.Cursor teardownRecoveryCursor(
        CircularMiningLocalSupport support
    ) {
        if (support == null) return null;
        return new DurableTeardownRecoveryCursor.Cursor(
            support.pairIndex(),
            support.targetIndex()
        );
    }

    private DurableTeardownRecoveryCursor.Cursor
        activeOrderedTeardownRecoveryCursor() {
        if (state != State.MiningUTraversal
            || !activeContinuousTeardownArmed
            || teardownScaffoldPhase != TeardownScaffoldPhase.NONE
            || compactPlan == null
            || mapCorner == null
            || activeContinuousTeardownPair < 0
            || activeContinuousTeardownPair
                >= compactPlan.pairRoutes().size()
            || activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex
                >= activeContinuousTeardownStages.size()) {
            return null;
        }

        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(
                activeContinuousTeardownPair
            );
        BlockPos support = activeContinuousTeardownStages.get(
            activeCircularRouteSupportIndex
        ).support();
        int targetIndex = circularPairTargets(route).indexOf(
            support.subtract(mapCorner)
        );
        if (targetIndex < 0
            || !isAuthoritativeRemainingTeardownSupport(
                route,
                targetIndex
            )) {
            return null;
        }
        return new DurableTeardownRecoveryCursor.Cursor(
            route.pairIndex(),
            targetIndex
        );
    }

    private void rememberConfirmedTeardownSupport(
        ActiveOrderedUTraversal traversal,
        BlockPos support
    ) {
        if (traversal == null
            || traversal.owner() != OrderedUTraversalOwner.TEARDOWN
            || mapCorner == null) {
            return;
        }
        int targetIndex = circularPairTargets(
            traversal.route()
        ).indexOf(support.subtract(mapCorner));
        if (targetIndex < 0) return;
        retainedTeardownRecoveryCursor =
            new DurableTeardownRecoveryCursor.Cursor(
                traversal.route().pairIndex(),
                targetIndex
            );
    }

    private boolean isAuthoritativeRemainingTeardownSupport(
        CompactCircularNbtPlan.PairRoute route,
        int targetIndex
    ) {
        if (route == null || mapCorner == null) return false;
        ArrayList<BlockPos> targets = circularPairTargets(route);
        if (targetIndex < 0 || targetIndex >= targets.size()) {
            return false;
        }
        BlockPos support = mapCorner.add(targets.get(targetIndex));
        if (!isSafeUCheckpointSupport(walkingPosition(support))) {
            return false;
        }
        return CircularMiningLocalResumePlan.create(
            targets.size(),
            analyzeCircularMiningRoute(route),
            targetIndex
        ).isPresent();
    }

    private Optional<DurableTeardownRecoveryCursor.Cursor>
        validateTeardownRecoveryCursor(
            DurableTeardownRecoveryCursor.Cursor cursor
        ) {
        if (cursor == null
            || compactPlan == null
            || workingInterval == null
            || cursor.pairIndex() < 0
            || cursor.pairIndex()
                >= compactPlan.pairRoutes().size()) {
            return Optional.empty();
        }
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(cursor.pairIndex());
        if (route.outboundX() < workingInterval.getLeft()
            || route.returnX() > workingInterval.getRight()) {
            return Optional.empty();
        }
        int targetCount = circularPairTargets(route).size();
        return DurableTeardownRecoveryCursor.validateForRecovery(
            cursor,
            route.pairIndex(),
            targetCount,
            targetIndex -> isAuthoritativeRemainingTeardownSupport(
                route,
                targetIndex
            )
        );
    }

    private boolean isRecoverablePersistedMiningPair(int pairIndex) {
        if (compactPlan == null
            || workingInterval == null
            || pairIndex < 0
            || pairIndex >= compactPlan.pairRoutes().size()) {
            return false;
        }
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(pairIndex);
        if (route.outboundX() < workingInterval.getLeft()
            || route.returnX() > workingInterval.getRight()) {
            return false;
        }
        CircularMiningRecoveryPlan.Mode mode =
            analyzeCircularMiningRoute(route).mode();
        return mode != CircularMiningRecoveryPlan.Mode.COMPLETE
            && mode != CircularMiningRecoveryPlan.Mode.FALLBACK;
    }

    private boolean isSafeNorthWalkway(int x) {
        if (northWalkwayRelativeY == null
            || x < 0
            || x >= CompactCircularNbtPlan.MAP_WIDTH) {
            return false;
        }
        BlockPos walkway = northWalkwaySupport(x);
        return MapAreaCache.getCachedBlockState(walkway).getBlock() == Blocks.COBBLESTONE
            && MapAreaCache.getCachedBlockState(walkway.up()).isAir()
            && MapAreaCache.getCachedBlockState(walkway.up(2)).isAir()
            && Math.abs(
                compactPlan.targetSurfaceY(x, 1) - northWalkwayRelativeY
            ) <= 1;
    }

    private boolean isSafeUCheckpointSupport(Vec3d checkpoint) {
        BlockPos support = BlockPos.ofFloored(
            checkpoint.x,
            checkpoint.y - 0.01,
            checkpoint.z
        );
        BlockPos relative = support.subtract(mapCorner);
        BlockState state = MapAreaCache.getCachedBlockState(support);
        Block expected = buildTargets.get(relative);
        boolean expectedSupport = expected == null
            ? relative.getZ() == -1
                && northWalkwayRelativeY != null
                && relative.getY() == northWalkwayRelativeY
                && relative.getX() >= 0
                && relative.getX() < map.length
                && state.getBlock() == Blocks.COBBLESTONE
            : state.getBlock() == expected;
        return expectedSupport
            && MapAreaCache.getCachedBlockState(support.up()).isAir()
            && MapAreaCache.getCachedBlockState(support.up(2)).isAir();
    }

    private boolean isSafeUCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        if (isSafeUCheckpointSupport(checkpoint.getLeft())) {
            return true;
        }
        Pair<String, BlockPos> action = checkpoint.getRight();
        if ((!action.getLeft().equals("verifyUTools")
                && !action.getLeft().equals("resumeUTools")
                && !action.getLeft().equals(
                    "verifyTeardownScaffold"
                )
                && !action.getLeft().equals(
                    "resumeTeardownScaffold"
                ))
            || action.getRight() == null) {
            return false;
        }
        int pairIndex = action.getRight().getX();
        if (compactPlan == null
            || pairIndex < 0
            || pairIndex >= compactPlan.pairRoutes().size()) {
            return false;
        }
        BlockPos requiredSupport = supportBelowCheckpoint(
            checkpoint.getLeft()
        );
        if (activeContinuousTeardownStages.isEmpty()
            || !requiredSupport.equals(
                activeContinuousTeardownStages.getFirst().support()
            )) {
            return false;
        }
        BlockState state = MapAreaCache.getCachedBlockState(
            requiredSupport
        );
        return mc.world != null
            && !state.isAir()
            && state.isSolidBlock(mc.world, requiredSupport)
            && MapAreaCache.getCachedBlockState(
                requiredSupport.up()
            ).isAir()
            && MapAreaCache.getCachedBlockState(
                requiredSupport.up(2)
            ).isAir();
    }

    private boolean isUTraversalCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        String action = checkpoint.getRight().getLeft();
        return action.isEmpty()
            || action.equals("verifyUTools")
            || action.equals("resumeUTools")
            || action.equals("verifyTeardownScaffold")
            || action.equals("resumeTeardownScaffold")
            || action.equals("uMiningRecoveryExit")
            || action.equals("uMiningTaskEnd")
            || action.equals("teardownScaffoldTaskEnd");
    }

    private boolean startTeardownScaffoldRecovery() {
        if (!circularTraversalForCurrentMap
            || compactPlan == null
            || mapCorner == null
            || mc.player == null
            || mc.world == null) {
            return false;
        }

        int maximumScaffoldBlocks = Math.multiplyExact(
            teardownScaffoldStacks.get(),
            64
        );
        BlockPos playerSupport = mc.player.isOnGround()
            ? supportBelowCheckpoint(mc.player.getEntityPos())
            : null;
        TeardownScaffoldRecoveryCandidate firstCandidate = null;
        TeardownScaffoldRecoveryCandidate localCandidate = null;
        for (CompactCircularNbtPlan.PairRoute route
            : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }
            ArrayList<BlockPos> relativeTargets =
                circularPairTargets(route);
            ArrayList<TeardownScaffoldPlan.Cell> cells =
                new ArrayList<>(relativeTargets.size());
            HashMap<Integer, Block> ownedBlocks = new HashMap<>();
            for (int index = 0;
                 index < relativeTargets.size();
                 index++) {
                BlockPos relative = relativeTargets.get(index);
                BlockPos world = mapCorner.add(relative);
                BlockState state =
                    MapAreaCache.getCachedBlockState(world);
                boolean clearHeadroom =
                    MapAreaCache.getCachedBlockState(world.up()).isAir()
                        && MapAreaCache.getCachedBlockState(
                            world.up(2)
                        ).isAir();
                if (!clearHeadroom) {
                    cells.add(TeardownScaffoldPlan.Cell.BLOCKED);
                    continue;
                }
                if (state.isAir()) {
                    cells.add(TeardownScaffoldPlan.Cell.AIR);
                    continue;
                }
                Block expected = buildTargets.get(relative);
                boolean owned = expected != null
                    && (state.getBlock() == expected
                        || state.getBlock() == Blocks.COBBLESTONE)
                    && state.isSolidBlock(mc.world, world);
                if (!owned) {
                    cells.add(TeardownScaffoldPlan.Cell.BLOCKED);
                    continue;
                }
                cells.add(TeardownScaffoldPlan.Cell.OWNED);
                ownedBlocks.put(index, state.getBlock());
            }

            Optional<TeardownScaffoldPlan.Plan> planned =
                TeardownScaffoldPlan.create(
                    cells,
                    maximumScaffoldBlocks
                );
            if (planned.isEmpty()) continue;
            TeardownScaffoldRecoveryCandidate candidate =
                new TeardownScaffoldRecoveryCandidate(
                    route,
                    relativeTargets,
                    cells,
                    ownedBlocks,
                    planned.orElseThrow()
                );
            if (firstCandidate == null) {
                firstCandidate = candidate;
            }
            if (playerSupport != null
                && teardownScaffoldCandidateContainsSupport(
                    candidate,
                    playerSupport
                )) {
                localCandidate = candidate;
                break;
            }
        }
        TeardownScaffoldRecoveryCandidate selected =
            localCandidate != null ? localCandidate : firstCandidate;
        if (selected == null) return false;
        return beginTeardownScaffoldRecovery(
            selected.route(),
            selected.relativeTargets(),
            selected.cells(),
            selected.ownedBlocks(),
            selected.plan()
        );
    }

    private boolean teardownScaffoldCandidateContainsSupport(
        TeardownScaffoldRecoveryCandidate candidate,
        BlockPos support
    ) {
        for (int index
            : candidate.plan().outwardSupportIndices()) {
            if (mapCorner.add(
                    candidate.relativeTargets().get(index)
                ).equals(support)) {
                return true;
            }
        }
        return mapCorner.add(
            candidate.relativeTargets().get(
                candidate.plan().terminalCleanupIndex()
            )
        ).equals(support);
    }

    private boolean beginTeardownScaffoldRecovery(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> relativeTargets,
        List<TeardownScaffoldPlan.Cell> cells,
        Map<Integer, Block> ownedBlocks,
        TeardownScaffoldPlan.Plan plan
    ) {
        resetTeardownMiningActionState();
        plannedTeardownHotbarAssignments.clear();
        checkpoints.clear();

        ArrayList<BlockPos> outwardWorld = new ArrayList<>();
        LinkedHashSet<BlockPos> scaffoldWorld =
            new LinkedHashSet<>();
        LinkedHashMap<BlockPos, Block> breakExpectations =
            new LinkedHashMap<>();
        HashMap<BlockPos, BlockState> plannedToolStates =
            new HashMap<>();
        for (int index : plan.outwardSupportIndices()) {
            BlockPos relative = relativeTargets.get(index);
            BlockPos world = mapCorner.add(relative);
            outwardWorld.add(world);
            Block expected;
            if (cells.get(index) == TeardownScaffoldPlan.Cell.AIR) {
                expected = Blocks.COBBLESTONE;
                scaffoldWorld.add(world);
            } else {
                expected = ownedBlocks.get(index);
            }
            breakExpectations.put(world, expected);
            plannedToolStates.put(
                relative,
                expected.getDefaultState()
            );
        }
        int terminalIndex = plan.terminalCleanupIndex();
        BlockPos terminalRelative = relativeTargets.get(terminalIndex);
        BlockPos terminalWorld = mapCorner.add(terminalRelative);
        Block terminalBlock = ownedBlocks.get(terminalIndex);
        if (terminalBlock == null) {
            error(
                "Sparse teardown scaffold planner lost terminal block "
                    + terminalWorld.toShortString() + "."
            );
            toggle();
            return true;
        }
        breakExpectations.put(terminalWorld, terminalBlock);
        plannedToolStates.put(
            terminalRelative,
            terminalBlock.getDefaultState()
        );

        int endpointX =
            plan.endpoint() == TeardownScaffoldPlan.Endpoint.START
                ? route.outboundX()
                : route.returnX();
        BlockPos endpoint = northWalkwaySupport(endpointX);
        BlockPos firstRouteSupport = mapCorner.add(
            relativeTargets.get(
                plan.endpoint()
                        == TeardownScaffoldPlan.Endpoint.START
                    ? 0
                    : relativeTargets.size() - 1
            )
        );
        BlockPos approach =
            OrderedUTraversalMovement.entryApproachSupport(
                endpoint,
                firstRouteSupport
            );
        activeTeardownScaffoldRecovery =
            new ActiveTeardownScaffoldRecovery(
                route.pairIndex(),
                plan.endpoint(),
                approach,
                endpoint,
                outwardWorld,
                terminalWorld,
                new ArrayList<>(scaffoldWorld),
                breakExpectations,
                plannedToolStates
            );
        BlockPos playerSupport = mc.player.isOnGround()
            ? supportBelowCheckpoint(mc.player.getEntityPos())
            : null;
        int localOutwardIndex = playerSupport == null
            ? -1
            : outwardWorld.indexOf(playerSupport);
        boolean standingOnTerminal = playerSupport != null
            && playerSupport.equals(terminalWorld);
        boolean locallyOnRecoveryPath =
            localOutwardIndex >= 0 || standingOnTerminal;
        if (locallyOnRecoveryPath) {
            HashMap<Item, Integer> missingLocalTools =
                missingOperationalMiningTools(
                    plannedToolStates.keySet(),
                    plannedToolStates
                );
            if (missingLocalTools == null) {
                resetTeardownMiningActionState();
                if (isActive()) toggle();
                return true;
            }
            if (!missingLocalTools.isEmpty()
                || !hasCompleteTeardownScaffoldReserve()) {
                teardownScaffoldPhase =
                    TeardownScaffoldPhase.EGRESS_TO_ENDPOINT;
                activateTeardownScaffoldEgressStages(
                    localOutwardIndex,
                    standingOnTerminal
                );
                state = State.MiningUTraversal;
                info(
                    "Resumed on a sparse teardown scaffold without its "
                        + "complete tool/material reserve; walking the "
                        + "intact scaffold back to the safe north endpoint "
                        + "before restocking."
                );
                debugLog(
                    "TeardownScaffold",
                    "activated safe egress pair=" + route.pairIndex()
                        + " localOutwardIndex=" + localOutwardIndex
                        + " standingOnTerminal=" + standingOnTerminal
                        + " missingTools=" + missingLocalTools
                        + " reserveComplete="
                            + hasCompleteTeardownScaffoldReserve()
                );
                return true;
            }
        }

        if (standingOnTerminal) {
            teardownScaffoldPhase =
                TeardownScaffoldPhase.CLEANING_RETURN;
            activateTeardownScaffoldCleanupStages(true);
        } else {
            teardownScaffoldPhase =
                TeardownScaffoldPhase.BUILDING_OUTBOUND;
            activateTeardownScaffoldOutboundStages(
                localOutwardIndex
            );
        }
        activeContinuousTeardownArmed = false;

        BlockPos verificationSupport = locallyOnRecoveryPath
            ? playerSupport
            : approach;
        checkpoints.add(new Pair<>(
            walkingPosition(verificationSupport),
            new Pair<>(
                locallyOnRecoveryPath
                    ? "resumeTeardownScaffold"
                    : "verifyTeardownScaffold",
                new BlockPos(route.pairIndex(), 0, 0)
            )
        ));
        checkpoints.add(new Pair<>(
            walkingPosition(endpoint),
            new Pair<>(
                "teardownScaffoldTaskEnd",
                new BlockPos(route.pairIndex(), 0, 0)
            )
        ));
        if (!ensureMiningToolDurability(
            plannedToolStates.keySet(),
            plannedToolStates,
            "sparse teardown scaffold for pair "
                + route.pairIndex()
        )) {
            resetTeardownMiningActionState();
            if (isActive()) toggle();
            return true;
        }
        state = State.MiningUTraversal;
        info(
            "Sparse teardown recovery found "
                + plan.ownedCleanupCount()
                + " server-missed block(s) in pair "
                + route.pairIndex()
                + (locallyOnRecoveryPath
                    ? "; resuming from the current scaffold support"
                    : "; entering from the closest "
                        + plan.endpoint().name()
                            .toLowerCase(Locale.ROOT)
                        + " endpoint")
                + " with " + scaffoldWorld.size()
                + " temporary cobblestone supports."
        );
        debugLog(
            "TeardownScaffold",
            "planned pair=" + route.pairIndex()
                + " endpoint=" + plan.endpoint()
                + " outwardSupports=" + outwardWorld.size()
                + " scaffoldBlocks=" + scaffoldWorld.size()
                + " cleanupTargets=" + breakExpectations.size()
                + " terminal=" + terminalWorld.toShortString()
        );
        return true;
    }

    private void activateTeardownScaffoldOutboundStages(
        int localOutwardIndex
    ) {
        ActiveTeardownScaffoldRecovery recovery =
            Objects.requireNonNull(
                activeTeardownScaffoldRecovery,
                "activeTeardownScaffoldRecovery"
            );
        ArrayList<ContinuousTeardownRoutePlan.Stage<BlockPos>> stages =
            new ArrayList<>(recovery.outwardSupports().size() + 2);
        if (localOutwardIndex < 0) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.entryApproach(),
                List.of()
            ));
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.entryEndpoint(),
                List.of()
            ));
        }
        for (int index = Math.max(0, localOutwardIndex);
             index < recovery.outwardSupports().size();
             index++) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.outwardSupports().get(index),
                List.of()
            ));
        }
        activeContinuousTeardownStages = List.copyOf(stages);
        activeContinuousTeardownPair = recovery.pairIndex();
        activeContinuousTeardownStageIndex = 0;
        activeCircularRouteSupportIndex = -1;
        activeContinuousTeardownRecoveryExit = false;
    }

    private void activateTeardownScaffoldEgressStages(
        int localOutwardIndex,
        boolean standingOnTerminal
    ) {
        ActiveTeardownScaffoldRecovery recovery =
            Objects.requireNonNull(
                activeTeardownScaffoldRecovery,
                "activeTeardownScaffoldRecovery"
            );
        if (localOutwardIndex < 0 && !standingOnTerminal) {
            throw new IllegalArgumentException(
                "Scaffold egress requires a local route support."
            );
        }
        ArrayList<ContinuousTeardownRoutePlan.Stage<BlockPos>> stages =
            new ArrayList<>(recovery.outwardSupports().size() + 2);
        if (standingOnTerminal) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.terminalCleanupTarget(),
                List.of()
            ));
            localOutwardIndex =
                recovery.outwardSupports().size() - 1;
        }
        for (int index = localOutwardIndex; index >= 0; index--) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.outwardSupports().get(index),
                List.of()
            ));
        }
        stages.add(new ContinuousTeardownRoutePlan.Stage<>(
            recovery.entryEndpoint(),
            List.of()
        ));
        activeContinuousTeardownStages = List.copyOf(stages);
        activeContinuousTeardownPair = recovery.pairIndex();
        activeContinuousTeardownStageIndex = 0;
        activeCircularRouteSupportIndex = 0;
        activeContinuousTeardownArmed = true;
        activeContinuousTeardownRecoveryExit = true;
        checkpoints.add(new Pair<>(
            walkingPosition(recovery.entryEndpoint()),
            new Pair<>(
                "uMiningRecoveryExit",
                new BlockPos(recovery.pairIndex(), 0, 0)
            )
        ));
    }

    private boolean isConfirmedTeardownScaffoldSupport(
        BlockPos support
    ) {
        ActiveTeardownScaffoldRecovery recovery =
            activeTeardownScaffoldRecovery;
        if (recovery == null || mc.world == null) return false;
        if (support.equals(recovery.entryEndpoint())) {
            int endpointX =
                recovery.endpoint()
                    == TeardownScaffoldPlan.Endpoint.START
                    ? compactPlan.pairRoutes()
                        .get(recovery.pairIndex()).outboundX()
                    : compactPlan.pairRoutes()
                        .get(recovery.pairIndex()).returnX();
            return isSafeNorthWalkway(endpointX);
        }
        if (support.equals(recovery.entryApproach())) {
            BlockState state =
                MapAreaCache.getCachedBlockState(support);
            return !state.isAir()
                && state.isSolidBlock(mc.world, support)
                && MapAreaCache.getCachedBlockState(
                    support.up()
                ).isAir()
                && MapAreaCache.getCachedBlockState(
                    support.up(2)
                ).isAir();
        }
        Block expected = recovery.breakExpectations().get(support);
        if (expected == null) return false;
        BlockState state = MapAreaCache.getCachedBlockState(support);
        return state.getBlock() == expected
            && state.isSolidBlock(mc.world, support)
            && MapAreaCache.getCachedBlockState(support.up()).isAir()
            && MapAreaCache.getCachedBlockState(support.up(2)).isAir();
    }

    private Block activeTeardownExpectedBlock(BlockPos target) {
        if (teardownScaffoldPhase
                == TeardownScaffoldPhase.CLEANING_RETURN
            && activeTeardownScaffoldRecovery != null) {
            return activeTeardownScaffoldRecovery
                .breakExpectations().get(target);
        }
        if (mapCorner == null) return null;
        return buildTargets.get(target.subtract(mapCorner));
    }

    private boolean calculateCircularMiningPath(
        CompactCircularNbtPlan.PairRoute route,
        boolean allowLocalResume
    ) {
        CircularMiningRecoveryPlan.Result recovery = analyzeCircularMiningRoute(route);
        if (recovery.mode() == CircularMiningRecoveryPlan.Mode.COMPLETE) {
            return false;
        }
        if (recovery.mode() == CircularMiningRecoveryPlan.Mode.FALLBACK) {
            return false;
        }

        ArrayList<BlockPos> targets = circularPairTargets(route);
        checkpoints.clear();
        activeContinuousTeardownStages = List.of();
        activeContinuousTeardownPair = -1;
        activeContinuousTeardownStageIndex = -1;
        activeCircularRouteSupportIndex = -1;
        activeContinuousTeardownArmed = false;
        activeContinuousTeardownRecoveryExit = false;
        activeMiningLine = -1;

        Optional<CircularMiningLocalSupport> localSupport =
            allowLocalResume
                ? circularMiningLocalSupport(route)
                : Optional.empty();
        Optional<CircularMiningLocalResumePlan.Plan> localPlan =
            localSupport.flatMap(
                local -> CircularMiningLocalResumePlan.create(
                    targets.size(),
                    recovery,
                    local.targetIndex()
                )
            );
        CircularMiningTraversalPlan.Plan endpointPlan = null;
        List<CircularMiningTraversalPlan.Step> traversalSteps;
        CircularMiningTraversalPlan.Endpoint exit;
        int finalRemoveIndex;
        if (localPlan.isPresent()) {
            CircularMiningLocalResumePlan.Plan plan =
                localPlan.orElseThrow();
            traversalSteps = plan.steps();
            exit = plan.exit();
            finalRemoveIndex = plan.finalRemoveIndex();
        } else {
            endpointPlan = CircularMiningTraversalPlan.create(
                targets.size(),
                recovery
            );
            traversalSteps = endpointPlan.steps();
            exit = endpointPlan.exit();
            finalRemoveIndex = endpointPlan.finalRemoveIndex();
        }
        HashMap<
            Integer,
            ArrayList<
                ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
            >
        > remoteTargetsBySupport = new HashMap<>();
        int remoteResumeSupportIndex =
            localPlan.isEmpty()
                ? 0
                : recovery.mode()
                    == CircularMiningRecoveryPlan.Mode.FORWARD
                        ? 0
                        : recovery.mode()
                            == CircularMiningRecoveryPlan.Mode
                                .RECOVER_FROM_END
                                ? recovery.firstWalkable()
                                : Integer.MAX_VALUE;
        if (remoteResumeSupportIndex != Integer.MAX_VALUE) {
            for (ReachOptimizedTeardownPlan.ScheduledTarget<BlockPos>
                scheduled : remoteMiningTargets(route)) {
                if (scheduled.destinationSupportIndex()
                    < remoteResumeSupportIndex) {
                    continue;
                }
                if (MapAreaCache.getCachedBlockState(
                    mapCorner.add(scheduled.target())
                ).isAir()) {
                    continue;
                }
                remoteTargetsBySupport.computeIfAbsent(
                    scheduled.destinationSupportIndex(),
                    ignored -> new ArrayList<>()
                ).add(scheduled);
            }
        }
        String entryDescription;
        List<BlockPos> entrySupports;
        if (localPlan.isPresent()) {
            CircularMiningLocalSupport local =
                localSupport.orElseThrow();
            entrySupports = List.of(local.support());
            checkpoints.add(new Pair<>(
                walkingPosition(local.support()),
                new Pair<>(
                    "resumeUTools",
                    new BlockPos(route.pairIndex(), 0, 0)
                )
            ));
            entryDescription =
                "verified local support index "
                    + local.targetIndex();
        } else {
            int entryX =
                endpointPlan.entry()
                    == CircularMiningTraversalPlan.Endpoint.START
                    ? route.outboundX()
                    : route.returnX();
            BlockPos entryEndpoint = northWalkwaySupport(entryX);
            BlockPos firstRouteSupport = mapCorner.add(
                targets.get(traversalSteps.getFirst().standIndex())
            );
            BlockPos entryApproach =
                OrderedUTraversalMovement.entryApproachSupport(
                    entryEndpoint,
                    firstRouteSupport
                );
            entrySupports = List.of(entryApproach, entryEndpoint);
            addCircularEntry(entryApproach, route.pairIndex());
            entryDescription =
                endpointPlan.entry().name()
                    .toLowerCase(Locale.ROOT)
                    + " endpoint";
        }
        HashMap<Integer, List<BlockPos>> remoteWorldTargetsBySupport =
            new HashMap<>();
        remoteTargetsBySupport.forEach(
            (supportIndex, scheduledTargets) ->
                remoteWorldTargetsBySupport.put(
                    supportIndex,
                    scheduledTargets.stream()
                        .map(ReachOptimizedTeardownPlan.ScheduledTarget::target)
                        .map(mapCorner::add)
                        .toList()
                )
        );
        int exitX =
            exit == CircularMiningTraversalPlan.Endpoint.START
                ? route.outboundX()
                : route.returnX();
        BlockPos exitSupport = northWalkwaySupport(exitX);
        List<BlockPos> worldTargets = targets.stream()
            .map(mapCorner::add)
            .toList();
        activeContinuousTeardownStages =
            ContinuousTeardownRoutePlan.create(
                worldTargets,
                traversalSteps,
                remoteWorldTargetsBySupport,
                remoteResumeSupportIndex,
                entrySupports,
                exitSupport,
                mapCorner.add(targets.get(finalRemoveIndex))
            ).stages();
        activeContinuousTeardownPair = route.pairIndex();
        activeContinuousTeardownStageIndex = 0;
        activeContinuousTeardownArmed = false;
        activeContinuousTeardownRecoveryExit = false;
        checkpoints.add(new Pair<>(
            walkingPosition(exitSupport),
            new Pair<>("uMiningTaskEnd", null)
        ));
        state = State.MiningUTraversal;
        info(
            "Mining pair " + route.pairIndex() + " with "
                + recovery.mode().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " U traversal from "
                + entryDescription
                + (remoteTargetsBySupport.isEmpty()
                    ? ""
                    : ", including "
                        + remoteTargetsBySupport.values().stream()
                            .mapToInt(List::size)
                            .sum()
                        + " reach-proven blocks from "
                        + remoteTargetsBySupport.values().stream()
                            .flatMap(Collection::stream)
                            .map(
                                ReachOptimizedTeardownPlan
                                    .ScheduledTarget::sourceRouteIndex
                            )
                            .distinct()
                            .count()
                        + " skippable U routes")
                + " using entry/exit checkpoints and "
                + activeContinuousTeardownStages.size()
                + " internal support stages"
        );
        return true;
    }

    private void addCircularEntry(
        BlockPos entryApproach,
        int pairIndex
    ) {
        checkpoints.add(new Pair<>(
            walkingPosition(entryApproach),
            new Pair<>("verifyUTools", new BlockPos(pairIndex, 0, 0))
        ));
    }

    private boolean calculateCircularMiningRecoveryEgress(
        CompactCircularNbtPlan.PairRoute route,
        CircularMiningLocalSupport local
    ) {
        CircularMiningRecoveryPlan.Result recovery =
            analyzeCircularMiningRoute(route);
        ArrayList<BlockPos> targets =
            circularPairTargets(route);
        Optional<CircularMiningLocalResumePlan.EgressPlan>
            egressPlan = CircularMiningLocalResumePlan.createEgress(
            targets.size(),
            recovery,
            local.targetIndex()
        );
        if (egressPlan.isEmpty()) return false;

        checkpoints.clear();
        activeContinuousTeardownStages = List.of();
        activeContinuousTeardownPair = -1;
        activeContinuousTeardownStageIndex = -1;
        activeCircularRouteSupportIndex = -1;
        activeContinuousTeardownArmed = false;
        activeContinuousTeardownRecoveryExit = false;
        activeMiningLine = -1;
        List<Integer> supportIndices =
            egressPlan.orElseThrow().supportIndices();
        int endpointX =
            egressPlan.orElseThrow().exit()
                == CircularMiningTraversalPlan.Endpoint.START
                ? route.outboundX()
                : route.returnX();
        BlockPos walkway = northWalkwaySupport(endpointX);
        ArrayList<
            ContinuousTeardownRoutePlan.Stage<BlockPos>
        > egressStages = new ArrayList<>(
            supportIndices.size() + 1
        );
        for (int supportIndex : supportIndices) {
            egressStages.add(
                new ContinuousTeardownRoutePlan.Stage<>(
                    mapCorner.add(targets.get(supportIndex)),
                    List.of()
                )
            );
        }
        egressStages.add(
            new ContinuousTeardownRoutePlan.Stage<>(
                walkway,
                List.of()
            )
        );
        activeContinuousTeardownStages =
            List.copyOf(egressStages);
        activeContinuousTeardownPair = route.pairIndex();
        activeContinuousTeardownStageIndex = 0;
        activeCircularRouteSupportIndex = 0;
        activeContinuousTeardownArmed = true;
        activeContinuousTeardownRecoveryExit = true;
        checkpoints.add(new Pair<>(
            walkingPosition(walkway),
            new Pair<>(
                "uMiningRecoveryExit",
                new BlockPos(route.pairIndex(), 0, 0)
            )
        ));
        state = State.MiningUTraversal;
        info(
            "Current U support is safe, but teardown tools require "
                + "restock; walking the intact route to its "
                + "safe north endpoint first."
        );
        debugLog(
            "Recovery",
            "planned tool-restock egress pair="
                + route.pairIndex()
                + " supportIndex=" + local.targetIndex()
                + " endpointX=" + endpointX
                + " structuralCheckpoints="
                    + checkpoints.size()
                + " internalStages="
                    + activeContinuousTeardownStages.size()
        );
        return true;
    }

    private boolean isUTraversalEndpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        String action = checkpoint.getRight().getLeft();
        return action.equals("verifyUTools")
            || action.equals("resumeUTools")
            || action.equals("verifyTeardownScaffold")
            || action.equals("resumeTeardownScaffold")
            || action.equals("uMiningRecoveryExit")
            || action.equals("uMiningTaskEnd")
            || action.equals("teardownScaffoldTaskEnd");
    }

    /**
     * Consumes support-entry events from the shared printing route cursor.
     * This method submits teardown work only; it never sets movement keys,
     * steering, sprint, jump, velocity, or route progress.
     */
    private boolean serviceContinuousTeardownWork() {
        if (activeContinuousTeardownStageIndex < 0
            || activeContinuousTeardownStageIndex
                > activeContinuousTeardownStages.size()
            || activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex
                >= activeContinuousTeardownStages.size()) {
            failTeardownMining(
                "Continuous U teardown lost its shared route cursor."
            );
            return false;
        }

        boolean pendingOwnedBreak =
            miningPos != null
                && teardownMineController.hasOwnedTarget();
        if (pendingOwnedBreak) {
            if (!ownedTeardownMayOverlapMovement()) {
                stopActiveOrderedUForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .OTHER_BUILD_ACTION
                );
                return false;
            }
            return true;
        }

        while (activeContinuousTeardownStageIndex
            <= activeCircularRouteSupportIndex) {
            ContinuousTeardownRoutePlan.Stage<BlockPos> stage =
                activeContinuousTeardownStages.get(
                    activeContinuousTeardownStageIndex
                );
            ArrayList<BlockPos> unresolvedTargets = new ArrayList<>();
            for (BlockPos target : stage.breakTargets()) {
                if (!MapAreaCache.getCachedBlockState(target).isAir()) {
                    unresolvedTargets.add(target);
                }
            }
            if (unresolvedTargets.isEmpty()) {
                activeContinuousTeardownStageIndex++;
                continue;
            }

            BlockPos nextTarget = unresolvedTargets.getFirst();
            OptionalInt requiredSupport =
                ContinuousTeardownRoutePlan
                    .requiredSupportIndexAtOrAfter(
                        activeContinuousTeardownStages,
                        activeCircularRouteSupportIndex,
                        nextTarget
                    );
            if (requiredSupport.isPresent()) {
                if (requiredSupport.getAsInt()
                    == activeCircularRouteSupportIndex) {
                    // The work cursor can lag behind the movement cursor while
                    // an earlier server acknowledgement is pending. Keep
                    // walking; this target becomes eligible only after it is
                    // physically behind the player.
                    return true;
                }
                failTeardownMining(
                    "Teardown work attempted to remove future route support "
                        + nextTarget.toShortString() + "."
                );
                return false;
            }
            if (isPlayerStandingOnTeardownTarget(nextTarget)) {
                // Authoritative underfoot safety is checked again at dispatch
                // time instead of trusting the earlier planning snapshot.
                return true;
            }
            if (!isBuildPlacementInReach(nextTarget)) {
                warning(
                    "Continuous U teardown work fell outside live reach at "
                        + nextTarget.toShortString()
                        + "; recovering from the next stable local U support."
                );
                beginMiningRecovery(false);
                return false;
            }

            TeardownBreakStatus breakStatus =
                beginContinuousTeardownBreak(
                    nextTarget
                );
            if (breakStatus == TeardownBreakStatus.FAILED) {
                return false;
            }
            if (breakStatus == TeardownBreakStatus.DEFERRED) {
                return true;
            }
            if (breakStatus == TeardownBreakStatus.CLEARED) {
                continue;
            }
            if (ownedTeardownMayOverlapMovement()) {
                if (unresolvedTargets.size() == 1) {
                    activeContinuousTeardownStageIndex++;
                }
                return true;
            }
            stopActiveOrderedUForAction(
                CircularBuildMovementPolicy.HoldReason
                    .OTHER_BUILD_ACTION
            );
            return false;
        }

        boolean routeComplete =
            activeCircularRouteSupportIndex
                == activeContinuousTeardownStages.size() - 1
                && activeContinuousTeardownStageIndex
                    == activeContinuousTeardownStages.size();
        if (!routeComplete) return true;
        if (miningPos != null
            || teardownMineController.hasOwnedTarget()) {
            stopActiveOrderedUForAction(
                CircularBuildMovementPolicy.HoldReason
                    .OTHER_BUILD_ACTION
            );
            return false;
        }

        if (teardownScaffoldPhase
            == TeardownScaffoldPhase.BUILDING_OUTBOUND) {
            if (!teardownScaffoldPlacementLedger.isEmpty()) {
                stopActiveOrderedUForAction(
                    CircularBuildMovementPolicy.HoldReason
                        .OTHER_BUILD_ACTION
                );
                return false;
            }
            beginTeardownScaffoldCleanupReturn();
            return false;
        }
        if (teardownScaffoldPhase
            == TeardownScaffoldPhase.CLEANING_RETURN) {
            completeTeardownScaffoldRecovery();
            return false;
        }

        activeContinuousTeardownArmed = false;
        activeContinuousTeardownPair = -1;
        activeCircularRouteSupportIndex = -1;
        stopCircularMiningMotion();
        if (activeContinuousTeardownRecoveryExit) {
            activeContinuousTeardownRecoveryExit = false;
            info(
                "Reached the safe U endpoint; replanning "
                    + "teardown and tool restock."
            );
            restartCurrentMiningAssignment();
            return false;
        }
        timeoutTicks = mineLineEndTimeout.get();
        completeCurrentMiningAssignment();
        return false;
    }

    private TeardownBreakStatus beginContinuousTeardownBreak(
        BlockPos target
    ) {
        if (isPlayerStandingOnTeardownTarget(target)) {
            return TeardownBreakStatus.DEFERRED;
        }
        if (!isBuildPlacementInReach(target)) {
            return TeardownBreakStatus.DEFERRED;
        }
        miningPos = new BlockPos(target);
        Block expected = activeTeardownExpectedBlock(miningPos);
        BlockState targetState =
            MapAreaCache.getCachedBlockState(miningPos);
        if (expected == null
            || (!targetState.isAir()
                && targetState.getBlock() != expected)) {
            error(
                "Continuous U teardown target changed unexpectedly at "
                    + miningPos.toShortString() + "."
            );
            toggle();
            return TeardownBreakStatus.FAILED;
        }
        if (targetState.isAir()) {
            miningPos = null;
            return TeardownBreakStatus.CLEARED;
        }

        state = State.AwaitUBlockBreak;
        teardownMovementOverlapAllowed = true;
        TeardownBreakStatus breakStatus =
            driveOrderedTeardownBreak(miningPos, expected);
        if (breakStatus == TeardownBreakStatus.CLEARED) {
            miningPos = null;
            teardownMovementOverlapAllowed = false;
            state = State.MiningUTraversal;
        } else if (breakStatus != TeardownBreakStatus.FAILED
            && ownedTeardownMayOverlapMovement()) {
            state = State.MiningUTraversal;
        } else if (breakStatus != TeardownBreakStatus.FAILED) {
            teardownMovementOverlapAllowed = false;
            jumpTimeout = 0;
            stopCircularMiningMotion();
        }
        return breakStatus;
    }

    private void beginTeardownScaffoldCleanupReturn() {
        ActiveTeardownScaffoldRecovery recovery =
            Objects.requireNonNull(
                activeTeardownScaffoldRecovery,
                "activeTeardownScaffoldRecovery"
            );
        teardownScaffoldPhase =
            TeardownScaffoldPhase.CLEANING_RETURN;
        teardownScaffoldPlacementLedger.reset();
        teardownScaffoldSubmissionBlockSequences.clear();
        activateTeardownScaffoldCleanupStages(false);
        activeContinuousTeardownArmed = true;
        state = State.MiningUTraversal;
        stopCircularMiningMotion();
        info(
            "Reached the sparse teardown target; returning to the "
                + "safe walkway while THM-clearing the missed blocks "
                + "and every temporary scaffold support."
        );
        debugLog(
            "TeardownScaffold",
            "activated cleanup return pair=" + recovery.pairIndex()
                + " stages="
                    + activeContinuousTeardownStages.size()
                + " targets="
                    + recovery.breakExpectations().size()
        );
    }

    private void activateTeardownScaffoldCleanupStages(
        boolean includeTerminalEntry
    ) {
        ActiveTeardownScaffoldRecovery recovery =
            Objects.requireNonNull(
                activeTeardownScaffoldRecovery,
                "activeTeardownScaffoldRecovery"
            );
        ArrayList<BlockPos> outward = new ArrayList<>(
            recovery.outwardSupports()
        );
        ArrayList<ContinuousTeardownRoutePlan.Stage<BlockPos>> stages =
            new ArrayList<>(
                outward.size() + (includeTerminalEntry ? 2 : 1)
            );
        if (includeTerminalEntry) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.terminalCleanupTarget(),
                List.of()
            ));
        }
        if (outward.isEmpty()) {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.entryEndpoint(),
                List.of(recovery.terminalCleanupTarget())
            ));
        } else {
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                outward.getLast(),
                List.of(recovery.terminalCleanupTarget())
            ));
            for (int index = outward.size() - 2;
                 index >= 0;
                 index--) {
                stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                    outward.get(index),
                    List.of(outward.get(index + 1))
                ));
            }
            stages.add(new ContinuousTeardownRoutePlan.Stage<>(
                recovery.entryEndpoint(),
                List.of(outward.getFirst())
            ));
        }
        activeContinuousTeardownStages = List.copyOf(stages);
        activeContinuousTeardownPair = recovery.pairIndex();
        activeContinuousTeardownStageIndex = 0;
        activeCircularRouteSupportIndex = 0;
        activeContinuousTeardownRecoveryExit = false;
    }

    private void completeTeardownScaffoldRecovery() {
        ActiveTeardownScaffoldRecovery recovery =
            activeTeardownScaffoldRecovery;
        if (recovery == null) {
            failTeardownMining(
                "Sparse teardown cleanup lost its active plan."
            );
            return;
        }
        for (BlockPos target : recovery.breakExpectations().keySet()) {
            if (!MapAreaCache.getCachedBlockState(target).isAir()) {
                failTeardownMining(
                    "Sparse teardown cleanup finished with a remaining "
                        + "owned block at " + target.toShortString() + "."
                );
                return;
            }
        }
        int pairIndex = recovery.pairIndex();
        int cleaned = recovery.breakExpectations().size();
        activeContinuousTeardownArmed = false;
        stopCircularMiningMotion();
        resetTeardownMiningActionState();
        checkpoints.clear();
        info(
            "Sparse teardown scaffold recovery cleared " + cleaned
                + " blocks from pair " + pairIndex
                + " and removed its complete temporary path."
        );
        finishMiningIfComplete();
    }

    private boolean isHorizontallyOverCheckpointSupport(Vec3d goal) {
        return isHorizontallyOverCheckpointSupport(
            supportBelowCheckpoint(goal)
        );
    }

    private boolean isPlayerStandingOnTeardownTarget(
        BlockPos target
    ) {
        if (mc.player == null || !mc.player.isOnGround()) {
            return false;
        }
        BlockPos support = supportBelowCheckpoint(
            mc.player.getEntityPos()
        );
        return target.equals(support)
            && isPlayerStandingOnSupport(support);
    }

    private boolean isHorizontallyOverCheckpointSupport(BlockPos support) {
        return mc.player.getBlockX() == support.getX()
            && mc.player.getBlockZ() == support.getZ();
    }

    private boolean isGroundedOnCheckpointSupport(Vec3d goal) {
        return mc.player.isOnGround()
            && isPlayerStandingOnSupport(supportBelowCheckpoint(goal));
    }

    private void stopCircularMiningMotion() {
        stopMovement();
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
    }

    private boolean ownedTeardownMayOverlapMovement() {
        if (teardownMineController == null
            || workActionBudget == null
            || miningPos == null) {
            return false;
        }
        return teardownMineController.snapshot()
            .map(snapshot ->
                TeardownMovementOverlapPolicy.mayContinue(
                    teardownMovementOverlapAllowed,
                    workActionBudget.paused(),
                    isBuildPlacementInReach(miningPos),
                    activeTeardownRouteAdvanceRetainsReach(
                        miningPos
                    ),
                    snapshot.classification(),
                    activeTeardownSpeedMineAllowsMovement(
                        snapshot.target().expectedBlock()
                    )
                ))
            .orElse(false);
    }

    private boolean activeTeardownRouteAdvanceRetainsReach(
        BlockPos target
    ) {
        if (mc.player == null
            || activeContinuousTeardownStages.isEmpty()
            || activeCircularRouteSupportIndex < 0
            || activeCircularRouteSupportIndex
                >= activeContinuousTeardownStages.size()) {
            return false;
        }
        int nextSupportIndex = activeCircularRouteSupportIndex + 1;
        if (nextSupportIndex
            >= activeContinuousTeardownStages.size()) {
            return true;
        }

        BlockPos nextSupport = activeContinuousTeardownStages.get(
            nextSupportIndex
        ).support();
        if (!isTeardownTargetReachableFromSupport(
            target,
            nextSupport
        )) {
            return false;
        }

        // A server acknowledgement for optional remote work may remain
        // pending after its stage cursor advances. Do not let that lease move
        // past any mandatory current-U work which becomes due at the proposed
        // support. This keeps the primary U route inside its proven live
        // deadline even under delayed acknowledgements.
        int firstDueStage = Math.max(
            0,
            activeContinuousTeardownStageIndex
        );
        int lastDueStage = Math.min(
            nextSupportIndex,
            activeContinuousTeardownStages.size() - 1
        );
        for (int stageIndex = firstDueStage;
             stageIndex <= lastDueStage;
             stageIndex++) {
            for (BlockPos dueTarget :
                activeContinuousTeardownStages.get(stageIndex)
                    .breakTargets()) {
                if (!MapAreaCache.getCachedBlockState(dueTarget).isAir()
                    && !isTeardownTargetReachableFromSupport(
                        dueTarget,
                        nextSupport
                    )) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isTeardownTargetReachableFromSupport(
        BlockPos target,
        BlockPos support
    ) {
        double standingEyeHeight =
            mc.player.getEyePos().y - mc.player.getY();
        return BlockReachWindow.find(
            new BlockReachWindow.Cell(
                target.getX(),
                target.getY(),
                target.getZ()
            ),
            List.of(
                new BlockReachWindow.Cell(
                    support.getX(),
                    support.getY(),
                    support.getZ()
                )
            ),
            standingEyeHeight,
            Math.max(
                0.1,
                effectiveBuildInteractionRange()
                    - TEARDOWN_REACH_POSITION_TOLERANCE
            )
        ).isPresent();
    }

    private boolean activeTeardownSpeedMineAllowsMovement(
        Block expectedBlock
    ) {
        if (!thmInstantTeardown.get()
            || speedMineOwner != SpeedMineOwner.TEARDOWN
            || ownedSpeedMineSnapshot == null) {
            return false;
        }
        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        return speedMine != null
            && speedMine.isActive()
            && speedMine.instamine()
            && speedMine.filter(expectedBlock);
    }

    private TeardownBreakStatus driveOrderedTeardownBreak(
        BlockPos position,
        Block expectedBlock
    ) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(expectedBlock, "expectedBlock");
        if (mc.player == null
            || mc.world == null
            || workActionBudget == null) {
            return TeardownBreakStatus.WAITING;
        }

        OrderedTeardownMineController.Target<BlockPos, Block>
            requestedTarget =
                new OrderedTeardownMineController.Target<>(
                    new BlockPos(position),
                    expectedBlock
                );
        Optional<
            OrderedTeardownMineController.Target<BlockPos, Block>
        > ownedTarget = teardownMineController.target();
        if (ownedTarget.isPresent()
            && !ownedTarget.get().equals(requestedTarget)) {
            return failTeardownMining(
                "Ordered teardown attempted to acquire "
                    + position.toShortString()
                    + " while still owning "
                    + ownedTarget.get().key().toShortString() + "."
            );
        }

        ServerBlockObservation observation =
            serverBlockObservations.get(position);
        if (ownedTarget.isPresent() && observation != null) {
            OrderedTeardownMineController.ObservationResult
                observationResult = teardownMineController.observe(
                    new OrderedTeardownMineController.Observation<>(
                        ownedTarget.get(),
                        observation.sequence(),
                        OrderedTeardownMineController
                            .ObservationSource.AUTHORITATIVE,
                        observation.block() == Blocks.AIR
                            ? OrderedTeardownMineController
                                .ObservedBlock.AIR
                            : OrderedTeardownMineController
                                .ObservedBlock.NON_AIR
                    )
                );
            if (observationResult
                != OrderedTeardownMineController
                    .ObservationResult.STALE_IGNORED) {
                debugLog(
                    "Teardown",
                    "authoritative observation position="
                        + position.toShortString()
                        + " block="
                            + Registries.BLOCK.getId(
                                observation.block()
                            )
                        + " sequence=" + observation.sequence()
                        + " result=" + observationResult
                );
            }
            if (observationResult
                == OrderedTeardownMineController
                    .ObservationResult.COMPLETED
                || observationResult
                    == OrderedTeardownMineController
                        .ObservationResult.ALREADY_COMPLETED) {
                teardownMineController.abandon();
                teardownMineFirstDispatchTick = -1L;
                teardownMineLastDispatchTick = -1L;
                confirmedTeardownBreaks++;
                debugLog(
                    "Teardown",
                    "authoritative air confirmed position="
                        + position.toShortString()
                        + " confirmedTotal="
                            + confirmedTeardownBreaks
                );
                return TeardownBreakStatus.CLEARED;
            }

            OrderedTeardownMineController.Snapshot<BlockPos, Block>
                snapshot = teardownMineController.snapshot()
                    .orElseThrow();
            if (snapshot.attempts() > 0
                && observation.sequence()
                    > snapshot.latestSubmissionSequence()
                && observation.block() != Blocks.AIR
                && observation.block() != expectedBlock) {
                return failTeardownMining(
                    "Ordered teardown target changed from "
                        + expectedBlock.getName().getString() + " to "
                        + observation.block().getName().getString()
                        + " at " + position.toShortString() + "."
                );
            }
        }

        BlockState targetState =
            MapAreaCache.getCachedBlockState(position);
        if (ownedTarget.isEmpty() && targetState.isAir()) {
            return TeardownBreakStatus.CLEARED;
        }
        if (targetState.isAir()) {
            // Local interaction prediction is not completion proof.
            stopMovement();
            return TeardownBreakStatus.WAITING;
        }
        if (targetState.getBlock() != expectedBlock) {
            return failTeardownMining(
                "Ordered teardown expected "
                    + expectedBlock.getName().getString() + " at "
                    + position.toShortString() + ", but found "
                    + targetState.getBlock().getName().getString() + "."
            );
        }

        if (workActionBudget.paused()) {
            releaseTeardownSpeedMine();
            if (lastMiningPauseReason
                != workActionBudget.pauseReason()) {
                warning(
                    "Ordered teardown paused by the server-TPS guard: "
                        + workActionBudget.pauseReason() + "."
                );
            }
            lastMiningPauseReason = workActionBudget.pauseReason();
            stopMovement();
            return TeardownBreakStatus.WAITING;
        }
        if (lastMiningPauseReason
            != TpsScaledActionBudget.PauseReason.NONE) {
            info(
                "Server TPS recovered; resuming rate-limited "
                    + "ordered teardown."
            );
            lastMiningPauseReason =
                TpsScaledActionBudget.PauseReason.NONE;
        }

        if (!equipMiningTool(targetState)) {
            stopMovement();
            return TeardownBreakStatus.WAITING;
        }
        if (thmInstantTeardown.get()
            && !acquireTeardownSpeedMine(expectedBlock)) {
            return failTeardownMining(
                "Meteor Speed Mine is unavailable for THM-style "
                    + "ordered teardown."
            );
        }

        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        RepairMiningClassification classification =
            RepairMiningClassification.classify(
                BlockUtils.canInstaBreak(position),
                thmInstantTeardown.get()
                    && speedMineOwner == SpeedMineOwner.TEARDOWN
                    && ownedSpeedMineSnapshot != null
                    && speedMine != null
                    && speedMine.instamine(),
                speedMine != null
                    && speedMine.filter(expectedBlock),
                targetState.calcBlockBreakingDelta(
                    mc.player,
                    mc.world,
                    position
                )
            );
        if (ownedTarget.isEmpty()) {
            long latestTargetObservation = observation == null
                ? 0L
                : observation.sequence();
            OrderedTeardownMineController.ClaimResult claim =
                teardownMineController.claim(
                    requestedTarget,
                    classification,
                    latestTargetObservation
                );
            debugLog(
                "Teardown",
                "claim position=" + position.toShortString()
                    + " block="
                        + Registries.BLOCK.getId(expectedBlock)
                    + " classification=" + classification
                    + " latestObservation="
                        + latestTargetObservation
                    + " result=" + claim
            );
            if (claim
                == OrderedTeardownMineController.ClaimResult
                    .BLOCKED_BY_OWNED_TARGET) {
                return failTeardownMining(
                    "Ordered teardown could not acquire "
                        + position.toShortString() + "."
                );
            }
        }

        OrderedTeardownMineController.Snapshot<BlockPos, Block>
            snapshot = teardownMineController.snapshot()
                .orElseThrow();
        if (teardownMineFirstDispatchTick >= 0
            && miningActionTick - teardownMineFirstDispatchTick
                >= TEARDOWN_BREAK_MAX_PENDING_TICKS) {
            return failTeardownMining(
                "Ordered teardown did not receive authoritative air "
                    + "within the bounded mining window at "
                    + position.toShortString() + "."
            );
        }
        if (snapshot.phase()
                == OrderedTeardownMineController.Phase
                    .AWAITING_AUTHORITATIVE_AIR
            && !snapshot.classification()
                .requiresProgressiveContinuation()
            && teardownMineLastDispatchTick >= 0
            && miningActionTick - teardownMineLastDispatchTick
                >= TEARDOWN_BREAK_RETRY_TICKS) {
            if (snapshot.attempts()
                >= TEARDOWN_BREAK_MAX_ATTEMPTS) {
                return failTeardownMining(
                    "Server rejected "
                        + TEARDOWN_BREAK_MAX_ATTEMPTS
                        + " ordered teardown attempts at "
                        + position.toShortString() + "."
                );
            }
            teardownMineController.requestRetry();
        }

        OrderedTeardownMineController.Plan<BlockPos, Block> plan =
            teardownMineController.planNext(
                workActionBudget::tryConsume
            );
        switch (plan.action()) {
            case WAITING_FOR_ACTION_BUDGET,
                 AWAIT_AUTHORITATIVE_AIR,
                 NO_TARGET -> {
                return TeardownBreakStatus.WAITING;
            }
            case COMPLETED -> {
                teardownMineController.abandon();
                teardownMineFirstDispatchTick = -1L;
                teardownMineLastDispatchTick = -1L;
                return TeardownBreakStatus.CLEARED;
            }
            case CONTINUE_PROGRESSIVE -> {
                ((IClientPlayerInteractionManager)
                    mc.interactionManager)
                    .setBlockBreakingCooldown(0);
                if (!BlockUtils.breakBlock(position, true)) {
                    return failTeardownMining(
                        "Owned progressive teardown could not continue at "
                        + position.toShortString() + "."
                    );
                }
                debugLog(
                    "Teardown",
                    "continued owned progressive break position="
                        + position.toShortString()
                        + " miningTick=" + miningActionTick
                );
                return TeardownBreakStatus.WAITING;
            }
            case DISPATCH,
                 DISPATCH_ALREADY_RESERVED -> {
                OrderedTeardownMineController.DispatchDecision<
                    BlockPos,
                    Block
                > decision = plan.dispatch().orElseThrow();
                long submissionSequence =
                    serverBlockUpdateSequence;
                ((IClientPlayerInteractionManager)
                    mc.interactionManager)
                    .setBlockBreakingCooldown(0);
                if (!BlockUtils.breakBlock(position, true)) {
                    teardownMineController.rejectDispatch(decision);
                    return failTeardownMining(
                        "Could not dispatch the owned ordered teardown "
                            + "target at " + position.toShortString() + "."
                    );
                }
                if (!teardownMineController.recordDispatched(
                    decision,
                    submissionSequence
                )) {
                    return failTeardownMining(
                        "Ordered teardown lost its dispatch lease at "
                            + position.toShortString() + "."
                    );
                }
                reserveToolUseShadow(
                    mc.player.getInventory().getSelectedSlot()
                );
                if (teardownMineFirstDispatchTick < 0) {
                    teardownMineFirstDispatchTick =
                        miningActionTick;
                }
                teardownMineLastDispatchTick = miningActionTick;
                teardownBreakAttempts++;
                debugLog(
                    "Teardown",
                    "dispatched position=" + position.toShortString()
                        + " kind=" + decision.kind()
                        + " attempt=" + decision.attemptNumber()
                        + " classification="
                            + decision.classification()
                        + " toolSlot="
                            + mc.player.getInventory()
                                .getSelectedSlot()
                        + " blockSequence="
                            + submissionSequence
                        + " miningTick=" + miningActionTick
                        + " submittedTotal="
                            + teardownBreakAttempts
                );
                return TeardownBreakStatus.WAITING;
            }
        }
        throw new IllegalStateException(
            "Unhandled ordered teardown action: " + plan.action()
        );
    }

    private TeardownBreakStatus failTeardownMining(String reason) {
        debugLog(
            "Teardown",
            "fatal failure reason=" + reason
                + " snapshot="
                    + (teardownMineController == null
                        ? "unavailable"
                        : teardownMineController.snapshot()
                            .map(Object::toString)
                            .orElse("none"))
        );
        error(reason);
        resetTeardownMiningActionState();
        stopMovement();
        toggle();
        return TeardownBreakStatus.FAILED;
    }

    private void resetTeardownMiningActionState() {
        if (debugPrints.get()
            && teardownMineController != null
            && teardownMineController.target().isPresent()) {
            debugLog(
                "Teardown",
                "resetting controller snapshot="
                    + teardownMineController.snapshot()
                        .map(Object::toString)
                        .orElse("none")
            );
        }
        releaseTeardownSpeedMine();
        if (teardownMineController != null) {
            teardownMineController.reset();
        }
        teardownMovementOverlapAllowed = false;
        activeContinuousTeardownStages = List.of();
        activeContinuousTeardownPair = -1;
        activeContinuousTeardownStageIndex = -1;
        activeCircularRouteSupportIndex = -1;
        activeContinuousTeardownArmed = false;
        activeContinuousTeardownRecoveryExit = false;
        clearTeardownScaffoldRecoveryState();
        teardownMineFirstDispatchTick = -1L;
        teardownMineLastDispatchTick = -1L;
        lastMiningPauseReason =
            TpsScaledActionBudget.PauseReason.NONE;
    }

    private void clearTeardownScaffoldRecoveryState() {
        teardownScaffoldPhase = TeardownScaffoldPhase.NONE;
        activeTeardownScaffoldRecovery = null;
        activeTeardownScaffoldHotbarSlot = -1;
        if (teardownScaffoldPlacementLedger != null) {
            teardownScaffoldPlacementLedger.reset();
        }
        if (teardownScaffoldSubmissionBlockSequences != null) {
            teardownScaffoldSubmissionBlockSequences.clear();
        }
    }

    private boolean prepareTeardownCheckpointHotbar(
        String checkpointAction,
        Vec3d checkpointGoal,
        BlockPos checkpointData,
        boolean enforceEntryDurability,
        boolean includeScaffoldMaterial
    ) {
        HotbarPreparation preparation =
            prepareTeardownHotbarLoadout(
                enforceEntryDurability
            );
        if (includeScaffoldMaterial
            && preparation == HotbarPreparation.READY) {
            preparation = prepareTeardownScaffoldHotbar();
        }
        if (preparation != HotbarPreparation.READY) {
            if (preparation == HotbarPreparation.WAITING) {
                checkpoints.add(0, new Pair<>(
                    checkpointGoal,
                    new Pair<>(
                        checkpointAction,
                        checkpointData
                    )
                ));
            } else if (isActive()) {
                toggle();
            }
            stopMovement();
            return false;
        }
        strictMiningRestockActive = false;
        strictMiningInventoryPlan = null;
        return true;
    }

    private void armContinuousTeardownRoute(
        String category,
        String description,
        int pairIndex
    ) {
        if (activeContinuousTeardownStages.isEmpty()) return;
        activeContinuousTeardownStageIndex = 0;
        activeCircularRouteSupportIndex = 0;
        activeContinuousTeardownArmed = true;
        debugLog(
            category,
            "activated " + description
                + " pair=" + pairIndex
                + " currentSupport="
                    + activeContinuousTeardownStages
                        .getFirst().support().toShortString()
                + (activeContinuousTeardownStages.size() < 2
                    ? ""
                    : " nextSupport="
                        + activeContinuousTeardownStages
                            .get(1).support().toShortString())
                + " stages="
                    + activeContinuousTeardownStages.size()
        );
    }

    private HotbarPreparation prepareTeardownHotbarLoadout() {
        return prepareTeardownHotbarLoadout(true);
    }

    private HotbarPreparation prepareTeardownHotbarLoadout(
        boolean enforceEntryDurability
    ) {
        if (confirmedMiningHotbarSwap.isPending()
            || confirmedBuildHotbarSwap.isPending()
            || pendingInventoryMetadataSwap != null) {
            stopMovement();
            return HotbarPreparation.WAITING;
        }

        LinkedHashMap<Item, Integer> minimum =
            teardownMinimumToolCounts();
        if (minimum == null) return HotbarPreparation.FAILED;
        ArrayList<Item> desired = new ArrayList<>();
        minimum.forEach((item, count) -> {
            for (int index = 0; index < count; index++) {
                desired.add(item);
            }
        });

        if (plannedTeardownHotbarAssignments.isEmpty()) {
            LinkedHashMap<Integer, Item> current =
                new LinkedHashMap<>();
            for (int slot : availableHotBarSlots) {
                ItemStack stack =
                    mc.player.getInventory().getStack(slot);
                if (!stack.isEmpty()
                    && minimum.containsKey(stack.getItem())
                    && isUsableTeardownLoadoutTool(
                        stack,
                        stack.getItem(),
                        enforceEntryDurability
                    )) {
                    current.put(slot, stack.getItem());
                }
            }
            plannedTeardownHotbarAssignments.putAll(
                PhaseHotbarPlan.assignRequiredItems(
                    availableHotBarSlots,
                    desired,
                    current
                )
            );
            debugLog(
                "HotbarPlan",
                "initialized teardown assignments="
                    + plannedTeardownHotbarAssignments
            );
        }

        for (Map.Entry<Integer, Item> assignment
            : plannedTeardownHotbarAssignments.entrySet()) {
            int targetSlot = assignment.getKey();
            Item expectedItem = assignment.getValue();
            ItemStack target =
                mc.player.getInventory().getStack(targetSlot);
            if (isUsableTeardownLoadoutTool(
                target,
                expectedItem,
                enforceEntryDurability
            )) {
                continue;
            }

            int sourceSlot =
                findBestTeardownMainInventoryTool(
                    expectedItem,
                    enforceEntryDurability
                );
            if (sourceSlot < 0) {
                error(
                    "The strict teardown inventory contains no usable "
                        + expectedItem.getName().getString()
                        + " for its planned hotbar slot."
                );
                return HotbarPreparation.FAILED;
            }
            MiningToolIdentity expectedIdentity =
                miningToolIdentity(
                    mc.player.getInventory().getStack(sourceSlot)
                );
            confirmedMiningHotbarSwap.begin(
                targetSlot,
                expectedIdentity,
                serverHotbarSwapAckSequences[targetSlot],
                clientActionTick
            );
            miningHotbarSwapContext =
                MiningHotbarSwapContext.TEARDOWN;
            if (!dispatchConfirmedInventorySwap(
                sourceSlot,
                targetSlot,
                "teardown-hotbar preload",
                false
            )) {
                failMiningHotbarSwap(expectedIdentity);
                return HotbarPreparation.FAILED;
            }
            debugLog(
                "HotbarPlan",
                "submitted silent teardown preload sourceSlot="
                    + sourceSlot + " targetHotbarSlot="
                    + targetSlot + " expected="
                    + Registries.ITEM.getId(expectedItem)
            );
            stopMovement();
            return HotbarPreparation.WAITING;
        }

        debugLog(
            "HotbarPlan",
            "teardown hotbar ready assignments="
                + plannedTeardownHotbarAssignments
        );
        return HotbarPreparation.READY;
    }

    private HotbarPreparation prepareTeardownScaffoldHotbar() {
        int slot = ensureTeardownScaffoldHotbarSlot();
        if (slot >= 0) return HotbarPreparation.READY;
        if (slot == HOTBAR_SLOT_PENDING) {
            return HotbarPreparation.WAITING;
        }
        return HotbarPreparation.FAILED;
    }

    private int ensureTeardownScaffoldHotbarSlot() {
        if (mc.player == null) return HOTBAR_ITEM_UNAVAILABLE;
        Set<Integer> toolSlots =
            plannedTeardownHotbarAssignments.keySet();
        if (activeTeardownScaffoldHotbarSlot >= 0
            && !toolSlots.contains(activeTeardownScaffoldHotbarSlot)) {
            ItemStack active = mc.player.getInventory().getStack(
                activeTeardownScaffoldHotbarSlot
            );
            if (!active.isEmpty()
                && active.getItem() == Items.COBBLESTONE) {
                return InvUtils.swap(
                    activeTeardownScaffoldHotbarSlot,
                    false
                )
                    ? activeTeardownScaffoldHotbarSlot
                    : HOTBAR_ITEM_UNAVAILABLE;
            }
        }
        for (int slot : availableHotBarSlots) {
            if (toolSlots.contains(slot)) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                && stack.getItem() == Items.COBBLESTONE) {
                activeTeardownScaffoldHotbarSlot = slot;
                return InvUtils.swap(slot, false)
                    ? slot
                    : HOTBAR_ITEM_UNAVAILABLE;
            }
        }
        if (confirmedMiningHotbarSwap.isPending()) {
            return HOTBAR_SLOT_PENDING;
        }

        int sourceSlot = findBestMainInventorySlot(
            Items.COBBLESTONE,
            -1
        );
        if (sourceSlot < 0) {
            error(
                "The teardown scaffold reserve contains no available "
                    + "cobblestone stack."
            );
            stopMovement();
            toggle();
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        int targetSlot = availableHotBarSlots.stream()
            .filter(slot -> !toolSlots.contains(slot))
            .min(Comparator
                .comparingInt((Integer slot) ->
                    mc.player.getInventory().getStack(slot).isEmpty()
                        ? 0
                        : 1)
                .thenComparingInt(Integer::intValue))
            .orElse(-1);
        if (targetSlot < 0) {
            error(
                "The teardown hotbar has no non-tool slot for its "
                    + "cobblestone scaffold."
            );
            stopMovement();
            toggle();
            return HOTBAR_ITEM_UNAVAILABLE;
        }

        ItemStack source = mc.player.getInventory().getStack(sourceSlot);
        MiningToolIdentity expected = miningToolIdentity(source);
        confirmedMiningHotbarSwap.begin(
            targetSlot,
            expected,
            serverHotbarSwapAckSequences[targetSlot],
            clientActionTick
        );
        miningHotbarSwapContext = MiningHotbarSwapContext.TEARDOWN;
        if (!dispatchConfirmedInventorySwap(
            sourceSlot,
            targetSlot,
            "teardown-scaffold cobblestone",
            false
        )) {
            failMiningHotbarSwap(expected);
            return HOTBAR_ITEM_UNAVAILABLE;
        }
        activeTeardownScaffoldHotbarSlot = targetSlot;
        debugLog(
            "HotbarPlan",
            "submitted silent scaffold preload sourceSlot="
                + sourceSlot + " targetHotbarSlot=" + targetSlot
        );
        stopMovement();
        return HOTBAR_SLOT_PENDING;
    }

    private boolean isUsableTeardownLoadoutTool(
        ItemStack stack,
        Item expectedItem
    ) {
        return isUsableTeardownLoadoutTool(
            stack,
            expectedItem,
            true
        );
    }

    private boolean isUsableTeardownLoadoutTool(
        ItemStack stack,
        Item expectedItem,
        boolean enforceEntryDurability
    ) {
        if (stack.isEmpty()
            || !stack.getItem().equals(expectedItem)
            || !(enforceEntryDurability
                ? hasMinimumToolDurability(stack)
                : hasOperationalToolDurability(stack))) {
            return false;
        }
        ItemStack template = toolSet.stream()
            .filter(candidate ->
                candidate.getItem().equals(expectedItem))
            .sorted(Comparator
                .comparingInt(
                    (ItemStack candidate) ->
                        registeredToolEfficiency(candidate)
                )
                .reversed())
            .findFirst()
            .orElse(null);
        return template != null
            && getEfficiencyLevel(stack)
                >= registeredToolEfficiency(template);
    }

    private int findBestTeardownMainInventoryTool(
        Item expectedItem,
        boolean enforceEntryDurability
    ) {
        int bestSlot = -1;
        int bestRemaining = -1;
        for (int slot : availableSlots) {
            if (slot < 9 || slot >= 36) continue;
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!isUsableTeardownLoadoutTool(
                stack,
                expectedItem,
                enforceEntryDurability
            )) {
                continue;
            }
            int remaining = remainingToolDurability(stack);
            if (remaining > bestRemaining
                || (remaining == bestRemaining
                    && (bestSlot < 0 || slot < bestSlot))) {
                bestSlot = slot;
                bestRemaining = remaining;
            }
        }
        return bestSlot;
    }

    private boolean equipMiningTool(BlockState targetState) {
        ItemStack bestTool = getBestRegisteredTool(targetState);
        if (bestTool == null) {
            error("No registered tool can safely mine " + targetState.getBlock().getName().getString() + ".");
            toggle();
            return false;
        }
        int plannedHotbarSlot =
            findBestCompatibleMiningHotbarSlot(
                bestTool,
                targetState
            );
        if (plannedHotbarSlot >= 0) {
            return InvUtils.swap(plannedHotbarSlot, false);
        }
        int bestInventorySlot = findBestMiningInventorySlot(
            bestTool,
            targetState,
            -1,
            false,
            false,
            0.0
        );
        if (confirmedMiningHotbarSwap.isPending()) return false;

        if (bestInventorySlot >= 0) {
            Optional<Integer> plannedTarget =
                plannedTeardownHotbarAssignments.entrySet()
                    .stream()
                    .filter(entry ->
                        entry.getValue().equals(
                            bestTool.getItem()
                        ))
                    .map(Map.Entry::getKey)
                    .filter(slot ->
                        !isCompatibleMiningTool(
                            mc.player.getInventory()
                                .getStack(slot),
                            bestTool,
                            targetState
                        )
                            || !hasOperationalToolDurability(
                                mc.player.getInventory()
                                    .getStack(slot)
                            ))
                    .findFirst();
            if (plannedTarget.isEmpty()) {
                error(
                    "The teardown hotbar plan has no replacement slot "
                        + "for "
                        + bestTool.getName().getString() + "."
                );
                toggle();
                return false;
            }
            int targetHotbarSlot = plannedTarget.get();
            MiningToolIdentity expectedIdentity = miningToolIdentity(
                mc.player.getInventory().getStack(bestInventorySlot)
            );
            confirmedMiningHotbarSwap.begin(
                targetHotbarSlot,
                expectedIdentity,
                serverHotbarSwapAckSequences[targetHotbarSlot],
                clientActionTick
            );
            miningHotbarSwapContext =
                MiningHotbarSwapContext.TEARDOWN;
            if (!dispatchConfirmedInventorySwap(
                bestInventorySlot,
                targetHotbarSlot,
                "teardown mining-tool",
                false
            )) {
                failMiningHotbarSwap(expectedIdentity);
                return false;
            }
            stopMovement();
            return false;
        }
        if (state == State.MiningUTraversal
            || state == State.AwaitUBlockBreak) {
            info(
                "No operational "
                    + bestTool.getName().getString()
                    + " remains in the preloaded teardown loadout; "
                    + "leaving the U through its verified endpoint "
                    + "before rebuilding the next entry plan."
            );
            miningRecoveryPending = true;
            miningRecoveryNeedsTools = false;
            stopMovement();
            return false;
        }
        error(
            "Required mining tool is missing from the inventory: "
                + bestTool.getName().getString() + "."
        );
        toggle();
        return false;
    }

    private int findBestCompatibleMiningHotbarSlot(
        ItemStack preferredTool,
        BlockState targetState
    ) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestRemaining = -1;
        for (int slot : availableHotBarSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!isCompatibleMiningTool(
                stack,
                preferredTool,
                targetState
            )) {
                continue;
            }
            int remaining = remainingToolDurability(stack);
            if (!hasOperationalToolDurability(stack)) continue;
            double score =
                ToolUtils.getEffectiveMiningScore(
                    stack,
                    targetState
                );
            if (score > bestScore
                || (Double.compare(score, bestScore) == 0
                    && (remaining > bestRemaining
                        || (remaining == bestRemaining
                            && (bestSlot < 0
                                || slot < bestSlot))))) {
                bestSlot = slot;
                bestScore = score;
                bestRemaining = remaining;
            }
        }
        return bestSlot;
    }

    private int findBestMiningInventorySlot(
        ItemStack preferredTool,
        BlockState targetState,
        int excludedSlot,
        boolean accountRepairShadow
    ) {
        return findBestMiningInventorySlot(
            preferredTool,
            targetState,
            excludedSlot,
            accountRepairShadow,
            false
        );
    }

    private int findBestMiningInventorySlot(
        ItemStack preferredTool,
        BlockState targetState,
        int excludedSlot,
        boolean accountRepairShadow,
        boolean requireMainInventory
    ) {
        return findBestMiningInventorySlot(
            preferredTool,
            targetState,
            excludedSlot,
            accountRepairShadow,
            requireMainInventory,
            minimumToolDurabilityFraction()
        );
    }

    private int findBestMiningInventorySlot(
        ItemStack preferredTool,
        BlockState targetState,
        int excludedSlot,
        boolean accountRepairShadow,
        boolean requireMainInventory,
        double minimumRemainingFraction
    ) {
        int bestSlot = -1;
        double bestMiningScore = Double.NEGATIVE_INFINITY;
        int bestRemainingDurability = -1;
        for (int slot : availableSlots) {
            if (slot == excludedSlot
                || slot < 0
                || slot >= 36
                || (requireMainInventory && slot < 9)) {
                continue;
            }
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isCompatibleMiningTool(
                stack,
                preferredTool,
                targetState
            )) continue;

            int remaining = remainingToolDurability(stack);
            if (accountRepairShadow) {
                RepairToolShadow shadow = repairToolShadows.get(slot);
                if (shadow != null) {
                    remaining = Math.max(
                        0,
                        remaining - shadow.unacknowledgedUses()
                    );
                }
            }
            if (!ToolDurabilityPolicy.isReusable(
                remaining,
                stack.getMaxDamage(),
                minimumRemainingFraction
            )) continue;
            double miningScore =
                ToolUtils.getEffectiveMiningScore(stack, targetState);
            boolean better = miningScore > bestMiningScore
                || (Double.compare(miningScore, bestMiningScore) == 0
                    && (remaining > bestRemainingDurability
                        || (remaining == bestRemainingDurability
                            && (bestSlot < 0
                                || (availableHotBarSlots.contains(slot)
                                    && !availableHotBarSlots.contains(
                                        bestSlot
                                    ))
                                || (availableHotBarSlots.contains(slot)
                                    == availableHotBarSlots.contains(
                                        bestSlot
                                    )
                                    && slot < bestSlot)))));
            if (better) {
                bestMiningScore = miningScore;
                bestRemainingDurability = remaining;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean isCompatibleMiningTool(
        ItemStack candidate,
        ItemStack registeredTemplate,
        BlockState targetState
    ) {
        return !candidate.isEmpty()
            && candidate.getItem().equals(
                registeredTemplate.getItem()
            )
            && getEfficiencyLevel(candidate)
                >= registeredToolEfficiency(registeredTemplate)
            && candidate.getMiningSpeedMultiplier(targetState)
                >= registeredTemplate.getMiningSpeedMultiplier(
                    targetState
                )
            && (!registeredTemplate.isSuitableFor(targetState)
                || candidate.isSuitableFor(targetState));
    }

    private boolean isCompatibleMiningTool(
        ItemStack candidate,
        MiningToolRequirement requirement
    ) {
        if (requirement.itemOnly()) {
            return !candidate.isEmpty()
                && candidate.getItem().equals(
                    requirement.registeredTemplate().getItem()
                )
                && getEfficiencyLevel(candidate)
                    >= registeredToolEfficiency(
                        requirement.registeredTemplate()
                    );
        }
        return isCompatibleMiningTool(
            candidate,
            requirement.registeredTemplate(),
            requirement.targetState()
        );
    }

    private int getEfficiencyLevel(ItemStack stack) {
        return ToolUtils.getEfficiencyLevel(stack);
    }

    private int registeredToolEfficiency(ItemStack template) {
        return Math.max(
            getEfficiencyLevel(template),
            registeredToolMinimumEfficiency.getOrDefault(
                template.getItem(),
                0
            )
        );
    }

    private ItemStack getBestRegisteredTool(BlockState targetState) {
        ItemStack best = null;
        double bestScore = 1.0;
        for (ItemStack template : toolSet) {
            double score = ToolUtils.getEffectiveMiningScore(
                template,
                targetState,
                registeredToolEfficiency(template)
            );
            if (score > bestScore
                || (Double.compare(score, bestScore) == 0
                    && isBetterRegisteredToolTemplate(
                        template,
                        best,
                        targetState
                    ))) {
                best = template;
                bestScore = score;
            }
        }
        if (best != null) return best;

        for (ItemStack template : toolSet) {
            if (!template.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)) {
                continue;
            }
            if (isBetterRegisteredToolTemplate(
                template,
                best,
                targetState
            )) {
                best = template;
            }
        }
        return best;
    }

    private boolean isBetterRegisteredToolTemplate(
        ItemStack candidate,
        ItemStack current,
        BlockState targetState
    ) {
        if (current == null) return true;
        double candidateScore = ToolUtils.getEffectiveMiningScore(
            candidate,
            targetState,
            registeredToolEfficiency(candidate)
        );
        double currentScore = ToolUtils.getEffectiveMiningScore(
            current,
            targetState,
            registeredToolEfficiency(current)
        );
        int scoreComparison = Double.compare(
            candidateScore,
            currentScore
        );
        if (scoreComparison != 0) return scoreComparison > 0;

        int efficiencyComparison = Integer.compare(
            registeredToolEfficiency(candidate),
            registeredToolEfficiency(current)
        );
        if (efficiencyComparison != 0) {
            return efficiencyComparison > 0;
        }
        return Registries.ITEM.getId(candidate.getItem()).toString()
            .compareTo(
                Registries.ITEM.getId(current.getItem()).toString()
            ) < 0;
    }

    private int findMatchingMiningMainInventorySlot(
        MiningToolIdentity expected,
        int excludedSlot
    ) {
        for (int slot : availableSlots) {
            if (slot < 9
                || slot >= 36
                || slot == excludedSlot) {
                continue;
            }
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                && miningToolIdentity(stack).equals(expected)) {
                return slot;
            }
        }
        return -1;
    }

    private MiningToolIdentity miningToolIdentity(ItemStack stack) {
        Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
        return new MiningToolIdentity(
            item,
            StructuralItemStackKey.exact(stack)
        );
    }

    private InventoryStackIdentity inventoryStackIdentity(
        ItemStack stack
    ) {
        Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
        return new InventoryStackIdentity(
            item,
            StructuralItemStackKey.withoutDamage(stack),
            stack.isEmpty() ? 0 : stack.getCount(),
            stack.getDamage()
        );
    }

    private boolean dispatchConfirmedInventorySwap(
        int sourceSlot,
        int targetHotbarSlot,
        String owner,
        boolean retry
    ) {
        if (mc.player == null
            || mc.interactionManager == null
            || mc.player.currentScreenHandler.syncId != 0
            || sourceSlot < 9
            || sourceSlot >= 36
            || targetHotbarSlot < 0
            || targetHotbarSlot >= 9) {
            error(
                "Cannot submit the authoritative " + owner
                    + " swap outside the player inventory handler."
            );
            return false;
        }
        if (pendingInventoryMetadataSwap != null && !retry) {
            error(
                "Cannot overlap authoritative inventory swaps while "
                    + pendingInventoryMetadataSwap.owner()
                    + " is pending."
            );
            return false;
        }

        ItemStack source =
            mc.player.getInventory().getStack(sourceSlot);
        ItemStack target =
            mc.player.getInventory().getStack(targetHotbarSlot);
        debugLog(
            "HotbarSwap",
            "dispatch owner=" + owner
                + " retry=" + retry
                + " sourceSlot=" + sourceSlot
                + " targetHotbarSlot=" + targetHotbarSlot
                + " source={" + inventoryStackIdentity(source) + "}"
                + " target={" + inventoryStackIdentity(target) + "}"
                + " submittedAfter="
                    + serverInventoryUpdateSequence
        );
        pendingInventoryMetadataSwap =
            new PendingInventoryMetadataSwap(
                sourceSlot,
                targetHotbarSlot,
                inventoryStackIdentity(source),
                inventoryStackIdentity(target),
                InventorySlotMetadataSwap.capture(
                    sourceSlot,
                    targetHotbarSlot,
                    repairToolShadows,
                    plannedRepairToolKeepSlots
                ),
                serverInventoryUpdateSequence,
                owner
            );
        Utils.performAuthoritativeSwap(
            sourceSlot,
            targetHotbarSlot
        );
        return true;
    }

    private int remainingToolDurability(ItemStack stack) {
        return stack.getMaxDamage() <= 0
            ? Integer.MAX_VALUE
            : Math.max(0, stack.getMaxDamage() - stack.getDamage());
    }

    private int minimumReusableToolDurability(ItemStack stack) {
        return ToolDurabilityPolicy.minimumRemaining(
            stack.getMaxDamage(),
            minimumToolDurabilityFraction()
        );
    }

    private double minimumToolDurabilityFraction() {
        // Clamp persisted configurations written by builds whose former
        // default was five percent. Teardown must never plan below the new
        // ten-percent entry contract.
        return Math.max(
            MINIMUM_TOOL_DURABILITY_FRACTION,
            minimumToolDurability.get()
        );
    }

    private boolean hasMinimumToolDurability(ItemStack stack) {
        return hasToolDurability(
            stack,
            minimumToolDurabilityFraction()
        );
    }

    private boolean hasOperationalToolDurability(ItemStack stack) {
        return hasToolDurability(stack, 0.0);
    }

    private boolean hasToolDurability(
        ItemStack stack,
        double minimumRemainingFraction
    ) {
        return !stack.isEmpty()
            && stack.getMaxDamage() > 1
            && ToolDurabilityPolicy.isReusable(
                remainingToolDurability(stack),
                stack.getMaxDamage(),
                minimumRemainingFraction
            );
    }

    private void reserveToolUseShadow(int toolSlot) {
        ItemStack stack =
            mc.player.getInventory().getStack(toolSlot);
        RepairToolShadow previous =
            repairToolShadows.get(toolSlot);
        // RepairMineController owns only one unresolved break lease at a
        // time. A retry or a later lease replaces that single prediction;
        // it must not accumulate a durability debit when Unbreaking causes
        // the server to send no damage update for a successful break.
        int observedRemaining = remainingToolDurability(stack);
        repairToolShadows.put(
            toolSlot,
            new RepairToolShadow(
                observedRemaining,
                1,
                serverHotbarUpdateSequences[toolSlot]
            )
        );
        debugLog(
            "ToolDurability",
            "reserved predicted use playerSlot=" + toolSlot
                + " item=" + Registries.ITEM.getId(stack.getItem())
                + " observedRemaining="
                    + observedRemaining
                + " pendingUses=1"
                + " replacedPending=" + (previous != null)
                + " inventoryRevision="
                    + serverHotbarUpdateSequences[toolSlot]
        );
    }

    private HashMap<Item, Integer> missingCircularMiningTools(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return missingMiningTools(
            circularMiningInventoryTargets(route)
        );
    }

    private ArrayList<BlockPos> independentMiningTargets(int line) {
        ArrayList<BlockPos> targets = new ArrayList<>(map[line].length);
        for (int z = 0; z < map[line].length; z++) {
            targets.add(new BlockPos(line, map[line][z].getRight(), z));
        }
        return targets;
    }

    private HashMap<Item, Integer> missingMiningTools(
        Collection<BlockPos> relativeTargets
    ) {
        return missingMiningTools(relativeTargets, Map.of());
    }

    private HashMap<Item, Integer> missingMiningTools(
        Collection<BlockPos> relativeTargets,
        Map<BlockPos, BlockState> plannedTargetStates
    ) {
        return missingMiningTools(
            relativeTargets,
            plannedTargetStates,
            minimumToolDurabilityFraction()
        );
    }

    private HashMap<Item, Integer> missingOperationalMiningTools(
        Collection<BlockPos> relativeTargets,
        Map<BlockPos, BlockState> plannedTargetStates
    ) {
        return missingMiningTools(
            relativeTargets,
            plannedTargetStates,
            0.0
        );
    }

    private HashMap<Item, Integer> missingMiningTools(
        Collection<BlockPos> relativeTargets,
        Map<BlockPos, BlockState> plannedTargetStates,
        double minimumRemainingFraction
    ) {
        MiningToolInventoryPlan<
            Item,
            ItemStack,
            MiningToolRequirement
        > plan = createMiningToolInventoryPlan(
            relativeTargets,
            plannedTargetStates,
            minimumRemainingFraction
        );
        if (plan == null) return null;

        HashMap<Item, Integer> missingTools = new HashMap<>();
        plan.restockDemands().forEach((item, demand) -> {
            if (demand.remainingAmount() > 0) {
                missingTools.put(item, demand.remainingAmount());
            }
        });
        return missingTools;
    }

    private MiningToolInventoryPlan<
        Item,
        ItemStack,
        MiningToolRequirement
    > createMiningToolInventoryPlan(
        Collection<BlockPos> relativeTargets
    ) {
        return createMiningToolInventoryPlan(
            relativeTargets,
            Map.of()
        );
    }

    private MiningToolInventoryPlan<
        Item,
        ItemStack,
        MiningToolRequirement
    > createMiningToolInventoryPlan(
        Collection<BlockPos> relativeTargets,
        Map<BlockPos, BlockState> plannedTargetStates
    ) {
        return createMiningToolInventoryPlan(
            relativeTargets,
            plannedTargetStates,
            minimumToolDurabilityFraction()
        );
    }

    private MiningToolInventoryPlan<
        Item,
        ItemStack,
        MiningToolRequirement
    > createMiningToolInventoryPlan(
        Collection<BlockPos> relativeTargets,
        Map<BlockPos, BlockState> plannedTargetStates,
        double minimumRemainingFraction
    ) {
        Objects.requireNonNull(
            plannedTargetStates,
            "plannedTargetStates"
        );
        LinkedHashSet<Item> requiredToolItems =
            new LinkedHashSet<>();
        HashMap<Item, ArrayList<MiningToolRequirement>>
            compatibilityRequirements = new HashMap<>();
        LinkedHashMap<Item, Integer> minimumHotbarCounts =
            teardownMinimumToolCounts();
        if (minimumHotbarCounts == null) return null;
        for (BlockPos relative : relativeTargets) {
            BlockState state = plannedTargetStates.getOrDefault(
                relative,
                MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                )
            );
            if (state.isAir()) continue;
            ItemStack bestTool = getBestRegisteredTool(state);
            if (bestTool == null || bestTool.getMaxDamage() <= 1) {
                error(
                    "No registered damageable tool can mine "
                        + state.getBlock().getName().getString() + "."
                );
                return null;
            }
            Item item = bestTool.getItem();
            requiredToolItems.add(item);
            compatibilityRequirements.computeIfAbsent(
                item,
                ignored -> new ArrayList<>()
            ).add(new MiningToolRequirement(bestTool, state));
        }
        for (Map.Entry<Item, Integer> entry
            : minimumHotbarCounts.entrySet()) {
            ItemStack template = toolSet.stream()
                .filter(stack ->
                    stack.getItem().equals(entry.getKey()))
                .sorted(Comparator
                    .comparingInt(
                        (ItemStack stack) ->
                            registeredToolEfficiency(stack)
                    )
                    .reversed()
                    .thenComparing(stack ->
                        Registries.ITEM.getId(
                            stack.getItem()
                        ).toString()))
                .findFirst()
                .orElse(null);
            if (template == null || template.getMaxDamage() <= 1) {
                error(
                    "The teardown loadout has no registered damageable "
                        + entry.getKey().getName().getString() + "."
                );
                return null;
            }
            requiredToolItems.add(entry.getKey());
            compatibilityRequirements.computeIfAbsent(
                entry.getKey(),
                ignored -> new ArrayList<>()
            ).add(MiningToolRequirement.itemOnly(template.copy()));
        }

        HashMap<Item, Integer> compatibleCarriedCounts =
            new HashMap<>();
        ArrayList<
            MiningToolInventoryPlan.Tool<Item, ItemStack>
        > carriedTools = new ArrayList<>();
        for (int slot : availableSlots) {
            if (slot < 0 || slot >= 36) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()
                || !requiredToolItems.contains(stack.getItem())) {
                continue;
            }
            boolean compatible =
                compatibilityRequirements.get(stack.getItem())
                    .stream()
                    .allMatch(
                        requirement -> isCompatibleMiningTool(
                            stack,
                            requirement
                        )
            );
            if (!compatible) continue;
            carriedTools.add(
                miningInventoryTool(
                    stack.copy(),
                    0
                )
            );
            if (hasToolDurability(
                stack,
                minimumRemainingFraction
            )) {
                compatibleCarriedCounts.merge(
                    stack.getItem(),
                    1,
                    Integer::sum
                );
            }
        }

        LinkedHashMap<Item, Integer> missingTools =
            new LinkedHashMap<>();
        for (Item item : requiredToolItems) {
            int requiredCount = Math.max(
                1,
                minimumHotbarCounts.getOrDefault(item, 0)
            );
            int missing = Math.max(
                0,
                requiredCount
                    - compatibleCarriedCounts.getOrDefault(item, 0)
            );
            missingTools.put(item, missing);
        }
        return MiningToolInventoryPlan.plan(
            compatibilityRequirements,
            carriedTools,
            missingTools,
            minimumRemainingFraction,
            (candidate, requirement) ->
                isCompatibleMiningTool(
                    candidate,
                    requirement
                )
        );
    }

    private TeardownScaffoldMaterialPlan.Plan
        teardownScaffoldMaterialPlan() {
        ArrayList<Integer> stackCounts = new ArrayList<>();
        for (int slot : availableSlots) {
            if (slot < 0 || slot >= 36) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                && stack.getItem() == Items.COBBLESTONE) {
                stackCounts.add(stack.getCount());
            }
        }
        return TeardownScaffoldMaterialPlan.create(
            teardownScaffoldStacks.get(),
            64,
            stackCounts
        );
    }

    private boolean hasCompleteTeardownScaffoldReserve() {
        return teardownScaffoldMaterialPlan().missingAmount() == 0;
    }

    private LinkedHashMap<Item, Integer>
        teardownMinimumToolCounts() {
        ItemStack pickaxe =
            preferredRegisteredTeardownTool(ItemTags.PICKAXES);
        ItemStack axe =
            preferredRegisteredTeardownTool(ItemTags.AXES);
        if (pickaxe == null || axe == null) {
            error(
                "Map teardown requires a registered pickaxe chest "
                    + "and a registered axe chest."
            );
            return null;
        }
        LinkedHashMap<Item, Integer> minimum =
            new LinkedHashMap<>();
        minimum.put(
            pickaxe.getItem(),
            TEARDOWN_PICKAXE_HOTBAR_COUNT
        );
        minimum.put(
            axe.getItem(),
            TEARDOWN_AXE_HOTBAR_COUNT
        );
        return minimum;
    }

    private ItemStack preferredRegisteredTeardownTool(
        net.minecraft.registry.tag.TagKey<Item> tag
    ) {
        return toolSet.stream()
            .filter(stack ->
                stack.getMaxDamage() > 1
                    && stack.isIn(tag))
            .sorted(Comparator
                .comparingInt(
                    (ItemStack stack) ->
                        registeredToolEfficiency(stack)
                )
                .reversed()
                .thenComparing(
                    Comparator.comparingInt(
                        ItemStack::getMaxDamage
                    ).reversed()
                )
                .thenComparing(stack ->
                    Registries.ITEM.getId(
                        stack.getItem()
                    ).toString()))
            .map(ItemStack::copy)
            .findFirst()
            .orElse(null);
    }

    private MiningToolInventoryPlan.Tool<Item, ItemStack>
        miningInventoryTool(
            ItemStack stack,
            int unacknowledgedUses
        ) {
        Objects.requireNonNull(stack, "stack");
        int maximumDurability = stack.getMaxDamage();
        if (maximumDurability <= 1) {
            throw new IllegalArgumentException(
                "Strict mining inventory entries must be damageable tools."
            );
        }
        int remaining = Math.max(
            0,
            remainingToolDurability(stack) - unacknowledgedUses
        );
        return new MiningToolInventoryPlan.Tool<>(
            stack.getItem(),
            stack,
            remaining,
            maximumDurability
        );
    }

    private boolean ensureCircularMiningToolDurability(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return ensureMiningToolDurability(
            circularMiningInventoryTargets(route),
            "circular pair " + route.pairIndex()
                + " and its reach-assigned U routes"
        );
    }

    private boolean ensureIndependentMiningToolDurability(int line) {
        return ensureMiningToolDurability(
            independentMiningTargets(line),
            "independent line " + line
        );
    }

    private boolean ensureMiningToolDurability(
        Collection<BlockPos> targets,
        String traversalName
    ) {
        return ensureMiningToolDurability(
            targets,
            Map.of(),
            traversalName
        );
    }

    private boolean ensureMiningToolDurability(
        Collection<BlockPos> targets,
        Map<BlockPos, BlockState> plannedTargetStates,
        String traversalName
    ) {
        abandonRestockSession(true);
        debugRestock(
            "mining-tool restock planning traversal=" + traversalName
                + " targets=" + targets.size()
        );
        strictMiningRestockActive = false;
        strictMiningInventoryPlan =
            createMiningToolInventoryPlan(
                targets,
                plannedTargetStates
            );
        if (strictMiningInventoryPlan == null) return false;

        TeardownScaffoldMaterialPlan.Plan scaffoldReserve =
            teardownScaffoldMaterialPlan();
        LinkedHashSet<Integer> usedToolDepositSlots =
            teardownEntryUsedToolDepositSlots(
                strictMiningInventoryPlan
                    .compatibilityRequirements()
                    .keySet()
            );
        if (usedToolDepositSlots == null) return false;

        int freeSlots = 0;
        for (int slot : availableSlots) {
            if (slot < 0 || slot >= 36) continue;
            if (mc.player.getInventory().getStack(slot).isEmpty()) freeSlots++;
        }
        int requiredSlots =
            strictMiningInventoryPlan.restockDemands()
                .values()
                .stream()
                .mapToInt(RestockDemand::remainingAmount)
                .sum()
                + scaffoldReserve.additionalSlotsRequired();
        int effectiveFreeSlots = Math.addExact(
            freeSlots,
            usedToolDepositSlots.size()
        );
        debugRestock(
            "mining-tool capacity freeSlots=" + freeSlots
                + " retiringToolSlots="
                    + usedToolDepositSlots.size()
                + " effectiveFreeSlots=" + effectiveFreeSlots
                + " requiredToolSlots=" + requiredSlots
                + " scaffoldReserve="
                    + scaffoldReserve.onHandAmount() + "/"
                    + scaffoldReserve.targetAmount()
                + " scaffoldNewSlots="
                    + scaffoldReserve.additionalSlotsRequired()
                + " demandTypes="
                    + strictMiningInventoryPlan.restockDemands().size()
        );
        if (requiredSlots > effectiveFreeSlots) {
            error(
                traversalName + " needs " + requiredSlots
                    + " tool/scaffold slots, but only "
                    + effectiveFreeSlots
                    + " slots are or will become empty after retiring "
                    + "below-threshold tools. Stopping before mining."
            );
            return false;
        }

        for (RestockDemand<Item> demand
            : strictMiningInventoryPlan.restockDemands().values()) {
            if (demand.remainingAmount() == 0) continue;
            ArrayList<Pair<BlockPos, Vec3d>> chests =
                materialDict.get(demand.item());
            if (chests == null || chests.isEmpty()) {
                error(
                    "No registered tool chest can supply "
                        + demand.item().getName().getString() + "."
                );
                return false;
            }
            info(
                "%s",
                "Restocking §a" + demand.remainingAmount() + " usable "
                    + demand.item().getName().getString()
                    + " below the configured "
                    + String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        minimumToolDurabilityFraction() * 100.0
                    )
                    + " floor for " + traversalName
            );
            restockList.add(demand);
            debugRestock(
                "planned mining-tool demand item="
                    + Registries.ITEM.getId(demand.item())
                    + " target="
                        + demand.targetCompatiblePlayerCount()
                    + " remaining=" + demand.remainingAmount()
                    + " registeredChests=" + chests.size()
            );
        }
        if (scaffoldReserve.missingAmount() > 0) {
            ArrayList<Pair<BlockPos, Vec3d>> chests =
                materialDict.get(Items.COBBLESTONE);
            if (chests == null || chests.isEmpty()) {
                error(
                    "No registered cobblestone chest can supply the "
                        + teardownScaffoldStacks.get()
                        + "-stack teardown scaffold reserve."
                );
                return false;
            }
            RestockDemand<Item> scaffoldDemand =
                RestockDemand.fromOnHandAndMissing(
                    Items.COBBLESTONE,
                    scaffoldReserve.onHandAmount(),
                    scaffoldReserve.missingAmount()
                );
            restockList.add(scaffoldDemand);
            info(
                "Preloading Â§a"
                    + scaffoldDemand.remainingStacks(64)
                    + " cobblestone stacks for sparse teardown "
                    + "scaffold recovery"
            );
            debugRestock(
                "planned teardown scaffold reserve onHand="
                    + scaffoldReserve.onHandAmount()
                    + " target=" + scaffoldReserve.targetAmount()
                    + " missing=" + scaffoldReserve.missingAmount()
                    + " registeredChests=" + chests.size()
            );
        }
        strictMiningRestockActive = !restockList.isEmpty();
        debugRestock(
            "mining-tool restock plan complete active="
                + strictMiningRestockActive
                + " demands=" + restockList.size()
        );
        addClosestRestockCheckpoint();
        prependTeardownEntryUsedToolDeposits(
            usedToolDepositSlots
        );
        return true;
    }

    private void resetBuildActionState() {
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        workActionBudget = createWorkActionBudget();
        pendingPlacementLedger.reset();
        optionalPendingPlacements.clear();
        confirmedBuildTargetsThisRun.clear();
        plannedDeferredMandatoryBuildOrder.clear();
        plannedDeferredMandatoryBuildTargets.clear();
        plannedDeferredReachWindows.clear();
        optimizedCircularTraversalPairs.clear();
        optimizedDeferredBuildTargets.clear();
        optimizedDeferredRouteAssignments.clear();
        placementSubmissionBlockSequences.clear();
        repairSubmissionBlockSequences.clear();
        serverBlockObservations.clear();
        serverBlockUpdateSequence = 0L;
        confirmedBuildHotbarSwap.clear();
        confirmedMiningHotbarSwap.clear();
        clearPendingInventorySwapState();
        repairToolShadows.clear();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        releaseBuildRepairSpeedMine();
        buildRepairController.reset();
        lastPrintPauseReason =
            TpsScaledActionBudget.PauseReason.NONE;
        printActionTick = 0L;
        activeCircularPlacementCursor = -1;
        clearCircularBuildInventoryPlan();
        placementAttempts = 0L;
        confirmedPlacements = 0L;
        teardownScaffoldPlacementAttempts = 0L;
        confirmedTeardownScaffoldPlacements = 0L;
        repairBreakAttempts = 0L;
        confirmedRepairBreaks = 0L;
        teardownBreakAttempts = 0L;
        confirmedTeardownBreaks = 0L;
        lastActionBudgetDebugTick =
            clientActionTick - debugPrintInterval.get();
        lastActionBudgetDebugNanos = 0L;
        lastActionBudgetPlacementAttempts = 0L;
        lastActionBudgetConfirmedPlacements = 0L;
    }

    private boolean isLineMined(int line) {
        if (line < 0 || line >= map.length) return false;

        boolean isMined = true;
        for (int z = 0; z < map[line].length; z++) {
            BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(line, map[line][z].getRight(), z));
            if (!blockstate.isAir()) {
                isMined = false;
                break;
            }
        }
        return isMined;
    }

    private void startBuilding() {
        if (SlaveSystem.isFileMode() && activeConfigSha256 == null) {
            error(
                "File coordination requires every bot to load the same saved "
                    + "staircased config file before printing."
            );
            stopMovement();
            return;
        }
        if (SlaveSystem.isFileMode()
            && (!localPlayerWithinMapZone()
                || !isMapAreaDataLoaded())) {
            if (SlaveSystem.isFileMaster()) {
                state = State.AwaitFileSlaves;
            }
            stopMovement();
            if (!waitingForFilePeersNotice) {
                waitingForFilePeersNotice = true;
                warning(
                    "File-coordinated printing is waiting because this bot is "
                        + "outside the configured map-area margin or the "
                        + "full 128x128 map area has not loaded yet."
                );
            }
            return;
        }
        publishFileCoordinationState();
        if (SlaveSystem.isFileMaster()
            && requireFileSlavesReady.get()
            && !allFileSlavesReady()) {
            state = State.AwaitFileSlaves;
            buildingActive = false;
            stopMovement();
            if (!waitingForFilePeersNotice) {
                waitingForFilePeersNotice = true;
                warning(
                    "Waiting for every configured file slave to load NBT generation "
                        + coordinationGeneration
                        + " and publish a fresh ready heartbeat."
                );
            }
            return;
        }
        waitingForFilePeersNotice = false;
        if (SlaveSystem.hasPendingRemoval()) {
            boolean firstWait = state != State.AwaitSlaveRemoval;
            state = State.AwaitSlaveRemoval;
            stopMovement();
            if (firstWait) {
                warning(
                    "Waiting for pending slave removal before starting the map. "
                        + "Wait for its acknowledgement, or force-remove only if it is offline."
                );
            }
            return;
        }
        if (availableSlots.isEmpty() && !setupSlots()) return;
        applyPendingInterval();
        circularTraversalForCurrentMap = circularTraversal.get();
        buildingActive = true;
        resetBuildActionState();
        buildRecoveryPending = false;
        buildRecoveryNeedsInventory = false;
        buildRecoveryRestockAfterEgress = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        resetMapAreaCache();
        configurePairTraversalModes();
        if (!validateCompactWorkspace()) {
            buildingActive = false;
            state = State.AwaitCompactWorkspace;
            timeoutTicks = 100;
            stopMovement();
            return;
        }
        calculateBuildingPath(true);
        if (circularTraversalForCurrentMap
            && !prepareNextCircularBuildInventoryPlan()
            && requireCompleteUInventory.get()) {
            buildingActive = false;
            stopMovement();
            toggle();
            return;
        }
        mapCyclePhase = MapCyclePhase.BUILDING;
        if (cycleStartedAtMs < 0) {
            cycleStartedAtMs = System.currentTimeMillis();
        }
        if (!persistFileCoordinationCheckpoint("build-start")) return;
        info("Start building map");
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        prependPlannedBuildUsedToolDeposits();
        if (sleep.get()) {
            if (bed == null) {
                warning("Can not sleep because bed was not set.");
            } else {
                checkpoints.add(0, new Pair(bed.getRight(), new Pair("sleep", null)));
            }
        }
        state = State.Walking;
    }

    private void configurePairTraversalModes() {
        circularPairModes.clear();

        int circularPairs = 0;
        int independentPairs = 0;
        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            boolean assignedToThisBot = route.outboundX() >= workingInterval.getLeft()
                && route.returnX() <= workingInterval.getRight();
            boolean pairFits = assignedToThisBot
                && pairFitsUsableInventory(route);
            boolean useCircular = assignedToThisBot
                && CircularBuildAssignmentPolicy.useCircular(
                    true,
                    circularTraversalForCurrentMap,
                    pairFits || requireCompleteUInventory.get()
                );
            circularPairModes.put(route.pairIndex(), useCircular);

            if (useCircular) {
                circularPairs++;
            } else {
                if (assignedToThisBot) independentPairs++;
            }
        }
        rebuildActiveBuildTargets();

        info(
            "Traversal analysis: §a" + circularPairs + " circular pairs, "
                + independentPairs + " independent pairs"
        );
    }

    private void rebuildActiveBuildTargets() {
        orderedBuildTargets.clear();
        activeConnectorTargets.clear();
        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            boolean assignedToThisBot = route.outboundX() >= workingInterval.getLeft()
                && route.returnX() <= workingInterval.getRight();
            if (!assignedToThisBot) continue;

            for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
                addActiveSurfaceTarget(route.outboundX(), nbtZ);
            }
            if (circularPairModes.getOrDefault(route.pairIndex(), false)) {
                for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
                    BlockPos relative = connectorRuntimePosition(connector);
                    addActiveBuildTarget(relative);
                    activeConnectorTargets.add(relative);
                }
                for (int nbtZ = CompactCircularNbtPlan.FAR_Z; nbtZ >= 1; nbtZ--) {
                    addActiveSurfaceTarget(route.returnX(), nbtZ);
                }
            } else {
                for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
                    addActiveSurfaceTarget(route.returnX(), nbtZ);
                }
            }
        }
    }

    private boolean pairFitsUsableInventory(CompactCircularNbtPlan.PairRoute route) {
        return createCircularInventoryPlan(
            route,
            false,
            List.of(),
            false
        ).plan().primaryFits();
    }

    private boolean prepareCircularBuildInventoryPlan(
        CompactCircularNbtPlan.PairRoute route
    ) {
        if (repairCurrentUPair.get()) {
            for (BlockPos relative : circularPairTargets(route)) {
                Block expected = buildTargets.get(relative);
                BlockState current = MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                );
                if (expected == null
                    || current.isAir()
                    || current.getBlock() == expected) {
                    continue;
                }
                ItemStack repairTool =
                    getBestRegisteredTool(current);
                if (repairTool == null
                    || repairTool.getMaxDamage() <= 1) {
                    error(
                        "Circular pair " + route.pairIndex()
                            + " contains a wrong block at "
                            + mapCorner.add(relative).toShortString()
                            + " but no registered repair tool can mine it."
                    );
                    return false;
                }
            }
            for (BlockPos relative :
                circularPairTargets(route)) {
                for (int offset = 1; offset <= 2; offset++) {
                    BlockPos obstructionRelative =
                        relative.up(offset);
                    BlockPos obstruction =
                        mapCorner.add(obstructionRelative);
                    BlockState obstructionState =
                        MapAreaCache.getCachedBlockState(
                            obstruction
                        );
                    if (obstructionState.isAir()) continue;
                    ItemStack repairTool =
                        getBestRegisteredTool(obstructionState);
                    if (buildTargets.containsKey(
                            obstructionRelative
                        )
                        || repairTool == null
                        || repairTool.getMaxDamage() <= 1
                        || !BlockUtils.canBreak(
                            obstruction,
                            obstructionState
                        )) {
                        error(
                            "Circular pair "
                                + route.pairIndex()
                                + " contains unrepairable blocked "
                                + "headroom at "
                                + obstruction.toShortString() + "."
                        );
                        return false;
                    }
                }
            }
        }
        List<BlockPos> deferredMandatoryTargets =
            optimizedDeferredBuildTargets.getOrDefault(
                route.pairIndex(),
                List.of()
            );
        CircularInventoryPlanningResult result =
            createCircularInventoryPlan(
                route,
                nearbyRangePlacement.get(),
                deferredMandatoryTargets
            );
        plannedCircularBuildPair = route.pairIndex();
        plannedPrimaryMaterialDemand.clear();
        plannedPrimaryMaterialDemand.putAll(
            circularPairMaterialDemand(
                route,
                deferredMandatoryTargets
            )
        );
        confirmedPrimaryMaterialUses.clear();
        plannedOptionalMaterialDemand.clear();
        plannedOptionalMaterialDemand.putAll(
            result.plan().optionalDemand()
        );
        plannedRepairToolDemand.clear();
        plannedRepairToolDemand.putAll(result.repairToolDemand());
        plannedRepairMinimumEfficiency.clear();
        plannedRepairMinimumEfficiency.putAll(
            result.repairMinimumEfficiency()
        );
        plannedRepairToolCompatibilityRequirements.clear();
        result.repairToolCompatibilityRequirements().forEach(
            (item, requirements) ->
                plannedRepairToolCompatibilityRequirements.put(
                    item,
                    List.copyOf(requirements)
                )
        );
        plannedRepairToolKeepSlots.clear();
        plannedRepairToolKeepSlots.addAll(
            result.repairToolKeepSlots()
        );
        plannedBuildToolKeepSlots.clear();
        plannedBuildToolKeepSlots.addAll(
            result.buildToolKeepSlots()
        );
        plannedBuildUsedToolDepositSlots.clear();
        plannedBuildUsedToolDepositSlots.addAll(
            result.buildUsedToolDepositSlots()
        );
        plannedRepairTargets.clear();
        plannedRepairTargets.addAll(result.repairTargets());
        plannedClearOnlyRepairTargets.clear();
        plannedClearOnlyRepairTargets.addAll(
            result.clearOnlyRepairTargets()
        );
        plannedOptionalBuildOrder.clear();
        plannedOptionalBuildOrder.addAll(
            result.plan().plannedOptionalKeys()
        );
        plannedOptionalBuildTargets.clear();
        plannedOptionalBuildTargets.addAll(
            result.plan().plannedOptionalKeys()
        );
        plannedDeferredMandatoryBuildOrder.clear();
        plannedDeferredMandatoryBuildOrder.addAll(
            deferredMandatoryTargets
        );
        plannedDeferredMandatoryBuildTargets.clear();
        plannedDeferredMandatoryBuildTargets.addAll(
            deferredMandatoryTargets
        );
        plannedDeferredReachWindows.clear();
        for (BlockPos relative : deferredMandatoryTargets) {
            BlockPos world = mapCorner.add(relative);
            if (optionalPendingPlacements.remove(world)) {
                pendingPlacementLedger.remove(world);
                placementSubmissionBlockSequences.remove(world);
                debugLog(
                    "TraversalPlan",
                    "promoted optional target to fresh mandatory "
                        + "reach-window ownership position="
                        + world.toShortString()
                );
            }
            Optional<BlockReachWindow.Window> window =
                deferredReachWindow(relative, route);
            if (window.isEmpty()) {
                error(
                    "Deferred U target "
                        + mapCorner.add(relative).toShortString()
                        + " lost its proven reach window for circular pair "
                        + route.pairIndex() + "."
                );
                return false;
            }
            plannedDeferredReachWindows.put(
                relative,
                window.get()
            );
        }
        plannedBuildHotbarStackItems.clear();
        plannedBuildHotbarStackItems.addAll(
            result.buildHotbarStackItems()
        );
        plannedBuildMaterialHotbarSlots.clear();
        plannedBuildToolHotbarSlot = -1;
        plannedBuildHotbarPair = -1;
        plannedBuildHotbarAssignments.clear();
        rejectedOptionalSwapMaterials.clear();

        if (!result.plan().primaryFits()) {
            error(
                "Circular pair " + route.pairIndex() + " needs "
                    + result.plan().primarySlotsRequired()
                    + " usable inventory slots including repair tools, but only "
                    + result.plan().usableSlots() + " are available."
            );
            return false;
        }
        if (debugPrints.get()) {
            long plannedConnectorTargets =
                plannedOptionalBuildOrder.stream()
                    .filter(connectorTargets::contains)
                    .count();
            debugLog(
                "InventoryPlan",
                "Pair " + route.pairIndex() + " inventory plan: "
                    + result.plan().primarySlotsRequired() + " guaranteed U slots, "
                    + result.plan().totalSlotsRequired() + "/"
                        + result.plan().usableSlots()
                        + " managed slots occupied, "
                    + result.plan().remainingSlots() + " unused slots, "
                    + plannedOptionalBuildOrder.size()
                        + "/"
                        + result.plan().optionalCandidateCount()
                        + " forward missing targets planned, "
                    + plannedConnectorTargets
                        + " connector targets included, "
                    + plannedDeferredMandatoryBuildOrder.size()
                        + " earlier-U targets mandatory, "
                    + plannedClearOnlyRepairTargets.size()
                        + " headroom obstructions owned for "
                        + "server-confirmed clearing."
            );
        }
        return true;
    }

    private List<
        DependencyClosedOptionalInventoryPlan.Target<BlockPos, Item>
    > optionalInventoryTargets(
        List<BlockPos> candidates
    ) {
        HashSet<BlockPos> candidateSet =
            new HashSet<>(candidates);
        ArrayList<
            DependencyClosedOptionalInventoryPlan.Target<BlockPos, Item>
        > targets = new ArrayList<>(candidates.size());
        for (BlockPos relative : candidates) {
            Block expected = buildTargets.get(relative);
            if (expected == null) {
                throw new IllegalStateException(
                    "Nearby inventory planning found an unknown target at "
                        + relative.toShortString() + "."
                );
            }

            // Every candidate belongs to the frozen, capacity-bounded forward
            // route plan. THM smart-air placement can establish its first
            // support without requiring an equal-height neighbor from the
            // active U.
            boolean initiallyAnchored = true;
            HashSet<BlockPos> optionalAnchors = new HashSet<>();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = relative.offset(direction);
                if (candidateSet.contains(neighbor)) {
                    optionalAnchors.add(neighbor);
                }
            }
            targets.add(
                new DependencyClosedOptionalInventoryPlan.Target<>(
                    relative,
                    expected.asItem(),
                    initiallyAnchored,
                    optionalAnchors
                )
            );
        }
        return List.copyOf(targets);
    }

    private boolean prepareNextCircularBuildInventoryPlan() {
        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()
                || !circularPairModes.getOrDefault(route.pairIndex(), false)
                || !optimizedCircularTraversalPairs.contains(
                    route.pairIndex()
                )) {
                continue;
            }
            return prepareCircularBuildInventoryPlan(route);
        }
        clearCircularBuildInventoryPlan();
        return true;
    }

    private void trimUnavailableOptionalBuildDemand(
        Item material,
        int authoritativeOnHand,
        int mandatoryTarget
    ) {
        if (plannedCircularBuildPair < 0
            || material == null
            || authoritativeOnHand < mandatoryTarget) {
            return;
        }
        int desiredOptional =
            plannedOptionalMaterialDemand.getOrDefault(material, 0);
        if (desiredOptional <= 0) return;

        int retainedOptional = Math.min(
            desiredOptional,
            Math.max(0, authoritativeOnHand - mandatoryTarget)
        );
        ArrayList<BlockPos> retainedOrder = new ArrayList<>(
            plannedOptionalBuildOrder.size()
        );
        int retainedForMaterial = 0;
        int removedTargets = 0;
        for (BlockPos relative : plannedOptionalBuildOrder) {
            Block expected = buildTargets.get(relative);
            if (expected == null
                || expected.asItem() != material) {
                retainedOrder.add(relative);
                continue;
            }
            if (retainedForMaterial < retainedOptional) {
                retainedOrder.add(relative);
                retainedForMaterial++;
            } else {
                removedTargets++;
            }
        }
        plannedOptionalBuildOrder.clear();
        plannedOptionalBuildOrder.addAll(retainedOrder);
        plannedOptionalBuildTargets.clear();
        plannedOptionalBuildTargets.addAll(retainedOrder);
        if (retainedForMaterial > 0) {
            plannedOptionalMaterialDemand.put(
                material,
                retainedForMaterial
            );
        } else {
            plannedOptionalMaterialDemand.remove(material);
        }
        rebuildFrozenBuildHotbarStackItems();
        debugLog(
            "InventoryPlan",
            "contracted unavailable optional demand item="
                + Registries.ITEM.getId(material)
                + " desired=" + desiredOptional
                + " retained=" + retainedForMaterial
                + " removedTargets=" + removedTargets
                + " authoritativeOnHand=" + authoritativeOnHand
                + " mandatoryTarget=" + mandatoryTarget
        );
    }

    private void rebuildFrozenBuildHotbarStackItems() {
        if (plannedCircularBuildPair < 0
            || plannedCircularBuildPair
                >= compactPlan.pairRoutes().size()) {
            return;
        }
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(plannedCircularBuildPair);
        ConfirmedBuildInventoryDemand.Result<BlockPos, Item>
            mandatoryDemand = outstandingCircularRouteDemand(
                route,
                plannedDeferredMandatoryBuildOrder
            );
        ArrayList<Item> primaryUses = new ArrayList<>(
            mandatoryDemand.outstandingMaterials()
        );
        ArrayList<Item> optionalUses = new ArrayList<>();
        for (BlockPos relative : plannedOptionalBuildOrder) {
            Block expected = buildTargets.get(relative);
            if (expected != null) {
                optionalUses.add(expected.asItem());
            }
        }
        HashMap<Item, Integer> stackSizes = new HashMap<>();
        for (Item item : primaryUses) {
            stackSizes.put(item, Utils.maximumStackSize(item));
        }
        for (Item item : optionalUses) {
            stackSizes.put(item, Utils.maximumStackSize(item));
        }
        plannedBuildHotbarStackItems.clear();
        plannedBuildHotbarStackItems.addAll(
            PhaseHotbarPlan.orderedStackUnits(
                primaryUses,
                optionalUses,
                stackSizes,
                BUILD_MATERIAL_HOTBAR_SLOT_COUNT
            )
        );
        while (plannedBuildHotbarStackItems.size()
            < BUILD_MATERIAL_HOTBAR_SLOT_COUNT) {
            plannedBuildHotbarStackItems.add(Items.AIR);
        }
        plannedBuildMaterialHotbarSlots.clear();
        plannedBuildToolHotbarSlot = -1;
        plannedBuildHotbarPair = -1;
        plannedBuildHotbarAssignments.clear();
        rejectedOptionalSwapMaterials.clear();
    }

    private void clearCircularBuildInventoryPlan() {
        plannedCircularBuildPair = -1;
        plannedPrimaryMaterialDemand.clear();
        confirmedPrimaryMaterialUses.clear();
        plannedOptionalMaterialDemand.clear();
        plannedRepairToolDemand.clear();
        plannedRepairMinimumEfficiency.clear();
        plannedRepairToolCompatibilityRequirements.clear();
        plannedRepairToolKeepSlots.clear();
        plannedBuildToolKeepSlots.clear();
        plannedBuildUsedToolDepositSlots.clear();
        plannedRepairTargets.clear();
        plannedClearOnlyRepairTargets.clear();
        plannedOptionalBuildOrder.clear();
        plannedOptionalBuildTargets.clear();
        plannedDeferredMandatoryBuildOrder.clear();
        plannedDeferredMandatoryBuildTargets.clear();
        plannedDeferredReachWindows.clear();
        plannedBuildHotbarStackItems.clear();
        plannedBuildMaterialHotbarSlots.clear();
        plannedBuildToolHotbarSlot = -1;
        plannedBuildHotbarPair = -1;
        plannedBuildHotbarAssignments.clear();
        rejectedOptionalSwapMaterials.clear();
    }

    private CircularInventoryPlanningResult createCircularInventoryPlan(
        CompactCircularNbtPlan.PairRoute route,
        boolean includeOptional
    ) {
        return createCircularInventoryPlan(
            route,
            includeOptional,
            optimizedDeferredBuildTargets.getOrDefault(
                route.pairIndex(),
                List.of()
            ),
            true
        );
    }

    private CircularInventoryPlanningResult createCircularInventoryPlan(
        CompactCircularNbtPlan.PairRoute route,
        boolean includeOptional,
        List<BlockPos> deferredMandatoryTargets
    ) {
        return createCircularInventoryPlan(
            route,
            includeOptional,
            deferredMandatoryTargets,
            true
        );
    }

    private CircularInventoryPlanningResult createCircularInventoryPlan(
        CompactCircularNbtPlan.PairRoute route,
        boolean includeOptional,
        List<BlockPos> deferredMandatoryTargets,
        boolean emitDiagnostics
    ) {
        ConfirmedBuildInventoryDemand.Result<BlockPos, Item>
            routeDemand = outstandingCircularRouteDemand(
                route,
                deferredMandatoryTargets
            );
        ArrayList<Item> primaryMaterials = new ArrayList<>(
            routeDemand.outstandingMaterials()
        );
        HashSet<BlockPos> outstandingPrimaryTargets = new HashSet<>(
            routeDemand.outstandingKeys()
        );
        LinkedHashSet<Item> requiredRepairTools =
            new LinkedHashSet<>();
        HashMap<Item, Integer> repairMinimumEfficiency =
            new HashMap<>();
        LinkedHashMap<Item, List<MiningToolRequirement>>
            repairCompatibilityRequirements = new LinkedHashMap<>();
        HashSet<BlockPos> repairTargets = new HashSet<>();
        HashSet<BlockPos> clearOnlyRepairTargets =
            new HashSet<>();

        for (BlockPos relative : circularPairTargets(route)) {
            Block expected = buildTargets.get(relative);
            if (expected == null) {
                throw new IllegalStateException(
                    "Circular inventory planning found an unknown target at "
                        + relative.toShortString() + "."
                );
            }
            BlockState current =
                MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (repairCurrentUPair.get()
                && !current.isAir()
                && current.getBlock() != expected) {
                if (addRepairToolRequirement(
                    current,
                    requiredRepairTools,
                    repairMinimumEfficiency,
                    repairCompatibilityRequirements
                )) {
                    repairTargets.add(relative);
                }
            }
            if (!repairCurrentUPair.get()) continue;
            for (int offset = 1; offset <= 2; offset++) {
                BlockPos obstructionRelative =
                    relative.up(offset);
                BlockPos obstructionWorld =
                    mapCorner.add(obstructionRelative);
                BlockState obstructionState =
                    MapAreaCache.getCachedBlockState(
                        obstructionWorld
                    );
                if (obstructionState.isAir()
                    || buildTargets.containsKey(
                        obstructionRelative
                    )
                    || clearOnlyRepairTargets.contains(
                        obstructionRelative
                    )) {
                    continue;
                }
                if (addRepairToolRequirement(
                    obstructionState,
                    requiredRepairTools,
                    repairMinimumEfficiency,
                    repairCompatibilityRequirements
                )) {
                    clearOnlyRepairTargets.add(
                        obstructionRelative
                    );
                }
            }
        }

        if (repairCurrentUPair.get()) {
            addBaselineRepairToolRequirements(requiredRepairTools);
        }

        ArrayList<CriticalToolCarryPlan.ToolStack<Item>>
            inventoryTools = new ArrayList<>();
        for (int slot : availableSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!ToolUtils.isTool(stack)
                || stack.getMaxDamage() <= 1) {
                continue;
            }
            List<MiningToolRequirement> requirementsForItem =
                repairCompatibilityRequirements.getOrDefault(
                    stack.getItem(),
                    List.of()
                );
            boolean compatible =
                !requiredRepairTools.contains(stack.getItem())
                    || requirementsForItem.isEmpty()
                    || (getEfficiencyLevel(stack)
                            >= repairMinimumEfficiency.getOrDefault(
                                stack.getItem(),
                                0
                            )
                        && requirementsForItem.stream().allMatch(
                            requirement -> isCompatibleMiningTool(
                                stack,
                                requirement.registeredTemplate(),
                                requirement.targetState()
                            )
                        ));
            inventoryTools.add(
                new CriticalToolCarryPlan.ToolStack<>(
                    slot,
                    stack.getItem(),
                    remainingToolDurability(stack),
                    stack.getMaxDamage(),
                    compatible
                )
            );
        }
        CriticalToolCarryPlan.Result<Item> toolCarryPlan =
            CriticalToolCarryPlan.plan(
                requiredRepairTools,
                inventoryTools,
                minimumToolDurabilityFraction()
            );
        HashMap<Item, Integer> repairToolDemand = new HashMap<>(
            toolCarryPlan.requiredItemCounts()
        );
        if (emitDiagnostics) {
            debugLog(
                "Inventory",
                "critical tool carry minimumRemaining="
                    + String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        minimumToolDurabilityFraction() * 100.0
                    )
                    + " required=" + repairToolDemand
                    + " selectedSlots="
                        + toolCarryPlan.requiredKeepSlots()
                    + " reusableSlots=" + toolCarryPlan.keepSlots()
                    + " usedSlots=" + toolCarryPlan.usedToolSlots()
            );
        }

        for (Map.Entry<Item, Integer> entry
            : repairToolDemand.entrySet()) {
            for (int count = 0; count < entry.getValue(); count++) {
                primaryMaterials.add(entry.getKey());
            }
        }

        ArrayList<BlockPos> optionalTargets = includeOptional
            ? forwardOptionalBuildTargets(route)
            : new ArrayList<>();
        List<
            DependencyClosedOptionalInventoryPlan.Target<BlockPos, Item>
        > optionalInventoryTargets =
            optionalInventoryTargets(optionalTargets);

        HashMap<Item, Integer> stackSizes = new HashMap<>();
        for (Item item : primaryMaterials) {
            stackSizes.put(item, Utils.maximumStackSize(item));
        }
        for (DependencyClosedOptionalInventoryPlan.Target<BlockPos, Item>
            target : optionalInventoryTargets) {
            stackSizes.put(
                target.material(),
                Utils.maximumStackSize(target.material())
            );
        }

        DependencyClosedOptionalInventoryPlan.Result<BlockPos, Item> plan =
            DependencyClosedOptionalInventoryPlan.plan(
            primaryMaterials,
            optionalInventoryTargets,
            stackSizes,
            Math.max(
                0,
                availableSlots.size()
                    - (int) toolCarryPlan.keepSlots().stream()
                        .filter(slot ->
                            !toolCarryPlan.requiredKeepSlots()
                                .contains(slot))
                        .count()
                    - (repairToolDemand.isEmpty() ? 1 : 0)
            )
        );
        ArrayList<Item> primaryHotbarUses = new ArrayList<>();
        for (BlockPos relative : mandatoryCircularBuildTargets(
            route,
            deferredMandatoryTargets
        )) {
            Block expected = buildTargets.get(relative);
            if (expected != null
                && outstandingPrimaryTargets.contains(relative)) {
                primaryHotbarUses.add(expected.asItem());
            }
        }
        ArrayList<Item> optionalHotbarUses = new ArrayList<>();
        for (BlockPos relative : plan.plannedOptionalKeys()) {
            Block expected = buildTargets.get(relative);
            if (expected != null) {
                optionalHotbarUses.add(expected.asItem());
            }
        }
        ArrayList<Item> buildHotbarStackItems = new ArrayList<>(
            PhaseHotbarPlan.orderedStackUnits(
                primaryHotbarUses,
                optionalHotbarUses,
                stackSizes,
                BUILD_MATERIAL_HOTBAR_SLOT_COUNT
            )
        );
        while (buildHotbarStackItems.size()
            < BUILD_MATERIAL_HOTBAR_SLOT_COUNT) {
            buildHotbarStackItems.add(Items.AIR);
        }
        return new CircularInventoryPlanningResult(
            plan,
            Map.copyOf(repairToolDemand),
            Map.copyOf(repairMinimumEfficiency),
            repairCompatibilityRequirements,
            toolCarryPlan.requiredKeepSlots(),
            toolCarryPlan.keepSlots(),
            toolCarryPlan.usedToolSlots(),
            Set.copyOf(repairTargets),
            Set.copyOf(clearOnlyRepairTargets),
            buildHotbarStackItems
        );
    }

    private void addBaselineRepairToolRequirements(
        Set<Item> requiredItems
    ) {
        toolSet.stream()
            .filter(registered ->
                registered.getMaxDamage() > 1
                    && (registered.isIn(ItemTags.PICKAXES)
                        || registered.isIn(ItemTags.AXES))
            )
            .sorted(Comparator.comparing(registered ->
                Registries.ITEM.getId(registered.getItem()).toString()
            ))
            .map(ItemStack::getItem)
            .forEach(requiredItems::add);
    }

    private boolean addRepairToolRequirement(
        BlockState targetState,
        Set<Item> requiredItems,
        Map<Item, Integer> minimumEfficiency,
        Map<Item, List<MiningToolRequirement>>
            compatibilityRequirements
    ) {
        ItemStack tool = getBestRegisteredTool(targetState);
        if (tool == null || tool.getMaxDamage() <= 1) return false;

        Item toolItem = tool.getItem();
        minimumEfficiency.merge(
            toolItem,
            registeredToolEfficiency(tool),
            Math::max
        );
        ArrayList<MiningToolRequirement> compatible =
            new ArrayList<>(
                compatibilityRequirements.getOrDefault(
                    toolItem,
                    List.of()
                )
            );
        compatible.add(
            new MiningToolRequirement(
                tool.copy(),
                targetState
            )
        );
        compatibilityRequirements.put(
            toolItem,
            List.copyOf(compatible)
        );
        requiredItems.add(toolItem);
        return true;
    }

    private ArrayList<BlockPos> forwardOptionalBuildTargets(
        CompactCircularNbtPlan.PairRoute activeRoute
    ) {
        ArrayList<BlockPos> candidates = new ArrayList<>();
        HashSet<Integer> optionalColumns = new HashSet<>(
            CircularBuildHorizon.forwardOptionalColumns(
                activeRoute.outboundX(),
                activeRoute.returnX(),
                workingInterval.getLeft(),
                workingInterval.getRight(),
                CompactCircularNbtPlan.MAP_WIDTH
            )
        );
        for (CompactCircularNbtPlan.PairRoute futureRoute
            : compactPlan.pairRoutes()) {
            if (futureRoute.outboundX() <= activeRoute.returnX()
                || !optionalColumns.contains(futureRoute.outboundX())
                || !optionalColumns.contains(futureRoute.returnX())) {
                continue;
            }

            if (circularPairModes.getOrDefault(
                futureRoute.pairIndex(),
                false
            )) {
                for (BlockPos relative :
                    circularPairTargets(futureRoute)) {
                    addForwardOptionalTarget(relative, candidates);
                }
                continue;
            }

            for (int nbtZ = 1;
                 nbtZ <= CompactCircularNbtPlan.FAR_Z;
                 nbtZ++) {
                addForwardOptionalTarget(
                    surfaceRuntimePosition(
                        futureRoute.outboundX(),
                        nbtZ
                    ),
                    candidates
                );
            }
            for (int nbtZ = 1;
                 nbtZ <= CompactCircularNbtPlan.FAR_Z;
                 nbtZ++) {
                addForwardOptionalTarget(
                    surfaceRuntimePosition(
                        futureRoute.returnX(),
                        nbtZ
                    ),
                    candidates
                );
            }
        }
        return candidates;
    }

    private void addForwardOptionalTarget(
        BlockPos relative,
        List<BlockPos> candidates
    ) {
        if (!isInWorkingInterval(relative)
            || !buildTargets.containsKey(relative)) {
            return;
        }

        // Correct blocks need no material. Wrong future blocks remain outside
        // optional repair ownership and are handled when their U becomes
        // primary. Only genuinely missing air consumes forward capacity.
        if (latestKnownBuildBlock(mapCorner.add(relative)) != Blocks.AIR) {
            return;
        }
        candidates.add(relative);
    }

    private HashMap<Item, Integer> circularPairMaterialDemand(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return circularPairMaterialDemand(
            route,
            optimizedDeferredBuildTargets.getOrDefault(
                route.pairIndex(),
                List.of()
            )
        );
    }

    private HashMap<Item, Integer> circularPairMaterialDemand(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> deferredMandatoryTargets
    ) {
        HashMap<Item, Integer> demand = new HashMap<>();
        for (Item material :
            outstandingCircularRouteDemand(
                route,
                deferredMandatoryTargets
            )
                .outstandingMaterials()) {
            demand.merge(material, 1, Integer::sum);
        }
        return demand;
    }

    private ConfirmedBuildInventoryDemand.Result<BlockPos, Item>
        outstandingCircularRouteDemand(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return outstandingCircularRouteDemand(
            route,
            optimizedDeferredBuildTargets.getOrDefault(
                route.pairIndex(),
                List.of()
            )
        );
    }

    private ConfirmedBuildInventoryDemand.Result<BlockPos, Item>
        outstandingCircularRouteDemand(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> deferredMandatoryTargets
    ) {
        ArrayList<
            ConfirmedBuildInventoryDemand.Target<BlockPos, Item>
        > targets = new ArrayList<>();
        for (BlockPos relative : mandatoryCircularBuildTargets(
            route,
            deferredMandatoryTargets
        )) {
            Block expected = buildTargets.get(relative);
            if (expected == null) {
                throw new IllegalStateException(
                    "Circular demand references an unknown target at "
                        + relative.toShortString() + "."
                );
            }
            targets.add(
                new ConfirmedBuildInventoryDemand.Target<>(
                    relative,
                    expected.asItem()
                )
            );
        }
        return ConfirmedBuildInventoryDemand.resolve(
            targets,
            confirmedBuildTargetsThisRun,
            relative -> {
                Block expected = buildTargets.get(relative);
                return expected != null
                    && latestKnownBuildBlock(mapCorner.add(relative))
                        == expected;
            }
        );
    }

    private List<BlockPos> mandatoryCircularBuildTargets(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> deferredMandatoryTargets
    ) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(
            deferredMandatoryTargets,
            "deferredMandatoryTargets"
        );
        ArrayList<BlockPos> targets = circularPairTargets(route);
        HashSet<BlockPos> seen = new HashSet<>(targets);
        for (BlockPos relative : deferredMandatoryTargets) {
            BlockPos candidate =
                Objects.requireNonNull(relative, "deferred target");
            if (!seen.add(candidate)) {
                throw new IllegalArgumentException(
                    "Deferred target duplicates active circular U: "
                        + candidate.toShortString()
                );
            }
            targets.add(candidate);
        }
        return List.copyOf(targets);
    }

    private boolean hasPartialConnector(CompactCircularNbtPlan.PairRoute route) {
        int correct = 0;
        int occupied = 0;
        int total = route.relativeInterior().size();
        for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
            BlockPos world = mapCorner.add(connectorRuntimePosition(connector));
            BlockState state = MapAreaCache.getCachedBlockState(world);
            if (state.isAir()) continue;
            occupied++;
            if (state.getBlock() == Blocks.COBBLESTONE) correct++;
        }
        return occupied > 0 && (occupied != total || correct != total);
    }

    private boolean hasAnyConnector(CompactCircularNbtPlan.PairRoute route) {
        for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
            if (!MapAreaCache.getCachedBlockState(
                mapCorner.add(connectorRuntimePosition(connector))
            ).isAir()) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<BlockPos> circularPairTargets(CompactCircularNbtPlan.PairRoute route) {
        ArrayList<BlockPos> targets = new ArrayList<>(256 + route.relativeInterior().size());
        for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
            targets.add(surfaceRuntimePosition(route.outboundX(), nbtZ));
        }
        for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
            targets.add(connectorRuntimePosition(connector));
        }
        for (int nbtZ = CompactCircularNbtPlan.FAR_Z; nbtZ >= 1; nbtZ--) {
            targets.add(surfaceRuntimePosition(route.returnX(), nbtZ));
        }
        return targets;
    }

    private void addActiveSurfaceTarget(int x, int nbtZ) {
        addActiveBuildTarget(surfaceRuntimePosition(x, nbtZ));
    }

    private void addActiveBuildTarget(BlockPos relative) {
        if (!buildTargets.containsKey(relative)) {
            throw new IllegalStateException("Active traversal references an unknown build target.");
        }
        orderedBuildTargets.add(relative);
    }

    private BlockPos surfaceRuntimePosition(int x, int nbtZ) {
        return new BlockPos(x, compactPlan.targetSurfaceY(x, nbtZ), nbtZ - 1);
    }

    private BlockPos connectorRuntimePosition(CompactCircularNbtPlan.Position connector) {
        return new BlockPos(connector.x(), connector.y(), connector.z() - 1);
    }

    private boolean ensureNorthWalkwayResolved() {
        if (northWalkwayRelativeY != null) return true;
        if (compactPlan == null || mapCorner == null) {
            error("Cannot resolve the compact north walkway before the map is loaded.");
            return false;
        }

        int[] firstVisibleRowY = new int[CompactCircularNbtPlan.MAP_WIDTH];
        for (int x = 0; x < CompactCircularNbtPlan.MAP_WIDTH; x++) {
            firstVisibleRowY[x] = compactPlan.targetSurfaceY(x, 1);
        }

        CompactNorthWalkwayResolver.Resolution resolution =
            CompactNorthWalkwayResolver.resolve(
                firstVisibleRowY,
                0,
                CompactCircularNbtPlan.MAP_WIDTH - 1,
                (x, relativeY) -> {
                    BlockPos support = mapCorner.add(x, relativeY, -1);
                    if (!MapAreaCache.hasBlockData(support)) {
                        return CompactNorthWalkwayResolver.Cell.UNAVAILABLE;
                    }
                    boolean safe =
                        MapAreaCache.getCachedBlockState(support).getBlock()
                            == Blocks.COBBLESTONE
                            && MapAreaCache.getCachedBlockState(support.up()).isAir()
                            && MapAreaCache.getCachedBlockState(support.up(2)).isAir();
                    return safe
                        ? CompactNorthWalkwayResolver.Cell.SAFE
                        : CompactNorthWalkwayResolver.Cell.UNSAFE;
                }
            );

        if (resolution.resolved()) {
            northWalkwayRelativeY = resolution.relativeY();
            info(
                "Compact north walkway validated at world Y §a"
                    + (mapCorner.getY() + northWalkwayRelativeY)
                    + "§r (relative " + northWalkwayRelativeY + ")."
            );
            return true;
        }

        String candidateWorldYs = resolution.candidates().stream()
            .map(relativeY -> Integer.toString(mapCorner.getY() + relativeY))
            .collect(Collectors.joining(", "));
        switch (resolution.status()) {
            case UNAVAILABLE -> error(
                "Could not validate the compact north walkway because one or more "
                    + "north-row chunks are unavailable. Load the entire map area first."
            );
            case AMBIGUOUS -> {
                String safeWorldYs = resolution.safeRows().stream()
                    .map(relativeY -> Integer.toString(mapCorner.getY() + relativeY))
                    .collect(Collectors.joining(", "));
                error(
                    "Compact north walkway is ambiguous: safe cobblestone rows exist "
                        + "at world Y " + safeWorldYs + ". Leave exactly one walkable row."
                );
            }
            case NO_WALKABLE_HEIGHT -> error(
                "No flat north-walkway height is within one block of every first map row."
            );
            case NO_SAFE_ROW -> error(
                "No safe compact north walkway was found at Z "
                    + (mapCorner.getZ() - 1)
                    + " on candidate world Y layer"
                    + (resolution.candidates().size() == 1 ? " " : "s ")
                    + candidateWorldYs
                    + ". It must be a complete cobblestone row with two blocks of headroom."
            );
            case RESOLVED -> throw new IllegalStateException(
                "Resolved north walkway has no relative Y."
            );
        }
        return false;
    }

    private BlockPos northWalkwaySupport(int x) {
        if (northWalkwayRelativeY == null) {
            throw new IllegalStateException("The compact north walkway is unresolved.");
        }
        return mapCorner.add(x, northWalkwayRelativeY, -1);
    }

    private BlockPos circularBuildAlignmentSupport(
        CompactCircularNbtPlan.PairRoute route
    ) {
        BlockPos endpoint = northWalkwaySupport(route.outboundX());
        BlockPos firstRouteSupport = mapCorner.add(
            surfaceRuntimePosition(route.outboundX(), 1)
        );
        return OrderedUTraversalMovement.entryApproachSupport(
            endpoint,
            firstRouteSupport
        );
    }

    private BlockPos circularBuildExitAlignmentSupport(
        CompactCircularNbtPlan.PairRoute route
    ) {
        BlockPos endpoint = northWalkwaySupport(route.returnX());
        BlockPos finalRouteSupport = mapCorner.add(
            surfaceRuntimePosition(route.returnX(), 1)
        );
        return OrderedUTraversalMovement.exitDepartureSupport(
            endpoint,
            finalRouteSupport
        );
    }

    private boolean isSafeCircularBuildAlignment(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return isSafeCircularExteriorSupport(
            route.outboundX(),
            circularBuildAlignmentSupport(route)
        );
    }

    private boolean isSafeCircularBuildExitAlignment(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return isSafeCircularExteriorSupport(
            route.returnX(),
            circularBuildExitAlignmentSupport(route)
        );
    }

    private boolean isSafeCircularExteriorSupport(
        int walkwayX,
        BlockPos exteriorSupport
    ) {
        if (mc.world == null || !isSafeNorthWalkway(walkwayX)) {
            return false;
        }
        BlockState state =
            MapAreaCache.getCachedBlockState(exteriorSupport);
        return !state.isAir()
            && state.isSolidBlock(mc.world, exteriorSupport)
            && MapAreaCache.getCachedBlockState(exteriorSupport.up()).isAir()
            && MapAreaCache.getCachedBlockState(exteriorSupport.up(2)).isAir();
    }

    private boolean validateCompactWorkspace() {
        if (compactPlan == null || buildTargets.isEmpty()) {
            error("No validated compact NBT plan is loaded.");
            return false;
        }
        if (!ensureNorthWalkwayResolved()) return false;

        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            BlockPos walkway = northWalkwaySupport(x);
            if (!isSafeNorthWalkway(x)) {
                error(
                    "Compact north walkway is no longer safe at "
                        + walkway.toShortString() + "."
                );
                return false;
            }
        }

        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }
            if (circularPairModes.getOrDefault(route.pairIndex(), false)) {
                if (!validateCircularPairWorkspace(route, true)) return false;
            } else if (hasPartialConnector(route)) {
                error(
                    "Pair " + route.pairIndex()
                        + " has a partial or unexpected connector. Complete or remove it before independent fallback."
                );
                return false;
            } else if (!circularTraversalForCurrentMap && hasAnyConnector(route)) {
                error(
                    "Pair " + route.pairIndex()
                        + " already has a connector, but circular U traversal is disabled for this map."
                );
                return false;
            }
        }
        return true;
    }

    private boolean validateCircularPairWorkspace(
        CompactCircularNbtPlan.PairRoute route,
        boolean reportError
    ) {
        if (!isSafeCircularBuildAlignment(route)) {
            if (reportError) {
                error(
                    "Circular pair one-block-back alignment is not safe at "
                        + circularBuildAlignmentSupport(route)
                            .toShortString() + "."
                );
            }
            return false;
        }
        if (!isSafeCircularBuildExitAlignment(route)) {
            if (reportError) {
                error(
                    "Circular pair one-block-back exit is not safe at "
                        + circularBuildExitAlignmentSupport(route)
                            .toShortString() + "."
                );
            }
            return false;
        }
        for (int x : new int[] {route.outboundX(), route.returnX()}) {
            BlockPos walkway = northWalkwaySupport(x);
            BlockState walkwayState = MapAreaCache.getCachedBlockState(walkway);
            if (walkwayState.getBlock() != Blocks.COBBLESTONE
                || !MapAreaCache.getCachedBlockState(walkway.up()).isAir()
                || !MapAreaCache.getCachedBlockState(walkway.up(2)).isAir()) {
                if (reportError) {
                    error("Circular pair north entry is not safe at " + walkway.toShortString() + ".");
                }
                return false;
            }
        }

        for (BlockPos relative : circularPairTargets(route)) {
            BlockPos world = mapCorner.add(relative);
            BlockState existing = MapAreaCache.getCachedBlockState(world);
            Block expected = buildTargets.get(relative);
            if (!existing.isAir() && (expected == null || existing.getBlock() != expected)) {
                boolean repairableCurrentPairSupport =
                    repairCurrentUPair.get()
                        && expected != null
                        && BlockUtils.canBreak(world, existing);
                if (!repairableCurrentPairSupport && reportError) {
                    error(
                        "Circular pair contains an unexpected support at "
                            + world.toShortString() + "."
                    );
                }
                if (!repairableCurrentPairSupport) return false;
            }
            for (int offset = 1; offset <= 2; offset++) {
                BlockPos obstruction = world.up(offset);
                BlockPos obstructionRelative =
                    obstruction.subtract(mapCorner);
                BlockState obstructionState =
                    MapAreaCache.getCachedBlockState(obstruction);
                if (obstructionState.isAir()) continue;
                boolean repairableHeadroom =
                    repairCurrentUPair.get()
                        && !buildTargets.containsKey(
                            obstructionRelative
                        )
                        && BlockUtils.canBreak(
                            obstruction,
                            obstructionState
                        )
                        && getBestRegisteredTool(
                            obstructionState
                        ) != null;
                if (!repairableHeadroom && reportError) {
                    error(
                        "Circular pair headroom is blocked at "
                            + obstruction.toShortString()
                            + " by "
                            + obstructionState.getBlock()
                                .getName().getString()
                            + "; no safe active-U repair owns it."
                    );
                }
                if (!repairableHeadroom) return false;
            }
        }
        return true;
    }

    private boolean endBuilding() {
        // Only executed on Master
        if (pendingPlacementLedger != null
            && !pendingPlacementLedger.isEmpty()) {
            stopBuildForAction();
            return false;
        }
        BlockPos stillMissing = orderedBuildTargets.stream()
            .filter(this::isInWorkingInterval)
            .filter(relative ->
                MapAreaCache.getCachedBlockState(
                    mapCorner.add(relative)
                ).isAir())
            .findFirst()
            .orElse(null);
        if (stillMissing != null) {
            warning(
                "Build completion found an unconfirmed missing target at "
                    + mapCorner.add(stillMissing).toShortString()
                    + "; replanning the ordered build path."
            );
            calculateBuildingPath(false);
            if (circularTraversalForCurrentMap
                && !prepareNextCircularBuildInventoryPlan()
                && requireCompleteUInventory.get()) {
                toggle();
                return false;
            }
            state = State.Walking;
            stopBuildForAction();
            return false;
        }
        if (!knownErrors.isEmpty()) {
            if (errorAction.get() == ErrorAction.ManualRepair) {
                workingInterval = new Pair<>(0, map.length - 1);
                info("Found errors: ");
                for (int i = knownErrors.size() - 1; i >= 0; i--) {
                    info("Pos: " + knownErrors.get(i).toShortString());
                }
                state = State.AwaitManualRepair;
                Utils.setForwardPressed(false);
                warning("ErrorAction is ManualRepair. The module resumes when all errors are fixed. All errors are highlighted");
                return false;
            }
        }
        info(
            "Finished building map ("
                + confirmedPlacements + "/" + placementAttempts
                + " placement confirmations, "
                + confirmedRepairBreaks + "/" + repairBreakAttempts
                + " repair-break confirmations)."
        );
        applyPendingInterval();
        buildingActive = false;
        mapCyclePhase = MapCyclePhase.MAP_HANDOFF;
        mapHandoffStage = MapHandoffStage.PREPARE_INVENTORY;
        handoffSourceMapId = null;
        handoffLockedMapId = null;
        releaseTransientBuildOwners();
        repairToolShadows.clear();
        if (!persistFileCoordinationCheckpoint("map-handoff-start")) {
            return false;
        }
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        buildRecoveryRestockAfterEgress = false;
        workActionBudget.reset();
        pendingPlacementLedger.reset();
        optionalPendingPlacements.clear();
        placementSubmissionBlockSequences.clear();
        repairSubmissionBlockSequences.clear();
        confirmedBuildHotbarSwap.clear();
        confirmedMiningHotbarSwap.clear();
        clearPendingInventorySwapState();
        miningHotbarSwapContext = MiningHotbarSwapContext.NONE;
        releaseBuildRepairSpeedMine();
        buildRepairController.reset();
        clearCircularBuildInventoryPlan();
        state = State.Walking;
        workingInterval = trueInterval;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Pair<BlockPos, Vec3d> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
        return true;
    }

    private boolean recordCurrentCycleTiming() {
        // Only the coordinator owns the complete build -> map handoff ->
        // verified-clear duration. Slave rows would duplicate one logical NBT.
        if (SlaveSystem.isSlave() || cycleTimingRecorded) return true;
        if (mapFolder == null || mapFile == null) {
            error("Cannot record NBT timing because the active NBT is unknown.");
            return false;
        }
        if (cycleStartedAtMs < 0) {
            error("Cannot record NBT timing because the cycle start was not recorded.");
            return false;
        }
        if (cycleCompletedAtMs < 0) {
            cycleCompletedAtMs = System.currentTimeMillis();
            publishFileCoordinationState();
        }

        String coordinator = mc.player == null
            ? null
            : mc.player.getName().getString();
        NbtTimingSummary.CycleCompletion completion =
            new NbtTimingSummary.CycleCompletion(
                coordinationJobId,
                coordinationGeneration,
                logicalSourceName == null
                    ? mapFile.getName()
                    : logicalSourceName,
                logicalPrintingName,
                cycleStartedAtMs,
                cycleCompletedAtMs,
                cycleRecovered,
                coordinator,
                Math.max(1, SlaveSystem.slaves.size() + 1)
            );
        try {
            NbtTimingSummary.WriteResult result =
                NbtTimingSummary.recordCycle(
                    mapFolder.toPath(),
                    completion
                );
            cycleTimingRecorded = true;
            info(
                "NBT timing "
                    + (result.status() == NbtTimingSummary.WriteStatus.INSERTED
                        ? "saved"
                        : "already saved")
                    + ": §a" + result.record().elapsedText()
                    + "§r in " + NbtTimingSummary.FILE_NAME
            );
            return true;
        } catch (
            IOException
                | IllegalArgumentException
                | IllegalStateException failure
        ) {
            error(
                "Failed to save " + NbtTimingSummary.FILE_NAME
                    + ": " + failure.getMessage()
            );
            return false;
        }
    }

    private boolean archiveCurrentNbtFiles() {
        if (!mapCyclePhase.canArchive()) {
            error(
                "Refusing to archive the active NBT before map clearing has been verified."
            );
            return false;
        }
        if (!moveToFinishedFolder.get()) return true;
        if (currentMapArchived) {
            return persistFileCoordinationCheckpoint("archive-result");
        }
        if (mapFile == null) {
            error("Cannot archive the finished map because its source NBT is unknown.");
            return false;
        }
        try {
            FinishedNbtArchiver.Result archived = FinishedNbtArchiver.archive(
                mapFolder.toPath(),
                mapFile.toPath(),
                generatedMapFile == null ? null : generatedMapFile.toPath()
            );
            archivedSourceName =
                archived.archivedSource().getFileName().toString();
            archivedPrintingName = archived.archivedGenerated()
                .map(path -> path.getFileName().toString())
                .orElse(null);
            mapFile = archived.archivedSource().toFile();
            generatedMapFile = archived.archivedGenerated()
                .map(java.nio.file.Path::toFile)
                .orElse(null);
            currentMapArchived = true;
            if (!persistFileCoordinationCheckpoint("archive-result")) {
                return false;
            }
            info(
                generatedMapFile == null
                    ? "Moved the finished NBT to §a_finished_maps"
                    : "Moved the original and generated compact NBTs to §a_finished_maps"
            );
            return true;
        } catch (IOException exception) {
            error(
                "Failed to move the finished NBT"
                    + (generatedMapFile == null ? "" : " pair")
                    + ": " + exception.getMessage()
            );
            exception.printStackTrace();
            return false;
        }
    }

    private record LogisticsTerminal(
        String action,
        double x,
        double y,
        double z,
        BlockPos target
    ) {
    }

    private record MiningAssignment(int anchorLine, boolean paired, Set<Integer> lines) {
        private MiningAssignment {
            lines = Set.copyOf(lines);
        }
    }

    private record CircularMiningLocalSupport(
        int pairIndex,
        int targetIndex,
        BlockPos support
    ) {
        private CircularMiningLocalSupport {
            if (pairIndex < 0 || targetIndex < 0) {
                throw new IllegalArgumentException(
                    "Local mining support indices cannot be negative."
                );
            }
            support = new BlockPos(
                Objects.requireNonNull(support, "support")
            );
        }
    }

    private record CircularTeardownTargetReference(
        int pairIndex,
        int targetIndex
    ) {
        private CircularTeardownTargetReference {
            if (pairIndex < 0 || targetIndex < 0) {
                throw new IllegalArgumentException(
                    "Circular teardown target indices cannot be negative."
                );
            }
        }
    }

    private void startMining() {
        beginMapMining(true);
    }

    private void beginMapMining(boolean refillTools) {
        beginMapMining(refillTools, false);
    }

    private void beginMapMining(
        boolean refillTools,
        boolean recoveringExistingMining
    ) {
        if (availableSlots.isEmpty() && !setupSlots()) return;
        buildingActive = false;
        buildRecoveryPending = false;
        buildRecoveryNeedsInventory = false;
        buildRecoveryRestockAfterEgress = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        resetTeardownMiningActionState();
        plannedTeardownHotbarAssignments.clear();
        releaseBuildRepairSpeedMine();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        workActionBudget.reset();
        miningActionTick = 0L;
        resetMapAreaCache();
        if (!ensureNorthWalkwayResolved()) {
            toggle();
            return;
        }
        Optional<CircularMiningLocalSupport> localSupport =
            circularMiningLocalSupport();
        DurableTeardownRecoveryCursor.Cursor persistedCursor =
            recoveredActiveMiningPair >= 0
                && recoveredActiveMiningTargetIndex >= 0
                ? new DurableTeardownRecoveryCursor.Cursor(
                    recoveredActiveMiningPair,
                    recoveredActiveMiningTargetIndex
                )
                : null;
        Optional<DurableTeardownRecoveryCursor.Cursor>
            validatedPersistedCursor =
                validateTeardownRecoveryCursor(persistedCursor);
        if (recoveringExistingMining
            && recoveredActiveMiningPair >= 0
            && localSupport.isPresent()
            && localSupport.orElseThrow().pairIndex()
                != recoveredActiveMiningPair) {
            error(
                "The persisted teardown owns pair "
                    + recoveredActiveMiningPair
                    + ", but the player is standing on pair "
                    + localSupport.orElseThrow().pairIndex()
                    + ". Stopping before movement."
            );
            stopMovement();
            toggle();
            return;
        }
        boolean recoveredPairCanContinue =
            recoveringExistingMining
                && recoveredActiveMiningPair >= 0
                && isRecoverablePersistedMiningPair(
                    recoveredActiveMiningPair
                );
        if (recoveringExistingMining
            && localSupport.isEmpty()
            && !isAtKnownSafeBuildRecoveryLocation()) {
            if (schedulePersistedMiningExteriorRecovery()) {
                return;
            }
            error(
                "Persisted teardown cannot resume because the player "
                    + "is neither on a verified remaining U route, "
                    + "the safe north walkway, a connected grounded "
                    + "exterior approach, nor a registered station. "
                    + "Stopping before unsafe movement."
            );
            stopMovement();
            toggle();
            return;
        }
        preferredRecoveredMiningPair = localSupport
            .map(CircularMiningLocalSupport::pairIndex)
            .orElse(
                recoveredPairCanContinue
                    ? recoveredActiveMiningPair
                    : -1
            );
        boolean startingFromLocalSupport = localSupport.isPresent();
        if (localSupport.isPresent()) {
            retainedTeardownRecoveryCursor = teardownRecoveryCursor(
                localSupport.orElseThrow()
            );
            debugLog(
                "Recovery",
                "restart is standing on verified remaining support "
                    + "for pair "
                    + preferredRecoveredMiningPair
                    + "; preferring that U over map-order assignment"
                    + (recoveredActiveMiningTargetIndex < 0
                        ? ""
                        : " (checkpoint support index="
                            + recoveredActiveMiningTargetIndex
                            + ", authoritative support index="
                            + localSupport.orElseThrow()
                                .targetIndex()
                            + ")")
            );
        } else if (preferredRecoveredMiningPair >= 0) {
            retainedTeardownRecoveryCursor =
                validatedPersistedCursor.orElse(null);
            debugLog(
                "Recovery",
                "restart is at a verified safe recovery location; "
                    + "retaining persisted teardown ownership of pair "
                    + preferredRecoveredMiningPair
                    + (retainedTeardownRecoveryCursor == null
                        ? " without a legacy support cursor"
                        : " at canonical support index "
                            + retainedTeardownRecoveryCursor
                                .targetIndex())
                    + " and routing through that pair's safe endpoint"
            );
        } else if (recoveringExistingMining) {
            retainedTeardownRecoveryCursor = null;
            debugLog(
                "Recovery",
                "the persisted teardown pair is already complete or "
                    + "cannot form a continuous safe U; selecting the "
                    + "next authoritative teardown assignment"
            );
        }
        recoveredActiveMiningPair = -1;
        recoveredActiveMiningTargetIndex = -1;
        refreshCircularMiningTraversalOptimization();
        if (circularTraversalForCurrentMap
            && !circularMiningOptimizationReady) {
            toggle();
            return;
        }
        mapCyclePhase = MapCyclePhase.MINING;
        if (!persistFileCoordinationCheckpoint("mining-start")) return;
        info("Start mining map");
        currentMiningSessionId = Math.max(
            nextMiningTaskId,
            Math.max(1L, System.currentTimeMillis())
        );
        nextMiningTaskId = currentMiningSessionId + 1;
        miningAssignmentsActive = true;
        reservedMiningLines.clear();
        currentMiningLines.clear();
        slaveMiningAssignments.clear();
        slaveMiningTaskIds.clear();
        reportedMinedLines.clear();
        reportedClearedConnectorPairs.clear();
        pendingIndependentMiningLines.clear();
        activeMiningLine = -1;
        currentMiningPaired = false;
        miningRecoveryPending = false;
        currentSlaveMiningTaskId = -1;

        for (String slave : SlaveSystem.slaves) {
            SlaveSystem.activeSlavesDict.put(slave, false);
            SlaveSystem.finishedSlavesDict.put(slave, true);
        }

        boolean masterAssigned = startNextMasterMiningAssignment();
        for (String slave : SlaveSystem.slaves) assignNextMiningTask(slave);

        if (masterAssigned
            && sleep.get()
            && !recoveringExistingMining
            && !startingFromLocalSupport) {
            if (bed == null) {
                warning("Can not sleep because bed was not set.");
            } else if (refillTools) {
                checkpoints.add(0, new Pair(bed.getRight(), new Pair("sleep", null)));
            }
        }

        if (!masterAssigned) {
            if (SlaveSystem.allSlavesFinished()) {
                finishMiningIfComplete();
            } else {
                state = State.AwaitMasterAllMined;
            }
        }
    }

    private MiningAssignment findNextMiningAssignment() {
        MiningAssignment preferred =
            claimPreferredRecoveredMiningAssignment();
        if (preferred != null) return preferred;

        for (int line = 0; line < map.length; line++) {
            if (reservedMiningLines.contains(line)
                || isLineMined(line)) {
                continue;
            }

            CompactCircularNbtPlan.PairRoute route = compactPlan.pairRoutes().get(line / 2);
            if (circularTraversalForCurrentMap
                && circularMiningOptimizationReady
                && !optimizedCircularMiningTraversalPairs.contains(
                    route.pairIndex()
                )) {
                continue;
            }
            boolean pairAvailable = !reservedMiningLines.contains(route.outboundX())
                && !reservedMiningLines.contains(route.returnX());
            if (!circularTraversalForCurrentMap || !pairAvailable) {
                return new MiningAssignment(line, false, Set.of(line));
            }
            CircularMiningRecoveryPlan.Result recovery =
                analyzeCircularMiningRoute(route);
            if (recovery.mode()
                == CircularMiningRecoveryPlan.Mode.FALLBACK) {
                // Disconnected sparse leftovers cannot safely use the old
                // independent-line walker. The master clears them only after
                // normal continuous U work through scaffold recovery.
                continue;
            }
            CircularMiningAssignmentPolicy.Kind policy =
                CircularMiningAssignmentPolicy.decide(
                    true,
                    true,
                    recovery.mode()
                );
            if (policy == CircularMiningAssignmentPolicy.Kind.CIRCULAR_PAIR) {
                return new MiningAssignment(
                    route.outboundX(),
                    true,
                    Set.of(route.outboundX(), route.returnX())
                );
            }
            if (policy == CircularMiningAssignmentPolicy.Kind.INDEPENDENT_PAIR) {
                return new MiningAssignment(
                    route.outboundX(),
                    false,
                    Set.of(route.outboundX(), route.returnX())
                );
            }
            return new MiningAssignment(line, false, Set.of(line));
        }
        return null;
    }

    private MiningAssignment
        claimPreferredRecoveredMiningAssignment() {
        int preferredPair = preferredRecoveredMiningPair;
        preferredRecoveredMiningPair = -1;
        if (!circularTraversalForCurrentMap
            || compactPlan == null
            || preferredPair < 0
            || preferredPair >= compactPlan.pairRoutes().size()
            || (circularMiningOptimizationReady
                && !optimizedCircularMiningTraversalPairs.contains(
                    preferredPair
                ))) {
            return null;
        }
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(preferredPair);
        if (reservedMiningLines.contains(route.outboundX())
            || reservedMiningLines.contains(route.returnX())) {
            return null;
        }
        CircularMiningRecoveryPlan.Result recovery =
            analyzeCircularMiningRoute(route);
        if (recovery.mode()
                == CircularMiningRecoveryPlan.Mode.COMPLETE
            || recovery.mode()
                == CircularMiningRecoveryPlan.Mode.FALLBACK) {
            return null;
        }
        info(
            "Restart recovery selected pair "
                + preferredPair
                + " from its persisted, authoritatively validated "
                + "teardown ownership."
        );
        return new MiningAssignment(
            route.outboundX(),
            true,
            Set.of(route.outboundX(), route.returnX())
        );
    }

    private boolean startNextMasterMiningAssignment() {
        MiningAssignment assignment = findNextMiningAssignment();
        if (assignment == null) return false;
        reservedMiningLines.addAll(assignment.lines());
        beginMiningAssignment(assignment);
        return true;
    }

    private boolean assignNextMiningTask(String slave) {
        if (SlaveSystem.isSlavePaused(slave)) return false;
        MiningAssignment assignment = findNextMiningAssignment();
        if (assignment == null) return false;

        reservedMiningLines.addAll(assignment.lines());
        slaveMiningAssignments.put(slave, assignment);
        long taskId = nextMiningTaskId++;
        slaveMiningTaskIds.put(slave, taskId);
        String mode = MiningAssignmentMode.wireName(
            assignment.paired(),
            isWholePairAssignment(assignment)
        );
        SlaveSystem.queueDM(
            slave,
            "mine2:" + taskId + ":" + assignment.anchorLine() + ":" + mode
        );
        SlaveSystem.activeSlavesDict.put(slave, true);
        SlaveSystem.finishedSlavesDict.put(slave, false);
        return true;
    }

    private void resendOutstandingMiningTasks() {
        if (!miningAssignmentsActive || SlaveSystem.isSlave()) return;
        for (String slave : new HashSet<>(slaveMiningTaskIds.keySet())) {
            resendOutstandingMiningTask(slave);
        }
    }

    private void resendOutstandingMiningTask(String slave) {
        Long taskId = slaveMiningTaskIds.get(slave);
        if (taskId == null) return;
        MiningAssignment assignment = slaveMiningAssignments.get(slave);
        if (assignment == null || assignment.lines().isEmpty()) {
            warning(
                "Cannot re-send mining task " + taskId
                    + " because its reserved lines are missing."
            );
            return;
        }
        Set<Integer> lines = assignment.lines();
        int anchor = Collections.min(lines);
        boolean wholePair = isWholePairAssignment(assignment);
        if (lines.size() != 1 && !wholePair) {
            error("Cannot safely re-send malformed mining task " + taskId + ".");
            return;
        }
        String mode =
            MiningAssignmentMode.wireName(assignment.paired(), wholePair);
        SlaveSystem.queueDM(
            slave,
            "mine2:" + taskId + ":" + anchor + ":" + mode
        );
    }

    private void resendPendingMiningFinalizations() {
        if (SlaveSystem.isSlave()) return;
        for (Map.Entry<String, Long> pending :
            new HashMap<>(pendingSlaveMiningFinalizations).entrySet()) {
            SlaveSystem.queueDM(
                pending.getKey(),
                "miningDone:" + pending.getValue()
            );
        }
    }

    private void resyncMiningProtocol() {
        if (SlaveSystem.isSlave()) {
            if (pendingSlaveMiningCompletion != null) {
                SlaveSystem.queueMasterDM(pendingSlaveMiningCompletion);
            }
            if (lastFinalizedMiningSessionId >= 0
                && pendingMiningFinalizationAck < 0) {
                SlaveSystem.queueMasterDM(
                    "miningDoneAck:" + lastFinalizedMiningSessionId
                );
            }
            SlaveSystem.queueMasterDM("sync");
            return;
        }
        resendOutstandingMiningTasks();
        resendPendingMiningFinalizations();
    }

    private void beginMiningAssignment(MiningAssignment assignment) {
        resetTeardownMiningActionState();
        plannedTeardownHotbarAssignments.clear();
        miningPos = null;
        currentMiningLines.clear();
        currentMiningLines.addAll(assignment.lines());
        currentMiningPaired = false;
        pendingIndependentMiningLines.clear();

        int assignmentPair = assignment.paired()
            ? assignment.anchorLine() / 2
            : -1;
        if (retainedTeardownRecoveryCursor != null
            && retainedTeardownRecoveryCursor.pairIndex()
                != assignmentPair) {
            retainedTeardownRecoveryCursor = null;
        }

        boolean wholePairReserved = isWholePairAssignment(assignment);
        if (assignment.paired() && !wholePairReserved) {
            error("Rejected a circular mining assignment without its complete pair.");
            toggle();
            return;
        }
        if (!persistLocalCycleCheckpoint("mining-assignment")) {
            return;
        }
        if (MiningAssignmentMode.usesCircularTraversal(
            assignment.paired(),
            wholePairReserved
        )) {
            CompactCircularNbtPlan.PairRoute route =
                compactPlan.pairRoutes().get(assignment.anchorLine() / 2);
            boolean allowLocalResume = false;
            Optional<CircularMiningLocalSupport> localSupport =
                circularMiningLocalSupport(route);
            if (localSupport.isPresent()) {
                HashMap<Item, Integer> locallyMissingTools =
                    missingOperationalMiningTools(
                        circularMiningInventoryTargets(route),
                        Map.of()
                    );
                if (locallyMissingTools == null) {
                    toggle();
                    return;
                }
                allowLocalResume =
                    locallyMissingTools.isEmpty()
                        && hasCompleteTeardownScaffoldReserve();
                if (!allowLocalResume) {
                    if (!calculateCircularMiningRecoveryEgress(
                        route,
                        localSupport.orElseThrow()
                    )) {
                        error(
                            "Could not generate a safe U egress before "
                                + "tool or cobblestone scaffold restock "
                                + "for pair "
                                + route.pairIndex() + "."
                        );
                        toggle();
                    } else {
                        currentMiningPaired = true;
                    }
                    return;
                }
            }
            if (calculateCircularMiningPath(
                route,
                allowLocalResume
            )) {
                currentMiningPaired = true;
                if (!allowLocalResume
                    && !ensureCircularMiningToolDurability(route)) {
                    toggle();
                }
                return;
            }
            info("Pair " + route.pairIndex() + " is not continuously walkable; using independent mining.");
        }

        assignment.lines().stream()
            .sorted()
            .filter(line -> !isLineMined(line))
            .forEach(pendingIndependentMiningLines::add);
        if (!startNextIndependentMiningLine()) {
            completeCurrentMiningAssignment();
        }
    }

    private boolean isWholePairAssignment(MiningAssignment assignment) {
        int anchor = assignment.anchorLine();
        return assignment.lines().size() == 2
            && (anchor & 1) == 0
            && assignment.lines().contains(anchor)
            && assignment.lines().contains(anchor + 1);
    }

    private boolean isCircularMiningOrRestockState() {
        return state == State.MiningUTraversal
            || state == State.AwaitUBlockBreak
            || (!currentMiningLines.isEmpty()
                && state == State.AwaitRestockResponse
                && (resumeAfterRestockState == State.MiningUTraversal
                    || resumeAfterRestockState == State.Walking));
    }

    private void restartCurrentMiningAssignment() {
        if (currentMiningLines.isEmpty()) {
            resetTeardownMiningActionState();
            abandonRestockSession(true);
            if (SlaveSystem.isSlave()) {
                state = State.AwaitSlaveMineLine;
                SlaveSystem.queueMasterDM("sync");
                stopMovement();
                return;
            }
            if (!startNextMasterMiningAssignment()) {
                if (SlaveSystem.allSlavesFinished()) {
                    finishMiningIfComplete();
                } else {
                    state = State.AwaitMasterAllMined;
                    stopMovement();
                }
            }
            return;
        }
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        miningPos = null;
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
        MiningAssignment assignment = new MiningAssignment(
            Collections.min(currentMiningLines),
            currentMiningPaired,
            currentMiningLines
        );
        info("Re-evaluating mining route after an interrupted U traversal");
        if (assignment.paired()) {
            refreshCircularMiningTraversalOptimization(
                assignment.anchorLine() / 2
            );
            if (!circularMiningOptimizationReady) {
                toggle();
                return;
            }
        }
        beginMiningAssignment(assignment);
    }

    private boolean startNextIndependentMiningLine() {
        while (!pendingIndependentMiningLines.isEmpty()) {
            int line = pendingIndependentMiningLines.removeFirst();
            if (isLineMined(line)) continue;
            if (!calculateIndependentMiningPath(line)) {
                toggle();
                return true;
            }
            if (!ensureIndependentMiningToolDurability(line)) {
                toggle();
                return true;
            }
            state = State.Walking;
            return true;
        }
        return false;
    }

    private void completeCurrentMiningAssignment() {
        if (startNextIndependentMiningLine()) return;
        resetTeardownMiningActionState();
        retainedTeardownRecoveryCursor = null;

        for (int line : currentMiningLines) {
            if (isLineMined(line)) reportedMinedLines.add(line);
        }
        boolean assignedConnectorsClear = areAssignedConnectorsClear();
        reservedMiningLines.removeAll(currentMiningLines);
        currentMiningLines.clear();
        currentMiningPaired = false;
        activeMiningLine = -1;
        miningPos = null;
        checkpoints.clear();

        if (SlaveSystem.isSlave()) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            state = State.AwaitSlaveMineLine;
            String completion = currentSlaveMiningTaskId >= 0
                ? "mined:" + currentSlaveMiningTaskId + ":"
                    + (assignedConnectorsClear ? "clear" : "blocked")
                : "finished";
            pendingSlaveMiningCompletion = completion;
            SlaveSystem.queueMasterDM(completion);
            return;
        }

        // Re-read every U after the authoritative final break. A remotely
        // cleared route disappears from assignment. A continuous remainder
        // left by interrupted remote work stays attached to its protected
        // endpoint and remains eligible for another proven safe-order remote
        // schedule; an unassignable remainder uses that same safe endpoint.
        refreshCircularMiningTraversalOptimization();
        if (!startNextMasterMiningAssignment()) {
            if (SlaveSystem.allSlavesFinished()) {
                finishMiningIfComplete();
            } else {
                state = State.AwaitMasterAllMined;
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
            }
        }
    }

    private boolean areAssignedConnectorsClear() {
        HashSet<Integer> pairIndexes = new HashSet<>();
        for (int line : currentMiningLines) pairIndexes.add(line / 2);
        for (int pairIndex : pairIndexes) {
            CompactCircularNbtPlan.PairRoute route = compactPlan.pairRoutes().get(pairIndex);
            for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
                if (!MapAreaCache.getCachedBlockState(
                    mapCorner.add(connectorRuntimePosition(connector))
                ).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void finishMiningIfComplete() {
        for (int line = 0; line < map.length; line++) {
            // Final clearance never trusts the earlier assignment report: a
            // late authoritative server update may reveal a missed block.
            if (!isLineMined(line)) {
                if (!startNextMasterMiningAssignment()) {
                    if (startTeardownScaffoldRecovery()) {
                        return;
                    }
                    error("No safe mining assignment could be generated for line " + line + ".");
                    toggle();
                }
                return;
            }
        }

        for (BlockPos relative : connectorTargets) {
            if (!MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) {
                if (startTeardownScaffoldRecovery()) {
                    return;
                }
                error(
                    "Map columns are mined, but a disconnected connector remains at "
                        + mapCorner.add(relative).toShortString() + "."
                );
                toggle();
                return;
            }
        }
        endMining();
    }

    private void endMining() {
        // Only executed on Master
        resetTeardownMiningActionState();
        retainedTeardownRecoveryCursor = null;
        abandonRestockSession(true);
        miningAssignmentsActive = false;
        mapCyclePhase = MapCyclePhase.VERIFIED_CLEAR;
        if (!persistFileCoordinationCheckpoint("verified-clear")) return;
        if (!recordCurrentCycleTiming() || !archiveCurrentNbtFiles()) {
            state = State.AwaitNbtArchive;
            timeoutTicks = 100;
            stopMovement();
            return;
        }
        finishMiningAfterArchive();
    }

    private void finishMiningAfterArchive() {
        resetTeardownMiningActionState();
        retainedTeardownRecoveryCursor = null;
        mapCyclePhase = MapCyclePhase.POST_MINING;
        if (!persistFileCoordinationCheckpoint("post-mining")) return;
        info("Finished mining map");
        long finalizationId = currentMiningSessionId >= 0
            ? currentMiningSessionId
            : Math.max(1L, System.currentTimeMillis());
        for (String slave : SlaveSystem.slaves) {
            pendingSlaveMiningFinalizations.put(slave, finalizationId);
            SlaveSystem.queueDM(slave, "miningDone:" + finalizationId);
            SlaveSystem.activeSlavesDict.put(slave, false);
        }
        SlaveSystem.setAllSlavesUnfinished();
        scheduleUsedToolDeposits();
    }

    private Set<Item> getInventoryToolItems() {
        Set<Item> items = new HashSet<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (ToolUtils.isTool(stack)) items.add(stack.getItem());
        }
        return items;
    }

    /**
     * Resolves the only legal destination for a tool removed from managed
     * inventory. Typed single-chest registrations always win. The original
     * Used Pickaxe Chest remains a compatibility destination only for
     * pickaxes; axes and every other tool require their own typed chest.
     */
    private Pair<BlockPos, Vec3d> registeredUsedToolDestination(
        Item item
    ) {
        Pair<BlockPos, Vec3d> typed = usedToolChests == null
            ? null
            : usedToolChests.get(item);
        if (typed != null) return typed;
        ItemStack stack = item.getDefaultStack();
        return stack.isIn(ItemTags.PICKAXES)
            ? usedToolChest
            : null;
    }

    /**
     * Selects exact below-threshold tool slots for retirement before a
     * teardown entry. Only slots with a legal used-tool destination are
     * counted as future free capacity. A required pickaxe/axe without its
     * destination fails entry planning rather than leaking into a material
     * dump or being overwritten by restock.
     */
    private LinkedHashSet<Integer> teardownEntryUsedToolDepositSlots(
        Set<Item> requiredToolItems
    ) {
        Objects.requireNonNull(
            requiredToolItems,
            "requiredToolItems"
        );
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        for (int slot : availableSlots) {
            if (slot < 0 || slot >= 36) continue;
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!ToolUtils.isTool(stack)
                || stack.getMaxDamage() <= 1
                || hasMinimumToolDurability(stack)) {
                continue;
            }
            Pair<BlockPos, Vec3d> destination =
                registeredUsedToolDestination(stack.getItem());
            if (destination != null) {
                slots.add(slot);
                continue;
            }
            if (requiredToolItems.contains(stack.getItem())) {
                error(
                    "Cannot replace below-threshold "
                        + stack.getName().getString()
                        + " because its typed used-tool chest is not "
                        + "registered. The tool was retained and teardown "
                        + "will not enter the traversal."
                );
                return null;
            }
            warning(
                "Retaining below-threshold "
                    + stack.getName().getString()
                    + " because no typed used-tool chest is registered."
            );
        }
        return slots;
    }

    private void prependTeardownEntryUsedToolDeposits(
        Set<Integer> depositSlots
    ) {
        if (depositSlots.isEmpty()) return;
        usedToolDepositPlan.clear();
        usedToolDepositSlotPlan.clear();
        currentUsedToolDepositItems.clear();
        currentUsedToolDepositSlots.clear();
        pendingUsedToolDeposit = null;
        activeUsedToolDepositChest = null;

        HashMap<BlockPos, Pair<Vec3d, Set<Integer>>> destinations =
            new HashMap<>();
        HashMap<BlockPos, Set<Item>> destinationItems =
            new HashMap<>();
        for (int slot : depositSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!ToolUtils.isTool(stack)
                || hasMinimumToolDurability(stack)) {
                continue;
            }
            Pair<BlockPos, Vec3d> destination =
                registeredUsedToolDestination(stack.getItem());
            if (destination == null) {
                throw new IllegalStateException(
                    "A planned teardown used-tool slot lost its typed "
                        + "destination."
                );
            }
            destinations.computeIfAbsent(
                destination.getLeft(),
                ignored -> new Pair<>(
                    destination.getRight(),
                    new LinkedHashSet<>()
                )
            ).getRight().add(slot);
            destinationItems.computeIfAbsent(
                destination.getLeft(),
                ignored -> new LinkedHashSet<>()
            ).add(stack.getItem());
        }

        ArrayList<Map.Entry<BlockPos, Pair<Vec3d, Set<Integer>>>>
            orderedDestinations =
                new ArrayList<>(destinations.entrySet());
        orderedDestinations.sort(
            Comparator.comparingDouble(entry ->
                PlayerUtils.distanceTo(entry.getValue().getLeft()))
        );
        ArrayList<Pair<Vec3d, Pair<String, BlockPos>>>
            depositCheckpoints = new ArrayList<>();
        for (Map.Entry<BlockPos, Pair<Vec3d, Set<Integer>>> entry
            : orderedDestinations) {
            usedToolDepositPlan.put(
                entry.getKey(),
                Set.copyOf(
                    destinationItems.getOrDefault(
                        entry.getKey(),
                        Set.of()
                    )
                )
            );
            usedToolDepositSlotPlan.put(
                entry.getKey(),
                Set.copyOf(entry.getValue().getRight())
            );
            depositCheckpoints.add(new Pair<>(
                entry.getValue().getLeft(),
                new Pair<>("usedToolChest", entry.getKey())
            ));
        }
        checkpoints.addAll(0, depositCheckpoints);
        info(
            "%s",
            "Routing " + depositSlots.size()
                + " below-"
                + String.format(
                    Locale.ROOT,
                    "%.1f%%",
                    minimumToolDurabilityFraction() * 100.0
                )
                + " teardown tool slot(s) to typed used-tool storage "
                + "before traversal restock."
        );
    }

    private void prependPlannedBuildUsedToolDeposits() {
        if (!buildingActive
            || plannedBuildUsedToolDepositSlots.isEmpty()) {
            return;
        }
        usedToolDepositPlan.clear();
        usedToolDepositSlotPlan.clear();
        currentUsedToolDepositItems.clear();
        currentUsedToolDepositSlots.clear();
        pendingUsedToolDeposit = null;
        activeUsedToolDepositChest = null;

        HashMap<BlockPos, Pair<Vec3d, Set<Integer>>> destinations =
            new HashMap<>();
        HashMap<BlockPos, Set<Item>> destinationItems =
            new HashMap<>();
        for (int slot : plannedBuildUsedToolDepositSlots) {
            if (slot < 0 || slot >= 36) continue;
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (!ToolUtils.isTool(stack)) continue;
            Pair<BlockPos, Vec3d> destination =
                registeredUsedToolDestination(stack.getItem());
            if (destination == null) {
                warning(
                    "No used-tool chest is registered for "
                        + stack.getName().getString()
                        + "; retaining it and excluding it from the "
                        + "material dump."
                );
                plannedBuildToolKeepSlots.add(slot);
                continue;
            }

            Pair<Vec3d, Set<Integer>> entry =
                destinations.get(destination.getLeft());
            if (entry == null) {
                entry = new Pair<>(
                    destination.getRight(),
                    new HashSet<>()
                );
                destinations.put(destination.getLeft(), entry);
            }
            entry.getRight().add(slot);
            destinationItems.computeIfAbsent(
                destination.getLeft(),
                ignored -> new HashSet<>()
            ).add(stack.getItem());
        }

        ArrayList<Map.Entry<BlockPos, Pair<Vec3d, Set<Integer>>>>
            orderedDestinations =
                new ArrayList<>(destinations.entrySet());
        orderedDestinations.sort(
            Comparator.comparingDouble(entry ->
                PlayerUtils.distanceTo(entry.getValue().getLeft()))
        );

        ArrayList<Pair<Vec3d, Pair<String, BlockPos>>>
            depositCheckpoints = new ArrayList<>();
        for (Map.Entry<BlockPos, Pair<Vec3d, Set<Integer>>> entry
            : orderedDestinations) {
            usedToolDepositPlan.put(
                entry.getKey(),
                Set.copyOf(
                    destinationItems.getOrDefault(
                        entry.getKey(),
                        Set.of()
                    )
                )
            );
            usedToolDepositSlotPlan.put(
                entry.getKey(),
                Set.copyOf(entry.getValue().getRight())
            );
            depositCheckpoints.add(
                new Pair<>(
                    entry.getValue().getLeft(),
                    new Pair<>(
                        "usedToolChest",
                        entry.getKey()
                    )
                )
            );
        }
        checkpoints.addAll(0, depositCheckpoints);
        if (!depositCheckpoints.isEmpty()) {
            info(
                "Routing "
                    + plannedBuildUsedToolDepositSlots.size()
                    + " low-durability tool slot(s) to used-tool "
                    + "storage before material refill."
            );
        }
    }

    private void scheduleUsedToolDeposits() {
        checkpoints.clear();
        usedToolDepositPlan.clear();
        usedToolDepositSlotPlan.clear();
        currentUsedToolDepositSlots.clear();
        pendingUsedToolDeposit = null;
        activeUsedToolDepositChest = null;

        HashMap<BlockPos, Pair<Vec3d, Set<Item>>> destinations = new HashMap<>();
        for (Item item : getInventoryToolItems()) {
            Pair<BlockPos, Vec3d> destination =
                registeredUsedToolDestination(item);
            if (destination == null) {
                warning("No used-tool chest registered for " + item.getName().getString());
                continue;
            }

            Pair<Vec3d, Set<Item>> entry = destinations.get(destination.getLeft());
            if (entry == null) {
                entry = new Pair<>(destination.getRight(), new HashSet<>());
                destinations.put(destination.getLeft(), entry);
            }
            entry.getRight().add(item);
        }

        ArrayList<Map.Entry<BlockPos, Pair<Vec3d, Set<Item>>>> orderedDestinations =
            new ArrayList<>(destinations.entrySet());
        orderedDestinations.sort(Comparator.comparingDouble(
            entry -> PlayerUtils.distanceTo(entry.getValue().getLeft())
        ));

        for (Map.Entry<BlockPos, Pair<Vec3d, Set<Item>>> entry : orderedDestinations) {
            usedToolDepositPlan.put(entry.getKey(), entry.getValue().getRight());
            checkpoints.add(new Pair<>(
                entry.getValue().getLeft(),
                new Pair<>("usedToolChest", entry.getKey())
            ));
        }

        state = checkpoints.isEmpty() ? State.AwaitNBTFile : State.Walking;
        if (checkpoints.isEmpty()) completeSlavePostMiningCleanup();
    }

    public ArrayList<BlockPos> getInvalidPlacements() {
        ArrayList<BlockPos> invalidPlacements = new ArrayList<>();
        for (int index = orderedBuildTargets.size() - 1; index >= 0; index--) {
            BlockPos relativePos = orderedBuildTargets.get(index);
            if (!isInWorkingInterval(relativePos)) continue;
            BlockPos absolutePos = mapCorner.add(relativePos);
            if (knownErrors.contains(absolutePos)) continue;
            BlockState blockState = MapAreaCache.getCachedBlockState(absolutePos);
            Block expected = buildTargets.get(relativePos);
            if (!blockState.isAir() && blockState.getBlock() != expected) {
                invalidPlacements.add(absolutePos);
            }
        }
        return invalidPlacements;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        availableHotBarSlots.clear();
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        info("Inventory slots available for building: " + availableSlots);
        if (availableHotBarSlots.size()
            < BUILD_REQUIRED_MANAGED_HOTBAR_SLOTS) {
            warning(
                "Fullblock printing requires all nine managed hotbar "
                    + "slots: eight build-material slots and one "
                    + "repair-tool slot."
            );
            availableSlots.clear();
            toggle();
            return false;
        }
        if (availableSlots.size() < 2) {
            warning("You need at least 2 free inventory slots!");
            availableSlots.clear();
            toggle();
            return false;
        }
        return true;
    }

    private int getDumpSlot() {
        if (buildingActive && plannedCircularBuildPair >= 0) {
            InventoryKeepAllocator.Allocation<Item> allocation =
                currentCircularMaterialAllocation();
            for (int slot : allocation.dumpSlots()) {
                ItemStack stack =
                    mc.player.getInventory().getStack(slot);
                if (!ToolUtils.isTool(stack)) return slot;
            }
            return -1;
        }
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        boolean protectHandoffItems =
            mapCyclePhase == MapCyclePhase.MAP_HANDOFF
                && mapHandoffStage
                    != MapHandoffStage.PREPARE_INVENTORY;
        for (int slot : invInformation.getLeft()) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (ToolUtils.isTool(stack)) continue;
            Item item = stack.getItem();
            if (protectHandoffItems
                && (item == Items.MAP
                    || item == Items.FILLED_MAP
                    || item == Items.GLASS_PANE)) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    private InventoryKeepAllocator.Allocation<Item>
        currentCircularMaterialAllocation() {
        HashMap<Item, Integer> materialDemand = getRequiredItems();
        for (Map.Entry<Item, Integer> tool
            : plannedRepairToolDemand.entrySet()) {
            int remaining = materialDemand.getOrDefault(
                tool.getKey(),
                0
            ) - tool.getValue();
            if (remaining > 0) {
                materialDemand.put(tool.getKey(), remaining);
            } else {
                materialDemand.remove(tool.getKey());
            }
        }

        HashMap<Item, Integer> maximumStackSizes = new HashMap<>();
        for (Item item : materialDemand.keySet()) {
            maximumStackSizes.put(
                item,
                Utils.maximumStackSize(item)
            );
        }
        ArrayList<InventoryKeepAllocator.StackEntry<Item>> stacks =
            new ArrayList<>();
        for (int slot : availableSlots) {
            ItemStack stack =
                mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()
                || ToolUtils.isTool(stack)) {
                continue;
            }
            stacks.add(
                new InventoryKeepAllocator.StackEntry<>(
                    slot,
                    stack.getItem(),
                    stack.getCount()
                )
            );
        }
        return InventoryKeepAllocator.allocate(
            materialDemand,
            maximumStackSizes,
            stacks
        );
    }

    private HashMap<Item, Integer> getRequiredItems() {
        if (buildingActive
            && plannedCircularBuildPair >= 0
            && compactPlan != null
            && plannedCircularBuildPair
                < compactPlan.pairRoutes().size()) {
            HashMap<Item, Integer> required =
                getRequiredPrimaryRestockItems();
            for (Map.Entry<Item, Integer> entry
                : plannedOptionalMaterialDemand.entrySet()) {
                required.merge(
                    entry.getKey(),
                    entry.getValue(),
                    Integer::sum
                );
            }
            return required;
        }

        HashMap<Item, Integer> requiredItems = new HashMap<>();
        for (BlockPos relative : orderedBuildTargets) {
            if (!isInWorkingInterval(relative)) continue;
            BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (blockState.isAir()) {
                Item material = buildTargets.get(relative).asItem();
                requiredItems.put(material, requiredItems.getOrDefault(material, 0) + 1);
                if (Utils.stacksRequired(requiredItems) > availableSlots.size()) {
                    requiredItems.put(material, requiredItems.get(material) - 1);
                    return requiredItems;
                }
            }
        }
        return requiredItems;
    }

    private HashMap<Item, Integer> getRequiredRestockItems() {
        return getRequiredItems();
    }

    private HashMap<Item, Integer>
        getRequiredPrimaryRestockItems() {
        if (!buildingActive
            || plannedCircularBuildPair < 0
            || compactPlan == null
            || plannedCircularBuildPair
                >= compactPlan.pairRoutes().size()) {
            return getRequiredItems();
        }

        HashMap<Item, Integer> required =
            new HashMap<>(plannedPrimaryMaterialDemand);
        for (Map.Entry<Item, Integer> entry
            : confirmedPrimaryMaterialUses.entrySet()) {
            int remaining = required.getOrDefault(entry.getKey(), 0)
                - entry.getValue();
            if (remaining > 0) {
                required.put(entry.getKey(), remaining);
            } else {
                required.remove(entry.getKey());
            }
        }
        for (Map.Entry<Item, Integer> entry
            : plannedRepairToolDemand.entrySet()) {
            required.merge(
                entry.getKey(),
                entry.getValue(),
                Integer::sum
            );
        }
        return required;
    }

    // MapPrinter Interface for Slave Logic

    public void setInterval(Pair<Integer, Integer> interval) {
        int left = Math.max(0, interval.getLeft());
        int right = Math.min(127, interval.getRight());
        if ((left & 1) != 0) left--;
        if ((right & 1) == 0) right++;
        Pair<Integer, Integer> pairAligned = new Pair<>(left, Math.min(127, right));
        if (buildingActive) {
            pendingInterval = pairAligned;
            info(
                "Deferring interval change to " + pairAligned.getLeft() + "-"
                    + pairAligned.getRight() + " until the next safe north endpoint."
            );
            return;
        }
        applyInterval(pairAligned);
    }

    private void applyPendingInterval() {
        if (pendingInterval == null) return;
        Pair<Integer, Integer> interval = pendingInterval;
        pendingInterval = null;
        applyInterval(interval);
    }

    private void applyInterval(Pair<Integer, Integer> interval) {
        workingInterval = interval;
        trueInterval = interval;
    }

    public void addError(BlockPos relPos) {
        BlockPos absPos = mapCorner.add(relPos);
        if (!knownErrors.contains(absPos)) knownErrors.add(new BlockPos(absPos));
    }

    public void pause() {
        if (!state.equals(State.AwaitSlaveContinue)) {
            releaseAnyOwnedSpeedMine();
            oldState = state;
            state = State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
        }
    }

    public void start() {
        if (startReadyFileSlaveCycle()) return;
        if (state.equals(State.AwaitSlaveContinue)) {
            state = oldState;
            if (state == State.AwaitMasterNextMap) {
                slaveAwaitingNextMapRelease = false;
                nextMapSyncTicks = 0;
                state = State.AwaitNBTFile;
                return;
            }
            if (state == State.AwaitSlaveMineLine && SlaveSystem.isSlave()) {
                SlaveSystem.queueMasterDM("sync");
            }
            switch (activeRecoveryOwner()) {
                case BUILD -> beginBuildRecovery(false);
                case MINING -> beginMiningRecovery(false);
                case LOGISTICS -> {
                }
            }
            return;
        }
        if (state == State.AwaitMasterNextMap) {
            slaveAwaitingNextMapRelease = false;
            nextMapSyncTicks = 0;
            state = State.AwaitNBTFile;
            return;
        }
        if (state.equals(State.AwaitSlaveMineLine)) return;
        if (availableSlots.isEmpty()) {
            state = State.AwaitNBTFile;
            return;
        }
    }

    @Override
    public void finishMiningCycle(long sessionId) {
        if (sessionId < lastFinalizedMiningSessionId) return;
        if (sessionId == lastFinalizedMiningSessionId) {
            if (pendingMiningFinalizationAck != sessionId) {
                SlaveSystem.queueMasterDM("miningDoneAck:" + sessionId);
            }
            return;
        }

        boolean pausedAtCompletion = state == State.AwaitSlaveContinue
            && oldState == State.AwaitSlaveMineLine;
        boolean awaitingFinalization =
            state == State.AwaitSlaveMineLine || pausedAtCompletion;
        String masterPhase = SlaveSystem.masterFileMetadata().get(
            FILE_META_PHASE
        );
        boolean recoveredFileFinalization =
            SlaveSystem.isFileSlave()
                && isFileSlaveReady()
                && currentSlaveMiningTaskId < 0
                && (MapCyclePhase.VERIFIED_CLEAR.name().equals(masterPhase)
                    || MapCyclePhase.POST_MINING.name().equals(masterPhase));
        if (!awaitingFinalization
            && !recoveredFileFinalization
            || (currentSlaveMiningTaskId >= 0
                && pendingSlaveMiningCompletion == null)) {
            warning(
                "Rejected mining finalization " + sessionId
                    + " because the current slave assignment has not completed."
            );
            return;
        }

        lastFinalizedMiningSessionId = sessionId;
        pendingMiningFinalizationAck = sessionId;
        finishSlaveMiningCycle(pausedAtCompletion);
    }

    private void finishSlaveMiningCycle(boolean remainPaused) {
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        currentSlaveMiningTaskId = -1;
        pendingSlaveMiningCompletion = null;
        miningAssignmentsActive = false;
        buildingActive = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        currentMiningPaired = false;
        strictMiningRestockActive = false;
        miningRecoveryPending = false;
        miningRecoveryNeedsTools = false;
        buildRecoveryPending = false;
        buildRecoveryNeedsInventory = false;
        buildRecoveryRestockAfterEgress = false;
        resumeAfterRestockState = null;
        currentMiningLines.clear();
        pendingIndependentMiningLines.clear();
        activeMiningLine = -1;
        miningPos = null;
        mapCyclePhase = MapCyclePhase.POST_MINING;
        scheduleUsedToolDeposits();
        if (remainPaused) {
            oldState = state;
            state = State.AwaitSlaveContinue;
            stopMovement();
        }
    }

    private void completeSlavePostMiningCleanup() {
        if (!SlaveSystem.isSlave() || pendingMiningFinalizationAck < 0) return;
        long sessionId = pendingMiningFinalizationAck;
        pendingMiningFinalizationAck = -1;
        slaveAwaitingNextMapRelease = true;
        mapCyclePhase = MapCyclePhase.IDLE;
        if (!clearLocalCycleCheckpoint(
            "slave-post-mining-complete"
        )) {
            return;
        }
        nextMapSyncTicks = 0;
        state = State.AwaitMasterNextMap;
        stopMovement();
        SlaveSystem.queueMasterDM("miningDoneAck:" + sessionId);
    }

    @Override
    public void slaveMiningCycleFinalized(String slave, long sessionId) {
        Long expected = pendingSlaveMiningFinalizations.get(slave);
        if (expected == null || expected != sessionId) return;
        pendingSlaveMiningFinalizations.remove(slave);
        completedSlaveMiningTaskIds.remove(slave);
    }

    @Override
    public void slaveSync(String slave) {
        Long finalization = pendingSlaveMiningFinalizations.get(slave);
        if (finalization != null) {
            SlaveSystem.queueDM(slave, "miningDone:" + finalization);
            return;
        }
        if (slaveMiningTaskIds.containsKey(slave)) {
            if (!SlaveSystem.isSlavePaused(slave)
                && SlaveSystem.isIntervalAcknowledged(slave)) {
                SlaveSystem.releaseSlave(slave);
            }
            resendOutstandingMiningTask(slave);
            return;
        }
        if ((buildingActive || printingComplete)
            && SlaveSystem.isIntervalAcknowledged(slave)) {
            SlaveSystem.releaseSlave(slave);
        }
    }

    @Override
    public void slaveResumed(String slave) {
        if (!miningAssignmentsActive
            || slaveMiningTaskIds.containsKey(slave)) {
            return;
        }
        assignNextMiningTask(slave);
    }

    @Override
    public void slaveIntervalReady(String slave) {
        if (SlaveSystem.isSlavePaused(slave)) return;
        if (slaveMiningTaskIds.containsKey(slave)) {
            SlaveSystem.releaseSlave(slave);
            resendOutstandingMiningTask(slave);
            return;
        }
        if (buildingActive || printingComplete || miningAssignmentsActive) {
            SlaveSystem.releaseSlave(slave);
        }
    }

    @Override
    public void masterRelationshipChanged() {
        if (SlaveSystem.isFileSlave()
            && state != null
            && mapCyclePhase.isInProgress()
            && state != State.AwaitSlaveContinue) {
            pause();
        }
        currentSlaveMiningTaskId = -1;
        highestSlaveMiningTaskId = -1;
        pendingSlaveMiningCompletion = null;
        currentMiningSessionId = -1;
        lastFinalizedMiningSessionId = -1;
        pendingMiningFinalizationAck = -1;
        slaveAwaitingNextMapRelease = false;
        nextMapSyncTicks = 0;
    }

    @Override
    public void prepareFileRecovery(
        MapCyclePhase phase,
        long recoveryToken
    ) {
        if (!SlaveSystem.isFileSlave() || recoveryToken < 1) return;
        if (recoveryToken == lastPreparedFileRecoveryToken) {
            SlaveSystem.queueMasterDM(
                "recoveryAck:" + recoveryToken
            );
            return;
        }
        if (phase == MapCyclePhase.IDLE) return;
        if ((phase == MapCyclePhase.BUILDING
            || phase == MapCyclePhase.MAP_HANDOFF
            || phase == MapCyclePhase.MAP_DEPOSITED
            || phase == MapCyclePhase.MINING)
            && availableSlots.isEmpty()
            && !setupSlots()) {
            return;
        }

        cancelLogisticsDetour();
        stopMovement();
        releaseTransientBuildOwners();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        checkpoints.clear();
        currentMiningLines.clear();
        pendingIndependentMiningLines.clear();
        miningPos = null;
        activeMiningLine = -1;
        currentMiningPaired = false;
        currentSlaveMiningTaskId = -1;
        pendingSlaveMiningCompletion = null;
        miningAssignmentsActive = false;
        miningRecoveryPending = false;
        strictMiningRestockActive = false;
        buildingActive = false;
        buildRecoveryPending = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildPhase = CircularBuildPhase.NONE;
        resumeAfterRestockState = null;

        if (phase == MapCyclePhase.BUILDING) {
            mapCyclePhase = MapCyclePhase.IDLE;
            state = State.SelectingChests;
        } else {
            mapCyclePhase = phase;
            state = State.AwaitSlaveMineLine;
        }
        lastPreparedFileRecoveryToken = recoveryToken;
        publishFileCoordinationState();
        SlaveSystem.queueMasterDM("recoveryAck:" + recoveryToken);
    }

    @Override
    public void slaveFileRecoveryReady(
        String slave,
        long recoveryToken
    ) {
        if (!SlaveSystem.isFileMaster()
            || recoveryToken != fileRecoveryToken
            || !SlaveSystem.slaves.contains(slave)) {
            return;
        }
        fileRecoveryAcknowledgements.add(slave);
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    @Override
    public boolean isBuildingInProgress() {
        return buildingActive;
    }

    @Override
    public boolean isWorkInProgress() {
        return mapCyclePhase.isInProgress()
            || state == State.AwaitMasterAllBuiltSkip
            || state == State.AwaitSlaveFinalization
            || state == State.AwaitSlaveRemoval
            || state == State.AwaitCompactWorkspace
            || state == State.AwaitFileRecovery
            || state == State.AwaitFileSlaves
            || state == State.AwaitNbtArchive
            || !pendingSlaveMiningFinalizations.isEmpty();
    }

    public void skipBuilding() {
        if (availableSlots.isEmpty()) setupSlots();
        if (cycleStartedAtMs < 0) {
            cycleStartedAtMs = System.currentTimeMillis();
        }
        circularTraversalForCurrentMap = circularTraversal.get();
        releaseTransientBuildOwners();
        resetTeardownMiningActionState();
        abandonRestockSession(true);
        pendingDumpTransfer = null;
        pendingUsedToolDeposit = null;
        buildingActive = false;
        mapCyclePhase = MapCyclePhase.MAP_HANDOFF;
        mapHandoffStage = MapHandoffStage.SKIPPED;
        handoffSourceMapId = null;
        handoffLockedMapId = null;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        knownErrors.clear();
        checkpoints.clear();
        if (!persistFileCoordinationCheckpoint("map-handoff-skip")) {
            return;
        }
        if (SlaveSystem.isSlave()) {
            checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
            state = State.Walking;
        } else {
            state = State.AwaitMasterAllBuiltSkip;
        }
    }

    public void slaveFinished(String slave) {
        if (!miningAssignmentsActive) return;
        if (slaveMiningAssignments.containsKey(slave)) {
            SlaveSystem.finishedSlavesDict.put(slave, false);
            SlaveSystem.activeSlavesDict.put(slave, true);
        }
    }

    @Override
    public void slaveFinished(String slave, long taskId) {
        slaveMined(slave, taskId);
    }

    @Override
    public boolean slaveMined(String slave, long taskId) {
        return slaveMined(slave, taskId, false);
    }

    @Override
    public boolean slaveMined(
        String slave,
        long taskId,
        boolean assignedConnectorsClear
    ) {
        Long expectedTaskId = slaveMiningTaskIds.get(slave);
        if (expectedTaskId == null || expectedTaskId != taskId) {
            Long completedTaskId = completedSlaveMiningTaskIds.get(slave);
            Long pendingFinalization =
                pendingSlaveMiningFinalizations.get(slave);
            if (completedTaskId != null
                && completedTaskId == taskId
                && pendingFinalization != null) {
                SlaveSystem.queueDM(
                    slave,
                    "miningDone:" + pendingFinalization
                );
            }
            return false;
        }
        if (!miningAssignmentsActive) return false;

        SlaveSystem.finishedSlavesDict.put(slave, true);
        SlaveSystem.activeSlavesDict.put(slave, false);
        slaveMiningTaskIds.remove(slave);
        completedSlaveMiningTaskIds.put(slave, taskId);
        MiningAssignment finishedAssignment = slaveMiningAssignments.remove(slave);
        if (finishedAssignment != null) {
            Set<Integer> finishedLines = finishedAssignment.lines();
            reservedMiningLines.removeAll(finishedLines);
            reportedMinedLines.addAll(finishedLines);
            if (assignedConnectorsClear) {
                for (int line : finishedLines) {
                    reportedClearedConnectorPairs.add(line / 2);
                }
            }
        }
        assignNextMiningTask(slave);
        return true;
    }

    @Override
    public void slaveRemoved(String slave) {
        slaveMiningTaskIds.remove(slave);
        completedSlaveMiningTaskIds.remove(slave);
        pendingSlaveMiningFinalizations.remove(slave);
        MiningAssignment abandoned = slaveMiningAssignments.remove(slave);
        if (abandoned != null) reservedMiningLines.removeAll(abandoned.lines());
    }

    public void mineLine(int line, boolean pairedTraversal) {
        mineLine(line, pairedTraversal, pairedTraversal);
    }

    @Override
    public void mineLine(
        int line,
        boolean pairedTraversal,
        boolean reserveWholePair
    ) {
        if (line < 0 || line >= map.length) {
            error("Rejected invalid mining line " + line + ".");
            toggle();
            return;
        }
        if (northWalkwayRelativeY == null) {
            resetMapAreaCache();
            if (!ensureNorthWalkwayResolved()) {
                toggle();
                return;
            }
        }
        if (SlaveSystem.isSlave()) {
            buildingActive = false;
            activeCircularBuildPair = -1;
            activeCircularConnectorIndex = -1;
            circularBuildRecoveryDirection = 0;
            circularBuildPhase = CircularBuildPhase.NONE;
            buildRecoveryPending = false;
            buildRecoveryNeedsInventory = false;
            buildRecoveryRestockAfterEgress = false;
        }
        miningAssignmentsActive = true;
        mapCyclePhase = MapCyclePhase.MINING;
        boolean validPair = reserveWholePair
            && line >= 0
            && (line & 1) == 0
            && line + 1 < map.length;
        Set<Integer> lines = validPair ? Set.of(line, line + 1) : Set.of(line);
        beginMiningAssignment(new MiningAssignment(
            line,
            pairedTraversal && validPair,
            lines
        ));
    }

    @Override
    public void mineLine(
        int line,
        boolean pairedTraversal,
        boolean reserveWholePair,
        long taskId
    ) {
        if (taskId < highestSlaveMiningTaskId) {
            warning("Ignored stale mining task " + taskId + ".");
            return;
        }
        if (taskId == highestSlaveMiningTaskId) {
            if (taskId == currentSlaveMiningTaskId
                && pendingSlaveMiningCompletion != null) {
                SlaveSystem.queueMasterDM(pendingSlaveMiningCompletion);
            }
            return;
        }
        if (state == State.AwaitSlaveContinue) {
            warning(
                "Deferred mining task " + taskId
                    + " because this slave is paused."
            );
            return;
        }
        if (currentSlaveMiningTaskId >= 0 && state != State.AwaitSlaveMineLine) {
            warning(
                "Rejected mining task " + taskId
                    + " because task " + currentSlaveMiningTaskId + " is still active."
            );
            return;
        }
        pendingSlaveMiningCompletion = null;
        highestSlaveMiningTaskId = taskId;
        currentSlaveMiningTaskId = taskId;
        mineLine(line, pairedTraversal, reserveWholePair);
    }

    public void mineLine(int line) {
        mineLine(line, false);
    }

    // Path Change Check

    private void warnPathChanged() {
        if (checkpoints != null && !activationReset.get()) {
            String reString = isActive() ? "re" : "";
            warning("The custom path is only applied if the module is " + reString + "started with Activation Reset enabled!");
        }
    }

    private void startOrContinueFromHud() {
        if (!isActive()) {
            startContinueActivationRequested = true;
            toggle();
            startContinueActivationRequested = false;
            if (!isActive() || state == null) return;
        }

        if (fileMasterRecoveryLoaded
            && isPrinterConfigurationReady()) {
            info(
                "Continuing the persisted Staircased lifecycle "
                    + "from phase " + fileMasterRecoveredPhase + "."
            );
            freezeForRecoveryClassification();
            state = State.AwaitFileRecovery;
            resumeRecoveredFileMasterCycle();
            return;
        }

        if (state == State.SelectingChests) {
            if (adoptProvableUncheckpointedTeardown()) {
                return;
            }
            if (validateStartRequirements()) {
                info("Starting Staircase from the module control.");
                startBuilding();
            }
            return;
        }
        if (state == State.AwaitSlaveContinue
            || state == State.AwaitMasterNextMap) {
            info("Continuing Staircase from the module control.");
            start();
            return;
        }
        switch (activeRecoveryOwner()) {
            case BUILD -> {
                info(
                    "Continuing Staircase build from authoritative "
                        + "world state."
                );
                freezeForRecoveryClassification();
                beginBuildRecovery(false);
                return;
            }
            case MINING -> {
                info(
                    "Continuing Staircase teardown from authoritative "
                        + "world state."
                );
                freezeForRecoveryClassification();
                beginMiningRecovery(false);
                return;
            }
            case LOGISTICS -> {
            }
        }

        warning(
            "Start / Continue is waiting for setup in state "
            + state + ". Complete the highlighted setup selections first."
        );
    }

    private boolean adoptProvableUncheckpointedTeardown() {
        if (SlaveSystem.isFileMode()
            || localCycleCheckpointStore == null
            || mapCyclePhase != MapCyclePhase.IDLE
            || localCycleRecoveryCandidate
            || buildingActive
            || miningAssignmentsActive
            || !circularTraversalForCurrentMap
            || compactPlan == null
            || mapCorner == null
            || mc.player == null
            || !mc.player.isOnGround()) {
            return false;
        }

        resetMapAreaCache();
        if (!ensureNorthWalkwayResolved()) return false;
        Optional<CircularMiningLocalSupport> support =
            circularMiningLocalSupport();
        if (support.isEmpty()) return false;
        CircularMiningLocalSupport local = support.orElseThrow();
        CompactCircularNbtPlan.PairRoute route =
            compactPlan.pairRoutes().get(local.pairIndex());
        CircularMiningRecoveryPlan.Result routeState =
            analyzeCircularMiningRoute(route);
        if (!UncheckpointedTeardownRecoveryPolicy.canAdopt(
            mapCyclePhase,
            routeState,
            true
        )) {
            return false;
        }

        mapCyclePhase = MapCyclePhase.MINING;
        mapHandoffStage = MapHandoffStage.SKIPPED;
        handoffSourceMapId = null;
        handoffLockedMapId = null;
        cycleRecovered = true;
        if (cycleStartedAtMs < 0) {
            cycleStartedAtMs = System.currentTimeMillis();
        }
        recoveredActiveMiningPair = local.pairIndex();
        recoveredActiveMiningTargetIndex =
            local.targetIndex();
        info(
            "The ordered world state proves an interrupted "
                + "teardown on pair " + local.pairIndex()
                + " (air prefix, continuous remaining U suffix). "
                + "Adopting that lifecycle and continuing from "
                + "the verified support under the player."
        );
        beginMapMining(true, true);
        return true;
    }

    private boolean startReadyFileSlaveCycle() {
        if (!SlaveSystem.isFileSlave() || !isFileSlaveReady()) return false;
        State candidate = state;
        if (candidate == State.AwaitSlaveContinue) candidate = oldState;
        boolean waitingForCycle = candidate == State.SelectingChests
            || candidate == State.AwaitMasterNextMap
            || candidate == State.AwaitNBTFile
            || candidate == State.AwaitFileMaster;
        if (!waitingForCycle) return false;

        oldState = null;
        slaveAwaitingNextMapRelease = false;
        nextMapSyncTicks = 0;
        state = candidate;
        startBuilding();
        return true;
    }

    private boolean setupRecoveredMapHandoffSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        for (int slot = 0; slot < 36; slot++) {
            Item item =
                mc.player.getInventory().getStack(slot).getItem();
            if ((item == Items.MAP
                    || item == Items.FILLED_MAP
                    || item == Items.GLASS_PANE)
                && !availableSlots.contains(slot)) {
                availableSlots.add(slot);
            }
        }
        Collections.sort(availableSlots);
        availableHotBarSlots.clear();
        for (int slot : availableSlots) {
            if (slot < 9) availableHotBarSlots.add(slot);
        }

        if (availableSlots.size() < 2) {
            warning(
                "Recovered map handoff needs two reserved inventory slots."
            );
            return false;
        }

        boolean needsAnotherSupply =
            mapHandoffStage == MapHandoffStage.PREPARE_INVENTORY
                || (mapHandoffStage
                    == MapHandoffStage.NEED_SUPPLIES
                    && (playerItemCount(Items.MAP) == 0
                        || playerItemCount(Items.GLASS_PANE) == 0));
        if (needsAnotherSupply && availableHotBarSlots.isEmpty()) {
            int emptyMainSlot = -1;
            for (int slot : availableSlots) {
                if (slot >= 9
                    && mc.player.getInventory()
                        .getStack(slot).isEmpty()) {
                    emptyMainSlot = slot;
                    break;
                }
            }
            if (emptyMainSlot < 0) {
                warning(
                    "Recovered map handoff needs one usable hotbar slot "
                        + "before taking missing supplies."
                );
                return false;
            }
            int targetHotbar = chooseHandoffHotbarSlot();
            Utils.performSwap(emptyMainSlot, targetHotbar);
            availableSlots.remove(Integer.valueOf(emptyMainSlot));
            availableSlots.add(targetHotbar);
            Collections.sort(availableSlots);
            availableHotBarSlots.add(targetHotbar);
        }
        return true;
    }

    private boolean configureFileCoordination() {
        if (coordinationMode.get() == SlaveSystem.CoordinationMode.Chat) {
            return true;
        }
        if (mc.player == null) {
            error("File coordination requires the player to be logged in.");
            return false;
        }

        File syncFolder = sharedSyncFolder.get().isBlank()
            ? new File(mapFolder, "_staircased_sync")
            : new File(sharedSyncFolder.get().trim());
        List<String> configuredSlaves = Arrays.stream(
                fileSlavePlayerNames.get().split(",")
            )
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();
        try {
            SlaveSystem.configureFileCoordination(
                coordinationMode.get(),
                syncFolder.toPath(),
                mc.player.getName().getString(),
                fileMasterPlayerName.get(),
                configuredSlaves,
                filePollTicks.get()
            );
            if (SlaveSystem.isFileMaster() && recoverActiveFileJob.get()) {
                restorePersistedFileMasterCycle();
            }
            info(
                "File coordination enabled as §a"
                    + coordinationMode.get()
                    + "§r in " + syncFolder.getAbsolutePath()
            );
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            error("Could not initialize file coordination: " + failure.getMessage());
            return false;
        }
    }

    private boolean configureLocalCycleCheckpointing() {
        if (SlaveSystem.isFileMode()) return true;
        if (mc.player == null || mapFolder == null) {
            error(
                "Local Staircased lifecycle checkpointing requires "
                    + "a logged-in player and map folder."
            );
            return false;
        }
        try {
            localCycleCheckpointStore =
                StaircasedCycleCheckpointStore.open(
                    mapFolder.toPath(),
                    mc.player.getName().getString()
                );
            Optional<
                StaircasedCycleCheckpointStore.Snapshot
            > persisted = localCycleCheckpointStore.read();
            if (persisted.isEmpty()) return true;
            restorePersistedLocalCycle(
                persisted.orElseThrow()
            );
            return true;
        } catch (
            IOException
                | IllegalArgumentException
                | IllegalStateException failure
        ) {
            error(
                "Could not restore the local Staircased lifecycle "
                    + "checkpoint: " + failure.getMessage()
            );
            return false;
        }
    }

    private void restorePersistedLocalCycle(
        StaircasedCycleCheckpointStore.Snapshot snapshot
    ) {
        if (!Objects.equals(
            snapshot.server(),
            currentServerIdentity()
        ) || !Objects.equals(
            snapshot.dimension(),
            currentDimensionIdentity()
        )) {
            throw new IllegalStateException(
                "The active local Staircased cycle belongs to "
                    + snapshot.server() + " / "
                    + snapshot.dimension()
                    + ", not the world currently joined. Its state "
                    + "file was retained."
            );
        }
        if (snapshot.circularTraversal()
            != circularTraversalForCurrentMap) {
            throw new IllegalStateException(
                "The active local Staircased cycle used circular "
                    + "traversal=" + snapshot.circularTraversal()
                    + ", but the current setting is "
                    + circularTraversalForCurrentMap
                    + ". Its state file was retained."
            );
        }

        coordinationJobId = snapshot.jobId();
        coordinationGeneration = snapshot.generation();
        cycleStartedAtMs = snapshot.startedAtMs();
        cycleCompletedAtMs = snapshot.completedAtMs();
        cycleRecovered = true;
        cycleTimingRecorded = false;
        loadedFileCycleKey = NbtTimingSummary.cycleKey(
            snapshot.jobId(),
            snapshot.generation()
        );
        fileMasterRecoveredPhase = snapshot.phase();
        fileMasterRecoveredSourceName = snapshot.sourceNbt();
        fileMasterRecoveredSourceSha256 =
            snapshot.sourceSha256();
        fileMasterRecoveredPrintingName =
            snapshot.printingNbt();
        fileMasterRecoveredArchivedSourceName =
            snapshot.archivedSourceNbt();
        fileMasterRecoveredArchivedPrintingName =
            snapshot.archivedPrintingNbt();
        fileMasterRecoveredConfigSha256 =
            snapshot.configSha256();
        fileMasterRecoveredPlanSha256 =
            snapshot.compactPlanSha256();
        fileMasterRecoveredCircular = Boolean.toString(
            snapshot.circularTraversal()
        );
        fileMasterRecoveredHandoffStage =
            snapshot.handoffStage();
        fileMasterRecoveredSourceMapId =
            snapshot.handoffSourceMapId();
        fileMasterRecoveredLockedMapId =
            snapshot.handoffLockedMapId();
        fileMasterRecoveredServer = snapshot.server();
        fileMasterRecoveredDimension = snapshot.dimension();
        fileMasterRecoveredMapCorner = snapshot.mapCorner();
        recoveredActiveMiningPair =
            Optional.ofNullable(
                snapshot.activeMiningPair()
            ).orElse(-1);
        recoveredActiveMiningTargetIndex =
            Optional.ofNullable(
                snapshot.activeMiningTargetIndex()
            ).orElse(-1);
        retainedTeardownRecoveryCursor =
            recoveredActiveMiningPair >= 0
                && recoveredActiveMiningTargetIndex >= 0
                ? new DurableTeardownRecoveryCursor.Cursor(
                    recoveredActiveMiningPair,
                    recoveredActiveMiningTargetIndex
                )
                : null;
        localCycleRecoveryCandidate = true;
        info(
            "Found local Staircased lifecycle checkpoint "
                + "Â§a"
                + localCycleCheckpointStore.stateFile()
                    .getFileName()
                + "Â§r for phase " + snapshot.phase()
                + ", runtime state " + snapshot.runtimeState()
                + (recoveredActiveMiningPair < 0
                    ? ""
                    : ", teardown pair "
                        + recoveredActiveMiningPair
                        + (recoveredActiveMiningTargetIndex < 0
                            ? ""
                            : " at support index "
                                + recoveredActiveMiningTargetIndex))
                + "."
        );
    }

    private void restorePersistedFileMasterCycle() {
        Map<String, String> metadata = SlaveSystem.localFileMetadata();
        if (!FILE_MODULE_VALUE.equals(metadata.get(FILE_META_MODULE))) return;

        final MapCyclePhase persistedPhase;
        try {
            persistedPhase = MapCyclePhase.valueOf(
                metadata.getOrDefault(FILE_META_PHASE, "")
            );
        } catch (IllegalArgumentException invalidPhase) {
            throw new IllegalStateException(
                "The persisted Staircased file-coordination phase is invalid.",
                invalidPhase
            );
        }
        if (!persistedPhase.isInProgress()) return;

        final MapHandoffStage persistedHandoffStage;
        try {
            persistedHandoffStage = MapHandoffStage.valueOf(
                metadata.getOrDefault(FILE_META_HANDOFF_STAGE, "")
            );
        } catch (IllegalArgumentException invalidStage) {
            throw new IllegalStateException(
                "The persisted map-handoff stage is invalid.",
                invalidStage
            );
        }
        Integer persistedSourceMapId = parseIntegerMetadata(
            metadata.get(FILE_META_HANDOFF_SOURCE_MAP_ID)
        );
        Integer persistedLockedMapId = parseIntegerMetadata(
            metadata.get(FILE_META_HANDOFF_LOCKED_MAP_ID)
        );
        if (!persistedHandoffStage.isValidFor(persistedPhase)
            || !persistedHandoffStage.hasValidMapIds(
                persistedSourceMapId,
                persistedLockedMapId
            )) {
            throw new IllegalStateException(
                "The persisted active job has an unprovable map-handoff "
                    + "stage or is missing its exact map ID."
            );
        }

        String jobId = metadata.get(FILE_META_JOB_ID);
        Long generation = parseLongMetadata(
            metadata.get(FILE_META_GENERATION)
        );
        String sourceName = metadata.get(FILE_META_SOURCE_NBT);
        String sourceSha256 = metadata.get(FILE_META_SOURCE_SHA256);
        String configSha256 = metadata.get(FILE_META_CONFIG_SHA256);
        String planSha256 = metadata.get(FILE_META_PLAN_SHA256);
        String masterCircular = metadata.get(FILE_META_CIRCULAR);
        String server = metadata.get(FILE_META_SERVER);
        String dimension = metadata.get(FILE_META_DIMENSION);
        String persistedMapCorner = metadata.get(FILE_META_MAP_CORNER);
        String printingName = metadata.get(FILE_META_PRINTING_NBT);
        String persistedArchivedSource =
            metadata.get(FILE_META_ARCHIVED_SOURCE_NBT);
        String persistedArchivedPrinting =
            metadata.get(FILE_META_ARCHIVED_PRINTING_NBT);
        if (!isUuid(jobId)
            || generation == null
            || generation < 1
            || !isSafeSharedFileName(sourceName)
            || !FileFingerprint.isSha256(sourceSha256)
            || !FileFingerprint.isSha256(configSha256)
            || !FileFingerprint.isSha256(planSha256)
            || parseMapCornerIdentity(persistedMapCorner) == null
            || (printingName != null
                && !isSafeSharedFileName(printingName))
            || (persistedArchivedSource != null
                && !isSafeSharedFileName(persistedArchivedSource))
            || (persistedArchivedPrinting != null
                && !isSafeSharedFileName(persistedArchivedPrinting))
            || !Boolean.toString(circularTraversalForCurrentMap).equals(
                masterCircular
            )) {
            throw new IllegalStateException(
                "The persisted active Staircased job is incomplete or does "
                    + "not match the current circular-traversal setting."
            );
        }
        if (!Objects.equals(server, currentServerIdentity())
            || !Objects.equals(dimension, currentDimensionIdentity())) {
            throw new IllegalStateException(
                "The persisted active Staircased job belongs to server "
                    + server + " / dimension " + dimension
                    + ", not the world currently joined."
            );
        }

        coordinationJobId = jobId;
        coordinationGeneration = generation;
        cycleStartedAtMs = Optional.ofNullable(
                parseLongMetadata(metadata.get(FILE_META_STARTED_AT))
            )
            .orElse(-1L);
        cycleCompletedAtMs = Optional.ofNullable(
                parseLongMetadata(metadata.get(FILE_META_COMPLETED_AT))
            )
            .orElse(-1L);
        cycleRecovered = true;
        cycleTimingRecorded = false;
        loadedFileCycleKey = NbtTimingSummary.cycleKey(jobId, generation);
        fileMasterRecoveredPhase = persistedPhase;
        fileMasterRecoveredSourceName = sourceName;
        fileMasterRecoveredSourceSha256 = sourceSha256;
        fileMasterRecoveredConfigSha256 = configSha256;
        fileMasterRecoveredPlanSha256 = planSha256;
        fileMasterRecoveredCircular = masterCircular;
        fileMasterRecoveredHandoffStage = persistedHandoffStage;
        fileMasterRecoveredSourceMapId = persistedSourceMapId;
        fileMasterRecoveredLockedMapId = persistedLockedMapId;
        fileMasterRecoveredServer = server;
        fileMasterRecoveredDimension = dimension;
        fileMasterRecoveredMapCorner = persistedMapCorner;
        fileMasterRecoveredPrintingName =
            isSafeSharedFileName(printingName) ? printingName : null;
        fileMasterRecoveredArchivedSourceName =
            isSafeSharedFileName(persistedArchivedSource)
                ? persistedArchivedSource
                : null;
        fileMasterRecoveredArchivedPrintingName =
            isSafeSharedFileName(persistedArchivedPrinting)
                ? persistedArchivedPrinting
                : null;
        info(
            "Found recoverable file-coordination job §a"
                + loadedFileCycleKey + "§r in phase "
                + persistedPhase + "."
        );
    }

    // Config System

    private void saveConfig(File configFile) {
        if (configFile == null) {
            error("No config file name selected.");
            return;
        }
        if (cartographyTable == null || finishedMapChest == null || dumpStation == null || mapCorner == null
            || materialDict.isEmpty() || usedToolChest == null || toolSet.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "staircased",
                cartographyTable,
                finishedMapChest,
                usedToolChest,
                bed,
                mapMaterialChests,
                dumpStation,
                mapCorner,
                materialDict,
                toolSet,
                anvil,
                enderChest,
                craftingTable,
                usedToolChests,
                registeredToolMinimumEfficiency);
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully saved config to: ").append(configText));
        } catch (IOException e) {
            error("Failed to create config file.");
        }
    }

    private boolean loadConfig(File configFile) {
        if (configFile == null || !configFile.exists() || state == null) {
            warning("Could not find config file.");
            return false;
        }
        List<State> allowedStates = List.of(
            State.SelectingChests,
            State.SelectingBed,
            State.SelectingFinishedMapChest,
            State.SelectingUsedPickaxeChest,
            State.SelectingDumpStation,
            State.SelectingTable,
            State.SelectingMapArea,
            State.AwaitRegisterResponse,
            State.AwaitUsedToolRegistrationResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return false;
        }

        try {
            ConfigDeserializer.ConfigData data =
                ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("staircased")) {
                error("Config file is of type " + data.type + " and not 'staircased'.");
                return false;
            }
            if (data.cartographyTable == null || data.finishedMapChest == null || data.dumpStation == null || data.mapCorner == null
                || data.materialDict.isEmpty() || data.usedToolChest == null || toolSet == null) {
                error("Config file is missing required data.");
                return false;
            }
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.usedToolChest = data.usedToolChest;
            this.bed = data.bed;
            this.anvil = data.anvil;
            this.enderChest = data.enderChest;
            this.craftingTable = data.craftingTable;
            this.usedToolChests = data.usedToolChests;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStation = data.dumpStation;
            this.mapCorner = data.mapCorner;
            this.northWalkwayRelativeY = null;
            resetMapAreaCache();
            this.materialDict = data.materialDict;
            this.toolSet = data.toolSet;
            this.registeredToolMinimumEfficiency =
                new HashMap<>(data.toolMinimumEfficiency);
            this.activeConfigSha256 = FileFingerprint.sha256(
                configFile.toPath()
            );
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully loaded config: ").append(configText));
            info("Interact with the Start Block to start printing.");
            state = State.SelectingChests;
        } catch (IOException e) {
            error("Failed to read config file.");
            activeConfigSha256 = null;
            return false;
        }
        return true;
    }

    private void beginNewMapCycle() {
        fileMasterRecoveryLoaded = false;
        fileMasterRecoveredPhase = null;
        fileMasterRecoveredSourceName = null;
        fileMasterRecoveredSourceSha256 = null;
        fileMasterRecoveredPrintingName = null;
        fileMasterRecoveredArchivedSourceName = null;
        fileMasterRecoveredArchivedPrintingName = null;
        fileMasterRecoveredConfigSha256 = null;
        fileMasterRecoveredPlanSha256 = null;
        fileMasterRecoveredCircular = null;
        fileMasterRecoveredHandoffStage = null;
        fileMasterRecoveredSourceMapId = null;
        fileMasterRecoveredLockedMapId = null;
        fileMasterRecoveredServer = null;
        fileMasterRecoveredDimension = null;
        fileMasterRecoveredMapCorner = null;
        fileRecoveryIdentityWarning = false;
        localCycleRecoveryCandidate = false;
        recoveredActiveMiningPair = -1;
        recoveredActiveMiningTargetIndex = -1;
        retainedTeardownRecoveryCursor = null;
        archivedSourceName = null;
        archivedPrintingName = null;
        coordinationGeneration = Math.max(0L, coordinationGeneration) + 1L;
        cycleStartedAtMs = -1L;
        cycleCompletedAtMs = -1L;
        cycleRecovered = false;
        cycleTimingRecorded = false;
        mapHandoffStage = MapHandoffStage.NONE;
        handoffSourceMapId = null;
        handoffLockedMapId = null;
        handoffConfirmationAfterSequence = -1L;
        handoffConfirmationAttempts = 0;
        handoffMapHotbarSlot = -1;
        loadedFileCycleKey = NbtTimingSummary.cycleKey(
            coordinationJobId,
            coordinationGeneration
        );
        publishFileCoordinationState();
        if (SlaveSystem.isFileMaster()) {
            SlaveSystem.generateIntervals();
        }
    }

    /**
     * Mirrors only lifecycle metadata through the file transport. The actual
     * build/mining commands still use SlaveSystem's existing, idempotent wire
     * protocol.
     */
    private void publishFileCoordinationState() {
        if (!SlaveSystem.isFileMode()) return;

        boolean publishingRecoveredCheckpoint =
            SlaveSystem.isFileMaster()
                && fileMasterRecoveredPhase != null;
        String publishedSourceName = publishingRecoveredCheckpoint
            ? fileMasterRecoveredSourceName
            : logicalSourceName;
        String publishedSourceSha256 = publishingRecoveredCheckpoint
            ? fileMasterRecoveredSourceSha256
            : activeSourceSha256;
        String publishedPrintingName = publishingRecoveredCheckpoint
            ? fileMasterRecoveredPrintingName
            : logicalPrintingName;
        String publishedArchivedSourceName = publishingRecoveredCheckpoint
            ? fileMasterRecoveredArchivedSourceName
            : archivedSourceName;
        String publishedArchivedPrintingName = publishingRecoveredCheckpoint
            ? fileMasterRecoveredArchivedPrintingName
            : archivedPrintingName;
        String publishedConfigSha256 = publishingRecoveredCheckpoint
            ? fileMasterRecoveredConfigSha256
            : activeConfigSha256;
        String publishedPlanSha256 = publishingRecoveredCheckpoint
            ? fileMasterRecoveredPlanSha256
            : activeCompactPlanSha256;
        String publishedCircular = publishingRecoveredCheckpoint
            ? fileMasterRecoveredCircular
            : Boolean.toString(circularTraversalForCurrentMap);
        String publishedServer = publishingRecoveredCheckpoint
            ? fileMasterRecoveredServer
            : currentServerIdentity();
        String publishedDimension = publishingRecoveredCheckpoint
            ? fileMasterRecoveredDimension
            : currentDimensionIdentity();
        String publishedMapCorner = publishingRecoveredCheckpoint
            ? fileMasterRecoveredMapCorner
            : mapCornerIdentity();
        MapCyclePhase publishedPhase = publishingRecoveredCheckpoint
            ? fileMasterRecoveredPhase
            : mapCyclePhase;
        MapHandoffStage publishedHandoffStage =
            publishingRecoveredCheckpoint
                ? fileMasterRecoveredHandoffStage
                : mapHandoffStage;
        Integer publishedSourceMapId = publishingRecoveredCheckpoint
            ? fileMasterRecoveredSourceMapId
            : handoffSourceMapId;
        Integer publishedLockedMapId = publishingRecoveredCheckpoint
            ? fileMasterRecoveredLockedMapId
            : handoffLockedMapId;

        SlaveSystem.setFileMetadata(FILE_META_MODULE, FILE_MODULE_VALUE);
        SlaveSystem.setFileMetadata(FILE_META_JOB_ID, coordinationJobId);
        SlaveSystem.setFileMetadata(
            FILE_META_GENERATION,
            Long.toString(coordinationGeneration)
        );
        SlaveSystem.setFileMetadata(
            FILE_META_SOURCE_NBT,
            publishedSourceName
        );
        SlaveSystem.setFileMetadata(
            FILE_META_SOURCE_SHA256,
            publishedSourceSha256
        );
        SlaveSystem.setFileMetadata(
            FILE_META_PRINTING_NBT,
            publishedPrintingName
        );
        SlaveSystem.setFileMetadata(
            FILE_META_ARCHIVED_SOURCE_NBT,
            publishedArchivedSourceName
        );
        SlaveSystem.setFileMetadata(
            FILE_META_ARCHIVED_PRINTING_NBT,
            publishedArchivedPrintingName
        );
        SlaveSystem.setFileMetadata(
            FILE_META_CONFIG_SHA256,
            publishedConfigSha256
        );
        SlaveSystem.setFileMetadata(
            FILE_META_PLAN_SHA256,
            publishedPlanSha256
        );
        SlaveSystem.setFileMetadata(
            FILE_META_CIRCULAR,
            publishedCircular
        );
        SlaveSystem.setFileMetadata(
            FILE_META_SERVER,
            publishedServer
        );
        SlaveSystem.setFileMetadata(
            FILE_META_DIMENSION,
            publishedDimension
        );
        SlaveSystem.setFileMetadata(
            FILE_META_MAP_CORNER,
            publishedMapCorner
        );
        SlaveSystem.setFileMetadata(
            FILE_META_PLAYER_X,
            mc.player == null
                ? null
                : Integer.toString(mc.player.getBlockX())
        );
        SlaveSystem.setFileMetadata(
            FILE_META_PLAYER_Y,
            mc.player == null
                ? null
                : Integer.toString(mc.player.getBlockY())
        );
        SlaveSystem.setFileMetadata(
            FILE_META_PLAYER_Z,
            mc.player == null
                ? null
                : Integer.toString(mc.player.getBlockZ())
        );
        SlaveSystem.setFileMetadata(
            FILE_META_MAP_DATA_LOADED,
            Boolean.toString(isMapAreaDataLoaded())
        );
        SlaveSystem.setFileMetadata(FILE_META_PHASE, publishedPhase.name());
        SlaveSystem.setFileMetadata(
            FILE_META_HANDOFF_STAGE,
            publishedHandoffStage == null
                ? null
                : publishedHandoffStage.name()
        );
        SlaveSystem.setFileMetadata(
            FILE_META_HANDOFF_SOURCE_MAP_ID,
            publishedSourceMapId == null
                ? null
                : Integer.toString(publishedSourceMapId)
        );
        SlaveSystem.setFileMetadata(
            FILE_META_HANDOFF_LOCKED_MAP_ID,
            publishedLockedMapId == null
                ? null
                : Integer.toString(publishedLockedMapId)
        );
        SlaveSystem.setFileMetadata(
            FILE_META_RUNNING,
            Boolean.toString(
                !fileMasterRecoveryLoaded
                    && state != State.AwaitFileSlaves
                    && (mapCyclePhase == MapCyclePhase.BUILDING
                        || mapCyclePhase == MapCyclePhase.MAP_HANDOFF
                        || mapCyclePhase == MapCyclePhase.MAP_DEPOSITED
                        || mapCyclePhase == MapCyclePhase.MINING)
            )
        );
        SlaveSystem.setFileMetadata(
            FILE_META_ACTIVE,
            Boolean.toString(isClientOnline())
        );
        SlaveSystem.setFileMetadata(
            FILE_META_STARTED_AT,
            cycleStartedAtMs < 0 ? null : Long.toString(cycleStartedAtMs)
        );
        SlaveSystem.setFileMetadata(
            FILE_META_COMPLETED_AT,
            cycleCompletedAtMs < 0 ? null : Long.toString(cycleCompletedAtMs)
        );
        SlaveSystem.setFileMetadata(
            FILE_META_STATUS,
            state == null ? "INITIALIZING" : state.name()
        );
        if (SlaveSystem.isFileSlave()) {
            SlaveSystem.setFileMetadata(
                FILE_META_READY,
                Boolean.toString(isFileSlaveReady())
            );
        }
    }

    private boolean persistLocalCycleCheckpoint(
        String checkpoint
    ) {
        if (localCycleCheckpointStore == null
            || mapCyclePhase == null
            || !mapCyclePhase.isInProgress()) {
            return true;
        }
        if (mc.player == null) {
            return failLocalCycleCheckpoint(
                checkpoint,
                "the player is unavailable"
            );
        }

        Integer activePair = null;
        Integer activeTargetIndex = null;
        if (mapCyclePhase == MapCyclePhase.MINING) {
            Optional<CircularMiningLocalSupport> localSupport =
                circularMiningLocalSupport();
            DurableTeardownRecoveryCursor.Cursor liveCursor =
                localSupport.map(this::teardownRecoveryCursor)
                    .orElse(null);
            DurableTeardownRecoveryCursor.Cursor orderedCursor =
                activeOrderedTeardownRecoveryCursor();
            DurableTeardownRecoveryCursor.Cursor retainedCursor =
                validateTeardownRecoveryCursor(
                    retainedTeardownRecoveryCursor
                ).orElse(null);
            Integer assignmentPair =
                currentMiningPairForCheckpoint();
            if (assignmentPair != null
                && liveCursor != null
                && liveCursor.pairIndex() != assignmentPair) {
                liveCursor = null;
            }
            Integer checkpointPair = assignmentPair != null
                ? assignmentPair
                : liveCursor != null
                    ? liveCursor.pairIndex()
                    : orderedCursor != null
                        ? orderedCursor.pairIndex()
                        : retainedCursor != null
                            ? retainedCursor.pairIndex()
                            : preferredRecoveredMiningPair >= 0
                                ? preferredRecoveredMiningPair
                                : recoveredActiveMiningPair >= 0
                                    ? recoveredActiveMiningPair
                                    : null;
            Optional<DurableTeardownRecoveryCursor.Cursor>
                selectedCursor =
                    DurableTeardownRecoveryCursor.select(
                        liveCursor,
                        checkpointPair,
                        orderedCursor,
                        retainedCursor
                    );
            if (selectedCursor.isPresent()) {
                retainedTeardownRecoveryCursor =
                    selectedCursor.orElseThrow();
                activePair =
                    retainedTeardownRecoveryCursor.pairIndex();
                activeTargetIndex =
                    retainedTeardownRecoveryCursor.targetIndex();
                if (liveCursor == null
                    && !"runtime-heartbeat".equals(checkpoint)) {
                    debugLog(
                        "Recovery",
                        "checkpoint " + checkpoint
                            + " retained canonical teardown cursor pair="
                            + activePair + " supportIndex="
                            + activeTargetIndex + " source="
                            + (Objects.equals(
                                orderedCursor,
                                retainedTeardownRecoveryCursor
                            )
                                ? "ordered-route"
                                : "last-confirmed")
                    );
                }
            } else {
                if (checkpointPair == null
                    || retainedTeardownRecoveryCursor != null
                        && retainedTeardownRecoveryCursor.pairIndex()
                            != checkpointPair) {
                    retainedTeardownRecoveryCursor = null;
                }
                activePair = checkpointPair;
            }
        }

        if (logicalSourceName == null
            || activeSourceSha256 == null
            || activeConfigSha256 == null
            || activeCompactPlanSha256 == null
            || mapCornerIdentity() == null
            || mapHandoffStage == null
            || cycleStartedAtMs < 0) {
            return failLocalCycleCheckpoint(
                checkpoint,
                "the active cycle identity is incomplete"
            );
        }

        StaircasedCycleCheckpointStore.Snapshot snapshot =
            new StaircasedCycleCheckpointStore.Snapshot(
                StaircasedCycleCheckpointStore.SCHEMA_VERSION,
                mc.player.getName().getString(),
                coordinationJobId,
                coordinationGeneration,
                logicalSourceName,
                activeSourceSha256,
                logicalPrintingName,
                archivedSourceName,
                archivedPrintingName,
                activeConfigSha256,
                activeCompactPlanSha256,
                circularTraversalForCurrentMap,
                currentServerIdentity(),
                currentDimensionIdentity(),
                mapCornerIdentity(),
                mapCyclePhase,
                mapHandoffStage,
                handoffSourceMapId,
                handoffLockedMapId,
                cycleStartedAtMs,
                cycleCompletedAtMs,
                state == null ? "INITIALIZING" : state.name(),
                activePair,
                activeTargetIndex,
                checkpoint,
                System.currentTimeMillis()
            );
        try {
            localCycleCheckpointStore.save(snapshot);
            lastLocalCycleCheckpointTick = clientActionTick;
            return true;
        } catch (IOException | IllegalArgumentException failure) {
            return failLocalCycleCheckpoint(
                checkpoint,
                failure.getMessage()
            );
        }
    }

    private Integer currentMiningPairForCheckpoint() {
        if (mapCyclePhase != MapCyclePhase.MINING
            || currentMiningLines == null
            || currentMiningLines.size() != 2) {
            return null;
        }
        int first = Collections.min(currentMiningLines);
        int last = Collections.max(currentMiningLines);
        if ((first & 1) != 0 || last != first + 1) {
            return null;
        }
        return first / 2;
    }

    private boolean failLocalCycleCheckpoint(
        String checkpoint,
        String reason
    ) {
        error(
            "Could not persist local Staircased checkpoint '"
                + checkpoint + "': " + reason
        );
        stopMovement();
        if (isActive()) toggle();
        return false;
    }

    private boolean clearLocalCycleCheckpoint(String checkpoint) {
        if (localCycleCheckpointStore == null) return true;
        try {
            localCycleCheckpointStore.clear();
            localCycleRecoveryCandidate = false;
            recoveredActiveMiningPair = -1;
            recoveredActiveMiningTargetIndex = -1;
            retainedTeardownRecoveryCursor = null;
            return true;
        } catch (IOException failure) {
            return failLocalCycleCheckpoint(
                checkpoint,
                failure.getMessage()
            );
        }
    }

    private boolean persistFileCoordinationCheckpoint(String checkpoint) {
        if (!persistLocalCycleCheckpoint(checkpoint)) return false;
        if (!SlaveSystem.isFileMode()) return true;
        publishFileCoordinationState();
        try {
            SlaveSystem.flushFileCoordinationNow();
            return true;
        } catch (IOException failure) {
            error(
                "Could not persist file-coordination checkpoint '"
                    + checkpoint + "': " + failure.getMessage()
            );
            stopMovement();
            if (isActive()) toggle();
            return false;
        }
    }

    private boolean isFileSlaveReady() {
        if (!SlaveSystem.isFileSlave()
            || !isClientOnline()
            || mapFile == null
            || loadedFileCycleKey == null
            || !isPrinterConfigurationReady()
            || !isMapAreaDataLoaded()) {
            return false;
        }
        Map<String, String> masterMetadata =
            SlaveSystem.masterFileMetadata();
        String masterKey = cycleKeyFromMetadata(masterMetadata);
        return FILE_MODULE_VALUE.equals(masterMetadata.get(FILE_META_MODULE))
            && loadedFileCycleKey.equals(masterKey)
            && Objects.equals(logicalSourceName,
                masterMetadata.get(FILE_META_SOURCE_NBT)
            )
            && activeSourceSha256 != null
            && activeSourceSha256.equals(
                masterMetadata.get(FILE_META_SOURCE_SHA256)
            )
            && activeCompactPlanSha256 != null
            && activeCompactPlanSha256.equals(
                masterMetadata.get(FILE_META_PLAN_SHA256)
            )
            && Boolean.toString(circularTraversalForCurrentMap).equals(
                masterMetadata.get(FILE_META_CIRCULAR)
            )
            && coordinationIdentityMatches(masterMetadata)
            && metadataPositionWithinMapZone(masterMetadata)
            && "true".equalsIgnoreCase(
                masterMetadata.get(FILE_META_MAP_DATA_LOADED)
            )
            && localPlayerWithinMapZone()
            && configFingerprintMatches(masterMetadata);
    }

    private boolean isClientOnline() {
        return isActive()
            && mc.player != null
            && mc.world != null
            && mc.getNetworkHandler() != null;
    }

    private String currentServerIdentity() {
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address;
        }
        return mc.isInSingleplayer() ? "singleplayer" : null;
    }

    private String currentDimensionIdentity() {
        return mc.world == null
            ? null
            : mc.world.getRegistryKey().getValue().toString();
    }

    private String mapCornerIdentity() {
        return mapCorner == null
            ? null
            : mapCorner.getX() + "," + mapCorner.getY() + ","
                + mapCorner.getZ();
    }

    private boolean isPrinterConfigurationReady() {
        return cartographyTable != null
            && finishedMapChest != null
            && usedToolChest != null
            && dumpStation != null
            && mapCorner != null
            && materialDict != null
            && !materialDict.isEmpty()
            && toolSet != null
            && !toolSet.isEmpty()
            && mapMaterialChests != null
            && !mapMaterialChests.isEmpty()
            && (!SlaveSystem.isFileMode() || activeConfigSha256 != null);
    }

    private boolean allFileSlavesReady() {
        if (!SlaveSystem.isFileMaster()) return true;
        String expectedKey = NbtTimingSummary.cycleKey(
            coordinationJobId,
            coordinationGeneration
        );
        String expectedSource = logicalSourceName;
        long maximumAgeMs = filePeerTimeoutSeconds.get() * 1000L;
        for (String slave : SlaveSystem.slaves) {
            if (!SlaveSystem.isFilePeerFresh(slave, maximumAgeMs)
                || !SlaveSystem.isIntervalAcknowledged(slave)) {
                return false;
            }
            Map<String, String> metadata =
                SlaveSystem.remoteFileMetadata(slave);
            if (!FILE_MODULE_VALUE.equals(metadata.get(FILE_META_MODULE))
                || !"true".equalsIgnoreCase(
                    metadata.get(FILE_META_ACTIVE)
                )
                || !"true".equalsIgnoreCase(metadata.get(FILE_META_READY))
                || !expectedKey.equals(cycleKeyFromMetadata(metadata))
                || !Objects.equals(
                    expectedSource,
                    metadata.get(FILE_META_SOURCE_NBT)
                )
                || activeSourceSha256 == null
                || !activeSourceSha256.equals(
                    metadata.get(FILE_META_SOURCE_SHA256)
                )
                || activeCompactPlanSha256 == null
                || !activeCompactPlanSha256.equals(
                    metadata.get(FILE_META_PLAN_SHA256)
                )
                || !Boolean.toString(
                    circularTraversalForCurrentMap
                ).equals(metadata.get(FILE_META_CIRCULAR))
                || !coordinationIdentityMatches(metadata)
                || !metadataPositionWithinMapZone(metadata)
                || !"true".equalsIgnoreCase(
                    metadata.get(FILE_META_MAP_DATA_LOADED)
                )
                || (activeConfigSha256 != null
                    && !activeConfigSha256.equals(
                        metadata.get(FILE_META_CONFIG_SHA256)
                    ))) {
                return false;
            }
        }
        return true;
    }

    private boolean configFingerprintMatches(
        Map<String, String> masterMetadata
    ) {
        String expected = masterMetadata.get(FILE_META_CONFIG_SHA256);
        return FileFingerprint.isSha256(expected)
            && expected.equals(activeConfigSha256);
    }

    private boolean coordinationIdentityMatches(
        Map<String, String> metadata
    ) {
        return Objects.equals(
                currentServerIdentity(),
                metadata.get(FILE_META_SERVER)
            )
            && Objects.equals(
                currentDimensionIdentity(),
                metadata.get(FILE_META_DIMENSION)
            )
            && Objects.equals(
                mapCornerIdentity(),
                metadata.get(FILE_META_MAP_CORNER)
            );
    }

    private boolean metadataPositionWithinMapZone(
        Map<String, String> metadata
    ) {
        Integer playerX = parseIntegerMetadata(
            metadata.get(FILE_META_PLAYER_X)
        );
        Integer playerY = parseIntegerMetadata(
            metadata.get(FILE_META_PLAYER_Y)
        );
        Integer playerZ = parseIntegerMetadata(
            metadata.get(FILE_META_PLAYER_Z)
        );
        return playerX != null
            && playerY != null
            && playerZ != null
            && positionWithinMapZone(playerX, playerY, playerZ);
    }

    private boolean localPlayerWithinMapZone() {
        return mc.player != null
            && positionWithinMapZone(
                mc.player.getBlockX(),
                mc.player.getBlockY(),
                mc.player.getBlockZ()
            );
    }

    private boolean positionWithinMapZone(int x, int y, int z) {
        if (mapCorner == null
            || minimumRelativeSupportY == Integer.MAX_VALUE
            || maximumRelativeSupportY == Integer.MIN_VALUE) {
            return false;
        }
        int margin = fileRecoveryMarginBlocks.get();
        int minimumSupportY =
            mapCorner.getY() + minimumRelativeSupportY;
        int maximumSupportY =
            mapCorner.getY() + maximumRelativeSupportY;
        int verticalMargin = 4;
        return x >= mapCorner.getX() - margin
            && x <= mapCorner.getX() + 127 + margin
            && y >= minimumSupportY + 1 - verticalMargin
            && y <= maximumSupportY + 1 + verticalMargin
            && z >= mapCorner.getZ() - margin
            && z <= mapCorner.getZ() + 127 + margin;
    }

    private boolean isMapAreaDataLoaded() {
        if (mc.world == null || mapCorner == null) return false;
        int minimumChunkX = mapCorner.getX() >> 4;
        int maximumChunkX = (mapCorner.getX() + 127) >> 4;
        int minimumChunkZ = mapCorner.getZ() >> 4;
        int maximumChunkZ = (mapCorner.getZ() + 127) >> 4;
        for (int chunkX = minimumChunkX;
             chunkX <= maximumChunkX;
             chunkX++) {
            for (int chunkZ = minimumChunkZ;
                 chunkZ <= maximumChunkZ;
                 chunkZ++) {
                if (!MapAreaCache.hasBlockData(
                    new BlockPos(
                        chunkX << 4,
                        mapCorner.getY(),
                        chunkZ << 4
                    )
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String cycleKeyFromMetadata(Map<String, String> metadata) {
        String jobId = metadata.get(FILE_META_JOB_ID);
        Long generation = parseLongMetadata(
            metadata.get(FILE_META_GENERATION)
        );
        if (jobId == null || jobId.isBlank() || generation == null) return null;
        try {
            return NbtTimingSummary.cycleKey(jobId, generation);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Long parseLongMetadata(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseIntegerMetadata(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BlockPos parseMapCornerIdentity(String value) {
        if (value == null) return null;
        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 3) return null;
        try {
            return new BlockPos(
                Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1]),
                Integer.parseInt(coordinates[2])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * File slaves never choose an NBT independently. They load the exact
     * source filename and generation advertised by the master.
     */
    private void synchronizeFileSlaveCycle() {
        if (!SlaveSystem.isFileSlave() || mapFolder == null) return;
        Map<String, String> metadata = SlaveSystem.masterFileMetadata();
        if (!FILE_MODULE_VALUE.equals(metadata.get(FILE_META_MODULE))) {
            publishFileCoordinationState();
            return;
        }

        String jobId = metadata.get(FILE_META_JOB_ID);
        Long generation = parseLongMetadata(
            metadata.get(FILE_META_GENERATION)
        );
        String sourceName = metadata.get(FILE_META_SOURCE_NBT);
        String sourceSha256 = metadata.get(FILE_META_SOURCE_SHA256);
        String planSha256 = metadata.get(FILE_META_PLAN_SHA256);
        String masterCircular = metadata.get(FILE_META_CIRCULAR);
        if (!isUuid(jobId)
            || generation == null
            || generation < 1
            || !isSafeSharedFileName(sourceName)
            || !FileFingerprint.isSha256(sourceSha256)
            || !FileFingerprint.isSha256(planSha256)
            || !Boolean.toString(circularTraversal.get()).equals(
                masterCircular
            )) {
            publishFileCoordinationState();
            return;
        }
        String advertisedKey;
        try {
            advertisedKey = NbtTimingSummary.cycleKey(jobId, generation);
        } catch (IllegalArgumentException ignored) {
            publishFileCoordinationState();
            return;
        }
        if (advertisedKey.equals(loadedFileCycleKey)
            && mapFile != null
            && sourceName.equals(logicalSourceName)) {
            publishFileCoordinationState();
            return;
        }
        if (advertisedKey.equals(rejectedFileCycleKey)) {
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            return;
        }
        rejectedFileCycleKey = null;
        if (!canSwitchFileSlaveCycle()) {
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "WAITING_TO_FINISH_PREVIOUS_CYCLE"
            );
            return;
        }

        File advertisedSource = new File(mapFolder, sourceName);
        boolean archivedRecovery = false;
        String recoveredArchivedPrintingName = null;
        String advertisedPhase = metadata.get(FILE_META_PHASE);
        if (!advertisedSource.isFile()
            && (MapCyclePhase.VERIFIED_CLEAR.name().equals(advertisedPhase)
                || MapCyclePhase.POST_MINING.name().equals(advertisedPhase))) {
            String archivedName =
                metadata.get(FILE_META_ARCHIVED_SOURCE_NBT);
            File archivedSource = new File(
                new File(mapFolder, "_finished_maps"),
                isSafeSharedFileName(archivedName)
                    ? archivedName
                    : sourceName
            );
            if (archivedSource.isFile()) {
                advertisedSource = archivedSource;
                archivedRecovery = true;
            } else {
                try {
                    Optional<FinishedNbtArchiver.LocatedPair> located =
                        FinishedNbtArchiver.locateArchivedPair(
                            mapFolder.toPath(),
                            sourceName,
                            isSafeSharedFileName(
                                metadata.get(FILE_META_PRINTING_NBT)
                            )
                                ? metadata.get(FILE_META_PRINTING_NBT)
                                : null,
                            sourceSha256
                        );
                    if (located.isPresent()) {
                        advertisedSource =
                            located.get().archivedSource().toFile();
                        recoveredArchivedPrintingName =
                            located.get().archivedGenerated()
                                .map(path ->
                                    path.getFileName().toString()
                                )
                                .orElse(null);
                        archivedRecovery = true;
                    }
                } catch (IOException | IllegalArgumentException failure) {
                    SlaveSystem.setFileMetadata(
                        FILE_META_READY,
                        "false"
                    );
                    SlaveSystem.setFileMetadata(
                        FILE_META_STATUS,
                        "AMBIGUOUS_ARCHIVED_SOURCE_NBT"
                    );
                    error(
                        "File slave could not recover archived NBT "
                            + sourceName + ": " + failure.getMessage()
                    );
                    return;
                }
            }
        }
        if (!advertisedSource.isFile()) {
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "MISSING_SOURCE_NBT"
            );
            return;
        }

        State previousState = state;
        mapFile = advertisedSource;
        logicalSourceName = sourceName;
        logicalPrintingName = isSafeSharedFileName(
            metadata.get(FILE_META_PRINTING_NBT)
        )
            ? metadata.get(FILE_META_PRINTING_NBT)
            : null;
        archivedSourceName = archivedRecovery
            ? advertisedSource.getName()
            : null;
        archivedPrintingName = archivedRecovery
            ? recoveredArchivedPrintingName != null
                ? recoveredArchivedPrintingName
                : isSafeSharedFileName(
                    metadata.get(FILE_META_ARCHIVED_PRINTING_NBT)
                )
                    ? metadata.get(FILE_META_ARCHIVED_PRINTING_NBT)
                    : null
            : null;
        if (!startedFiles.contains(advertisedSource)) {
            startedFiles.add(advertisedSource);
        }
        if (!loadNBTFile()) {
            rejectedFileCycleKey = advertisedKey;
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "INVALID_SOURCE_NBT"
            );
            return;
        }
        if (!sourceSha256.equals(activeSourceSha256)) {
            rejectedFileCycleKey = advertisedKey;
            mapFile = null;
            generatedMapFile = null;
            compactPlan = null;
            activeCompactPlanSha256 = null;
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "SOURCE_NBT_HASH_MISMATCH"
            );
            error(
                "File slave rejected " + sourceName
                    + " because its SHA-256 does not match the master's file."
            );
            return;
        }
        if (!planSha256.equals(activeCompactPlanSha256)) {
            rejectedFileCycleKey = advertisedKey;
            mapFile = null;
            generatedMapFile = null;
            compactPlan = null;
            activeCompactPlanSha256 = null;
            SlaveSystem.setFileMetadata(FILE_META_READY, "false");
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "COMPACT_PLAN_HASH_MISMATCH"
            );
            error(
                "File slave rejected " + sourceName
                    + " because its generated compact plan differs from the master."
            );
            return;
        }
        if (archivedRecovery) {
            String printingName = archivedPrintingName != null
                ? archivedPrintingName
                : metadata.get(FILE_META_PRINTING_NBT);
            if (isSafeSharedFileName(printingName)) {
                File archivedPrinting = new File(
                    advertisedSource.getParentFile(),
                    printingName
                );
                if (archivedPrinting.isFile()) {
                    generatedMapFile = archivedPrinting;
                    activeMapName = archivedPrinting.getName();
                }
            }
        }
        currentMapArchived = archivedRecovery;

        coordinationJobId = jobId;
        coordinationGeneration = generation;
        cycleStartedAtMs = Optional.ofNullable(
                parseLongMetadata(metadata.get(FILE_META_STARTED_AT))
            )
            .orElse(-1L);
        cycleCompletedAtMs = Optional.ofNullable(
                parseLongMetadata(metadata.get(FILE_META_COMPLETED_AT))
            )
            .orElse(-1L);
        cycleRecovered = cycleStartedAtMs >= 0;
        cycleTimingRecorded = false;
        loadedFileCycleKey = advertisedKey;
        printingComplete = false;

        if (previousState == State.AwaitMasterNextMap
            || previousState == State.AwaitNBTFile
            || previousState == State.AwaitFileMaster) {
            state = State.AwaitMasterNextMap;
        }
        info(
            "File slave loaded master NBT generation §a"
                + generation + "§r: " + sourceName
        );
        publishFileCoordinationState();
    }

    private boolean canSwitchFileSlaveCycle() {
        if (state == null) return true;
        return switch (state) {
            case SelectingMapArea,
                SelectingTable,
                SelectingUsedPickaxeChest,
                SelectingDumpStation,
                SelectingFinishedMapChest,
                SelectingBed,
                SelectingChests,
                AwaitRegisterResponse,
                AwaitUsedToolRegistrationResponse,
                AwaitNBTFile,
                AwaitMasterNextMap,
                AwaitFileMaster -> true;
            default -> !mapCyclePhase.isInProgress()
                && !buildingActive
                && !miningAssignmentsActive;
        };
    }

    private static boolean isSafeSharedFileName(String fileName) {
        if (fileName == null
            || fileName.isBlank()
            || !fileName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            return false;
        }
        return new File(fileName).getName().equals(fileName)
            && !fileName.contains("/")
            && !fileName.contains("\\");
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        if (SlaveSystem.isFileSlave()) {
            synchronizeFileSlaveCycle();
            return mapFile != null;
        }
        if (fileMasterRecoveredSourceName != null
            && !fileMasterRecoveryLoaded) {
            return prepareRecoveredFileMasterCycle();
        }
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("All nbt files finished");
                toggle();
            }
            return false;
        }
        logicalSourceName = mapFile.getName();
        logicalPrintingName = null;
        archivedSourceName = null;
        archivedPrintingName = null;
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }
        logicalPrintingName = generatedMapFile == null
            ? null
            : generatedMapFile.getName();

        beginNewMapCycle();
        return true;
    }

    private boolean prepareRecoveredFileMasterCycle() {
        boolean archived =
            fileMasterRecoveredArchivedSourceName != null;
        File source;
        if (archived) {
            source = new File(
                new File(
                    mapFolder,
                    FinishedNbtArchiver.FINISHED_DIRECTORY_NAME
                ),
                fileMasterRecoveredArchivedSourceName
            );
        } else {
            source = new File(mapFolder, fileMasterRecoveredSourceName);
        }
        if (!source.isFile()) {
            try {
                Optional<FinishedNbtArchiver.LocatedPair> located =
                    FinishedNbtArchiver.locateArchivedPair(
                        mapFolder.toPath(),
                        fileMasterRecoveredSourceName,
                        fileMasterRecoveredPrintingName,
                        fileMasterRecoveredSourceSha256
                    );
                if (located.isPresent()) {
                    FinishedNbtArchiver.LocatedPair pair = located.get();
                    source = pair.archivedSource().toFile();
                    archived = true;
                    fileMasterRecoveredArchivedSourceName =
                        source.getName();
                    fileMasterRecoveredArchivedPrintingName =
                        pair.archivedGenerated()
                            .map(path -> path.getFileName().toString())
                            .orElse(null);
                }
            } catch (IOException | IllegalArgumentException failure) {
                error(
                    "Cannot recover persisted archive for "
                        + loadedFileCycleKey + ": "
                        + failure.getMessage()
                );
                return false;
            }
        }
        if (!source.isFile()) {
            error(
                "Cannot recover persisted job "
                    + loadedFileCycleKey + ": source NBT "
                    + fileMasterRecoveredSourceName
                    + " is missing from both the map and finished folders."
            );
            return false;
        }

        mapFile = source;
        logicalSourceName = fileMasterRecoveredSourceName;
        logicalPrintingName = fileMasterRecoveredPrintingName;
        archivedSourceName = archived
            ? source.getName()
            : null;
        archivedPrintingName =
            fileMasterRecoveredArchivedPrintingName;
        if (!startedFiles.contains(source)) startedFiles.add(source);
        if (!loadNBTFile()) {
            error(
                "Cannot recover persisted job "
                    + loadedFileCycleKey + " because its NBT was rejected."
            );
            return false;
        }
        if (!Objects.equals(
            fileMasterRecoveredSourceSha256,
            activeSourceSha256
        )) {
            error(
                "Cannot recover persisted job "
                    + loadedFileCycleKey
                    + ": the source NBT SHA-256 changed."
            );
            return false;
        }
        if (!Objects.equals(
            fileMasterRecoveredPlanSha256,
            activeCompactPlanSha256
        )) {
            error(
                "Cannot recover persisted job "
                    + loadedFileCycleKey
                    + ": the generated compact runtime plan changed."
            );
            return false;
        }
        currentMapArchived = archived;

        if (fileMasterRecoveredPrintingName != null) {
            String onDiskPrintingName = archived
                && fileMasterRecoveredArchivedPrintingName != null
                ? fileMasterRecoveredArchivedPrintingName
                : fileMasterRecoveredPrintingName;
            File printing = archived
                ? new File(source.getParentFile(), onDiskPrintingName)
                : new File(
                    new File(mapFolder, "_generated_compact"),
                    onDiskPrintingName
                );
            if (printing.isFile()) {
                generatedMapFile = printing;
                activeMapName = printing.getName();
            }
        }

        try {
            Optional<NbtTimingSummary.CycleTiming> recorded =
                NbtTimingSummary.read(mapFolder.toPath()).find(
                    coordinationJobId,
                    coordinationGeneration
                );
            if (recorded.isPresent()) {
                NbtTimingSummary.CycleTiming timing = recorded.get();
                cycleStartedAtMs = timing.startedAtMs();
                cycleCompletedAtMs = timing.completedAtMs();
                cycleTimingRecorded = true;
            }
        } catch (IOException failure) {
            error(
                "Could not inspect " + NbtTimingSummary.FILE_NAME
                    + " during recovery: " + failure.getMessage()
            );
            return false;
        }
        if (cycleStartedAtMs < 0) {
            cycleStartedAtMs = System.currentTimeMillis();
            warning(
                "Recovered job had no persisted start timestamp; timing resumes from now."
            );
        }

        mapCyclePhase = fileMasterRecoveredPhase;
        cycleRecovered = true;
        fileMasterRecoveryLoaded = true;
        info(
            "Recovered NBT §a" + mapFile.getName()
                + "§r at lifecycle phase "
                + fileMasterRecoveredPhase + "."
        );
        publishFileCoordinationState();
        return true;
    }

    private boolean recoveredFileIdentityMatchesLoadedState() {
        boolean matches = Objects.equals(
                fileMasterRecoveredServer,
                currentServerIdentity()
            )
            && Objects.equals(
                fileMasterRecoveredDimension,
                currentDimensionIdentity()
            )
            && Objects.equals(
                fileMasterRecoveredMapCorner,
                mapCornerIdentity()
            )
            && Objects.equals(
                fileMasterRecoveredConfigSha256,
                activeConfigSha256
            )
            && Objects.equals(
                fileMasterRecoveredPlanSha256,
                activeCompactPlanSha256
            )
            && Objects.equals(
                fileMasterRecoveredCircular,
                Boolean.toString(circularTraversal.get())
            );
        if (matches) return true;

        error(
            "Cannot resume persisted job " + loadedFileCycleKey
                + ": the server, dimension, map corner, saved config, "
                + "compact plan, or circular-traversal setting changed."
        );
        stopMovement();
        if (isActive()) toggle();
        return false;
    }

    private void resumeRecoveredFileMasterCycle() {
        if (!fileMasterRecoveryLoaded || fileMasterRecoveredPhase == null) {
            state = State.SelectingChests;
            return;
        }
        if (!recoveredFileIdentityMatchesLoadedState()) return;
        if (!localPlayerWithinMapZone() || !isMapAreaDataLoaded()) {
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "WAITING_IN_MAP_RECOVERY_ZONE"
            );
            if (!fileRecoveryIdentityWarning) {
                fileRecoveryIdentityWarning = true;
                warning(
                    "Recovered job is paused because the player is "
                        + "outside the configured map-area recovery margin "
                        + "or all map chunks are not loaded. Reposition/load "
                        + "the area, then reactivate the module if needed."
                );
            }
            return;
        }
        fileRecoveryIdentityWarning = false;
        if (SlaveSystem.isFileMaster()
            && requireFileSlavesReady.get()
            && !allFileSlavesReady()) {
            if (!waitingForFilePeersNotice) {
                waitingForFilePeersNotice = true;
                warning(
                    "Recovered job is waiting for all configured file slaves "
                        + "to acknowledge the same NBT generation and interval."
                );
            }
            return;
        }
        waitingForFilePeersNotice = false;

        if (SlaveSystem.isFileMaster()) {
            if (fileRecoveryToken < 0) {
                fileRecoveryToken = Math.max(
                    1L,
                    Math.max(
                        nextMiningTaskId,
                        System.currentTimeMillis()
                    )
                );
                fileRecoveryAcknowledgements.clear();
                fileRecoveryRetryTicks = 0;
            }
            if (!fileRecoveryAcknowledgements.containsAll(
                SlaveSystem.slaves
            )) {
                if (fileRecoveryRetryTicks <= 0) {
                    for (String slave : SlaveSystem.slaves) {
                        if (!fileRecoveryAcknowledgements.contains(
                            slave
                        )) {
                            SlaveSystem.queueDM(
                                slave,
                                "recovery:" + fileRecoveryToken + ":"
                                    + fileMasterRecoveredPhase.name()
                            );
                        }
                    }
                    fileRecoveryRetryTicks = 100;
                } else {
                    fileRecoveryRetryTicks--;
                }
                return;
            }
        }

        if (fileMasterRecoveredPhase
                == MapCyclePhase.MINING
            && !hasStableGroundedMiningRecoverySnapshot(
                "persisted teardown recovery"
            )) {
            SlaveSystem.setFileMetadata(
                FILE_META_STATUS,
                "WAITING_FOR_STABLE_MINING_RECOVERY_SNAPSHOT"
            );
            stopMovement();
            return;
        }

        MapCyclePhase recoveredPhase = fileMasterRecoveredPhase;
        MapHandoffStage recoveredHandoffStage =
            fileMasterRecoveredHandoffStage;
        Integer recoveredSourceMapId =
            fileMasterRecoveredSourceMapId;
        Integer recoveredLockedMapId =
            fileMasterRecoveredLockedMapId;
        fileRecoveryToken = -1L;
        fileRecoveryAcknowledgements.clear();
        fileMasterRecoveryLoaded = false;
        localCycleRecoveryCandidate = false;
        fileMasterRecoveredPhase = null;
        fileMasterRecoveredSourceName = null;
        fileMasterRecoveredSourceSha256 = null;
        fileMasterRecoveredPrintingName = null;
        fileMasterRecoveredArchivedSourceName = null;
        fileMasterRecoveredArchivedPrintingName = null;
        fileMasterRecoveredConfigSha256 = null;
        fileMasterRecoveredPlanSha256 = null;
        fileMasterRecoveredCircular = null;
        fileMasterRecoveredHandoffStage = null;
        fileMasterRecoveredSourceMapId = null;
        fileMasterRecoveredLockedMapId = null;
        fileMasterRecoveredServer = null;
        fileMasterRecoveredDimension = null;
        fileMasterRecoveredMapCorner = null;
        fileRecoveryIdentityWarning = false;
        mapHandoffStage = recoveredHandoffStage;
        handoffSourceMapId = recoveredSourceMapId;
        handoffLockedMapId = recoveredLockedMapId;
        printingComplete = false;

        info("Resuming recovered job from phase §a" + recoveredPhase);
        switch (recoveredPhase) {
            case BUILDING -> startBuilding();
            case MAP_HANDOFF -> {
                boolean needsReservedHandoffSlots =
                    recoveredHandoffStage
                        == MapHandoffStage.PREPARE_INVENTORY
                        || recoveredHandoffStage
                            == MapHandoffStage.NEED_SUPPLIES
                        || recoveredHandoffStage
                            == MapHandoffStage.SUPPLIES_CONFIRMED;
                if (needsReservedHandoffSlots
                    && availableSlots.isEmpty()
                    && !setupRecoveredMapHandoffSlots()) {
                    failMapHandoff(
                        "Restart recovery could not reserve the map/pane "
                            + "inventory slots."
                    );
                    return;
                }
                knownErrors.clear();
                resumeMapHandoffFromCheckpoint();
            }
            case MAP_DEPOSITED -> {
                if (availableSlots.isEmpty() && !setupSlots()) return;
                beginMapMining(true);
            }
            case MINING -> {
                if (availableSlots.isEmpty() && !setupSlots()) return;
                beginMapMining(true, true);
            }
            case VERIFIED_CLEAR -> {
                mapCyclePhase = MapCyclePhase.VERIFIED_CLEAR;
                if (!recordCurrentCycleTiming() || !archiveCurrentNbtFiles()) {
                    state = State.AwaitNbtArchive;
                    timeoutTicks = 100;
                } else {
                    finishMiningAfterArchive();
                }
            }
            case POST_MINING -> {
                if (!cycleTimingRecorded) {
                    // Timing is committed before entering POST_MINING. If a
                    // legacy/interrupted state lacks it, retry through the one
                    // legal archiving boundary without moving an archived file
                    // again.
                    mapCyclePhase = MapCyclePhase.VERIFIED_CLEAR;
                    state = State.AwaitNbtArchive;
                    timeoutTicks = 0;
                } else {
                    finishMiningAfterArchive();
                }
            }
            case IDLE -> {
                state = State.AwaitNBTFile;
            }
        }
        publishFileCoordinationState();
    }

    private boolean loadNBTFile() {
        try {
            activeSourceSha256 = null;
            activeCompactPlanSha256 = null;
            generatedMapFile = null;
            currentMapArchived = false;
            mapCyclePhase = MapCyclePhase.IDLE;
            activeMapName = null;
            northWalkwayRelativeY = null;
            info("Loading NBT: §a" + mapFile.getName());
            activeSourceSha256 = FileFingerprint.sha256(mapFile.toPath());
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            CompactCircularNbtGenerator.LoadedNbt loaded =
                CompactCircularNbtGenerator.loadOrGenerate(nbt);
            CompactCircularNbtGenerator.GeneratedNbt generated = loaded.generated();
            compactPlan = generated.plan();

            NbtList paletteList = generated.root().getList("palette")
                .orElseThrow(() -> new IllegalArgumentException("Generated compact NBT has no palette."));
            blockPaletteDict = Utils.getBlockPalette(paletteList);
            map = generateMapArray(compactPlan);
            generateRuntimeBuildPlan(compactPlan);
            activeCompactPlanSha256 = compactPlanFingerprint();
            loadOrCompileCircularTeardownReachTopology();

            boolean sourceAlreadyArchived = mapFile.getParentFile() != null
                && mapFile.getParentFile().getName().equals(
                    FinishedNbtArchiver.FINISHED_DIRECTORY_NAME
                );
            if (loaded.inputKind() == CompactCircularNbtGenerator.InputKind.SOURCE
                && saveGeneratedNbt.get()
                && !sourceAlreadyArchived) {
                File generatedFolder = new File(mapFolder, "_generated_compact");
                File generatedFile = new File(
                    generatedFolder,
                    compactOutputName(mapFile.getName())
                );
                CompactCircularNbtGenerator.writeValidated(generated, generatedFile.toPath());
                generatedMapFile = generatedFile;
                activeMapName = generatedFile.getName();
                info(
                    "Building generated compact NBT: §a" + activeMapName
                        + "§r (source: " + mapFile.getName() + ")"
                );
            } else if (loaded.inputKind() == CompactCircularNbtGenerator.InputKind.SOURCE) {
                activeMapName = compactOutputName(mapFile.getName()) + " [in memory]";
                info(
                    "Building generated compact plan in memory: §a" + activeMapName
                        + "§r (source: " + mapFile.getName() + ")"
                );
            } else {
                activeMapName = mapFile.getName();
                String legacy = loaded.inputKind()
                    == CompactCircularNbtGenerator.InputKind.LEGACY_COMPACT
                    ? " legacy"
                    : "";
                info("Building validated" + legacy + " compact NBT: §a" + activeMapName);
            }

            info("Requirements: ");
            for (Pair<Block, Integer> p : blockPaletteDict.values()) {
                if (p.getRight() == 0) continue;
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            info(
                "Compact plan validated: §a64 pairs, "
                    + compactPlan.connectorBlocks().size()
                    + " connector blocks, "
                    + compactPlan.sizeX() + "x" + compactPlan.sizeY() + "x" + compactPlan.sizeZ()
            );
            return true;
        } catch (Exception e) {
            activeSourceSha256 = null;
            activeCompactPlanSha256 = null;
            warning("Compact NBT rejected: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String compactPlanFingerprint() {
        ArrayList<Map.Entry<BlockPos, Block>> entries =
            new ArrayList<>(buildTargets.entrySet());
        entries.sort(
            Comparator
                .comparingInt(
                    (Map.Entry<BlockPos, Block> entry) ->
                        entry.getKey().getX()
                )
                .thenComparingInt(entry -> entry.getKey().getY())
                .thenComparingInt(entry -> entry.getKey().getZ())
                .thenComparing(
                    entry -> Registries.BLOCK.getId(
                        entry.getValue()
                    ).toString()
                )
        );
        StringBuilder canonical = new StringBuilder(
            entries.size() * 40
        );
        canonical.append("compact-plan-v1|")
            .append(compactPlan.sizeX()).append('|')
            .append(compactPlan.sizeY()).append('|')
            .append(compactPlan.sizeZ()).append('|');
        for (Map.Entry<BlockPos, Block> entry : entries) {
            BlockPos position = entry.getKey();
            canonical.append(position.getX()).append(',')
                .append(position.getY()).append(',')
                .append(position.getZ()).append('=')
                .append(Registries.BLOCK.getId(entry.getValue()))
                .append(';');
        }
        return FileFingerprint.sha256(
            canonical.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private double compiledTeardownInteractionReach() {
        return Math.max(
            0.1,
            effectiveBuildInteractionRange()
                - TEARDOWN_REACH_POSITION_TOLERANCE
        );
    }

    private boolean ensureCircularTeardownReachTopology() {
        if (compactPlan == null
            || activeCompactPlanSha256 == null
            || mapFolder == null) {
            return false;
        }
        double reach = compiledTeardownInteractionReach();
        if (circularTeardownReachTopology != null
            && Objects.equals(
                circularTeardownReachTopology.compactPlanSha256(),
                activeCompactPlanSha256
            )
            && Double.compare(
                circularTeardownReachTopology.standingEyeHeight(),
                TEARDOWN_STANDING_EYE_HEIGHT
            ) == 0
            && Double.compare(
                circularTeardownReachTopology.maximumReach(),
                reach
            ) == 0) {
            return true;
        }
        try {
            loadOrCompileCircularTeardownReachTopology();
            return true;
        } catch (IOException | RuntimeException failure) {
            warning(
                "Could not prepare the compiled circular teardown reach "
                    + "plan: " + failure.getMessage()
            );
            circularTeardownReachTopology = null;
            circularTeardownReachTopologyFile = null;
            circularTeardownTargetReferences.clear();
            return false;
        }
    }

    private void loadOrCompileCircularTeardownReachTopology()
        throws IOException {
        if (compactPlan == null
            || activeCompactPlanSha256 == null
            || mapFolder == null) {
            throw new IOException(
                "The compact NBT identity is incomplete."
            );
        }
        double reach = compiledTeardownInteractionReach();
        Path cacheFile = CircularTeardownReachTopologyStore.pathFor(
            mapFolder.toPath(),
            activeCompactPlanSha256,
            TEARDOWN_STANDING_EYE_HEIGHT,
            reach
        );
        CircularTeardownReachTopology.Snapshot topology = null;
        boolean loaded = false;
        if (Files.isRegularFile(cacheFile)) {
            try {
                topology = CircularTeardownReachTopologyStore.read(
                    cacheFile,
                    activeCompactPlanSha256,
                    TEARDOWN_STANDING_EYE_HEIGHT,
                    reach
                );
                loaded = true;
            } catch (IOException invalidCache) {
                warning(
                    "Rebuilding invalid circular teardown reach plan "
                        + cacheFile.getFileName() + ": "
                        + invalidCache.getMessage()
                );
            }
        }
        if (topology == null) {
            ArrayList<CircularTeardownReachTopology.Route> routes =
                new ArrayList<>(compactPlan.pairRoutes().size());
            for (CompactCircularNbtPlan.PairRoute route
                 : compactPlan.pairRoutes()) {
                List<BlockReachWindow.Cell> targets =
                    circularPairTargets(route).stream()
                        .map(position -> new BlockReachWindow.Cell(
                            position.getX(),
                            position.getY(),
                            position.getZ()
                        ))
                        .toList();
                routes.add(
                    new CircularTeardownReachTopology.Route(
                        route.pairIndex(),
                        targets
                    )
                );
            }
            topology = CircularTeardownReachTopology.compile(
                activeCompactPlanSha256,
                routes,
                TEARDOWN_STANDING_EYE_HEIGHT,
                reach
            );
            CircularTeardownReachTopologyStore.save(
                cacheFile,
                topology
            );
        }

        HashMap<BlockPos, CircularTeardownTargetReference> references =
            new HashMap<>();
        for (CompactCircularNbtPlan.PairRoute route
             : compactPlan.pairRoutes()) {
            ArrayList<BlockPos> targets = circularPairTargets(route);
            for (int targetIndex = 0;
                 targetIndex < targets.size();
                 targetIndex++) {
                CircularTeardownTargetReference previous = references.put(
                    targets.get(targetIndex),
                    new CircularTeardownTargetReference(
                        route.pairIndex(),
                        targetIndex
                    )
                );
                if (previous != null) {
                    throw new IOException(
                        "Circular U routes share teardown target "
                            + targets.get(targetIndex).toShortString() + "."
                    );
                }
            }
        }
        circularTeardownReachTopology = topology;
        circularTeardownReachTopologyFile = cacheFile;
        circularTeardownTargetReferences.clear();
        circularTeardownTargetReferences.putAll(references);
        debugLog(
            "TeardownTopology",
            (loaded ? "loaded" : "compiled and saved")
                + " plan=" + cacheFile.getFileName()
                + " reach=" + String.format(Locale.ROOT, "%.2f", reach)
                + " fullTraversalPairs="
                + topology.fullMapTraversalRoutes()
                + " fullAssignments="
                + topology.fullMapRouteAssignments()
        );
    }

    private static String compactOutputName(String sourceName) {
        String baseName = sourceName.toLowerCase(Locale.ROOT).endsWith(".nbt")
            ? sourceName.substring(0, sourceName.length() - 4)
            : sourceName;
        return baseName + "_compact_circular_u.nbt";
    }

    private Pair<Block, Integer>[][] generateMapArray(CompactCircularNbtPlan.Result plan) {
        Pair<Block, Integer>[][] smoothedHeightMap = new Pair[128][128];
        for (int x = 0; x < CompactCircularNbtPlan.MAP_WIDTH; x++) {
            for (int nbtZ = 1; nbtZ < CompactCircularNbtPlan.SOURCE_Z_SIZE; nbtZ++) {
                int state = plan.sourceTopState(x, nbtZ);
                Pair<Block, Integer> paletteEntry = blockPaletteDict.get(state);
                if (paletteEntry == null) {
                    throw new IllegalArgumentException("Visible block references missing palette state " + state + ".");
                }
                smoothedHeightMap[x][nbtZ - 1] = new Pair<>(
                    paletteEntry.getLeft(),
                    plan.targetSurfaceY(x, nbtZ)
                );
            }
        }
        return smoothedHeightMap;
    }

    private void generateRuntimeBuildPlan(CompactCircularNbtPlan.Result plan) {
        buildTargets.clear();
        orderedBuildTargets.clear();
        minimumRelativeSupportY = Integer.MAX_VALUE;
        maximumRelativeSupportY = Integer.MIN_VALUE;
        connectorTargets.clear();
        activeConnectorTargets.clear();
        circularPairModes.clear();

        for (Integer state : new ArrayList<>(blockPaletteDict.keySet())) {
            Pair<Block, Integer> entry = blockPaletteDict.get(state);
            blockPaletteDict.put(state, new Pair<>(entry.getLeft(), 0));
        }

        for (CompactCircularNbtPlan.PairRoute route : plan.pairRoutes()) {
            int firstX = route.outboundX();
            int secondX = route.returnX();

            for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
                addRuntimeSurfaceTarget(plan, firstX, nbtZ);
            }
            for (CompactCircularNbtPlan.Position connector : route.relativeInterior()) {
                BlockPos relative = new BlockPos(
                    connector.x(),
                    connector.y(),
                    connector.z() - 1
                );
                addRuntimeTarget(relative, plan.cobblestoneState(), true);
            }
            for (int nbtZ = CompactCircularNbtPlan.FAR_Z; nbtZ >= 1; nbtZ--) {
                addRuntimeSurfaceTarget(plan, secondX, nbtZ);
            }
        }

        int expectedTargets = 128 * 128 + plan.connectorBlocks().size();
        if (buildTargets.size() != expectedTargets || orderedBuildTargets.size() != expectedTargets) {
            throw new IllegalArgumentException(
                "Runtime compact plan has " + buildTargets.size()
                    + " targets; expected " + expectedTargets + "."
            );
        }
        for (BlockPos relative : buildTargets.keySet()) {
            minimumRelativeSupportY = Math.min(
                minimumRelativeSupportY,
                relative.getY()
            );
            maximumRelativeSupportY = Math.max(
                maximumRelativeSupportY,
                relative.getY()
            );
        }
    }

    private void addRuntimeSurfaceTarget(CompactCircularNbtPlan.Result plan, int x, int nbtZ) {
        BlockPos relative = new BlockPos(
            x,
            plan.targetSurfaceY(x, nbtZ),
            nbtZ - 1
        );
        addRuntimeTarget(relative, plan.sourceTopState(x, nbtZ), false);
    }

    private void addRuntimeTarget(BlockPos relative, int state, boolean connector) {
        Pair<Block, Integer> paletteEntry = blockPaletteDict.get(state);
        if (paletteEntry == null) {
            throw new IllegalArgumentException("Build target references missing palette state " + state + ".");
        }
        if (buildTargets.putIfAbsent(relative, paletteEntry.getLeft()) != null) {
            throw new IllegalArgumentException("Duplicate runtime build target at " + relative.toShortString() + ".");
        }
        orderedBuildTargets.add(relative);
        if (connector) connectorTargets.add(relative);
        blockPaletteDict.put(
            state,
            new Pair<>(paletteEntry.getLeft(), paletteEntry.getRight() + 1)
        );
    }

    // Rendering

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = new WTable();
        list.add(table);

        WButton startContinueButton =
            table.add(
                theme.button("Start / Continue Staircase")
            ).widget();
        startContinueButton.action = this::startOrContinueFromHud;
        table.row();

        File widgetMapFolder = mapFolder;
        if (widgetMapFolder == null) {
            widgetMapFolder = customFolderPath.get()
                ? new File(mapPrinterFolderPath.get())
                : new File(
                    Utils.getMinecraftDirectory(),
                    "nerv-printer"
                );
        }
        File configFolder = new File(widgetMapFolder, "_configs");
        if (!configFolder.exists()) return list;

        table.add(theme.label("Configurations: "));
        // ---- Save config button ----
        WButton saveButton = table.add(theme.button("Save Config")).widget();
        saveButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null,
                null
            );
            if (path != null) saveConfig(new File(path));
        };

        // ---- Load config button ----
        WButton loadButton = table.add(theme.button("Load Config")).widget();
        loadButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                "Load Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null,
                null,
                false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);

        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, true);
        slaveController.rebuild();

        SlaveSystem.tableController = slaveController;
        return list;
    }

    @Override
    public String getInfoString() {
        if (activeMapName != null) {
            return activeMapName;
        } else if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mapCorner == null || !render.get()) return;

        int renderedDepth = compactPlan == null ? 128 : compactPlan.sizeZ() - 1;
        event.renderer.box(
            mapCorner.getX(),
            mapCorner.getY(),
            mapCorner.getZ(),
            mapCorner.getX() + 128,
            mapCorner.getY(),
            mapCorner.getZ() + renderedDepth,
            color.get(),
            color.get(),
            ShapeMode.Lines,
            0
        );

        if (renderMap.get()
            && !(state.equals(State.Mining)
            || state.equals(State.AwaitBlockBreak)
            || state.equals(State.MiningUTraversal)
            || state.equals(State.AwaitUBlockBreak))) {
            for (BlockPos relative : orderedBuildTargets) {
                if (!isInWorkingInterval(relative)) continue;
                BlockPos renderPos = mapCorner.add(relative);
                if (!MapAreaCache.getCachedBlockState(renderPos).isAir()) continue;
                event.renderer.box(renderPos, color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }

        if (knownErrors != null) {
            for (BlockPos pos : knownErrors) {
                event.renderer.box(pos, color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }

        ArrayList<Pair<BlockPos, Vec3d>> renderedPairs = new ArrayList<>();
        for (ArrayList<Pair<BlockPos, Vec3d>> list : materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        for (Pair<BlockPos, Vec3d> pair : renderedPairs) {
            if (renderChestPositions.get())
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3d openPos = pair.getRight();
                event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            HashSet<Vec3d> renderedCheckpoints = new HashSet<>();
            for (Pair<Vec3d, Pair<String, BlockPos>> pair : checkpoints) {
                Vec3d cp = pair.getLeft();
                if (!renderedCheckpoints.add(cp)) continue;
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.getX() + indicatorSize.get(), cp.getY() + indicatorSize.get(), cp.getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (usedToolChest != null) {
                event.renderer.box(usedToolChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(usedToolChest.getRight().x - indicatorSize.get(), usedToolChest.getRight().y - indicatorSize.get(), usedToolChest.getRight().z - indicatorSize.get(), usedToolChest.getRight().getX() + indicatorSize.get(), usedToolChest.getRight().getY() + indicatorSize.get(), usedToolChest.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            for (Pair<BlockPos, Vec3d> pair : usedToolChests.values()) {
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(pair.getRight().x - indicatorSize.get(), pair.getRight().y - indicatorSize.get(), pair.getRight().z - indicatorSize.get(), pair.getRight().getX() + indicatorSize.get(), pair.getRight().getY() + indicatorSize.get(), pair.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            for (Pair<BlockPos, Vec3d> pair : Arrays.asList(anvil, enderChest, craftingTable)) {
                if (pair == null) continue;
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(pair.getRight().x - indicatorSize.get(), pair.getRight().y - indicatorSize.get(), pair.getRight().z - indicatorSize.get(), pair.getRight().getX() + indicatorSize.get(), pair.getRight().getY() + indicatorSize.get(), pair.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (bed != null) {
                event.renderer.box(bed.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(bed.getRight().x - indicatorSize.get(), bed.getRight().y - indicatorSize.get(), bed.getRight().z - indicatorSize.get(), bed.getRight().getX() + indicatorSize.get(), bed.getRight().getY() + indicatorSize.get(), bed.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x - indicatorSize.get(), cartographyTable.getRight().y - indicatorSize.get(), cartographyTable.getRight().z - indicatorSize.get(), cartographyTable.getRight().getX() + indicatorSize.get(), cartographyTable.getRight().getY() + indicatorSize.get(), cartographyTable.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStation != null) {
                event.renderer.box(dumpStation.getLeft().x - indicatorSize.get(), dumpStation.getLeft().y - indicatorSize.get(), dumpStation.getLeft().z - indicatorSize.get(), dumpStation.getLeft().getX() + indicatorSize.get(), dumpStation.getLeft().getY() + indicatorSize.get(), dumpStation.getLeft().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x - indicatorSize.get(), finishedMapChest.getRight().y - indicatorSize.get(), finishedMapChest.getRight().z - indicatorSize.get(), finishedMapChest.getRight().getX() + indicatorSize.get(), finishedMapChest.getRight().getY() + indicatorSize.get(), finishedMapChest.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    private enum CircularBuildPhase {
        NONE,
        OUTBOUND,
        CONNECTOR,
        RETURN,
        RECOVERY,
        RECOVERY_EXIT
    }

    private enum OrderedUTraversalOwner {
        PRINTING,
        TEARDOWN,
        TEARDOWN_SCAFFOLD
    }

    private enum TeardownScaffoldPhase {
        NONE,
        BUILDING_OUTBOUND,
        CLEANING_RETURN,
        EGRESS_TO_ENDPOINT
    }

    private record TeardownScaffoldRecoveryCandidate(
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> relativeTargets,
        List<TeardownScaffoldPlan.Cell> cells,
        Map<Integer, Block> ownedBlocks,
        TeardownScaffoldPlan.Plan plan
    ) {
        private TeardownScaffoldRecoveryCandidate {
            Objects.requireNonNull(route, "route");
            relativeTargets = List.copyOf(relativeTargets);
            cells = List.copyOf(cells);
            ownedBlocks = Map.copyOf(ownedBlocks);
            Objects.requireNonNull(plan, "plan");
        }
    }

    private record ActiveTeardownScaffoldRecovery(
        int pairIndex,
        TeardownScaffoldPlan.Endpoint endpoint,
        BlockPos entryApproach,
        BlockPos entryEndpoint,
        List<BlockPos> outwardSupports,
        BlockPos terminalCleanupTarget,
        List<BlockPos> scaffoldTargets,
        Map<BlockPos, Block> breakExpectations,
        Map<BlockPos, BlockState> plannedToolStates
    ) {
        private ActiveTeardownScaffoldRecovery {
            if (pairIndex < 0) {
                throw new IllegalArgumentException(
                    "Scaffold recovery pair cannot be negative."
                );
            }
            Objects.requireNonNull(endpoint, "endpoint");
            entryApproach = new BlockPos(
                Objects.requireNonNull(
                    entryApproach,
                    "entryApproach"
                )
            );
            entryEndpoint = new BlockPos(
                Objects.requireNonNull(
                    entryEndpoint,
                    "entryEndpoint"
                )
            );
            outwardSupports = List.copyOf(outwardSupports);
            terminalCleanupTarget = new BlockPos(
                Objects.requireNonNull(
                    terminalCleanupTarget,
                    "terminalCleanupTarget"
                )
            );
            scaffoldTargets = List.copyOf(scaffoldTargets);
            breakExpectations = Collections.unmodifiableMap(
                new LinkedHashMap<>(breakExpectations)
            );
            plannedToolStates = Collections.unmodifiableMap(
                new HashMap<>(plannedToolStates)
            );
            if (!breakExpectations.containsKey(
                terminalCleanupTarget
            )) {
                throw new IllegalArgumentException(
                    "Scaffold terminal cleanup target is not owned."
                );
            }
        }
    }

    private record ActiveOrderedUTraversal(
        OrderedUTraversalOwner owner,
        CompactCircularNbtPlan.PairRoute route,
        List<BlockPos> supports
    ) {
        private ActiveOrderedUTraversal {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(route, "route");
            supports = List.copyOf(
                Objects.requireNonNull(supports, "supports")
            );
            if (supports.isEmpty()) {
                throw new IllegalArgumentException(
                    "An active ordered U traversal requires supports."
                );
            }
        }
    }

    private enum InventoryLogisticsRecovery {
        NONE,
        MAP_HANDOFF,
        MAP_HANDOFF_PROBE,
        POST_MINING_USED_TOOLS
    }

    private enum InventoryRecoveryAuthority {
        NONE,
        PLAYER_SNAPSHOT,
        REGISTERED_HANDLER_PROBE
    }

    private record CircularInventoryPlanningResult(
        DependencyClosedOptionalInventoryPlan.Result<BlockPos, Item> plan,
        Map<Item, Integer> repairToolDemand,
        Map<Item, Integer> repairMinimumEfficiency,
        Map<Item, List<MiningToolRequirement>>
            repairToolCompatibilityRequirements,
        Set<Integer> repairToolKeepSlots,
        Set<Integer> buildToolKeepSlots,
        Set<Integer> buildUsedToolDepositSlots,
        Set<BlockPos> repairTargets,
        Set<BlockPos> clearOnlyRepairTargets,
        List<Item> buildHotbarStackItems
    ) {
        private CircularInventoryPlanningResult {
            LinkedHashMap<Item, List<MiningToolRequirement>>
                compatibilityCopy = new LinkedHashMap<>();
            repairToolCompatibilityRequirements.forEach(
                (item, requirements) ->
                    compatibilityCopy.put(
                        item,
                        List.copyOf(requirements)
                    )
            );
            repairToolCompatibilityRequirements =
                Collections.unmodifiableMap(compatibilityCopy);
            buildHotbarStackItems =
                List.copyOf(buildHotbarStackItems);
        }
    }

    private record ServerBlockObservation(
        long sequence,
        Block block
    ) {
    }

    private enum MiningHotbarSwapContext {
        NONE,
        BUILD_REPAIR,
        TEARDOWN;

        private long clockTick(
            long clientActionTick,
            long printActionTick
        ) {
            return this == BUILD_REPAIR
                ? printActionTick
                : clientActionTick;
        }
    }

    private enum SpeedMineOwner {
        NONE,
        BUILD_REPAIR,
        TEARDOWN
    }

    private enum TeardownBreakStatus {
        CLEARED,
        DEFERRED,
        WAITING,
        FAILED
    }

    private enum HotbarPreparation {
        READY,
        WAITING,
        RESTOCK_REQUIRED,
        FAILED
    }

    private enum RestockConfirmationPhase {
        NONE,
        REOPEN_PENDING,
        AWAITING_REOPEN_SNAPSHOT,
        AWAITING_HANDLER_REOPEN_SNAPSHOT,
        AWAITING_SOURCE_REFILL,
        AWAITING_SOURCE_REFILL_SNAPSHOT
    }

    private record MiningToolIdentity(
        Item item,
        StructuralItemStackKey components
    ) {
        private MiningToolIdentity {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(components, "components");
        }
    }

    private record MiningToolRequirement(
        ItemStack registeredTemplate,
        BlockState targetState,
        boolean itemOnly
    ) {
        private MiningToolRequirement(
            ItemStack registeredTemplate,
            BlockState targetState
        ) {
            this(registeredTemplate, targetState, false);
        }

        private static MiningToolRequirement itemOnly(
            ItemStack registeredTemplate
        ) {
            return new MiningToolRequirement(
                registeredTemplate,
                Blocks.AIR.getDefaultState(),
                true
            );
        }

        private MiningToolRequirement {
            Objects.requireNonNull(
                registeredTemplate,
                "registeredTemplate"
            );
            Objects.requireNonNull(targetState, "targetState");
        }
    }

    private record InventoryStackIdentity(
        Item item,
        StructuralItemStackKey nonDamageComponents,
        int count,
        int damage
    ) {
        private InventoryStackIdentity {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(
                nonDamageComponents,
                "nonDamageComponents"
            );
            if (count < 0 || damage < 0) {
                throw new IllegalArgumentException(
                    "Stack identity values cannot be negative."
                );
            }
        }

        private boolean matchesMonotonicDamage(
            InventoryStackIdentity before
        ) {
            return item == before.item
                && count == before.count
                && nonDamageComponents.equals(
                    before.nonDamageComponents
                )
                && damage >= before.damage;
        }
    }

    private record PendingInventoryMetadataSwap(
        int sourceSlot,
        int targetHotbarSlot,
        InventoryStackIdentity beforeSource,
        InventoryStackIdentity beforeTarget,
        InventorySlotMetadataSwap.Captured<RepairToolShadow> metadata,
        long submittedAfterSequence,
        String owner
    ) {
        private PendingInventoryMetadataSwap {
            if (sourceSlot < 9
                || sourceSlot >= 36
                || targetHotbarSlot < 0
                || targetHotbarSlot >= 9
                || submittedAfterSequence < 0) {
                throw new IllegalArgumentException(
                    "Invalid pending inventory metadata swap."
                );
            }
            Objects.requireNonNull(beforeSource, "beforeSource");
            Objects.requireNonNull(beforeTarget, "beforeTarget");
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(owner, "owner");
        }

        private PendingInventoryMetadataSwap withMetadata(
            InventorySlotMetadataSwap.Captured<RepairToolShadow>
                refreshedMetadata
        ) {
            return new PendingInventoryMetadataSwap(
                sourceSlot,
                targetHotbarSlot,
                beforeSource,
                beforeTarget,
                refreshedMetadata,
                submittedAfterSequence,
                owner
            );
        }
    }

    private record RepairToolSwapStaging(
        int desiredSourceSlot,
        int stagingSourceSlot,
        int targetHotbarSlot,
        MiningToolIdentity desiredIdentity,
        boolean finalSubmitted
    ) {
        private RepairToolSwapStaging {
            if (desiredSourceSlot < 9
                || desiredSourceSlot >= 36
                || stagingSourceSlot < 9
                || stagingSourceSlot >= 36
                || stagingSourceSlot == desiredSourceSlot
                || targetHotbarSlot < 0
                || targetHotbarSlot >= 9) {
                throw new IllegalArgumentException(
                    "Invalid repair-tool staging slots."
                );
            }
            Objects.requireNonNull(desiredIdentity, "desiredIdentity");
        }

        private RepairToolSwapStaging markFinalSubmitted() {
            return new RepairToolSwapStaging(
                desiredSourceSlot,
                stagingSourceSlot,
                targetHotbarSlot,
                desiredIdentity,
                true
            );
        }
    }

    private record RepairToolShadow(
        int observedRemainingDurability,
        int unacknowledgedUses,
        long inventoryRevision
    ) {
        private RepairToolShadow {
            if (observedRemainingDurability < 0
                || unacknowledgedUses <= 0) {
                throw new IllegalArgumentException(
                    "Repair-tool shadow values must be positive."
                );
            }
        }
    }

    private record PendingRestockTransfer(
        Item item,
        int syncId,
        int sourceSlot,
        int beforeSourceCount,
        int beforePlayerCount,
        long inventoryRevision,
        long submittedAtTick,
        int consecutiveNoProgressAttempts
    ) {
        private PendingRestockTransfer {
            Objects.requireNonNull(item, "item");
            if (syncId <= 0
                || sourceSlot < 0
                || beforeSourceCount <= 0
                || beforePlayerCount < 0
                || inventoryRevision < 0
                || submittedAtTick < -1
                || consecutiveNoProgressAttempts <= 0) {
                throw new IllegalArgumentException(
                    "Invalid pending restock transfer."
                );
            }
        }
    }

    private record PendingDumpTransfer(
        int playerSlot,
        InventoryStackIdentity before,
        long submittedAfterSequence,
        long submittedAtTick,
        int attempts
    ) {
        private PendingDumpTransfer {
            if (playerSlot < 0
                || playerSlot >= 36
                || submittedAfterSequence < 0
                || submittedAtTick < -1
                || attempts <= 0) {
                throw new IllegalArgumentException(
                    "Invalid pending dump transaction."
                );
            }
            Objects.requireNonNull(before, "before");
        }
    }

    private record PendingUsedToolDeposit(
        int syncId,
        int handlerSlot,
        InventoryStackIdentity before,
        long submittedAfterSequence,
        long submittedAtTick,
        int attempts
    ) {
        private PendingUsedToolDeposit {
            if (syncId <= 0
                || handlerSlot < 0
                || submittedAfterSequence < 0
                || submittedAtTick < -1
                || attempts <= 0) {
                throw new IllegalArgumentException(
                    "Invalid pending used-tool deposit."
                );
            }
            Objects.requireNonNull(before, "before");
        }
    }

    private record SpeedMineSettingsSnapshot(
        boolean wasActive,
        SpeedMine.Mode mode,
        SpeedMine.ListMode blocksFilter,
        List<Block> blocks,
        boolean instamine,
        boolean grimBypass
    ) {
        private SpeedMineSettingsSnapshot {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(blocksFilter, "blocksFilter");
            blocks = List.copyOf(blocks);
        }
    }

    private enum State {
        SelectingMapArea,
        SelectingTable,
        SelectingUsedPickaxeChest,
        SelectingDumpStation,
        SelectingFinishedMapChest,
        SelectingBed,
        SelectingChests,
        AwaitRegisterResponse,
        AwaitUsedToolRegistrationResponse,
        AwaitRestockResponse,
        AwaitMapChestResponse,
        AwaitMapSuppliesConfirmation,
        AwaitMapHotbarSwapConfirmation,
        AwaitFilledMapConfirmation,
        AwaitFinishedMapChestResponse,
        AwaitFinishedMapDepositConfirmation,
        AwaitMapHandoffRecoveryProbeResponse,
        AwaitUsedToolChestResponse,
        AwaitCartographyResponse,
        AwaitCartographyOutputConfirmation,
        AwaitHandoffMapState,
        AwaitNBTFile,
        AwaitFileMaster,
        AwaitFileSlaves,
        AwaitFileRecovery,
        AwaitBlockBreak,
        AwaitUBlockBreak,
        AwaitMasterAllBuilt,
        AwaitMasterAllBuiltSkip,
        AwaitNbtArchive,
        AwaitMasterAllMined,
        AwaitSlaveFinalization,
        AwaitSlaveRemoval,
        AwaitMasterNextMap,
        AwaitCompactWorkspace,
        AwaitSlaveContinue,
        AwaitSlaveMineLine,
        AwaitManualRepair,
        Walking,
        MiningUTraversal,
        Mining,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum ErrorAction {
        Ignore,
        ManualRepair
    }
}
