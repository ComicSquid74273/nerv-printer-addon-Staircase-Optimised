package com.julflips.nerv_printer.mixins;

import com.julflips.nerv_printer.interfaces.IClientPlayerInteractionManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = MultiPlayerGameMode.class, priority = 1002)
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
    @Unique
    private boolean nerv$forceNextClickFullSync;

    @Shadow
    private float destroyProgress;

    @Shadow
    private int destroyDelay;

    @Shadow
    public abstract void handleContainerInput(int syncId, int slotId, int button, ContainerInput actionType, Player player);

    @Override
    public void handleContainerInputWithForcedFullSync(
        int syncId,
        int slotId,
        int button,
        ContainerInput actionType,
        Player player
    ) {
        if (nerv$forceNextClickFullSync) {
            throw new IllegalStateException(
                "A forced-full-sync inventory click is already active."
            );
        }
        nerv$forceNextClickFullSync = true;
        try {
            handleContainerInput(syncId, slotId, button, actionType, player);
        } finally {
            nerv$forceNextClickFullSync = false;
        }
    }

    /**
     * Vanilla predicts the click before sending its changed-slot hashes. With
     * a matching revision the server adopts those hashes as its remote shadow,
     * so a correct prediction can legitimately produce no S2C slot packet.
     * Keeping the exact predicted payload but forcing a stale revision makes
     * the server execute the click and then send one authoritative full state.
     */
    @Redirect(
        method = "handleContainerInput",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void nerv$sendInventoryClick(
        ClientPacketListener networkHandler,
        Packet<?> packet
    ) {
        if (!nerv$forceNextClickFullSync
            || !(packet instanceof ServerboundContainerClickPacket click)) {
            networkHandler.send(packet);
            return;
        }

        // Clear before sending so another hook cannot recursively rewrite it.
        nerv$forceNextClickFullSync = false;
        networkHandler.send(
            new ServerboundContainerClickPacket(
                click.containerId(),
                -1,
                click.slotNum(),
                click.buttonNum(),
                click.containerInput(),
                click.changedSlots(),
                click.carriedItem()
            )
        );
    }

    @Override
    public void setBlockBreakingCooldown(int cooldown) {
        destroyDelay = cooldown;
    }

    @Override
    public float getCurrentBreakingProgress() {
        return destroyProgress;
    }
}
