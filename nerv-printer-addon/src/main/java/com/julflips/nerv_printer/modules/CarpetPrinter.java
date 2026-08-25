package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.interfaces.MapPrinter;
import com.julflips.nerv_printer.utils.*;
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
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CarpetPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render", false);

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("The maximum range you can place carpets around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> minPlaceDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-place-distance")
        .description("The minimal distance a placement has to have to the player. Avoids placements colliding with the player.")
        .defaultValue(0.8)
        .min(0)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<List<Block>> ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignored-Blocks")
        .description("Blocks types that will not be placed. Useful to print semi-transparent maps.")
        .defaultValue()
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

    private final Setting<List<Block>> startBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-blocks")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<Integer> mapFillSquareSize = sgGeneral.add(new IntSetting.Builder()
        .name("map-fill-square-size")
        .description("The radius of the square the bot fill walk to explore the map.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.NotPlacing)
        .build()
    );

    public final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Resets all values when module is activated or the client relogs. Disable to be able to pause.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate when placing a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> startNorthToSouth = sgGeneral.add(new BoolSetting.Builder()
        .name("north-to-south")
        .description("Start printing on the north side and go south. Flipped if disabled.")
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

    private final Setting<Integer> resetChestCloseDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("reset-chest-close-delay")
        .description("How many ticks to wait before closing the reset trap chest again.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
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

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> breakCarpetAboveReset = sgAdvanced.add(new BoolSetting.Builder()
        .name("break-carpet-above-reset")
        .description("Break the carpet above the reset chest before activating. Useful when interactions trough blocks are not allowed.")
        .defaultValue(true)
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
        .defaultValue(ErrorAction.Repair)
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
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
        .defaultValue(0.15)
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
    int closeResetChestTicks;
    int interactTimeout;
    int toBeSwappedSlot;
    long lastTickTime;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    State debugPreviousState;
    Tuple<Integer, Integer> workingInterval;     //Interval the bot should work in 0-127
    Tuple<BlockPos, Vec3> reset;
    Tuple<BlockPos, Vec3> cartographyTable;
    Tuple<BlockPos, Vec3> finishedMapChest;
    ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests;
    Tuple<Vec3, Tuple<Float, Float>> dumpStation;                    //Pos, Yaw, Pitch
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedBlockPos;
    BlockPos miningPos;
    Item lastSwappedMaterial;
    ClientboundContainerSetContentPacket toBeHandledInvPacket;
    HashMap<Integer, Tuple<Block, Integer>> blockPaletteDict;       //Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict;  //Maps item to the chest pos and the open position
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Item, Integer, Integer>> restockList;        //Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Tuple<Vec3, Tuple<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<BlockPos> knownErrors;
    Block[][] map;
    File mapFolder;
    File mapFile;

    public CarpetPrinter() {
        super(Addon.CATEGORY, "carpet-printer", "Automatically builds 2D carpet maps from nbt files.");
    }

    @Override
    public void onActivate() {
        lastTickTime = System.currentTimeMillis();
        if (!activationReset.get() && checkpoints != null) {
            return;
        }
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        knownErrors = new ArrayList<>();
        reset = null;
        mapCorner = null;
        lastInteractedBlockPos = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        closeResetChestTicks = 0;
        toBeSwappedSlot = -1;
        oldState = null;
        debugPreviousState = null;

        setInterval(new Tuple<>(0, 127));
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

        if (!prepareNextMapFile()) return;

        state = State.SelectingMapArea;
        if (useDefaultConfigFile.get()) {
            File configFolder = new File(mapFolder, "_configs");
            if (!loadConfig(new File(configFolder, configFileName.get()))) {
                info("Select the §aMap Building Area (128x128)");
            }
        } else {
            info("Select the §aMap Building Area (128x128)");
        }
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof ServerboundPlayerActionPacket packet
            && (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            dumpStation = new Tuple<>(mc.player.position(), new Tuple<>(mc.player.getYRot(), mc.player.getXRot()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the §aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof ServerboundUseItemOnPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getHitResult().getBlockPos().above();
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                MapAreaCache.reset(mapCorner);
                state = State.SelectingReset;
                info("Map Area selected. Press the §aReset Trapped Chest §7used to remove the carpets");
                break;
            case SelectingReset:
                BlockPos blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof TrappedChestBlock) {
                    reset = new Tuple<>(blockPos, mc.player.position());
                    info("Reset Trapped Chest selected. Select the §aCartography Table.");
                    state = State.SelectingTable;
                }
                break;
            case SelectingTable:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Tuple<>(blockPos, mc.player.position());
                    info("Cartography Table selected. Please throw an item into the §aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Tuple<>(blockPos, mc.player.position());
                    info("Finished Map Chest selected. Select all §aMap- and Material-Chests. Interact with the Start Block to start printing.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlocks.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (blockState.getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlocks.get().contains(blockState.getBlock())) {
                    //Check if requirements to start building are met
                    if (materialDict.isEmpty()) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.isEmpty()) {
                        warning("No Map Chests selected!");
                        return;
                    }
                    if (!setupSlots()) {
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

        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) Utils.setForwardPressed(false);
        }

        if (!(event.packet instanceof ClientboundContainerSetContentPacket packet)) return;

        if (state.equals(State.AwaitRegisterResponse)) {
            //info("Chest content received.");
            Item foundItem = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.items().size() - 36; i++) {
                ItemStack stack = packet.items().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered §aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.position());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            info("Registered §a" + Utils.itemName(foundItem));
            if (!materialDict.containsKey(foundItem)) materialDict.put(foundItem, new ArrayList<>());
            ArrayList<Tuple<BlockPos, Vec3>> oldList = materialDict.get(foundItem);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.position());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitResetResponse);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
        }
    }

    private void handleInventoryPacket(ClientboundContainerSetContentPacket packet) {
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                boolean foundMaterials = false;
                List<Integer> slots = IntStream.rangeClosed(0, packet.items().size() - 37)
                    .boxed()
                    .collect(Collectors.toList());
                Collections.shuffle(slots);

                for (int slot : slots) {
                    ItemStack stack = packet.items().get(slot);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && stack.getCount() == 64) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Tuple(dumpStation.getA(), new Tuple("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(slot);
                        Triple<Item, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                //Search for map and glass pane
                for (int slot = 0; slot < packet.items().size() - 36; slot++) {
                    ItemStack stack = packet.items().get(slot);
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

                Vec3 center = Vec3.atCenterOf(mapCorner.offset(map.length / 2 - 1, 0, map[0].length / 2 - 1));
                checkpoints.add(new Tuple(center, new Tuple("fillMap", null)));
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
                    ItemStack stack = packet.items().get(slot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.items().get(slot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                mc.gameMode.handleContainerInput(packet.containerId(), 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                checkpoints.add(new Tuple(finishedMapChest.getB(), new Tuple("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                if (breakCarpetAboveReset.get()) {
                    BlockPos abovePos = reset.getA().above();
                    if (MapAreaCache.getCachedBlockState(abovePos).getBlock() instanceof CarpetBlock) {
                        checkpoints.add(new Tuple(reset.getB(), new Tuple("break", abovePos)));
                    }
                }
                checkpoints.add(new Tuple(reset.getB(), new Tuple("reset", null)));
                state = State.Walking;
                break;
            case AwaitResetResponse:
                interactTimeout = 0;
                closeNextInvPacket = false;
                closeResetChestTicks = resetChestCloseDelay.get();
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: §a" + state);
        }

        if (state.equals(State.AwaitMasterAllBuilt)) {
            if (SlaveSystem.allSlavesFinished()) {
                if (!endBuilding()) return;
            } else {
                return;
            }
        }

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / placeDelay.get());
        lastTickTime += (long) allowedPlacements * placeDelay.get();

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                interactWithBlock(lastInteractedBlockPos);
            }
        }

        if (closeResetChestTicks > 0) {
            closeResetChestTicks--;
            if (closeResetChestTicks == 0) {
                mc.player.closeContainer();
                Vec3 center = Vec3.atCenterOf(mapCorner.offset(map.length / 2, 0, map[0].length / 2));
                checkpoints.add(0, new Tuple(center, new Tuple("awaitClear", null)));
                state = State.Walking;
                info("close reset chest");
            }
        }

        if (timeoutTicks > 0) {
            if (mc.player.onGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
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
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 1, ContainerInput.QUICK_MOVE, mc.player);
            if (restockBacklogSlots.isEmpty()) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        // Break blocks for repair
        if (state == State.AwaitBlockBreak) {
            if (MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                miningPos = null;
                state = State.Walking;
            } else {
                Rotations.rotate(Rotations.getYaw(miningPos), Rotations.getPitch(miningPos), 50);
                BlockUtils.breakBlock(miningPos, true);
                return;
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                HashMap<Item, Integer> requiredItems = getRequiredItems();
                Tuple<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                refillInventory(invInformation.getB());
                state = State.Walking;
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getItem(dumpSlot).getHoverName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        // Await map reset
        if (state == State.AwaitAreaClear && MapAreaCache.isMapAreaClear()) {
            state = State.AwaitNBTFile;
            return;
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
            if (!prepareNextMapFile()) return;
            startBuilding();
        }

        // Handle Block Entity interaction response
        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            if (mc.gui.screen() != null) {
                mc.player.closeContainer();
            }
            closeNextInvPacket = false;
        }

        // Main Loop for building
        if (!state.equals(State.Walking)) return;
        Utils.setForwardPressed(true);
        if (checkpoints.isEmpty()) {
            // Creating fallback checkpoint
            checkpoints.add(new Tuple(mc.player.position(), new Tuple<>("lineEnd", null)));
        }
        Vec3 goal = checkpoints.get(0).getA();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Tuple<String, BlockPos> checkpointAction = checkpoints.get(0).getB();
            if (debugPrints.get() && checkpointAction.getA() != null)
                info("Reached: §a" + checkpointAction.getA());
            checkpoints.remove(0);
            switch (checkpointAction.getA()) {
                case "lineEnd":
                    boolean reachedNorthSide = goal.z == Vec3.atCenterOf(mapCorner).z;
                    calculateBuildingPath(reachedNorthSide, false);
                    ArrayList<BlockPos> newErrors = Utils.getInvalidPlacements(mapCorner, workingInterval, map, knownErrors);
                    for (BlockPos errorPos : newErrors) {
                        BlockPos relativePos = errorPos.subtract(mapCorner);
                        if (logErrors.get()) {
                            Block missingBlock = map[relativePos.getX()][relativePos.getZ()];
                            String missingBlockString = missingBlock == null ? "empty" : missingBlock.getName().getString();
                            info("Error at: " + errorPos.toShortString() + ". Is: "
                                + MapAreaCache.getCachedBlockState(errorPos).getBlock().getName().getString()
                                + ". Should be: " + missingBlockString);
                        }
                    }
                    knownErrors.addAll(newErrors);
                    if (!knownErrors.isEmpty() && errorAction.get() == ErrorAction.Reset) {
                        warning("ErrorAction is Reset: Resetting map because of an error...");
                        checkpoints.clear();
                        if (breakCarpetAboveReset.get()) {
                            BlockPos abovePos = reset.getA().above();
                            if (MapAreaCache.getCachedBlockState(abovePos).getBlock() instanceof CarpetBlock) {
                                checkpoints.add(new Tuple(reset.getB(), new Tuple("break", abovePos)));
                            }
                        }
                        checkpoints.add(new Tuple(reset.getB(), new Tuple("reset", null)));
                        startedFiles.remove(mapFile);
                    }
                    break;
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getA();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYRot(), mc.player.getXRot()));
                    if (mapFillSquareSize.get() == 0) {
                        checkpoints.add(0, new Tuple(cartographyTable.getB(), new Tuple<>("cartographyTable", null)));
                    } else {
                        checkpoints.add(new Tuple(goal.add(-mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Tuple("sprint", null)));
                        checkpoints.add(new Tuple(goal.add(mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Tuple("sprint", null)));
                        checkpoints.add(new Tuple(goal.add(mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Tuple("sprint", null)));
                        checkpoints.add(new Tuple(goal.add(-mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Tuple("sprint", null)));
                        checkpoints.add(new Tuple(cartographyTable.getB(), new Tuple("cartographyTable", null)));
                    }
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getA());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getA());
                    return;
                case "reset":
                    info("Resetting...");
                    state = State.AwaitResetResponse;
                    interactWithBlock(reset.getA());
                    lastInteractedBlockPos = reset.getA();
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setForwardPressed(false);
                    mc.player.setYRot(dumpStation.getB().getA());
                    mc.player.setXRot(dumpStation.getB().getB());
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getB());
                    return;
                case "awaitClear":
                    state = State.AwaitAreaClear;
                    Utils.setForwardPressed(false);
                    return;
                case "break":
                    state = State.AwaitBlockBreak;
                    miningPos = checkpointAction.getB();
                    Utils.setForwardPressed(false);
                    Rotations.rotate(Rotations.getYaw(miningPos), Rotations.getPitch(miningPos), 50);
                    BlockUtils.breakBlock(miningPos, true);
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (!knownErrors.isEmpty()) {
                    if (errorAction.get() == ErrorAction.ToggleOff) {
                        info("Found errors: ");
                        for (int i = knownErrors.size() - 1; i >= 0; i--) {
                            info("Pos: " + knownErrors.get(i).toShortString());
                        }
                        knownErrors.clear();
                        checkpoints.add(new Tuple(mc.player.position(), new Tuple("lineEnd", null)));
                        state = State.Walking;
                        warning("ErrorAction is ToggleOff: Stopping because of an error...");
                        toggle();
                        return;
                    } else if (errorAction.get() == ErrorAction.Repair) {
                        info("Fixing errors: ");
                        for (int i = knownErrors.size() - 1; i >= 0; i--) {
                            BlockPos errorPos = knownErrors.get(i);
                            info("Pos: " + errorPos.toShortString());
                            checkpoints.add(new Tuple(Vec3.atCenterOf(errorPos), new Tuple("break", errorPos)));
                        }
                        checkpoints.add(new Tuple(dumpStation.getA(), new Tuple("dump", null)));
                        for (int i = 0; i < knownErrors.size(); i++) {
                            String action = (i == knownErrors.size() - 1) ? "lineEnd" : "sprint";
                            BlockPos errorPos = knownErrors.get(i);
                            checkpoints.add(new Tuple(Vec3.atCenterOf(errorPos), new Tuple(action, null)));
                        }
                        knownErrors.clear();
                        return;
                    }
                }
                if (SlaveSystem.isSlave()) {
                    SlaveSystem.queueMasterDM("finished");
                    state = State.AwaitSlaveNextMap;
                    Utils.setForwardPressed(false);
                    return;
                }
                if (SlaveSystem.allSlavesFinished()) {
                    if (!endBuilding()) return;
                } else {
                    info("Waiting for slaves to finish...");
                    state = State.AwaitMasterAllBuilt;
                    Utils.setForwardPressed(false);
                    return;
                }
            }
            goal = checkpoints.get(0).getA();
        }
        mc.player.setYRot((float) Rotations.getYaw(goal));
        String nextAction = checkpoints.get(0).getB().getA();

        if ((nextAction == "" || nextAction == "lineEnd") && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("", "lineEnd", "sprint");
        if (!allowPlaceActions.contains(nextAction)) return;

        ArrayList<BlockPos> placements = new ArrayList<>();
        for (int i = 0; i < allowedPlacements; i++) {
            AtomicReference<BlockPos> closestPos = new AtomicReference<>();
            final Vec3 currentGoal = goal;
            BlockPos groundedPlayerPos = new BlockPos(mc.player.blockPosition().getX(), mapCorner.getY(), mc.player.blockPosition().getZ());
            Utils.iterateBlocks(groundedPlayerPos, (int) Math.ceil(placeRange.get()) + 1, 0, ((blockPos, blockState) -> {
                Double posDistance = PlayerUtils.distanceTo(Vec3.atCenterOf(blockPos));
                BlockPos relativePos = blockPos.subtract(mapCorner);
                if (blockState.isAir() && posDistance <= placeRange.get() && posDistance > minPlaceDistance.get()
                    && MapAreaCache.isWithingMap(blockPos) && map[relativePos.getX()][relativePos.getZ()] != null
                    && blockPos.getX() <= currentGoal.x() + linesPerRun.get() - 1 && !placements.contains(blockPos)
                    && blockPos.getX() >= currentGoal.x() - 1) {
                    if (closestPos.get() == null || posDistance < PlayerUtils.distanceTo(closestPos.get())) {
                        closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                    }
                }
            }));

            if (closestPos.get() != null) {
                //Stop placing if restocking
                placements.add(closestPos.get());
                if (!tryPlacingBlock(closestPos.get())) {
                    return;
                }
            }
        }
    }

    // Restocking

    private Tuple<BlockPos, Vec3> getBestChest(Item item) {
        Vec3 bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Tuple<BlockPos, Vec3>> list;
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + Utils.itemName(item));
            toggle();
            return null;
        }
        //Get nearest chest
        for (Tuple<BlockPos, Vec3> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getA())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getB()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getB();
                bestChestPos = p.getA();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            checkedChests.clear();
            return getBestChest(item);
        }
        return new Tuple(bestChestPos, bestPos);
    }

    private void refillInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required items
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        for (Item item : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(item);
            requiredItems.put(item, oldAmount - invMaterial.get(item));
        }

        for (Item item : requiredItems.keySet()) {
            if (requiredItems.get(item) <= 0) continue;
            int stacks = (int) Math.ceil((float) requiredItems.get(item) / 64f);
            info("Restocking §a" + stacks + " stacks " + Utils.itemName(item) + " (" + requiredItems.get(item) + ")");
            restockList.add(0, Triple.of(item, stacks, requiredItems.get(item)));
        }
        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Tuple<BlockPos, Vec3> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Tuple<BlockPos, Vec3> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos == null) return;
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getB());
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        //Set closest material as first and as checkpoint
        restockList.remove(closestEntry);
        restockList.add(0, closestEntry);
        checkpoints.add(0, new Tuple(restockPos.getB(), new Tuple("refill", restockPos.getA())));
    }

    private void endRestocking() {
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedBlockPos);

            Item foundItem = null;
            for (Item item : materialDict.keySet()) {
                for (Tuple<BlockPos, Vec3> p : materialDict.get(item)) {
                    if (p.getA().equals(lastInteractedBlockPos)) {
                        foundItem = item;
                        break;
                    }
                }
            }
            if (foundItem == null) {
                warning("Could not find material for chest position : " + lastInteractedBlockPos.toShortString());
                toggle();
                return;
            }
            Tuple<BlockPos, Vec3> bestRestockPos = getBestChest(foundItem);
            if (bestRestockPos == null) return;
            checkpoints.add(0, new Tuple<>(bestRestockPos.getB(), new Tuple<>("refill", bestRestockPos.getA())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos blockPos) {
        Utils.setForwardPressed(false);
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setYRot((float) Rotations.getYaw(Vec3.atCenterOf(blockPos)));
        mc.player.setXRot((float) Rotations.getPitch(Vec3.atCenterOf(blockPos)));

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(blockPos), Utils.getInteractionSide(blockPos), blockPos, false);
        BlockUtils.interact(hitResult, InteractionHand.MAIN_HAND, true);
        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedBlockPos = blockPos;
    }

    private boolean tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Item material = map[relativePos.getX()][relativePos.getZ()].asItem();
        //info("Placing " + Utils.itemName(material) + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, InteractionHand.MAIN_HAND, slot, rotate.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return true;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                toBeSwappedSlot = slot;
                Utils.setForwardPressed(false);
                mc.player.setDeltaMovement(0, 0, 0);
                timeoutTicks = preSwapDelay.get();
                return false;
            }
        }
        if (lastSwappedMaterial == material) return false;      //Wait for swapped material
        info("No " + Utils.itemName(material) + " found in inventory. Resetting...");
        checkpoints.add(0, new Tuple(mc.player.position(), new Tuple("sprint", null)));
        checkpoints.add(0, new Tuple(dumpStation.getA(), new Tuple("dump", null)));
        return false;
    }

    // Path and Building Management

    private void calculateBuildingPath(boolean startNorthSide, boolean sprintFirst) {
        //Iterate over map and skip completed lines. Player has to be able to see the complete map area
        //Fills checkpoints list
        boolean northToSouth = startNorthSide;
        checkpoints.clear();
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += linesPerRun.get()) {
            if (!Utils.isInInterval(workingInterval, x)) continue;
            boolean lineFinished = true;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                int adjustedX = x + lineBonus;
                if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                for (int z = 0; z < 128; z++) {
                    BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.offset(adjustedX, 0, z));
                    if (blockState.isAir() && map[adjustedX][z] != null) {
                        //If there is a replaceable block and not an ignored block type at the position. Mark the line as not done
                        lineFinished = false;
                        break;
                    }
                }
            }
            if (lineFinished) continue;
            Vec3 cp1 = Vec3.atCenterOf(mapCorner).add(x, 0, 0);
            Vec3 cp2 = Vec3.atCenterOf(mapCorner).add(x, 0, 127);
            if (northToSouth) {
                checkpoints.add(new Tuple(cp1, new Tuple("", null)));
                checkpoints.add(new Tuple(cp2, new Tuple("lineEnd", null)));
            } else {
                checkpoints.add(new Tuple(cp2, new Tuple("", null)));
                checkpoints.add(new Tuple(cp1, new Tuple("lineEnd", null)));
            }
            northToSouth = !northToSouth;
        }
        if (checkpoints.size() > 0 && sprintFirst) {
            //Make player sprint to the start of the map
            Tuple<Vec3, Tuple<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Tuple(firstPoint.getA(), new Tuple("sprint", firstPoint.getB().getB())));
        }
    }

    private void startBuilding() {
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(mapCorner);
        calculateBuildingPath(startNorthToSouth.get(), true);
        checkpoints.add(0, new Tuple(dumpStation.getA(), new Tuple("dump", null)));
        state = State.Walking;
    }

    private boolean endBuilding() {
        info("Finished building map");
        state = State.Walking;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Tuple<BlockPos, Vec3> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        if (bestChest == null) return false;
        checkpoints.add(new Tuple(dumpStation.getA(), new Tuple("dump", null)));
        checkpoints.add(new Tuple(bestChest.getB(), new Tuple("mapMaterialChest", bestChest.getA())));
        try {
            if (moveToFinishedFolder.get())
                mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
        } catch (Exception e) {
            warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
            e.printStackTrace();
        }
        return true;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
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
        Tuple<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getA().isEmpty()) {
            return -1;
        }
        return invInformation.getA().get(0);
    }

    private HashMap<Item, Integer> getRequiredItems() {
        // Calculate the next items to restock
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        boolean northToSouth = true;
        boolean hasFoundAir = false;
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += linesPerRun.get()) {
            for (int z = 0; z < 128; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (adjustedX > workingInterval.getB()) break;
                    int adjustedZ = z;
                    if (!northToSouth) adjustedZ = 127 - z;
                    BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.offset(adjustedX, 0, adjustedZ));
                    if (blockState.isAir() && map[adjustedX][adjustedZ] != null) {
                        if (!hasFoundAir) {
                            hasFoundAir = true;
                            BlockState oppositeBlockState = MapAreaCache.getCachedBlockState(mapCorner.offset(adjustedX, 0, 127 - adjustedZ));
                            // If the first air block does not have an opposite air block, the snake pattern got reversed at some point
                            // We reverse the search too
                            if (!oppositeBlockState.isAir() && z < 64) {
                                northToSouth = !northToSouth;
                                adjustedZ = 127 - z;
                            }
                        }
                        //ChatUtils.info("Add material for: " + mapCorner.add(x + lineBonus, 0, adjustedZ).toShortString());
                        Item material = map[adjustedX][adjustedZ].asItem();
                        if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                        requiredItems.put(material, requiredItems.get(material) + 1);
                        //Check if the item fits into inventory. If not, undo the last increment and return
                        if (Utils.stacksRequired(requiredItems.values()) > availableSlots.size()) {
                            requiredItems.put(material, requiredItems.get(material) - 1);
                            return requiredItems;
                        }
                    }
                }
            }
            northToSouth = !northToSouth;
        }
        return requiredItems;
    }

    private void swapIntoHotbar(int slot) {
        Map<Item, Integer> itemSlot = new HashMap<>();
        Map<Item, Integer> blocksUntilItemUse = new HashMap<>();
        Map<Item, Integer> itemFrequency = new HashMap<>();

        int targetSlot = availableHotBarSlots.get(0);

        // Scan hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemSlot.put(item, hotbarSlot);
                blocksUntilItemUse.put(item, -1); // -1 = never used
                itemFrequency.put(item, 0);
            } else {
                targetSlot = hotbarSlot;
                break;
            }
        }

        // PRIORITY 1: empty slot → instant choice
        if (mc.player.getInventory().getItem(targetSlot).isEmpty()) {
            Utils.performSwap(slot, targetSlot);
            return;
        }

        // Get blocks until next use of items in hotbar
        int blockCounter = 0;
        boolean northToSouth = startNorthToSouth.get();
        boolean hasFoundAir = false;
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += linesPerRun.get()) {
            if (!Utils.isInInterval(workingInterval, x)) continue;

            for (int z = 0; z < 128; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    int adjustedZ = z;
                    if (!northToSouth) adjustedZ = 127 - z;
                    if (!Utils.isInInterval(workingInterval, adjustedX)) break;

                    blockCounter++;
                    BlockState state = MapAreaCache.getCachedBlockState(mapCorner.offset(adjustedX, 0, adjustedZ));
                    if (state.isAir()) {
                        if (!hasFoundAir) {
                            hasFoundAir = true;
                            BlockState oppositeBlockState = MapAreaCache.getCachedBlockState(mapCorner.offset(adjustedX, 0, 127 - adjustedZ));
                            // If the first air block does not have an opposite air block, the snake pattern got reversed at some point
                            // We reverse the search too
                            if (!oppositeBlockState.isAir() && z < 64) {
                                northToSouth = !northToSouth;
                                adjustedZ = 127 - z;
                            }
                        }

                        Block block = map[adjustedX][adjustedZ];
                        if (block == null) continue;

                        Item item = block.asItem();

                        if (blocksUntilItemUse.containsKey(item) &&
                            blocksUntilItemUse.get(item) == -1) {
                            blocksUntilItemUse.put(item, blockCounter);
                        }
                    }
                }
            }
            northToSouth = !northToSouth;
        }

        // Count frequency of items in hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
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
            int distance = blocksUntilItemUse.get(item); // -1 = never used
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

    public void setInterval(Tuple<Integer, Integer> interval) {
        workingInterval = interval;
    }

    public void addError(BlockPos relativeBlockPos) {
        BlockPos absoluteErrorPos = mapCorner.offset(relativeBlockPos);
        if (!knownErrors.contains(absoluteErrorPos)) knownErrors.add(absoluteErrorPos);
    }

    public void pause() {
        if (!state.equals(CarpetPrinter.State.AwaitSlaveContinue)) {
            oldState = state;
            state = CarpetPrinter.State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
        }
    }

    public void start() {
        if (availableSlots.isEmpty() || state.equals(State.AwaitSlaveNextMap)) {
            state = State.AwaitNBTFile;
            return;
        }
        if (state.equals(State.AwaitSlaveContinue)) {
            state = oldState;
        }
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {
    }

    public void mineLine(int lines) {
    }

    public void slaveFinished(String slave) {
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
        if (reset == null || cartographyTable == null || finishedMapChest == null || dumpStation == null || mapCorner == null || materialDict.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "carpet",
                reset,
                cartographyTable,
                finishedMapChest,
                mapMaterialChests,
                dumpStation,
                mapCorner,
                materialDict);
            Component configText = Component.literal(configFile.getName())
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully saved config to: ").append(configText));
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
            State.SelectingReset,
            State.SelectingChests,
            State.SelectingFinishedMapChest,
            State.SelectingDumpStation,
            State.SelectingTable,
            State.SelectingMapArea,
            State.AwaitRegisterResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return false;
        }

        try {
            ConfigDeserializer.ConfigData data =
                ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("carpet")) {
                error("Config file is of type " + data.type + " and not 'carpet'.");
                return false;
            }
            if (data.reset == null || data.cartographyTable == null || data.finishedMapChest == null || data.dumpStation == null || data.mapCorner == null || data.materialDict.isEmpty()) {
                error("Config file is missing required data.");
                return false;
            }
            this.reset = data.reset;
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStation = data.dumpStation;
            this.mapCorner = data.mapCorner;
            MapAreaCache.reset(mapCorner);
            this.materialDict = data.materialDict;
            Component configText = Component.literal(configFile.getName())
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully loaded config: ").append(configText));
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
            info("Building: §a" + mapFile.getName());
            NbtAccounter sizeTracker = new NbtAccounter(0x20000000L, 100);
            CompoundTag nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            ListTag paletteList = (ListTag) nbt.get("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            //Remove any blocks that should be ignored
            List<Integer> toBeRemoved = new ArrayList<>();
            for (int key : blockPaletteDict.keySet()) {
                if (ignoredBlocks.get().contains(blockPaletteDict.get(key).getA())) toBeRemoved.add(key);
            }
            for (int key : toBeRemoved) blockPaletteDict.remove(key);

            ListTag blockList = (ListTag) nbt.get("blocks");
            map = Utils.generateMapArray(blockList, blockPaletteDict);

            info("Requirements: ");
            for (Tuple<Block, Integer> p : blockPaletteDict.values()) {
                info(p.getA().getName().getString() + ": " + p.getB());
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
                new File(configFolder, "carpet-printer-config.json").getAbsolutePath(),
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
                new File(configFolder, "carpet-printer-config.json").getAbsolutePath(),
                null,
                null,
                false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);

        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, false);
        slaveController.rebuild();

        SlaveSystem.tableController = slaveController;
        return list;
    }

    @Override
    public String getInfoString() {
        if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mapCorner == null || !render.get()) return;
        event.renderer.box(mapCorner, color.get(), color.get(), ShapeMode.Lines, 0);
        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX() + 128, mapCorner.getY(), mapCorner.getZ() + 128, color.get(), color.get(), ShapeMode.Lines, 0);

        ArrayList<Tuple<BlockPos, Vec3>> renderedPairs = new ArrayList<>();
        for (ArrayList<Tuple<BlockPos, Vec3>> list : materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        for (Tuple<BlockPos, Vec3> pair : renderedPairs) {
            if (renderChestPositions.get())
                event.renderer.box(pair.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3 openPos = pair.getB();
                event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            for (Tuple<Vec3, Tuple<String, BlockPos>> pair : checkpoints) {
                Vec3 cp = pair.getA();
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.x() + indicatorSize.get(), cp.y() + indicatorSize.get(), cp.z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (reset != null) {
                event.renderer.box(reset.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(reset.getB().x - indicatorSize.get(), reset.getB().y - indicatorSize.get(), reset.getB().z - indicatorSize.get(), reset.getB().x() + indicatorSize.get(), reset.getB().y() + indicatorSize.get(), reset.getB().z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getB().x - indicatorSize.get(), cartographyTable.getB().y - indicatorSize.get(), cartographyTable.getB().z - indicatorSize.get(), cartographyTable.getB().x() + indicatorSize.get(), cartographyTable.getB().y() + indicatorSize.get(), cartographyTable.getB().z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStation != null) {
                event.renderer.box(dumpStation.getA().x - indicatorSize.get(), dumpStation.getA().y - indicatorSize.get(), dumpStation.getA().z - indicatorSize.get(), dumpStation.getA().x() + indicatorSize.get(), dumpStation.getA().y() + indicatorSize.get(), dumpStation.getA().z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getB().x - indicatorSize.get(), finishedMapChest.getB().y - indicatorSize.get(), finishedMapChest.getB().z - indicatorSize.get(), finishedMapChest.getB().x() + indicatorSize.get(), finishedMapChest.getB().y() + indicatorSize.get(), finishedMapChest.getB().z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    // Enums

    private enum State {
        SelectingReset,
        SelectingChests,
        SelectingFinishedMapChest,
        SelectingDumpStation,
        SelectingTable,
        SelectingMapArea,
        AwaitRegisterResponse,
        AwaitRestockResponse,
        AwaitResetResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitCartographyResponse,
        AwaitBlockBreak,
        AwaitAreaClear,
        AwaitNBTFile,
        AwaitMasterAllBuilt,
        AwaitSlaveContinue,
        AwaitSlaveNextMap,
        Walking,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum ErrorAction {
        Ignore,
        ToggleOff,
        Reset,
        Repair
    }
}
