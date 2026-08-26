package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RasterReachBarrierTest {
    @Test
    void limitsCruiseByTheRealHorizontalReach() {
        assertEquals(3, RasterReachBarrier.maximumLeadCells(4.5, 2.0, 0.5, 8));
    }

    @Test
    void steepAltitudeDifferenceForcesAStopAtTheFrontier() {
        assertEquals(0, RasterReachBarrier.maximumLeadCells(4.5, 4.5, 0.5, 8));
    }

    @Test
    void neverExceedsTheConfiguredLookaheadCap() {
        assertEquals(2, RasterReachBarrier.maximumLeadCells(6.0, 0.0, 0.25, 2));
    }

    @Test
    void rejectsInvalidGeometry() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RasterReachBarrier.maximumLeadCells(0.0, 0.0, 0.0, 1)
        );
    }
}
