package com.julflips.nerv_printer.utils;

/**
 * Computes how far a vehicle may advance beyond the first unfinished raster
 * target without carrying that target outside the player's real interaction
 * sphere.
 */
public final class RasterReachBarrier {
    private RasterReachBarrier() {
    }

    public static int maximumLeadCells(
        double interactionRange,
        double verticalDistance,
        double safetyMargin,
        int configuredMaximum
    ) {
        if (!(interactionRange > 0.0)
            || verticalDistance < 0.0
            || safetyMargin < 0.0
            || configuredMaximum < 0) {
            throw new IllegalArgumentException("Reach barrier inputs must be non-negative and range must be positive.");
        }

        double vertical = Math.min(verticalDistance, interactionRange);
        double horizontalReach = Math.sqrt(Math.max(
            0.0,
            interactionRange * interactionRange - vertical * vertical
        ));
        int safeCells = (int) Math.floor(Math.max(0.0, horizontalReach - safetyMargin));
        return Math.min(configuredMaximum, safeCells);
    }
}
