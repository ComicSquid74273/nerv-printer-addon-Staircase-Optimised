package com.julflips.nerv_printer.utils;

/** Decides which network position updates invalidate a compiled raster route. */
public final class RasterCorrectionPacketPolicy {
    private RasterCorrectionPacketPolicy() {
    }

    public static boolean requiresRouteRejoin(
        boolean playerPositionLook,
        boolean vehicleMove
    ) {
        // VehicleMove is normal mounted-entity synchronization. The current
        // boat pose and no-progress watchdog already absorb it locally; making
        // every packet a route correction brakes BoatFly every server tick.
        return playerPositionLook;
    }
}
