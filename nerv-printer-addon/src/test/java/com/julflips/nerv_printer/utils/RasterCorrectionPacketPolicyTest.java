package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterCorrectionPacketPolicyTest {
    @Test
    void ordinaryVehicleSynchronizationDoesNotBrakeOrRejoin() {
        assertFalse(
            RasterCorrectionPacketPolicy.requiresRouteRejoin(false, true)
        );
    }

    @Test
    void playerPositionCorrectionStillInvalidatesTheRoute() {
        assertTrue(
            RasterCorrectionPacketPolicy.requiresRouteRejoin(true, false)
        );
    }
}
