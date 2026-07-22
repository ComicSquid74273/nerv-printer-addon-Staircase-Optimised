package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildCheckpointPlanTest {
    @Test
    void exposesThreeUStructuralEndpointsAndTheExteriorExit() {
        var plan = CircularBuildCheckpointPlan.create(
            "outbound-north",
            List.of(
                "outbound-far",
                "turn-1",
                "turn-2",
                "return-far"
            ),
            "return-exterior"
        );

        assertEquals(
            List.of(
                "outbound-north",
                "outbound-far",
                "return-far",
                "return-exterior"
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

    @Test
    void phaseHandoffsKeepSteeringWithoutStoppingForwardMovement() {
        assertTrue(
            CircularBuildCheckpointPlan.checkpointOwnsSteering(
                CircularBuildCheckpointPlan.TraversalPhase.OUTBOUND,
                1
            )
        );
        assertTrue(
            CircularBuildCheckpointPlan.checkpointOwnsSteering(
                CircularBuildCheckpointPlan.TraversalPhase.CONNECTOR,
                1
            )
        );
        assertFalse(
            CircularBuildCheckpointPlan.checkpointOwnsSteering(
                CircularBuildCheckpointPlan.TraversalPhase.RETURN,
                1
            )
        );
        assertFalse(
            CircularBuildCheckpointPlan.checkpointOwnsSteering(
                CircularBuildCheckpointPlan.TraversalPhase.OUTBOUND,
                -1
            )
        );
    }

    @Test
    void completeRouteCanConsumeOnlyTheFinalNorthExit() {
        assertFalse(
            CircularBuildCheckpointPlan.routeCompletionReachesCheckpoint(
                false,
                true
            )
        );
        assertFalse(
            CircularBuildCheckpointPlan.routeCompletionReachesCheckpoint(
                true,
                false
            )
        );
        assertTrue(
            CircularBuildCheckpointPlan.routeCompletionReachesCheckpoint(
                true,
                true
            )
        );
    }

    @Test
    void returnLegEntryConsumesOnlyTheConnectorExit() {
        assertFalse(
            CircularBuildCheckpointPlan.connectorHandoffReachesCheckpoint(
                false,
                true
            )
        );
        assertFalse(
            CircularBuildCheckpointPlan.connectorHandoffReachesCheckpoint(
                true,
                false
            )
        );
        assertTrue(
            CircularBuildCheckpointPlan.connectorHandoffReachesCheckpoint(
                true,
                true
            )
        );
    }
}
