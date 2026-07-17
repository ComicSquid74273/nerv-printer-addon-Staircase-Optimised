package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
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
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
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
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StaircasedPrinter extends Module implements MapPrinter {
    private static final Set<String> LOGISTICS_TRAVEL_ACTIONS = Set.of(
        "dump",
        "refill",
        "sleep",
        "mapMaterialChest",
        "cartographyTable",
        "finishedMapChest",
        "usedToolChest",
        "walkRestock"
    );
    private static final int MAX_LOGISTICS_DETOUR_ATTEMPTS = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range")
        .description("The maximum range you can place blocks around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many milliseconds to wait after placing.")
        .defaultValue(50)
        .min(1)
        .sliderRange(10, 300)
        .build()
    );

    private final Setting<Double> maxMiningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-mining-range")
        .description("The maximum range you can place blocks around yourself.")
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
        .description("Rotate when placing a block.")
        .defaultValue(true)
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

    private final Setting<Integer> preSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-swap-delay")
        .description("How many ticks to wait before swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
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

    private final Setting<Double> durabilityBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("durability-buffer")
        .description("The additional required durability for restocked mining tools on top of the predicted one (in %).")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
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
        .defaultValue(false)
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
        .description("Prints additional information.")
        .defaultValue(false)
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
    int toBeSwappedSlot;
    long lastTickTime;
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
    BlockPos miningPos;
    Item lastSwappedMaterial;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;      //Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    HashMap<Item, Pair<BlockPos, Vec3d>> usedToolChests;          //Maps a used tool type to its single chest
    HashMap<BlockPos, Set<Item>> usedToolDepositPlan;
    Set<Item> currentUsedToolDepositItems;
    Set<ItemStack> toolSet;                                       //Set of all registered tool item stacks
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Item, Integer, Integer>> restockList;//Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<BlockPos> knownErrors;
    boolean tempChestIsSingle;
    Pair<Block, Integer>[][] map;
    CompactCircularNbtPlan.Result compactPlan;
    Integer northWalkwayRelativeY;
    LinkedHashMap<BlockPos, Block> buildTargets;
    ArrayList<BlockPos> orderedBuildTargets;
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
    LogisticsTerminal activeLogisticsTerminal;
    double logisticsDetourStandingY;
    int logisticsDetourAttempts;
    boolean logisticsSidestepUsed;
    File mapFolder;
    File mapFile;
    File generatedMapFile;
    boolean currentMapArchived;
    String activeMapName;

    public StaircasedPrinter() {
        super(Addon.CATEGORY, "fullblock-printer", "Automatically builds fullblock maps with optional staircasing from nbt files.");
    }

    @Override
    public void onActivate() {
        lastTickTime = System.currentTimeMillis();
        cancelLogisticsDetour();
        if (reconnectRecoveryPending && checkpoints != null) {
            reconnectRecoveryPending = false;
            if (isCircularMiningOrRestockState()) {
                miningRecoveryPending = true;
            } else if (buildingActive && activeCircularBuildPair >= 0) {
                buildRecoveryPending = true;
            }
            resyncMiningProtocol();
            return;
        }
        if (!activationReset.get() && checkpoints != null) {
            if (isCircularMiningOrRestockState()) {
                miningRecoveryPending = true;
            } else if (buildingActive && activeCircularBuildPair >= 0) {
                buildRecoveryPending = true;
            }
            resyncMiningProtocol();
            return;
        }
        materialDict = new HashMap<>();
        usedToolChests = new HashMap<>();
        usedToolDepositPlan = new HashMap<>();
        currentUsedToolDepositItems = new HashSet<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        toolSet = new HashSet<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        knownErrors = new ArrayList<>();
        buildTargets = new LinkedHashMap<>();
        orderedBuildTargets = new ArrayList<>();
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
        northWalkwayRelativeY = null;
        generatedMapFile = null;
        currentMapArchived = false;
        activeMapName = null;
        usedToolChest = null;
        mapCorner = null;
        lastInteractedChest = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        bed = null;
        anvil = null;
        enderChest = null;
        craftingTable = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        jumpTimeout = 0;
        interactTimeout = 0;
        miningFinalizationRetryTicks = 0;
        nextMapSyncTicks = 0;
        toBeSwappedSlot = -1;
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

        if (displayMaxRequirements.get()) {
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

        if (!prepareNextMapFile()) return;

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

    @Override
    public void onDeactivate() {
        cancelLogisticsDetour();
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onGameLeft(GameLeftEvent event) {
        cancelLogisticsDetour();
        reconnectRecoveryPending =
            buildingActive
                || miningAssignmentsActive
                || state == State.AwaitNbtArchive
                || state == State.AwaitNbtArchiveSkip
                || SlaveSystem.hasRelationship();
        if (isCircularMiningOrRestockState()) {
            miningRecoveryPending = true;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
        } else if (buildingActive && activeCircularBuildPair >= 0 && state == State.Walking) {
            buildRecoveryPending = true;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
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
                    //Check if requirements to start building are met
                    if (materialDict.isEmpty()) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (toolSet.isEmpty()) {
                        warning("No Tool Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.isEmpty()) {
                        warning("No Map Chests selected!");
                        return;
                    }

                    startBuilding();
                }
                break;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == null) return;

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            cancelLogisticsDetour();
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) {
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
            }
            if (isCircularMiningOrRestockState()) {
                miningRecoveryPending = true;
            } else if (buildingActive && activeCircularBuildPair >= 0 && state == State.Walking) {
                buildRecoveryPending = true;
            }
        }

        if (!(event.packet instanceof InventoryS2CPacket packet)) return;

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
                    foundItemStack = stack;
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
                toolSet.add(foundItemStack);
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
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitUsedToolChestResponse);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
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
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                boolean foundMaterials = false;
                List<Integer> slots = IntStream.rangeClosed(0, packet.contents().size() - 37)
                    .boxed()
                    .collect(Collectors.toList());
                Collections.shuffle(slots);
                for (int slot : slots) {
                    ItemStack stack = packet.contents().get(slot);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    Item requestedItem = restockList.get(0).getLeft();
                    if (!stack.isEmpty()
                        && stack.getItem() == requestedItem) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(slot);
                        Triple<Item, Integer, Integer> oldTriple = restockList.remove(0);
                        int remainingAmount = Math.max(
                            0,
                            oldTriple.getRight() - stack.getCount()
                        );
                        restockList.add(0, Triple.of(
                            oldTriple.getLeft(),
                            InventoryCapacity.slotsForAmount(
                                remainingAmount,
                                Utils.maximumStackSize(oldTriple.getLeft())
                            ),
                            remainingAmount
                        ));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                //Search for map and glass pane
                for (int slot = 0; slot < packet.contents().size() - 36; slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if (mapSlot == -1 || paneSlot == -1) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet);
                Utils.getOneItem(paneSlot, true, availableSlots, availableHotBarSlots, packet);
                mc.player.getInventory().setSelectedSlot(availableHotBarSlots.get(0));

                BlockPos centerBlockPos = mapCorner.add(map.length / 2 - 1, map[map.length / 2 - 1][map[0].length / 2 - 1].getRight(), map[0].length / 2 - 1);
                Vec3d center = centerBlockPos.toCenterPos().add(0, 0.5, 0);
                Vec3d centerEdge = walkingPosition(
                    northWalkwaySupport(map.length / 2 - 1)
                );
                checkpoints.add(new Pair(centerEdge, new Pair("walkRestock", null)));
                checkpoints.add(new Pair(center, new Pair("fillMap", null)));
                checkpoints.add(new Pair(centerEdge, new Pair("walkRestock", null)));
                checkpoints.add(new Pair(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
                state = State.Walking;
                break;
            case AwaitCartographyResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                boolean searchingMap = true;
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.contents().get(slot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.contents().get(slot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                mc.interactionManager.clickSlot(packet.syncId(), 2, 0, SlotActionType.QUICK_MOVE, mc.player);
                checkpoints.add(new Pair(finishedMapChest.getRight(), new Pair("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.contents().size() - 36; slot < packet.contents().size(); slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                startMining();
                break;
            case AwaitUsedToolChestResponse:
                interactTimeout = 0;
                for (int slot = packet.contents().size() - 36; slot < packet.contents().size(); slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (ToolUtils.isTool(stack) && currentUsedToolDepositItems.contains(stack.getItem())) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                }
                usedToolDepositPlan.remove(lastInteractedChest);
                currentUsedToolDepositItems.clear();
                if (checkpoints.isEmpty()) {
                    state = State.AwaitNBTFile;
                    completeSlavePostMiningCleanup();
                } else {
                    timeoutTicks = postRestockDelay.get();
                    state = State.Walking;
                }
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;
        if (activeLogisticsTerminal != null
            && (mc.player == null || mc.player.isDead())) {
            cancelLogisticsDetour();
        }
        if (isCircularMiningOrRestockState()
            && (mc.player == null || mc.player.isDead())) {
            miningRecoveryPending = true;
            miningRecoveryNeedsTools = true;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }
        if (buildingActive
            && activeCircularBuildPair >= 0
            && state == State.Walking
            && (mc.player == null || mc.player.isDead())) {
            buildRecoveryPending = true;
            buildRecoveryNeedsInventory = true;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }
        if (mc.player == null || mc.player.isDead()) {
            stopMovement();
            return;
        }
        if (miningRecoveryPending) {
            if (mc.player == null || mc.player.isDead()) return;
            miningRecoveryPending = false;
            miningRecoveryNeedsTools = false;
            restartCurrentMiningAssignment();
        }
        if (buildRecoveryPending) {
            if (mc.player == null || mc.player.isDead() || !mc.player.isOnGround()) {
                stopMovement();
                return;
            }
            buildRecoveryPending = false;
            boolean inventoryWasLost = buildRecoveryNeedsInventory;
            buildRecoveryNeedsInventory = false;
            if (!recoverCircularBuildTraversal(inventoryWasLost)) return;
        }

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / (long) placeDelay.get());
        lastTickTime += allowedPlacements * placeDelay.get();

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: §a" + state);
        }

        if (state == State.AwaitNbtArchive) {
            if (timeoutTicks > 0) {
                if (mc.player.isOnGround()) timeoutTicks--;
                stopMovement();
                return;
            }
            if (archiveCurrentNbtFiles()) {
                endBuilding();
            } else {
                timeoutTicks = 100;
            }
            return;
        }

        if (state == State.AwaitNbtArchiveSkip) {
            if (timeoutTicks > 0) {
                if (mc.player.isOnGround()) timeoutTicks--;
                stopMovement();
                return;
            }
            if (archiveCurrentNbtFiles()) {
                state = State.AwaitMasterAllBuiltSkip;
            } else {
                timeoutTicks = 100;
            }
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
                info("Interaction timed out. Interacting again...");
                if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getLeft());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        boolean continuingCircularBuildJump =
            jumpTimeout > 0
                && state == State.Walking
                && activeCircularBuildPair >= 0
                && (circularBuildPhase == CircularBuildPhase.OUTBOUND
                    || circularBuildPhase == CircularBuildPhase.CONNECTOR
                    || circularBuildPhase == CircularBuildPhase.RETURN);
        if (jumpTimeout > 0) {
            jumpTimeout--;
            if (!continuingCircularBuildJump) return;
        }

        if (timeoutTicks > 0) {
            if (mc.player.isOnGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }

        // Swap into Hotbar
        if (toBeSwappedSlot != -1) {
            swapIntoHotbar(toBeSwappedSlot);
            toBeSwappedSlot = -1;
            if (postSwapDelay.get() != 0) {
                timeoutTicks = postSwapDelay.get();
                return;
            }
        }

        // Restocking
        if (restockBacklogSlots.size() > 0) {
            int slot = restockBacklogSlots.remove(0);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 1, SlotActionType.QUICK_MOVE, mc.player);
            if (restockBacklogSlots.isEmpty()) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && miningPos != null) {
            // Break block if miningPos is not null. Stop walking if further away than maxMiningRange
            if (MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                miningPos = null;
                state = State.Mining;
            } else {
                BlockState targetState = MapAreaCache.getCachedBlockState(miningPos);
                if (!equipMiningTool(targetState)) {
                    stopMovement();
                    return;
                }
                mc.player.setPitch((float) Rotations.getPitch(miningPos));
                BlockUtils.breakBlock(miningPos, true);

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

        if (state.equals(State.AwaitUBlockBreak) && miningPos != null) {
            BlockState targetState = MapAreaCache.getCachedBlockState(miningPos);
            if (targetState.isAir()) {
                miningPos = null;
                state = State.MiningUTraversal;
            } else {
                Block expected = buildTargets.get(miningPos.subtract(mapCorner));
                if (expected == null || targetState.getBlock() != expected) {
                    error("U mining target changed unexpectedly at " + miningPos.toShortString() + ".");
                    toggle();
                    return;
                }
                if (!equipMiningTool(targetState)) return;
                mc.player.setPitch((float) Rotations.getPitch(miningPos));
                BlockUtils.breakBlock(miningPos, true);
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
            if (activeMiningLine >= 0 && isLineMined(activeMiningLine)) {
                miningPos = null;
                timeoutTicks = mineLineEndTimeout.get();
                Utils.setBackwardPressed(false);
                completeCurrentMiningAssignment();
                return;
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                state = State.Walking;
                if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                    state = State.AwaitSlaveMineLine;
                    SlaveSystem.queueMasterDM("finished");
                    return;
                } else {
                    HashMap<Item, Integer> requiredItems = getRequiredItems();
                    Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(invInformation.getRight());
                }
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getStack(dumpSlot).getName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
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
            if (!prepareNextMapFile()) {
                if (!SlaveSystem.isSlave()) {
                    boolean firstCompletionNotice = !printingComplete;
                    printingComplete = true;
                    if (firstCompletionNotice) {
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
                mc.player.closeHandledScreen();
            }
            closeNextInvPacket = false;
        }

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
            boolean walkwayCheckpoint =
                circularBuildAction.equals("preparePair")
                    || circularBuildAction.equals("finishPair")
                    || circularBuildAction.equals("uBuildRecoveryExit");
            boolean continuousLegCheckpoint =
                circularBuildAction.equals("uBuildOutboundEnd");
            BlockPos requiredSupport =
                supportBelowCheckpoint(checkpoints.getFirst().getLeft());
            if (walkwayCheckpoint) {
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

        if (state.equals(State.Walking) || state.equals(State.MiningUTraversal)) {
            Utils.setForwardPressed(true);
            Utils.setBackwardPressed(false);
        } else if (state.equals(State.Mining)) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(true);
        } else {
            return;
        }
        if (!continuingCircularBuildJump) Utils.setJumpPressed(false);
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
        Vec3d goal = followingCircularConnector
            ? currentCircularConnectorGoal()
            : checkpoints.get(0).getLeft();
        Vec3d circularHandoffGoal = circularBuildHandoffGoal(goal);
        if (followingCircularConnector) {
            steerTowardGoal(goal);
        } else if (circularHandoffGoal != null) {
            steerTowardGoal(circularHandoffGoal);
        }

        // AutoJump logic
        boolean followingLogisticsDetour = !checkpoints.isEmpty()
            && isLogisticsDetourCheckpoint(checkpoints.getFirst());
        if (!followingLogisticsDetour
            && !continuingCircularBuildJump
            && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed())
            && jumpTimeout <= 0) {
            Direction direction = Direction.fromHorizontalDegrees(mc.player.getYaw());
            if (mc.options.backKey.isPressed()) direction = direction.getOpposite();
            BlockPos target = mc.player.getBlockPos().offset(direction);
            if (mc.player.isOnGround() && !MapAreaCache.getCachedBlockState(target).isAir()
                && MapAreaCache.getCachedBlockState(target.up(1)).isAir() && MapAreaCache.getCachedBlockState(target.up(2)).isAir()) {
                jumpTimeout = jumpCoolDown.get();
                Utils.setJumpPressed(true);
            }
        }
        if (state == State.MiningUTraversal
            && isUTraversalCheckpoint(checkpoints.get(0))
            && !isSafeUCheckpointSupport(goal)) {
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
                || isLogisticsDetourCheckpoint(checkpoints.get(0));
        double checkpointDistance = usesThreeDimensionalCheckpoint
            ? PlayerUtils.distanceTo(goal)
            : PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0));
        if (!followingCircularConnector && handleLogisticsNavigation(goal)) return;
        boolean circularRouteCheckpoint =
            followingCircularConnector
                || (state == State.MiningUTraversal && isUTraversalCheckpoint(checkpoints.get(0)))
                || preciseCircularBuildCheckpoint
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
        if (!followingCircularConnector
            && checkpointDistance < requiredCheckpointBuffer) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null)
                info("Reached: §a" + checkpointAction.getLeft());
            if (snapToCheckpoints.get()) mc.player.setPosition(goal.x, mc.player.getY(), goal.z);
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
                case "lineEnd":
                    activeCircularBuildPair = -1;
                    activeCircularConnectorIndex = -1;
                    circularBuildRecoveryDirection = 0;
                    circularBuildPhase = CircularBuildPhase.NONE;
                    calculateBuildingPath(false);
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
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()));
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "preparePair": {
                    int pairIndex = checkpointAction.getRight().getX();
                    CompactCircularNbtPlan.PairRoute pairRoute =
                        compactPlan.pairRoutes().get(pairIndex);
                    BlockPos pairEntry =
                        northWalkwaySupport(pairRoute.outboundX());
                    if (!isPlayerStandingOnSupport(pairEntry)
                        || !isSafeNorthWalkway(pairRoute.outboundX())) {
                        error(
                            "Circular pair " + pairIndex
                                + " was not entered from its validated north endpoint."
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
                        return;
                    }
                    if (!validateCircularPairWorkspace(pairRoute, true)) {
                        toggle();
                        return;
                    }
                    if (!pairFitsUsableInventory(pairRoute)) {
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
                        info(
                            "Pair " + pairIndex
                                + " no longer fits the usable inventory; using two independent columns."
                        );
                        return;
                    }
                    if (!hasSufficientPairMaterials(pairIndex)) {
                        BlockPos pairStart = northWalkwaySupport(pairRoute.outboundX());
                        checkpoints.add(0, new Pair<>(
                            walkingPosition(pairStart),
                            new Pair<>("preparePair", checkpointAction.getRight())
                        ));
                        checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
                    } else {
                        activeCircularBuildPair = pairIndex;
                        activeCircularConnectorIndex = 0;
                        circularBuildPhase = CircularBuildPhase.OUTBOUND;
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
                        BlockPos outboundEnd = mapCorner.add(
                            surfaceRuntimePosition(
                                route.outboundX(),
                                CompactCircularNbtPlan.FAR_Z
                            )
                        );
                        checkpoints.add(0, new Pair<>(
                            walkingPosition(outboundEnd),
                            new Pair<>("uBuildOutboundEnd", null)
                        ));
                        checkpoints.add(0, new Pair<>(
                            walkingPosition(
                                northWalkwaySupport(route.outboundX())
                            ),
                            new Pair<>("", null)
                        ));
                        info(
                            "Circular pair " + activeCircularBuildPair
                                + " has an unconfirmed outbound placement; "
                                + "retrying it with the normal return pass."
                        );
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
                        BlockPos returnWalkway =
                            northWalkwaySupport(exitingRoute.returnX());
                        if (!isPlayerStandingOnSupport(returnWalkway)) {
                            error(
                                "Circular pair " + finishedPairIndex
                                    + " did not reach its exact north return endpoint."
                            );
                            toggle();
                            return;
                        }
                    }
                    activeCircularBuildPair = -1;
                    activeCircularConnectorIndex = -1;
                    circularBuildRecoveryDirection = 0;
                    circularBuildPhase = CircularBuildPhase.NONE;
                    if (finishedPairIndex >= 0) {
                        CompactCircularNbtPlan.PairRoute finishedRoute =
                            compactPlan.pairRoutes().get(finishedPairIndex);
                        boolean complete = circularPairTargets(finishedRoute).stream()
                            .allMatch(relative -> {
                                BlockState state = MapAreaCache.getCachedBlockState(
                                    mapCorner.add(relative)
                                );
                                return !state.isAir()
                                    && state.getBlock() == buildTargets.get(relative);
                            });
                        if (!complete) {
                            stopMovement();
                            calculateBuildingPath(false);
                            return;
                        }
                    }
                    if (pendingInterval != null) {
                        if (!replanCircularBuildFromSafeArea(false)) return;
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
                    resumeAfterRestockState = state;
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
                case "removeUBlock":
                    miningPos = checkpointAction.getRight();
                    if (miningPos != null && !MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                        Block expected = buildTargets.get(miningPos.subtract(mapCorner));
                        BlockState targetState = MapAreaCache.getCachedBlockState(miningPos);
                        if (expected == null || targetState.getBlock() != expected) {
                            error("U mining target changed unexpectedly at " + miningPos.toShortString() + ".");
                            toggle();
                            return;
                        }
                        state = State.AwaitUBlockBreak;
                        if (!equipMiningTool(targetState)) return;
                        Utils.setForwardPressed(false);
                        Utils.setBackwardPressed(false);
                        BlockUtils.breakBlock(miningPos, true);
                        return;
                    }
                    miningPos = null;
                    stopMovement();
                    return;
                case "verifyUTools":
                    int miningPairIndex = checkpointAction.getRight().getX();
                    CompactCircularNbtPlan.PairRoute miningRoute =
                        compactPlan.pairRoutes().get(miningPairIndex);
                    HashMap<Item, Integer> stillMissingTools =
                        missingCircularMiningTools(miningRoute);
                    if (stillMissingTools == null || !stillMissingTools.isEmpty()) {
                        error(
                            "Required worst-case tool durability was not available at the U entry. "
                                + "Stopping before entering pair " + miningPairIndex + "."
                        );
                        toggle();
                        return;
                    }
                    strictMiningRestockActive = false;
                    stopMovement();
                    return;
                case "verifyIndependentTools":
                    int independentLine = checkpointAction.getRight().getX();
                    HashMap<Item, Integer> missingIndependentTools =
                        missingMiningTools(independentMiningTargets(independentLine));
                    if (missingIndependentTools == null
                        || !missingIndependentTools.isEmpty()) {
                        error(
                            "Required worst-case tool durability was not available at "
                                + "the entry to independent line " + independentLine + "."
                        );
                        toggle();
                        return;
                    }
                    strictMiningRestockActive = false;
                    stopMovement();
                    return;
                case "uMiningTaskEnd":
                    timeoutTicks = mineLineEndTimeout.get();
                    completeCurrentMiningAssignment();
                    return;
                case "usedToolChest":
                    BlockPos usedToolChestPos = checkpointAction.getRight();
                    if (usedToolChestPos == null) usedToolChestPos = usedToolChest.getLeft();
                    currentUsedToolDepositItems = usedToolDepositPlan.getOrDefault(
                        usedToolChestPos,
                        getInventoryToolItems()
                    );
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
            goal = followingCircularConnector
                ? currentCircularConnectorGoal()
                : checkpoints.get(0).getLeft();
        }

        //Set yaw rotation
        steerTowardGoal(goal);

        // Set print mode
        String nextAction = checkpoints.get(0).getRight().getLeft();
        if (state.equals(State.MiningUTraversal)
            || circularBuildPhase == CircularBuildPhase.CONNECTOR
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

        BlockPos nextBlockPos = getNextBlockPos(state.equals(State.Mining));

        if (miningPos != null || nextBlockPos == null) return;

        if (state.equals(State.Walking)) {
            if (PlayerUtils.distanceTo(nextBlockPos.toCenterPos()) <= interactionRange.get()) {
                tryPlacingBlock(nextBlockPos);
            }
        } else {
            Vec3d centerPos = nextBlockPos.toCenterPos();
            if (centerPos.getZ() - mc.player.getZ() > 0.5) {
                miningPos = nextBlockPos;
                mc.player.setPitch((float) Rotations.getPitch(miningPos));
                BlockState blockState = MapAreaCache.getCachedBlockState(miningPos);
                if (!equipMiningTool(blockState)) {
                    state = State.AwaitBlockBreak;
                    stopMovement();
                    return;
                }
                BlockUtils.breakBlock(miningPos, true);
                state = State.Mining;
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
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            checkedChests.clear();
            return getBestChest(item);
        }
        return new Pair(bestChestPos, bestPos);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required build materials
        strictMiningRestockActive = false;
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        for (Item item : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(item);
            requiredItems.put(item, oldAmount - invMaterial.get(item));
        }

        for (Item item : requiredItems.keySet()) {
            if (requiredItems.get(item) <= 0) continue;
            int stacks = InventoryCapacity.slotsForAmount(
                requiredItems.get(item),
                Utils.maximumStackSize(item)
            );
            info("Restocking §a" + stacks + " stacks " + item.getName().getString() + " (" + requiredItems.get(item) + ")");
            restockList.add(0, Triple.of(item, stacks, requiredItems.get(item)));
        }
        addClosestRestockCheckpoint();
    }

    private void refillMiningInventory() {
        // Fills restockList with required mining tools
        strictMiningRestockActive = false;
        restockList.clear();

        // Calculate total uses per tool
        HashMap<ItemStack, Integer> toolUseDict = new HashMap<>();
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(x, map[x][z].getRight(), z));
                if (!blockstate.isAir()) {
                    ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockstate);
                    if (bestTool == null) continue;
                    if (toolUseDict.containsKey(bestTool)) {
                        toolUseDict.put(bestTool, toolUseDict.get(bestTool) + 1);
                    } else {
                        toolUseDict.put(bestTool, 1);
                    }
                }
            }
        }
        for (BlockPos relative : connectorTargets) {
            BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (blockstate.isAir()) continue;
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockstate);
            if (bestTool == null) continue;
            toolUseDict.put(bestTool, toolUseDict.getOrDefault(bestTool, 0) + 1);
        }

        for (ItemStack itemStack : toolUseDict.keySet()) {
            // Fetch unbreaking level
            int unbreakingLevel = 0;
            for (var e : EnchantmentHelper.getEnchantments(itemStack).getEnchantmentEntries()) {
                if (!e.getKey().getKey().isPresent()) continue;
                if (e.getKey().getKey().get().getValue().equals(Enchantments.UNBREAKING.getValue())) {
                    unbreakingLevel = e.getIntValue();
                }
            }
            int rawUses = toolUseDict.get(itemStack);
            float slaveModifier = (float) (trueInterval.getRight() - trueInterval.getLeft() + 1) / (float) map.length;
            int itemsNeeded = MiningToolBudget.toolsRequired(
                rawUses,
                unbreakingLevel,
                itemStack.getMaxDamage(),
                durabilityBuffer.get(),
                slaveModifier
            );
            info("Restocking §a" + itemsNeeded + " " + itemStack.getItem().getName().getString() + " (" + rawUses + " uses)");
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), itemsNeeded, itemsNeeded));
        }

        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Pair<BlockPos, Vec3d> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos.getLeft() == null) {
                warning("No chest found for " + entry.getLeft().getName().getString());
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getRight());
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
    }

    private void endRestocking() {
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedChest);
            Item requestedItem = restockList.getFirst().getLeft();
            ArrayList<Pair<BlockPos, Vec3d>> registeredChests =
                materialDict.getOrDefault(requestedItem, new ArrayList<>());
            boolean uncheckedChestExists = registeredChests.stream()
                .anyMatch(chest -> !checkedChests.contains(chest.getLeft()));
            if (strictMiningRestockActive && !uncheckedChestExists) {
                error(
                    "Registered chests do not contain enough "
                        + requestedItem.getName().getString()
                        + " for the verified mining traversal."
                );
                strictMiningRestockActive = false;
                restockList.clear();
                toggle();
                return;
            }
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(requestedItem);
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
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

    private void tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Block target = buildTargets.get(relativePos);
        if (target == null) {
            warning("No compact build target at " + relativePos.toShortString() + ".");
            return;
        }
        Item material = target.asItem();
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotatePlace.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                toBeSwappedSlot = slot;
                Utils.setForwardPressed(false);
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(
                    activeCircularBuildPair >= 0 ? 0 : velocity.x,
                    velocity.y,
                    0
                );
                timeoutTicks = preSwapDelay.get();
                return;
            }
        }
        if (lastSwappedMaterial == material) return;      //Wait for swapped material
        int pairIndex = Math.floorDiv(relativePos.getX(), 2);
        if (circularPairModes.getOrDefault(pairIndex, false)) {
            error(
                "Unexpected material shortage inside circular pair " + pairIndex
                    + ". Stopping instead of leaving the U route for a mid-pair restock."
            );
            toggle();
            return;
        }
        info("No " + material.getName().getString() + " found in inventory. Resetting...");
        mc.player.setVelocity(0, 0, 0);
        Vec3d pathCheckpoint = new Vec3d(
            mc.player.getX(),
            northWalkwayFeetY(),
            mapCorner.north().toCenterPos().getZ()
        );
        checkpoints.add(0, new Pair(mc.player.getEntityPos(), new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));
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
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        activeCircularConnectorSteps = List.of();
        activeCircularRecoveryTargets = List.of();
        checkpoints.clear();
        for (CompactCircularNbtPlan.PairRoute route : compactPlan.pairRoutes()) {
            if (route.outboundX() < workingInterval.getLeft()
                || route.returnX() > workingInterval.getRight()) {
                continue;
            }

            if (circularPairModes.getOrDefault(route.pairIndex(), false)) {
                boolean pairFinished = circularPairTargets(route).stream()
                    .allMatch(relative -> {
                        BlockState state =
                            MapAreaCache.getCachedBlockState(mapCorner.add(relative));
                        return !state.isAir() && state.getBlock() == buildTargets.get(relative);
                    });
                if (pairFinished) continue;

                CircularBuildCheckpointPlan.Plan<BlockPos> traversal =
                    circularBuildCheckpointPlan(route);
                List<BlockPos> structural = traversal.structuralCheckpoints();
                checkpoints.add(new Pair<>(
                    walkingPosition(structural.get(0)),
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

    private boolean hasSufficientPairMaterials(int pairIndex) {
        if (pairIndex < 0 || pairIndex >= compactPlan.pairRoutes().size()) return false;
        CompactCircularNbtPlan.PairRoute route = compactPlan.pairRoutes().get(pairIndex);
        HashMap<Item, Integer> required = new HashMap<>();
        for (BlockPos relative : circularPairTargets(route)) {
            if (!MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) continue;
            Item item = buildTargets.get(relative).asItem();
            required.put(item, required.getOrDefault(item, 0) + 1);
        }

        HashMap<Item, Integer> available = new HashMap<>();
        for (int slot : availableSlots) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            available.put(
                stack.getItem(),
                available.getOrDefault(stack.getItem(), 0) + stack.getCount()
            );
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    private boolean hasEarlierMissingBuildTarget(CompactCircularNbtPlan.PairRoute route) {
        BlockPos firstTarget = surfaceRuntimePosition(route.outboundX(), 1);
        int pairStart = orderedBuildTargets.indexOf(firstTarget);
        if (pairStart < 0) return false;
        for (int index = 0; index < pairStart; index++) {
            BlockPos relative = orderedBuildTargets.get(index);
            if (MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) {
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
            error(
                "Circular connector changed unexpectedly at "
                    + world.toShortString() + "."
            );
            toggle();
            return false;
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

    private Vec3d circularBuildHandoffGoal(Vec3d currentGoal) {
        if (state != State.Walking
            || checkpoints.isEmpty()
            || activeCircularBuildPair < 0
            || activeCircularBuildPair >= compactPlan.pairRoutes().size()) {
            return null;
        }
        double horizontalDistance = PlayerUtils.distanceTo(
            currentGoal.add(0, mc.player.getY() - currentGoal.y, 0)
        );
        if (horizontalDistance >= circularBuildConnectorBuffer()) {
            return null;
        }

        String action = checkpoints.getFirst().getRight().getLeft();
        if (action.equals("uBuildOutboundEnd")
            && circularBuildPhase == CircularBuildPhase.OUTBOUND) {
            CompactCircularNbtPlan.PairRoute route =
                compactPlan.pairRoutes().get(activeCircularBuildPair);
            List<BlockPos> connectorSteps =
                circularBuildCheckpointPlan(route).connectorTraversalSteps();
            return walkingPosition(connectorSteps.getFirst());
        }
        if (action.equals("uBuildConnectorEnd")
            && circularBuildPhase == CircularBuildPhase.RETURN
            && checkpoints.size() > 1) {
            return checkpoints.get(1).getLeft();
        }
        return null;
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

        List<BlockPos> targets = activeCircularRecoveryTargets;
        if (targets.isEmpty()) {
            error("Circular build recovery lost its validated support path.");
            toggle();
            return;
        }
        if (CircularBuildRecoveryCursor.complete(
            activeCircularConnectorIndex,
            targets.size()
        )) {
            circularBuildRecoveryDirection = 0;
            activeCircularRecoveryTargets = List.of();
            circularBuildPhase = CircularBuildPhase.RECOVERY_EXIT;
            stopMovement();
            return;
        }

        BlockPos relative = targets.get(activeCircularConnectorIndex);
        BlockPos world = mapCorner.add(relative);
        Block expected = buildTargets.get(relative);
        BlockState current = MapAreaCache.getCachedBlockState(world);
        if (expected == null
            || current.getBlock() != expected
            || !MapAreaCache.getCachedBlockState(world.up()).isAir()
            || !MapAreaCache.getCachedBlockState(world.up(2)).isAir()) {
            error(
                "Interrupted circular build route changed at "
                    + world.toShortString() + "."
            );
            toggle();
            return;
        }

        Vec3d goal = walkingPosition(world);
        double buffer =
            CircularTraversalSafety.checkpointBuffer(checkpointBuffer.get());
        if (PlayerUtils.distanceTo(goal) < buffer) {
            if (snapToCheckpoints.get()) {
                mc.player.setPosition(goal.x, mc.player.getY(), goal.z);
            }
            stopMovement();
            activeCircularConnectorIndex =
                CircularBuildRecoveryCursor.advance(
                    activeCircularConnectorIndex,
                    circularBuildRecoveryDirection,
                    targets.size()
                );
            if (CircularBuildRecoveryCursor.complete(
                activeCircularConnectorIndex,
                targets.size()
            )) {
                circularBuildRecoveryDirection = 0;
                activeCircularRecoveryTargets = List.of();
                circularBuildPhase = CircularBuildPhase.RECOVERY_EXIT;
            }
            return;
        }

        moveAlongCircularSupport(goal);
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
            northWalkwaySupport(route.returnX())
        );
    }

    private boolean isCircularSurfaceLegComplete(int x) {
        for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
            BlockPos relative = surfaceRuntimePosition(x, nbtZ);
            BlockState state =
                MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (state.isAir() || state.getBlock() != buildTargets.get(relative)) {
                return false;
            }
        }
        return true;
    }

    private BlockPos firstUnexpectedCircularSurfaceBlock(int x) {
        for (int nbtZ = 1; nbtZ <= CompactCircularNbtPlan.FAR_Z; nbtZ++) {
            BlockPos relative = surfaceRuntimePosition(x, nbtZ);
            BlockState state =
                MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (!state.isAir()
                && state.getBlock() != buildTargets.get(relative)) {
                return mapCorner.add(relative);
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
            int currentIndex = targets.indexOf(supportUnderPlayer.subtract(mapCorner));
            if (currentIndex >= 0) {
                if (!isPlayerStandingOnSupport(supportUnderPlayer)) {
                    error(
                        "Interrupted circular build recovery cannot identify a stable support under the player."
                    );
                    toggle();
                    return false;
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

        checkpoints.clear();
        buildRecoveryRestockAfterEgress |= inventoryLost;
        circularBuildPhase = CircularBuildPhase.RECOVERY;
        activeCircularConnectorIndex = currentIndex;
        activeCircularConnectorSteps = List.of();
        activeCircularRecoveryTargets = List.copyOf(targets);
        if (prefixSafe) {
            circularBuildRecoveryDirection = -1;
            BlockPos walkway = northWalkwaySupport(route.outboundX());
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
            checkpoints.add(new Pair<>(
                walkingPosition(walkway),
                new Pair<>(
                    "uBuildRecoveryExit",
                    new BlockPos(route.returnX(), 0, 0)
                )
            ));
        }
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

    private boolean isNearRegisteredPosition(Vec3d position) {
        return PlayerUtils.distanceTo(position) <= 1.0;
    }

    private boolean replanCircularBuildFromSafeArea(boolean inventoryLost) {
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        applyPendingInterval();
        resetMapAreaCache();
        configurePairTraversalModes();
        if (!validateCompactWorkspace()) {
            toggle();
            return false;
        }
        calculateBuildingPath(false);
        if (inventoryLost && !checkpoints.isEmpty()) {
            checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
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
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ()
                + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        Vec3d lookPos = new Vec3d(goal.x, goal.y, lookZ);
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

    private boolean isUTraversalCheckpoint(
        Pair<Vec3d, Pair<String, BlockPos>> checkpoint
    ) {
        String action = checkpoint.getRight().getLeft();
        return action.isEmpty()
            || action.equals("removeUBlock")
            || action.equals("verifyUTools")
            || action.equals("uMiningTaskEnd");
    }

    private boolean calculateCircularMiningPath(CompactCircularNbtPlan.PairRoute route) {
        CircularMiningRecoveryPlan.Result recovery = analyzeCircularMiningRoute(route);
        if (recovery.mode() == CircularMiningRecoveryPlan.Mode.COMPLETE) {
            return false;
        }
        if (recovery.mode() == CircularMiningRecoveryPlan.Mode.FALLBACK) {
            return false;
        }

        ArrayList<BlockPos> targets = circularPairTargets(route);
        checkpoints.clear();
        activeMiningLine = -1;

        CircularMiningTraversalPlan.Plan traversal =
            CircularMiningTraversalPlan.create(targets.size(), recovery);
        addCircularEntry(
            traversal.entry() == CircularMiningTraversalPlan.Endpoint.START
                ? route.outboundX()
                : route.returnX(),
            route.pairIndex()
        );
        for (CircularMiningTraversalPlan.Step step : traversal.steps()) {
            if (step.removesBlock()) {
                addWalkTarget(
                    targets.get(step.standIndex()),
                    "removeUBlock",
                    targets.get(step.removeIndex())
                );
            } else {
                addWalkTarget(targets.get(step.standIndex()), "");
            }
        }
        addCircularExit(
            traversal.exit() == CircularMiningTraversalPlan.Endpoint.START
                ? route.outboundX()
                : route.returnX(),
            targets.get(traversal.finalRemoveIndex())
        );

        Pair<Vec3d, Pair<String, BlockPos>> end = checkpoints.getLast();
        checkpoints.add(new Pair<>(end.getLeft(), new Pair<>("uMiningTaskEnd", null)));
        state = State.MiningUTraversal;
        info(
            "Mining pair " + route.pairIndex() + " with "
                + recovery.mode().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " U traversal"
        );
        return true;
    }

    private void addCircularEntry(int x, int pairIndex) {
        BlockPos walkway = northWalkwaySupport(x);
        checkpoints.add(new Pair<>(
            walkingPosition(walkway),
            new Pair<>("verifyUTools", new BlockPos(pairIndex, 0, 0))
        ));
    }

    private void addCircularExit(int x, BlockPos finalRelativeTarget) {
        BlockPos walkway = northWalkwaySupport(x);
        checkpoints.add(new Pair<>(
            walkingPosition(walkway),
            new Pair<>("removeUBlock", mapCorner.add(finalRelativeTarget))
        ));
    }

    private void addWalkTarget(BlockPos relative, String action) {
        addWalkTarget(relative, action, null);
    }

    private void addWalkTarget(BlockPos relative, String action, BlockPos removeRelative) {
        checkpoints.add(new Pair<>(
            walkingPosition(mapCorner.add(relative)),
            new Pair<>(action, removeRelative == null ? null : mapCorner.add(removeRelative))
        ));
    }

    private boolean equipMiningTool(BlockState targetState) {
        ItemStack bestTool = ToolUtils.getBestTool(toolSet, targetState);
        if (bestTool == null) {
            error("No registered tool can safely mine " + targetState.getBlock().getName().getString() + ".");
            toggle();
            return false;
        }
        for (int slot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem().equals(bestTool.getItem())) {
                return InvUtils.swap(slot, false);
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem().equals(bestTool.getItem())) {
                int targetHotbarSlot = availableHotBarSlots.getFirst();
                Utils.performSwap(slot, targetHotbarSlot);
                timeoutTicks = Math.max(1, postSwapDelay.get());
                stopMovement();
                return false;
            }
        }
        error("Required mining tool is missing from the inventory: " + bestTool.getName().getString() + ".");
        toggle();
        return false;
    }

    private HashMap<Item, Integer> missingCircularMiningTools(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return missingMiningTools(circularPairTargets(route));
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
        HashMap<Item, Integer> rawUses = new HashMap<>();
        HashMap<Item, Integer> maximumDamage = new HashMap<>();
        for (BlockPos relative : relativeTargets) {
            BlockState state = MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (state.isAir()) continue;
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, state);
            if (bestTool == null || bestTool.getMaxDamage() <= 0) {
                error(
                    "No registered damageable tool can mine "
                        + state.getBlock().getName().getString() + "."
                );
                return null;
            }
            Item item = bestTool.getItem();
            rawUses.put(item, rawUses.getOrDefault(item, 0) + 1);
            maximumDamage.put(item, bestTool.getMaxDamage());
        }

        HashMap<Item, Long> remainingDurability = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !rawUses.containsKey(stack.getItem())) continue;
            long remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
            remainingDurability.put(
                stack.getItem(),
                remainingDurability.getOrDefault(stack.getItem(), 0L) + remaining
            );
        }

        HashMap<Item, Integer> missingTools = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : rawUses.entrySet()) {
            Item item = entry.getKey();
            int missing = MiningToolBudget.missingFreshToolsForTraversal(
                entry.getValue(),
                durabilityBuffer.get(),
                maximumDamage.get(item),
                remainingDurability.getOrDefault(item, 0L)
            );
            if (missing > 0) missingTools.put(item, missing);
        }
        return missingTools;
    }

    private boolean ensureCircularMiningToolDurability(
        CompactCircularNbtPlan.PairRoute route
    ) {
        return ensureMiningToolDurability(
            circularPairTargets(route),
            "circular pair " + route.pairIndex()
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
        restockList.clear();
        strictMiningRestockActive = false;
        HashMap<Item, Integer> missingTools = missingMiningTools(targets);
        if (missingTools == null) return false;

        int freeSlots = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) freeSlots++;
        }
        int requiredSlots = missingTools.values().stream().mapToInt(Integer::intValue).sum();
        if (requiredSlots > freeSlots) {
            error(
                traversalName + " needs " + requiredSlots
                    + " fresh tool slots, but only " + freeSlots
                    + " inventory slots are empty. Stopping before mining."
            );
            return false;
        }

        for (Map.Entry<Item, Integer> entry : missingTools.entrySet()) {
            ArrayList<Pair<BlockPos, Vec3d>> chests = materialDict.get(entry.getKey());
            if (chests == null || chests.isEmpty()) {
                error(
                    "No registered tool chest can supply "
                        + entry.getKey().getName().getString() + "."
                );
                return false;
            }
            info(
                "Preloading §a" + entry.getValue() + " "
                    + entry.getKey().getName().getString()
                    + " for worst-case durability on " + traversalName
            );
            restockList.add(Triple.of(entry.getKey(), entry.getValue(), entry.getValue()));
        }
        strictMiningRestockActive = !restockList.isEmpty();
        addClosestRestockCheckpoint();
        return true;
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

    private boolean isMiningLineComplete(int line) {
        return reportedMinedLines.contains(line) || isLineMined(line);
    }

    private void startBuilding() {
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
        info("Start building map");
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        calculateBuildingPath(true);
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
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
            boolean useCircular = assignedToThisBot
                && CircularBuildAssignmentPolicy.useCircular(
                    true,
                    circularTraversalForCurrentMap,
                    pairFitsUsableInventory(route)
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
        HashMap<Item, Integer> required = new HashMap<>();
        for (BlockPos relative : circularPairTargets(route)) {
            if (!MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) continue;
            Item item = buildTargets.get(relative).asItem();
            required.put(item, required.getOrDefault(item, 0) + 1);
        }
        return Utils.stacksRequired(required) <= availableSlots.size();
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

    private double northWalkwayFeetY() {
        if (northWalkwayRelativeY == null) {
            throw new IllegalStateException("The compact north walkway is unresolved.");
        }
        return mapCorner.getY() + northWalkwayRelativeY + 1.0;
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
                if (reportError) {
                    error(
                        "Circular pair contains an unexpected support at "
                            + world.toShortString() + "."
                    );
                }
                return false;
            }
            if (!MapAreaCache.getCachedBlockState(world.up()).isAir()
                || !MapAreaCache.getCachedBlockState(world.up(2)).isAir()) {
                if (reportError) {
                    error("Circular pair headroom is blocked at " + world.toShortString() + ".");
                }
                return false;
            }
        }
        return true;
    }

    private boolean endBuilding() {
        // Only executed on Master
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
        if (!archiveCurrentNbtFiles()) {
            state = State.AwaitNbtArchive;
            timeoutTicks = 100;
            stopMovement();
            return false;
        }
        info("Finished building map");
        applyPendingInterval();
        buildingActive = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        buildRecoveryRestockAfterEgress = false;
        state = State.Walking;
        workingInterval = trueInterval;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Pair<BlockPos, Vec3d> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
        return true;
    }

    private boolean archiveCurrentNbtFiles() {
        if (!moveToFinishedFolder.get() || currentMapArchived) return true;
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
            mapFile = archived.archivedSource().toFile();
            generatedMapFile = archived.archivedGenerated()
                .map(java.nio.file.Path::toFile)
                .orElse(null);
            currentMapArchived = true;
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

    private void startMining() {
        beginMapMining(true);
    }

    private void beginMapMining(boolean refillTools) {
        resetMapAreaCache();
        if (!ensureNorthWalkwayResolved()) {
            toggle();
            return;
        }
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

        if (masterAssigned && sleep.get()) {
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
        for (int line = 0; line < map.length; line++) {
            if (reservedMiningLines.contains(line) || isMiningLineComplete(line)) continue;

            CompactCircularNbtPlan.PairRoute route = compactPlan.pairRoutes().get(line / 2);
            boolean pairAvailable = !reservedMiningLines.contains(route.outboundX())
                && !reservedMiningLines.contains(route.returnX());
            if (!circularTraversalForCurrentMap || !pairAvailable) {
                return new MiningAssignment(line, false, Set.of(line));
            }
            CircularMiningAssignmentPolicy.Kind policy =
                CircularMiningAssignmentPolicy.decide(
                    true,
                    true,
                    analyzeCircularMiningRoute(route).mode()
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
        currentMiningLines.clear();
        currentMiningLines.addAll(assignment.lines());
        currentMiningPaired = false;
        pendingIndependentMiningLines.clear();

        boolean wholePairReserved = isWholePairAssignment(assignment);
        if (assignment.paired() && !wholePairReserved) {
            error("Rejected a circular mining assignment without its complete pair.");
            toggle();
            return;
        }
        if (MiningAssignmentMode.usesCircularTraversal(
            assignment.paired(),
            wholePairReserved
        )) {
            CompactCircularNbtPlan.PairRoute route =
                compactPlan.pairRoutes().get(assignment.anchorLine() / 2);
            if (calculateCircularMiningPath(route)) {
                currentMiningPaired = true;
                if (!ensureCircularMiningToolDurability(route)) {
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
            error("Cannot recover U mining because no active pair assignment is available.");
            toggle();
            return;
        }
        restockList.clear();
        restockBacklogSlots.clear();
        checkedChests.clear();
        strictMiningRestockActive = false;
        resumeAfterRestockState = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        interactTimeout = 0;
        lastInteractedChest = null;
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
            if (!isMiningLineComplete(line)) {
                if (!startNextMasterMiningAssignment()) {
                    error("No safe mining assignment could be generated for line " + line + ".");
                    toggle();
                }
                return;
            }
        }

        for (BlockPos relative : connectorTargets) {
            if (reportedClearedConnectorPairs.contains(relative.getX() / 2)) continue;
            if (!MapAreaCache.getCachedBlockState(mapCorner.add(relative)).isAir()) {
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
        miningAssignmentsActive = false;
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

    private void scheduleUsedToolDeposits() {
        checkpoints.clear();
        usedToolDepositPlan.clear();

        HashMap<BlockPos, Pair<Vec3d, Set<Item>>> destinations = new HashMap<>();
        for (Item item : getInventoryToolItems()) {
            Pair<BlockPos, Vec3d> destination = usedToolChests.get(item);
            if (destination == null) destination = usedToolChest;
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
        if (availableHotBarSlots.isEmpty()) {
            warning("No free slots found in hot-bar!");
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
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        return invInformation.getLeft().get(0);
    }

    private HashMap<Item, Integer> getRequiredItems() {
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

    private void swapIntoHotbar(int slot) {
        Map<Item, Integer> itemSlot = new HashMap<>();
        Map<Item, Integer> itemDistance = new HashMap<>();
        Map<Item, Integer> itemFrequency = new HashMap<>();

        int targetSlot = availableHotBarSlots.get(0);

        // Scan hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemSlot.put(item, hotbarSlot);
                itemDistance.put(item, -1); // -1 = never used
                itemFrequency.put(item, 0);
            } else {
                targetSlot = hotbarSlot;
                break;
            }
        }

        // PRIORITY 1: empty slot → instant choice
        if (mc.player.getInventory().getStack(targetSlot).isEmpty()) {
            Utils.performSwap(slot, targetSlot);
            return;
        }

        // Get blocks until next use of items in hotbar
        int blockCounter = 0;
        for (BlockPos relative : orderedBuildTargets) {
            if (!isInWorkingInterval(relative)) continue;
            blockCounter++;

            BlockState state = MapAreaCache.getCachedBlockState(mapCorner.add(relative));
            if (state.isAir()) {
                Item item = buildTargets.get(relative).asItem();
                if (itemDistance.containsKey(item) && itemDistance.get(item) == -1) {
                    itemDistance.put(item, blockCounter);
                }
            }
        }

        // Count frequency of items in hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemFrequency.put(item, itemFrequency.get(item) + 1);
            }
        }

        // Choose best candidate
        Item bestItem = null;
        int bestDistance = -2; // lower than -1
        int bestFrequency = -1;

        for (Item item : itemSlot.keySet()) {
            int distance = itemDistance.get(item); // -1 = never used
            int frequency = itemFrequency.get(item);

            boolean better = false;

            // PRIORITY 2: never used (-1)
            if (distance == -1 && bestDistance != -1) {
                better = true;
            }
            // PRIORITY 3: hotbar frequency
            else if (frequency > bestFrequency) {
                better = true;
            }
            // PRIORITY 4: distance to next use
            else if (frequency == bestFrequency && distance > bestDistance && bestDistance != -1) {
                better = true;
            }

            if (better) {
                bestItem = item;
                bestDistance = distance;
                bestFrequency = frequency;
            }
        }

        if (bestItem != null) {
            targetSlot = itemSlot.get(bestItem);
        }

        Utils.performSwap(slot, targetSlot);
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
            oldState = state;
            state = State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
        }
    }

    public void start() {
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
            if (isCircularMiningOrRestockState()) {
                miningRecoveryPending = true;
            } else if (buildingActive && activeCircularBuildPair >= 0) {
                buildRecoveryPending = true;
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
        if (!awaitingFinalization
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
        currentSlaveMiningTaskId = -1;
        highestSlaveMiningTaskId = -1;
        pendingSlaveMiningCompletion = null;
        currentMiningSessionId = -1;
        lastFinalizedMiningSessionId = -1;
        pendingMiningFinalizationAck = -1;
        slaveAwaitingNextMapRelease = false;
        nextMapSyncTicks = 0;
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
        return buildingActive
            || miningAssignmentsActive
            || state == State.AwaitMasterAllBuiltSkip
            || state == State.AwaitSlaveFinalization
            || state == State.AwaitSlaveRemoval
            || state == State.AwaitCompactWorkspace
            || state == State.AwaitNbtArchive
            || state == State.AwaitNbtArchiveSkip
            || !pendingSlaveMiningFinalizations.isEmpty();
    }

    public void skipBuilding() {
        if (availableSlots.isEmpty()) setupSlots();
        circularTraversalForCurrentMap = circularTraversal.get();
        buildingActive = false;
        activeCircularBuildPair = -1;
        activeCircularConnectorIndex = -1;
        circularBuildRecoveryDirection = 0;
        circularBuildPhase = CircularBuildPhase.NONE;
        knownErrors.clear();
        checkpoints.clear();
        if (SlaveSystem.isSlave()) {
            checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
            state = State.Walking;
        } else {
            if (archiveCurrentNbtFiles()) {
                state = State.AwaitMasterAllBuiltSkip;
            } else {
                state = State.AwaitNbtArchiveSkip;
                timeoutTicks = 100;
                stopMovement();
            }
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
                usedToolChests);
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
        }
        return true;
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("All nbt files finished");
                toggle();
            }
            return false;
        }
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }

        return true;
    }

    private boolean loadNBTFile() {
        try {
            generatedMapFile = null;
            currentMapArchived = false;
            activeMapName = null;
            northWalkwayRelativeY = null;
            info("Loading NBT: §a" + mapFile.getName());
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

            if (loaded.inputKind() == CompactCircularNbtGenerator.InputKind.SOURCE
                && saveGeneratedNbt.get()) {
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
            warning("Compact NBT rejected: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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

        File configFolder = new File(mapFolder, "_configs");
        if (!configFolder.exists()) return table;

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
        AwaitFinishedMapChestResponse,
        AwaitUsedToolChestResponse,
        AwaitCartographyResponse,
        AwaitNBTFile,
        AwaitBlockBreak,
        AwaitUBlockBreak,
        AwaitMasterAllBuilt,
        AwaitMasterAllBuiltSkip,
        AwaitNbtArchive,
        AwaitNbtArchiveSkip,
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
