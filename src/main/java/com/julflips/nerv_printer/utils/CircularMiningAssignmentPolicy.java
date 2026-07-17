package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Decides whether a newly scheduled pair is traversed as a U or reserved as
 * one two-column old-style fallback assignment.
 */
public final class CircularMiningAssignmentPolicy {
    private CircularMiningAssignmentPolicy() {
    }

    public enum Kind {
        SINGLE_LINE,
        CIRCULAR_PAIR,
        INDEPENDENT_PAIR
    }

    public static Kind decide(
        boolean circularEnabled,
        boolean wholePairAvailable,
        CircularMiningRecoveryPlan.Mode routeMode
    ) {
        Objects.requireNonNull(routeMode, "routeMode");
        if (!circularEnabled || !wholePairAvailable) return Kind.SINGLE_LINE;
        return switch (routeMode) {
            case FORWARD, RECOVER_FROM_START, RECOVER_FROM_END -> Kind.CIRCULAR_PAIR;
            case FALLBACK -> Kind.INDEPENDENT_PAIR;
            case COMPLETE -> Kind.SINGLE_LINE;
        };
    }
}
