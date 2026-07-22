package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrioritizedRestockPolicyTest {
    @Test
    void distinguishesCompleteOptionalAndMandatoryShortfalls() {
        assertEquals(
            PrioritizedRestockPolicy.Shortfall.NONE,
            PrioritizedRestockPolicy.classify(128, 192, 192)
        );
        assertEquals(
            PrioritizedRestockPolicy.Shortfall.OPTIONAL_ONLY,
            PrioritizedRestockPolicy.classify(128, 192, 128)
        );
        assertEquals(
            PrioritizedRestockPolicy.Shortfall.MANDATORY,
            PrioritizedRestockPolicy.classify(128, 192, 127)
        );
    }

    @Test
    void validatesTierOrderingAndCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PrioritizedRestockPolicy.classify(2, 1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrioritizedRestockPolicy.classify(-1, 1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrioritizedRestockPolicy.classify(0, 1, -1)
        );
    }
}
