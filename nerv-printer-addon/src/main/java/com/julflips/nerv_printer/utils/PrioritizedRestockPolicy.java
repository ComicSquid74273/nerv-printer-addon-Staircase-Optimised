package com.julflips.nerv_printer.utils;

/**
 * Classifies a tiered restock shortfall without weakening its mandatory
 * traversal reservation.
 */
public final class PrioritizedRestockPolicy {
    private PrioritizedRestockPolicy() {
    }

    public enum Shortfall {
        NONE,
        OPTIONAL_ONLY,
        MANDATORY
    }

    public static Shortfall classify(
        int mandatoryTarget,
        int desiredTarget,
        int observedAmount
    ) {
        if (mandatoryTarget < 0
            || desiredTarget < mandatoryTarget
            || observedAmount < 0) {
            throw new IllegalArgumentException(
                "Restock targets and observations are inconsistent."
            );
        }
        if (observedAmount >= desiredTarget) return Shortfall.NONE;
        if (observedAmount >= mandatoryTarget) {
            return Shortfall.OPTIONAL_ONLY;
        }
        return Shortfall.MANDATORY;
    }
}
