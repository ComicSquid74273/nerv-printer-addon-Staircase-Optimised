package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterRouteDeadlinePolicyTest {
    @Test
    void futureDeadlineDoesNotBlockAdjacentRouteMovement() {
        assertEquals(
            RasterRouteDeadlinePolicy.Decision.ADVANCE,
            RasterRouteDeadlinePolicy.decide(10, 11, false, false, false)
        );
    }

    @Test
    void submittedPlacementNeverBrakesForServerConfirmation() {
        assertEquals(
            RasterRouteDeadlinePolicy.Decision.PLACE_AND_HOLD,
            RasterRouteDeadlinePolicy.decide(10, 10, false, false, true)
        );
        assertEquals(
            RasterRouteDeadlinePolicy.Decision.ADVANCE,
            RasterRouteDeadlinePolicy.decide(10, 10, false, true, true)
        );
        assertEquals(
            RasterRouteDeadlinePolicy.Decision.ADVANCE,
            RasterRouteDeadlinePolicy.decide(10, 10, true, false, false)
        );
    }

    @Test
    void missedPastDeadlineUsesAboveRepairInsteadOfAWorldPath() {
        assertEquals(
            RasterRouteDeadlinePolicy.Decision.REPOSITION_SIDE_LANE,
            RasterRouteDeadlinePolicy.decide(12, 10, false, false, false)
        );
    }

    @Test
    void cliffDeadlineMovesOnlyToFirstReachableAdjacentPoint() {
        assertEquals(
            13,
            RasterRouteDeadlinePolicy.firstReachableDeadline(
                10,
                16,
                index -> index >= 13
            )
        );
        assertEquals(
            10,
            RasterRouteDeadlinePolicy.firstReachableDeadline(
                10,
                12,
                index -> false
            )
        );
    }

    @Test
    void deadlineRefinesNearbyRetainedPoseBeforeAboveRepair() {
        assertEquals(
            true,
            RasterRouteDeadlinePolicy.requiresExactPlacementPose(
                12,
                12,
                false,
                true
            )
        );
        assertEquals(
            false,
            RasterRouteDeadlinePolicy.requiresExactPlacementPose(
                13,
                12,
                false,
                true
            )
        );
    }
}
