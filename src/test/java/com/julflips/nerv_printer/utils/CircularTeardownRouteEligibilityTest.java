package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FALLBACK;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularTeardownRouteEligibilityTest {
    @Test
    void nonlocalInterruptedRemainderCanBeFinishedRemotely() {
        var result = CircularTeardownRouteEligibility.classify(
            RECOVER_FROM_END,
            false
        );

        assertFalse(result.complete());
        assertFalse(result.mustTraverse());
        assertFalse(result.canHostRemoteTeardown());
    }

    @Test
    void occupiedRecoveryAndFallbackRoutesRemainMandatory() {
        assertTrue(
            CircularTeardownRouteEligibility.classify(
                RECOVER_FROM_END,
                true
            ).mustTraverse()
        );
        assertTrue(
            CircularTeardownRouteEligibility.classify(
                FALLBACK,
                false
            ).mustTraverse()
        );
    }
}
