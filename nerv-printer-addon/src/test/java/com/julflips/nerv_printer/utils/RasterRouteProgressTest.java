package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterRouteProgressTest {
    @Test
    void acceptsAWaypointCrossedBetweenFastMovementSamples() {
        assertTrue(RasterRouteProgress.reachedOrPassed(
            new RasterRouteProgress.Point(0.0, 0.0, 0.0),
            new RasterRouteProgress.Point(2.0, 0.0, 0.0),
            new RasterRouteProgress.Point(1.0, 0.0, 0.0),
            0.6
        ));
    }

    @Test
    void catchesUpAStaleCursorWhenBothSamplesArePastTheWaypoint() {
        assertTrue(RasterRouteProgress.passedOnSegment(
            new RasterRouteProgress.Point(0.0, 0.0, 0.0),
            new RasterRouteProgress.Point(3.0, 0.0, 0.0),
            new RasterRouteProgress.Point(1.0, 0.0, 0.0),
            0.10
        ));
    }

    @Test
    void staleCursorCatchUpRejectsAnUnrelatedParallelLane() {
        assertFalse(RasterRouteProgress.passedOnSegment(
            new RasterRouteProgress.Point(0.0, 0.0, 0.0),
            new RasterRouteProgress.Point(3.0, 0.0, 1.0),
            new RasterRouteProgress.Point(1.0, 0.0, 0.0),
            0.10
        ));
    }

    @Test
    void rejectsPassingFarToTheSideOfAWaypoint() {
        assertFalse(RasterRouteProgress.reachedOrPassed(
            new RasterRouteProgress.Point(0.0, 0.0, 2.0),
            new RasterRouteProgress.Point(2.0, 0.0, 2.0),
            new RasterRouteProgress.Point(1.0, 0.0, 0.0),
            0.6
        ));
    }

    @Test
    void exactArrivalDoesNotAcceptAServerCorrectionAcrossWaypoint() {
        var waypoint = new RasterRouteProgress.Point(0.0, -14.0, 0.0);
        var corrected = new RasterRouteProgress.Point(0.0, -37.0, 0.0);

        assertFalse(RasterRouteProgress.reached(corrected, waypoint, 0.85));
        assertTrue(RasterRouteProgress.reached(
            new RasterRouteProgress.Point(0.0, -14.4, 0.0),
            waypoint,
            0.85
        ));
    }

    @Test
    void exteriorDescentMustReachSafeYBeforeHorizontalHandoff() {
        var waypoint = new RasterRouteProgress.Point(-41.5, -37.5375, -324.5);
        var stillTooHigh = new RasterRouteProgress.Point(
            -41.5, -37.10, -324.5
        );

        assertTrue(RasterRouteProgress.reached(
            stillTooHigh, waypoint, 0.85
        ));
        assertFalse(RasterRouteProgress.reached(
            stillTooHigh, waypoint, 0.85, 0.10
        ));
        assertTrue(RasterRouteProgress.reached(
            new RasterRouteProgress.Point(-41.5, -37.49, -324.5),
            waypoint,
            0.85,
            0.10
        ));
    }

    @Test
    void underMapDoglegMustClearTheLowerAdjacentBlockBeforeAscending() {
        var waypoint = new RasterRouteProgress.Point(
            -41.5, -37.5375, -310.5
        );
        var widenedBoatStillOverlapsPreviousCell =
            new RasterRouteProgress.Point(-41.5, -37.5375, -311.2);

        assertTrue(RasterRouteProgress.reached(
            widenedBoatStillOverlapsPreviousCell,
            waypoint,
            0.85,
            0.10
        ));
        assertFalse(RasterRouteProgress.reached(
            widenedBoatStillOverlapsPreviousCell,
            waypoint,
            0.10,
            0.10
        ));
    }
}
