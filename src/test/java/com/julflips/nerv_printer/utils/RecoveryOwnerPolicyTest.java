package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryOwnerPolicyTest {
    @Test
    void miningAssignmentWinsOverStaleBuildFlag() {
        assertEquals(
            RecoveryOwnerPolicy.Owner.MINING,
            RecoveryOwnerPolicy.decide(
                MapCyclePhase.MINING,
                true,
                true
            )
        );
    }

    @Test
    void persistedMiningPhaseRecoversMiningBeforeAssignmentsAreRebuilt() {
        assertEquals(
            RecoveryOwnerPolicy.Owner.MINING,
            RecoveryOwnerPolicy.decide(
                MapCyclePhase.MINING,
                true,
                false
            )
        );
    }

    @Test
    void activeBuildOwnsBuildPhaseRecovery() {
        assertEquals(
            RecoveryOwnerPolicy.Owner.BUILD,
            RecoveryOwnerPolicy.decide(
                MapCyclePhase.BUILDING,
                true,
                false
            )
        );
    }
}
