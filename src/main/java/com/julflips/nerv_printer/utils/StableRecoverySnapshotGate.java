package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Prevents recovery from committing against a transient player snapshot.
 *
 * <p>Joining a world and reactivating a module can expose the correct player
 * position before the grounded flag has settled. Recovery may proceed only
 * after the same eligible snapshot has been observed for the configured
 * number of consecutive ticks.</p>
 */
public final class StableRecoverySnapshotGate<T> {
    private final int requiredObservations;
    private T candidate;
    private int observations;

    public StableRecoverySnapshotGate(int requiredObservations) {
        if (requiredObservations <= 0) {
            throw new IllegalArgumentException(
                "Required observations must be positive."
            );
        }
        this.requiredObservations = requiredObservations;
    }

    public boolean observe(T snapshot, boolean eligible) {
        if (!eligible || snapshot == null) {
            reset();
            return false;
        }
        if (!Objects.equals(candidate, snapshot)) {
            candidate = snapshot;
            observations = 1;
        } else if (observations < requiredObservations) {
            observations++;
        }
        return observations >= requiredObservations;
    }

    public int observations() {
        return observations;
    }

    public void reset() {
        candidate = null;
        observations = 0;
    }
}
