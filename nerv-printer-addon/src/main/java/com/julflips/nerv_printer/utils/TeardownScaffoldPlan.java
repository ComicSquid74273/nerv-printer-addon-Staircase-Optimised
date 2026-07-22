package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plans one bounded scaffold sortie for sparse blocks left on an ordered U.
 *
 * <p>The route is considered from both safe north endpoints. The selected
 * endpoint has the nearest owned cleanup block. Air supports before the last
 * selected cleanup block become temporary scaffold, while owned blocks in
 * that prefix remain walking supports until the return pass removes them.
 * The last cleanup block is never used as a standing support: it is mined
 * from the preceding support (or directly from the endpoint walkway).</p>
 */
public final class TeardownScaffoldPlan {
    private TeardownScaffoldPlan() {
    }

    public enum Cell {
        AIR,
        OWNED,
        BLOCKED
    }

    public enum Endpoint {
        START,
        END
    }

    public record Plan(
        Endpoint endpoint,
        List<Integer> outwardSupportIndices,
        List<Integer> scaffoldIndices,
        int terminalCleanupIndex,
        int ownedCleanupCount
    ) {
        public Plan {
            Objects.requireNonNull(endpoint, "endpoint");
            outwardSupportIndices = List.copyOf(
                outwardSupportIndices
            );
            scaffoldIndices = List.copyOf(scaffoldIndices);
            if (terminalCleanupIndex < 0 || ownedCleanupCount <= 0) {
                throw new IllegalArgumentException(
                    "A scaffold sortie requires owned cleanup work."
                );
            }
        }

        public List<Integer> cleanupIndices() {
            ArrayList<Integer> cleanup = new ArrayList<>(
                outwardSupportIndices.size() + 1
            );
            cleanup.addAll(outwardSupportIndices);
            cleanup.add(terminalCleanupIndex);
            return List.copyOf(cleanup);
        }
    }

    public static Optional<Plan> create(
        List<Cell> orderedRoute,
        int maximumScaffoldBlocks
    ) {
        Objects.requireNonNull(orderedRoute, "orderedRoute");
        if (orderedRoute.isEmpty()) {
            throw new IllegalArgumentException(
                "An ordered U route cannot be empty."
            );
        }
        if (maximumScaffoldBlocks < 0) {
            throw new IllegalArgumentException(
                "Maximum scaffold blocks cannot be negative."
            );
        }
        for (Cell cell : orderedRoute) {
            Objects.requireNonNull(cell, "route cell");
        }

        Optional<Candidate> start = candidate(
            orderedRoute,
            maximumScaffoldBlocks,
            Endpoint.START
        );
        Optional<Candidate> end = candidate(
            orderedRoute,
            maximumScaffoldBlocks,
            Endpoint.END
        );
        if (start.isEmpty()) return end.map(Candidate::plan);
        if (end.isEmpty()) return start.map(Candidate::plan);

        Candidate left = start.orElseThrow();
        Candidate right = end.orElseThrow();
        if (right.nearestOwnedDistance()
                < left.nearestOwnedDistance()
            || (right.nearestOwnedDistance()
                    == left.nearestOwnedDistance()
                && right.plan().ownedCleanupCount()
                    > left.plan().ownedCleanupCount())
            || (right.nearestOwnedDistance()
                    == left.nearestOwnedDistance()
                && right.plan().ownedCleanupCount()
                    == left.plan().ownedCleanupCount()
                && right.plan().scaffoldIndices().size()
                    < left.plan().scaffoldIndices().size())) {
            return Optional.of(right.plan());
        }
        return Optional.of(left.plan());
    }

    private static Optional<Candidate> candidate(
        List<Cell> route,
        int maximumScaffoldBlocks,
        Endpoint endpoint
    ) {
        int maximumDistance = (route.size() - 1) / 2;
        int nearestOwnedDistance = -1;
        int farthestOwnedDistance = -1;
        int ownedCount = 0;
        int scaffoldCount = 0;
        for (int distance = 0;
             distance <= maximumDistance;
             distance++) {
            int index = routeIndex(route.size(), endpoint, distance);
            Cell cell = route.get(index);
            if (cell == Cell.BLOCKED) break;
            if (cell == Cell.AIR) {
                scaffoldCount++;
                if (scaffoldCount > maximumScaffoldBlocks) break;
                continue;
            }
            if (nearestOwnedDistance < 0) {
                nearestOwnedDistance = distance;
            }
            farthestOwnedDistance = distance;
            ownedCount++;
        }
        if (farthestOwnedDistance < 0) return Optional.empty();

        ArrayList<Integer> outward = new ArrayList<>(
            farthestOwnedDistance
        );
        ArrayList<Integer> scaffold = new ArrayList<>();
        for (int distance = 0;
             distance < farthestOwnedDistance;
             distance++) {
            int index = routeIndex(route.size(), endpoint, distance);
            outward.add(index);
            if (route.get(index) == Cell.AIR) scaffold.add(index);
        }
        int terminal = routeIndex(
            route.size(),
            endpoint,
            farthestOwnedDistance
        );
        return Optional.of(
            new Candidate(
                new Plan(
                    endpoint,
                    outward,
                    scaffold,
                    terminal,
                    ownedCount
                ),
                nearestOwnedDistance
            )
        );
    }

    private static int routeIndex(
        int routeSize,
        Endpoint endpoint,
        int distance
    ) {
        return endpoint == Endpoint.START
            ? distance
            : routeSize - 1 - distance;
    }

    private record Candidate(
        Plan plan,
        int nearestOwnedDistance
    ) {
    }
}
