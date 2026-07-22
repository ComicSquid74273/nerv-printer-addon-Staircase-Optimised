package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.BLOCKED;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.WALKABLE;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.COMPLETE;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FALLBACK;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FORWARD;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_START;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CircularMiningRecoveryPlanTest {
    @Test
    void completeUStartsAtTheConfiguredStartingEndpoint() {
        var result = CircularMiningRecoveryPlan.analyze(
            List.of(WALKABLE, WALKABLE, WALKABLE, WALKABLE)
        );

        assertEquals(FORWARD, result.mode());
        assertEquals(0, result.firstWalkable());
        assertEquals(3, result.lastWalkable());
    }

    @Test
    void brokenPrefixRecoversFromTheOtherEndpoint() {
        var result = CircularMiningRecoveryPlan.analyze(
            List.of(AIR, AIR, WALKABLE, WALKABLE, WALKABLE)
        );

        assertEquals(RECOVER_FROM_END, result.mode());
        assertEquals(2, result.firstWalkable());
        assertEquals(4, result.lastWalkable());
    }

    @Test
    void brokenSuffixRecoversSymmetricallyFromTheStartingEndpoint() {
        var result = CircularMiningRecoveryPlan.analyze(
            List.of(WALKABLE, WALKABLE, WALKABLE, AIR, AIR)
        );

        assertEquals(RECOVER_FROM_START, result.mode());
        assertEquals(0, result.firstWalkable());
        assertEquals(2, result.lastWalkable());
    }

    @Test
    void disconnectedOrBlockedRoutesFallBack() {
        assertEquals(
            FALLBACK,
            CircularMiningRecoveryPlan.analyze(List.of(WALKABLE, AIR, WALKABLE)).mode()
        );
        assertEquals(
            FALLBACK,
            CircularMiningRecoveryPlan.analyze(List.of(WALKABLE, BLOCKED, WALKABLE)).mode()
        );
        assertEquals(
            FALLBACK,
            CircularMiningRecoveryPlan.analyze(List.of(AIR, WALKABLE, AIR)).mode()
        );
    }

    @Test
    void fullyBrokenRouteIsComplete() {
        assertEquals(
            COMPLETE,
            CircularMiningRecoveryPlan.analyze(List.of(AIR, AIR, AIR)).mode()
        );
    }
}
