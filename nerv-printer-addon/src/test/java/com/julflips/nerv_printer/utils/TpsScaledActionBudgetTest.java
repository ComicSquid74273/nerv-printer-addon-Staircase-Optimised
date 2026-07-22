package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpsScaledActionBudgetTest {
    private static final long CLIENT_TICK_NANOS = 50_000_000L;

    @Test
    void thirtyActionsPerSecondScalesAtTwentyFifteenAndTenTps() {
        assertEquals(60, consumeForTicks(newBudget(30.0, 100), 20.0, 40));
        assertEquals(45, consumeForTicks(newBudget(30.0, 100), 15.0, 40));
        assertEquals(30, consumeForTicks(newBudget(30.0, 100), 10.0, 40));
    }

    @Test
    void minimumTpsIsInclusiveAndLowerTpsPauses() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);

        assertEquals(0, budget.beginTick(0L, 10.0, 0.0));
        assertEquals(0, budget.beginTick(CLIENT_TICK_NANOS, 10.0, 0.0));
        assertEquals(
            1,
            budget.beginTick(2 * CLIENT_TICK_NANOS, 10.0, 0.0)
        );
        assertFalse(budget.paused());

        assertEquals(
            0,
            budget.beginTick(3 * CLIENT_TICK_NANOS, 9.999, 0.0)
        );
        assertTrue(budget.paused());
        assertEquals(
            TpsScaledActionBudget.PauseReason.BELOW_MINIMUM_TPS,
            budget.pauseReason()
        );
        assertEquals(0.0, budget.fractionalCarry());
    }

    @Test
    void invalidAndStaleSamplesPauseWithSpecificReasons() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);

        assertEquals(0, budget.beginTick(0L, Double.NaN, 0.0));
        assertEquals(
            TpsScaledActionBudget.PauseReason.INVALID_TPS,
            budget.pauseReason()
        );
        assertEquals(
            0,
            budget.beginTick(CLIENT_TICK_NANOS, 20.0, Double.POSITIVE_INFINITY)
        );
        assertEquals(
            TpsScaledActionBudget.PauseReason.INVALID_SERVER_TICK_AGE,
            budget.pauseReason()
        );
        assertEquals(
            0,
            budget.beginTick(2 * CLIENT_TICK_NANOS, 20.0, -0.01)
        );
        assertEquals(
            TpsScaledActionBudget.PauseReason.INVALID_SERVER_TICK_AGE,
            budget.pauseReason()
        );
        assertEquals(
            0,
            budget.beginTick(3 * CLIENT_TICK_NANOS, 20.0, 1.500_001)
        );
        assertEquals(
            TpsScaledActionBudget.PauseReason.STALE_SERVER_TICK,
            budget.pauseReason()
        );

        // The configured boundary itself is still fresh.
        assertEquals(
            0,
            budget.beginTick(4 * CLIENT_TICK_NANOS, 20.0, 1.5)
        );
        assertFalse(budget.paused());
    }

    @Test
    void fractionalRatesCarryWithoutRoundingTheConfiguredRate() {
        assertEquals(4, consumeForTicks(newBudget(0.8, 10), 20.0, 100));
        assertEquals(3, consumeForTicks(newBudget(1.5, 10), 20.0, 40));
        assertEquals(7, consumeForTicks(newBudget(7.0, 10), 20.0, 20));
    }

    @Test
    void elapsedTimeCanProduceOnlyOneCappedTickBurst() {
        TpsScaledActionBudget budget = newBudget(30.0, 3);

        assertEquals(0, budget.beginTick(0L, 20.0, 0.0));
        assertEquals(3, budget.beginTick(10_000_000_000L, 20.0, 0.0));
        assertTrue(budget.tryConsume(3));
        assertEquals(0, budget.remainingThisTick());

        // The 297 discarded whole credits do not leak into later ticks.
        assertEquals(
            1,
            budget.beginTick(10_000_000_000L + CLIENT_TICK_NANOS, 20.0, 0.0)
        );
        assertEquals(0.5, budget.fractionalCarry());
    }

    @Test
    void pauseAndResumeDiscardElapsedTimeAndOldFractionalCarry() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);

        assertEquals(0, budget.beginTick(0L, 20.0, 0.0));
        assertEquals(0, budget.beginTick(25_000_000L, 20.0, 0.0));
        assertEquals(0.75, budget.fractionalCarry());

        assertEquals(0, budget.beginTick(50_000_000L, 9.0, 0.0));
        assertEquals(0.0, budget.fractionalCarry());

        // A healthy resume only establishes a new baseline, even after 10 s.
        assertEquals(0, budget.beginTick(10_000_000_000L, 20.0, 0.0));
        assertEquals(
            1,
            budget.beginTick(10_000_000_000L + CLIENT_TICK_NANOS, 20.0, 0.0)
        );
    }

    @Test
    void resetCannotAccumulateAResumeBurst() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);

        budget.beginTick(0L, 20.0, 0.0);
        assertEquals(3, budget.beginTick(100_000_000L, 20.0, 0.0));
        budget.reset();

        assertFalse(budget.initialized());
        assertEquals(0, budget.beginTick(100_000_000_000L, 20.0, 0.0));
        assertEquals(
            1,
            budget.beginTick(100_000_000_000L + CLIENT_TICK_NANOS, 20.0, 0.0)
        );
    }

    @Test
    void consumptionIsAtomicAndCannotOverspend() {
        TpsScaledActionBudget budget = newBudget(30.0, 10);

        budget.beginTick(0L, 20.0, 0.0);
        assertEquals(3, budget.beginTick(100_000_000L, 20.0, 0.0));
        assertTrue(budget.tryConsume(2));
        assertEquals(1, budget.remainingThisTick());
        assertFalse(budget.tryConsume(2));
        assertEquals(1, budget.remainingThisTick());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
        assertEquals(3, budget.consumedThisTick());
        assertThrows(IllegalArgumentException.class, () -> budget.tryConsume(-1));
    }

    @Test
    void unusedWholeCreditsDoNotCarryIntoAnotherTick() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);

        budget.beginTick(0L, 20.0, 0.0);
        assertEquals(3, budget.beginTick(100_000_000L, 20.0, 0.0));
        assertEquals(
            1,
            budget.beginTick(
                100_000_000L + CLIENT_TICK_NANOS,
                20.0,
                0.0
            )
        );
    }

    @Test
    void monotonicTimeCannotMoveBackwards() {
        TpsScaledActionBudget budget = newBudget(30.0, 100);
        budget.beginTick(100L, 20.0, 0.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> budget.beginTick(99L, 20.0, 0.0)
        );
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TpsScaledActionBudget(0.0, 10.0, 1.5, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new TpsScaledActionBudget(30.0, 0.0, 1.5, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new TpsScaledActionBudget(30.0, 20.1, 1.5, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new TpsScaledActionBudget(30.0, 10.0, -1.0, 3)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new TpsScaledActionBudget(30.0, 10.0, 1.5, 0)
        );
    }

    private static TpsScaledActionBudget newBudget(
        double actionsPerSecond,
        int burstCap
    ) {
        return new TpsScaledActionBudget(
            actionsPerSecond,
            10.0,
            1.5,
            burstCap
        );
    }

    private static int consumeForTicks(
        TpsScaledActionBudget budget,
        double tps,
        int tickCount
    ) {
        long now = 0L;
        int consumed = budget.beginTick(now, tps, 0.0);
        for (int tick = 0; tick < tickCount; tick++) {
            now += CLIENT_TICK_NANOS;
            int available = budget.beginTick(now, tps, 0.0);
            assertTrue(budget.tryConsume(available));
            consumed += available;
        }
        return consumed;
    }
}
