package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Pure progress tracking for movement toward an off-map logistics terminal.
 *
 * <p>The watchdog automatically starts a fresh observation window when the
 * terminal identity changes. A stall is reported as a one-tick pulse after
 * enough eligible, grounded movement ticks fail to make meaningful horizontal
 * progress.</p>
 */
public final class LogisticsProgressWatchdog<T> {
    public static final int DEFAULT_NO_PROGRESS_TICKS = 30;
    public static final double DEFAULT_MINIMUM_PROGRESS = 0.15;
    public static final int DEFAULT_COOLDOWN_TICKS = 30;

    private final int requiredNoProgressTicks;
    private final double minimumProgress;
    private final int configuredCooldownTicks;

    private T terminalIdentity;
    private boolean tracking;
    private double bestHorizontalDistance;
    private double progressReferenceDistance;
    private int noProgressTicks;
    private int cooldownTicksRemaining;

    public LogisticsProgressWatchdog() {
        this(
            DEFAULT_NO_PROGRESS_TICKS,
            DEFAULT_MINIMUM_PROGRESS,
            DEFAULT_COOLDOWN_TICKS
        );
    }

    public LogisticsProgressWatchdog(
        int requiredNoProgressTicks,
        double minimumProgress,
        int cooldownTicks
    ) {
        if (requiredNoProgressTicks <= 0) {
            throw new IllegalArgumentException(
                "Required no-progress ticks must be positive."
            );
        }
        if (!Double.isFinite(minimumProgress) || minimumProgress <= 0) {
            throw new IllegalArgumentException(
                "Minimum progress must be finite and positive."
            );
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("Cooldown ticks cannot be negative.");
        }
        this.requiredNoProgressTicks = requiredNoProgressTicks;
        this.minimumProgress = minimumProgress;
        this.configuredCooldownTicks = cooldownTicks;
        reset();
    }

    /**
     * Observes one client tick.
     *
     * @param terminalIdentity stable, immutable identity of the terminal
     * checkpoint being approached
     * @return {@code true} only on the tick that reaches the configured
     * no-progress limit
     */
    public boolean observe(
        T terminalIdentity,
        double horizontalDistance,
        boolean eligibleMovement,
        boolean grounded
    ) {
        Objects.requireNonNull(terminalIdentity, "terminalIdentity");
        validateDistance(horizontalDistance);

        if (!tracking || !Objects.equals(this.terminalIdentity, terminalIdentity)) {
            beginTracking(terminalIdentity, horizontalDistance);
            return false;
        }

        bestHorizontalDistance = Math.min(
            bestHorizontalDistance,
            horizontalDistance
        );
        boolean madeMeaningfulProgress =
            progressReferenceDistance - bestHorizontalDistance >= minimumProgress;
        if (madeMeaningfulProgress) {
            progressReferenceDistance = bestHorizontalDistance;
            noProgressTicks = 0;
        }

        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
            return false;
        }
        if (!eligibleMovement || !grounded || madeMeaningfulProgress) {
            return false;
        }

        noProgressTicks++;
        if (noProgressTicks < requiredNoProgressTicks) return false;

        startCooldown();
        return true;
    }

    /**
     * Clears the active terminal, observations, and cooldown.
     */
    public void reset() {
        terminalIdentity = null;
        tracking = false;
        bestHorizontalDistance = Double.POSITIVE_INFINITY;
        progressReferenceDistance = Double.POSITIVE_INFINITY;
        noProgressTicks = 0;
        cooldownTicksRemaining = 0;
    }

    /**
     * Rearms the current observation window after the configured cooldown.
     */
    public void startCooldown() {
        noProgressTicks = 0;
        cooldownTicksRemaining = configuredCooldownTicks;
    }

    public boolean isTracking() {
        return tracking;
    }

    public double bestHorizontalDistance() {
        return bestHorizontalDistance;
    }

    public int noProgressTicks() {
        return noProgressTicks;
    }

    public int cooldownTicksRemaining() {
        return cooldownTicksRemaining;
    }

    private void beginTracking(T terminalIdentity, double horizontalDistance) {
        this.terminalIdentity = terminalIdentity;
        tracking = true;
        bestHorizontalDistance = horizontalDistance;
        progressReferenceDistance = horizontalDistance;
        noProgressTicks = 0;
        cooldownTicksRemaining = 0;
    }

    private static void validateDistance(double horizontalDistance) {
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0) {
            throw new IllegalArgumentException(
                "Horizontal distance must be finite and non-negative."
            );
        }
    }
}
