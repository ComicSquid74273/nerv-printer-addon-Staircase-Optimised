package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatFlyHorizontalStepTest {
    @Test
    void travelModeProducesExactlyFourteenBlocksPerSecond() {
        var velocity = BoatFlyHorizontalStep.toward(0.0, 20.0, 14.0, 0.10);

        assertEquals(0.0, velocity.x(), 1.0e-9);
        assertEquals(0.70, velocity.z(), 1.0e-9);
    }

    @Test
    void buildModeProducesExactlyTenBlocksPerSecond() {
        var velocity = BoatFlyHorizontalStep.toward(3.0, 4.0, 10.0, 0.10);

        assertEquals(0.30, velocity.x(), 1.0e-9);
        assertEquals(0.40, velocity.z(), 1.0e-9);
    }

    @Test
    void finalStepCannotOvershootAndArrivalStopsMotion() {
        var finalStep = BoatFlyHorizontalStep.toward(0.05, 0.20, 14.0, 0.10);
        assertEquals(0.05, finalStep.x(), 1.0e-9);
        assertEquals(0.20, finalStep.z(), 1.0e-9);

        var arrived = BoatFlyHorizontalStep.toward(0.02, 0.02, 14.0, 0.10);
        assertTrue(arrived.arrived());
        assertEquals(0.0, arrived.x(), 1.0e-9);
        assertEquals(0.0, arrived.z(), 1.0e-9);
    }
}
