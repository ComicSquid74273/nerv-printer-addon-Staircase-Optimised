package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockReachWindowTest {
    @Test
    void fiveBlockReachMarginStillCoversASecondFollowingPair() {
        assertTrue(
            BlockReachWindow.find(
                new BlockReachWindow.Cell(4, 64, 0),
                List.of(
                    new BlockReachWindow.Cell(0, 64, 0)
                ),
                1.62,
                4.8
            ).isPresent()
        );
    }

    @Test
    void findsEveryConservativeSupportCenterWithinFiveBlocks() {
        BlockReachWindow.Window window = BlockReachWindow.find(
            new BlockReachWindow.Cell(0, 100, 4),
            List.of(
                new BlockReachWindow.Cell(3, 100, 0),
                new BlockReachWindow.Cell(3, 100, 2),
                new BlockReachWindow.Cell(3, 100, 4),
                new BlockReachWindow.Cell(3, 100, 6),
                new BlockReachWindow.Cell(3, 100, 8)
            ),
            1.62,
            5.0
        ).orElseThrow();

        assertEquals(1, window.firstSupportIndex());
        assertEquals(3, window.lastSupportIndex());
        assertEquals(
            List.of(1, 2, 3),
            window.reachableSupportIndices()
        );
    }

    @Test
    void rejectsTargetThatNoLaterSupportCanReach() {
        assertTrue(
            BlockReachWindow.find(
                new BlockReachWindow.Cell(0, 100, 0),
                List.of(
                    new BlockReachWindow.Cell(5, 100, 0),
                    new BlockReachWindow.Cell(6, 100, 0)
                ),
                1.62,
                5.0
            ).isEmpty()
        );
    }

    @Test
    void accountsForVerticalDifferenceAndValidatesMeasurements() {
        assertTrue(
            BlockReachWindow.find(
                new BlockReachWindow.Cell(0, 108, 0),
                List.of(new BlockReachWindow.Cell(0, 100, 0)),
                1.62,
                5.0
            ).isEmpty()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> BlockReachWindow.find(
                new BlockReachWindow.Cell(0, 0, 0),
                List.of(),
                0.0,
                5.0
            )
        );
    }
}
