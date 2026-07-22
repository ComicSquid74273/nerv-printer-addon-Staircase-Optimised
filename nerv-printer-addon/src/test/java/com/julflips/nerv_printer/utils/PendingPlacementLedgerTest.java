package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingPlacementLedgerTest {
    @Test
    void submitPreventsDuplicatePendingAttempts() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 2);

        assertTrue(ledger.submit("position", "stone", 10));
        assertFalse(ledger.submit("position", "dirt", 11));
        assertTrue(ledger.isPending("position"));
        assertEquals("stone", ledger.pendingAttempts().getFirst().expected());
        assertEquals(1, ledger.size());
    }

    @Test
    void delayedObservationConfirmsOnlyTheMatchingExpectedState() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 2);
        ledger.submit("position", "stone", 0);

        assertEquals(
            PendingPlacementLedger.ObservationStatus.UNRESOLVED,
            ledger.observeUnresolved("position").status()
        );
        assertTrue(ledger.advance(4, 1).isEmpty());
        assertTrue(ledger.isPending("position"));

        PendingPlacementLedger.Observation<String, String> confirmation =
            ledger.observePresent("position", "stone");
        assertEquals(
            PendingPlacementLedger.ObservationStatus.CONFIRMED,
            confirmation.status()
        );
        assertEquals(Optional.of("stone"), confirmation.expected());
        assertEquals(Optional.of("stone"), confirmation.observed());
        assertFalse(ledger.isPending("position"));
    }

    @Test
    void conflictingObservedStateIsClassifiedWithoutFalseConfirmation() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        ledger.submit("position", "stone", 0);

        PendingPlacementLedger.Observation<String, String> conflict =
            ledger.observePresent("position", "dirt");
        assertEquals(
            PendingPlacementLedger.ObservationStatus.CONFLICT,
            conflict.status()
        );
        assertEquals(Optional.of("stone"), conflict.expected());
        assertEquals(Optional.of("dirt"), conflict.observed());
        assertTrue(ledger.isPending("position"));

        assertEquals(
            PendingPlacementLedger.ObservationStatus.NOT_PENDING,
            ledger.observePresent("unknown", "stone").status()
        );
    }

    @Test
    void retriesAreBoundedAndThenTheAttemptExpires() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 2);
        ledger.submit("position", "stone", 0);

        List<PendingPlacementLedger.TimeoutDecision<String, String>> first =
            ledger.advance(5, 1);
        assertEquals(1, first.size());
        assertEquals(
            PendingPlacementLedger.TimeoutAction.RETRY,
            first.getFirst().action()
        );
        assertEquals(1, first.getFirst().attempt().retriesUsed());
        assertEquals(2, first.getFirst().attempt().totalAttempts());

        assertTrue(ledger.advance(9, 1).isEmpty());

        List<PendingPlacementLedger.TimeoutDecision<String, String>> second =
            ledger.advance(10, 1);
        assertEquals(
            PendingPlacementLedger.TimeoutAction.RETRY,
            second.getFirst().action()
        );
        assertEquals(2, second.getFirst().attempt().retriesUsed());
        assertEquals(3, second.getFirst().attempt().totalAttempts());

        List<PendingPlacementLedger.TimeoutDecision<String, String>> expired =
            ledger.advance(15, 0);
        assertEquals(
            PendingPlacementLedger.TimeoutAction.EXPIRED,
            expired.getFirst().action()
        );
        assertEquals(2, expired.getFirst().attempt().retriesUsed());
        assertTrue(ledger.isEmpty());
    }

    @Test
    void retryBudgetLeavesAdditionalDueAttemptsPendingAndDue() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        ledger.submit("first", "stone", 0);
        ledger.submit("second", "dirt", 0);

        List<PendingPlacementLedger.TimeoutDecision<String, String>> firstTick =
            ledger.advance(5, 1);
        assertEquals(List.of("first"), firstTick.stream()
            .map(decision -> decision.attempt().key())
            .toList());
        assertEquals(0, ledger.pendingAttempts().get(1).retriesUsed());

        List<PendingPlacementLedger.TimeoutDecision<String, String>> nextTick =
            ledger.advance(6, 1);
        assertEquals("second", nextTick.getFirst().attempt().key());
        assertEquals(
            PendingPlacementLedger.TimeoutAction.RETRY,
            nextTick.getFirst().action()
        );
    }

    @Test
    void keySpecificRetryPreservesCallerPriorityInsteadOfInsertionOrder() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 2);
        ledger.submit("optional", "dirt", 0);
        ledger.submit("primary", "stone", 0);

        PendingPlacementLedger.TimeoutDecision<String, String> primary =
            ledger.reserveRetry("primary", 5).orElseThrow();
        assertEquals(
            PendingPlacementLedger.TimeoutAction.RETRY,
            primary.action()
        );
        assertEquals("primary", primary.attempt().key());
        assertEquals(1, primary.attempt().retriesUsed());
        assertEquals(0, ledger.pendingAttempts().getFirst().retriesUsed());

        PendingPlacementLedger.TimeoutDecision<String, String> optional =
            ledger.reserveRetry("optional", 5).orElseThrow();
        assertEquals("optional", optional.attempt().key());
        assertEquals(1, optional.attempt().retriesUsed());
    }

    @Test
    void keySpecificRetryDoesNothingForMissingOrNotYetDueKeys() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        ledger.submit("position", "stone", 10);

        assertTrue(ledger.reserveRetry("missing", 14).isEmpty());
        assertTrue(ledger.reserveRetry("position", 14).isEmpty());
        assertEquals(0, ledger.pendingAttempts().getFirst().retriesUsed());
        assertEquals(10, ledger.pendingAttempts().getFirst().lastAttemptTick());
    }

    @Test
    void keySpecificRetryExpiresAndRemovesAnExhaustedAttempt() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        ledger.submit("position", "stone", 0);

        PendingPlacementLedger.TimeoutDecision<String, String> retry =
            ledger.reserveRetry("position", 5).orElseThrow();
        assertEquals(
            PendingPlacementLedger.TimeoutAction.RETRY,
            retry.action()
        );
        assertTrue(ledger.reserveRetry("position", 9).isEmpty());

        PendingPlacementLedger.TimeoutDecision<String, String> expired =
            ledger.reserveRetry("position", 10).orElseThrow();
        assertEquals(
            PendingPlacementLedger.TimeoutAction.EXPIRED,
            expired.action()
        );
        assertEquals(1, expired.attempt().retriesUsed());
        assertFalse(ledger.isPending("position"));
        assertTrue(ledger.reserveRetry("position", 15).isEmpty());
    }

    @Test
    void removeClearAndResetReleasePendingKeys() {
        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        ledger.submit("first", "stone", 0);
        assertEquals("stone", ledger.remove("first").orElseThrow().expected());
        assertFalse(ledger.isPending("first"));

        ledger.submit("second", "dirt", 1);
        ledger.clear();
        assertTrue(ledger.isEmpty());

        ledger.submit("third", "sand", 2);
        ledger.reset();
        assertTrue(ledger.isEmpty());
        assertTrue(ledger.pendingAttempts().isEmpty());
    }

    @Test
    void validatesConfigurationAndDeterministicTickInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PendingPlacementLedger<String, String>(0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new PendingPlacementLedger<String, String>(1, -1)
        );

        PendingPlacementLedger<String, String> ledger =
            new PendingPlacementLedger<>(5, 1);
        assertThrows(
            IllegalArgumentException.class,
            () -> ledger.submit("position", "stone", -1)
        );
        ledger.submit("position", "stone", 10);
        assertThrows(
            IllegalArgumentException.class,
            () -> ledger.advance(9, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ledger.advance(10, -1)
        );
    }
}
