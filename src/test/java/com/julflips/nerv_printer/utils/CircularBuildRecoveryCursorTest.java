package com.julflips.nerv_printer.utils;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildRecoveryCursorTest {
    @Test
    void choosesOneSafeShortestEgressDirection() {
        assertEquals(
            -1,
            CircularBuildRecoveryCursor.chooseDirection(
                true,
                3,
                true,
                8
            )
        );
        assertEquals(
            1,
            CircularBuildRecoveryCursor.chooseDirection(
                true,
                9,
                true,
                2
            )
        );
        assertEquals(
            1,
            CircularBuildRecoveryCursor.chooseDirection(
                false,
                1,
                true,
                9
            )
        );
    }

    @Test
    void backsOutTowardTheOutboundNorthEndpoint() {
        int index = 2;
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertEquals(1, index);
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertEquals(0, index);
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertTrue(CircularBuildRecoveryCursor.complete(index, 4));
    }

    @Test
    void continuesTowardTheReturnNorthEndpoint() {
        int index = 1;
        assertFalse(CircularBuildRecoveryCursor.complete(index, 4));
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        assertEquals(2, index);
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        assertTrue(CircularBuildRecoveryCursor.complete(index, 4));
    }

    @Test
    void rejectsInvalidCursorState() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.advance(0, 0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.advance(-1, 1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.complete(0, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.chooseDirection(
                false,
                1,
                false,
                1
            )
        );
    }

    @Test
    void resolvesCorrectedYFromTheRetainedHorizontalRouteCell() {
        List<BlockPos> supports = List.of(
            new BlockPos(10, 50, 20),
            new BlockPos(10, 51, 21),
            new BlockPos(10, 52, 22)
        );

        assertEquals(
            1,
            CircularBuildRecoveryCursor.resolveHorizontalSupport(
                supports,
                1,
                10.4,
                21.7
            ).orElseThrow()
        );
    }

    @Test
    void nearestRetainedCursorDisambiguatesAHelixCell() {
        List<BlockPos> supports = List.of(
            new BlockPos(5, 40, 5),
            new BlockPos(6, 40, 5),
            new BlockPos(5, 44, 5)
        );

        assertEquals(
            2,
            CircularBuildRecoveryCursor.resolveHorizontalSupport(
                supports,
                2,
                5.5,
                5.5
            ).orElseThrow()
        );
    }
}
