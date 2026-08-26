package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterLocalCheckpointResumePolicyTest {
    @Test
    void nearbyBoatAlreadyUnderMapResumesLocally() {
        assertTrue(RasterLocalCheckpointResumePolicy.canResume(
            -43.57, -36.16, -267.25,
            -44.5, -35.96, -261.5,
            -64.5, 64.5, -320.5, -191.5,
            8.5, 4.0
        ));
    }

    @Test
    void exteriorOrDistantBoatStillRequiresIngress() {
        assertFalse(RasterLocalCheckpointResumePolicy.canResume(
            -65.4, -27.8, -254.3,
            -44.5, -35.96, -261.5,
            -64.5, 64.5, -320.5, -191.5,
            8.5, 4.0
        ));
        assertFalse(RasterLocalCheckpointResumePolicy.canResume(
            -43.5, -36.0, -300.0,
            -44.5, -36.0, -261.5,
            -64.5, 64.5, -320.5, -191.5,
            8.5, 4.0
        ));
    }
}
