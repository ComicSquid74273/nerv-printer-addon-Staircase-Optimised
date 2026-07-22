package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDurabilityPolicyTest {
    @Test
    void acceptsTheConfiguredTenPercentBoundary() {
        assertEquals(200, ToolDurabilityPolicy.minimumRemaining(2000, 0.10));
        assertTrue(ToolDurabilityPolicy.isReusable(200, 2000, 0.10));
        assertFalse(ToolDurabilityPolicy.isReusable(199, 2000, 0.10));
    }

    @Test
    void roundsThePercentageFloorUp() {
        assertEquals(204, ToolDurabilityPolicy.minimumRemaining(2031, 0.10));
        assertTrue(ToolDurabilityPolicy.isReusable(204, 2031, 0.10));
        assertFalse(ToolDurabilityPolicy.isReusable(203, 2031, 0.10));
    }

    @Test
    void activeTraversalCanUseAFrozenToolBelowTheEntryFloor() {
        assertFalse(ToolDurabilityPolicy.isReusable(50, 2000, 0.10));
        assertTrue(ToolDurabilityPolicy.isReusable(50, 2000, 0.0));
    }

    @Test
    void alwaysReservesTheFinalDurabilityPoint() {
        assertEquals(2, ToolDurabilityPolicy.minimumRemaining(100, 0.0));
        assertFalse(ToolDurabilityPolicy.isReusable(1, 100, 0.0));
    }

    @Test
    void rejectsInvalidFractions() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ToolDurabilityPolicy.minimumRemaining(100, -0.01)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ToolDurabilityPolicy.minimumRemaining(100, 1.01)
        );
    }
}
