package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/** Small bounded A* used for autonomous launch, logistics, and BoatFly routing. */
public final class RasterVoxelPathfinder {
    private RasterVoxelPathfinder() {
    }

    public enum Mode {
        WALK,
        FLY
    }

    public record Cell(int x, int y, int z) {
    }

    private record QueueEntry(Cell cell, double score) {
    }

    public enum SearchStatus {
        SEARCHING,
        FOUND,
        FAILED
    }

    /** Incremental A* session so large 3D scans can be spread across client ticks. */
    public static final class Search {
        private final Cell goal;
        private final Mode mode;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final int maximumVisited;
        private final Predicate<Cell> passable;
        private final PriorityQueue<QueueEntry> open = new PriorityQueue<>(
            Comparator.comparingDouble(QueueEntry::score)
        );
        private final Map<Cell, Double> cost = new HashMap<>();
        private final Map<Cell, Cell> previous = new HashMap<>();
        private final Set<Cell> closed = new HashSet<>();
        private SearchStatus status = SearchStatus.SEARCHING;
        private List<Cell> path = List.of();

        private Search(
            Cell start,
            Cell goal,
            Mode mode,
            int horizontalPadding,
            int verticalPadding,
            int maximumVisited,
            Predicate<Cell> passable
        ) {
            this.goal = goal;
            this.mode = mode;
            this.maximumVisited = maximumVisited;
            this.passable = passable;
            minX = Math.min(start.x(), goal.x()) - horizontalPadding;
            maxX = Math.max(start.x(), goal.x()) + horizontalPadding;
            minY = Math.min(start.y(), goal.y()) - verticalPadding;
            maxY = Math.max(start.y(), goal.y()) + verticalPadding;
            minZ = Math.min(start.z(), goal.z()) - horizontalPadding;
            maxZ = Math.max(start.z(), goal.z()) + horizontalPadding;
            if (!passable.test(goal)) {
                status = SearchStatus.FAILED;
                return;
            }
            cost.put(start, 0.0);
            open.add(new QueueEntry(start, distance(start, goal)));
            if (start.equals(goal)) {
                path = List.of(start);
                status = SearchStatus.FOUND;
            }
        }

        public SearchStatus advance(int expansionBudget) {
            if (expansionBudget < 1) {
                throw new IllegalArgumentException("Expansion budget must be positive.");
            }
            int examined = 0;
            while (status == SearchStatus.SEARCHING
                && !open.isEmpty()
                && closed.size() < maximumVisited
                && examined < expansionBudget) {
                Cell current = open.remove().cell();
                examined++;
                if (!closed.add(current)) continue;
                if (current.equals(goal)) {
                    path = reconstruct(previous, current);
                    status = SearchStatus.FOUND;
                    break;
                }
                expand(current);
            }
            if (status == SearchStatus.SEARCHING
                && (open.isEmpty() || closed.size() >= maximumVisited)) {
                status = SearchStatus.FAILED;
            }
            return status;
        }

        public SearchStatus status() {
            return status;
        }

        public int visitedCount() {
            return closed.size();
        }

        public List<Cell> path() {
            return path;
        }

        private void expand(Cell current) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (mode == Mode.WALK && dx == 0 && dz == 0) continue;
                        Cell next = new Cell(
                            current.x() + dx,
                            current.y() + dy,
                            current.z() + dz
                        );
                        if (next.x() < minX || next.x() > maxX
                            || next.y() < minY || next.y() > maxY
                            || next.z() < minZ || next.z() > maxZ
                            || closed.contains(next)
                            || !passable.test(next)
                            || cutsCorner(current, dx, dy, dz, mode, passable)) {
                            continue;
                        }
                        double nextCost = cost.get(current)
                            + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (nextCost >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                            continue;
                        }
                        cost.put(next, nextCost);
                        previous.put(next, current);
                        open.add(new QueueEntry(
                            next,
                            nextCost + distance(next, goal)
                        ));
                    }
                }
            }
        }
    }

    public static Search begin(
        Cell start,
        Cell goal,
        Mode mode,
        int horizontalPadding,
        int verticalPadding,
        int maximumVisited,
        Predicate<Cell> passable
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(passable, "passable");
        if (horizontalPadding < 0 || verticalPadding < 0 || maximumVisited < 1) {
            throw new IllegalArgumentException("Raster path bounds are invalid.");
        }
        return new Search(
            start,
            goal,
            mode,
            horizontalPadding,
            verticalPadding,
            maximumVisited,
            passable
        );
    }

    public static List<Cell> find(
        Cell start,
        Cell goal,
        Mode mode,
        int horizontalPadding,
        int verticalPadding,
        int maximumVisited,
        Predicate<Cell> passable
    ) {
        Search search = begin(
            start,
            goal,
            mode,
            horizontalPadding,
            verticalPadding,
            maximumVisited,
            passable
        );
        while (search.status() == SearchStatus.SEARCHING) {
            search.advance(maximumVisited);
        }
        return search.path();
    }

    /**
     * Returns the complete bounded component reachable from {@code start}.
     * Launch selection uses one component scan instead of running a fresh A*
     * for every geometrically valid candidate.
     */
    public static Set<Cell> reachable(
        Cell start,
        Mode mode,
        int horizontalPadding,
        int verticalPadding,
        int maximumVisited,
        Predicate<Cell> passable
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(passable, "passable");
        if (horizontalPadding < 0 || verticalPadding < 0 || maximumVisited < 1) {
            throw new IllegalArgumentException("Raster reachability bounds are invalid.");
        }
        int minX = start.x() - horizontalPadding;
        int maxX = start.x() + horizontalPadding;
        int minY = start.y() - verticalPadding;
        int maxY = start.y() + verticalPadding;
        int minZ = start.z() - horizontalPadding;
        int maxZ = start.z() + horizontalPadding;
        if (!passable.test(start)) return Set.of();
        ArrayDeque<Cell> open = new ArrayDeque<>();
        HashSet<Cell> reached = new HashSet<>();
        open.add(start);
        reached.add(start);
        while (!open.isEmpty() && reached.size() < maximumVisited) {
            Cell current = open.removeFirst();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Cell next = new Cell(
                            current.x() + dx,
                            current.y() + dy,
                            current.z() + dz
                        );
                        if (next.x() < minX || next.x() > maxX
                            || next.y() < minY || next.y() > maxY
                            || next.z() < minZ || next.z() > maxZ
                            || reached.contains(next)
                            || !canTraverse(current, next, mode, passable)) {
                            continue;
                        }
                        reached.add(next);
                        open.addLast(next);
                    }
                }
            }
        }
        return Set.copyOf(reached);
    }

    /** Revalidates one adjacent route edge against the current world. */
    public static boolean canTraverse(
        Cell current,
        Cell next,
        Mode mode,
        Predicate<Cell> passable
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(passable, "passable");
        int dx = next.x() - current.x();
        int dy = next.y() - current.y();
        int dz = next.z() - current.z();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) {
            return false;
        }
        if (dx == 0 && dy == 0 && dz == 0) return passable.test(next);
        if (mode == Mode.WALK && dx == 0 && dz == 0) return false;
        return passable.test(next)
            && !cutsCorner(current, dx, dy, dz, mode, passable);
    }

    /**
     * Retains every turn and caps straight runs, avoiding one-block targets
     * that a fast BoatFly controller can repeatedly overshoot.
     */
    public static List<Cell> compressStraightSegments(
        List<Cell> path,
        int maximumSegmentLength
    ) {
        Objects.requireNonNull(path, "path");
        if (maximumSegmentLength < 1) {
            throw new IllegalArgumentException("Maximum segment length must be positive.");
        }
        if (path.size() <= 2) return List.copyOf(path);
        ArrayList<Cell> result = new ArrayList<>();
        result.add(path.getFirst());
        int segmentStart = 0;
        int previousDx = path.get(1).x() - path.getFirst().x();
        int previousDy = path.get(1).y() - path.getFirst().y();
        int previousDz = path.get(1).z() - path.getFirst().z();
        for (int index = 2; index < path.size(); index++) {
            Cell before = path.get(index - 1);
            Cell current = path.get(index);
            int dx = current.x() - before.x();
            int dy = current.y() - before.y();
            int dz = current.z() - before.z();
            boolean directionChanged = dx != previousDx
                || dy != previousDy || dz != previousDz;
            boolean segmentFull = index - segmentStart > maximumSegmentLength;
            if (directionChanged || segmentFull) {
                if (!result.getLast().equals(before)) result.add(before);
                segmentStart = index - 1;
            }
            previousDx = dx;
            previousDy = dy;
            previousDz = dz;
        }
        if (!result.getLast().equals(path.getLast())) result.add(path.getLast());
        return List.copyOf(result);
    }

    private static boolean cutsCorner(
        Cell current,
        int dx,
        int dy,
        int dz,
        Mode mode,
        Predicate<Cell> passable
    ) {
        int changedAxes = (dx == 0 ? 0 : 1)
            + (dy == 0 ? 0 : 1)
            + (dz == 0 ? 0 : 1);
        if (changedAxes < 2) return false;
        if (mode == Mode.WALK && dy != 0) {
            // A normal one-block stair move necessarily has a solid block in
            // the horizontal cell at the old feet height. Treating that block
            // as a cut corner made every real step-up impossible. Vertical
            // diagonals still cross too many edges at once, so keep rejecting
            // those and require a cardinal stair step instead.
            return dx != 0 && dz != 0;
        }
        if (dx != 0 && !passable.test(new Cell(
            current.x() + dx, current.y(), current.z()))) return true;
        if (mode == Mode.FLY && dy != 0 && !passable.test(new Cell(
            current.x(), current.y() + dy, current.z()))) return true;
        if (dz != 0 && !passable.test(new Cell(
            current.x(), current.y(), current.z() + dz))) return true;
        return false;
    }

    private static List<Cell> reconstruct(Map<Cell, Cell> previous, Cell end) {
        ArrayList<Cell> path = new ArrayList<>();
        Cell current = end;
        while (current != null) {
            path.add(current);
            current = previous.get(current);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private static double distance(Cell left, Cell right) {
        int dx = left.x() - right.x();
        int dy = left.y() - right.y();
        int dz = left.z() - right.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
