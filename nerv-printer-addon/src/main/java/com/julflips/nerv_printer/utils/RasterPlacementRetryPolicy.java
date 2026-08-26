package com.julflips.nerv_printer.utils;

/** Decides how an out-of-reach placement acknowledgement is recovered. */
public final class RasterPlacementRetryPolicy {
    private RasterPlacementRetryPolicy() {
    }

    public enum Action {
        WAIT_FOR_ACKNOWLEDGEMENT,
        DROP_OPTIONAL,
        REANCHOR_RASTER_ROUTE,
        HOLD_BUILD
    }

    public static Action decideOutOfReach(
        boolean retryDue,
        boolean optional,
        boolean boatRaster
    ) {
        if (!retryDue) return Action.WAIT_FOR_ACKNOWLEDGEMENT;
        if (optional) return Action.DROP_OPTIONAL;
        return boatRaster
            ? Action.REANCHOR_RASTER_ROUTE
            : Action.HOLD_BUILD;
    }
}
