package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static com.julflips.nerv_printer.utils.CircularMiningAssignmentPolicy.Kind.CIRCULAR_PAIR;
import static com.julflips.nerv_printer.utils.CircularMiningAssignmentPolicy.Kind.INDEPENDENT_PAIR;
import static com.julflips.nerv_printer.utils.CircularMiningAssignmentPolicy.Kind.SINGLE_LINE;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FALLBACK;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FORWARD;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CircularMiningAssignmentPolicyTest {
    @Test
    void disconnectedUReservesBothColumnsForIndependentFallback() {
        assertEquals(
            INDEPENDENT_PAIR,
            CircularMiningAssignmentPolicy.decide(true, true, FALLBACK)
        );
    }

    @Test
    void completeAndInterruptedRoutesUseCircularTraversal() {
        assertEquals(
            CIRCULAR_PAIR,
            CircularMiningAssignmentPolicy.decide(true, true, FORWARD)
        );
        assertEquals(
            CIRCULAR_PAIR,
            CircularMiningAssignmentPolicy.decide(true, true, RECOVER_FROM_END)
        );
    }

    @Test
    void disabledOrSplitPairsStaySingle() {
        assertEquals(
            SINGLE_LINE,
            CircularMiningAssignmentPolicy.decide(false, true, FORWARD)
        );
        assertEquals(
            SINGLE_LINE,
            CircularMiningAssignmentPolicy.decide(true, false, FORWARD)
        );
    }
}
