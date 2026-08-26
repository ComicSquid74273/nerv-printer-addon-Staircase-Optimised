package com.julflips.nerv_printer.utils;

/** Selects a local construction handoff only for a nearby in-footprint boat. */
public final class RasterLocalCheckpointResumePolicy {
    private RasterLocalCheckpointResumePolicy() {}

    public static boolean canResume(
        double x,
        double y,
        double z,
        double targetX,
        double targetY,
        double targetZ,
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ,
        double maximumHorizontalDistance,
        double maximumVerticalDistance
    ) {
        if (x < minimumX || x > maximumX
            || z < minimumZ || z > maximumZ) {
            return false;
        }
        return Math.hypot(x - targetX, z - targetZ)
                <= maximumHorizontalDistance
            && Math.abs(y - targetY) <= maximumVerticalDistance;
    }
}
