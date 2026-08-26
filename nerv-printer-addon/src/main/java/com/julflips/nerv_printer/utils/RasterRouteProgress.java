package com.julflips.nerv_printer.utils;

/** Geometric progress checks for fast vehicle waypoint following. */
public final class RasterRouteProgress {
    private RasterRouteProgress() {
    }

    public record Point(double x, double y, double z) {
    }

    public static boolean reached(
        Point current,
        Point waypoint,
        double tolerance
    ) {
        if (current == null || waypoint == null || !(tolerance > 0.0)) {
            throw new IllegalArgumentException("Route progress inputs are invalid.");
        }
        return distanceSquared(current, waypoint) <= tolerance * tolerance;
    }

    /**
     * Exterior flight may use a broad horizontal arrival radius at speed, but
     * a descent handoff must reach its safe Y before horizontal entry begins.
     */
    public static boolean reached(
        Point current,
        Point waypoint,
        double horizontalTolerance,
        double verticalTolerance
    ) {
        if (current == null || waypoint == null
            || !(horizontalTolerance > 0.0)
            || !(verticalTolerance > 0.0)) {
            throw new IllegalArgumentException("Route progress inputs are invalid.");
        }
        double dx = current.x() - waypoint.x();
        double dz = current.z() - waypoint.z();
        return dx * dx + dz * dz
                <= horizontalTolerance * horizontalTolerance
            && Math.abs(current.y() - waypoint.y()) <= verticalTolerance;
    }

    public static boolean reachedOrPassed(
        Point previous,
        Point current,
        Point waypoint,
        double tolerance
    ) {
        if (current == null || waypoint == null || !(tolerance > 0.0)) {
            throw new IllegalArgumentException("Route progress inputs are invalid.");
        }
        if (reached(current, waypoint, tolerance)) {
            return true;
        }
        if (previous == null) return false;
        double dx = current.x() - previous.x();
        double dy = current.y() - previous.y();
        double dz = current.z() - previous.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0e-9) return false;
        double projection = (
            (waypoint.x() - previous.x()) * dx
                + (waypoint.y() - previous.y()) * dy
                + (waypoint.z() - previous.z()) * dz
        ) / lengthSquared;
        if (projection < 0.0 || projection > 1.0) return false;
        Point closest = new Point(
            previous.x() + projection * dx,
            previous.y() + projection * dy,
            previous.z() + projection * dz
        );
        return distanceSquared(closest, waypoint) <= tolerance * tolerance;
    }

    /**
     * Accepts a waypoint that is already behind the authoritative vehicle on
     * its compiled segment, even when both observations in the current tick
     * were taken after it was crossed. Cross-track distance remains bounded so
     * a correction onto an unrelated parallel route cannot skip points.
     */
    public static boolean passedOnSegment(
        Point segmentStart,
        Point current,
        Point waypoint,
        double tolerance
    ) {
        if (segmentStart == null || current == null || waypoint == null
            || !(tolerance > 0.0)) {
            throw new IllegalArgumentException("Route progress inputs are invalid.");
        }
        double dx = waypoint.x() - segmentStart.x();
        double dy = waypoint.y() - segmentStart.y();
        double dz = waypoint.z() - segmentStart.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0e-9) return reached(current, waypoint, tolerance);
        double projection = (
            (current.x() - segmentStart.x()) * dx
                + (current.y() - segmentStart.y()) * dy
                + (current.z() - segmentStart.z()) * dz
        ) / lengthSquared;
        if (projection < 1.0) return false;
        Point projected = new Point(
            segmentStart.x() + projection * dx,
            segmentStart.y() + projection * dy,
            segmentStart.z() + projection * dz
        );
        return distanceSquared(projected, current) <= tolerance * tolerance;
    }

    public static double distance(Point left, Point right) {
        return Math.sqrt(distanceSquared(left, right));
    }

    private static double distanceSquared(Point left, Point right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
