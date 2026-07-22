package com.comicsquid.autoprt.mixin;

import com.comicsquid.autoprt.AutoPortalResume;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    /**
     * After keyboard + options populate {@link Input#playerInput}, override with Baritone-stuck recovery
     * so forward + jump apply together before movement runs.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void comicAutoPortal$afterKeyboardTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.input != (Input) (Object) this) return;
        AutoPortalResume.applyRecoveryClientInput((Input) (Object) this);
    }
}
