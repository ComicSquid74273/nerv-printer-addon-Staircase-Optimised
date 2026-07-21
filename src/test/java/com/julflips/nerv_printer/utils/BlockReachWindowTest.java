package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockReachWindowTest {
    @Test
    void adjacentFlatUCanRemoveItsNeighborFromEndToStartMonotonically() {
        List<BlockReachWindow.Cell> hostSupports = List.of(
            new BlockReachWindow.Cell(0, 64, 0),
            new BlockReachWindow.Cell(0, 64, 1),
            new BlockReachWindow.Cell(0, 64, 2),
            new BlockReachWindow.Cell(0, 64, 3),
            new BlockReachWindow.Cell(1, 64, 3),
            new BlockReachWindow.Cell(1, 64, 2),
            new BlockReachWindow.Cell(1, 64, 1),
            new BlockReachWindow.Cell(1, 64, 0)
        );
        List<BlockReachWindow.Cell> neighborEndToStart = List.of(
            new BlockReachWindow.Cell(3, 64, 0),
            new BlockReachWindow.Cell(3, 64, 1),
            new BlockReachWindow.Cell(3, 64, 2),
            new BlockReachWindow.Cell(3, 64, 3),
            new BlockReachWindow.Cell(2, 64, 3),
            new BlockReachWindow.Cell(2, 64, 2),
            new BlockReachWindow.Cell(2, 64, 1),
            new BlockReachWindow.Cell(2, 64, 0)
        );

        int previousSupport = 0;
        ArrayList<Integer> schedule = new ArrayList<>();
        for (BlockReachWindow.Cell target : neighborEndToStart) {
            int minimum = previousSupport;
            int support = BlockReachWindow.find(
                target,
                hostSupports,
                1.62,
                4.8
            ).orElseThrow().reachableSupportIndices().stream()
                .filter(index -> index >= minimum)
                .findFirst()
                .orElseThrow();
            schedule.add(support);
            previousSupport = support;
        }

        assertEquals(8, schedule.size());
        for (int index = 1; index < schedule.size(); index++) {
            assertTrue(schedule.get(index) >= schedule.get(index - 1));
        }
    }

    @Test
    void risingRouteSupportCanCloseAFormerlyValidTrailingWindow() {
        BlockReachWindow.Cell target =
            new BlockReachWindow.Cell(0, 102, 0);

        assertTrue(
            BlockReachWindow.find(
                target,
                List.of(new BlockReachWindow.Cell(0, 102, 4)),
                1.62,
                4.8
            ).isPresent()
        );
        assertTrue(
            BlockReachWindow.find(
                target,
                List.of(new BlockReachWindow.Cell(0, 104, 4)),
                1.62,
                4.8
            ).isEmpty()
        );
    }

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
    void distinguishesThinCenterOpportunityFromWholeCellGuarantee() {
        BlockReachWindow.Cell target =
            new BlockReachWindow.Cell(0, 100, 4);
        List<BlockReachWindow.Cell> supports = List.of(
            new BlockReachWindow.Cell(2, 100, 0),
            new BlockReachWindow.Cell(2, 100, 1)
        );

        assertEquals(
            List.of(0, 1),
            BlockReachWindow.find(
                target,
                supports,
                1.62,
                5.0
            ).orElseThrow().reachableSupportIndices()
        );
        assertEquals(
            List.of(1),
            BlockReachWindow.findGuaranteedFromSupportCell(
                target,
                supports,
                1.62,
                5.0
            ).orElseThrow().reachableSupportIndices()
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
