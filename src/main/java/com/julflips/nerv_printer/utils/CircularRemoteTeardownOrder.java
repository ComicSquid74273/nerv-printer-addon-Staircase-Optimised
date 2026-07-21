package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Chooses the only remote teardown order that preserves the currently safe
 * endpoint of an interrupted circular U route.
 *
 * <p>An intact or start-connected U is removed from its end back toward its
 * start. Therefore every remotely completed prefix leaves one continuous
 * support path attached to the normal start walkway. A legacy end-connected
 * remainder is removed in its existing start-to-end order so that it remains
 * attached to the end walkway until it is complete.</p>
 */
public final class CircularRemoteTeardownOrder {
    private CircularRemoteTeardownOrder() {
    }

    public static <K> List<K> create(
        List<K> remainingTargetsInRouteOrder,
        CircularMiningRecoveryPlan.Mode recoveryMode
    ) {
        Objects.requireNonNull(
            remainingTargetsInRouteOrder,
            "remainingTargetsInRouteOrder"
        );
        Objects.requireNonNull(recoveryMode, "recoveryMode");

        ArrayList<K> ordered = new ArrayList<>(
            remainingTargetsInRouteOrder
        );
        if (recoveryMode == CircularMiningRecoveryPlan.Mode.FORWARD
            || recoveryMode
                == CircularMiningRecoveryPlan.Mode.RECOVER_FROM_START) {
            Collections.reverse(ordered);
        }
        return List.copyOf(ordered);
    }
}
