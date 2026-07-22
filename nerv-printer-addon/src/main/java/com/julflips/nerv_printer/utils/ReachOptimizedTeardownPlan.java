package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects circular U routes to traverse and assigns fully reachable route
 * remainders to them for remote teardown.
 *
 * <p>An assigned route is accepted only when the caller supplies its targets
 * in an endpoint-preserving removal order and one nondecreasing destination
 * support index for every target. Consequently an interrupted remote teardown
 * has removed only a prefix of that safe order and leaves one continuous
 * remainder attached to the endpoint selected by the caller. Traversal hosts
 * are selected by global uncovered-route coverage rather than map order, then
 * redundant nonmandatory hosts are pruned before final assignment.</p>
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
         * Returns one destination-support index per source target in the
         * caller's endpoint-preserving removal order. Indices must be
         * nondecreasing.
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

        HashSet<Integer> selected = new HashSet<>();
        ArrayList<Integer> completed = new ArrayList<>();
        HashMap<ScheduleKey, Optional<List<Integer>>> scheduleCache =
            new HashMap<>();

        for (Route<K> route : routes) {
            if (route.complete()) {
                completed.add(route.routeIndex());
            } else if (route.mustTraverse()) {
                selected.add(route.routeIndex());
            }
        }

        // Select the host with the greatest global uncovered coverage. A
        // middle or later U can therefore win when it reaches lanes on both
        // sides, even if the earliest U could save one traversal itself.
        while (true) {
            List<Route<K>> uncovered = routes.stream()
                .filter(route ->
                    !route.complete()
                        && !isCoveredBySelected(
                            route,
                            selected,
                            byIndex,
                            scheduleFinder,
                            scheduleCache
                        ))
                .toList();
            if (uncovered.isEmpty()) break;

            Route<K> destination = routes.stream()
                .filter(route ->
                    !route.complete()
                        && !selected.contains(route.routeIndex()))
                .max((left, right) -> {
                    int coverage = Integer.compare(
                        coverageGain(
                            left,
                            uncovered,
                            scheduleFinder,
                            scheduleCache
                        ),
                        coverageGain(
                            right,
                            uncovered,
                            scheduleFinder,
                            scheduleCache
                        )
                    );
                    if (coverage != 0) return coverage;
                    // max() must prefer the smaller map-order index on ties.
                    return Integer.compare(
                        right.routeIndex(),
                        left.routeIndex()
                    );
                })
                .orElseThrow();
            if (coverageGain(
                    destination,
                    uncovered,
                    scheduleFinder,
                    scheduleCache
                ) <= 0) {
                throw new IllegalStateException(
                    "No teardown traversal can cover an incomplete U."
                );
            }
            selected.add(destination.routeIndex());
        }

        // Later selections may make an earlier greedy host redundant. Remove
        // it only when every incomplete U still has complete proven coverage.
        boolean removed;
        do {
            removed = false;
            ArrayList<Integer> removable = new ArrayList<>(selected);
            removable.sort(Collections.reverseOrder());
            for (int candidate : removable) {
                if (byIndex.get(candidate).mustTraverse()) continue;
                HashSet<Integer> withoutCandidate =
                    new HashSet<>(selected);
                withoutCandidate.remove(candidate);
                boolean allCovered = routes.stream()
                    .filter(route -> !route.complete())
                    .allMatch(route ->
                        isCoveredBySelected(
                            route,
                            withoutCandidate,
                            byIndex,
                            scheduleFinder,
                            scheduleCache
                        ));
                if (!allCovered) continue;
                selected.remove(candidate);
                removed = true;
            }
        } while (removed);

        ArrayList<Integer> traversals =
            new ArrayList<>(selected);
        Collections.sort(traversals);
        Collections.sort(completed);

        LinkedHashMap<Integer, Integer> assignments =
            new LinkedHashMap<>();
        LinkedHashMap<Integer, ArrayList<ScheduledTarget<K>>>
            scheduled = new LinkedHashMap<>();
        traversals.forEach(
            traversal -> scheduled.put(
                traversal,
                new ArrayList<>()
            )
        );
        for (Route<K> source : routes) {
            if (source.complete()
                || selected.contains(source.routeIndex())) {
                continue;
            }
            DestinationSchedule chosen = traversals.stream()
                .map(destinationIndex ->
                    scheduleFor(
                        source,
                        byIndex.get(destinationIndex),
                        scheduleFinder,
                        scheduleCache
                    ).map(indices ->
                        new DestinationSchedule(
                            destinationIndex,
                            indices,
                            Math.abs(
                                destinationIndex
                                    - source.routeIndex()
                            )
                        )
                    ).orElse(null)
                )
                .filter(Objects::nonNull)
                .min((left, right) -> {
                    int distance = Integer.compare(
                        left.routeDistance(),
                        right.routeDistance()
                    );
                    if (distance != 0) return distance;
                    int leftFinish = left.supportIndices().isEmpty()
                        ? 0 : left.supportIndices().getLast();
                    int rightFinish = right.supportIndices().isEmpty()
                        ? 0 : right.supportIndices().getLast();
                    int finish = Integer.compare(
                        leftFinish,
                        rightFinish
                    );
                    if (finish != 0) return finish;
                    return Integer.compare(
                        left.destinationRouteIndex(),
                        right.destinationRouteIndex()
                    );
                })
                .orElseThrow(() -> new IllegalStateException(
                    "Selected teardown hosts do not cover route "
                        + source.routeIndex() + "."
                ));
            assignments.put(
                source.routeIndex(),
                chosen.destinationRouteIndex()
            );
            ArrayList<ScheduledTarget<K>> destinationTargets =
                scheduled.get(chosen.destinationRouteIndex());
            for (int index = 0;
                 index < source.orderedTargets().size();
                 index++) {
                destinationTargets.add(
                    new ScheduledTarget<>(
                        source.routeIndex(),
                        source.orderedTargets().get(index),
                        chosen.supportIndices().get(index),
                        index
                    )
                );
            }
        }

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

    private static <K> int coverageGain(
        Route<K> destination,
        List<Route<K>> uncovered,
        ScheduleFinder<K> scheduleFinder,
        Map<ScheduleKey, Optional<List<Integer>>> scheduleCache
    ) {
        int count = 0;
        for (Route<K> source : uncovered) {
            if (source.routeIndex() == destination.routeIndex()
                || scheduleFor(
                source,
                destination,
                scheduleFinder,
                scheduleCache
            ).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private static <K> boolean isCoveredBySelected(
        Route<K> source,
        HashSet<Integer> selected,
        Map<Integer, Route<K>> byIndex,
        ScheduleFinder<K> scheduleFinder,
        Map<ScheduleKey, Optional<List<Integer>>> scheduleCache
    ) {
        if (selected.contains(source.routeIndex())) return true;
        if (source.mustTraverse()) return false;
        for (int destinationIndex : selected) {
            if (scheduleFor(
                source,
                byIndex.get(destinationIndex),
                scheduleFinder,
                scheduleCache
            ).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static <K> Optional<List<Integer>> scheduleFor(
        Route<K> source,
        Route<K> destination,
        ScheduleFinder<K> scheduleFinder,
        Map<ScheduleKey, Optional<List<Integer>>> scheduleCache
    ) {
        if (source.routeIndex() == destination.routeIndex()
            || source.complete()
            || source.mustTraverse()
            || !destination.canHostRemoteTeardown()) {
            return Optional.empty();
        }
        ScheduleKey key = new ScheduleKey(
            source.routeIndex(),
            destination.routeIndex()
        );
        return scheduleCache.computeIfAbsent(
            key,
            ignored -> findValidatedSchedule(
                source,
                destination.routeIndex(),
                scheduleFinder
            )
        );
    }

    private record ScheduleKey(
        int sourceRouteIndex,
        int destinationRouteIndex
    ) {
        private ScheduleKey {
            if (sourceRouteIndex < 0
                || destinationRouteIndex < 0
                || sourceRouteIndex == destinationRouteIndex) {
                throw new IllegalArgumentException(
                    "A remote teardown schedule requires distinct routes."
                );
            }
        }
    }

    private record DestinationSchedule(
        int destinationRouteIndex,
        List<Integer> supportIndices,
        int routeDistance
    ) {
        private DestinationSchedule {
            if (destinationRouteIndex < 0 || routeDistance < 0) {
                throw new IllegalArgumentException(
                    "A teardown destination has invalid route indices."
                );
            }
            supportIndices = List.copyOf(supportIndices);
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
