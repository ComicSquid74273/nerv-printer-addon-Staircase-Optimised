package com.julflips.nerv_printer.utils;

import java.util.function.IntPredicate;

/** Pure movement gate for immutable Boat Raster placement deadlines. */
public final class RasterRouteDeadlinePolicy {
    private RasterRouteDeadlinePolicy() {
    }

    public enum Decision {
        ADVANCE,
        PLACE_AND_HOLD,
        REPOSITION_SIDE_LANE
    }

    public static Decision decide(
        int routeCursor,
        int placementDeadline,
        boolean confirmed,
        boolean pending,
        boolean inReach
    ) {
        if (routeCursor < 0 || placementDeadline < 0) {
            throw new IllegalArgumentException(
                "Route cursors and deadlines cannot be negative."
            );
        }
        if (confirmed || pending || placementDeadline > routeCursor) {
            return Decision.ADVANCE;
        }
        return inReach
            ? Decision.PLACE_AND_HOLD
            : Decision.REPOSITION_SIDE_LANE;
    }

    /**
     * Defers a nominal deadline to the first later adjacent route point whose
     * strict side-lane pose can actually reach the target. Callers bound the
     * search to the same construction line/pass.
     */
    public static int firstReachableDeadline(
        int nominalDeadline,
        int maximumDeadline,
        IntPredicate reachableAtRoutePoint
    ) {
        if (nominalDeadline < 0 || maximumDeadline < nominalDeadline
            || reachableAtRoutePoint == null) {
            throw new IllegalArgumentException(
                "Deferred route-deadline inputs are invalid."
            );
        }
        for (int index = nominalDeadline;
             index <= maximumDeadline;
             index++) {
            if (reachableAtRoutePoint.test(index)) return index;
        }
        return nominalDeadline;
    }

    public static boolean requiresExactPlacementPose(
        int routeCursor,
        int placementDeadline,
        boolean actualPoseInReach,
        boolean retainedPoseInReach
    ) {
        return routeCursor == placementDeadline
            && !actualPoseInReach
            && retainedPoseInReach;
    }
}
