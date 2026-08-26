package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, NBT-derived BoatFly construction route.
 *
 * <p>Build points follow complete north/south strip passes from a lateral lane
 * beside the active row. Height changes remain in that lane, and every band
 * change exits through the endpoint just completed, shifts X outside the
 * footprint, and enters the next row through that same endpoint. Successive
 * rows can therefore form one continuous north/south serpentine.</p>
 */
public final class RasterBuildRoutePlan {
    public static final double DEFAULT_EXTERIOR_MARGIN = 3.5;
    public static final double MAXIMUM_TRANSIT_SEGMENT = 8.0;

    private RasterBuildRoutePlan() {
    }

    public enum Phase {
        BUILD,
        SIDE_TRANSIT,
        HEIGHT_CONNECTOR,
        BAND_CONNECTOR
    }

    public record Point<T>(
        double x,
        double eyeY,
        double z,
        int band,
        int pass,
        int direction,
        Phase phase,
        List<T> placementDeadlines
    ) {
        public Point {
            Objects.requireNonNull(phase, "phase");
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException(
                    "Route-point direction must be -1 or 1."
                );
            }
            placementDeadlines = List.copyOf(placementDeadlines);
        }

        public boolean constructionPoint() {
            return phase == Phase.BUILD;
        }
    }

    public record Plan<T>(
        List<Point<T>> points,
        Map<T, Integer> deadlineByTarget,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ,
        double exteriorMargin,
        double minimumEyeY,
        int lateralDirection
    ) {
        public Plan {
            points = List.copyOf(points);
            deadlineByTarget = Map.copyOf(deadlineByTarget);
            if (points.isEmpty() || minimumX > maximumX
                || minimumZ > maximumZ || !(exteriorMargin > 0.0)
                || (lateralDirection != -1 && lateralDirection != 1)) {
                throw new IllegalArgumentException("Compiled route is invalid.");
            }
        }

        public int deadline(T target) {
            Integer index = deadlineByTarget.get(target);
            if (index == null) {
                throw new IllegalArgumentException(
                    "Target is absent from the compiled route."
                );
            }
            return index;
        }

        public int replayIndexFor(T target, int replayPoints) {
            if (replayPoints < 0) {
                throw new IllegalArgumentException(
                    "Replay-point count cannot be negative."
                );
            }
            return replayIndex(deadline(target), replayPoints);
        }

        /**
         * Reconciles a persisted physical route cursor with the first unfinished
         * target. Old checkpoints can contain a cursor far behind an already
         * confirmed frontier, while a cursor beyond the target deadline has
         * already missed that target. Both cases replay from the bounded target
         * deadline; an in-window cursor is retained exactly.
         */
        public int resumeIndexFor(
            T firstUnfinishedTarget,
            int persistedRouteIndex,
            int replayPoints
        ) {
            int targetDeadline = deadline(firstUnfinishedTarget);
            int replayIndex = replayIndex(targetDeadline, replayPoints);
            int persisted = Math.clamp(
                persistedRouteIndex,
                0,
                points.size() - 1
            );
            if (persisted < replayIndex || persisted > targetDeadline) {
                return replayIndex;
            }
            return persisted;
        }

        public int replayIndex(int retainedIndex, int replayPoints) {
            if (replayPoints < 0) {
                throw new IllegalArgumentException(
                    "Replay-point count cannot be negative."
                );
            }
            int index = Math.clamp(retainedIndex, 0, points.size() - 1);
            return Math.max(0, index - replayPoints);
        }

        /** Safe exterior pose paired with one retained route point. */
        public Point<T> exteriorAnchor(int routeIndex) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            double north = minimumZ - exteriorMargin;
            double south = maximumZ + 1.0 + exteriorMargin;
            boolean useNorth = Math.abs(retained.z() - north)
                <= Math.abs(south - retained.z());
            return exteriorAnchor(routeIndex, useNorth);
        }

        /** Last exterior route point at or behind the retained cursor. */
        public int previousExteriorIndex(int routeIndex) {
            int retained = Math.clamp(routeIndex, 0, points.size() - 1);
            for (int index = retained; index >= 0; index--) {
                if (isExterior(points.get(index))) return index;
            }
            return 0;
        }

        /** First compiled exterior connector at or after the retained point. */
        public int nextExteriorIndex(int routeIndex) {
            int retained = Math.clamp(routeIndex, 0, points.size() - 1);
            for (int index = retained; index < points.size(); index++) {
                if (isExterior(points.get(index))) return index;
            }
            return points.size() - 1;
        }

        public boolean isExteriorIndex(int routeIndex) {
            return isExterior(points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            )));
        }

        /**
         * Continues in the row's compiled direction until the mounted route
         * is outside. It never reverses through newly completed targets.
         */
        public List<Point<T>> forwardEgressAlongRoute(int routeIndex) {
            int retained = Math.clamp(routeIndex, 0, points.size() - 1);
            int exterior = nextExteriorIndex(retained);
            ArrayList<Point<T>> route = new ArrayList<>();
            for (int index = retained + 1; index <= exterior; index++) {
                route.add(points.get(index));
            }
            if (!isExterior(points.get(exterior))) {
                Point<T> end = points.get(exterior);
                boolean north = end.direction() < 0;
                Point<T> synthetic = exteriorAnchor(exterior, north);
                if (!samePose(end, synthetic)) route.add(synthetic);
            }
            return List.copyOf(route);
        }

        /**
         * Open-air access paired with the last exterior point. The first band
         * has no preceding connector, so its access is a same-height point
         * immediately north/south of route point zero.
         */
        public Point<T> previousExteriorAccess(int routeIndex) {
            int accessIndex = previousExteriorIndex(routeIndex);
            Point<T> access = points.get(accessIndex);
            if (isExterior(access)) return access;
            double north = minimumZ - exteriorMargin;
            double south = maximumZ + 1.0 + exteriorMargin;
            double exteriorZ = Math.abs(access.z() - north)
                <= Math.abs(south - access.z()) ? north : south;
            return new Point<>(
                access.x(),
                access.eyeY(),
                exteriorZ,
                access.band(),
                access.pass(),
                access.direction(),
                Phase.BAND_CONNECTOR,
                List.of()
            );
        }

        /** Reverse only through already-retained adjacent route points. */
        public List<Point<T>> egressAlongRoute(int routeIndex) {
            int retained = Math.clamp(routeIndex, 0, points.size() - 1);
            int accessIndex = previousExteriorIndex(retained);
            ArrayList<Point<T>> route = new ArrayList<>();
            for (int index = retained - 1; index >= accessIndex; index--) {
                route.add(points.get(index));
            }
            Point<T> access = points.get(accessIndex);
            if (!isExterior(access)) {
                route.add(previousExteriorAccess(retained));
            }
            return List.copyOf(route);
        }

        /** Forward replay from the paired exterior access to the cursor. */
        public List<Point<T>> entryAlongRoute(int routeIndex) {
            int retained = Math.clamp(routeIndex, 0, points.size() - 1);
            int accessIndex = previousExteriorIndex(retained);
            ArrayList<Point<T>> route = new ArrayList<>();
            Point<T> access = points.get(accessIndex);
            // Always begin at the same physical exterior access where the
            // reverse route ends. The first band has no compiled exterior
            // point, so include its synthetic access before route point zero.
            route.add(previousExteriorAccess(retained));
            if (!isExterior(access)) route.add(access);
            for (int index = accessIndex + 1; index <= retained; index++) {
                route.add(points.get(index));
            }
            return List.copyOf(route);
        }

        public Point<T> exteriorAnchor(
            int routeIndex,
            boolean north
        ) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            double exteriorZ = north
                ? minimumZ - exteriorMargin
                : maximumZ + 1.0 + exteriorMargin;
            return transit(
                retained.x(), minimumEyeY, exteriorZ,
                retained, Phase.BAND_CONNECTOR
            );
        }

        /** Route from the retained side-lane point to its exterior anchor. */
        public List<Point<T>> egress(int routeIndex) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            double north = minimumZ - exteriorMargin;
            double south = maximumZ + 1.0 + exteriorMargin;
            return egress(
                routeIndex,
                Math.abs(retained.z() - north)
                    <= Math.abs(south - retained.z())
            );
        }

        public List<Point<T>> egress(
            int routeIndex,
            boolean north
        ) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            Point<T> anchor = exteriorAnchor(routeIndex, north);
            ArrayList<Point<T>> route = new ArrayList<>();
            route.add(retained);
            appendIfDifferent(route, transit(
                retained.x(), minimumEyeY, retained.z(),
                retained, Phase.BAND_CONNECTOR
            ));
            appendHorizontal45(
                route,
                retained.x(), retained.z(),
                anchor.x(), anchor.z(),
                minimumEyeY,
                retained,
                Phase.BAND_CONNECTOR
            );
            return List.copyOf(route.subList(1, route.size()));
        }

        /** Exact reverse of {@link #egress(int)}, ending on the route point. */
        public List<Point<T>> entry(int routeIndex) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            double north = minimumZ - exteriorMargin;
            double south = maximumZ + 1.0 + exteriorMargin;
            return entry(
                routeIndex,
                Math.abs(retained.z() - north)
                    <= Math.abs(south - retained.z())
            );
        }

        public List<Point<T>> entry(
            int routeIndex,
            boolean north
        ) {
            Point<T> retained = points.get(Math.clamp(
                routeIndex, 0, points.size() - 1
            ));
            Point<T> anchor = exteriorAnchor(routeIndex, north);
            ArrayList<Point<T>> route = new ArrayList<>();
            appendHorizontal45(
                route,
                anchor.x(), anchor.z(),
                retained.x(), retained.z(),
                minimumEyeY,
                retained,
                Phase.BAND_CONNECTOR
            );
            // Preserve the authoritative build-point metadata even when the
            // final connector pose has identical coordinates.
            route.add(retained);
            return List.copyOf(route);
        }

        private boolean isExterior(Point<T> point) {
            return point.x() < minimumX
                || point.x() > maximumX + 1.0
                || point.z() < minimumZ
                || point.z() > maximumZ + 1.0;
        }
    }

    public static <T> Plan<T> create(
        RasterThreeLanePathPlanner.Plan<T> stripPlan,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ,
        double exteriorMargin
    ) {
        Objects.requireNonNull(stripPlan, "stripPlan");
        if (stripPlan.passPoints().isEmpty() || minimumX > maximumX
            || minimumZ > maximumZ || !(exteriorMargin > 0.0)) {
            throw new IllegalArgumentException(
                "Build-route inputs are invalid."
            );
        }

        ArrayList<Point<T>> result = new ArrayList<>();
        LinkedHashMap<T, Integer> deadlines = new LinkedHashMap<>();
        RasterThreeLanePathPlanner.PassPoint<T> previousPassPoint = null;

        for (RasterThreeLanePathPlanner.PassPoint<T> passPoint
            : stripPlan.passPoints()) {
            Point<T> build = buildPoint(passPoint);
            if (previousPassPoint != null) {
                Point<T> previous = result.getLast();
                if (previousPassPoint.band() == passPoint.band()) {
                    if (previousPassPoint.pass() == passPoint.pass()) {
                        appendContinuousSideRamp(result, previous, build);
                    } else {
                        appendBandTransition(
                            result,
                            previous,
                            build,
                            minimumX,
                            maximumX,
                            minimumZ,
                            maximumZ,
                            exteriorMargin
                        );
                    }
                } else {
                    appendBandTransition(
                        result,
                        previous,
                        build,
                        minimumX,
                        maximumX,
                        minimumZ,
                        maximumZ,
                        exteriorMargin
                    );
                }
            }

            if (!result.isEmpty() && samePose(result.getLast(), build)) {
                result.set(result.size() - 1, build);
            } else {
                result.add(build);
            }
            int deadline = result.size() - 1;
            for (T target : build.placementDeadlines()) {
                if (deadlines.put(target, deadline) != null) {
                    throw new IllegalArgumentException(
                        "A structural target was assigned more than once."
                    );
                }
            }
            previousPassPoint = passPoint;
        }

        if (deadlines.size() != stripPlan.assignments().size()) {
            throw new IllegalArgumentException(
                "Compiled route did not schedule every structural target."
            );
        }
        double minimumEyeY = result.stream()
            .mapToDouble(Point::eyeY)
            .min().orElseThrow();
        int lateralDirection = lateralDirection(stripPlan.passPoints());
        return new Plan<>(
            result,
            deadlines,
            minimumX,
            maximumX,
            minimumZ,
            maximumZ,
            exteriorMargin,
            minimumEyeY,
            lateralDirection
        );
    }

    private static int lateralDirection(
        List<? extends RasterThreeLanePathPlanner.PassPoint<?>> points
    ) {
        int firstX = points.getFirst().pathX();
        for (RasterThreeLanePathPlanner.PassPoint<?> point : points) {
            if (point.pathX() != firstX) {
                return point.pathX() > firstX ? 1 : -1;
            }
        }
        return 1;
    }

    private static <T> Point<T> buildPoint(
        RasterThreeLanePathPlanner.PassPoint<T> point
    ) {
        return new Point<>(
            point.pathX() + 0.5,
            point.pathSurfaceY() + 0.5,
            point.pathZ() + 0.5,
            point.band(),
            point.pass(),
            point.direction(),
            Phase.BUILD,
            point.targets().stream()
                .map(RasterThreeLanePathPlanner.Target::payload)
                .toList()
        );
    }

    /**
     * A height change inside one row is part of the lateral flight line, not
     * a stop-and-drop connector. Interpolate it directly with forward row
     * progress so the mounted envelope never descends at a printable column.
     */
    private static <T> void appendContinuousSideRamp(
        List<Point<T>> output,
        Point<T> from,
        Point<T> to
    ) {
        Phase phase = Math.abs(from.eyeY() - to.eyeY()) > 1.0e-6
            ? Phase.HEIGHT_CONNECTOR
            : Phase.SIDE_TRANSIT;
        appendIfDifferent(output, transit(
            to.x(), to.eyeY(), to.z(), to, phase
        ));
    }

    private static <T> void appendBandTransition(
        List<Point<T>> output,
        Point<T> from,
        Point<T> to,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ,
        double margin
    ) {
        double north = minimumZ - margin;
        double south = maximumZ + 1.0 + margin;
        double exteriorZ = Math.abs(from.z() - north)
            <= Math.abs(south - from.z()) ? north : south;
        // Leave the completed row at its current height. All Y and X changes
        // occur only after the entire mounted envelope is beyond the active
        // north/south endpoint.
        appendHorizontal45(
            output,
            from.x(), from.z(), from.x(), exteriorZ,
            from.eyeY(), from, Phase.BAND_CONNECTOR
        );
        appendIfDifferent(output, transit(
            from.x(), to.eyeY(), exteriorZ, to, Phase.BAND_CONNECTOR
        ));
        appendHorizontal45(
            output,
            from.x(), exteriorZ, to.x(), exteriorZ,
            to.eyeY(), to, Phase.BAND_CONNECTOR
        );
        appendHorizontal45(
            output,
            to.x(), exteriorZ, to.x(), to.z(),
            to.eyeY(), to, Phase.BAND_CONNECTOR
        );
    }

    private static <T> void appendHorizontal45(
        List<Point<T>> output,
        double startX,
        double startZ,
        double targetX,
        double targetZ,
        double eyeY,
        Point<T> metadata,
        Phase phase
    ) {
        double dx = targetX - startX;
        double dz = targetZ - startZ;
        double diagonal = Math.min(Math.abs(dx), Math.abs(dz));
        double x = startX;
        double z = startZ;
        if (diagonal > 1.0e-6) {
            double nextX = x + Math.copySign(diagonal, dx);
            double nextZ = z + Math.copySign(diagonal, dz);
            appendHorizontalSegment(
                output,
                x,
                z,
                nextX,
                nextZ,
                eyeY,
                metadata,
                phase
            );
            x = nextX;
            z = nextZ;
        }
        if (Math.abs(targetX - x) > 1.0e-6
            || Math.abs(targetZ - z) > 1.0e-6) {
            appendHorizontalSegment(
                output,
                x,
                z,
                targetX,
                targetZ,
                eyeY,
                metadata,
                phase
            );
        }
    }

    private static <T> void appendHorizontalSegment(
        List<Point<T>> output,
        double startX,
        double startZ,
        double targetX,
        double targetZ,
        double eyeY,
        Point<T> metadata,
        Phase phase
    ) {
        double dx = targetX - startX;
        double dz = targetZ - startZ;
        double distance = Math.hypot(dx, dz);
        int pieces = Math.max(
            1,
            (int) Math.ceil(distance / MAXIMUM_TRANSIT_SEGMENT)
        );
        for (int piece = 1; piece <= pieces; piece++) {
            double fraction = (double) piece / pieces;
            appendIfDifferent(output, transit(
                startX + dx * fraction,
                eyeY,
                startZ + dz * fraction,
                metadata,
                phase
            ));
        }
    }

    private static <T> Point<T> transit(
        double x,
        double eyeY,
        double z,
        Point<T> metadata,
        Phase phase
    ) {
        return new Point<>(
            x,
            eyeY,
            z,
            metadata.band(),
            metadata.pass(),
            metadata.direction(),
            phase,
            List.of()
        );
    }

    private static <T> void appendIfDifferent(
        List<Point<T>> output,
        Point<T> candidate
    ) {
        if (!output.isEmpty()) {
            Point<T> previous = output.getLast();
            if (Math.abs(previous.x() - candidate.x()) < 1.0e-6
                && Math.abs(previous.eyeY() - candidate.eyeY()) < 1.0e-6
                && Math.abs(previous.z() - candidate.z()) < 1.0e-6) {
                return;
            }
            double dx = candidate.x() - previous.x();
            double dy = candidate.eyeY() - previous.eyeY();
            double dz = candidate.z() - previous.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int pieces = (int) Math.ceil(
                distance / MAXIMUM_TRANSIT_SEGMENT
            );
            for (int piece = 1; piece < pieces; piece++) {
                double fraction = (double) piece / pieces;
                output.add(new Point<>(
                    previous.x() + dx * fraction,
                    previous.eyeY() + dy * fraction,
                    previous.z() + dz * fraction,
                    candidate.band(),
                    candidate.pass(),
                    candidate.direction(),
                    candidate.phase(),
                    List.of()
                ));
            }
        }
        output.add(candidate);
    }

    private static boolean samePose(Point<?> left, Point<?> right) {
        return Math.abs(left.x() - right.x()) < 1.0e-6
            && Math.abs(left.eyeY() - right.eyeY()) < 1.0e-6
            && Math.abs(left.z() - right.z()) < 1.0e-6;
    }
}
