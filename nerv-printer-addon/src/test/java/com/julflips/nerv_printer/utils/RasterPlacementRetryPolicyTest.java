package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterPlacementRetryPolicyTest {
    @Test
    void mandatoryRasterRetryReanchorsInsteadOfHoldingForever() {
        assertEquals(
            RasterPlacementRetryPolicy.Action.REANCHOR_RASTER_ROUTE,
            RasterPlacementRetryPolicy.decideOutOfReach(true, false, true)
        );
    }

    @Test
    void freshPacketStillGetsItsNormalAcknowledgementWindow() {
        assertEquals(
            RasterPlacementRetryPolicy.Action.WAIT_FOR_ACKNOWLEDGEMENT,
            RasterPlacementRetryPolicy.decideOutOfReach(false, false, true)
        );
    }
}
