package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterRouteRejoinSnapshotPolicyTest {
    @Test
    void mountedSnapshotCanSeedRejoinWithoutAProximitySafetyGate() {
        assertTrue(RasterRouteRejoinSnapshotPolicy.mayObserve(true, false));
    }

    @Test
    void actualDamageEgressRetainsMovementAuthority() {
        assertFalse(RasterRouteRejoinSnapshotPolicy.mayObserve(true, true));
        assertFalse(RasterRouteRejoinSnapshotPolicy.mayObserve(false, false));
    }
}
