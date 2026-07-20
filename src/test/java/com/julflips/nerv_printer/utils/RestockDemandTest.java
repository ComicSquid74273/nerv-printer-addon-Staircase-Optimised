package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestockDemandTest {
    @Test
    void capturesAbsoluteTargetFromOnHandAndMissing() {
        RestockDemand<String> demand =
            RestockDemand.fromOnHandAndMissing("obsidian", 37, 91);

        assertEquals("obsidian", demand.item());
        assertEquals(128, demand.targetCompatiblePlayerCount());
        assertEquals(91, demand.remainingAmount());
    }

    @Test
    void constructionRejectsTargetOverflowWithoutWrapping() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockDemand.fromOnHandAndMissing(
                "obsidian",
                Integer.MAX_VALUE,
                1
            )
        );

        RestockDemand<String> boundary =
            RestockDemand.fromOnHandAndMissing(
                "obsidian",
                Integer.MAX_VALUE,
                0
            );
        assertEquals(
            Integer.MAX_VALUE,
            boundary.targetCompatiblePlayerCount()
        );
    }

    @Test
    void reconcilesFromAuthoritativeAbsoluteCountWithoutMutatingSource() {
        RestockDemand<String> original =
            RestockDemand.fromOnHandAndMissing("obsidian", 32, 96);

        RestockDemand<String> reconciled = original.reconcile(80);

        assertNotSame(original, reconciled);
        assertEquals(128, reconciled.targetCompatiblePlayerCount());
        assertEquals(48, reconciled.remainingAmount());
        assertEquals(96, original.remainingAmount());
    }

    @Test
    void confirmedCountAtOrAboveTargetCompletesDemand() {
        RestockDemand<String> demand =
            RestockDemand.fromOnHandAndMissing("obsidian", 16, 48);

        assertEquals(0, demand.reconcile(64).remainingAmount());
        assertEquals(0, demand.reconcile(96).remainingAmount());
    }

    @Test
    void roundsRemainingStacksUpWithoutArithmeticOverflow() {
        assertEquals(
            0,
            new RestockDemand<>("obsidian", 0, 0).remainingStacks(64)
        );
        assertEquals(
            1,
            new RestockDemand<>("obsidian", 1, 1).remainingStacks(64)
        );
        assertEquals(
            1,
            new RestockDemand<>("obsidian", 64, 64).remainingStacks(64)
        );
        assertEquals(
            2,
            new RestockDemand<>("obsidian", 65, 65).remainingStacks(64)
        );
        assertEquals(
            Integer.MAX_VALUE,
            new RestockDemand<>(
                "obsidian",
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
            ).remainingStacks(1)
        );
    }

    @Test
    void validatesConstructionReconciliationAndStackSize() {
        assertThrows(
            NullPointerException.class,
            () -> RestockDemand.fromOnHandAndMissing(null, 0, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockDemand.fromOnHandAndMissing("obsidian", -1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockDemand.fromOnHandAndMissing("obsidian", 0, -1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestockDemand<>("obsidian", -1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestockDemand<>("obsidian", 1, -1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestockDemand<>("obsidian", 1, 2)
        );

        RestockDemand<String> demand =
            RestockDemand.fromOnHandAndMissing("obsidian", 0, 1);
        assertThrows(
            IllegalArgumentException.class,
            () -> demand.reconcile(-1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> demand.remainingStacks(0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> demand.remainingStacks(-1)
        );
    }
}
