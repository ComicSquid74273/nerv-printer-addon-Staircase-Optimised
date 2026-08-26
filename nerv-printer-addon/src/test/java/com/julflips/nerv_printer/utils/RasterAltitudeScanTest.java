package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterAltitudeScanTest {
    @Test
    void alwaysTestsNoDescentBeforeAnyLowerLayer() {
        var candidates = RasterAltitudeScan.candidates(-34.125, -34.96, 1.0, 0.25);
        assertEquals(-34.125, candidates.getFirst());
        assertTrue(candidates.get(1) < candidates.getFirst());
    }

    @Test
    void scansInSmallStepsAndNeverBelowTheBoundByMoreThanOneStep() {
        var candidates = RasterAltitudeScan.candidates(10.0, 8.0, 1.0, 0.25);
        assertEquals(0.25, candidates.get(0) - candidates.get(1));
        assertTrue(candidates.getLast() >= 7.0 - 0.25);
    }
}
