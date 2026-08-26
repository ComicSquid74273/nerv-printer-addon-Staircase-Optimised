package com.julflips.nerv_printer.utils;

/** Distinguishes flat-flight support contact from side/overhead obstruction. */
public final class RasterSupportedTravelPolicy {
    private static final double EPSILON = 1.0e-7;

    private RasterSupportedTravelPolicy() {
    }

    public static boolean isSupportContact(
        double obstacleMaximumY,
        double supportPlaneY
    ) {
        if (!Double.isFinite(obstacleMaximumY)
            || !Double.isFinite(supportPlaneY)) {
            throw new IllegalArgumentException(
                "Support-plane coordinates must be finite."
            );
        }
        return obstacleMaximumY <= supportPlaneY + EPSILON;
    }
}
