package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Converts authoritative U-recovery state into reach-optimizer ownership.
 *
 * <p>A continuous endpoint-attached remainder left by interrupted remote
 * teardown does not by itself require a dedicated traversal. Unless it is the
 * route currently occupied by the recovering player, another forward U may
 * finish that remainder when the complete endpoint-preserving removal order
 * has a proven monotonic reach schedule. Broken or blocked fallback routes
 * remain mandatory.</p>
 */
public final class CircularTeardownRouteEligibility {
    private CircularTeardownRouteEligibility() {
    }

    public record Result(
        boolean complete,
        boolean mustTraverse,
        boolean canHostRemoteTeardown
    ) {
    }

    public static Result classify(
        CircularMiningRecoveryPlan.Mode recoveryMode,
        boolean preferredLocalRoute
    ) {
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        return switch (recoveryMode) {
            case COMPLETE -> new Result(true, false, false);
            case FORWARD ->
                new Result(false, preferredLocalRoute, true);
            case RECOVER_FROM_START, RECOVER_FROM_END ->
                new Result(false, preferredLocalRoute, false);
            case FALLBACK -> new Result(false, true, false);
        };
    }
}
