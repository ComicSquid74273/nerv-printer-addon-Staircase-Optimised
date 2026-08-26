package com.julflips.nerv_printer.utils;

/** Distinguishes a real mounted-envelope change from normal rider bobbing. */
public final class RasterMountedEnvelopePolicy {
    private static final double HORIZONTAL_TOLERANCE = 0.02;
    private static final double VERTICAL_TOLERANCE = 0.05;

    private RasterMountedEnvelopePolicy() {
    }

    public static boolean sameExtents(
        double previousMinX,
        double previousMinY,
        double previousMinZ,
        double previousMaxX,
        double previousMaxY,
        double previousMaxZ,
        double currentMinX,
        double currentMinY,
        double currentMinZ,
        double currentMaxX,
        double currentMaxY,
        double currentMaxZ
    ) {
        return close(previousMinX, currentMinX, HORIZONTAL_TOLERANCE)
            && close(previousMinY, currentMinY, VERTICAL_TOLERANCE)
            && close(previousMinZ, currentMinZ, HORIZONTAL_TOLERANCE)
            && close(previousMaxX, currentMaxX, HORIZONTAL_TOLERANCE)
            && close(previousMaxY, currentMaxY, VERTICAL_TOLERANCE)
            && close(previousMaxZ, currentMaxZ, HORIZONTAL_TOLERANCE);
    }

    private static boolean close(double left, double right, double tolerance) {
        return Math.abs(left - right) <= tolerance;
    }
}
