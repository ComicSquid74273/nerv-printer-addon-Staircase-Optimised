package com.julflips.nerv_printer.utils;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftWalkableSupportSemanticsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void walkabilityUsesCollisionRatherThanMaterialProperties() {
        assertFalse(
            Blocks.REDSTONE_BLOCK.defaultBlockState()
                .isRedstoneConductor(
                    EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO
                )
        );
        assertFalse(
            Blocks.OAK_LEAVES.defaultBlockState().isFaceSturdy(
                EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO,
                net.minecraft.core.Direction.UP
            )
        );

        for (Block block : new Block[] {
            Blocks.REDSTONE_BLOCK,
            Blocks.OAK_LEAVES,
            Blocks.GLASS,
            Blocks.ICE
        }) {
            assertTrue(
                Block.isShapeFullBlock(
                    block.defaultBlockState().getCollisionShape(
                        EmptyBlockGetter.INSTANCE,
                        BlockPos.ZERO
                    )
                ),
                () -> block.getName().getString()
                    + " should be accepted from collision geometry"
            );
        }
        assertFalse(
            Block.isShapeFullBlock(
                Blocks.OAK_SLAB.defaultBlockState().getCollisionShape(
                    EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO
                )
            )
        );
    }
}
