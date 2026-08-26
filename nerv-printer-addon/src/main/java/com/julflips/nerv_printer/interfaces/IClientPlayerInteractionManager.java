package com.julflips.nerv_printer.interfaces;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

public interface IClientPlayerInteractionManager {
    void setBlockBreakingCooldown(int cooldown);

    float getCurrentBreakingProgress();

    void clickSlot(int syncId, int slotId, int button, ContainerInput actionType, Player player);

    void clickSlotWithForcedFullSync(
        int syncId,
        int slotId,
        int button,
        ContainerInput actionType,
        Player player
    );
}
