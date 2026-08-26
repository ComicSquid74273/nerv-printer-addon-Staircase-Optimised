package com.julflips.nerv_printer.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tick-driven action budget whose configured rate is expressed in actions per
 * real second, rather than actions per client tick.
 *
 * <p>The budget earns credits from monotonic elapsed nanoseconds and scales the
 * configured rate proportionally from the sampled server TPS to 20 TPS. Only a
 * fractional credit carries between ticks. Unused whole credits and credits
 * above the per-tick burst cap are discarded, so an idle or delayed caller
 * cannot accumulate a later burst.</p>
 *
 * <p>This class is deliberately independent of Minecraft and wall-clock time.
 * A runtime owner should call {@link #reset()} when starting a new run, call
 * {@link #beginTick(long, double, double)} once per client tick, and consume
 * admitted actions through {@link #tryConsume()} or
 * {@link #tryConsume(int)}.</p>
 */
public final class TpsScaledActionBudget {
    public static final double NORMAL_SERVER_TPS = 20.0;
    private static final double SERVER_TPS_SAMPLE_INTERVAL_TICKS = 20.0;
    private static final double STALE_SAMPLE_GRACE_INTERVALS = 1.5;

    public enum PauseReason {
        NONE,
        INVALID_TPS,
        BELOW_MINIMUM_TPS,
        INVALID_SERVER_TICK_AGE,
        STALE_SERVER_TICK
    }

    private static final BigDecimal NORMAL_SERVER_TPS_DECIMAL =
        BigDecimal.valueOf(NORMAL_SERVER_TPS);
    private static final BigDecimal NANOS_PER_SECOND =
        BigDecimal.valueOf(1_000_000_000L);

    private final BigDecimal maximumActionsPerSecond;
    private final double minimumTps;
    private final double staleAfterSeconds;
    private final int perTickBurstCap;

    private boolean initialized;
    private boolean paused;
    private long lastTickNanos;
    private BigDecimal fractionalCarry = BigDecimal.ZERO;
    private int grantedThisTick;
    private int consumedThisTick;
    private int remainingThisTick;
    private PauseReason pauseReason = PauseReason.NONE;

    public TpsScaledActionBudget(
        double maximumActionsPerSecond,
        double minimumTps,
        double staleAfterSeconds,
        int perTickBurstCap
    ) {
        if (!Double.isFinite(maximumActionsPerSecond)
            || maximumActionsPerSecond <= 0.0) {
            throw new IllegalArgumentException(
                "Maximum actions per second must be finite and positive."
            );
        }
        if (!Double.isFinite(minimumTps)
            || minimumTps <= 0.0
            || minimumTps > NORMAL_SERVER_TPS) {
            throw new IllegalArgumentException(
                "Minimum TPS must be finite and inside (0, 20]."
            );
        }
        if (!Double.isFinite(staleAfterSeconds) || staleAfterSeconds < 0.0) {
            throw new IllegalArgumentException(
                "The stale-server-tick threshold must be finite and non-negative."
            );
        }
        if (perTickBurstCap <= 0) {
            throw new IllegalArgumentException(
                "The per-tick burst cap must be positive."
            );
        }

        this.maximumActionsPerSecond =
            BigDecimal.valueOf(maximumActionsPerSecond);
        this.minimumTps = minimumTps;
        // Meteor refreshes TickRate from the periodic server time packet,
        // normally once per 20 server ticks. Size the stale window for the
        // slowest TPS this budget accepts so a healthy low-TPS server does not
        // alternate between paused and resumed states between normal samples.
        this.staleAfterSeconds = Math.max(
            staleAfterSeconds,
            SERVER_TPS_SAMPLE_INTERVAL_TICKS
                * STALE_SAMPLE_GRACE_INTERVALS
                / minimumTps
        );
        this.perTickBurstCap = perTickBurstCap;
    }

    /**
     * Starts a new client-tick budget window.
     *
     * @param nowNanos monotonic time, normally {@link System#nanoTime()}
     * @param sampledTps latest sampled server TPS
     * @param secondsSinceServerTick age of the latest observed server tick
     * @return the number of actions available for this tick
     */
    public int beginTick(
        long nowNanos,
        double sampledTps,
        double secondsSinceServerTick
    ) {
        long elapsedNanos = 0L;
        if (initialized) {
            if (nowNanos < lastTickNanos) {
                throw new IllegalArgumentException(
                    "Monotonic time cannot move backwards."
                );
            }
            try {
                elapsedNanos = Math.subtractExact(nowNanos, lastTickNanos);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                    "Monotonic elapsed time is too large.",
                    exception
                );
            }
        }

        PauseReason nextPauseReason =
            classifyPause(sampledTps, secondsSinceServerTick);
        if (nextPauseReason != PauseReason.NONE) {
            initialized = true;
            lastTickNanos = nowNanos;
            paused = true;
            pauseReason = nextPauseReason;
            clearCredits();
            return 0;
        }

        // The first healthy sample after reset or a pause only establishes a
        // fresh time baseline. Time spent stopped must never become credits.
        if (!initialized || paused) {
            initialized = true;
            lastTickNanos = nowNanos;
            paused = false;
            pauseReason = PauseReason.NONE;
            clearCredits();
            return 0;
        }

        lastTickNanos = nowNanos;
        paused = false;
        pauseReason = PauseReason.NONE;
        clearWholeTickBudget();

        if (elapsedNanos == 0L) return 0;

        BigDecimal clampedTps = BigDecimal.valueOf(
            Math.min(sampledTps, NORMAL_SERVER_TPS)
        );
        BigDecimal earned = BigDecimal.valueOf(elapsedNanos)
            .multiply(maximumActionsPerSecond)
            .multiply(clampedTps)
            .divide(NANOS_PER_SECOND.multiply(NORMAL_SERVER_TPS_DECIMAL))
            .add(fractionalCarry);

        BigDecimal wholeCredits = earned.setScale(0, RoundingMode.FLOOR);
        fractionalCarry = earned.subtract(wholeCredits);

        if (wholeCredits.compareTo(BigDecimal.valueOf(perTickBurstCap)) >= 0) {
            grantedThisTick = perTickBurstCap;
        } else {
            grantedThisTick = wholeCredits.intValueExact();
        }
        remainingThisTick = grantedThisTick;
        return grantedThisTick;
    }

    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * Atomically consumes the requested number of this tick's credits.
     *
     * @return {@code true} when all requested credits were consumed;
     * otherwise {@code false} and the budget is unchanged
     */
    public boolean tryConsume(int actions) {
        if (actions < 0) {
            throw new IllegalArgumentException(
                "Consumed action count cannot be negative."
            );
        }
        if (actions > remainingThisTick) return false;

        remainingThisTick -= actions;
        consumedThisTick += actions;
        return true;
    }

    /**
     * Clears elapsed time, fractional carry, pause state, and the current
     * tick's credits. The next healthy tick only establishes a time baseline.
     */
    public void reset() {
        initialized = false;
        paused = false;
        lastTickNanos = 0L;
        pauseReason = PauseReason.NONE;
        clearCredits();
    }

    public double maximumActionsPerSecond() {
        return maximumActionsPerSecond.doubleValue();
    }

    public double minimumTps() {
        return minimumTps;
    }

    public double staleAfterSeconds() {
        return staleAfterSeconds;
    }

    public int perTickBurstCap() {
        return perTickBurstCap;
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean paused() {
        return paused;
    }

    public PauseReason pauseReason() {
        return pauseReason;
    }

    public int grantedThisTick() {
        return grantedThisTick;
    }

    public int consumedThisTick() {
        return consumedThisTick;
    }

    public int remainingThisTick() {
        return remainingThisTick;
    }

    public double fractionalCarry() {
        return fractionalCarry.doubleValue();
    }

    private PauseReason classifyPause(
        double sampledTps,
        double secondsSinceServerTick
    ) {
        if (!Double.isFinite(sampledTps) || sampledTps <= 0.0) {
            return PauseReason.INVALID_TPS;
        }
        if (sampledTps < minimumTps) {
            return PauseReason.BELOW_MINIMUM_TPS;
        }
        if (!Double.isFinite(secondsSinceServerTick)
            || secondsSinceServerTick < 0.0) {
            return PauseReason.INVALID_SERVER_TICK_AGE;
        }
        if (secondsSinceServerTick > staleAfterSeconds) {
            return PauseReason.STALE_SERVER_TICK;
        }
        return PauseReason.NONE;
    }

    private void clearCredits() {
        fractionalCarry = BigDecimal.ZERO;
        clearWholeTickBudget();
    }

    private void clearWholeTickBudget() {
        grantedThisTick = 0;
        consumedThisTick = 0;
        remainingThisTick = 0;
    }
}
