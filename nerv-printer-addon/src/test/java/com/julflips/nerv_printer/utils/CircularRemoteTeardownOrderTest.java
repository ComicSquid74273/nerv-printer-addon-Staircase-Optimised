package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.WALKABLE;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.FORWARD;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Mode.RECOVER_FROM_START;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CircularRemoteTeardownOrderTest {
    @Test
    void intactRouteIsRemovedFromEndTowardItsStartWalkway() {
        List<Integer> order = CircularRemoteTeardownOrder.create(
            List.of(0, 1, 2, 3),
            FORWARD
        );

        assertEquals(List.of(3, 2, 1, 0), order);

        ArrayList<CircularMiningRecoveryPlan.Cell> interrupted =
            new ArrayList<>(List.of(WALKABLE, WALKABLE, WALKABLE, WALKABLE));
        interrupted.set(order.get(0), AIR);
        interrupted.set(order.get(1), AIR);
        assertEquals(
            RECOVER_FROM_START,
            CircularMiningRecoveryPlan.analyze(interrupted).mode()
        );
    }

    @Test
    void startConnectedRemainderKeepsShrinkingTowardStart() {
        assertEquals(
            List.of(2, 1, 0),
            CircularRemoteTeardownOrder.create(
                List.of(0, 1, 2),
                RECOVER_FROM_START
            )
        );
    }

    @Test
    void legacyEndConnectedRemainderStaysAttachedToItsEnd() {
        assertEquals(
            List.of(1, 2, 3),
            CircularRemoteTeardownOrder.create(
                List.of(1, 2, 3),
                RECOVER_FROM_END
            )
        );
    }
}
