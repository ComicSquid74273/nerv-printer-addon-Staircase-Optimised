package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterSideLanePlannerTest {
    @Test
    void laneClearsTargetAndExpandedMountedEnvelope() {
        assertEquals(
            1.55,
            RasterSideLanePlanner.minimumOffset(0.95),
            1.0e-9
        );
        assertEquals(
            12.05,
            RasterSideLanePlanner.laneX(10.5, 1, 1.55),
            1.0e-9
        );
    }

    @Test
    void supportsNegativeXAndDifferentMountedWidths() {
        assertEquals(8.95, RasterSideLanePlanner.laneX(
            10.5,
            -1,
            RasterSideLanePlanner.minimumOffset(0.95)
        ), 1.0e-9);
        assertEquals(1.30, RasterSideLanePlanner.minimumOffset(0.70), 1.0e-9);
        assertEquals(1.85, RasterSideLanePlanner.minimumOffset(1.25), 1.0e-9);
    }

    @Test
    void adjacentPrintableRowAddsOneFullBlockOfClearance() {
        assertEquals(
            2.5375,
            RasterSideLanePlanner.minimumAdjacentRowOffset(0.9375),
            1.0e-9
        );
        assertEquals(
            54.9625,
            RasterSideLanePlanner.laneX(
                57.5,
                -1,
                RasterSideLanePlanner.minimumAdjacentRowOffset(0.9375)
            ),
            1.0e-9
        );
    }

    @Test
    void outwardScanSelectsAndLatchesTheNearestQuarterStep() {
        var selected = RasterSideLanePlanner.nearestOutwardOffset(
            1.55,
            offset -> offset >= 2.05
        );
        assertTrue(selected.isPresent());
        assertEquals(2.05, selected.orElseThrow(), 1.0e-9);
    }

    @Test
    void outwardScanFailsWhenNoReachableLaneRemains() {
        assertTrue(RasterSideLanePlanner.nearestOutwardOffset(
            5.80,
            offset -> false
        ).isEmpty());
    }

    @Test
    void absoluteReachNeverRoundsUpToSixBlocks() {
        assertTrue(RasterSideLanePlanner.withinAbsoluteReach(
            0.0, 0.0, 0.0,
            5.90, 0.0, 0.0
        ));
        assertFalse(RasterSideLanePlanner.withinAbsoluteReach(
            0.0, 0.0, 0.0,
            5.91, 0.0, 0.0
        ));
    }

    @Test
    void strictArrivalRejectsTheObservedSevenTenthsDrift() {
        assertFalse(RasterSideLanePlanner.arrived(
            -41.5, -25.4875, -211.2,
            -41.5, -25.4875, -210.5
        ));
        assertTrue(RasterSideLanePlanner.arrived(
            -41.55, -25.50, -210.55,
            -41.5, -25.4875, -210.5
        ));
    }

    @Test
    void fartherOutwardPositionsRemainInTheSameLane() {
        assertTrue(RasterSideLanePlanner.atOrOutwardOfLane(
            56.4625, 56.9625, -1, 0.25
        ));
        assertFalse(RasterSideLanePlanner.atOrOutwardOfLane(
            57.2125, 56.9625, -1, 0.10
        ));
        assertTrue(RasterSideLanePlanner.atOrOutwardOfLane(
            63.5, 63.0, 1, 0.25
        ));
        assertFalse(RasterSideLanePlanner.atOrOutwardOfLane(
            62.75, 63.0, 1, 0.10
        ));
    }

    @Test
    void sameLaneRecoveryRejectsCrossMapLaunchButAcceptsLocalOvershoot() {
        assertTrue(RasterSideLanePlanner.canContinueAlongLane(
            56.4625, 56.9625, -1, 0.25
        ));
        assertFalse(RasterSideLanePlanner.canContinueAlongLane(
            -66.9456, 56.9625, -1, 0.25
        ));
        assertTrue(RasterSideLanePlanner.canContinueAlongLane(
            63.4625, 62.9625, 1, 0.25
        ));
        assertFalse(RasterSideLanePlanner.canContinueAlongLane(
            70.0, 62.9625, 1, 0.25
        ));
    }

    @Test
    void passedExteriorShiftStillRequiresSafeOutwardX() {
        assertFalse(RasterSideLanePlanner.canAdvanceExteriorLaneShift(
            true, 57.2125, 56.9625, -1
        ));
        assertTrue(RasterSideLanePlanner.canAdvanceExteriorLaneShift(
            true, 56.4625, 56.9625, -1
        ));
        assertFalse(RasterSideLanePlanner.canAdvanceExteriorLaneShift(
            false, 56.4625, 56.9625, -1
        ));
        assertTrue(RasterSideLanePlanner.canAdvanceExteriorLaneShift(
            true, 63.4625, 62.9625, 1
        ));
    }
}
