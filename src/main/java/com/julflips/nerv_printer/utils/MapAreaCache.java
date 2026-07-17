package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class MapAreaCache {
    private static BlockPos mapCorner = null;
    private static Map<ChunkPos, Chunk> cachedChunks = new HashMap<>();
    private static int minimumRelativeX = 0;
    private static int maximumRelativeX = 127;
    private static int minimumRelativeZ = 0;
    private static int maximumRelativeZ = 127;

    public static boolean isWithingMap(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        return relativePos.getX() >= minimumRelativeX
            && relativePos.getX() <= maximumRelativeX
            && relativePos.getZ() >= minimumRelativeZ
            && relativePos.getZ() <= maximumRelativeZ;
    }

    public static boolean isMapAreaClear() {
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = mc.world.getBlockState(mapCorner.add(x, 0, z));
                if (!blockState.isAir() || !blockState.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    public static void reset(BlockPos newCorner) {
        reset(newCorner, 0, 127, 0, 127);
    }

    public static void reset(
        BlockPos newCorner,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ
    ) {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Invalid map cache bounds.");
        }
        mapCorner = new BlockPos(newCorner);
        minimumRelativeX = minimumX;
        maximumRelativeX = maximumX;
        minimumRelativeZ = minimumZ;
        maximumRelativeZ = maximumZ;
        cachedChunks.clear();
    }

    public static BlockState getCachedBlockState(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        if (mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return mc.world.getBlockState(blockPos);
        }
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        if (cachedChunks.containsKey(chunkPos)) {
            Chunk chunk = cachedChunks.get(chunkPos);
            return chunk.getBlockState(blockPos);
        }
        ChatUtils.warning("Could not fetch Block at " + blockPos.toShortString() + ". Try loading the entire Map Area first.");
        return mc.world.getBlockState(blockPos);
    }

    public static boolean hasBlockData(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        return mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)
            || cachedChunks.containsKey(new ChunkPos(chunkX, chunkZ));
    }

    @EventHandler()
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (mapCorner != null && event.packet instanceof UnloadChunkS2CPacket packet) {
            BlockPos chunkCorner = packet.pos().getStartPos();
            BlockPos oppositeChunkCorner = chunkCorner.add(15, 0, 15);
            BlockPos relativeStart = chunkCorner.subtract(mapCorner);
            BlockPos relativeEnd = oppositeChunkCorner.subtract(mapCorner);
            boolean overlaps = relativeEnd.getX() >= minimumRelativeX
                && relativeStart.getX() <= maximumRelativeX
                && relativeEnd.getZ() >= minimumRelativeZ
                && relativeStart.getZ() <= maximumRelativeZ;
            if (overlaps) {
                cachedChunks.put(packet.pos(), mc.world.getChunk(packet.pos().getStartPos()));
            }
        }
    }
}
