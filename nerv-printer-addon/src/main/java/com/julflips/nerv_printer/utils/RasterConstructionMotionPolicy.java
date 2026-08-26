package com.julflips.nerv_printer.utils;

/**
 * Chooses BoatFly's speed class while following the map construction route.
 * A quiet placement tick is still build traversal; it must not temporarily
 * accelerate to exterior travel speed between placement submissions.
 */
public final class RasterConstructionMotionPolicy {
    private RasterConstructionMotionPolicy() {
    }

    public static BoatFlyAdapter.DriveMode driveMode(
        boolean placementActiveThisTick
    ) {
        return BoatFlyAdapter.DriveMode.BUILD;
    }
}
