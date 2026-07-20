package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableRecoverySnapshotGateTest {
    @Test
    void requiresConsecutiveEligibleObservationsOfTheSameSnapshot() {
        StableRecoverySnapshotGate<String> gate =
            new StableRecoverySnapshotGate<>(2);

        assertFalse(gate.observe("support-155", true));
        assertTrue(gate.observe("support-155", true));
    }

    @Test
    void changedOrUngroundedSnapshotRestartsClassification() {
        StableRecoverySnapshotGate<String> gate =
            new StableRecoverySnapshotGate<>(2);

        assertFalse(gate.observe("support-153", true));
        assertFalse(gate.observe("support-155", true));
        assertFalse(gate.observe("support-155", false));
        assertFalse(gate.observe("support-155", true));
        assertTrue(gate.observe("support-155", true));
    }

    @Test
    void rejectsAnInvalidObservationRequirement() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new StableRecoverySnapshotGate<>(0)
        );
    }
}
