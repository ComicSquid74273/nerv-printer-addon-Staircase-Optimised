package com.julflips.nerv_printer.utils;

/**
 * Defines when a mounted-boat snapshot may seed a route-rejoin attempt.
 *
 * <p>Nearby blocks are deliberately not part of this decision. A stationary
 * boat can be close enough to planned blocks to fail the conservative swept
 * envelope while still being a valid recovery start. Requiring that envelope
 * here deadlocks recovery because the gate stops the movement needed to leave
 * the pose. Actual damage egress remains the authority for unsafe recovery.</p>
 */
public final class RasterRouteRejoinSnapshotPolicy {
    private RasterRouteRejoinSnapshotPolicy() {}

    public static boolean mayObserve(
        boolean mounted,
        boolean actualDamageEgressActive
    ) {
        return mounted && !actualDamageEgressActive;
    }
}
