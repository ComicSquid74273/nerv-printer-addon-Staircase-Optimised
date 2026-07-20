package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.WALKABLE;
import static com.julflips.nerv_printer.utils.CircularMiningTraversalPlan.Endpoint.END;
import static com.julflips.nerv_printer.utils.CircularMiningTraversalPlan.Endpoint.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularMiningTraversalPlanTest {
    @Test
    void fullUAlwaysRemovesTheSupportBehindTheBot() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(WALKABLE, WALKABLE, WALKABLE, WALKABLE, WALKABLE);
        var plan = CircularMiningTraversalPlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells)
        );

        assertEquals(START, plan.entry());
        assertEquals(END, plan.exit());
        assertEquals(
            List.of(
                new CircularMiningTraversalPlan.Step(0, -1),
                new CircularMiningTraversalPlan.Step(1, 0),
                new CircularMiningTraversalPlan.Step(2, 1),
                new CircularMiningTraversalPlan.Step(3, 2),
                new CircularMiningTraversalPlan.Step(4, 3)
            ),
            plan.steps()
        );
        assertSafeAndComplete(cells, plan);
    }

    @Test
    void brokenPrefixEntersAndExitsAtTheOppositeEndpoint() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(AIR, AIR, WALKABLE, WALKABLE, WALKABLE);
        var plan = CircularMiningTraversalPlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells)
        );

        assertEquals(END, plan.entry());
        assertEquals(END, plan.exit());
        assertEquals(
            List.of(
                new CircularMiningTraversalPlan.Step(4, -1),
                new CircularMiningTraversalPlan.Step(3, -1),
                new CircularMiningTraversalPlan.Step(2, -1),
                new CircularMiningTraversalPlan.Step(3, 2),
                new CircularMiningTraversalPlan.Step(4, 3)
            ),
            plan.steps()
        );
        assertSafeAndComplete(cells, plan);
    }

    @Test
    void brokenSuffixRecoversSymmetrically() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(WALKABLE, WALKABLE, WALKABLE, AIR, AIR);
        var plan = CircularMiningTraversalPlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells)
        );

        assertEquals(START, plan.entry());
        assertEquals(START, plan.exit());
        assertEquals(
            List.of(
                new CircularMiningTraversalPlan.Step(0, -1),
                new CircularMiningTraversalPlan.Step(1, -1),
                new CircularMiningTraversalPlan.Step(2, -1),
                new CircularMiningTraversalPlan.Step(1, 2),
                new CircularMiningTraversalPlan.Step(0, 1)
            ),
            plan.steps()
        );
        assertSafeAndComplete(cells, plan);
    }

    @Test
    void refusesCompleteOrDisconnectedRoutes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularMiningTraversalPlan.create(
                3,
                CircularMiningRecoveryPlan.analyze(List.of(AIR, AIR, AIR))
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularMiningTraversalPlan.create(
                3,
                CircularMiningRecoveryPlan.analyze(List.of(WALKABLE, AIR, WALKABLE))
            )
        );
    }

    private static void assertSafeAndComplete(
        List<CircularMiningRecoveryPlan.Cell> cells,
        CircularMiningTraversalPlan.Plan plan
    ) {
        ArrayList<Boolean> remaining = new ArrayList<>();
        for (CircularMiningRecoveryPlan.Cell cell : cells) {
            remaining.add(cell == WALKABLE);
        }

        for (CircularMiningTraversalPlan.Step step : plan.steps()) {
            assertTrue(remaining.get(step.standIndex()), "Bot must stand on an intact support.");
            if (step.removesBlock()) {
                assertTrue(remaining.get(step.removeIndex()), "Each removed support must still exist.");
                assertFalse(
                    step.standIndex() == step.removeIndex(),
                    "The bot must never remove its current support."
                );
                remaining.set(step.removeIndex(), false);
            }
        }
        assertTrue(remaining.get(plan.finalRemoveIndex()));
        remaining.set(plan.finalRemoveIndex(), false);
        assertTrue(remaining.stream().noneMatch(Boolean::booleanValue));
    }
}
