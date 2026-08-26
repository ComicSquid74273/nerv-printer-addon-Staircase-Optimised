package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoatFlyAdapterTest {
    @Test
    void travelUsesConfiguredCruiseSpeedOnBothAxes() {
        assertEquals(20.0, BoatFlyAdapter.DriveMode.TRAVEL.blocksPerSecond());
        assertEquals(20.0, BoatFlyAdapter.DriveMode.TRAVEL.verticalBlocksPerSecond());
    }

    @Test
    void buildingUsesStrictFifteenBlocksPerSecondOnBothAxes() {
        assertEquals(15.0, BoatFlyAdapter.DriveMode.BUILD.blocksPerSecond());
        assertEquals(15.0, BoatFlyAdapter.DriveMode.BUILD.verticalBlocksPerSecond());
    }

    @Test
    void configuredSpeedsAreBoundedIndependently() {
        BoatFlyAdapter adapter = new BoatFlyAdapter();
        adapter.setSpeeds(25.0, 0.5);
        assertEquals(20.0, adapter.travelBlocksPerSecond());
        assertEquals(1.0, adapter.buildBlocksPerSecond());

        adapter.setSpeeds(20.0, 4.5);
        assertEquals(20.0, adapter.travelBlocksPerSecond());
        assertEquals(4.5, adapter.buildBlocksPerSecond());

        adapter.setSpeeds(20.0, 25.0);
        assertEquals(15.0, adapter.buildBlocksPerSecond());
    }

    @Test
    void entityControlCannotOvershootClampedFinalAxisStep() {
        assertEquals(
            8.75,
            BoatFlyAdapter.entityControlSpeedForStep(14.0, -0.4375),
            1.0e-9
        );
        assertEquals(
            20.0,
            BoatFlyAdapter.entityControlSpeedForStep(20.0, 1.00),
            1.0e-9
        );
    }
}
