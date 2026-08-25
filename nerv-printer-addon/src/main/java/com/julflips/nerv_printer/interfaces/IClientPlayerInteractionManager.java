package com.julflips.nerv_printer.interfaces;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

public interface IClientPlayerInteractionManager {
    void setBlockBreakingCooldown(int cooldown);

    float getCurrentBreakingProgress();

    void handleContainerInput(int syncId, int slotId, int button, ContainerInput actionType, Player player);

    void handleContainerInputWithForcedFullSync(
        int syncId,
        int slotId,
        int button,
        ContainerInput actionType,
        Player player
    );
}
