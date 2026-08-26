package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterFlightProgressWatchdogTest {
    @Test
    void stalledDirectLegTriggersBoundedRecovery() {
        RasterFlightProgressWatchdog<String> watchdog =
            new RasterFlightProgressWatchdog<>(3, 0.10);

        assertFalse(watchdog.observe("south-leg", 7.2));
        assertFalse(watchdog.observe("south-leg", 7.2));
        assertFalse(watchdog.observe("south-leg", 7.2));
        assertTrue(watchdog.observe("south-leg", 7.2));
    }

    @Test
    void progressOrANewWaypointStartsAFreshWindow() {
        RasterFlightProgressWatchdog<String> watchdog =
            new RasterFlightProgressWatchdog<>(2, 0.10);

        assertFalse(watchdog.observe("a", 7.2));
        assertFalse(watchdog.observe("a", 7.2));
        assertFalse(watchdog.observe("a", 7.0));
        assertFalse(watchdog.observe("b", 3.0));
        assertFalse(watchdog.observe("b", 3.0));
        assertTrue(watchdog.observe("b", 3.0));
    }
}
