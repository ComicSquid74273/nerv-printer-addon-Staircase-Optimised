package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningAssignmentModeTest {
    @Test
    void pairModeRoundTripsAsCircular() {
        String wireName = MiningAssignmentMode.wireName(true, true);
        assertEquals("pair", wireName);
        assertEquals(
            new MiningAssignmentMode.Decision(true, true),
            MiningAssignmentMode.parseWireName(wireName).orElseThrow()
        );
        assertTrue(MiningAssignmentMode.usesCircularTraversal(true, true));
    }

    @Test
    void fallbackPairNeverUpgradesToCircular() {
        String wireName = MiningAssignmentMode.wireName(false, true);
        assertEquals("fallback", wireName);
        assertEquals(
            new MiningAssignmentMode.Decision(false, true),
            MiningAssignmentMode.parseWireName(wireName).orElseThrow()
        );
        assertFalse(MiningAssignmentMode.usesCircularTraversal(false, true));
    }

    @Test
    void singleModeRemainsIndependent() {
        String wireName = MiningAssignmentMode.wireName(false, false);
        assertEquals("single", wireName);
        assertEquals(
            new MiningAssignmentMode.Decision(false, false),
            MiningAssignmentMode.parseWireName(wireName).orElseThrow()
        );
        assertFalse(MiningAssignmentMode.usesCircularTraversal(false, false));
    }

    @Test
    void rejectsCircularModeWithoutAWholePair() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningAssignmentMode.wireName(true, false)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningAssignmentMode.usesCircularTraversal(true, false)
        );
        assertTrue(MiningAssignmentMode.parseWireName("unknown").isEmpty());
    }
}
