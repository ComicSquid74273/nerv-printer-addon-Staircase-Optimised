package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterVoxelPathfinderTest {
    @Test
    void fliesAroundAWallInsteadOfCrossingIt() {
        Set<RasterVoxelPathfinder.Cell> wall = Set.of(
            new RasterVoxelPathfinder.Cell(1, 0, -1),
            new RasterVoxelPathfinder.Cell(1, 0, 0),
            new RasterVoxelPathfinder.Cell(1, 0, 1)
        );
        var path = RasterVoxelPathfinder.find(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            new RasterVoxelPathfinder.Cell(2, 0, 0),
            RasterVoxelPathfinder.Mode.FLY,
            2,
            0,
            200,
            cell -> !wall.contains(cell)
        );
        assertFalse(path.isEmpty());
        assertTrue(path.stream().noneMatch(wall::contains));
        assertTrue(path.stream().anyMatch(cell -> Math.abs(cell.z()) == 2));
    }

    @Test
    void usesFortyFiveDegreeStepsInOpenSpace() {
        var path = RasterVoxelPathfinder.find(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            new RasterVoxelPathfinder.Cell(3, 0, 3),
            RasterVoxelPathfinder.Mode.FLY,
            0,
            0,
            100,
            ignored -> true
        );
        assertEquals(4, path.size());
        assertEquals(new RasterVoxelPathfinder.Cell(1, 0, 1), path.get(1));
    }

    @Test
    void walkingNeverUsesPureVerticalFlight() {
        var path = RasterVoxelPathfinder.find(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            new RasterVoxelPathfinder.Cell(2, 1, 0),
            RasterVoxelPathfinder.Mode.WALK,
            1,
            1,
            100,
            ignored -> true
        );
        assertFalse(path.isEmpty());
        for (int index = 1; index < path.size(); index++) {
            var before = path.get(index - 1);
            var after = path.get(index);
            assertTrue(before.x() != after.x() || before.z() != after.z());
        }
    }

    @Test
    void walkingCanUseASupportedCardinalOneBlockStep() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);
        var raised = new RasterVoxelPathfinder.Cell(1, 1, 0);
        var path = RasterVoxelPathfinder.find(
            start,
            raised,
            RasterVoxelPathfinder.Mode.WALK,
            0,
            1,
            20,
            cell -> cell.equals(start) || cell.equals(raised)
        );

        assertEquals(List.of(start, raised), path);
    }

    @Test
    void walkingRejectsAnUnsupportedDiagonalVerticalShortcut() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);
        var diagonal = new RasterVoxelPathfinder.Cell(1, 1, 1);
        var path = RasterVoxelPathfinder.find(
            start,
            diagonal,
            RasterVoxelPathfinder.Mode.WALK,
            0,
            1,
            20,
            cell -> cell.equals(start) || cell.equals(diagonal)
        );

        assertTrue(path.isEmpty());
    }

    @Test
    void walkingDoesNotCrossAnUnsupportedGap() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);
        var farPlatform = new RasterVoxelPathfinder.Cell(2, 0, 0);
        var path = RasterVoxelPathfinder.find(
            start,
            farPlatform,
            RasterVoxelPathfinder.Mode.WALK,
            0,
            0,
            20,
            cell -> cell.equals(start) || cell.equals(farPlatform)
        );

        assertTrue(path.isEmpty());
    }

    @Test
    void boundedReachabilityStaysOnItsSupportedPlatformComponent() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);
        var platform = Set.of(
            start,
            new RasterVoxelPathfinder.Cell(1, 0, 0),
            new RasterVoxelPathfinder.Cell(2, 0, 0),
            new RasterVoxelPathfinder.Cell(4, 0, 0)
        );

        Set<RasterVoxelPathfinder.Cell> reachable =
            RasterVoxelPathfinder.reachable(
                start,
                RasterVoxelPathfinder.Mode.WALK,
                6,
                1,
                100,
                platform::contains
            );

        assertTrue(reachable.contains(new RasterVoxelPathfinder.Cell(2, 0, 0)));
        assertFalse(reachable.contains(new RasterVoxelPathfinder.Cell(4, 0, 0)));
    }

    @Test
    void boundedReachabilityRejectsAnUnsafeStartCell() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);

        Set<RasterVoxelPathfinder.Cell> reachable =
            RasterVoxelPathfinder.reachable(
                start,
                RasterVoxelPathfinder.Mode.WALK,
                4,
                1,
                100,
                ignored -> false
            );

        assertTrue(reachable.isEmpty());
    }

    @Test
    void transitionRevalidationRejectsAChangedDiagonalCorner() {
        var start = new RasterVoxelPathfinder.Cell(0, 0, 0);
        var east = new RasterVoxelPathfinder.Cell(1, 0, 0);
        var south = new RasterVoxelPathfinder.Cell(0, 0, 1);
        var diagonal = new RasterVoxelPathfinder.Cell(1, 0, 1);
        Set<RasterVoxelPathfinder.Cell> supported = Set.of(
            start,
            east,
            diagonal
        );

        assertFalse(RasterVoxelPathfinder.canTraverse(
            start,
            diagonal,
            RasterVoxelPathfinder.Mode.WALK,
            supported::contains
        ));
        assertFalse(supported.contains(south));
    }

    @Test
    void incrementalSearchCompletesAcrossBoundedSlices() {
        var search = RasterVoxelPathfinder.begin(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            new RasterVoxelPathfinder.Cell(20, 4, 20),
            RasterVoxelPathfinder.Mode.FLY,
            2,
            2,
            10000,
            ignored -> true
        );
        assertEquals(
            RasterVoxelPathfinder.SearchStatus.SEARCHING,
            search.advance(1)
        );
        while (search.status() == RasterVoxelPathfinder.SearchStatus.SEARCHING) {
            search.advance(8);
        }
        assertEquals(RasterVoxelPathfinder.SearchStatus.FOUND, search.status());
        assertEquals(new RasterVoxelPathfinder.Cell(20, 4, 20), search.path().getLast());
    }

    @Test
    void impossibleGoalFailsBeforeScanningTheWholeSearchVolume() {
        var goal = new RasterVoxelPathfinder.Cell(20, 4, 20);
        var search = RasterVoxelPathfinder.begin(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            goal,
            RasterVoxelPathfinder.Mode.FLY,
            48,
            24,
            200000,
            cell -> !cell.equals(goal)
        );

        assertEquals(RasterVoxelPathfinder.SearchStatus.FAILED, search.status());
        assertEquals(0, search.visitedCount());
        assertTrue(search.path().isEmpty());
    }

    @Test
    void compressionRetainsTurnsAndCapsLongStraightSegments() {
        var path = List.of(
            new RasterVoxelPathfinder.Cell(0, 0, 0),
            new RasterVoxelPathfinder.Cell(1, 0, 0),
            new RasterVoxelPathfinder.Cell(2, 0, 0),
            new RasterVoxelPathfinder.Cell(3, 0, 0),
            new RasterVoxelPathfinder.Cell(4, 0, 0),
            new RasterVoxelPathfinder.Cell(4, -1, 1),
            new RasterVoxelPathfinder.Cell(4, -2, 2)
        );
        var compressed = RasterVoxelPathfinder.compressStraightSegments(path, 3);
        assertEquals(path.getFirst(), compressed.getFirst());
        assertTrue(compressed.contains(new RasterVoxelPathfinder.Cell(3, 0, 0)));
        assertEquals(path.getLast(), compressed.getLast());
        assertTrue(compressed.size() < path.size());
    }
}
