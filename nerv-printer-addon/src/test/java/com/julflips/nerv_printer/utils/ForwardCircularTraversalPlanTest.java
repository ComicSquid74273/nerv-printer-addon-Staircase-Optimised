package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForwardCircularTraversalPlanTest {
    @Test
    void skipsCompleteRouteAndDefersSparseRouteToNearestLaterTraversal() {
        ForwardCircularTraversalPlan.Plan<String> plan =
            ForwardCircularTraversalPlan.create(
                List.of(
                    route(1, List.of(), false),
                    route(2, List.of("a", "b"), false),
                    route(3, List.of("c"), false)
                ),
                (target, destination) ->
                    destination == 3
                        && (target.equals("a") || target.equals("b")),
                (destination, targets) -> true
            );

        assertEquals(List.of(3), plan.traversalRouteIndices());
        assertEquals(
            Map.of(3, List.of("a", "b")),
            plan.deferredTargetsByTraversal()
        );
        assertEquals(
            Map.of(2, 3),
            plan.deferredRouteAssignments()
        );
        assertEquals(List.of(1), plan.completedRouteIndices());
    }

    @Test
    void retainsTraversalWhenAnyTargetIsUnreachableOrDemandDoesNotFit() {
        ForwardCircularTraversalPlan.Plan<String> unreachable =
            ForwardCircularTraversalPlan.create(
                List.of(
                    route(2, List.of("reachable", "unreachable"), false),
                    route(3, List.of("anchor"), false)
                ),
                (target, destination) -> !target.equals("unreachable"),
                (destination, targets) -> true
            );
        assertEquals(
            List.of(2, 3),
            unreachable.traversalRouteIndices()
        );

        ForwardCircularTraversalPlan.Plan<String> noCapacity =
            ForwardCircularTraversalPlan.create(
                List.of(
                    route(2, List.of("reachable"), false),
                    route(3, List.of("anchor"), false)
                ),
                (target, destination) -> true,
                (destination, targets) -> false
            );
        assertEquals(
            List.of(2, 3),
            noCapacity.traversalRouteIndices()
        );
    }

    @Test
    void wrongBlockAlwaysKeepsItsNormalRepairTraversal() {
        ForwardCircularTraversalPlan.Plan<String> plan =
            ForwardCircularTraversalPlan.create(
                List.of(
                    route(2, List.of("air"), true),
                    route(3, List.of("later"), false)
                ),
                (target, destination) -> true,
                (destination, targets) -> true
            );

        assertEquals(List.of(2, 3), plan.traversalRouteIndices());
        assertEquals(Map.of(), plan.deferredRouteAssignments());
    }

    @Test
    void validatesOrderingAndUniqueTargetOwnership() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ForwardCircularTraversalPlan.create(
                List.of(
                    route(2, List.of("a"), false),
                    route(1, List.of("b"), false)
                ),
                (target, destination) -> true,
                (destination, targets) -> true
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ForwardCircularTraversalPlan.create(
                List.of(
                    route(1, List.of("same"), false),
                    route(2, List.of("same"), false)
                ),
                (target, destination) -> true,
                (destination, targets) -> true
            )
        );
    }

    private static ForwardCircularTraversalPlan.Route<String> route(
        int index,
        List<String> missing,
        boolean wrong
    ) {
        return new ForwardCircularTraversalPlan.Route<>(
            index,
            missing,
            wrong
        );
    }
}
