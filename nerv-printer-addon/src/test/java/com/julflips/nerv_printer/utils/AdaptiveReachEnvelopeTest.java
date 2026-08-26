package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveReachEnvelopeTest {
    @Test
    void keepsASharedLookaheadEnvelope() {
        AdaptiveReachEnvelope.Solution solution = AdaptiveReachEnvelope.solve(
            0.5, 0.5, 4, 1.6, 5, 0.5,
            List.of(
                new AdaptiveReachEnvelope.Target(0.5, 1.5, 0.5),
                new AdaptiveReachEnvelope.Target(1.5, 2.5, 0.5)
            )
        );
        assertTrue(solution.sharedEnvelope());
        assertFalse(solution.requiresVerticalStop());
        assertTrue(solution.vehicleY() >= solution.minimumVehicleY());
        assertTrue(solution.vehicleY() <= solution.maximumVehicleY());
    }

    @Test
    void requestsAStopWhenLookaheadHasNoSharedAltitude() {
        AdaptiveReachEnvelope.Solution solution = AdaptiveReachEnvelope.solve(
            0.5, 0.5, 0, 1.6, 5, 0.5,
            List.of(
                new AdaptiveReachEnvelope.Target(0.5, 0.5, 0.5),
                new AdaptiveReachEnvelope.Target(0.5, 20.5, 0.5)
            )
        );
        assertFalse(solution.sharedEnvelope());
        assertTrue(solution.requiresVerticalStop());
    }
}
