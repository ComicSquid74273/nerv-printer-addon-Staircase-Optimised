package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmedHotbarSwapTest {
    @Test
    void requiresBothExpectedDestinationAndNewServerRevision() {
        ConfirmedHotbarSwap<String> swap = new ConfirmedHotbarSwap<>();
        swap.begin(4, "stone", 7, 100);

        assertEquals(
            ConfirmedHotbarSwap.Observation.WAITING,
            swap.observe("stone", 7, 101, 5, 3)
        );
        assertEquals(
            ConfirmedHotbarSwap.Observation.WAITING,
            swap.observe("dirt", 8, 101, 5, 3)
        );
        assertEquals(
            ConfirmedHotbarSwap.Observation.CONFIRMED,
            swap.observe("stone", 8, 101, 5, 3)
        );
        assertFalse(swap.isPending());
    }

    @Test
    void retriesAreBoundedAndDoNotChangeTheExpectedRole() {
        ConfirmedHotbarSwap<String> swap = new ConfirmedHotbarSwap<>();
        swap.begin(2, "cobble", 1, 10);

        assertEquals(
            ConfirmedHotbarSwap.Observation.RETRY_REQUIRED,
            swap.observe("air", 1, 15, 5, 2)
        );
        swap.markRetried(2, 15);

        assertEquals(2, swap.attempts());
        assertEquals("cobble", swap.expected());
        assertEquals(2, swap.targetHotbarSlot());
        assertEquals(
            ConfirmedHotbarSwap.Observation.FAILED,
            swap.observe("air", 2, 20, 5, 2)
        );
        assertFalse(swap.isPending());
    }

    @Test
    void rejectsNestedOrInvalidRequests() {
        ConfirmedHotbarSwap<String> swap = new ConfirmedHotbarSwap<>();
        assertThrows(
            IllegalArgumentException.class,
            () -> swap.begin(9, "stone", 0, 0)
        );

        swap.begin(0, "stone", 0, 0);
        assertTrue(swap.isPending());
        assertThrows(
            IllegalStateException.class,
            () -> swap.begin(1, "dirt", 0, 0)
        );
    }
}
