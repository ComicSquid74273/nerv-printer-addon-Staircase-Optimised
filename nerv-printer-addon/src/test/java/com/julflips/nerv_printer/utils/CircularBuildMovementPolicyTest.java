package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static com.julflips.nerv_printer.utils.CircularBuildMovementPolicy.HoldReason;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircularBuildMovementPolicyTest {
    @Test
    void ordinaryPlacementWorkDoesNotBlockWalking() {
        assertEquals(
            HoldReason.NONE,
            CircularBuildMovementPolicy.holdReason(
                0,
                false
            )
        );
    }

    @Test
    void repairAndMandatoryServerConfirmedSwapHoldMovement() {
        assertEquals(
            HoldReason.HOTBAR_SWAP_CONFIRMATION,
            CircularBuildMovementPolicy.holdReason(
                0,
                true
            )
        );
        assertEquals(
            HoldReason.ACTIVE_U_REPAIR,
            CircularBuildMovementPolicy.holdReason(
                1,
                true
            )
        );
    }

    @Test
    void negativeRepairCountIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildMovementPolicy.holdReason(
                -1,
                false
            )
        );
    }

    @Test
    void passedDeadlineBacktracksOnlyAlongTheOrderedRoute() {
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction
                .BACKTRACK_ON_ROUTE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                82,
                82,
                false,
                false
            )
        );
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction.CONTINUE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                83,
                82,
                false,
                true
            )
        );
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction.CONTINUE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                82,
                82,
                false,
                true
            )
        );
    }

    @Test
    void reachableMissingTargetNeverStopsRouteMovement() {
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction.CONTINUE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                129,
                129,
                true,
                false
            )
        );
    }

    @Test
    void unsubmittedTargetBeforeFinalReachSupportDoesNotDeadlock() {
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction.CONTINUE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                128,
                129,
                false,
                false
            )
        );
    }

    @Test
    void reachRecoverySweepsBetweenRouteDerivedBoundaries() {
        assertEquals(
            -1,
            CircularBuildMovementPolicy.reachSweepDirection(
                130,
                127,
                130,
                -1
            )
        );
        assertEquals(
            1,
            CircularBuildMovementPolicy.reachSweepDirection(
                127,
                127,
                130,
                -1
            )
        );
        assertEquals(
            1,
            CircularBuildMovementPolicy.reachSweepDirection(
                129,
                127,
                130,
                1
            )
        );
        assertEquals(
            -1,
            CircularBuildMovementPolicy.reachSweepDirection(
                130,
                127,
                130,
                1
            )
        );
    }

    @Test
    void invalidReachSweepIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildMovementPolicy.reachSweepDirection(
                5,
                6,
                5,
                -1
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildMovementPolicy.reachSweepDirection(
                5,
                4,
                6,
                0
            )
        );
    }
}
