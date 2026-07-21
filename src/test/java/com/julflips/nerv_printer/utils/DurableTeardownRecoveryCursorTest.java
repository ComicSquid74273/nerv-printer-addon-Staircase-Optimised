package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableTeardownRecoveryCursorTest {
    @Test
    void liveAuthoritativeCursorWinsOverRuntimeAndPersistedState() {
        var live = cursor(24, 18);

        assertEquals(
            live,
            DurableTeardownRecoveryCursor.select(
                live,
                24,
                cursor(24, 17),
                cursor(24, 16)
            ).orElseThrow()
        );
    }

    @Test
    void liveCursorCanIdentifyThePairBeforeAssignmentIsReconstructed() {
        var live = cursor(24, 18);

        assertEquals(
            live,
            DurableTeardownRecoveryCursor.select(
                live,
                null,
                null,
                null
            ).orElseThrow()
        );
    }

    @Test
    void orderedRouteCursorSurvivesAnInstantaneousUngroundedSnapshot() {
        var ordered = cursor(24, 17);

        assertEquals(
            ordered,
            DurableTeardownRecoveryCursor.select(
                null,
                24,
                ordered,
                cursor(24, 16)
            ).orElseThrow()
        );
    }

    @Test
    void lastConfirmedCursorSurvivesWhenNoGroundedOrRuntimeCursorExists() {
        var confirmed = cursor(24, 17);

        assertEquals(
            confirmed,
            DurableTeardownRecoveryCursor.select(
                null,
                24,
                null,
                confirmed
            ).orElseThrow()
        );
    }

    @Test
    void mismatchedRuntimeCursorFallsThroughToMatchingConfirmedCursor() {
        var confirmed = cursor(24, 17);

        assertEquals(
            confirmed,
            DurableTeardownRecoveryCursor.select(
                null,
                24,
                cursor(23, 142),
                confirmed
            ).orElseThrow()
        );
    }

    @Test
    void staleConfirmedCursorFromAnEarlierAssignmentIsRejected() {
        assertTrue(
            DurableTeardownRecoveryCursor.select(
                null,
                24,
                null,
                cursor(23, 142)
            ).isEmpty()
        );
        assertTrue(
            DurableTeardownRecoveryCursor.select(
                null,
                null,
                null,
                cursor(24, 17)
            ).isEmpty()
        );
    }

    @Test
    void recoveryAcceptsOnlyAMatchingInRangeRemainingSupport() {
        var saved = cursor(24, 17);

        assertEquals(
            saved,
            DurableTeardownRecoveryCursor.validateForRecovery(
                saved,
                24,
                260,
                index -> index == 17
            ).orElseThrow()
        );
        assertTrue(
            DurableTeardownRecoveryCursor.validateForRecovery(
                saved,
                23,
                260,
                index -> true
            ).isEmpty()
        );
        assertTrue(
            DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(24, 260),
                24,
                260,
                index -> true
            ).isEmpty()
        );
        assertTrue(
            DurableTeardownRecoveryCursor.validateForRecovery(
                saved,
                24,
                260,
                index -> false
            ).isEmpty()
        );
    }

    @Test
    void mismatchedOrOutOfRangeCursorDoesNotReadWorldState() {
        AtomicBoolean queried = new AtomicBoolean();

        assertFalse(
            DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(23, 17),
                24,
                260,
                index -> {
                    queried.set(true);
                    return true;
                }
            ).isPresent()
        );
        assertFalse(queried.get());

        assertFalse(
            DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(24, 260),
                24,
                260,
                index -> {
                    queried.set(true);
                    return true;
                }
            ).isPresent()
        );
        assertFalse(queried.get());
    }

    @Test
    void invalidIndicesAndRouteArgumentsFailClosed() {
        assertThrows(
            IllegalArgumentException.class,
            () -> cursor(-1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> cursor(0, -1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DurableTeardownRecoveryCursor.select(
                null,
                -1,
                null,
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(0, 0),
                -1,
                1,
                index -> true
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(0, 0),
                0,
                -1,
                index -> true
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> DurableTeardownRecoveryCursor.validateForRecovery(
                cursor(0, 0),
                0,
                1,
                null
            )
        );
    }

    private static DurableTeardownRecoveryCursor.Cursor cursor(
        int pairIndex,
        int targetIndex
    ) {
        return new DurableTeardownRecoveryCursor.Cursor(
            pairIndex,
            targetIndex
        );
    }
}
