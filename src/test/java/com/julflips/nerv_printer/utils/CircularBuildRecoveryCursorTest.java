package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildRecoveryCursorTest {
    @Test
    void backsOutTowardTheOutboundNorthEndpoint() {
        int index = 2;
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertEquals(1, index);
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertEquals(0, index);
        index = CircularBuildRecoveryCursor.advance(index, -1, 4);
        assertTrue(CircularBuildRecoveryCursor.complete(index, 4));
    }

    @Test
    void continuesTowardTheReturnNorthEndpoint() {
        int index = 1;
        assertFalse(CircularBuildRecoveryCursor.complete(index, 4));
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        assertEquals(2, index);
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        index = CircularBuildRecoveryCursor.advance(index, 1, 4);
        assertTrue(CircularBuildRecoveryCursor.complete(index, 4));
    }

    @Test
    void rejectsInvalidCursorState() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.advance(0, 0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.advance(-1, 1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildRecoveryCursor.complete(0, 0)
        );
    }
}
