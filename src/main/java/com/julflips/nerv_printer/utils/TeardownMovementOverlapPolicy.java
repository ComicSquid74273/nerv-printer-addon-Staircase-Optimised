package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Teardown-only policy for continuing ordered movement while one break target
 * remains server-owned. Printing and active-U repair do not use this policy.
 */
public final class TeardownMovementOverlapPolicy {
    private TeardownMovementOverlapPolicy() {
    }

    public static boolean mayContinue(
        boolean overlapArmed,
        boolean actionBudgetPaused,
        boolean targetInReach,
        boolean routeAdvanceRetainsReach,
        RepairMiningClassification classification,
        boolean ownedThmSpeedMineActive
    ) {
        Objects.requireNonNull(classification, "classification");
        return overlapArmed
            && !actionBudgetPaused
            && targetInReach
            && routeAdvanceRetainsReach
            && (classification.allowsOwnedRouteMovementOverlap()
                || ownedThmSpeedMineActive);
    }
}
