package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RasterLaunchPointPlanTest {
    @Test
    void neverSelectsThePlayerSupportOrAdjacentCollisionCells() {
        List<RasterLaunchPointPlan.Offset> offsets =
            RasterLaunchPointPlan.candidateOffsets(0, 1);
        assertEquals(8, offsets.size());
        assertTrue(offsets.stream().allMatch(offset -> offset.distanceSquared() >= 9));
        assertTrue(offsets.stream().noneMatch(offset -> offset.dx() == 0 && offset.dz() == 0));
    }

    @Test
    void coversFourDirectionsAtEveryRadiusWithoutDuplicates() {
        List<RasterLaunchPointPlan.Offset> offsets =
            RasterLaunchPointPlan.candidateOffsets(1, 0);
        assertEquals(offsets.size(), new HashSet<>(offsets).size());
        for (int radius = 3; radius <= 4; radius++) {
            int distance = radius * radius;
            assertEquals(
                4,
                offsets.stream().filter(offset -> offset.distanceSquared() == distance).count()
            );
        }
    }

    @Test
    void rejectsNonCardinalForwardDirections() {
        assertThrows(
            IllegalArgumentException.class,
            () -> RasterLaunchPointPlan.candidateOffsets(0, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RasterLaunchPointPlan.candidateOffsets(1, 1)
        );
    }

    @Test
    void boatEscapeLeavesTheSupportNorthOrSouthBeforeDescending() {
        List<RasterLaunchPointPlan.Offset> southFirst =
            RasterLaunchPointPlan.boatEscapeOffsets(1);
        assertEquals(new RasterLaunchPointPlan.Offset(0, 3), southFirst.getFirst());
        assertEquals(6, southFirst.size());
        assertTrue(southFirst.stream().allMatch(offset -> Math.abs(offset.dz()) == 3));
        assertTrue(southFirst.stream().allMatch(offset -> Math.abs(offset.dx()) <= 1));

        List<RasterLaunchPointPlan.Offset> northFirst =
            RasterLaunchPointPlan.boatEscapeOffsets(-1);
        assertEquals(new RasterLaunchPointPlan.Offset(0, -3), northFirst.getFirst());
        assertThrows(
            IllegalArgumentException.class,
            () -> RasterLaunchPointPlan.boatEscapeOffsets(0)
        );
    }
}
