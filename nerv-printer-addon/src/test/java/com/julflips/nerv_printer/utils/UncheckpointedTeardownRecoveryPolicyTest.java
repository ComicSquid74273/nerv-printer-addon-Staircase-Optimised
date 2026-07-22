package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.WALKABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UncheckpointedTeardownRecoveryPolicyTest {
    @Test
    void adoptsOnlyTheAirPrefixLeftByOrderedTeardown() {
        CircularMiningRecoveryPlan.Result route =
            CircularMiningRecoveryPlan.analyze(
                List.of(AIR, AIR, WALKABLE, WALKABLE)
            );

        assertTrue(
            UncheckpointedTeardownRecoveryPolicy.canAdopt(
                MapCyclePhase.IDLE,
                route,
                true
            )
        );
    }

    @Test
    void doesNotMistakeAConstructionPrefixForTeardown() {
        CircularMiningRecoveryPlan.Result route =
            CircularMiningRecoveryPlan.analyze(
                List.of(WALKABLE, WALKABLE, AIR, AIR)
            );

        assertFalse(
            UncheckpointedTeardownRecoveryPolicy.canAdopt(
                MapCyclePhase.IDLE,
                route,
                true
            )
        );
    }

    @Test
    void existingLifecycleOwnershipOrWrongPositionCannotBeAdopted() {
        CircularMiningRecoveryPlan.Result route =
            CircularMiningRecoveryPlan.analyze(
                List.of(AIR, WALKABLE, WALKABLE)
            );

        assertFalse(
            UncheckpointedTeardownRecoveryPolicy.canAdopt(
                MapCyclePhase.MINING,
                route,
                true
            )
        );
        assertFalse(
            UncheckpointedTeardownRecoveryPolicy.canAdopt(
                MapCyclePhase.IDLE,
                route,
                false
            )
        );
    }
}
