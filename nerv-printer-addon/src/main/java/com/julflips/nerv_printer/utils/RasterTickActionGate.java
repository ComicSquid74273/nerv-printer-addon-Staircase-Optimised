package com.julflips.nerv_printer.utils;

import java.util.function.BooleanSupplier;

/** Runs authoritative pending actions before Boat Raster's early-return loop. */
public final class RasterTickActionGate {
    private RasterTickActionGate() {}

    public static boolean allowRasterStateMachine(
        boolean actionPending,
        BooleanSupplier processPendingAction
    ) {
        if (!actionPending) return true;
        return !processPendingAction.getAsBoolean();
    }
}
