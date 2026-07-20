package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects circular U routes to traverse and assigns fully reachable routes to
 * them for remote teardown.
 *
 * <p>An assigned route is accepted only when the caller supplies one
 * nondecreasing destination-support index for every target in the source
 * route's endpoint-to-endpoint order. Consequently an interrupted remote
 * teardown has removed only a prefix and leaves one continuous suffix attached
 * to the opposite endpoint.</p>
 */
public final class ReachOptimizedTeardownPlan {
    private ReachOptimizedTeardownPlan() {
    }

    public record Route<K>(
        int routeIndex,
        List<K> orderedTargets,
        boolean mustTraverse,
        boolean canHostRemoteTeardown
    ) {
        public Route {
            if (routeIndex < 0) {
                throw new IllegalArgumentException(
                    "Route index cannot be negative."
                );
            }
            orderedTargets = List.copyOf(
                Objects.requireNonNull(
                    orderedTargets,
                    "orderedTargets"
                )
            );
            if (new HashSet<>(orderedTargets).size()
                != orderedTargets.size()) {
                throw new IllegalArgumentException(
                    "A route cannot contain duplicate teardown targets."
                );
            }
            if (orderedTargets.isEmpty()
                && (mustTraverse || canHostRemoteTeardown)) {
                throw new IllegalArgumentException(
                    "A completed route cannot traverse or host teardown."
                );
            }
        }

        public boolean complete() {
            return orderedTargets.isEmpty();
        }
    }

    public record ScheduledTarget<K>(
        int sourceRouteIndex,
        K target,
        int destinationSupportIndex,
        int sourceTargetIndex
    ) {
        public ScheduledTarget {
            if (sourceRouteIndex < 0
                || destinationSupportIndex < 0
                || sourceTargetIndex < 0) {
                throw new IllegalArgumentException(
                    "Scheduled teardown indices cannot be negative."
                );
            }
            Objects.requireNonNull(target, "target");
        }
    }

    public record Plan<K>(
        List<Integer> traversalRouteIndices,
        Map<Integer, List<ScheduledTarget<K>>>
            scheduledTargetsByTraversal,
        Map<Integer, Integer> routeAssignments,
        List<Integer> completedRouteIndices
    ) {
        public Plan {
            traversalRouteIndices =
                List.copyOf(traversalRouteIndices);
            LinkedHashMap<Integer, List<ScheduledTarget<K>>> schedules =
                new LinkedHashMap<>();
            scheduledTargetsByTraversal.forEach(
                (route, targets) ->
                    schedules.put(route, List.copyOf(targets))
            );
            scheduledTargetsByTraversal =
                Collections.unmodifiableMap(schedules);
            routeAssignments = Collections.unmodifiableMap(
                new LinkedHashMap<>(routeAssignments)
            );
            completedRouteIndices =
                List.copyOf(completedRouteIndices);
        }
    }

    @FunctionalInterface
    public interface ScheduleFinder<K> {
        /**
         * Returns one destination-support index per ordered source target.
         * Indices must be nondecreasing.
         */
        Optional<List<Integer>> find(
            List<K> orderedSourceTargets,
            int destinationRouteIndex
        );
    }

    public static <K> Plan<K> create(
        List<? extends Route<K>> orderedRoutes,
        ScheduleFinder<K> scheduleFinder
    ) {
        Objects.requireNonNull(orderedRoutes, "orderedRoutes");
        Objects.requireNonNull(scheduleFinder, "scheduleFinder");

        ArrayList<Route<K>> routes =
            validateRoutes(orderedRoutes);
        LinkedHashMap<Integer, Route<K>> byIndex =
            new LinkedHashMap<>();
        routes.forEach(route -> byIndex.put(route.routeIndex(), route));

        HashSet<Integer> covered = new HashSet<>();
        HashSet<Integer> selected = new HashSet<>();
        LinkedHashMap<Integer, Integer> assignments =
            new LinkedHashMap<>();
        LinkedHashMap<Integer, ArrayList<ScheduledTarget<K>>>
            scheduled = new LinkedHashMap<>();
        ArrayList<Integer> completed = new ArrayList<>();

        for (Route<K> route : routes) {
            if (route.complete()) {
                covered.add(route.routeIndex());
                completed.add(route.routeIndex());
            } else if (route.mustTraverse()) {
                selected.add(route.routeIndex());
                covered.add(route.routeIndex());
                scheduled.put(route.routeIndex(), new ArrayList<>());
            }
        }

        // A mandatory recovery traversal gets first ownership of every intact
        // route it can safely consume.
        for (Route<K> destination : routes) {
            if (!selected.contains(destination.routeIndex())
                || !destination.canHostRemoteTeardown()) {
                continue;
            }
            coverReachableRoutes(
                destination,
                routes,
                covered,
                assignments,
                scheduled,
                scheduleFinder
            );
        }

        while (covered.size() < routes.size()) {
            Route<K> earliest = routes.stream()
                .filter(route -> !covered.contains(route.routeIndex()))
                .findFirst()
                .orElseThrow();

            Route<K> destination = chooseDestination(
                earliest,
                routes,
                covered,
                scheduleFinder
            );
            selected.add(destination.routeIndex());
            covered.add(destination.routeIndex());
            scheduled.putIfAbsent(
                destination.routeIndex(),
                new ArrayList<>()
            );
            coverReachableRoutes(
                destination,
                routes,
                covered,
                assignments,
                scheduled,
                scheduleFinder
            );
        }

        ArrayList<Integer> traversals =
            new ArrayList<>(selected);
        Collections.sort(traversals);
        Collections.sort(completed);

        LinkedHashMap<Integer, List<ScheduledTarget<K>>>
            immutableSchedules = new LinkedHashMap<>();
        for (int traversal : traversals) {
            ArrayList<ScheduledTarget<K>> targets =
                scheduled.getOrDefault(
                    traversal,
                    new ArrayList<>()
                );
            targets.sort((left, right) -> {
                int support = Integer.compare(
                    left.destinationSupportIndex(),
                    right.destinationSupportIndex()
                );
                if (support != 0) return support;
                int source = Integer.compare(
                    left.sourceRouteIndex(),
                    right.sourceRouteIndex()
                );
                if (source != 0) return source;
                return Integer.compare(
                    left.sourceTargetIndex(),
                    right.sourceTargetIndex()
                );
            });
            immutableSchedules.put(
                traversal,
                List.copyOf(targets)
            );
        }

        return new Plan<>(
            traversals,
            immutableSchedules,
            assignments,
            completed
        );
    }

    private static <K> Route<K> chooseDestination(
        Route<K> earliest,
        List<Route<K>> routes,
        HashSet<Integer> covered,
        ScheduleFinder<K> scheduleFinder
    ) {
        if (earliest.canHostRemoteTeardown()) {
            int ownCoverage = coverageCount(
                earliest,
                routes,
                covered,
                scheduleFinder
            );
            // Prefer continuing from the earliest live U whenever it saves at
            // least one additional traversal.
            if (ownCoverage > 1) return earliest;
        }

        Route<K> best = earliest;
        int bestCoverage = earliest.canHostRemoteTeardown()
            ? coverageCount(
                earliest,
                routes,
                covered,
                scheduleFinder
            )
            : 1;
        int bestDistance = 0;
        for (Route<K> candidate : routes) {
            if (candidate.complete()
                || candidate.mustTraverse()
                || !candidate.canHostRemoteTeardown()
                || covered.contains(candidate.routeIndex())) {
                continue;
            }
            if (candidate.routeIndex() != earliest.routeIndex()
                && findValidatedSchedule(
                    earliest,
                    candidate.routeIndex(),
                    scheduleFinder
                ).isEmpty()) {
                continue;
            }
            int coverage = coverageCount(
                candidate,
                routes,
                covered,
                scheduleFinder
            );
            int distance = Math.abs(
                candidate.routeIndex() - earliest.routeIndex()
            );
            if (coverage > bestCoverage
                || (coverage == bestCoverage
                    && distance < bestDistance)
                || (coverage == bestCoverage
                    && distance == bestDistance
                    && candidate.routeIndex() < best.routeIndex())) {
                best = candidate;
                bestCoverage = coverage;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static <K> int coverageCount(
        Route<K> destination,
        List<Route<K>> routes,
        HashSet<Integer> covered,
        ScheduleFinder<K> scheduleFinder
    ) {
        int count = covered.contains(destination.routeIndex())
            ? 0 : 1;
        if (!destination.canHostRemoteTeardown()) return count;
        for (Route<K> source : routes) {
            if (covered.contains(source.routeIndex())
                || source.routeIndex() == destination.routeIndex()
                || source.complete()
                || source.mustTraverse()) {
                continue;
            }
            if (findValidatedSchedule(
                source,
                destination.routeIndex(),
                scheduleFinder
            ).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private static <K> void coverReachableRoutes(
        Route<K> destination,
        List<Route<K>> routes,
        HashSet<Integer> covered,
        LinkedHashMap<Integer, Integer> assignments,
        LinkedHashMap<Integer, ArrayList<ScheduledTarget<K>>> scheduled,
        ScheduleFinder<K> scheduleFinder
    ) {
        if (!destination.canHostRemoteTeardown()) return;
        ArrayList<ScheduledTarget<K>> destinationTargets =
            scheduled.computeIfAbsent(
                destination.routeIndex(),
                ignored -> new ArrayList<>()
            );
        for (Route<K> source : routes) {
            if (covered.contains(source.routeIndex())
                || source.routeIndex() == destination.routeIndex()
                || source.complete()
                || source.mustTraverse()) {
                continue;
            }
            Optional<List<Integer>> schedule =
                findValidatedSchedule(
                    source,
                    destination.routeIndex(),
                    scheduleFinder
                );
            if (schedule.isEmpty()) continue;

            covered.add(source.routeIndex());
            assignments.put(
                source.routeIndex(),
                destination.routeIndex()
            );
            for (int index = 0;
                 index < source.orderedTargets().size();
                 index++) {
                destinationTargets.add(
                    new ScheduledTarget<>(
                        source.routeIndex(),
                        source.orderedTargets().get(index),
                        schedule.get().get(index),
                        index
                    )
                );
            }
        }
    }

    private static <K> Optional<List<Integer>>
        findValidatedSchedule(
            Route<K> source,
            int destinationRoute,
            ScheduleFinder<K> scheduleFinder
        ) {
        Optional<List<Integer>> candidate =
            Objects.requireNonNull(
                scheduleFinder.find(
                    source.orderedTargets(),
                    destinationRoute
                ),
                "schedule"
            );
        if (candidate.isEmpty()) return Optional.empty();
        List<Integer> indices = List.copyOf(candidate.get());
        if (indices.size() != source.orderedTargets().size()) {
            throw new IllegalArgumentException(
                "A teardown reach schedule must contain one index per target."
            );
        }
        int previous = -1;
        for (int index : indices) {
            if (index < previous) {
                throw new IllegalArgumentException(
                    "A teardown reach schedule must be nondecreasing."
                );
            }
            previous = index;
        }
        return Optional.of(indices);
    }

    private static <K> ArrayList<Route<K>> validateRoutes(
        List<? extends Route<K>> orderedRoutes
    ) {
        ArrayList<Route<K>> routes =
            new ArrayList<>(orderedRoutes.size());
        int previous = -1;
        HashSet<K> allTargets = new HashSet<>();
        for (Route<K> route : orderedRoutes) {
            Route<K> candidate =
                Objects.requireNonNull(route, "route");
            if (candidate.routeIndex() <= previous) {
                throw new IllegalArgumentException(
                    "Routes must have strictly increasing indices."
                );
            }
            previous = candidate.routeIndex();
            for (K target : candidate.orderedTargets()) {
                if (!allTargets.add(
                    Objects.requireNonNull(target, "target")
                )) {
                    throw new IllegalArgumentException(
                        "A teardown target cannot belong to multiple routes."
                    );
                }
            }
            routes.add(candidate);
        }
        return routes;
    }
}
