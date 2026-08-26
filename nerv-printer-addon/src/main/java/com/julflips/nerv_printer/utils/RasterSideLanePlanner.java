package com.julflips.nerv_printer.utils;

import java.util.OptionalDouble;
import java.util.function.DoublePredicate;

/** Pure geometry contract for Staircased Printer's lateral construction lane. */
public final class RasterSideLanePlanner {
    public static final double ABSOLUTE_REACH = 5.90;
    public static final double TARGET_HALF_WIDTH = 0.50;
    public static final double SEPARATION = 0.10;
    public static final double ADJACENT_ROW_WIDTH = 1.00;
    public static final double OUTWARD_SCAN_STEP = 0.25;
    public static final double STRICT_ARRIVAL_TOLERANCE = 0.10;

    private RasterSideLanePlanner() {
    }

    public static double minimumOffset(double expandedVehicleHalfWidth) {
        if (!(expandedVehicleHalfWidth >= 0.0)
            || !Double.isFinite(expandedVehicleHalfWidth)) {
            throw new IllegalArgumentException(
                "Expanded vehicle half-width must be finite and non-negative."
            );
        }
        return TARGET_HALF_WIDTH + expandedVehicleHalfWidth + SEPARATION;
    }

    /** Clears the active row and the full neighboring printable row. */
    public static double minimumAdjacentRowOffset(
        double expandedVehicleHalfWidth
    ) {
        return minimumOffset(expandedVehicleHalfWidth) + ADJACENT_ROW_WIDTH;
    }

    public static double laneX(
        double targetCenterX,
        int lateralDirection,
        double offset
    ) {
        if ((lateralDirection != -1 && lateralDirection != 1)
            || !(offset > 0.0) || !Double.isFinite(offset)
            || !Double.isFinite(targetCenterX)) {
            throw new IllegalArgumentException("Side-lane inputs are invalid.");
        }
        return targetCenterX + lateralDirection * offset;
    }

    /** Deterministically returns the nearest viable outward 0.25 lane. */
    public static OptionalDouble nearestOutwardOffset(
        double minimumOffset,
        DoublePredicate viable
    ) {
        if (!(minimumOffset > 0.0) || !Double.isFinite(minimumOffset)
            || viable == null) {
            throw new IllegalArgumentException(
                "Outward lane scan inputs are invalid."
            );
        }
        for (double offset = minimumOffset;
             offset <= ABSOLUTE_REACH + 1.0e-7;
             offset += OUTWARD_SCAN_STEP) {
            if (viable.test(offset)) return OptionalDouble.of(offset);
        }
        return OptionalDouble.empty();
    }

    public static boolean withinAbsoluteReach(
        double eyeX,
        double eyeY,
        double eyeZ,
        double interactionX,
        double interactionY,
        double interactionZ
    ) {
        double dx = interactionX - eyeX;
        double dy = interactionY - eyeY;
        double dz = interactionZ - eyeZ;
        return dx * dx + dy * dy + dz * dz
            <= ABSOLUTE_REACH * ABSOLUTE_REACH + 1.0e-9;
    }

    public static boolean arrived(
        double actualX,
        double actualY,
        double actualZ,
        double targetX,
        double targetY,
        double targetZ
    ) {
        return Math.abs(actualX - targetX) <= STRICT_ARRIVAL_TOLERANCE
            && Math.abs(actualY - targetY) <= STRICT_ARRIVAL_TOLERANCE
            && Math.abs(actualZ - targetZ) <= STRICT_ARRIVAL_TOLERANCE;
    }

    /** A farther-out pose is still inside the same legal lateral lane. */
    public static boolean atOrOutwardOfLane(
        double actualX,
        double laneX,
        int lateralDirection,
        double tolerance
    ) {
        if ((lateralDirection != -1 && lateralDirection != 1)
            || !(tolerance >= 0.0)
            || !Double.isFinite(actualX)
            || !Double.isFinite(laneX)
            || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException(
                "Outward lane inputs are invalid."
            );
        }
        return lateralDirection < 0
            ? actualX <= laneX + tolerance
            : actualX >= laneX - tolerance;
    }

    /** Same-lane recovery is outward and locally bounded, never cross-map. */
    public static boolean canContinueAlongLane(
        double actualX,
        double laneX,
        int lateralDirection,
        double tolerance
    ) {
        return Math.abs(actualX - laneX) <= ABSOLUTE_REACH + 1.0e-7
            && atOrOutwardOfLane(
            actualX,
            laneX,
            lateralDirection,
            tolerance
        );
    }

    /** Passed-waypoint movement still must finish the safety-critical X shift. */
    public static boolean canAdvanceExteriorLaneShift(
        boolean normallyReached,
        double actualX,
        double laneX,
        int lateralDirection
    ) {
        return normallyReached && atOrOutwardOfLane(
            actualX,
            laneX,
            lateralDirection,
            STRICT_ARRIVAL_TOLERANCE
        );
    }
}
