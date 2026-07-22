package com.julflips.nerv_printer.utils;

/**
 * Selects how a planned build target may be submitted.
 *
 * <p>Callers explicitly authorize the same smart air-placement packet as
 * THM's packet builder. Targets whose state does not depend on player
 * rotation use that direct path even when an adjacent face exists. A
 * rotation-sensitive target retains adjacent placement when possible.</p>
 */
public final class BuildPlacementPolicy {
    private BuildPlacementPolicy() {
    }

    public enum Mode {
        BLOCKED,
        ADJACENT,
        SMART_AIR
    }

    public static Mode select(
        boolean inReach,
        boolean targetReplaceable,
        boolean blockCanBePlaced,
        boolean hasAdjacentSide,
        boolean adjacentSupportPending,
        boolean smartAirAllowed,
        boolean playerRotationRequired
    ) {
        if (!inReach || !targetReplaceable || !blockCanBePlaced) {
            return Mode.BLOCKED;
        }
        if (smartAirAllowed && !playerRotationRequired) {
            return Mode.SMART_AIR;
        }
        if (hasAdjacentSide) {
            return adjacentSupportPending
                ? Mode.BLOCKED
                : Mode.ADJACENT;
        }
        return smartAirAllowed ? Mode.SMART_AIR : Mode.BLOCKED;
    }
}
