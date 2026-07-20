package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDemandSatisfactionTest {
    @Test
    void forwardPlanShortfallBlocksDepartureEvenWhenActiveUIsFull() {
        Map<String, Integer> missing =
            InventoryDemandSatisfaction.missingAmounts(
                Map.of(
                    "active-u", 64,
                    "future-white", 128,
                    "deferred-cobblestone", 12
                ),
                Map.of(
                    "active-u", 64,
                    "future-white", 64,
                    "deferred-cobblestone", 12
                )
            );

        assertEquals(Map.of("future-white", 64), missing);
    }

    @Test
    void reportsSatisfiedOnlyForTheCompleteFrozenDemand() {
        assertTrue(
            InventoryDemandSatisfaction.missingAmounts(
                Map.of("white", 64, "black", 32),
                Map.of("white", 64, "black", 40)
            ).isEmpty()
        );
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryDemandSatisfaction.missingAmounts(
                Map.of("white", -1),
                Map.of()
            )
        );
    }
}
