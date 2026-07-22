package com.julflips.nerv_printer.utils;

/**
 * Describes the lifecycle of one map from printing through verified cleanup.
 *
 * <p>An NBT pair is safe to archive only at {@link #VERIFIED_CLEAR}. Once the
 * cycle advances to post-mining cleanup, the archive operation must not be
 * attempted again.</p>
 */
public enum MapCyclePhase {
    IDLE(false, false),
    BUILDING(true, false),
    MAP_HANDOFF(true, false),
    MAP_DEPOSITED(true, false),
    MINING(true, false),
    VERIFIED_CLEAR(true, true),
    POST_MINING(true, false);

    private final boolean inProgress;
    private final boolean canArchive;

    MapCyclePhase(boolean inProgress, boolean canArchive) {
        this.inProgress = inProgress;
        this.canArchive = canArchive;
    }

    /**
     * Whether reconnecting must preserve the current map-cycle state.
     */
    public boolean isInProgress() {
        return inProgress;
    }

    /**
     * Whether the original and generated NBT files may be archived now.
     */
    public boolean canArchive() {
        return canArchive;
    }
}
