package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsProgressWatchdogTest {
    @Test
    void triggersAfterThirtyEligibleGroundedNoProgressTicks() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(30, 0.15, 5);

        assertFalse(watchdog.observe("dump", 12.0, true, true));
        for (int tick = 1; tick < 30; tick++) {
            assertFalse(watchdog.observe("dump", 12.0, true, true));
        }
        assertTrue(watchdog.observe("dump", 12.0, true, true));
        assertEquals(5, watchdog.cooldownTicksRemaining());
    }

    @Test
    void ignoresIneligibleAndAirborneTicks() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(2, 0.15, 0);

        assertFalse(watchdog.observe("chest", 8.0, true, true));
        assertFalse(watchdog.observe("chest", 8.0, false, true));
        assertFalse(watchdog.observe("chest", 8.0, true, false));
        assertEquals(0, watchdog.noProgressTicks());

        assertFalse(watchdog.observe("chest", 8.0, true, true));
        assertTrue(watchdog.observe("chest", 8.0, true, true));
    }

    @Test
    void cumulativeMeaningfulProgressResetsTheCounter() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(4, 0.15, 0);

        assertFalse(watchdog.observe("table", 10.0, true, true));
        assertFalse(watchdog.observe("table", 10.0, true, true));
        assertFalse(watchdog.observe("table", 9.92, true, true));
        assertEquals(9.92, watchdog.bestHorizontalDistance());
        assertEquals(2, watchdog.noProgressTicks());

        assertFalse(watchdog.observe("table", 9.84, true, true));
        assertEquals(9.84, watchdog.bestHorizontalDistance());
        assertEquals(0, watchdog.noProgressTicks());

        for (int tick = 1; tick < 4; tick++) {
            assertFalse(watchdog.observe("table", 9.84, true, true));
        }
        assertTrue(watchdog.observe("table", 9.84, true, true));
    }

    @Test
    void changingTerminalIdentityStartsACompleteFreshWindow() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(2, 0.15, 10);

        assertFalse(watchdog.observe("chest-a", 6.0, true, true));
        assertFalse(watchdog.observe("chest-a", 6.0, true, true));
        assertTrue(watchdog.observe("chest-a", 6.0, true, true));

        assertFalse(watchdog.observe("chest-b", 3.0, true, true));
        assertEquals(0, watchdog.cooldownTicksRemaining());
        assertEquals(3.0, watchdog.bestHorizontalDistance());
        assertFalse(watchdog.observe("chest-b", 3.0, true, true));
        assertTrue(watchdog.observe("chest-b", 3.0, true, true));
    }

    @Test
    void cooldownProducesOneTriggerPulseAndThenRearms() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(2, 0.15, 3);

        assertFalse(watchdog.observe("dump", 4.0, true, true));
        assertFalse(watchdog.observe("dump", 4.0, true, true));
        assertTrue(watchdog.observe("dump", 4.0, true, true));

        assertFalse(watchdog.observe("dump", 4.0, false, false));
        assertFalse(watchdog.observe("dump", 4.0, false, false));
        assertFalse(watchdog.observe("dump", 4.0, false, false));
        assertEquals(0, watchdog.cooldownTicksRemaining());

        assertFalse(watchdog.observe("dump", 4.0, true, true));
        assertTrue(watchdog.observe("dump", 4.0, true, true));
    }

    @Test
    void explicitResetDropsAllPriorProgressAndCooldown() {
        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>(2, 0.15, 5);

        assertFalse(watchdog.observe("bed", 5.0, true, true));
        assertFalse(watchdog.observe("bed", 5.0, true, true));
        watchdog.startCooldown();
        watchdog.reset();

        assertFalse(watchdog.isTracking());
        assertEquals(0, watchdog.cooldownTicksRemaining());
        assertFalse(watchdog.observe("bed", 2.0, true, true));
        assertFalse(watchdog.observe("bed", 2.0, true, true));
        assertTrue(watchdog.observe("bed", 2.0, true, true));
    }

    @Test
    void rejectsInvalidConfigurationAndObservations() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new LogisticsProgressWatchdog<>(0, 0.15, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new LogisticsProgressWatchdog<>(30, Double.NaN, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new LogisticsProgressWatchdog<>(30, 0.15, -1)
        );

        LogisticsProgressWatchdog<String> watchdog =
            new LogisticsProgressWatchdog<>();
        assertThrows(
            NullPointerException.class,
            () -> watchdog.observe(null, 1.0, true, true)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> watchdog.observe("dump", -1.0, true, true)
        );
    }
}
