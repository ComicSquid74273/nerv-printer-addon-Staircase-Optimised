package com.julflips.nerv_printer.mixins;

import com.julflips.nerv_printer.interfaces.IClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 1002)
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
    @Unique
    private boolean nerv$forceNextClickFullSync;

    @Shadow
    private float currentBreakingProgress;

    @Shadow
    private int blockBreakingCooldown;

    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

    @Override
    public void clickSlotWithForcedFullSync(
        int syncId,
        int slotId,
        int button,
        SlotActionType actionType,
        PlayerEntity player
    ) {
        if (nerv$forceNextClickFullSync) {
            throw new IllegalStateException(
                "A forced-full-sync inventory click is already active."
            );
        }
        nerv$forceNextClickFullSync = true;
        try {
            clickSlot(syncId, slotId, button, actionType, player);
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
        method = "clickSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
        )
    )
    private void nerv$sendInventoryClick(
        ClientPlayNetworkHandler networkHandler,
        Packet<?> packet
    ) {
        if (!nerv$forceNextClickFullSync
            || !(packet instanceof ClickSlotC2SPacket click)) {
            networkHandler.sendPacket(packet);
            return;
        }

        // Clear before sending so another hook cannot recursively rewrite it.
        nerv$forceNextClickFullSync = false;
        networkHandler.sendPacket(
            new ClickSlotC2SPacket(
                click.syncId(),
                -1,
                click.slot(),
                click.button(),
                click.actionType(),
                click.modifiedStacks(),
                click.cursor()
            )
        );
    }

    @Override
    public void setBlockBreakingCooldown(int cooldown) {
        blockBreakingCooldown = cooldown;
    }

    @Override
    public float getCurrentBreakingProgress() {
        return currentBreakingProgress;
    }
}
