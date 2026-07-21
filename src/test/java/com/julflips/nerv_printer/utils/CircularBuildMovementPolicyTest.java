package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static com.julflips.nerv_printer.utils.CircularBuildMovementPolicy.HoldReason;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void pendingDeferredPlacementDoesNotHoldBeforeReachDeadline() {
        assertFalse(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                55,
                208
            )
        );
    }

    @Test
    void deferredPlacementHoldsOnEntryToFinalReachSupport() {
        assertFalse(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                206,
                208
            )
        );
        assertTrue(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                207,
                208
            )
        );
        assertTrue(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                208,
                208
            )
        );
    }

    @Test
    void deadlineHoldDoesNotDependOnOneStaleLiveReachSample() {
        assertTrue(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                208,
                208
            )
        );
    }

    @Test
    void invalidDeferredReachDeadlineIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildMovementPolicy
                .requiresDeferredPlacementHold(0, -1)
        );
    }

    @Test
    void passedDeadlineBacktracksOnlyAlongTheOrderedRoute() {
        assertEquals(
            CircularBuildMovementPolicy.ReachDeadlineAction
                .BACKTRACK_ON_ROUTE,
            CircularBuildMovementPolicy.reachDeadlineAction(
                84,
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
            CircularBuildMovementPolicy.ReachDeadlineAction
                .HOLD_FOR_PLACEMENT,
            CircularBuildMovementPolicy.reachDeadlineAction(
                82,
                82,
                false,
                true
            )
        );
    }
}
