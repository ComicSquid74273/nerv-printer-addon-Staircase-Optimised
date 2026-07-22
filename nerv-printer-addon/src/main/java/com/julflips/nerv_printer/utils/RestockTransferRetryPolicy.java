package com.julflips.nerv_printer.utils;

/**
 * Retry-cycle bookkeeping for an inventory transfer that produced a fresh
 * authoritative snapshot without increasing the compatible player count.
 *
 * <p>A completed cycle is diagnostic information, not a terminal failure.
 * The caller can continue with the next server-observed source slot while
 * keeping each individual retry cycle bounded.</p>
 */
public final class RestockTransferRetryPolicy {
    public record Decision(int nextAttempt, boolean completedCycle) {
        public Decision {
            if (nextAttempt <= 0) {
                throw new IllegalArgumentException(
                    "The next restock attempt must be positive."
                );
            }
        }
    }

    private RestockTransferRetryPolicy() {
    }

    public static Decision afterNoProgress(
        int currentAttempt,
        int attemptsPerCycle
    ) {
        if (currentAttempt <= 0) {
            throw new IllegalArgumentException(
                "The current restock attempt must be positive."
            );
        }
        if (attemptsPerCycle <= 0) {
            throw new IllegalArgumentException(
                "The restock retry-cycle length must be positive."
            );
        }
        if (currentAttempt >= attemptsPerCycle) {
            return new Decision(1, true);
        }
        return new Decision(currentAttempt + 1, false);
    }
}
