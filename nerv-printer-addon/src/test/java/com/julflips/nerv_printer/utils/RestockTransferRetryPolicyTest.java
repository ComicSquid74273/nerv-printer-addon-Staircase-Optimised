package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestockTransferRetryPolicyTest {
    @Test
    void advancesInsideOneBoundedCycle() {
        RestockTransferRetryPolicy.Decision decision =
            RestockTransferRetryPolicy.afterNoProgress(1, 3);

        assertEquals(2, decision.nextAttempt());
        assertFalse(decision.completedCycle());
    }

    @Test
    void completedCycleRestartsWithoutBecomingTerminal() {
        RestockTransferRetryPolicy.Decision decision =
            RestockTransferRetryPolicy.afterNoProgress(3, 3);

        assertEquals(1, decision.nextAttempt());
        assertTrue(decision.completedCycle());
    }

    @Test
    void anOversizedRecoveredAttemptAlsoRestartsTheCycle() {
        RestockTransferRetryPolicy.Decision decision =
            RestockTransferRetryPolicy.afterNoProgress(8, 3);

        assertEquals(1, decision.nextAttempt());
        assertTrue(decision.completedCycle());
    }

    @Test
    void validatesAttemptsAndCycleLength() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockTransferRetryPolicy.afterNoProgress(0, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockTransferRetryPolicy.afterNoProgress(1, 0)
        );
    }
}
