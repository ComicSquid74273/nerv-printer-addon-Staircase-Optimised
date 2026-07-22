package com.julflips.nerv_printer.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Pure, bounded local bypass planning on a fixed-Y two-dimensional grid.
 */
public final class LogisticsDetourPlanner {
    private LogisticsDetourPlanner() {
    }

    public record Point(int x, int z) {
        public Point offset(Direction direction) {
            Objects.requireNonNull(direction, "direction");
            return new Point(x + direction.dx(), z + direction.dz());
        }

        public int manhattanDistance(Point other) {
            Objects.requireNonNull(other, "other");
            return Math.abs(x - other.x) + Math.abs(z - other.z);
        }
    }

    public enum Direction {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int dx;
        private final int dz;

        Direction(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        public Direction left() {
            return values()[(ordinal() + 3) % values().length];
        }

        public Direction right() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Direction opposite() {
            return values()[(ordinal() + 2) % values().length];
        }
    }

    /**
     * {@code path} includes both the start and endpoint. {@code waypoints}
     * excludes the start and contains only each turn's endpoint plus the final
     * endpoint, so it can be prepended directly to a checkpoint queue.
     */
    public record Plan(Point endpoint, List<Point> path, List<Point> waypoints) {
        public Plan {
            Objects.requireNonNull(endpoint, "endpoint");
            path = List.copyOf(path);
            waypoints = List.copyOf(waypoints);
            if (path.isEmpty()) throw new IllegalArgumentException("Path cannot be empty.");
            if (!path.getLast().equals(endpoint)) {
                throw new IllegalArgumentException("Endpoint must be the last path cell.");
            }
        }
    }

    /**
     * Finds the shortest local path that rejoins the original forward line
     * beyond an obstructed first step.
     *
     * <p>The search is restricted to a square of {@code radius} around
     * {@code start} and discovers at most {@code nodeCap} cells. Only cells in
     * {@code passableCells} may be traversed. Neighbors are considered in
     * left, forward, right, back order, which makes a symmetric result prefer
     * the left bypass. Among endpoints at the same BFS depth, the endpoint
     * nearest {@code terminal} wins.</p>
     *
     * <p>An empty result means either that the direct first step is already
     * clear and no bypass is needed, or that no safe bounded bypass exists.</p>
     */
    public static Optional<Plan> findBypass(
        Set<Point> passableCells,
        Point start,
        Direction forward,
        Point terminal,
        int radius,
        int nodeCap
    ) {
        return findBypass(
            passableCells,
            start,
            forward,
            terminal,
            (from, to) -> true,
            radius,
            nodeCap
        );
    }

    /**
     * Finds a bypass while also rejecting unsafe movement edges, such as a
     * door collision plane between two otherwise clear cell centers.
     */
    public static Optional<Plan> findBypass(
        Set<Point> passableCells,
        Point start,
        Direction forward,
        Point terminal,
        BiPredicate<Point, Point> traversableEdge,
        int radius,
        int nodeCap
    ) {
        Objects.requireNonNull(passableCells, "passableCells");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(traversableEdge, "traversableEdge");
        if (radius < 1) throw new IllegalArgumentException("Radius must be positive.");
        if (nodeCap < 1) throw new IllegalArgumentException("Node cap must be positive.");
        if (!passableCells.contains(start)) return Optional.empty();
        if (forwardProgress(start, terminal, forward) <= 0) return Optional.empty();
        Point directStep = start.offset(forward);
        if (passableCells.contains(directStep)
            && traversableEdge.test(start, directStep)) {
            return Optional.empty();
        }

        Direction[] neighborOrder = {
            forward.left(),
            forward,
            forward.right(),
            forward.opposite()
        };
        ArrayDeque<Point> queue = new ArrayDeque<>();
        Map<Point, Point> parent = new HashMap<>();
        Set<Point> discovered = new HashSet<>();
        queue.add(start);
        discovered.add(start);

        int startTerminalDistance = start.manhattanDistance(terminal);
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            ArrayList<Point> candidates = new ArrayList<>();
            for (int index = 0; index < levelSize; index++) {
                Point current = queue.removeFirst();
                if (isBypassEndpoint(
                    current,
                    start,
                    forward,
                    terminal,
                    startTerminalDistance
                )) {
                    candidates.add(current);
                }

                for (Direction direction : neighborOrder) {
                    if (discovered.size() >= nodeCap) break;
                    Point next = current.offset(direction);
                    if (!insideRadius(next, start, radius)
                        || !passableCells.contains(next)
                        || !traversableEdge.test(current, next)
                        || !discovered.add(next)) {
                        continue;
                    }
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }

            if (!candidates.isEmpty()) {
                candidates.sort(Comparator.comparingInt(
                    candidate -> candidate.manhattanDistance(terminal)
                ));
                Point endpoint = candidates.getFirst();
                List<Point> path = reconstructPath(parent, start, endpoint);
                return Optional.of(new Plan(
                    endpoint,
                    path,
                    collapseToTurnWaypoints(path)
                ));
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a short lateral nudge when a complete rejoining bypass cannot fit
     * inside the bounded search. The longest safe nudge up to
     * {@code maximumDistance} wins; equal choices prefer the left side.
     */
    public static Optional<Plan> findSidestep(
        Set<Point> passableCells,
        Point start,
        Direction forward,
        BiPredicate<Point, Point> traversableEdge,
        int maximumDistance
    ) {
        Objects.requireNonNull(passableCells, "passableCells");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(traversableEdge, "traversableEdge");
        if (maximumDistance < 1) {
            throw new IllegalArgumentException(
                "Maximum sidestep distance must be positive."
            );
        }
        if (!passableCells.contains(start)) return Optional.empty();

        Plan best = null;
        int bestDistance = 0;
        for (Direction lateral : List.of(forward.left(), forward.right())) {
            ArrayList<Point> path = new ArrayList<>();
            path.add(start);
            Point current = start;
            for (int distance = 1; distance <= maximumDistance; distance++) {
                Point next = current.offset(lateral);
                if (!passableCells.contains(next)
                    || !traversableEdge.test(current, next)) {
                    break;
                }
                path.add(next);
                current = next;
                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = new Plan(current, path, List.of(current));
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isBypassEndpoint(
        Point candidate,
        Point start,
        Direction forward,
        Point terminal,
        int startTerminalDistance
    ) {
        int dx = candidate.x() - start.x();
        int dz = candidate.z() - start.z();
        int progress = dx * forward.dx() + dz * forward.dz();
        int lateral = dx * forward.left().dx() + dz * forward.left().dz();
        return progress > 0
            && lateral == 0
            && candidate.manhattanDistance(terminal) < startTerminalDistance;
    }

    private static int forwardProgress(
        Point start,
        Point terminal,
        Direction forward
    ) {
        int dx = terminal.x() - start.x();
        int dz = terminal.z() - start.z();
        return dx * forward.dx() + dz * forward.dz();
    }

    private static boolean insideRadius(Point point, Point start, int radius) {
        return Math.abs(point.x() - start.x()) <= radius
            && Math.abs(point.z() - start.z()) <= radius;
    }

    private static List<Point> reconstructPath(
        Map<Point, Point> parent,
        Point start,
        Point endpoint
    ) {
        ArrayDeque<Point> reversed = new ArrayDeque<>();
        Point current = endpoint;
        reversed.addFirst(current);
        while (!current.equals(start)) {
            current = parent.get(current);
            if (current == null) {
                throw new IllegalStateException("Bypass parent chain is incomplete.");
            }
            reversed.addFirst(current);
        }
        return List.copyOf(reversed);
    }

    static List<Point> collapseToTurnWaypoints(List<Point> path) {
        Objects.requireNonNull(path, "path");
        if (path.size() <= 1) return List.of();

        ArrayList<Point> waypoints = new ArrayList<>();
        int previousDx = path.get(1).x() - path.get(0).x();
        int previousDz = path.get(1).z() - path.get(0).z();
        validateCardinalStep(previousDx, previousDz);

        for (int index = 2; index < path.size(); index++) {
            int dx = path.get(index).x() - path.get(index - 1).x();
            int dz = path.get(index).z() - path.get(index - 1).z();
            validateCardinalStep(dx, dz);
            if (dx != previousDx || dz != previousDz) {
                waypoints.add(path.get(index - 1));
                previousDx = dx;
                previousDz = dz;
            }
        }
        waypoints.add(path.getLast());
        return List.copyOf(waypoints);
    }

    private static void validateCardinalStep(int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            throw new IllegalArgumentException(
                "Path must contain only single-cell cardinal steps."
            );
        }
    }
}
