package com.julflips.nerv_printer.utils;

/**
 * Prevents an active U from being abandoned merely because the inventory is
 * short for the whole route. Egress is reserved for an exhausted material at
 * the blocked walking frontier after all other reachable mandatory work has
 * been consumed.
 */
public final class ActiveUInventoryRecoveryPolicy {
    private ActiveUInventoryRecoveryPolicy() {
    }

    public enum Action {
        CONTINUE_IN_PLACE,
        EGRESS_AND_RESTOCK
    }

    public static Action decide(
        boolean nextWalkingSupportBlocked,
        boolean requiredSupportStillMissing,
        int requiredMaterialOnHand,
        boolean otherReachableMandatoryWork
    ) {
        if (requiredMaterialOnHand < 0) {
            throw new IllegalArgumentException(
                "Required material count cannot be negative."
            );
        }
        return nextWalkingSupportBlocked
                && requiredSupportStillMissing
                && requiredMaterialOnHand == 0
                && !otherReachableMandatoryWork
            ? Action.EGRESS_AND_RESTOCK
            : Action.CONTINUE_IN_PLACE;
    }
}
