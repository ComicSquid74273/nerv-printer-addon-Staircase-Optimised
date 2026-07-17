package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularTraversalSafetyTest {
    @Test
    void generalWalkingBufferCannotMakeRemovalUnsafe() {
        assertEquals(0.15, CircularTraversalSafety.checkpointBuffer(1.0));
        assertEquals(0.15, CircularTraversalSafety.checkpointBuffer(0.2));
        assertEquals(0.1, CircularTraversalSafety.checkpointBuffer(0.1));
    }

    @Test
    void zeroCannotMakeCircularCheckpointsUnreachable() {
        assertEquals(0.05, CircularTraversalSafety.checkpointBuffer(0.0));
    }

    @Test
    void normalLongLineBufferCannotCutConnectorCorners() {
        assertEquals(
            0.35,
            CircularTraversalSafety.connectorCheckpointBuffer(0.8)
        );
        assertEquals(
            0.3,
            CircularTraversalSafety.connectorCheckpointBuffer(0.3)
        );
        assertEquals(
            0.2,
            CircularTraversalSafety.connectorCheckpointBuffer(0.0)
        );
    }

    @Test
    void legalStepHeightIsAcceptedWithoutWaitingForLanding() {
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(100, 101)
        );
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(101, 100)
        );
        assertTrue(
            CircularTraversalSafety.isConnectorStepHeightReachable(100.8, 101)
        );
    }

    @Test
    void anotherHelixLevelCannotAdvanceTheCursor() {
        assertFalse(
            CircularTraversalSafety.isConnectorStepHeightReachable(100, 103)
        );
        assertFalse(
            CircularTraversalSafety.isConnectorStepHeightReachable(
                Double.NaN,
                100
            )
        );
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularTraversalSafety.checkpointBuffer(Double.NaN)
        );
    }
}
