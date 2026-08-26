package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterSweptEnvelopeTest {
    private static final RasterSweptEnvelope.Bounds ENVELOPE =
        new RasterSweptEnvelope.Bounds(-0.95, -0.15, -0.95,
            0.95, 2.0, 0.95);

    @Test
    void detectsThinObstacleBetweenDiscreteEndpoints() {
        assertTrue(RasterSweptEnvelope.intersects(
            new RasterSweptEnvelope.Point(0.0, 0.0, 0.0),
            new RasterSweptEnvelope.Point(0.25, 0.0, 0.0),
            ENVELOPE,
            new RasterSweptEnvelope.Bounds(1.05, 0.0, -0.2,
                1.10, 1.0, 0.2)
        ));
    }

    @Test
    void preservesARealSeparationGap() {
        assertFalse(RasterSweptEnvelope.intersects(
            new RasterSweptEnvelope.Point(0.0, 0.0, 0.0),
            new RasterSweptEnvelope.Point(0.25, 0.0, 0.0),
            ENVELOPE,
            new RasterSweptEnvelope.Bounds(1.31, 0.0, -0.2,
                1.40, 1.0, 0.2)
        ));
    }

    @Test
    void detectsCollisionAtTheAuthoritativeStartPose() {
        assertTrue(RasterSweptEnvelope.intersects(
            new RasterSweptEnvelope.Point(0.0, 0.0, 0.0),
            new RasterSweptEnvelope.Point(4.0, 0.0, 0.0),
            ENVELOPE,
            new RasterSweptEnvelope.Bounds(-0.10, 0.0, -0.10,
                0.10, 1.0, 0.10)
        ));
    }

    @Test
    void honorsAsymmetricMountedEnvelopeExtents() {
        var asymmetric = new RasterSweptEnvelope.Bounds(
            -0.70, -0.20, -1.10,
            1.30, 2.25, 0.80
        );
        assertTrue(RasterSweptEnvelope.intersects(
            new RasterSweptEnvelope.Point(0.0, 0.0, 0.0),
            new RasterSweptEnvelope.Point(0.25, 0.0, 0.0),
            asymmetric,
            new RasterSweptEnvelope.Bounds(1.35, 0.0, -0.1,
                1.40, 1.0, 0.1)
        ));
        assertFalse(RasterSweptEnvelope.intersects(
            new RasterSweptEnvelope.Point(0.0, 0.0, 0.0),
            new RasterSweptEnvelope.Point(0.25, 0.0, 0.0),
            asymmetric,
            new RasterSweptEnvelope.Bounds(-1.10, 0.0, -0.1,
                -1.05, 1.0, 0.1)
        ));
    }
}
