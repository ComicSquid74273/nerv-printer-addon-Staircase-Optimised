package com.julflips.nerv_printer.utils;

/**
 * Chooses the least disruptive safe response to an authoritative inventory
 * observation that disagrees with a pending hotbar swap.
 */
public final class BuildInventoryConflictPolicy {
    private BuildInventoryConflictPolicy() {
    }

    public enum Action {
        /** Re-read the authoritative slots and keep the current build route. */
        RESYNC_IN_PLACE,
        /** Tool identity is no longer provable, so use grounded build recovery. */
        RECOVER_BUILD,
        /** The conflict does not belong to printing. */
        STOP_NON_BUILD_OWNER
    }

    public static Action decide(
        boolean buildingActive,
        boolean buildMaterialSwapPending,
        boolean buildRepairSwapPending
    ) {
        if (!buildingActive) return Action.STOP_NON_BUILD_OWNER;
        if (buildRepairSwapPending) return Action.RECOVER_BUILD;
        if (buildMaterialSwapPending) return Action.RESYNC_IN_PLACE;
        return Action.STOP_NON_BUILD_OWNER;
    }
}
