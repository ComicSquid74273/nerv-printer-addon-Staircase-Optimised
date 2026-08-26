package com.julflips.nerv_printer.utils;

/** Prevents north/south row construction from reaching ahead or behind. */
public final class RasterLateralPlacementPolicy {
    public static final double MAX_LONGITUDINAL_DELTA = 0.55;

    private RasterLateralPlacementPolicy() {
    }

    public static boolean isBeside(double actualEyeZ, double targetCenterZ) {
        return Double.isFinite(actualEyeZ)
            && Double.isFinite(targetCenterZ)
            && Math.abs(targetCenterZ - actualEyeZ)
                <= MAX_LONGITUDINAL_DELTA + 1.0e-9;
    }
}
