package com.julflips.nerv_printer.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure, bounded path planning across grounded cardinal support cells.
 *
 * <p>Each edge moves one block horizontally and may step down, stay level,
 * or step up by one block. The caller owns the authoritative definition of a
 * walkable support (normally a solid block with two air blocks above it) and
 * the search domain.</p>
 */
public final class GroundedSupportPathPlanner {
    private GroundedSupportPathPlanner() {
    }

    public record Cell(int x, int y, int z) {
        private Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }
    }

    /**
     * The full path includes its start and endpoint. Waypoints exclude the
     * start and collapse straight runs to their turning points and endpoint.
     */
    public record Plan(
        Cell endpoint,
        List<Cell> path,
        List<Cell> waypoints
    ) {
        public Plan {
            Objects.requireNonNull(endpoint, "endpoint");
            path = List.copyOf(path);
            waypoints = List.copyOf(waypoints);
            if (path.isEmpty() || !path.getLast().equals(endpoint)) {
                throw new IllegalArgumentException(
                    "A grounded path must end at its declared endpoint."
                );
            }
        }
    }

    /**
     * Finds the shortest grounded path from {@code start} to any goal.
     * Discovery is capped so a malformed domain cannot create an unbounded
     * world search.
     */
    public static Optional<Plan> findPath(
        Cell start,
        Set<Cell> goals,
        Predicate<Cell> insideDomain,
        Predicate<Cell> walkable,
        int nodeCap
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goals, "goals");
        Objects.requireNonNull(insideDomain, "insideDomain");
        Objects.requireNonNull(walkable, "walkable");
        if (nodeCap < 1) {
            throw new IllegalArgumentException("Node cap must be positive.");
        }
        if (goals.isEmpty()
            || !insideDomain.test(start)
            || !walkable.test(start)) {
            return Optional.empty();
        }

        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> parent = new HashMap<>();
        Set<Cell> discovered = new HashSet<>();
        queue.addLast(start);
        discovered.add(start);

        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            if (goals.contains(current)) {
                List<Cell> path = reconstructPath(parent, start, current);
                return Optional.of(new Plan(
                    current,
                    path,
                    collapseToWaypoints(path)
                ));
            }
            if (discovered.size() >= nodeCap) continue;

            // Prefer level movement, then a one-block rise, then a one-block
            // descent. Cardinal ordering is stable for reproducible recovery.
            for (int[] horizontal : new int[][] {
                {0, -1}, {1, 0}, {0, 1}, {-1, 0}
            }) {
                for (int dy : new int[] {0, 1, -1}) {
                    if (discovered.size() >= nodeCap) break;
                    Cell next = current.offset(
                        horizontal[0],
                        dy,
                        horizontal[1]
                    );
                    if (!insideDomain.test(next)
                        || !walkable.test(next)
                        || !discovered.add(next)) {
                        continue;
                    }
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        return Optional.empty();
    }

    private static List<Cell> reconstructPath(
        Map<Cell, Cell> parent,
        Cell start,
        Cell endpoint
    ) {
        ArrayDeque<Cell> reversed = new ArrayDeque<>();
        Cell current = endpoint;
        reversed.addFirst(current);
        while (!current.equals(start)) {
            current = parent.get(current);
            if (current == null) {
                throw new IllegalStateException(
                    "Grounded path parent chain is incomplete."
                );
            }
            reversed.addFirst(current);
        }
        return List.copyOf(reversed);
    }

    static List<Cell> collapseToWaypoints(List<Cell> path) {
        Objects.requireNonNull(path, "path");
        if (path.size() <= 1) return List.of();

        ArrayList<Cell> waypoints = new ArrayList<>();
        int previousDx = path.get(1).x() - path.get(0).x();
        int previousDy = path.get(1).y() - path.get(0).y();
        int previousDz = path.get(1).z() - path.get(0).z();
        validateStep(previousDx, previousDy, previousDz);
        for (int index = 2; index < path.size(); index++) {
            int dx = path.get(index).x() - path.get(index - 1).x();
            int dy = path.get(index).y() - path.get(index - 1).y();
            int dz = path.get(index).z() - path.get(index - 1).z();
            validateStep(dx, dy, dz);
            if (dx != previousDx
                || dy != previousDy
                || dz != previousDz) {
                waypoints.add(path.get(index - 1));
                previousDx = dx;
                previousDy = dy;
                previousDz = dz;
            }
        }
        waypoints.add(path.getLast());
        return List.copyOf(waypoints);
    }

    private static void validateStep(int dx, int dy, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1 || Math.abs(dy) > 1) {
            throw new IllegalArgumentException(
                "Grounded paths require cardinal one-block steps with at most one block of height change."
            );
        }
    }
}
