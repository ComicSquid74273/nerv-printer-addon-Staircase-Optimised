package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticShulkerRescanPolicyTest {
    @Test
    void incompleteScanRetriesAfterFortyTicks() {
        assertEquals(
            140,
            AutomaticShulkerRescanPolicy.nextRetryTick(100)
        );
    }

    @Test
    void everyFifthPassReopensKnownStations() {
        assertFalse(
            AutomaticShulkerRescanPolicy.shouldReinspectKnownStations(0)
        );
        assertFalse(
            AutomaticShulkerRescanPolicy.shouldReinspectKnownStations(4)
        );
        assertTrue(
            AutomaticShulkerRescanPolicy.shouldReinspectKnownStations(5)
        );
        assertTrue(
            AutomaticShulkerRescanPolicy.shouldReinspectKnownStations(10)
        );
    }
}
