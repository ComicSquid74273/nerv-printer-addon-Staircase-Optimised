package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoritativeInventorySwapGateTest {
    @Test
    void acceptsTwoNewAuthoritativeSlotObservations() {
        assertTrue(AuthoritativeInventorySwapGate.confirms(
            12, 13, 14, true
        ));
    }

    @Test
    void rejectsPartialStaleOrNonExchangedObservations() {
        assertFalse(AuthoritativeInventorySwapGate.confirms(
            12, 12, 14, true
        ));
        assertFalse(AuthoritativeInventorySwapGate.confirms(
            12, 13, 12, true
        ));
        assertFalse(AuthoritativeInventorySwapGate.confirms(
            12, 13, 14, false
        ));
    }

    @Test
    void rejectsInvalidRevisions() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AuthoritativeInventorySwapGate.confirms(-1, 0, 0, false)
        );
    }
}
