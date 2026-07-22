package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Determines whether ordered world state alone proves that a legacy run was
 * interrupted during teardown.
 *
 * <p>Normal circular construction produces a continuous built prefix and an
 * air suffix. Ordered teardown produces the inverse: an air prefix followed
 * by one continuous remaining suffix. Only the latter is adopted without a
 * durable lifecycle checkpoint; ambiguous shapes remain under setup control.
 * This is intentionally stricter than ordinary in-phase mining recovery.</p>
 */
public final class UncheckpointedTeardownRecoveryPolicy {
    private UncheckpointedTeardownRecoveryPolicy() {
    }

    public static boolean canAdopt(
        MapCyclePhase currentPhase,
        CircularMiningRecoveryPlan.Result route,
        boolean playerOnRemainingSupport
    ) {
        Objects.requireNonNull(currentPhase, "currentPhase");
        Objects.requireNonNull(route, "route");
        return currentPhase == MapCyclePhase.IDLE
            && playerOnRemainingSupport
            && route.mode()
                == CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END
            && route.firstWalkable() > 0;
    }
}
