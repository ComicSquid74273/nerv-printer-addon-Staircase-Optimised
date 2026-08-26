package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterMountedEnvelopePolicyTest {
    @Test
    void ignoresObservedRiderBobbing() {
        assertTrue(sameWithVerticalShift(0.0313));
    }

    @Test
    void detectsMeaningfulVerticalShapeChange() {
        assertFalse(sameWithVerticalShift(0.10));
    }

    @Test
    void retainsStrictHorizontalEnvelopeTolerance() {
        assertFalse(RasterMountedEnvelopePolicy.sameExtents(
            -0.95, -0.59, -0.95, 0.95, 1.51, 0.95,
            -0.92, -0.59, -0.95, 0.95, 1.51, 0.95
        ));
    }

    private static boolean sameWithVerticalShift(double shift) {
        return RasterMountedEnvelopePolicy.sameExtents(
            -0.95, -0.5938, -0.95, 0.95, 1.5062, 0.95,
            -0.95, -0.5938 + shift, -0.95,
            0.95, 1.5062 + shift, 0.95
        );
    }
}
