package com.julflips.nerv_printer.utils;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * Selects and validates the durable cursor for an interrupted ordered
 * teardown route.
 *
 * <p>A checkpoint can be written while the player is between supports, so
 * the instantaneous grounded-support classifier is not always available.
 * In that case an ordered runtime cursor, followed by the last confirmed
 * cursor, may carry the route position forward only while it still belongs
 * to the current assignment. This prevents a cursor from an earlier pair
 * being attached to newly assigned work.</p>
 */
public final class DurableTeardownRecoveryCursor {
    private DurableTeardownRecoveryCursor() {
    }

    public record Cursor(int pairIndex, int targetIndex) {
        public Cursor {
            if (pairIndex < 0) {
                throw new IllegalArgumentException(
                    "A teardown recovery pair index cannot be negative."
                );
            }
            if (targetIndex < 0) {
                throw new IllegalArgumentException(
                    "A teardown recovery target index cannot be negative."
                );
            }
        }
    }

    /**
     * Selects the pair identity written at a teardown checkpoint.
     *
     * <p>The priority matches runtime recovery ownership. A mining-start
     * checkpoint may legitimately precede the first assignment, in which
     * case this method returns {@code null} without unboxing it.</p>
     */
    public static Integer selectCheckpointPair(
        Integer currentAssignmentPair,
        Cursor liveAuthoritativeCursor,
        Cursor activeOrderedRouteCursor,
        Cursor lastConfirmedCursor,
        int preferredRecoveredPair,
        int recoveredActivePair
    ) {
        if (currentAssignmentPair != null) {
            return currentAssignmentPair;
        }
        if (liveAuthoritativeCursor != null) {
            return liveAuthoritativeCursor.pairIndex();
        }
        if (activeOrderedRouteCursor != null) {
            return activeOrderedRouteCursor.pairIndex();
        }
        if (lastConfirmedCursor != null) {
            return lastConfirmedCursor.pairIndex();
        }
        if (preferredRecoveredPair >= 0) {
            return preferredRecoveredPair;
        }
        if (recoveredActivePair >= 0) {
            return recoveredActivePair;
        }
        return null;
    }

    /**
     * Chooses the cursor to persist at the current crash boundary.
     *
     * <p>A live authoritative observation is self-identifying and therefore
     * wins even before an assignment has been reconstructed. Non-live
     * cursors are accepted only for the current assignment.</p>
     */
    public static Optional<Cursor> select(
        Cursor liveAuthoritativeCursor,
        Integer currentAssignmentPair,
        Cursor activeOrderedRouteCursor,
        Cursor lastConfirmedCursor
    ) {
        if (liveAuthoritativeCursor != null) {
            return Optional.of(liveAuthoritativeCursor);
        }
        if (currentAssignmentPair == null) return Optional.empty();
        if (currentAssignmentPair < 0) {
            throw new IllegalArgumentException(
                "The current teardown assignment pair cannot be negative."
            );
        }
        if (belongsTo(
            activeOrderedRouteCursor,
            currentAssignmentPair
        )) {
            return Optional.of(activeOrderedRouteCursor);
        }
        if (belongsTo(
            lastConfirmedCursor,
            currentAssignmentPair
        )) {
            return Optional.of(lastConfirmedCursor);
        }
        return Optional.empty();
    }

    /**
     * Validates a saved cursor against the reconstructed assignment and
     * authoritative route state.
     *
     * <p>The supplied predicate is evaluated only after pair and route-bound
     * checks succeed. Callers can use it to prove that the saved target is
     * still a safe remaining support in the current world snapshot.</p>
     */
    public static Optional<Cursor> validateForRecovery(
        Cursor savedCursor,
        int expectedPair,
        int routeTargetCount,
        IntPredicate isAuthoritativeRemainingSupport
    ) {
        if (expectedPair < 0) {
            throw new IllegalArgumentException(
                "The expected teardown pair cannot be negative."
            );
        }
        if (routeTargetCount < 0) {
            throw new IllegalArgumentException(
                "The teardown route target count cannot be negative."
            );
        }
        if (isAuthoritativeRemainingSupport == null) {
            throw new NullPointerException(
                "isAuthoritativeRemainingSupport"
            );
        }
        if (!belongsTo(savedCursor, expectedPair)
            || savedCursor.targetIndex() >= routeTargetCount
            || !isAuthoritativeRemainingSupport.test(
                savedCursor.targetIndex()
            )) {
            return Optional.empty();
        }
        return Optional.of(savedCursor);
    }

    private static boolean belongsTo(
        Cursor cursor,
        int pairIndex
    ) {
        return cursor != null && cursor.pairIndex() == pairIndex;
    }
}
