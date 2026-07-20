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
                208,
                true
            )
        );
    }

    @Test
    void deferredPlacementHoldsOnEntryToFinalReachSupport() {
        assertFalse(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                206,
                208,
                true
            )
        );
        assertTrue(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                207,
                208,
                true
            )
        );
        assertTrue(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                208,
                208,
                true
            )
        );
    }

    @Test
    void unreachableDeferredPlacementUsesRecoveryInsteadOfDeadlineHold() {
        assertFalse(
            CircularBuildMovementPolicy.requiresDeferredPlacementHold(
                208,
                208,
                false
            )
        );
    }

    @Test
    void invalidDeferredReachDeadlineIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildMovementPolicy
                .requiresDeferredPlacementHold(0, -1, true)
        );
    }
}
