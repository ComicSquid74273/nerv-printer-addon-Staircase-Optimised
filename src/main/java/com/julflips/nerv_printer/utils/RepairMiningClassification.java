package com.julflips.nerv_printer.utils;

/**
 * Pure scheduling classification for one printing-time repair mine.
 *
 * <p>Meteor Speed Mine's Damage mode accelerates vanilla progressive mining,
 * but it does not make a block safe to replace with another mining target in
 * the same client tick. Only vanilla instant breaking is therefore eligible
 * for batched dispatch. Both other classifications retain one progressive
 * target until the caller observes authoritative air.</p>
 */
public enum RepairMiningClassification {
    VANILLA_BATCH_INSTANT(true),
    SPEED_MINE_ACCELERATED_PROGRESSIVE(false),
    SLOW_PROGRESSIVE(false);

    private static final float SPEED_MINE_ACCELERATION_THRESHOLD = 0.5F;

    private final boolean batchDispatchAllowed;

    RepairMiningClassification(boolean batchDispatchAllowed) {
        this.batchDispatchAllowed = batchDispatchAllowed;
    }

    /**
     * Classifies the target using facts calculated with the tool that will be
     * selected for the break.
     *
     * @param vanillaInstant whether vanilla/Meteor considers the target a
     *                       true one-action instant break
     * @param speedMineAccelerationEnabled whether the caller owns an active
     *                                     Speed Mine instamine lease
     * @param speedMineFilterMatches whether that lease admits this block
     * @param blockBreakingDelta the selected tool's vanilla breaking delta
     */
    public static RepairMiningClassification classify(
        boolean vanillaInstant,
        boolean speedMineAccelerationEnabled,
        boolean speedMineFilterMatches,
        float blockBreakingDelta
    ) {
        if (vanillaInstant) return VANILLA_BATCH_INSTANT;
        if (speedMineAccelerationEnabled
            && speedMineFilterMatches
            && blockBreakingDelta
                > SPEED_MINE_ACCELERATION_THRESHOLD) {
            return SPEED_MINE_ACCELERATED_PROGRESSIVE;
        }
        return SLOW_PROGRESSIVE;
    }

    /**
     * Whether another repair target may be dispatched immediately after this
     * one without aborting an owned progressive mine.
     */
    public boolean allowsBatchDispatch() {
        return batchDispatchAllowed;
    }

    public boolean requiresProgressiveContinuation() {
        return !batchDispatchAllowed;
    }
}
