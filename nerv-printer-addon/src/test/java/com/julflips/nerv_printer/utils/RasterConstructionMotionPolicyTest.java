package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterConstructionMotionPolicyTest {
    @Test
    void constructionRouteStaysAtBuildSpeedBetweenPlacements() {
        assertEquals(
            BoatFlyAdapter.DriveMode.BUILD,
            RasterConstructionMotionPolicy.driveMode(false)
        );
    }

    @Test
    void constructionRouteStaysAtBuildSpeedDuringPlacement() {
        assertEquals(
            BoatFlyAdapter.DriveMode.BUILD,
            RasterConstructionMotionPolicy.driveMode(true)
        );
    }
}
