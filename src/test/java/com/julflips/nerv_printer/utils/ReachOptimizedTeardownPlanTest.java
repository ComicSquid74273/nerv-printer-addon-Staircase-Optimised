package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReachOptimizedTeardownPlanTest {
    @Test
    void currentRouteConsumesFullyReachableNextRoute() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                route(0, "a0", "a1"),
                route(1, "b0", "b1"),
                route(2, "c0", "c1")
            ),
            (targets, destination) -> {
                if (destination == 0
                    && targets.getFirst().startsWith("b")) {
                    return Optional.of(List.of(0, 1));
                }
                return Optional.empty();
            }
        );

        assertEquals(List.of(0, 2), plan.traversalRouteIndices());
        assertEquals(Map.of(1, 0), plan.routeAssignments());
        assertEquals(
            List.of("b0", "b1"),
            plan.scheduledTargetsByTraversal().get(0).stream()
                .map(ReachOptimizedTeardownPlan.ScheduledTarget::target)
                .toList()
        );
    }

    @Test
    void oneTraversalConsumesBothReachableFollowingRoutes() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                route(0, "a"),
                route(1, "b"),
                route(2, "c"),
                route(3, "d")
            ),
            (targets, destination) ->
                destination == 0
                    && (targets.getFirst().equals("b")
                        || targets.getFirst().equals("c"))
                    ? Optional.of(List.of(0))
                    : Optional.empty()
        );

        assertEquals(List.of(0, 3), plan.traversalRouteIndices());
        assertEquals(Map.of(1, 0, 2, 0), plan.routeAssignments());
    }

    @Test
    void laterRouteMayConsumeEarliestWhenEarliestCannotSaveAnotherTraversal() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                route(0, "a"),
                route(1, "b"),
                route(2, "c")
            ),
            (targets, destination) -> destination == 1
                ? Optional.of(List.of(0))
                : Optional.empty()
        );

        assertEquals(List.of(1), plan.traversalRouteIndices());
        assertEquals(Map.of(0, 1, 2, 1), plan.routeAssignments());
    }

    @Test
    void mandatoryRecoveryRouteCannotBeSkipped() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                new ReachOptimizedTeardownPlan.Route<>(
                    0,
                    List.of("remaining"),
                    true,
                    false
                ),
                route(1, "next")
            ),
            (targets, destination) -> Optional.of(List.of(0))
        );

        assertEquals(List.of(0, 1), plan.traversalRouteIndices());
        assertEquals(Map.of(), plan.routeAssignments());
    }

    @Test
    void schedulesPreserveSourcePrefixOrder() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                route(0, "a"),
                route(1, "b0", "b1", "b2")
            ),
            (targets, destination) -> destination == 0
                ? Optional.of(List.of(2, 2, 5))
                : Optional.empty()
        );

        assertEquals(
            List.of(2, 2, 5),
            plan.scheduledTargetsByTraversal().get(0).stream()
                .map(
                    ReachOptimizedTeardownPlan.ScheduledTarget::
                        destinationSupportIndex
                )
                .toList()
        );
    }

    @Test
    void rejectsAReachScheduleThatCouldCreateAnInteriorGap() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ReachOptimizedTeardownPlan.create(
                List.of(
                    route(0, "a"),
                    route(1, "b0", "b1")
                ),
                (targets, destination) -> destination == 0
                    ? Optional.of(List.of(3, 2))
                    : Optional.empty()
            )
        );
    }

    @Test
    void callerSuppliedSafeOrderIsPreservedByTheRemoteSchedule() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                route(0, "host"),
                route(1, "remote-2", "remote-1", "remote-0")
            ),
            (targets, destination) -> destination == 0
                ? Optional.of(List.of(0, 1, 2))
                : Optional.empty()
        );

        assertEquals(Map.of(1, 0), plan.routeAssignments());
        assertEquals(
            List.of("remote-2", "remote-1", "remote-0"),
            plan.scheduledTargetsByTraversal().get(0).stream()
                .map(ReachOptimizedTeardownPlan.ScheduledTarget::target)
                .toList()
        );
    }

    @Test
    void interruptedRemoteSuffixCanMoveToTheNextReachableHost() {
        var plan = ReachOptimizedTeardownPlan.create(
            List.of(
                new ReachOptimizedTeardownPlan.Route<>(
                    0,
                    List.of("remaining-0", "remaining-1"),
                    false,
                    false
                ),
                route(1, "host")
            ),
            (targets, destination) -> destination == 1
                && targets.getFirst().startsWith("remaining")
                    ? Optional.of(List.of(0, 1))
                    : Optional.empty()
        );

        assertEquals(List.of(1), plan.traversalRouteIndices());
        assertEquals(Map.of(0, 1), plan.routeAssignments());
        assertEquals(
            List.of("remaining-0", "remaining-1"),
            plan.scheduledTargetsByTraversal().get(1).stream()
                .map(
                    ReachOptimizedTeardownPlan.ScheduledTarget::target
                )
                .toList()
        );
    }

    private static ReachOptimizedTeardownPlan.Route<String> route(
        int index,
        String... targets
    ) {
        return new ReachOptimizedTeardownPlan.Route<>(
            index,
            List.of(targets),
            false,
            true
        );
    }
}
