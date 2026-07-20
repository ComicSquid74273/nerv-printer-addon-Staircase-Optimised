package com.julflips.nerv_printer.utils;

/**
 * Durable substages for the one-master empty-map -> locked-map handoff.
 *
 * <p>The cartography lock operation creates a new map ID, so both the source
 * and locked IDs are retained once they exist.</p>
 */
public enum MapHandoffStage {
    NONE(false, false),
    PREPARE_INVENTORY(false, false),
    NEED_SUPPLIES(false, false),
    SUPPLIES_CONFIRMED(false, false),
    SOURCE_MAP_CONFIRMED(true, false),
    LOCKED_MAP_CONFIRMED(true, true),
    DEPOSIT_REQUESTED(true, true),
    DEPOSITED(true, true),
    SKIPPED(false, false);

    private final boolean requiresSourceMapId;
    private final boolean requiresLockedMapId;

    MapHandoffStage(
        boolean requiresSourceMapId,
        boolean requiresLockedMapId
    ) {
        this.requiresSourceMapId = requiresSourceMapId;
        this.requiresLockedMapId = requiresLockedMapId;
    }

    public boolean requiresSourceMapId() {
        return requiresSourceMapId;
    }

    public boolean requiresLockedMapId() {
        return requiresLockedMapId;
    }

    /**
     * Checks the exact map identities required by this durable stage.
     *
     * <p>The cartography output must have a different ID from the source
     * map. Keeping this invariant in the checkpoint model prevents a damaged
     * status file from turning an unlocked source map into proof of a
     * finished-map deposit.</p>
     */
    public boolean hasValidMapIds(
        Integer sourceMapId,
        Integer lockedMapId
    ) {
        if (requiresSourceMapId
            && (sourceMapId == null || sourceMapId < 0)) {
            return false;
        }
        return !requiresLockedMapId
            || (lockedMapId != null
                && lockedMapId >= 0
                && !lockedMapId.equals(sourceMapId));
    }

    public boolean isValidFor(MapCyclePhase phase) {
        return switch (phase) {
            case IDLE, BUILDING -> this == NONE;
            case MAP_HANDOFF ->
                this != NONE && this != DEPOSITED;
            case MAP_DEPOSITED, MINING, VERIFIED_CLEAR, POST_MINING ->
                this == DEPOSITED || this == SKIPPED;
        };
    }
}
