package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterThreeLanePathPlannerTest {
    @Test
    void singleLaneUsesItsOwnHeightAndExactlyOnePass() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(
                sliceAtX(0, 1, 0, 10),
                sliceAtX(0, 1, 1, 97),
                sliceAtX(0, 1, 2, 1)
            ),
            6.0
        );

        assertEquals(1, plan.passCount());
        assertEquals(List.of(10, 97, 1), plan.assignments().stream()
            .map(RasterThreeLanePathPlanner.Assignment::pathSurfaceY)
            .toList());
        assertTrue(plan.assignments().stream()
            .allMatch(step -> step.pathZ() == 0));
    }

    @Test
    void reachableThreeHeightSliceUsesOneLowEnvelopePass() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(slice(0, 1, 10, 11, 12)),
            5.0
        );

        assertEquals(1, plan.passCount());
        assertEquals(3, plan.assignments().size());
        assertTrue(plan.assignments().stream().allMatch(step -> step.pass() == 0));
        assertTrue(plan.assignments().stream().allMatch(step -> step.pathSurfaceY() == 10));
    }

    @Test
    void cliffUsesLowOutboundAndHighReturnWithoutGroupingImpossibleHeights() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(slice(0, 1, 1, 97, 97)),
            5.0
        );

        assertEquals(2, plan.passCount());
        assertEquals(3, plan.assignments().size());
        assertEquals(1, plan.assignments().getFirst().pathSurfaceY());
        assertTrue(plan.assignments().stream()
            .filter(step -> step.pass() == 1)
            .allMatch(step -> step.pathSurfaceY() == 97 && step.direction() == -1));
    }

    @Test
    void returnPassTraversesEverySliceInReverseWhenOnlyOneSliceNeedsIt() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(
                sliceAtX(0, 1, 0, 10, 10, 10),
                sliceAtX(0, 1, 1, 1, 97, 97),
                sliceAtX(0, 1, 2, 20, 20, 20)
            ),
            5.0
        );

        var returnSteps = plan.assignments().stream()
            .filter(step -> step.pass() == 1)
            .toList();
        assertEquals(List.of(2, 1, 1, 0), returnSteps.stream()
            .map(step -> step.target().x()).toList());
        assertTrue(returnSteps.stream().allMatch(step -> step.direction() == -1));
    }

    @Test
    void extremeLowMiddleHighSplitGetsBoundedThirdCleanupPass() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(slice(0, 1, 1, 50, 97)),
            5.0
        );

        assertEquals(3, plan.passCount());
        assertEquals(List.of(0, 1, 2), plan.assignments().stream()
            .map(RasterThreeLanePathPlanner.Assignment::pass).toList());
        assertEquals(50, plan.assignments().getLast().pathSurfaceY());
    }

    @Test
    void cleanupPassRetainsEmptySlicesAsAContinuousPhysicalRoute() {
        var plan = RasterThreeLanePathPlanner.create(
            List.of(
                sliceAtX(0, 1, 0, 10, 10, 10),
                sliceAtX(0, 1, 1, 1, 50, 97),
                sliceAtX(0, 1, 2, 20, 20, 20)
            ),
            5.0
        );

        var cleanup = plan.passPoints().stream()
            .filter(point -> point.pass() == 2)
            .toList();
        assertEquals(3, cleanup.size());
        assertEquals(List.of(0, 1, 2), cleanup.stream()
            .map(RasterThreeLanePathPlanner.PassPoint::pathX).toList());
        assertTrue(cleanup.getFirst().targets().isEmpty());
        assertEquals(1, cleanup.get(1).targets().size());
        assertTrue(cleanup.getLast().targets().isEmpty());
    }

    private static RasterThreeLanePathPlanner.Slice<String> slice(
        int band,
        int direction,
        int... heights
    ) {
        return sliceAtX(band, direction, 0, heights);
    }

    private static RasterThreeLanePathPlanner.Slice<String> sliceAtX(
        int band,
        int direction,
        int x,
        int... heights
    ) {
        java.util.ArrayList<RasterThreeLanePathPlanner.Target<String>> targets =
            new java.util.ArrayList<>();
        for (int lane = 0; lane < heights.length; lane++) {
            targets.add(new RasterThreeLanePathPlanner.Target<>(
                x,
                heights[lane],
                lane,
                x + ":" + lane
            ));
        }
        return new RasterThreeLanePathPlanner.Slice<>(band, direction, targets);
    }
}
