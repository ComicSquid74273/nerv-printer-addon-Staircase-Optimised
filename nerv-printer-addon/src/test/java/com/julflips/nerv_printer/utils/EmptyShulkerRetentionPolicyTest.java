package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmptyShulkerRetentionPolicyTest {
    @Test
    void ordinaryDisposableItemsStillDumpWithoutCapacityPressure() {
        assertEquals(
            12,
            EmptyShulkerRetentionPolicy.selectDumpSlot(
                List.of(
                    new EmptyShulkerRetentionPolicy.Candidate(7, true),
                    new EmptyShulkerRetentionPolicy.Candidate(12, false)
                ),
                false
            )
        );
    }

    @Test
    void retainsEmptyShulkersDuringOrdinaryDumping() {
        assertEquals(
            -1,
            EmptyShulkerRetentionPolicy.selectDumpSlot(
                List.of(
                    new EmptyShulkerRetentionPolicy.Candidate(7, true),
                    new EmptyShulkerRetentionPolicy.Candidate(8, true)
                ),
                false
            )
        );
    }

    @Test
    void prefersOrdinaryDisposableItemsWhenCapacityIsNeeded() {
        assertEquals(
            12,
            EmptyShulkerRetentionPolicy.selectDumpSlot(
                List.of(
                    new EmptyShulkerRetentionPolicy.Candidate(7, true),
                    new EmptyShulkerRetentionPolicy.Candidate(12, false)
                ),
                true
            )
        );
    }

    @Test
    void releasesOneEmptyShulkerOnlyWhenCapacityIsNeeded() {
        assertEquals(
            7,
            EmptyShulkerRetentionPolicy.selectDumpSlot(
                List.of(
                    new EmptyShulkerRetentionPolicy.Candidate(7, true),
                    new EmptyShulkerRetentionPolicy.Candidate(8, true)
                ),
                true
            )
        );
    }
}
