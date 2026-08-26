package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterLateralPlacementPolicyTest {
    @Test
    void acceptsOnlyTheLateralLongitudinalSlice() {
        assertTrue(RasterLateralPlacementPolicy.isBeside(-320.5, -320.5));
        assertTrue(RasterLateralPlacementPolicy.isBeside(-320.0, -320.5));
        assertTrue(RasterLateralPlacementPolicy.isBeside(-321.0, -320.5));
    }

    @Test
    void rejectsTargetsInFrontOrBehind() {
        assertFalse(RasterLateralPlacementPolicy.isBeside(-316.0, -320.5));
        assertFalse(RasterLateralPlacementPolicy.isBeside(-325.0, -320.5));
    }
}
