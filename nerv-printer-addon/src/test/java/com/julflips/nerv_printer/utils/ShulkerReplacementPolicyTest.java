package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShulkerReplacementPolicyTest {
    @Test
    void ignoresStaleObservations() {
        assertEquals(
            ShulkerReplacementPolicy.Decision.CONTINUE,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.BREAKING_EMPTY_BOX,
                true,
                12,
                12,
                ShulkerReplacementPolicy.ObservedBlock.AIR
            )
        );
    }

    @Test
    void confirmsAirBeforeWaitingForNormalReplacement() {
        assertEquals(
            ShulkerReplacementPolicy.Decision.EMPTY_BOX_CONFIRMED,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.BREAKING_EMPTY_BOX,
                true,
                12,
                13,
                ShulkerReplacementPolicy.ObservedBlock.AIR
            )
        );
        assertEquals(
            ShulkerReplacementPolicy.Decision.REPLACEMENT_CONFIRMED,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.WAITING_FOR_REPLACEMENT,
                true,
                13,
                14,
                ShulkerReplacementPolicy.ObservedBlock.SHULKER
            )
        );
    }

    @Test
    void acceptsDispenserReplacementCollapsedIntoOneServerUpdate() {
        assertEquals(
            ShulkerReplacementPolicy.Decision.REPLACEMENT_CONFIRMED,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.BREAKING_EMPTY_BOX,
                true,
                20,
                21,
                ShulkerReplacementPolicy.ObservedBlock.SHULKER
            )
        );
    }

    @Test
    void doesNotTreatUnbrokenShulkerAsReplacement() {
        assertEquals(
            ShulkerReplacementPolicy.Decision.CONTINUE,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.BREAKING_EMPTY_BOX,
                false,
                20,
                21,
                ShulkerReplacementPolicy.ObservedBlock.SHULKER
            )
        );
    }

    @Test
    void rejectsAnotherBlockPlacedAtStation() {
        assertEquals(
            ShulkerReplacementPolicy.Decision.UNEXPECTED_BLOCK,
            ShulkerReplacementPolicy.decide(
                ShulkerReplacementPolicy.Phase.WAITING_FOR_REPLACEMENT,
                true,
                20,
                21,
                ShulkerReplacementPolicy.ObservedBlock.OTHER
            )
        );
    }
}
