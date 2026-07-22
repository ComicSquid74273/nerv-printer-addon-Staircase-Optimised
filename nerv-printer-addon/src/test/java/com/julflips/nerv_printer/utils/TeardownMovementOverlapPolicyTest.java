package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeardownMovementOverlapPolicyTest {
    @Test
    void ownedThmLeaseKeepsSlowClassifiedTeardownMoving() {
        assertTrue(
            TeardownMovementOverlapPolicy.mayContinue(
                true,
                false,
                true,
                true,
                RepairMiningClassification.SLOW_PROGRESSIVE,
                true
            )
        );
    }

    @Test
    void ordinarySlowMiningStillHoldsWithoutThmOwnership() {
        assertFalse(
            TeardownMovementOverlapPolicy.mayContinue(
                true,
                false,
                true,
                true,
                RepairMiningClassification.SLOW_PROGRESSIVE,
                false
            )
        );
    }

    @Test
    void tpsPauseAndLostReachRemainHardSafetyStops() {
        assertFalse(
            TeardownMovementOverlapPolicy.mayContinue(
                true,
                true,
                true,
                true,
                RepairMiningClassification.VANILLA_BATCH_INSTANT,
                true
            )
        );
        assertFalse(
            TeardownMovementOverlapPolicy.mayContinue(
                true,
                false,
                false,
                true,
                RepairMiningClassification.VANILLA_BATCH_INSTANT,
                true
            )
        );
    }

    @Test
    void routeCannotAdvancePastTheTargetsLastReachableSupport() {
        assertFalse(
            TeardownMovementOverlapPolicy.mayContinue(
                true,
                false,
                true,
                false,
                RepairMiningClassification.SLOW_PROGRESSIVE,
                true
            )
        );
    }
}
