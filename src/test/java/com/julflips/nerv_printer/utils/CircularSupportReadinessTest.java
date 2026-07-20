package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularSupportReadinessTest {
    @Test
    void acceptsOnlyConfirmedExpectedSupportWithClearHeadroom() {
        CircularSupportReadiness.Assessment ready =
            CircularSupportReadiness.assess(
                false,
                true,
                false,
                true,
                true
            );

        assertEquals(
            CircularSupportReadiness.Status.READY,
            ready.status()
        );
        assertTrue(ready.ready());
        assertFalse(ready.repairRequired());
    }

    @Test
    void keepsMissingAndPendingSupportAsPlacementWork() {
        assertEquals(
            CircularSupportReadiness.Status.WAITING_FOR_SUPPORT,
            CircularSupportReadiness.assess(
                true,
                false,
                false,
                true,
                true
            ).status()
        );
        assertEquals(
            CircularSupportReadiness.Status.WAITING_FOR_SUPPORT,
            CircularSupportReadiness.assess(
                false,
                true,
                true,
                true,
                true
            ).status()
        );
    }

    @Test
    void distinguishesWrongSupportFromBothHeadroomObstructions() {
        CircularSupportReadiness.Assessment wrong =
            CircularSupportReadiness.assess(
                false,
                false,
                false,
                true,
                true
            );
        CircularSupportReadiness.Assessment lower =
            CircularSupportReadiness.assess(
                false,
                true,
                false,
                false,
                true
            );
        CircularSupportReadiness.Assessment upper =
            CircularSupportReadiness.assess(
                false,
                true,
                false,
                true,
                false
            );

        assertEquals(
            CircularSupportReadiness.Status.WRONG_SUPPORT,
            wrong.status()
        );
        assertEquals(0, wrong.obstructionOffset());
        assertEquals(
            CircularSupportReadiness.Status.LOWER_HEADROOM_BLOCKED,
            lower.status()
        );
        assertEquals(1, lower.obstructionOffset());
        assertEquals(
            CircularSupportReadiness.Status.UPPER_HEADROOM_BLOCKED,
            upper.status()
        );
        assertEquals(2, upper.obstructionOffset());
        assertTrue(lower.repairRequired());
        assertTrue(upper.repairRequired());
    }

    @Test
    void rejectsInconsistentAssessmentConstruction() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CircularSupportReadiness.Assessment(
                CircularSupportReadiness.Status.READY,
                1
            )
        );
    }
}
