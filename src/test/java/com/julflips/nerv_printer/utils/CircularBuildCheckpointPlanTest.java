package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircularBuildCheckpointPlanTest {
    @Test
    void exposesOnlyTheFourUniqueUStructuralEndpoints() {
        var plan = CircularBuildCheckpointPlan.create(
            "outbound-north",
            List.of(
                "outbound-far",
                "turn-1",
                "turn-2",
                "return-far"
            ),
            "return-north"
        );

        assertEquals(
            List.of(
                "outbound-north",
                "outbound-far",
                "return-far",
                "return-north"
            ),
            plan.structuralCheckpoints()
        );
        assertEquals(
            4,
            new HashSet<>(plan.structuralCheckpoints()).size()
        );
        assertEquals(
            List.of("turn-1", "turn-2", "return-far"),
            plan.connectorTraversalSteps()
        );
    }

    @Test
    void directConnectorStillInternallyReachesTheReturnEndpoint() {
        var plan = CircularBuildCheckpointPlan.create(
            "start",
            List.of("outbound-far", "return-far"),
            "finish"
        );

        assertEquals(4, plan.structuralCheckpoints().size());
        assertEquals(
            List.of("return-far"),
            plan.connectorTraversalSteps()
        );
    }

    @Test
    void longHelixDoesNotCreateMoreStructuralCheckpoints() {
        ArrayList<Integer> connector = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) connector.add(index);

        var plan = CircularBuildCheckpointPlan.create(-1, connector, 1_000);

        assertEquals(4, plan.structuralCheckpoints().size());
        assertEquals(999, plan.connectorTraversalSteps().size());
        assertEquals(1, plan.connectorTraversalSteps().getFirst());
        assertEquals(999, plan.connectorTraversalSteps().getLast());
    }

    @Test
    void rejectsAConnectorWithoutBothEndpoints() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildCheckpointPlan.create(
                "start",
                List.of("only-one-far-end"),
                "finish"
            )
        );
    }
}
