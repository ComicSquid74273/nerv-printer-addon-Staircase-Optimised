package com.julflips.nerv_printer.utils;

/** Separates active-row virtual solids from physical completed/future rows. */
public final class RasterVirtualTargetPolicy {
    private RasterVirtualTargetPolicy() {
    }

    public static boolean isActiveRowVirtualSolid(
        int targetBand,
        int activeBand
    ) {
        return targetBand == activeBand;
    }

    /** Movement owns the virtual row until its compiled transition completes. */
    public static int movementOwnedBand(
        int placementBand,
        int routeBand,
        boolean routeAvailable
    ) {
        return routeAvailable ? routeBand : placementBand;
    }
}
