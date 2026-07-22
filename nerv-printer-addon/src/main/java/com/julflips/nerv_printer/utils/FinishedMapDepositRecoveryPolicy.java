package com.julflips.nerv_printer.utils;

/**
 * Pure recovery policy for the finished-map destination.
 *
 * <p>The destination may immediately feed a hopper, so after a durably saved
 * deposit request the exact map is allowed to be absent from both the player
 * and the registered input chest. Before that request, absence is not enough
 * evidence to advance.</p>
 */
public final class FinishedMapDepositRecoveryPolicy {
    private FinishedMapDepositRecoveryPolicy() {
    }

    public enum Decision {
        RETRY_DEPOSIT,
        COMPLETE,
        FAIL
    }

    public static Decision decide(
        MapHandoffStage stage,
        boolean expectedMapInPlayer,
        boolean expectedMapInDestination,
        boolean otherFilledMapInPlayer,
        boolean unexpectedSuppliesInPlayer
    ) {
        if ((stage != MapHandoffStage.LOCKED_MAP_CONFIRMED
                && stage != MapHandoffStage.DEPOSIT_REQUESTED)
            || otherFilledMapInPlayer
            || unexpectedSuppliesInPlayer) {
            return Decision.FAIL;
        }
        // The player's exact output always wins over a possible duplicate in
        // the destination: the bot still owns a map that must be deposited.
        if (expectedMapInPlayer) return Decision.RETRY_DEPOSIT;
        if (expectedMapInDestination) return Decision.COMPLETE;
        return stage == MapHandoffStage.DEPOSIT_REQUESTED
            ? Decision.COMPLETE
            : Decision.FAIL;
    }
}
