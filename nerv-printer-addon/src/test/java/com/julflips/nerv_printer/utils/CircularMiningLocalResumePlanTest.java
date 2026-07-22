package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.CircularMiningRecoveryPlan.Cell.WALKABLE;
import static com.julflips.nerv_printer.utils.CircularMiningTraversalPlan.Endpoint.END;
import static com.julflips.nerv_printer.utils.CircularMiningTraversalPlan.Endpoint.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularMiningLocalResumePlanTest {
    @Test
    void intactRouteBacktracksFromLocalSupportBeforeRemoving() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(WALKABLE, WALKABLE, WALKABLE, WALKABLE);
        var plan = CircularMiningLocalResumePlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells),
            2
        ).orElseThrow();

        assertEquals(END, plan.exit());
        assertEquals(
            List.of(
                new CircularMiningTraversalPlan.Step(2, -1),
                new CircularMiningTraversalPlan.Step(1, -1),
                new CircularMiningTraversalPlan.Step(0, -1),
                new CircularMiningTraversalPlan.Step(1, 0),
                new CircularMiningTraversalPlan.Step(2, 1),
                new CircularMiningTraversalPlan.Step(3, 2)
            ),
            plan.steps()
        );
        assertSafeAndComplete(cells, 2, plan);
    }

    @Test
    void removedPrefixContinuesLocallyAndStillExitsOppositeEnd() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(AIR, AIR, WALKABLE, WALKABLE, WALKABLE);
        var plan = CircularMiningLocalResumePlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells),
            3
        ).orElseThrow();

        assertEquals(END, plan.exit());
        assertEquals(
            List.of(
                new CircularMiningTraversalPlan.Step(3, -1),
                new CircularMiningTraversalPlan.Step(2, -1),
                new CircularMiningTraversalPlan.Step(3, 2),
                new CircularMiningTraversalPlan.Step(4, 3)
            ),
            plan.steps()
        );
        assertSafeAndComplete(cells, 3, plan);
    }

    @Test
    void removedSuffixContinuesSymmetrically() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(WALKABLE, WALKABLE, WALKABLE, AIR, AIR);
        var plan = CircularMiningLocalResumePlan.create(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells),
            1
        ).orElseThrow();

        assertEquals(START, plan.exit());
        assertSafeAndComplete(cells, 1, plan);
    }

    @Test
    void refusesAirOrDisconnectedCurrentSupport() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(AIR, AIR, WALKABLE, WALKABLE);
        var recovery = CircularMiningRecoveryPlan.analyze(cells);

        assertTrue(
            CircularMiningLocalResumePlan.create(
                cells.size(),
                recovery,
                1
            ).isEmpty()
        );
    }

    @Test
    void toolRestockEgressUsesOnlyTheConnectedRemainingSuffix() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(AIR, AIR, WALKABLE, WALKABLE, WALKABLE);
        var egress = CircularMiningLocalResumePlan.createEgress(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells),
            3
        ).orElseThrow();

        assertEquals(List.of(3, 4), egress.supportIndices());
        assertEquals(END, egress.exit());
    }

    @Test
    void intactRouteEgressesToItsOrderedStartingEndpoint() {
        List<CircularMiningRecoveryPlan.Cell> cells =
            List.of(WALKABLE, WALKABLE, WALKABLE, WALKABLE);
        var egress = CircularMiningLocalResumePlan.createEgress(
            cells.size(),
            CircularMiningRecoveryPlan.analyze(cells),
            2
        ).orElseThrow();

        assertEquals(List.of(2, 1, 0), egress.supportIndices());
        assertEquals(START, egress.exit());
    }

    private static void assertSafeAndComplete(
        List<CircularMiningRecoveryPlan.Cell> cells,
        int currentSupport,
        CircularMiningLocalResumePlan.Plan plan
    ) {
        ArrayList<Boolean> remaining = new ArrayList<>();
        for (CircularMiningRecoveryPlan.Cell cell : cells) {
            remaining.add(cell == WALKABLE);
        }
        assertTrue(remaining.get(currentSupport));

        for (CircularMiningTraversalPlan.Step step : plan.steps()) {
            assertTrue(
                remaining.get(step.standIndex()),
                "Every local step must stand on remaining support."
            );
            if (step.removesBlock()) {
                assertTrue(remaining.get(step.removeIndex()));
                remaining.set(step.removeIndex(), false);
            }
        }
        assertTrue(remaining.get(plan.finalRemoveIndex()));
        remaining.set(plan.finalRemoveIndex(), false);
        assertTrue(remaining.stream().noneMatch(Boolean::booleanValue));
    }
}
