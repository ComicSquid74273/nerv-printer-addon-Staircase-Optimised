package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterWalkRouteTest {
    @Test
    void leavesAnUnreachableEmptyPathEmpty() {
        assertEquals(
            List.of(),
            RasterWalkRoute.waypoints(
                List.of(),
                new RasterWalkRoute.Point(1.25, 0.0, 1.75)
            )
        );
    }

    @Test
    void insetsACornerTargetInsideItsSupportedGoalCell() {
        RasterWalkRoute.Point requested = new RasterWalkRoute.Point(
            -66.02605944157293,
            -35.0,
            -195.98678073373816
        );

        List<RasterWalkRoute.Point> waypoints = RasterWalkRoute.waypoints(
            List.of(new RasterVoxelPathfinder.Cell(-67, -35, -196)),
            requested
        );

        assertEquals(
            List.of(new RasterWalkRoute.Point(-66.31, -35.0, -195.69)),
            waypoints
        );
    }

    @Test
    void finishesMultiCellRouteAtExactRequestedCoordinate() {
        RasterWalkRoute.Point requested = new RasterWalkRoute.Point(2.1, 0.0, 0.9);

        List<RasterWalkRoute.Point> waypoints = RasterWalkRoute.waypoints(
            List.of(
                new RasterVoxelPathfinder.Cell(0, 0, 0),
                new RasterVoxelPathfinder.Cell(1, 0, 0),
                new RasterVoxelPathfinder.Cell(2, 0, 0)
            ),
            requested
        );

        assertEquals(new RasterWalkRoute.Point(1.5, 0.0, 0.5), waypoints.get(0));
        assertEquals(new RasterWalkRoute.Point(2.5, 0.0, 0.5), waypoints.get(1));
        assertEquals(new RasterWalkRoute.Point(2.31, 0.0, 0.69), waypoints.get(2));
    }

    @Test
    void doesNotDuplicateAnAlreadyCenteredExactDestination() {
        RasterWalkRoute.Point requested = new RasterWalkRoute.Point(1.5, 0.0, 0.5);

        List<RasterWalkRoute.Point> waypoints = RasterWalkRoute.waypoints(
            List.of(
                new RasterVoxelPathfinder.Cell(0, 0, 0),
                new RasterVoxelPathfinder.Cell(1, 0, 0)
            ),
            requested
        );

        assertEquals(List.of(requested), waypoints);
    }

    @Test
    void doesNotAppendUnsafeRequestedTargetAfterFallbackGoal() {
        RasterWalkRoute.Point requested = new RasterWalkRoute.Point(2.1, 0.0, 0.9);

        List<RasterWalkRoute.Point> waypoints = RasterWalkRoute.waypoints(
            List.of(
                new RasterVoxelPathfinder.Cell(0, 0, 0),
                new RasterVoxelPathfinder.Cell(1, 0, 1)
            ),
            requested
        );

        assertEquals(
            List.of(new RasterWalkRoute.Point(1.5, 0.0, 1.5)),
            waypoints
        );
    }

    @Test
    void leavesAnAlreadyInsetFractionalTargetUnchanged() {
        RasterWalkRoute.Point requested = new RasterWalkRoute.Point(2.4, 0.0, 0.6);

        List<RasterWalkRoute.Point> waypoints = RasterWalkRoute.waypoints(
            List.of(new RasterVoxelPathfinder.Cell(2, 0, 0)),
            requested
        );

        assertEquals(List.of(requested), waypoints);
    }
}
