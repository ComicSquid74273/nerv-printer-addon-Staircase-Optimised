package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterHeightBandPlannerTest {
    @Test
    void splitsTheObservedNinetySixBlockFrontierCliff() {
        var clusters = RasterHeightBandPlanner.create(
            List.of(
                new RasterHeightBandPlanner.Lane(127, 97),
                new RasterHeightBandPlanner.Lane(126, 97),
                new RasterHeightBandPlanner.Lane(125, 1)
            ),
            5.0
        );

        assertEquals(2, clusters.size());
        assertEquals(List.of(127, 126), clusters.getFirst().lanes().stream()
            .map(RasterHeightBandPlanner.Lane::coordinate).toList());
        assertEquals(97, clusters.getFirst().safeHeight());
        assertEquals(List.of(125), clusters.getLast().lanes().stream()
            .map(RasterHeightBandPlanner.Lane::coordinate).toList());
        assertEquals(1, clusters.getLast().safeHeight());
    }

    @Test
    void retainsAReachableThreeWideBand() {
        var clusters = RasterHeightBandPlanner.create(
            List.of(
                new RasterHeightBandPlanner.Lane(7, 10),
                new RasterHeightBandPlanner.Lane(6, 11),
                new RasterHeightBandPlanner.Lane(5, 9)
            ),
            5.0
        );
        assertEquals(1, clusters.size());
        assertEquals(6, clusters.getFirst().centerCoordinate());
        assertEquals(9, clusters.getFirst().safeHeight());
    }

    @Test
    void lookaheadCannotCrossAHeightClusterBoundary() {
        assertTrue(RasterHeightBandPlanner.sameLookaheadEnvelope(10.0, 11.0, 1.0));
        assertFalse(RasterHeightBandPlanner.sameLookaheadEnvelope(97.0, 1.0, 1.0));
    }
}
