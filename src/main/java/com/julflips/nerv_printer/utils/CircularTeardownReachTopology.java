package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Compiles the translation-invariant reach graph between circular U routes.
 *
 * <p>The graph contains geometry only. Authoritative remaining blocks and the
 * current recovery direction are deliberately applied later, so a restart or
 * partial teardown cannot reuse a stale "skip this lane" decision. Each
 * relation retains the exact destination supports reachable from every source
 * target. This permits a cheap runtime schedule for any endpoint-preserving
 * subset without repeating distance calculations.</p>
 */
public final class CircularTeardownReachTopology {
    public static final int SCHEMA_VERSION = 1;
    public static final int ALGORITHM_VERSION = 1;

    private CircularTeardownReachTopology() {
    }

    public record Route(
        int routeIndex,
        List<BlockReachWindow.Cell> orderedTargets
    ) {
        public Route {
            if (routeIndex < 0) {
                throw new IllegalArgumentException(
                    "A teardown route index cannot be negative."
                );
            }
            orderedTargets = List.copyOf(
                Objects.requireNonNull(
                    orderedTargets,
                    "orderedTargets"
                )
            );
            if (orderedTargets.isEmpty()) {
                throw new IllegalArgumentException(
                    "A teardown topology route cannot be empty."
                );
            }
            for (BlockReachWindow.Cell target : orderedTargets) {
                Objects.requireNonNull(target, "route target");
            }
        }
    }

    public record TargetReach(
        int sourceTargetIndex,
        List<Integer> destinationSupportIndices
    ) {
        public TargetReach {
            if (sourceTargetIndex < 0) {
                throw new IllegalArgumentException(
                    "A source target index cannot be negative."
                );
            }
            destinationSupportIndices = requireIncreasingIndices(
                destinationSupportIndices,
                "destination support"
            );
            if (destinationSupportIndices.isEmpty()) {
                throw new IllegalArgumentException(
                    "A persisted target reach cannot be empty."
                );
            }
        }
    }

    public record Relation(
        int sourceRouteIndex,
        int destinationRouteIndex,
        List<TargetReach> targetReaches,
        boolean preserveStartFullyReachable,
        List<Integer> preserveStartDestinationSupports,
        boolean preserveEndFullyReachable,
        List<Integer> preserveEndDestinationSupports
    ) {
        public Relation {
            if (sourceRouteIndex < 0
                || destinationRouteIndex < 0
                || sourceRouteIndex == destinationRouteIndex) {
                throw new IllegalArgumentException(
                    "A reach relation requires distinct nonnegative routes."
                );
            }
            targetReaches = List.copyOf(
                Objects.requireNonNull(targetReaches, "targetReaches")
            );
            if (targetReaches.isEmpty()) {
                throw new IllegalArgumentException(
                    "A persisted reach relation cannot be empty."
                );
            }
            int previousTarget = -1;
            for (TargetReach reach : targetReaches) {
                Objects.requireNonNull(reach, "target reach");
                if (reach.sourceTargetIndex() <= previousTarget) {
                    throw new IllegalArgumentException(
                        "Target reaches must have increasing source indices."
                    );
                }
                previousTarget = reach.sourceTargetIndex();
            }
            preserveStartDestinationSupports = requireSchedule(
                preserveStartFullyReachable,
                preserveStartDestinationSupports,
                "preserve-start"
            );
            preserveEndDestinationSupports = requireSchedule(
                preserveEndFullyReachable,
                preserveEndDestinationSupports,
                "preserve-end"
            );
        }
    }

    public record RouteAssignment(
        int sourceRouteIndex,
        int destinationRouteIndex
    ) {
        public RouteAssignment {
            if (sourceRouteIndex < 0
                || destinationRouteIndex < 0
                || sourceRouteIndex == destinationRouteIndex) {
                throw new IllegalArgumentException(
                    "A compiled route assignment requires distinct routes."
                );
            }
        }
    }

    public record Snapshot(
        int schemaVersion,
        int algorithmVersion,
        String compactPlanSha256,
        double standingEyeHeight,
        double maximumReach,
        List<Integer> targetCounts,
        List<Relation> relations,
        List<Integer> fullMapTraversalRoutes,
        List<RouteAssignment> fullMapRouteAssignments
    ) {
        public Snapshot {
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                    "Unsupported teardown topology schema "
                        + schemaVersion + "."
                );
            }
            if (algorithmVersion != ALGORITHM_VERSION) {
                throw new IllegalArgumentException(
                    "Unsupported teardown topology algorithm "
                        + algorithmVersion + "."
                );
            }
            if (!FileFingerprint.isSha256(compactPlanSha256)) {
                throw new IllegalArgumentException(
                    "The teardown topology has an invalid compact-plan hash."
                );
            }
            if (!Double.isFinite(standingEyeHeight)
                || standingEyeHeight <= 0.0
                || !Double.isFinite(maximumReach)
                || maximumReach <= 0.0) {
                throw new IllegalArgumentException(
                    "The teardown reach profile must be finite and positive."
                );
            }
            targetCounts = List.copyOf(
                Objects.requireNonNull(targetCounts, "targetCounts")
            );
            if (targetCounts.isEmpty()
                || targetCounts.stream().anyMatch(count ->
                    count == null || count <= 0)) {
                throw new IllegalArgumentException(
                    "The teardown topology target counts are invalid."
                );
            }
            relations = List.copyOf(
                Objects.requireNonNull(relations, "relations")
            );
            validateRelations(relations, targetCounts);
            fullMapTraversalRoutes = requireIncreasingIndices(
                fullMapTraversalRoutes,
                "full-map traversal route"
            );
            if (fullMapTraversalRoutes.isEmpty()) {
                throw new IllegalArgumentException(
                    "A compiled full-map plan needs at least one traversal."
                );
            }
            for (int route : fullMapTraversalRoutes) {
                requireRouteIndex(route, targetCounts.size());
            }
            fullMapRouteAssignments = List.copyOf(
                Objects.requireNonNull(
                    fullMapRouteAssignments,
                    "fullMapRouteAssignments"
                )
            );
            validateAssignments(
                fullMapRouteAssignments,
                fullMapTraversalRoutes,
                targetCounts.size(),
                relations
            );
        }

        public Optional<Relation> relation(
            int sourceRouteIndex,
            int destinationRouteIndex
        ) {
            return relations.stream()
                .filter(relation ->
                    relation.sourceRouteIndex() == sourceRouteIndex
                        && relation.destinationRouteIndex()
                            == destinationRouteIndex)
                .findFirst();
        }

        /**
         * Resolves one canonical destination-support index per source target.
         * Source indices must already be in the safe endpoint-preserving
         * removal order selected from the authoritative world state.
         */
        public Optional<List<Integer>> monotonicSchedule(
            int sourceRouteIndex,
            List<Integer> orderedSourceTargetIndices,
            int destinationRouteIndex
        ) {
            requireRouteIndex(sourceRouteIndex, targetCounts.size());
            requireRouteIndex(destinationRouteIndex, targetCounts.size());
            Objects.requireNonNull(
                orderedSourceTargetIndices,
                "orderedSourceTargetIndices"
            );
            if (sourceRouteIndex == destinationRouteIndex
                || orderedSourceTargetIndices.isEmpty()) {
                return Optional.empty();
            }
            Relation relation = relation(
                sourceRouteIndex,
                destinationRouteIndex
            ).orElse(null);
            if (relation == null) return Optional.empty();

            Map<Integer, List<Integer>> reachableByTarget =
                new LinkedHashMap<>();
            for (TargetReach reach : relation.targetReaches()) {
                reachableByTarget.put(
                    reach.sourceTargetIndex(),
                    reach.destinationSupportIndices()
                );
            }

            int sourceCount = targetCounts.get(sourceRouteIndex);
            int destinationCount = targetCounts.get(
                destinationRouteIndex
            );
            int previousSupport = 0;
            ArrayList<Integer> schedule = new ArrayList<>(
                orderedSourceTargetIndices.size()
            );
            HashSet<Integer> duplicateGuard = new HashSet<>();
            for (Integer sourceTarget : orderedSourceTargetIndices) {
                if (sourceTarget == null
                    || sourceTarget < 0
                    || sourceTarget >= sourceCount
                    || !duplicateGuard.add(sourceTarget)) {
                    throw new IllegalArgumentException(
                        "The source target order contains an invalid index."
                    );
                }
                List<Integer> reachable = reachableByTarget.get(
                    sourceTarget
                );
                if (reachable == null) return Optional.empty();
                int selected = -1;
                for (int support : reachable) {
                    if (support >= destinationCount) {
                        throw new IllegalStateException(
                            "The persisted reach topology exceeds its route."
                        );
                    }
                    if (support >= previousSupport) {
                        selected = support;
                        break;
                    }
                }
                if (selected < 0) return Optional.empty();
                previousSupport = selected;
                schedule.add(selected);
            }
            return Optional.of(List.copyOf(schedule));
        }
    }

    public static Snapshot compile(
        String compactPlanSha256,
        List<Route> orderedRoutes,
        double standingEyeHeight,
        double maximumReach
    ) {
        if (!FileFingerprint.isSha256(compactPlanSha256)) {
            throw new IllegalArgumentException(
                "The compact-plan fingerprint must be SHA-256."
            );
        }
        Objects.requireNonNull(orderedRoutes, "orderedRoutes");
        if (orderedRoutes.isEmpty()) {
            throw new IllegalArgumentException(
                "A teardown topology requires routes."
            );
        }
        ArrayList<Route> routes = new ArrayList<>(orderedRoutes.size());
        int previousRoute = -1;
        for (Route route : orderedRoutes) {
            Route candidate = Objects.requireNonNull(route, "route");
            if (candidate.routeIndex() != previousRoute + 1) {
                throw new IllegalArgumentException(
                    "Topology routes must be contiguous and map ordered."
                );
            }
            previousRoute = candidate.routeIndex();
            routes.add(candidate);
        }

        ArrayList<Integer> targetCounts = new ArrayList<>(routes.size());
        routes.forEach(route ->
            targetCounts.add(route.orderedTargets().size())
        );
        ArrayList<Relation> relations = new ArrayList<>();
        for (Route source : routes) {
            int sourceMinX = source.orderedTargets().stream()
                .mapToInt(BlockReachWindow.Cell::x)
                .min().orElseThrow();
            int sourceMaxX = source.orderedTargets().stream()
                .mapToInt(BlockReachWindow.Cell::x)
                .max().orElseThrow();
            for (Route destination : routes) {
                if (source.routeIndex() == destination.routeIndex()) {
                    continue;
                }
                int destinationMinX = destination.orderedTargets().stream()
                    .mapToInt(BlockReachWindow.Cell::x)
                    .min().orElseThrow();
                int destinationMaxX = destination.orderedTargets().stream()
                    .mapToInt(BlockReachWindow.Cell::x)
                    .max().orElseThrow();
                double minimumXDistance = intervalDistance(
                    sourceMinX,
                    sourceMaxX,
                    destinationMinX,
                    destinationMaxX
                );
                if (minimumXDistance > maximumReach) continue;

                ArrayList<TargetReach> reaches = new ArrayList<>();
                for (int sourceIndex = 0;
                     sourceIndex < source.orderedTargets().size();
                     sourceIndex++) {
                    Optional<BlockReachWindow.Window> window =
                        BlockReachWindow.find(
                            source.orderedTargets().get(sourceIndex),
                            destination.orderedTargets(),
                            standingEyeHeight,
                            maximumReach
                        );
                    if (window.isPresent()) {
                        reaches.add(
                            new TargetReach(
                                sourceIndex,
                                window.orElseThrow()
                                    .reachableSupportIndices()
                            )
                        );
                    }
                }
                if (reaches.isEmpty()) continue;

                List<Integer> preserveStartOrder = IntStream.range(
                        0,
                        source.orderedTargets().size()
                    )
                    .map(index ->
                        source.orderedTargets().size() - 1 - index)
                    .boxed()
                    .toList();
                List<Integer> preserveEndOrder = IntStream.range(
                        0,
                        source.orderedTargets().size()
                    )
                    .boxed()
                    .toList();
                Optional<List<Integer>> preserveStart = schedule(
                    reaches,
                    preserveStartOrder
                );
                Optional<List<Integer>> preserveEnd = schedule(
                    reaches,
                    preserveEndOrder
                );
                relations.add(
                    new Relation(
                        source.routeIndex(),
                        destination.routeIndex(),
                        reaches,
                        preserveStart.isPresent(),
                        preserveStart.orElse(List.of()),
                        preserveEnd.isPresent(),
                        preserveEnd.orElse(List.of())
                    )
                );
            }
        }

        ReachOptimizedTeardownPlan.Plan<PlannerTarget> fullMapPlan =
            compileFullMapPlan(routes, relations);
        ArrayList<RouteAssignment> assignments = new ArrayList<>();
        fullMapPlan.routeAssignments().forEach(
            (source, destination) -> assignments.add(
                new RouteAssignment(source, destination)
            )
        );
        assignments.sort((left, right) -> Integer.compare(
            left.sourceRouteIndex(),
            right.sourceRouteIndex()
        ));

        return new Snapshot(
            SCHEMA_VERSION,
            ALGORITHM_VERSION,
            compactPlanSha256,
            standingEyeHeight,
            maximumReach,
            targetCounts,
            relations,
            fullMapPlan.traversalRouteIndices(),
            assignments
        );
    }

    private record PlannerTarget(int routeIndex, int targetIndex) {
    }

    private static ReachOptimizedTeardownPlan.Plan<PlannerTarget>
        compileFullMapPlan(
            List<Route> routes,
            List<Relation> relations
        ) {
        ArrayList<ReachOptimizedTeardownPlan.Route<PlannerTarget>>
            plannerRoutes =
            new ArrayList<>(routes.size());
        for (Route route : routes) {
            ArrayList<PlannerTarget> preserveStartOrder = new ArrayList<>(
                IntStream.range(0, route.orderedTargets().size())
                    .mapToObj(index -> new PlannerTarget(
                        route.routeIndex(),
                        index
                    ))
                    .toList()
            );
            Collections.reverse(preserveStartOrder);
            plannerRoutes.add(
                new ReachOptimizedTeardownPlan.Route<>(
                    route.routeIndex(),
                    preserveStartOrder,
                    false,
                    true
                )
            );
        }
        return ReachOptimizedTeardownPlan.create(
            plannerRoutes,
            (sourceTargets, destinationRoute) -> {
                if (sourceTargets.isEmpty()) return Optional.empty();
                int sourceRoute = sourceTargets.getFirst().routeIndex();
                Relation relation = relation(
                    relations,
                    sourceRoute,
                    destinationRoute
                );
                if (relation == null
                    || !relation.preserveStartFullyReachable()) {
                    return Optional.empty();
                }
                return Optional.of(
                    relation.preserveStartDestinationSupports()
                );
            }
        );
    }

    private static Optional<List<Integer>> schedule(
        List<TargetReach> reaches,
        List<Integer> orderedSourceTargets
    ) {
        Map<Integer, List<Integer>> byTarget = new LinkedHashMap<>();
        reaches.forEach(reach -> byTarget.put(
            reach.sourceTargetIndex(),
            reach.destinationSupportIndices()
        ));
        int previousSupport = 0;
        ArrayList<Integer> result = new ArrayList<>(
            orderedSourceTargets.size()
        );
        for (int sourceTarget : orderedSourceTargets) {
            List<Integer> candidates = byTarget.get(sourceTarget);
            if (candidates == null) return Optional.empty();
            int selected = -1;
            for (int candidate : candidates) {
                if (candidate >= previousSupport) {
                    selected = candidate;
                    break;
                }
            }
            if (selected < 0) return Optional.empty();
            previousSupport = selected;
            result.add(selected);
        }
        return Optional.of(List.copyOf(result));
    }

    private static Relation relation(
        List<Relation> relations,
        int sourceRoute,
        int destinationRoute
    ) {
        return relations.stream()
            .filter(candidate ->
                candidate.sourceRouteIndex() == sourceRoute
                    && candidate.destinationRouteIndex()
                        == destinationRoute)
            .findFirst()
            .orElse(null);
    }

    private static List<Integer> requireSchedule(
        boolean fullyReachable,
        List<Integer> schedule,
        String label
    ) {
        schedule = List.copyOf(
            Objects.requireNonNull(schedule, label + " schedule")
        );
        if (fullyReachable != !schedule.isEmpty()) {
            throw new IllegalArgumentException(
                "The " + label + " reach flag and schedule disagree."
            );
        }
        int previous = -1;
        for (Integer support : schedule) {
            if (support == null || support < previous) {
                throw new IllegalArgumentException(
                    "The " + label + " schedule is not monotonic."
                );
            }
            previous = support;
        }
        return schedule;
    }

    private static List<Integer> requireIncreasingIndices(
        List<Integer> values,
        String label
    ) {
        values = List.copyOf(
            Objects.requireNonNull(values, label + " indices")
        );
        int previous = -1;
        for (Integer value : values) {
            if (value == null || value <= previous) {
                throw new IllegalArgumentException(
                    "The " + label + " indices must be increasing."
                );
            }
            previous = value;
        }
        return values;
    }

    private static void validateRelations(
        List<Relation> relations,
        List<Integer> targetCounts
    ) {
        long previousKey = -1L;
        for (Relation relation : relations) {
            Objects.requireNonNull(relation, "relation");
            requireRouteIndex(
                relation.sourceRouteIndex(),
                targetCounts.size()
            );
            requireRouteIndex(
                relation.destinationRouteIndex(),
                targetCounts.size()
            );
            long key = (long) relation.sourceRouteIndex()
                * targetCounts.size()
                + relation.destinationRouteIndex();
            if (key <= previousKey) {
                throw new IllegalArgumentException(
                    "Reach relations must be unique and map ordered."
                );
            }
            previousKey = key;
            int sourceCount = targetCounts.get(
                relation.sourceRouteIndex()
            );
            int destinationCount = targetCounts.get(
                relation.destinationRouteIndex()
            );
            for (TargetReach reach : relation.targetReaches()) {
                if (reach.sourceTargetIndex() >= sourceCount
                    || reach.destinationSupportIndices().getLast()
                        >= destinationCount) {
                    throw new IllegalArgumentException(
                        "A reach relation exceeds its route geometry."
                    );
                }
            }
            validateCompleteSchedule(
                relation.preserveStartFullyReachable(),
                relation.preserveStartDestinationSupports(),
                sourceCount,
                destinationCount,
                relation.targetReaches(),
                true
            );
            validateCompleteSchedule(
                relation.preserveEndFullyReachable(),
                relation.preserveEndDestinationSupports(),
                sourceCount,
                destinationCount,
                relation.targetReaches(),
                false
            );
        }
    }

    private static void validateCompleteSchedule(
        boolean fullyReachable,
        List<Integer> schedule,
        int sourceTargetCount,
        int destinationTargetCount,
        List<TargetReach> reaches,
        boolean reverseSourceOrder
    ) {
        if (fullyReachable && schedule.size() != sourceTargetCount) {
            throw new IllegalArgumentException(
                "A full-route reach schedule has the wrong target count."
            );
        }
        if (!fullyReachable) return;
        Map<Integer, List<Integer>> reachableByTarget =
            new LinkedHashMap<>();
        reaches.forEach(reach -> reachableByTarget.put(
            reach.sourceTargetIndex(),
            reach.destinationSupportIndices()
        ));
        for (int scheduleIndex = 0;
             scheduleIndex < schedule.size();
             scheduleIndex++) {
            int sourceTarget = reverseSourceOrder
                ? sourceTargetCount - 1 - scheduleIndex
                : scheduleIndex;
            int destinationSupport = schedule.get(scheduleIndex);
            if (destinationSupport >= destinationTargetCount
                || !reachableByTarget.getOrDefault(
                    sourceTarget,
                    List.of()
                ).contains(destinationSupport)) {
                throw new IllegalArgumentException(
                    "A full-route schedule is not proven by its reach "
                        + "windows."
                );
            }
        }
    }

    private static void validateAssignments(
        List<RouteAssignment> assignments,
        List<Integer> traversals,
        int routeCount,
        List<Relation> relations
    ) {
        HashSet<Integer> hosts = new HashSet<>(traversals);
        HashSet<Integer> sources = new HashSet<>();
        int previousSource = -1;
        for (RouteAssignment assignment : assignments) {
            Objects.requireNonNull(assignment, "route assignment");
            requireRouteIndex(assignment.sourceRouteIndex(), routeCount);
            requireRouteIndex(
                assignment.destinationRouteIndex(),
                routeCount
            );
            if (assignment.sourceRouteIndex() <= previousSource
                || !sources.add(assignment.sourceRouteIndex())
                || hosts.contains(assignment.sourceRouteIndex())
                || !hosts.contains(assignment.destinationRouteIndex())
                || Optional.ofNullable(relation(
                    relations,
                    assignment.sourceRouteIndex(),
                    assignment.destinationRouteIndex()
                )).filter(Relation::preserveStartFullyReachable)
                    .isEmpty()) {
                throw new IllegalArgumentException(
                    "The compiled full-map route assignments are invalid."
                );
            }
            previousSource = assignment.sourceRouteIndex();
        }
        if (hosts.size() + sources.size() != routeCount) {
            throw new IllegalArgumentException(
                "The compiled full-map plan does not cover every route."
            );
        }
    }

    private static void requireRouteIndex(int route, int routeCount) {
        if (route < 0 || route >= routeCount) {
            throw new IllegalArgumentException(
                "A teardown topology route index is out of bounds."
            );
        }
    }

    private static double intervalDistance(
        int firstMin,
        int firstMax,
        int secondMin,
        int secondMax
    ) {
        if (firstMax < secondMin) return secondMin - firstMax;
        if (secondMax < firstMin) return firstMin - secondMax;
        return 0.0;
    }
}
