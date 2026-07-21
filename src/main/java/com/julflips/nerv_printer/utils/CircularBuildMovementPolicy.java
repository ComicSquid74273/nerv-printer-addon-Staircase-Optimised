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
        NEXT_ROUTE_SUPPORT_CONFIRMATION,
        OTHER_BUILD_ACTION
    }

    public enum ReachDeadlineAction {
        CONTINUE,
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
     * Selects bounded route behavior for one still-missing frozen neighbor.
     * Missing placement work never owns a stationary movement hold. While the
     * target remains live-reachable, normal movement and placement continue in
     * parallel. At the final guaranteed support, a target that is still not
     * live-reachable starts a recovery sweep over the same verified route.
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
        if (alreadyBacktracking) {
            return ReachDeadlineAction.CONTINUE;
        }
        if (currentSupportIndex >= lastGuaranteedSupportIndex
            && !currentlyReachable) {
            return ReachDeadlineAction.BACKTRACK_ON_ROUTE;
        }
        return ReachDeadlineAction.CONTINUE;
    }

    /**
     * Reverses a recovery sweep only at its route-derived boundaries. Keeping
     * the direction as explicit state prevents a one-support backtrack from
     * changing direction again as soon as the cursor enters the next cell.
     */
    public static int reachSweepDirection(
        int currentSupportIndex,
        int firstSweepSupportIndex,
        int lastSweepSupportIndex,
        int currentDirection
    ) {
        if (firstSweepSupportIndex < 0
            || lastSweepSupportIndex < firstSweepSupportIndex) {
            throw new IllegalArgumentException(
                "The reach-sweep boundaries are invalid."
            );
        }
        if (currentDirection != -1 && currentDirection != 1) {
            throw new IllegalArgumentException(
                "The reach-sweep direction must be -1 or 1."
            );
        }
        if (currentDirection < 0
            && currentSupportIndex <= firstSweepSupportIndex) {
            return 1;
        }
        if (currentDirection > 0
            && currentSupportIndex >= lastSweepSupportIndex) {
            return -1;
        }
        return currentDirection;
    }
}
