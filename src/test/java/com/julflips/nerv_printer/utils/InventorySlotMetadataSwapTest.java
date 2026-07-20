package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySlotMetadataSwapTest {
    @Test
    void movesShadowAndReservationWithTheirPhysicalStacks() {
        Map<Integer, String> shadows = new HashMap<>();
        shadows.put(4, "used-tool");
        Set<Integer> reserved = new HashSet<>(Set.of(4));

        InventorySlotMetadataSwap.Captured<String> swap =
            InventorySlotMetadataSwap.capture(
                12,
                4,
                shadows,
                reserved
            );
        swap.applyTo(shadows, reserved);

        assertEquals("used-tool", shadows.get(12));
        assertFalse(shadows.containsKey(4));
        assertTrue(reserved.contains(12));
        assertFalse(reserved.contains(4));
    }

    @Test
    void exchangesTwoShadowsAndTwoDifferentReservationFlags() {
        Map<Integer, String> shadows = new HashMap<>(
            Map.of(7, "target", 20, "source")
        );
        Set<Integer> reserved = new HashSet<>(Set.of(20));

        InventorySlotMetadataSwap.Captured<String> swap =
            InventorySlotMetadataSwap.capture(
                20,
                7,
                shadows,
                reserved
            );
        swap.applyTo(shadows, reserved);

        assertEquals("target", shadows.get(20));
        assertEquals("source", shadows.get(7));
        assertFalse(reserved.contains(20));
        assertTrue(reserved.contains(7));
    }

    @Test
    void applicationUsesCapturedStateAndIsIdempotent() {
        Map<Integer, String> shadows = new HashMap<>(
            Map.of(3, "target")
        );
        Set<Integer> reserved = new HashSet<>(Set.of(3));
        InventorySlotMetadataSwap.Captured<String> swap =
            InventorySlotMetadataSwap.capture(
                15,
                3,
                shadows,
                reserved
            );

        shadows.put(15, "unconfirmed-client-prediction");
        reserved.add(15);
        swap.applyTo(shadows, reserved);
        swap.applyTo(shadows, reserved);

        assertEquals("target", shadows.get(15));
        assertFalse(shadows.containsKey(3));
        assertTrue(reserved.contains(15));
        assertFalse(reserved.contains(3));
    }

    @Test
    void rejectsInvalidCaptureSlots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> InventorySlotMetadataSwap.capture(
                5,
                5,
                Map.of(),
                Set.of()
            )
        );
    }
}
