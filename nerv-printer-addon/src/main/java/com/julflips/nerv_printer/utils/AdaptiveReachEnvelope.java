package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;

/** Computes a smooth vehicle altitude that keeps raster targets in reach. */
public final class AdaptiveReachEnvelope {
    private AdaptiveReachEnvelope() {
    }

    public record Target(double x, double y, double z) {
    }

    public record Solution(
        double vehicleY,
        double minimumVehicleY,
        double maximumVehicleY,
        boolean sharedEnvelope,
        boolean requiresVerticalStop
    ) {
    }

    public static Solution solve(
        double waypointX,
        double waypointZ,
        double currentVehicleY,
        double eyeOffset,
        double reach,
        double maximumStep,
        List<Target> lookahead
    ) {
        Objects.requireNonNull(lookahead, "lookahead");
        if (lookahead.isEmpty()) {
            throw new IllegalArgumentException("Reach lookahead is empty.");
        }
        if (!(reach > 0) || maximumStep < 0) {
            throw new IllegalArgumentException("Reach and smoothing must be valid.");
        }

        Interval shared = null;
        for (Target target : lookahead) {
            double dx = target.x() - waypointX;
            double dz = target.z() - waypointZ;
            double horizontalSquared = dx * dx + dz * dz;
            if (horizontalSquared > reach * reach) continue;
            double vertical = Math.sqrt(Math.max(0, reach * reach - horizontalSquared));
            Interval candidate = new Interval(
                target.y() - vertical - eyeOffset,
                target.y() + vertical - eyeOffset
            );
            shared = shared == null ? candidate : shared.intersect(candidate);
            if (shared == null) break;
        }

        boolean sharedEnvelope = shared != null;
        Interval usable = sharedEnvelope
            ? shared
            : intervalFor(waypointX, waypointZ, eyeOffset, reach, lookahead.getFirst());
        if (usable == null) {
            Target first = lookahead.getFirst();
            double direct = first.y() - eyeOffset;
            return new Solution(direct, direct, direct, false, true);
        }

        double desired = clamp(currentVehicleY, usable.minimum, usable.maximum);
        double smoothed = clamp(
            desired,
            currentVehicleY - maximumStep,
            currentVehicleY + maximumStep
        );
        boolean requiresStop = smoothed < usable.minimum || smoothed > usable.maximum;
        if (requiresStop) smoothed = desired;
        return new Solution(
            smoothed,
            usable.minimum,
            usable.maximum,
            sharedEnvelope,
            requiresStop || !sharedEnvelope
        );
    }

    private static Interval intervalFor(
        double waypointX,
        double waypointZ,
        double eyeOffset,
        double reach,
        Target target
    ) {
        double dx = target.x() - waypointX;
        double dz = target.z() - waypointZ;
        double horizontalSquared = dx * dx + dz * dz;
        if (horizontalSquared > reach * reach) return null;
        double vertical = Math.sqrt(Math.max(0, reach * reach - horizontalSquared));
        return new Interval(
            target.y() - vertical - eyeOffset,
            target.y() + vertical - eyeOffset
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Interval(double minimum, double maximum) {
        private Interval intersect(Interval other) {
            double nextMinimum = Math.max(minimum, other.minimum);
            double nextMaximum = Math.min(maximum, other.maximum);
            return nextMinimum <= nextMaximum
                ? new Interval(nextMinimum, nextMaximum)
                : null;
        }
    }
}
