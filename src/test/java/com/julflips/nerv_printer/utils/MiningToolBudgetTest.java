package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningToolBudgetTest {
    @Test
    void bufferIsAdditionalDurabilityNotAReplacementMultiplier() {
        assertEquals(1, MiningToolBudget.toolsRequired(100, 0, 100, 0.0, 1.0));
        assertEquals(2, MiningToolBudget.toolsRequired(100, 0, 100, 0.2, 1.0));
        assertEquals(2, MiningToolBudget.toolsRequired(100, 0, 100, 1.0, 1.0));
    }

    @Test
    void appliesUnbreakingAndWorkShareBeforeBuffering() {
        assertEquals(1, MiningToolBudget.toolsRequired(800, 3, 100, 0.2, 0.25));
    }

    @Test
    void strictTraversalBudgetUsesActualRemainingDurability() {
        assertEquals(
            0,
            MiningToolBudget.missingFreshToolsForTraversal(100, 0.2, 100, 120)
        );
        assertEquals(
            1,
            MiningToolBudget.missingFreshToolsForTraversal(100, 0.2, 100, 20)
        );
        assertEquals(
            2,
            MiningToolBudget.missingFreshToolsForTraversal(100, 0.2, 100, 19)
        );
    }

    @Test
    void strictTraversalBudgetRoundsBufferedUsesUpBeforeSubtracting() {
        assertEquals(
            2,
            MiningToolBudget.missingFreshToolsForTraversal(3, 0.1, 2, 0)
        );
        assertEquals(
            1,
            MiningToolBudget.missingFreshToolsForTraversal(200, 0.0, 100, 150)
        );
        assertEquals(
            0,
            MiningToolBudget.missingFreshToolsForTraversal(0, 1.0, 100, 0)
        );
    }

    @Test
    void strictTraversalBudgetRejectsInvalidInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolBudget.missingFreshToolsForTraversal(-1, 0.2, 100, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolBudget.missingFreshToolsForTraversal(1, -0.1, 100, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolBudget.missingFreshToolsForTraversal(
                1,
                Double.NaN,
                100,
                0
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolBudget.missingFreshToolsForTraversal(1, 0.2, 0, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolBudget.missingFreshToolsForTraversal(1, 0.2, 100, -1)
        );
    }
}
