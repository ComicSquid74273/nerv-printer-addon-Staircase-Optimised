package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.julflips.nerv_printer.utils.LogisticsDetourPlanner.Direction.EAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsDetourPlannerTest {
    private static final LogisticsDetourPlanner.Point START =
        new LogisticsDetourPlanner.Point(0, 0);
    private static final LogisticsDetourPlanner.Point TERMINAL =
        new LogisticsDetourPlanner.Point(6, 0);

    @Test
    void returnsEmptyWhenTheDirectStepIsClear() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);

        assertTrue(plan(passable, 3, 100).isEmpty());
    }

    @Test
    void bypassesASmallWallOnTheLeftByDefault() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));

        LogisticsDetourPlanner.Plan plan = plan(passable, 3, 100).orElseThrow();

        assertEquals(new LogisticsDetourPlanner.Point(2, 0), plan.endpoint());
        assertEquals(
            List.of(
                START,
                new LogisticsDetourPlanner.Point(0, -1),
                new LogisticsDetourPlanner.Point(1, -1),
                new LogisticsDetourPlanner.Point(2, -1),
                new LogisticsDetourPlanner.Point(2, 0)
            ),
            plan.path()
        );
        assertEquals(
            List.of(
                new LogisticsDetourPlanner.Point(0, -1),
                new LogisticsDetourPlanner.Point(2, -1),
                new LogisticsDetourPlanner.Point(2, 0)
            ),
            plan.waypoints()
        );
    }

    @Test
    void usesTheRightWhenTheLeftSideIsBlocked() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));
        passable.remove(new LogisticsDetourPlanner.Point(0, -1));

        LogisticsDetourPlanner.Plan plan = plan(passable, 3, 100).orElseThrow();

        assertEquals(new LogisticsDetourPlanner.Point(0, 1), plan.path().get(1));
        assertEquals(new LogisticsDetourPlanner.Point(2, 0), plan.endpoint());
    }

    @Test
    void usesTheRightWhenOnlyTheLeftMovementEdgeIsBlocked() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));
        LogisticsDetourPlanner.Point left =
            new LogisticsDetourPlanner.Point(0, -1);

        LogisticsDetourPlanner.Plan plan = LogisticsDetourPlanner.findBypass(
            passable,
            START,
            EAST,
            TERMINAL,
            (from, to) -> !(from.equals(START) && to.equals(left)),
            3,
            100
        ).orElseThrow();

        assertEquals(new LogisticsDetourPlanner.Point(0, 1), plan.path().get(1));
        assertEquals(new LogisticsDetourPlanner.Point(2, 0), plan.endpoint());
    }

    @Test
    void bypassesWhenTheForwardCellIsClearButItsEdgeIsBlocked() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        LogisticsDetourPlanner.Point direct =
            new LogisticsDetourPlanner.Point(1, 0);

        LogisticsDetourPlanner.Plan plan = LogisticsDetourPlanner.findBypass(
            passable,
            START,
            EAST,
            TERMINAL,
            (from, to) -> !(from.equals(START) && to.equals(direct)),
            3,
            100
        ).orElseThrow();

        assertEquals(new LogisticsDetourPlanner.Point(0, -1), plan.path().get(1));
        assertEquals(direct, plan.endpoint());
    }

    @Test
    void symmetricRoutesUseTheStableLeftTieBreak() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));

        for (int run = 0; run < 10; run++) {
            LogisticsDetourPlanner.Plan plan =
                plan(new HashSet<>(passable), 3, 100).orElseThrow();
            assertEquals(
                new LogisticsDetourPlanner.Point(0, -1),
                plan.path().get(1)
            );
        }
    }

    @Test
    void returnsEmptyWhenNoSafeRejoinExists() {
        Set<LogisticsDetourPlanner.Point> passable = Set.of(
            START,
            new LogisticsDetourPlanner.Point(0, -1),
            new LogisticsDetourPlanner.Point(1, -1),
            new LogisticsDetourPlanner.Point(0, 1),
            new LogisticsDetourPlanner.Point(1, 1)
        );

        assertTrue(plan(passable, 3, 100).isEmpty());
    }

    @Test
    void honorsRadiusAndNodeBounds() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));

        assertTrue(plan(passable, 1, 100).isEmpty());
        assertTrue(plan(passable, 3, 3).isEmpty());
        assertTrue(plan(passable, 2, 100).isPresent());
    }

    @Test
    void everyFullPathStepIsCardinalAndMakesTerminalProgress() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(3);
        passable.remove(new LogisticsDetourPlanner.Point(1, 0));

        LogisticsDetourPlanner.Plan plan = plan(passable, 3, 100).orElseThrow();
        for (int index = 1; index < plan.path().size(); index++) {
            assertEquals(
                1,
                plan.path().get(index - 1).manhattanDistance(plan.path().get(index))
            );
        }
        assertTrue(
            plan.endpoint().manhattanDistance(TERMINAL)
                < START.manhattanDistance(TERMINAL)
        );
    }

    @Test
    void collapsedWaypointsRejectNonCardinalInput() {
        assertThrows(
            IllegalArgumentException.class,
            () -> LogisticsDetourPlanner.collapseToTurnWaypoints(List.of(
                START,
                new LogisticsDetourPlanner.Point(1, 1)
            ))
        );
    }

    @Test
    void sidestepMovesTwoBlocksLeftWhenBothSidesAreOpen() {
        LogisticsDetourPlanner.Plan sidestep =
            LogisticsDetourPlanner.findSidestep(
                openSquare(2),
                START,
                EAST,
                (from, to) -> true,
                2
            ).orElseThrow();

        assertEquals(
            List.of(
                START,
                new LogisticsDetourPlanner.Point(0, -1),
                new LogisticsDetourPlanner.Point(0, -2)
            ),
            sidestep.path()
        );
        assertEquals(
            List.of(new LogisticsDetourPlanner.Point(0, -2)),
            sidestep.waypoints()
        );
    }

    @Test
    void sidestepUsesTheRightWhenItProvidesMoreClearance() {
        Set<LogisticsDetourPlanner.Point> passable = openSquare(2);
        passable.remove(new LogisticsDetourPlanner.Point(0, -2));

        LogisticsDetourPlanner.Plan sidestep =
            LogisticsDetourPlanner.findSidestep(
                passable,
                START,
                EAST,
                (from, to) -> true,
                2
            ).orElseThrow();

        assertEquals(
            new LogisticsDetourPlanner.Point(0, 2),
            sidestep.endpoint()
        );
    }

    @Test
    void sidestepUsesOneBlockOrRejectsUnsafeEdges() {
        Set<LogisticsDetourPlanner.Point> passable = Set.of(
            START,
            new LogisticsDetourPlanner.Point(0, -1)
        );

        LogisticsDetourPlanner.Plan oneBlock =
            LogisticsDetourPlanner.findSidestep(
                passable,
                START,
                EAST,
                (from, to) -> true,
                2
            ).orElseThrow();
        assertEquals(1, oneBlock.path().size() - 1);

        LogisticsDetourPlanner.Point leftOne =
            new LogisticsDetourPlanner.Point(0, -1);
        LogisticsDetourPlanner.Point leftTwo =
            new LogisticsDetourPlanner.Point(0, -2);
        LogisticsDetourPlanner.Plan blockedSecondEdge =
            LogisticsDetourPlanner.findSidestep(
                Set.of(START, leftOne, leftTwo),
                START,
                EAST,
                (from, to) -> !to.equals(leftTwo),
                2
            ).orElseThrow();
        assertEquals(leftOne, blockedSecondEdge.endpoint());

        assertTrue(
            LogisticsDetourPlanner.findSidestep(
                passable,
                START,
                EAST,
                (from, to) -> false,
                2
            ).isEmpty()
        );
    }

    @Test
    void sidestepUsesTheRelativeLeftForEveryForwardHeading() {
        for (LogisticsDetourPlanner.Direction forward
            : LogisticsDetourPlanner.Direction.values()) {
            LogisticsDetourPlanner.Point leftOne = START.offset(forward.left());
            LogisticsDetourPlanner.Point leftTwo = leftOne.offset(forward.left());
            LogisticsDetourPlanner.Point rightOne = START.offset(forward.right());
            LogisticsDetourPlanner.Point rightTwo = rightOne.offset(forward.right());
            Set<LogisticsDetourPlanner.Point> passable = Set.of(
                START,
                leftOne,
                leftTwo,
                rightOne,
                rightTwo
            );

            LogisticsDetourPlanner.Plan sidestep =
                LogisticsDetourPlanner.findSidestep(
                    passable,
                    START,
                    forward,
                    (from, to) -> true,
                    2
                ).orElseThrow();

            assertEquals(leftOne, sidestep.path().get(1));
            assertEquals(leftTwo, sidestep.endpoint());
        }
    }

    @Test
    void sidestepCannotJumpAnUnsafeIntermediateCell() {
        LogisticsDetourPlanner.Point leftTwo =
            START.offset(EAST.left()).offset(EAST.left());

        assertTrue(
            LogisticsDetourPlanner.findSidestep(
                Set.of(START, leftTwo),
                START,
                EAST,
                (from, to) -> true,
                2
            ).isEmpty()
        );
        assertTrue(
            LogisticsDetourPlanner.findSidestep(
                Set.of(new LogisticsDetourPlanner.Point(0, -1)),
                START,
                EAST,
                (from, to) -> true,
                2
            ).isEmpty()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> LogisticsDetourPlanner.findSidestep(
                Set.of(START),
                START,
                EAST,
                (from, to) -> true,
                0
            )
        );
    }

    private static Optional<LogisticsDetourPlanner.Plan> plan(
        Set<LogisticsDetourPlanner.Point> passable,
        int radius,
        int nodeCap
    ) {
        return LogisticsDetourPlanner.findBypass(
            passable,
            START,
            EAST,
            TERMINAL,
            radius,
            nodeCap
        );
    }

    private static Set<LogisticsDetourPlanner.Point> openSquare(int radius) {
        Set<LogisticsDetourPlanner.Point> passable = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                passable.add(new LogisticsDetourPlanner.Point(x, z));
            }
        }
        return passable;
    }
}
