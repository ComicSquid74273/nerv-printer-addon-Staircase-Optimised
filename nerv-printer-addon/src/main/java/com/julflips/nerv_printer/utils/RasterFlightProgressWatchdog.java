package com.julflips.nerv_printer.utils;

import java.util.Objects;

/** Detects a commanded BoatFly leg that makes no meaningful 3D progress. */
public final class RasterFlightProgressWatchdog<T> {
    private final int requiredStagnantTicks;
    private final double minimumProgress;
    private T target;
    private double referenceDistance;
    private double bestDistance;
    private int stagnantTicks;

    public RasterFlightProgressWatchdog(
        int requiredStagnantTicks,
        double minimumProgress
    ) {
        if (requiredStagnantTicks <= 0
            || !Double.isFinite(minimumProgress)
            || minimumProgress <= 0.0) {
            throw new IllegalArgumentException(
                "Flight progress limits must be positive and finite."
            );
        }
        this.requiredStagnantTicks = requiredStagnantTicks;
        this.minimumProgress = minimumProgress;
        reset();
    }

    /** Returns true once, then starts a fresh observation window. */
    public boolean observe(T target, double distance) {
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(
                "Flight distance must be finite and non-negative."
            );
        }
        if (!Objects.equals(this.target, target)) {
            this.target = target;
            referenceDistance = distance;
            bestDistance = distance;
            stagnantTicks = 0;
            return false;
        }
        bestDistance = Math.min(bestDistance, distance);
        if (referenceDistance - bestDistance >= minimumProgress) {
            referenceDistance = bestDistance;
            stagnantTicks = 0;
            return false;
        }
        stagnantTicks++;
        if (stagnantTicks < requiredStagnantTicks) return false;
        referenceDistance = bestDistance;
        stagnantTicks = 0;
        return true;
    }

    public void reset() {
        target = null;
        referenceDistance = Double.POSITIVE_INFINITY;
        bestDistance = Double.POSITIVE_INFINITY;
        stagnantTicks = 0;
    }
}
