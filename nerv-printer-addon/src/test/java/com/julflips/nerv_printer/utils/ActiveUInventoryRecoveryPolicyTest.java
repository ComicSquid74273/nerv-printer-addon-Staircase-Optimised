package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveUInventoryRecoveryPolicyTest {
    @Test
    void wholeRouteShortfallAloneDoesNotAbandonTheU() {
        assertEquals(
            ActiveUInventoryRecoveryPolicy.Action.CONTINUE_IN_PLACE,
            ActiveUInventoryRecoveryPolicy.decide(false, true, 0, false)
        );
    }

    @Test
    void consumesOtherReachableWorkBeforeLeaving() {
        assertEquals(
            ActiveUInventoryRecoveryPolicy.Action.CONTINUE_IN_PLACE,
            ActiveUInventoryRecoveryPolicy.decide(true, true, 0, true)
        );
    }

    @Test
    void availableFrontierMaterialKeepsPrinting() {
        assertEquals(
            ActiveUInventoryRecoveryPolicy.Action.CONTINUE_IN_PLACE,
            ActiveUInventoryRecoveryPolicy.decide(true, true, 1, false)
        );
    }

    @Test
    void exhaustedBlockedFrontierIsTheEgressBoundary() {
        assertEquals(
            ActiveUInventoryRecoveryPolicy.Action.EGRESS_AND_RESTOCK,
            ActiveUInventoryRecoveryPolicy.decide(true, true, 0, false)
        );
    }
}
