package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure launch-to-route geometry. The ingress rises in the launch column,
 * moves wholly outside one horizontal side of the map, follows that exterior
 * corridor to the selected north/south access row, and only then descends.
 * Construction-route points are deliberately outside this plan.
 */
public final class RasterExteriorIngressPlan {
    private static final double EPSILON = 1.0e-7;

    private RasterExteriorIngressPlan() {
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z)) {
                throw new IllegalArgumentException(
                    "Ingress coordinates must be finite."
                );
            }
        }

        public double distanceTo(Point other) {
            Objects.requireNonNull(other, "other");
            double dx = other.x - x;
            double dy = other.y - y;
            double dz = other.z - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** Block-footprint edges: maximum values are exclusive. */
    public record Bounds(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ
    ) {
        public Bounds {
            if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX)
                || !Double.isFinite(minimumZ) || !Double.isFinite(maximumZ)
                || minimumX >= maximumX || minimumZ >= maximumZ) {
                throw new IllegalArgumentException(
                    "Ingress bounds must be finite and non-empty."
                );
            }
        }
    }

    public enum Side {
        WEST,
        EAST
    }

    /** Never crosses the map below its known rider-clear cruise altitude. */
    public static double mapClearCruiseY(
        double startY,
        double mapClearY
    ) {
        if (!Double.isFinite(startY) || !Double.isFinite(mapClearY)) {
            throw new IllegalArgumentException(
                "Ingress cruise inputs must be finite."
            );
        }
        return Math.max(startY, mapClearY);
    }

    /** Nearby exterior columns to try when the current rise is obstructed. */
    public static List<Point> verticalStagingCandidates(
        Point start,
        Bounds bounds,
        double exteriorMargin,
        double extraClearance
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(bounds, "bounds");
        if (!(exteriorMargin > 0.0)
            || !(extraClearance >= 0.0)
            || !Double.isFinite(exteriorMargin)
            || !Double.isFinite(extraClearance)) {
            throw new IllegalArgumentException(
                "Exterior staging clearance is invalid."
            );
        }
        double offset = exteriorMargin + extraClearance;
        ArrayList<Point> candidates = new ArrayList<>(4);
        candidates.add(new Point(
            bounds.minimumX - offset, start.y, start.z
        ));
        candidates.add(new Point(
            bounds.maximumX + offset, start.y, start.z
        ));
        candidates.add(new Point(
            start.x, start.y, bounds.minimumZ - offset
        ));
        candidates.add(new Point(
            start.x, start.y, bounds.maximumZ + offset
        ));
        candidates.sort(Comparator.comparingDouble(start::distanceTo));
        return List.copyOf(candidates);
    }

    public record Candidate(Side side, List<Point> waypoints) {
        public Candidate {
            Objects.requireNonNull(side, "side");
            waypoints = List.copyOf(waypoints);
            if (waypoints.isEmpty()) {
                throw new IllegalArgumentException(
                    "An ingress candidate requires at least one waypoint."
                );
            }
        }

        public double lengthFrom(Point start) {
            Point previous = Objects.requireNonNull(start, "start");
            double length = 0.0;
            for (Point waypoint : waypoints) {
                length += previous.distanceTo(waypoint);
                previous = waypoint;
            }
            return length;
        }
    }

    /**
     * Deterministic southwest ingress requested by Boat Raster: rise in the
     * launch column, travel north without changing X, turn east at the north
     * access row, then descend into the retained route.
     */
    public static Candidate northThenEast(
        Point start,
        Point access,
        double cruiseY
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(access, "access");
        if (!Double.isFinite(cruiseY) || cruiseY + EPSILON < start.y) {
            throw new IllegalArgumentException(
                "Ingress cruise height is invalid."
            );
        }
        ArrayList<Point> route = new ArrayList<>();
        appendDifferent(route, start, new Point(start.x, cruiseY, start.z));
        appendDifferent(route, start, new Point(start.x, cruiseY, access.z));
        appendDifferent(route, start, new Point(access.x, cruiseY, access.z));
        appendDifferent(route, start, access);
        return new Candidate(Side.WEST, route);
    }

    /** Rise vertically, cruise directly, then descend at the destination. */
    public static List<Point> directAerial(
        Point start,
        Point destination,
        double cruiseY
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(destination, "destination");
        if (!Double.isFinite(cruiseY)
            || cruiseY + EPSILON < start.y
            || cruiseY + EPSILON < destination.y) {
            throw new IllegalArgumentException(
                "Direct aerial cruise height is invalid."
            );
        }
        ArrayList<Point> route = new ArrayList<>(3);
        appendDifferent(
            route, start, new Point(start.x, cruiseY, start.z)
        );
        appendDifferent(
            route,
            start,
            new Point(destination.x, cruiseY, destination.z)
        );
        appendDifferent(route, start, destination);
        if (route.isEmpty()) route.add(destination);
        return List.copyOf(route);
    }

    /**
     * Flat exterior travel: leave/retain the west exterior column, travel
     * north/south without changing Y, cross X only at the exterior endpoint,
     * then perform the single required endpoint height handoff.
     */
    public static Candidate flatWestExterior(
        Point start,
        Point destination,
        Bounds bounds,
        double exteriorMargin
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(bounds, "bounds");
        if (!(exteriorMargin > 0.0) || !Double.isFinite(exteriorMargin)) {
            throw new IllegalArgumentException(
                "Flat exterior margin must be finite and positive."
            );
        }
        double exteriorX = bounds.minimumX - exteriorMargin;
        ArrayList<Point> route = new ArrayList<>();
        Point previous = start;
        Point west = new Point(exteriorX, start.y, start.z);
        appendDifferent(route, previous, west);
        if (!route.isEmpty()) previous = route.getLast();
        Point longitudinal = new Point(
            exteriorX, start.y, destination.z
        );
        appendDifferent(route, previous, longitudinal);
        if (!route.isEmpty()) previous = route.getLast();
        Point horizontalArrival = new Point(
            destination.x, start.y, destination.z
        );
        appendDifferent(route, previous, horizontalArrival);
        if (!route.isEmpty()) previous = route.getLast();
        appendDifferent(route, previous, destination);
        if (route.isEmpty()) route.add(destination);
        return new Candidate(Side.WEST, route);
    }

    /**
     * Stages a vertical entry handoff while the boat is still outside the
     * footprint, preventing a diagonal from cutting through printable columns.
     */
    public static List<Point> descendBeforeHorizontalEntry(
        Point exterior,
        Point firstInterior
    ) {
        return safeVerticalDogleg(exterior, firstInterior);
    }

    /**
     * Decomposes a simultaneous horizontal/vertical handoff at the lower Y.
     * Descents happen before horizontal motion; ascents happen afterward.
     */
    public static List<Point> safeVerticalDogleg(
        Point start,
        Point destination
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(destination, "destination");
        boolean horizontalMove = Math.hypot(
            destination.x - start.x,
            destination.z - start.z
        ) > EPSILON;
        boolean verticalMove = Math.abs(destination.y - start.y) > EPSILON;
        if (!horizontalMove || !verticalMove) {
            return List.of(destination);
        }
        if (destination.y < start.y) {
            return List.of(
                new Point(start.x, destination.y, start.z),
                destination
            );
        }
        return List.of(
            new Point(destination.x, start.y, destination.z),
            destination
        );
    }

    /**
     * Returns west/east exterior U-routes, shortest first. Every route performs
     * the vertical lift before horizontal motion and descends only at access.
     */
    public static List<Candidate> candidates(
        Point start,
        Point access,
        Bounds bounds,
        double cruiseY,
        double exteriorMargin
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(cruiseY) || cruiseY + EPSILON < start.y
            || !(exteriorMargin > 0.0)
            || !Double.isFinite(exteriorMargin)) {
            throw new IllegalArgumentException(
                "Ingress cruise height and margin are invalid."
            );
        }

        ArrayList<Candidate> result = new ArrayList<>(2);
        result.add(candidate(
            Side.WEST,
            start,
            access,
            cruiseY,
            bounds.minimumX - exteriorMargin
        ));
        result.add(candidate(
            Side.EAST,
            start,
            access,
            cruiseY,
            bounds.maximumX + exteriorMargin
        ));
        result.sort(
            Comparator.comparingDouble((Candidate route) ->
                route.lengthFrom(start))
                .thenComparing(Candidate::side)
        );
        return List.copyOf(result);
    }

    private static Candidate candidate(
        Side side,
        Point start,
        Point access,
        double cruiseY,
        double outsideX
    ) {
        ArrayList<Point> route = new ArrayList<>();
        appendDifferent(route, start, new Point(start.x, cruiseY, start.z));
        appendDifferent(route, start, new Point(outsideX, cruiseY, start.z));
        appendDifferent(route, start, new Point(outsideX, cruiseY, access.z));
        appendDifferent(route, start, new Point(access.x, cruiseY, access.z));
        appendDifferent(route, start, access);
        return new Candidate(side, route);
    }

    private static void appendDifferent(
        List<Point> route,
        Point start,
        Point candidate
    ) {
        Point previous = route.isEmpty() ? start : route.getLast();
        if (previous.distanceTo(candidate) > EPSILON) route.add(candidate);
    }
}
