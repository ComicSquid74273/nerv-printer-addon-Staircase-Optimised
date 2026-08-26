package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterSupportedTravelPolicyTest {
    @Test
    void groundAtOrBelowBoatFeetIsSupport() {
        assertTrue(RasterSupportedTravelPolicy.isSupportContact(-35.0, -35.0));
        assertTrue(RasterSupportedTravelPolicy.isSupportContact(-36.0, -35.0));
    }

    @Test
    void anythingAboveBoatFeetRemainsAnObstruction() {
        assertFalse(RasterSupportedTravelPolicy.isSupportContact(-34.999, -35.0));
        assertFalse(RasterSupportedTravelPolicy.isSupportContact(-33.0, -35.0));
    }
}
