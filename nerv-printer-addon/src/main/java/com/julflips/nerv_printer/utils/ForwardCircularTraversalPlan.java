package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Selects the minimum safe forward set of circular routes to walk.
 *
 * <p>A complete route is omitted. A route containing only missing-air targets
 * may also be omitted when one already-selected later traversal can reach
 * every missing target and can carry the complete cumulative deferred demand.
 * Wrong blocks always retain their own traversal so normal active-U repair
 * ownership is never widened.</p>
 */
public final class ForwardCircularTraversalPlan {
    private ForwardCircularTraversalPlan() {
    }

    public record Route<K>(
        int routeIndex,
        List<K> missingTargets,
        boolean containsWrongTarget
    ) {
        public Route {
            if (routeIndex < 0) {
                throw new IllegalArgumentException(
                    "Route index cannot be negative."
                );
            }
            missingTargets = List.copyOf(
                Objects.requireNonNull(
                    missingTargets,
                    "missingTargets"
                )
            );
            if (new HashSet<>(missingTargets).size()
                != missingTargets.size()) {
                throw new IllegalArgumentException(
                    "A route cannot contain duplicate missing targets."
                );
            }
        }

        public boolean complete() {
            return missingTargets.isEmpty() && !containsWrongTarget;
        }
    }

    public record Plan<K>(
        List<Integer> traversalRouteIndices,
        Map<Integer, List<K>> deferredTargetsByTraversal,
        Map<Integer, Integer> deferredRouteAssignments,
        List<Integer> completedRouteIndices
    ) {
        public Plan {
            traversalRouteIndices = List.copyOf(
                traversalRouteIndices
            );
            deferredTargetsByTraversal =
                immutableListMap(deferredTargetsByTraversal);
            deferredRouteAssignments =
                Collections.unmodifiableMap(
                    new LinkedHashMap<>(deferredRouteAssignments)
                );
            completedRouteIndices = List.copyOf(
                completedRouteIndices
            );
        }
    }

    /**
     * @param targetReachableFromTraversal target/route reach proof
     * @param cumulativeDemandFits complete deferred-demand capacity proof
     */
    public static <K> Plan<K> create(
        List<? extends Route<K>> orderedRoutes,
        BiPredicate<? super K, Integer>
            targetReachableFromTraversal,
        BiPredicate<Integer, List<K>> cumulativeDemandFits
    ) {
        Objects.requireNonNull(orderedRoutes, "orderedRoutes");
        Objects.requireNonNull(
            targetReachableFromTraversal,
            "targetReachableFromTraversal"
        );
        Objects.requireNonNull(
            cumulativeDemandFits,
            "cumulativeDemandFits"
        );

        ArrayList<Route<K>> routes = new ArrayList<>(
            orderedRoutes.size()
        );
        int previousIndex = -1;
        HashSet<K> allTargets = new HashSet<>();
        for (Route<K> route : orderedRoutes) {
            Route<K> candidate =
                Objects.requireNonNull(route, "route");
            if (candidate.routeIndex() <= previousIndex) {
                throw new IllegalArgumentException(
                    "Routes must have strictly increasing indices."
                );
            }
            previousIndex = candidate.routeIndex();
            for (K target : candidate.missingTargets()) {
                if (!allTargets.add(
                    Objects.requireNonNull(target, "missing target")
                )) {
                    throw new IllegalArgumentException(
                        "Missing targets cannot belong to multiple routes."
                    );
                }
            }
            routes.add(candidate);
        }

        ArrayList<Integer> selectedReverse = new ArrayList<>();
        LinkedHashMap<Integer, ArrayList<K>> deferredByTraversal =
            new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> assignments =
            new LinkedHashMap<>();
        ArrayList<Integer> completed = new ArrayList<>();

        for (int routePosition = routes.size() - 1;
             routePosition >= 0;
             routePosition--) {
            Route<K> route = routes.get(routePosition);
            if (route.complete()) {
                completed.add(route.routeIndex());
                continue;
            }

            Integer destination = null;
            if (!route.containsWrongTarget()
                && !route.missingTargets().isEmpty()) {
                // selectedReverse is farthest-to-nearest. Test nearest first.
                for (int selectedPosition =
                         selectedReverse.size() - 1;
                     selectedPosition >= 0;
                     selectedPosition--) {
                    int candidateDestination =
                        selectedReverse.get(selectedPosition);
                    boolean allReachable =
                        route.missingTargets().stream().allMatch(
                            target ->
                                targetReachableFromTraversal.test(
                                    target,
                                    candidateDestination
                                )
                        );
                    if (!allReachable) continue;

                    ArrayList<K> cumulative = new ArrayList<>(
                        route.missingTargets()
                    );
                    cumulative.addAll(
                        deferredByTraversal.getOrDefault(
                            candidateDestination,
                            new ArrayList<>()
                        )
                    );
                    if (!cumulativeDemandFits.test(
                        candidateDestination,
                        List.copyOf(cumulative)
                    )) {
                        continue;
                    }
                    destination = candidateDestination;
                    deferredByTraversal.put(
                        candidateDestination,
                        cumulative
                    );
                    assignments.put(
                        route.routeIndex(),
                        candidateDestination
                    );
                    break;
                }
            }

            if (destination == null) {
                selectedReverse.add(route.routeIndex());
                deferredByTraversal.putIfAbsent(
                    route.routeIndex(),
                    new ArrayList<>()
                );
            }
        }

        Collections.reverse(selectedReverse);
        Collections.sort(completed);
        LinkedHashMap<Integer, List<K>> immutableDeferred =
            new LinkedHashMap<>();
        for (int routeIndex : selectedReverse) {
            immutableDeferred.put(
                routeIndex,
                List.copyOf(
                    deferredByTraversal.getOrDefault(
                        routeIndex,
                        new ArrayList<>()
                    )
                )
            );
        }
        return new Plan<>(
            selectedReverse,
            immutableDeferred,
            assignments,
            completed
        );
    }

    private static <K, V> Map<K, List<V>> immutableListMap(
        Map<K, ? extends List<V>> source
    ) {
        LinkedHashMap<K, List<V>> result = new LinkedHashMap<>();
        source.forEach(
            (key, value) -> result.put(key, List.copyOf(value))
        );
        return Collections.unmodifiableMap(result);
    }
}
