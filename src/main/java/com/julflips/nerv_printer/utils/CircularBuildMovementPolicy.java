package com.julflips.nerv_printer.utils;

/**
 * Decides whether an active circular build action must temporarily hold route
 * movement.
 *
 * <p>Placement is deliberately not an input. The scheduler may submit every
 * independently placeable target while route movement continues, and the
 * ordered support guard separately blocks the exact next walking support until
 * the server confirms it. Only an owned repair or a mandatory inventory
 * transaction must stop horizontal movement here.</p>
 */
public final class CircularBuildMovementPolicy {
    private CircularBuildMovementPolicy() {
    }

    public enum HoldReason {
        NONE,
        ACTIVE_U_REPAIR,
        HOTBAR_SWAP_CONFIRMATION,
        DEFERRED_U_PLACEMENT_CONFIRMATION,
        NEXT_ROUTE_SUPPORT_CONFIRMATION,
        OTHER_BUILD_ACTION
    }

    public enum ReachDeadlineAction {
        CONTINUE,
        HOLD_FOR_PLACEMENT,
        BACKTRACK_ON_ROUTE
    }

    public static HoldReason holdReason(
        int repairTargetCount,
        boolean mandatoryHotbarSwapPending
    ) {
        if (repairTargetCount < 0) {
            throw new IllegalArgumentException(
                "Repair target count cannot be negative."
            );
        }
        if (repairTargetCount > 0) {
            return HoldReason.ACTIVE_U_REPAIR;
        }
        if (mandatoryHotbarSwapPending) {
            return HoldReason.HOTBAR_SWAP_CONFIRMATION;
        }
        return HoldReason.NONE;
    }

    /**
     * Returns whether an unconfirmed deferred target has reached the last
     * support from which the route can still guarantee its placement.
     *
     * <p>A pending placement by itself must never stop walking. The route can
     * continue through every earlier reachable support and waits only on entry
     * to the final conservative reach cell. This leaves one support of margin
     * for the server acknowledgement without consuming movement or an active
     * auto-jump while the target is still safely reachable far ahead.</p>
     */
    public static boolean requiresDeferredPlacementHold(
        int currentSupportIndex,
        int lastReachSupportIndex
    ) {
        if (lastReachSupportIndex < 0) {
            throw new IllegalArgumentException(
                "The last reachable support index cannot be negative."
            );
        }
        int deadlineEntryIndex = Math.max(
            0,
            lastReachSupportIndex - 1
        );
        return currentSupportIndex >= deadlineEntryIndex;
    }

    /**
     * Selects bounded route behavior for one still-missing frozen neighbor.
     * A stale live-range sample cannot let forward movement consume a proven
     * deadline. If lag has already moved beyond it, only the same ordered
     * support path may be reversed.
     */
    public static ReachDeadlineAction reachDeadlineAction(
        int currentSupportIndex,
        int lastGuaranteedSupportIndex,
        boolean currentlyReachable,
        boolean alreadyBacktracking
    ) {
        if (lastGuaranteedSupportIndex < 0) {
            throw new IllegalArgumentException(
                "The last guaranteed support index cannot be negative."
            );
        }
        if (alreadyBacktracking
            && currentSupportIndex > lastGuaranteedSupportIndex) {
            return ReachDeadlineAction.CONTINUE;
        }
        if (currentSupportIndex > lastGuaranteedSupportIndex
            && !currentlyReachable) {
            return ReachDeadlineAction.BACKTRACK_ON_ROUTE;
        }
        return requiresDeferredPlacementHold(
            currentSupportIndex,
            lastGuaranteedSupportIndex
        )
            ? ReachDeadlineAction.HOLD_FOR_PLACEMENT
            : ReachDeadlineAction.CONTINUE;
    }
}
