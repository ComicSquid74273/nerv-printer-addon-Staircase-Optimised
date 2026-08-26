package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Precomputed, immutable print-flight geometry for one complete raster. */
public final class RasterFlightPlan {
    /**
     * Keeps the rider's eyes below the target underside; with the mounted
     * rider offset this places the boat itself about two blocks beneath it.
     */
    public static final double EYE_CLEARANCE_BELOW_SURFACE = 0.75;

    private RasterFlightPlan() {
    }

    public record Target(
        int x,
        int y,
        int z,
        int sweepLine,
        int direction
    ) {
        public Target(int x, int y, int z) {
            this(x, y, z, z + 1, ((z + 1) & 1) == 0 ? 1 : -1);
        }

        public Target {
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException("Sweep direction must be -1 or 1.");
            }
        }
    }

    public record Waypoint(
        int targetIndex,
        int row,
        int direction,
        double x,
        double eyeY,
        double z,
        boolean verticalStop,
        boolean descendFirst,
        boolean rowTurn,
        int maximumLeadCells
    ) {
    }

    public record Plan(List<Waypoint> waypoints, double pathLength) {
        public Plan {
            waypoints = List.copyOf(waypoints);
        }
    }

    public static Plan create(
        List<Target> orderedTargets,
        double interactionRange,
        double maximumEyeStep,
        int altitudeLookahead,
        int maximumLeadCells
    ) {
        Objects.requireNonNull(orderedTargets, "orderedTargets");
        if (orderedTargets.isEmpty()) {
            throw new IllegalArgumentException("Raster flight plan requires at least one target.");
        }
        if (!(interactionRange > 0.0) || maximumEyeStep < 0.0
            || altitudeLookahead < 1 || maximumLeadCells < 0) {
            throw new IllegalArgumentException("Raster flight parameters are invalid.");
        }

        ArrayList<Waypoint> result = new ArrayList<>(orderedTargets.size());
        Target first = orderedTargets.getFirst();
        double currentEyeY = first.y() - EYE_CLEARANCE_BELOW_SURFACE;
        double pathLength = 0.0;
        Waypoint previous = null;

        for (int index = 0; index < orderedTargets.size(); index++) {
            Target target = orderedTargets.get(index);
            double waypointX = target.x() + 0.5;
            double waypointZ = target.z() + 0.5;
            double nextEyeY = target.y() - EYE_CLEARANCE_BELOW_SURFACE;
            double eyeDelta = previous == null
                ? 0.0
                : nextEyeY - previous.eyeY();
            currentEyeY = nextEyeY;
            boolean rowTurn = previous != null && (
                orderedTargets.get(index - 1).sweepLine()
                    != target.sweepLine()
                    || Math.abs(waypointX - previous.x()) > 1.01
                    || Math.abs(waypointZ - previous.z()) > 1.01
            );
            int row = target.sweepLine();
            int direction = target.direction();
            int safeLead = RasterReachBarrier.maximumLeadCells(
                interactionRange,
                Math.abs(currentEyeY - (target.y() + 0.5)),
                0.5,
                maximumLeadCells
            );
            Waypoint waypoint = new Waypoint(
                index,
                row,
                direction,
                waypointX,
                currentEyeY,
                waypointZ,
                Math.abs(eyeDelta) > maximumEyeStep,
                eyeDelta < -maximumEyeStep,
                rowTurn,
                safeLead
            );
            if (previous != null) {
                double dx = waypoint.x() - previous.x();
                double dy = waypoint.eyeY() - previous.eyeY();
                double dz = waypoint.z() - previous.z();
                pathLength += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            result.add(waypoint);
            previous = waypoint;
        }
        return new Plan(result, pathLength);
    }
}
