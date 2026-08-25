package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.interfaces.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import java.util.ArrayList;

public class MapNamer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> startX = sgGeneral.add(new IntSetting.Builder()
        .name("start-x")
        .description("The starting x index from which the enumeration should begin.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> endX = sgGeneral.add(new IntSetting.Builder()
        .name("end-x")
        .description("The maximum x value of the map.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> startY = sgGeneral.add(new IntSetting.Builder()
        .name("start-y")
        .description("The starting y index from which the enumeration should begin.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> endY = sgGeneral.add(new IntSetting.Builder()
        .name("end-y")
        .description("The maximum y value of the map.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    public final Setting<String> mapName = sgGeneral.add(new StringSetting.Builder()
        .name("map-name")
        .description("The name for the map items.")
        .defaultValue("map-name_")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    public final Setting<String> separator = sgGeneral.add(new StringSetting.Builder()
        .name("separator")
        .description("The separator between the x and y index.")
        .defaultValue("_")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<Integer> renameDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rename-delay")
        .description("The delay between the renaming of maps in ticks.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Order> order = sgGeneral.add(new EnumSetting.Builder<Order>()
        .name("order")
        .description("The order in which the maps are named. Slot = Hotbar+Inventory left to right.")
        .defaultValue(Order.Slot)
        .build()
    );

    public MapNamer() {
        super(Addon.CATEGORY, "map-namer", "Automatically names maps in the inventory using the format: Map-Name + X + Separator + Y.");
    }

    ArrayList<Integer> mapSlots;
    State state;
    int ticks;
    int currentX;
    int currentY;

    @Override
    public void onActivate() {
        mapSlots = new ArrayList<>();
        state = State.AwaitInteract;
        ticks = 0;
        currentX = -1;
        currentY = -1;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.AwaitInteract && event.packet instanceof ServerboundUseItemOnPacket packet) {
            BlockPos blockPos = packet.getHitResult().getBlockPos();
            if (mc.level.getBlockState(blockPos).getBlock() instanceof AnvilBlock) {
                state = State.AwaitScreen;
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == State.HandleMaps && event.packet instanceof ClientboundContainerClosePacket) {
            info("Inventory screen closed. Interact with an anvil.");
            state = State.AwaitInteract;
            return;
        }
        if (state != State.AwaitScreen) return;
        if (event.packet instanceof ServerboundRenameItemPacket packet && !packet.getName().startsWith(mapName.get())) {
            event.cancel();
        }
        if (!(event.packet instanceof ClientboundContainerSetContentPacket packet)) return;

        if (startX.get() > endX.get() || startY.get() > endY.get()) {
            warning("Start index is larger than end index.");
            return;
        }
        mapSlots.clear();
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            int adjustedSlot = slot;
            if (order.get() == Order.ReversedSlot) {
                adjustedSlot = mc.player.getInventory().getContainerSize() - slot - 1;
            }
            ItemStack itemStack = mc.player.getInventory().getItem(adjustedSlot);
            if (itemStack.getItem() == Items.FILLED_MAP) {
                // info("Map Name: " + itemStack.getName().getString());
                if (itemStack.getHoverName().getString().equals("Map")) {
                    if (adjustedSlot < 9) {  //Stupid slot correction
                        adjustedSlot += 30;
                    } else {
                        adjustedSlot -= 6;
                    }
                    mapSlots.add(adjustedSlot);
                }
            }
        }
        ticks = renameDelay.get();
        state = State.HandleMaps;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticks == 0 || state != State.HandleMaps) return;
        ticks--;
        if (ticks == 0 && !mapSlots.isEmpty()) {
            if (mc.player.experienceLevel < 1) {
                info("Not enough XP.");
                state = State.AwaitInteract;
                if (mc.gui.screen() != null) mc.player.closeContainer();
                return;
            }
            int slot = mapSlots.remove(0);
            if (currentX == -1) currentX = startX.get();
            if (currentY == -1) currentY = startY.get();
            // info("Process map: " + slot + " with x: " + startX.get() + ", y: " + startY.get());

            IClientPlayerInteractionManager cim = (IClientPlayerInteractionManager) mc.gameMode;
            cim.handleContainerInput(mc.player.containerMenu.containerId, slot, 1, ContainerInput.QUICK_MOVE, mc.player);
            String newMapName = mapName.get() + currentX + separator + currentY;
            mc.getConnection().send(new ServerboundRenameItemPacket(newMapName));
            cim.handleContainerInput(mc.player.containerMenu.containerId, 2, 1, ContainerInput.QUICK_MOVE, mc.player);

            currentY++;
            if (currentY > endY.get()) {
                currentY = 0;
                currentX++;
                if (currentX > endX.get()) {
                    if (mapSlots.isEmpty()) {
                        info("Complete map was successfully named.");
                        startX.set(0);
                        startY.set(0);
                    } else {
                        info("More maps found than with endX and endY described.");
                    }
                    if (mc.gui.screen() != null) mc.player.closeContainer();
                    toggle();
                    return;
                }
            }

            if (mapSlots.isEmpty()) {
                startX.set(currentX);
                startY.set(currentY);
                state = State.AwaitInteract;
                info("All maps in inventory named. Progress (x: " + currentX + ", y: " + currentY + ") saved. " +
                    "Interact with an anvil with the next batch in the inventory.");
                if (mc.gui.screen() != null) mc.player.closeContainer();
            } else {
                ticks = renameDelay.get();
            }
        }
    }

    private enum State {
        AwaitInteract,
        AwaitScreen,
        HandleMaps
    }

    private enum Order {
        Slot,
        ReversedSlot
        //MapID,
        //ReversedMapID
    }
}
