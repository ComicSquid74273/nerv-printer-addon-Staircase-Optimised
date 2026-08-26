package com.julflips.nerv_printer.utils;

/** Pure, speed-bounded horizontal BoatFly motion for one client tick. */
public final class BoatFlyHorizontalStep {
    private BoatFlyHorizontalStep() {
    }

    public record Velocity(double x, double z, boolean arrived) {
    }

    public static Velocity toward(
        double deltaX,
        double deltaZ,
        double blocksPerSecond,
        double arrivalRadius
    ) {
        if (!Double.isFinite(deltaX)
            || !Double.isFinite(deltaZ)
            || !(blocksPerSecond > 0.0)
            || !(arrivalRadius >= 0.0)
            || !Double.isFinite(blocksPerSecond)
            || !Double.isFinite(arrivalRadius)) {
            throw new IllegalArgumentException(
                "BoatFly horizontal step inputs are invalid."
            );
        }
        double distance = Math.hypot(deltaX, deltaZ);
        if (distance <= arrivalRadius) {
            return new Velocity(0.0, 0.0, true);
        }
        double step = Math.min(blocksPerSecond / 20.0, distance);
        double scale = step / distance;
        return new Velocity(deltaX * scale, deltaZ * scale, false);
    }
}
