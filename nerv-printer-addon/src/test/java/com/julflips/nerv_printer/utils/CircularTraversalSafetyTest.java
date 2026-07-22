package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularTraversalSafetyTest {
    @Test
    void crossedMixedHeightCheckpointKeepsOrderedForwardSteering() {
        var point =
            CircularTraversalSafety.orderedForwardSteeringPoint(
                10.6,
                20.5,
                10.5,
                20.5,
                9.5,
                20.5,
                1.0
            );

        assertEquals(11.6, point.x(), 0.000_001);
        assertEquals(20.5, point.z());
    }

    @Test
    void crossedCheckpointSteeringRemainsAheadAfterLargeOvershoot() {
        var point =
            CircularTraversalSafety.orderedForwardSteeringPoint(
                15.6,
                20.5,
                10.5,
                20.5,
                9.5,
                20.5,
                1.0
            );

        assertEquals(16.6, point.x(), 0.000_001);
        assertTrue(point.x() > 15.6);
        assertEquals(20.5, point.z());
    }

    @Test
    void approachingCheckpointStillSteersToItsCenter() {
        var point =
            CircularTraversalSafety.orderedForwardSteeringPoint(
                9.8,
                20.5,
                10.5,
                20.5,
                9.5,
                20.5,
                1.0
            );

        assertEquals(10.5, point.x());
        assertEquals(20.5, point.z());
    }

    @Test
    void generalWalkingBufferCannotMakeRemovalUnsafe() {
        assertEquals(0.15, CircularTraversalSafety.checkpointBuffer(1.0));
        assertEquals(0.15, CircularTraversalSafety.checkpointBuffer(0.2));
        assertEquals(0.1, CircularTraversalSafety.checkpointBuffer(0.1));
    }

    @Test
    void zeroCannotMakeCircularCheckpointsUnreachable() {
        assertEquals(0.05, CircularTraversalSafety.checkpointBuffer(0.0));
    }

    @Test
    void normalLongLineBufferCannotCutConnectorCorners() {
        assertEquals(
            0.35,
            CircularTraversalSafety.connectorCheckpointBuffer(0.8)
        );
        assertEquals(
            0.3,
            CircularTraversalSafety.connectorCheckpointBuffer(0.3)
        );
        assertEquals(
            0.2,
            CircularTraversalSafety.connectorCheckpointBuffer(0.0)
        );
    }

    @Test
    void legalStepHeightIsAcceptedWithoutWaitingForLanding() {
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(100, 101)
        );
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(101, 100)
        );
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(100.8, 101)
        );
    }

    @Test
    void anotherHelixLevelCannotAdvanceTheCursor() {
        assertFalse(
            CircularTraversalSafety.isConnectorStepHeightReachable(100, 103)
        );
        assertFalse(
            CircularTraversalSafety.isConnectorStepHeightReachable(
                Double.NaN,
                100
            )
        );
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularTraversalSafety.checkpointBuffer(Double.NaN)
        );
    }

    @Test
    void crossedCheckpointNeverTurnsAnOvershotStraightRouteBackward() {
        assertFalse(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.5, 1.49,
                0.5, 1.5,
                0.5, 0.5
            )
        );
        assertTrue(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.5, 1.5,
                0.5, 1.5,
                0.5, 0.5
            )
        );
        assertTrue(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.5, 1.8,
                0.5, 1.5,
                0.5, 0.5
            )
        );
    }

    @Test
    void crossedCheckpointWorksInBothDirectionsAndAroundTurns() {
        assertTrue(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.5, 0.2,
                0.5, 0.5,
                0.5, 1.5
            )
        );
        assertTrue(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                1.7, 0.5,
                1.5, 0.5,
                0.5, 0.5
            )
        );
        assertFalse(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                1.3, 0.5,
                1.5, 0.5,
                0.5, 0.5
            )
        );
        assertTrue(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.2, 0.5,
                0.5, 0.5,
                1.5, 0.5
            )
        );
    }

    @Test
    void crossedCheckpointRejectsDegenerateOrInvalidSegments() {
        assertFalse(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                0.5, 0.5,
                0.5, 0.5,
                0.5, 0.5
            )
        );
        assertFalse(
            CircularTraversalSafety.hasCrossedCheckpointCenter(
                Double.NaN, 0.5,
                1.5, 0.5,
                0.5, 0.5
            )
        );
    }

    @Test
    void miningCheckpointWaitsForLandingInsteadOfReversing() {
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.HOLD_FOR_LANDING,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                false,
                false,
                true
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.HOLD_FOR_LANDING,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                false,
                true,
                false
            )
        );
    }

    @Test
    void orderedStepUpRemainsEligibleAfterEnteringTargetCell() {
        assertTrue(
            CircularTraversalSafety.isOrderedStepUpTarget(
                1,
                156,
                157,
                1
            )
        );
        assertTrue(
            CircularTraversalSafety.isOrderedStepUpTarget(
                1,
                156,
                157,
                0
            )
        );
        assertFalse(
            CircularTraversalSafety.isOrderedStepUpTarget(
                0,
                156,
                157,
                0
            )
        );
        assertFalse(
            CircularTraversalSafety.isOrderedStepUpTarget(
                1,
                156,
                157,
                2
            )
        );
    }

    @Test
    void orderedStepUpKeepsMovingUntilStableLanding() {
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.APPROACHING,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                false,
                true,
                false,
                true
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.APPROACHING,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                false,
                false,
                true,
                true
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.REACHED,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                true,
                true,
                false,
                true
            )
        );
    }

    @Test
    void orderedStepUpOwnsJumpUntilTheLandingIsStable() {
        assertTrue(
            CircularTraversalSafety.shouldHoldOrderedStepUpJump(
                true,
                false,
                1,
                98,
                99,
                1
            )
        );
        assertTrue(
            CircularTraversalSafety.shouldHoldOrderedStepUpJump(
                true,
                false,
                1,
                98,
                99,
                0
            )
        );
        assertFalse(
            CircularTraversalSafety.shouldHoldOrderedStepUpJump(
                true,
                true,
                1,
                98,
                99,
                0
            )
        );
        assertFalse(
            CircularTraversalSafety.shouldHoldOrderedStepUpJump(
                false,
                false,
                1,
                98,
                99,
                1
            )
        );
    }

    @Test
    void miningCheckpointRequiresStableSupportBeforeItIsReached() {
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.REACHED,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                true,
                false,
                true
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.REACHED,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                true,
                true,
                false
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.APPROACHING,
            CircularTraversalSafety.miningCheckpointProgress(
                false,
                false,
                false,
                true
            )
        );
        assertEquals(
            CircularTraversalSafety.MiningCheckpointProgress.APPROACHING,
            CircularTraversalSafety.miningCheckpointProgress(
                true,
                true,
                false,
                false
            )
        );
    }

    @Test
    void orderedStepUpRejectsInvalidDistance() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularTraversalSafety.isOrderedStepUpTarget(
                -1,
                100,
                101,
                0
            )
        );
    }

    @Test
    void routeReversalDistinguishesRecoveryFromTurnsAndStraightWalking() {
        assertTrue(
            CircularTraversalSafety.isRouteReversal(
                0.5, 0.5,
                0.5, 1.5,
                0.5, 0.5
            )
        );
        assertFalse(
            CircularTraversalSafety.isRouteReversal(
                0.5, 0.5,
                0.5, 1.5,
                0.5, 2.5
            )
        );
        assertFalse(
            CircularTraversalSafety.isRouteReversal(
                0.5, 0.5,
                0.5, 1.5,
                1.5, 1.5
            )
        );
    }
}
