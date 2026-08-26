package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildInventoryConflictPolicyTest {
    @Test
    void materialConflictKeepsTheCurrentBuildRoute() {
        assertEquals(
            BuildInventoryConflictPolicy.Action.RESYNC_IN_PLACE,
            BuildInventoryConflictPolicy.decide(true, true, false)
        );
    }

    @Test
    void ambiguousRepairToolIdentityUsesGroundedBuildRecovery() {
        assertEquals(
            BuildInventoryConflictPolicy.Action.RECOVER_BUILD,
            BuildInventoryConflictPolicy.decide(true, false, true)
        );
    }

    @Test
    void repairIdentityRiskWinsIfOwnersEverOverlap() {
        assertEquals(
            BuildInventoryConflictPolicy.Action.RECOVER_BUILD,
            BuildInventoryConflictPolicy.decide(true, true, true)
        );
    }

    @Test
    void unrelatedConflictDoesNotClaimBuildRecovery() {
        assertEquals(
            BuildInventoryConflictPolicy.Action.STOP_NON_BUILD_OWNER,
            BuildInventoryConflictPolicy.decide(false, true, false)
        );
    }
}
