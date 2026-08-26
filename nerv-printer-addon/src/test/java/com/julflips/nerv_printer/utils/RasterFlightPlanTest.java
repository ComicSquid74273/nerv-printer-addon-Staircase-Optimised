package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterFlightPlanTest {
    @Test
    void precomputesEveryTargetAndMarksRowTurns() {
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            List.of(
                new RasterFlightPlan.Target(0, 0, -1),
                new RasterFlightPlan.Target(1, 0, -1),
                new RasterFlightPlan.Target(1, 1, 0),
                new RasterFlightPlan.Target(0, 1, 0)
            ),
            5.0,
            0.5,
            3,
            4
        );
        assertEquals(4, plan.waypoints().size());
        assertFalse(plan.waypoints().get(1).rowTurn());
        assertTrue(plan.waypoints().get(2).rowTurn());
        assertEquals(1, plan.waypoints().getFirst().direction());
        assertEquals(-1, plan.waypoints().getLast().direction());
        assertTrue(plan.pathLength() > 0.0);
    }

    @Test
    void steepCrossColumnTransitionCreatesAStoppedVerticalWaypoint() {
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            List.of(
                new RasterFlightPlan.Target(0, 0, 0),
                new RasterFlightPlan.Target(1, 20, 0)
            ),
            5.0,
            0.5,
            2,
            4
        );
        assertTrue(plan.waypoints().stream().anyMatch(RasterFlightPlan.Waypoint::verticalStop));
    }

    @Test
    void twoLaneEnvelopeJumpRequestsVoxelPathInsteadOfCuttingAcrossStrip() {
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            List.of(
                new RasterFlightPlan.Target(0, 10, 0, 4, 1),
                new RasterFlightPlan.Target(1, 10, 2, 4, 1)
            ),
            5.0,
            1.0,
            3,
            4
        );

        assertTrue(plan.waypoints().getLast().rowTurn());
    }

    @Test
    void everyBuildWaypointStaysBelowItsSurfaceBlock() {
        List<RasterFlightPlan.Target> targets = List.of(
            new RasterFlightPlan.Target(0, 12, 0),
            new RasterFlightPlan.Target(0, 13, 1),
            new RasterFlightPlan.Target(0, 11, 2)
        );
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            targets, 5.0, 1.0, 3, 4
        );

        for (int index = 0; index < targets.size(); index++) {
            assertTrue(
                plan.waypoints().get(index).eyeY()
                    <= targets.get(index).y()
                        - RasterFlightPlan.EYE_CLEARANCE_BELOW_SURFACE,
                "The eye path must keep the boat roughly two blocks below the underside."
            );
        }
    }

    @Test
    void oneBlockStairStepsRemainDiagonalButLargeDropsDescendFirst() {
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            List.of(
                new RasterFlightPlan.Target(0, 10, 0),
                new RasterFlightPlan.Target(0, 9, 1),
                new RasterFlightPlan.Target(0, 3, 2)
            ),
            5.0,
            1.0,
            3,
            4
        );

        assertFalse(plan.waypoints().get(1).descendFirst());
        assertTrue(plan.waypoints().get(2).descendFirst());
    }

    @Test
    void compilesTheCompleteStaircasedMapBeforeRuntime() {
        ArrayList<RasterFlightPlan.Target> targets = new ArrayList<>();
        for (int z = -1; z < 128; z++) {
            boolean east = ((z + 1) & 1) == 0;
            for (int offset = 0; offset < 128; offset++) {
                int x = east ? offset : 127 - offset;
                targets.add(new RasterFlightPlan.Target(
                    x,
                    Math.floorMod(x + z, 7),
                    z
                ));
            }
        }
        RasterFlightPlan.Plan plan = RasterFlightPlan.create(
            targets, 5.0, 0.5, 8, 4
        );
        assertEquals(128 * 129, plan.waypoints().size());
        assertEquals(128, plan.waypoints().stream()
            .filter(RasterFlightPlan.Waypoint::rowTurn).count());
        assertTrue(plan.waypoints().stream()
            .allMatch(waypoint -> waypoint.maximumLeadCells() <= 4));
    }
}
