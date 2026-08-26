package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterBandTraversalTest {
    @Test
    void keepsThreeLanesTogetherAndShiftsByThreeAtTurns() {
        var steps = RasterBandTraversal.create(0, 7, -1, 2, 3, true, true);
        assertEquals(java.util.List.of(0, 1, 2), steps.get(0).outerLanes());
        assertEquals(java.util.List.of(0, 1, 2), steps.get(3).outerLanes());
        assertEquals(java.util.List.of(3, 4, 5), steps.get(4).outerLanes());
        assertEquals(-1, steps.get(4).direction());
        assertEquals(java.util.List.of(6, 7), steps.get(8).outerLanes());
    }

    @Test
    void coversEveryCellExactlyOnceWithoutThreeTwoOneCollapse() {
        var steps = RasterBandTraversal.create(0, 7, -1, 2, 3, true, true);
        Set<String> cells = new HashSet<>();
        for (var step : steps) {
            for (int outer : step.outerLanes()) {
                cells.add(outer + ":" + step.inner());
            }
        }
        assertEquals(8 * 4, cells.size());
    }

    @Test
    void canForceEveryPrimaryBandNorthToSouth() {
        var steps = RasterBandTraversal.create(
            0, 5, -1, 2, 3, true, true, false
        );

        assertEquals(java.util.List.of(-1, 0, 1, 2), steps.stream()
            .filter(step -> step.band() == 0).map(RasterBandTraversal.Step::inner).toList());
        assertEquals(java.util.List.of(-1, 0, 1, 2), steps.stream()
            .filter(step -> step.band() == 1).map(RasterBandTraversal.Step::inner).toList());
        assertEquals(1, steps.getFirst().direction());
        assertEquals(1, steps.getLast().direction());
    }

    @Test
    void oneWideBandsKeepEveryNorthSouthLineIndependent() {
        var steps = RasterBandTraversal.create(
            0, 2, -1, 2, 1, true, true, false
        );

        assertEquals(12, steps.size());
        for (int band = 0; band < 3; band++) {
            int currentBand = band;
            int expectedLane = band;
            var line = steps.stream()
                .filter(step -> step.band() == currentBand)
                .toList();
            assertEquals(4, line.size());
            assertEquals(
                java.util.List.of(-1, 0, 1, 2),
                line.stream().map(RasterBandTraversal.Step::inner).toList()
            );
            assertEquals(
                java.util.List.of(expectedLane),
                line.getFirst().outerLanes()
            );
            assertEquals(1, line.getFirst().direction());
        }
    }
}
