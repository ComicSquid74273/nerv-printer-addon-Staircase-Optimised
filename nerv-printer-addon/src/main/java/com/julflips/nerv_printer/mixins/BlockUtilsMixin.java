package com.julflips.nerv_printer.mixins;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.isClickable;

@Mixin(value = BlockUtils.class, remap = false)
public class BlockUtilsMixin {

    @Inject(method = "isClickable", at = @At("HEAD"), cancellable = true)
    private static void injectedIsClickable(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof CartographyTableBlock) {
            cir.setReturnValue(true);
        }
    }

    //Fixing meteors garbo code
    @Inject(method = "getPlaceSide", at = @At("HEAD"), cancellable = true)
    private static void injectedGetPlaceSide(BlockPos blockPos, CallbackInfoReturnable<Direction> cir) {
        ArrayList<Direction> placeableDirections = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.relative(side);
            BlockState state = mc.level.getBlockState(neighbor);

            // Check if neighbour isn't empty
            if (state.isAir() || isClickable(state.getBlock())) continue;

            // Check if neighbour is a fluid
            if (!state.getFluidState().isEmpty()) continue;
            placeableDirections.add(side);
        }

        //Get the direction the player is looking at
        Vec3 lookVec = Vec3.atCenterOf(blockPos).subtract(mc.player.getEyePosition());
        //List of direction and their significance (a larger score means the player is looking more in that direction)
        List<Tuple<Direction, Double>> directionSignificance = Arrays.asList(
            new Tuple<>(Direction.WEST, -lookVec.x()),
            new Tuple<>(Direction.EAST, lookVec.x()),
            new Tuple<>(Direction.DOWN, -lookVec.y()),
            new Tuple<>(Direction.UP, lookVec.y()),
            new Tuple<>(Direction.NORTH, -lookVec.z()),
            new Tuple<>(Direction.SOUTH, lookVec.z())
        );

        // Sort the list descending based on the significance of the direction
        Collections.sort(directionSignificance, (pair1, pair2) -> Double.compare(pair2.getB(), pair1.getB()));

        //Return the direction the player is looking at the most and has a placeable neighbour
        for (Tuple<Direction, Double> pair : directionSignificance) {
            if (placeableDirections.contains(pair.getA())) {
                cir.setReturnValue(pair.getA());
                return;
            }
        }

        cir.setReturnValue(null);
    }
}
