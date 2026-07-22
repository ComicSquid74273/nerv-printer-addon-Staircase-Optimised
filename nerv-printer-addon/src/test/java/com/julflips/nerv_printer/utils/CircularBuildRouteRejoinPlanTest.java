package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildRouteRejoinPlanTest {
    @Test
    void rejoinsRetainedSupportAfterLateralHeightOvershoot() {
        var retained = cell(71, -45, 127);
        var start = cell(72, -44, 127);
        List<GroundedSupportPathPlanner.Cell> route = List.of(
            cell(70, -45, 127),
            retained,
            cell(71, -44, 126)
        );
        Set<GroundedSupportPathPlanner.Cell> walkable = Set.of(
            start,
            retained
        );

        CircularBuildRouteRejoinPlan.Plan plan =
            CircularBuildRouteRejoinPlan.find(
                start,
                route,
                1,
                0,
                2,
                1,
                0,
                ignored -> true,
                walkable::contains,
                16
            ).orElseThrow();

        assertEquals(1, plan.routeSupportIndex());
        assertEquals(List.of(start, retained), plan.path());
    }

    @Test
    void adjacentOppositeLegCannotStealASeparatedCursor() {
        var start = cell(2, 0, 5);
        var physicallyNearOppositeLeg = cell(1, 0, 5);
        List<GroundedSupportPathPlanner.Cell> route = List.of(
            cell(0, 0, 5),
            cell(0, 0, 6),
            cell(0, 0, 7),
            physicallyNearOppositeLeg
        );

        assertTrue(
            CircularBuildRouteRejoinPlan.find(
                start,
                route,
                0,
                0,
                3,
                1,
                0,
                ignored -> true,
                Set.of(start, physicallyNearOppositeLeg)::contains,
                16
            ).isEmpty()
        );
    }

    @Test
    void rejectsUnconfirmedGapBackToRetainedRoute() {
        var start = cell(3, 0, 0);
        var retained = cell(0, 0, 0);

        assertTrue(
            CircularBuildRouteRejoinPlan.find(
                start,
                List.of(retained),
                0,
                0,
                0,
                1,
                0,
                ignored -> true,
                Set.of(start, retained)::contains,
                16
            ).isEmpty()
        );
    }

    @Test
    void rejoinsBehindRetainedCursorToReplayConfirmedSupports() {
        var start = cell(1, 0, 5);
        List<GroundedSupportPathPlanner.Cell> route = List.of(
            cell(0, 0, 0),
            cell(0, 0, 1),
            cell(0, 0, 2),
            cell(0, 0, 3),
            cell(0, 0, 4),
            cell(0, 0, 5),
            cell(0, 0, 6)
        );
        Set<GroundedSupportPathPlanner.Cell> walkable = Set.of(
            start,
            route.get(2),
            route.get(3),
            route.get(4),
            route.get(5)
        );

        CircularBuildRouteRejoinPlan.Plan plan =
            CircularBuildRouteRejoinPlan.find(
                start,
                route,
                5,
                0,
                6,
                1,
                3,
                ignored -> true,
                walkable::contains,
                32
            ).orElseThrow();

        assertEquals(2, plan.routeSupportIndex());
        assertEquals(route.get(2), plan.path().getLast());
    }

    @Test
    void routeDomainIncludesGeneratedFarConnectorExtension() {
        var start = cell(9, 4, 130);
        List<GroundedSupportPathPlanner.Cell> connector = List.of(
            cell(10, 3, 127),
            cell(10, 4, 128),
            cell(10, 5, 129),
            cell(11, 6, 130)
        );

        CircularBuildRouteRejoinPlan.Domain domain =
            CircularBuildRouteRejoinPlan.routeDomain(start, connector);

        assertTrue(domain.contains(start));
        assertTrue(domain.contains(connector.getLast()));
        assertFalse(domain.contains(cell(11, 6, 131)));
    }

    @Test
    void farConnectorOvershootRejoinsThreeSupportsBehindCursor() {
        var start = cell(9, 0, 130);
        List<GroundedSupportPathPlanner.Cell> connector = List.of(
            cell(10, 0, 127),
            cell(10, 0, 128),
            cell(10, 0, 129),
            cell(10, 0, 130),
            cell(11, 0, 130)
        );
        Set<GroundedSupportPathPlanner.Cell> walkable = Set.of(
            start,
            connector.get(0),
            connector.get(1),
            connector.get(2),
            connector.get(3)
        );
        CircularBuildRouteRejoinPlan.Domain domain =
            CircularBuildRouteRejoinPlan.routeDomain(
                start,
                connector
            );

        CircularBuildRouteRejoinPlan.Plan plan =
            CircularBuildRouteRejoinPlan.find(
                start,
                connector,
                3,
                0,
                4,
                1,
                3,
                domain::contains,
                walkable::contains,
                32
            ).orElseThrow();

        assertEquals(0, plan.routeSupportIndex());
        assertEquals(connector.getFirst(), plan.path().getLast());
        assertTrue(plan.path().contains(cell(10, 0, 130)));
    }

    private static GroundedSupportPathPlanner.Cell cell(
        int x,
        int y,
        int z
    ) {
        return new GroundedSupportPathPlanner.Cell(x, y, z);
    }
}
