package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.julflips.nerv_printer.utils.UsedToolDepositRecoveryPolicy.ResolutionStatus.AMBIGUOUS;
import static com.julflips.nerv_printer.utils.UsedToolDepositRecoveryPolicy.ResolutionStatus.NO_CURRENT_WORK;
import static com.julflips.nerv_printer.utils.UsedToolDepositRecoveryPolicy.ResolutionStatus.RESOLVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsedToolDepositRecoveryPolicyTest {
    @Test
    void plannedLastInteractedDestinationTakesPriority() {
        UsedToolDepositRecoveryPolicy.DestinationResolution<String>
            resolution =
                UsedToolDepositRecoveryPolicy.resolveDestination(
                    Set.of("missing", "last"),
                    List.of("last"),
                    Optional.of("last"),
                    true
                );

        assertEquals(RESOLVED, resolution.status());
        assertEquals(Optional.of("last"), resolution.destination());
    }

    @Test
    void derivesUniquePlannedDestinationMissingFromQueue() {
        UsedToolDepositRecoveryPolicy.DestinationResolution<String>
            resolution =
                UsedToolDepositRecoveryPolicy.resolveDestination(
                    Set.of("queued", "interrupted"),
                    List.of("queued"),
                    Optional.of("stale"),
                    true
                );

        assertEquals(RESOLVED, resolution.status());
        assertEquals(
            Optional.of("interrupted"),
            resolution.destination()
        );
    }

    @Test
    void noInFlightTransactionMeansNoCurrentWork() {
        UsedToolDepositRecoveryPolicy.DestinationResolution<String>
            resolution =
                UsedToolDepositRecoveryPolicy.resolveDestination(
                    Set.of("last"),
                    List.of(),
                    Optional.of("last"),
                    false
                );

        assertEquals(NO_CURRENT_WORK, resolution.status());
        assertEquals(Optional.empty(), resolution.destination());
    }

    @Test
    void zeroMissingDestinationsIsAmbiguousInFlightState() {
        UsedToolDepositRecoveryPolicy.DestinationResolution<String>
            resolution =
                UsedToolDepositRecoveryPolicy.resolveDestination(
                    Set.of("a", "b"),
                    List.of("a", "b"),
                    Optional.empty(),
                    true
                );

        assertEquals(AMBIGUOUS, resolution.status());
        assertEquals(Optional.empty(), resolution.destination());
    }

    @Test
    void multipleMissingDestinationsIsAmbiguousInFlightState() {
        UsedToolDepositRecoveryPolicy.DestinationResolution<String>
            resolution =
                UsedToolDepositRecoveryPolicy.resolveDestination(
                    Set.of("a", "b"),
                    List.of(),
                    Optional.empty(),
                    true
                );

        assertEquals(AMBIGUOUS, resolution.status());
        assertEquals(Optional.empty(), resolution.destination());
    }

    @Test
    void acceptsSingleAndDoubleChestHandlerSnapshots() {
        assertTrue(acceptsHandlerSnapshot(63, 8, 8));
        assertTrue(acceptsHandlerSnapshot(90, 8, 8));
    }

    @Test
    void rejectsZeroOrStaleHandlerSync() {
        assertFalse(acceptsHandlerSnapshot(63, 0, 0));
        assertFalse(acceptsHandlerSnapshot(63, 7, 8));
    }

    @Test
    void rejectsDestinationWithoutAPlan() {
        assertFalse(
            UsedToolDepositRecoveryPolicy.acceptsHandlerSnapshot(
                "chest",
                "chest",
                Set.of(),
                List.of("pickaxe"),
                8,
                8,
                63
            )
        );
    }

    @Test
    void rejectsEmptyItemPlan() {
        assertFalse(
            UsedToolDepositRecoveryPolicy.acceptsHandlerSnapshot(
                "chest",
                "chest",
                Set.of("chest"),
                List.of(),
                8,
                8,
                63
            )
        );
    }

    @Test
    void rejectsUnexpectedDestinationOrHandlerSize() {
        assertFalse(
            UsedToolDepositRecoveryPolicy.acceptsHandlerSnapshot(
                "expected",
                "other",
                Set.of("expected", "other"),
                List.of("pickaxe"),
                8,
                8,
                63
            )
        );
        assertFalse(acceptsHandlerSnapshot(62, 8, 8));
        assertFalse(acceptsHandlerSnapshot(91, 8, 8));
    }

    private boolean acceptsHandlerSnapshot(
        int totalSlots,
        int packetSyncId,
        int currentHandlerSyncId
    ) {
        return UsedToolDepositRecoveryPolicy.acceptsHandlerSnapshot(
            "chest",
            "chest",
            Set.of("chest"),
            List.of("pickaxe"),
            packetSyncId,
            currentHandlerSyncId,
            totalSlots
        );
    }
}
