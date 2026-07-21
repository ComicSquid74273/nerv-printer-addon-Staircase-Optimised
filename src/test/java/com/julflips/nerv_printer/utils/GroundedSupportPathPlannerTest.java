package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedSupportPathPlannerTest {
    @Test
    void joinsAnExteriorPlatformToTheWalkway() {
        GroundedSupportPathPlanner.Cell start = cell(21, -1, -3);
        GroundedSupportPathPlanner.Cell goal = cell(21, -1, -1);
        Set<GroundedSupportPathPlanner.Cell> supports = Set.of(
            start,
            cell(21, -1, -2),
            goal
        );

        GroundedSupportPathPlanner.Plan plan =
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(goal),
                ignored -> true,
                supports::contains,
                32
            ).orElseThrow();

        assertEquals(
            List.of(start, cell(21, -1, -2), goal),
            plan.path()
        );
        assertEquals(List.of(goal), plan.waypoints());
    }

    @Test
    void takesAConnectedDetourInsteadOfCrossingMissingSupport() {
        GroundedSupportPathPlanner.Cell start = cell(0, 0, -3);
        GroundedSupportPathPlanner.Cell goal = cell(0, 0, -1);
        Set<GroundedSupportPathPlanner.Cell> supports = Set.of(
            start,
            cell(1, 0, -3),
            cell(1, 0, -2),
            cell(1, 0, -1),
            goal
        );

        GroundedSupportPathPlanner.Plan plan =
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(goal),
                ignored -> true,
                supports::contains,
                32
            ).orElseThrow();

        assertEquals(
            List.of(
                cell(1, 0, -3),
                cell(1, 0, -1),
                goal
            ),
            plan.waypoints()
        );
    }

    @Test
    void supportsOneBlockAutomaticHeightChanges() {
        GroundedSupportPathPlanner.Cell start = cell(0, 0, -3);
        GroundedSupportPathPlanner.Cell goal = cell(0, 1, -1);
        Set<GroundedSupportPathPlanner.Cell> supports = Set.of(
            start,
            cell(0, 1, -2),
            goal
        );

        assertTrue(
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(goal),
                ignored -> true,
                supports::contains,
                32
            ).isPresent()
        );
    }

    @Test
    void rejectsDisconnectedSupports() {
        GroundedSupportPathPlanner.Cell start = cell(0, 0, -3);
        GroundedSupportPathPlanner.Cell goal = cell(0, 0, -1);

        assertTrue(
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(goal),
                ignored -> true,
                Set.of(start, goal)::contains,
                32
            ).isEmpty()
        );
    }

    @Test
    void neverLeavesTheDeclaredRecoveryDomain() {
        GroundedSupportPathPlanner.Cell start = cell(0, 0, -3);
        GroundedSupportPathPlanner.Cell goal = cell(0, 0, -1);
        Set<GroundedSupportPathPlanner.Cell> supports = new HashSet<>(Set.of(
            start,
            cell(1, 0, -3),
            cell(1, 0, -2),
            cell(1, 0, -1),
            goal
        ));

        assertTrue(
            GroundedSupportPathPlanner.findPath(
                start,
                Set.of(goal),
                candidate -> candidate.x() == 0,
                supports::contains,
                32
            ).isEmpty()
        );
    }

    private static GroundedSupportPathPlanner.Cell cell(
        int x,
        int y,
        int z
    ) {
        return new GroundedSupportPathPlanner.Cell(x, y, z);
    }
}
