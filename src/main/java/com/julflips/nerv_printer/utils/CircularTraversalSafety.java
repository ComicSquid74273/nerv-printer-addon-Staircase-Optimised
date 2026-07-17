package com.julflips.nerv_printer.utils;

/**
 * Runtime safety limits that must not be weakened by general walking settings.
 */
public final class CircularTraversalSafety {
    public static final double MAXIMUM_CHECKPOINT_BUFFER = 0.15;
    public static final double MINIMUM_CHECKPOINT_BUFFER = 0.05;
    public static final double MAXIMUM_CONNECTOR_CHECKPOINT_BUFFER = 0.35;
    public static final double MINIMUM_CONNECTOR_CHECKPOINT_BUFFER = 0.20;
    public static final double MAXIMUM_CONNECTOR_STEP_HEIGHT_DISTANCE = 1.25;

    private CircularTraversalSafety() {
    }

    public static double checkpointBuffer(double configuredBuffer) {
        validateBuffer(configuredBuffer);
        return Math.max(
            MINIMUM_CHECKPOINT_BUFFER,
            Math.min(configuredBuffer, MAXIMUM_CHECKPOINT_BUFFER)
        );
    }

    /**
     * Connector turns must be reached inside the target block rather than at
     * its edge. The wider floor avoids exact-centering stalls while the cap
     * prevents a normal long-line buffer from cutting ninety-degree corners.
     */
    public static double connectorCheckpointBuffer(double configuredBuffer) {
        validateBuffer(configuredBuffer);
        return Math.max(
            MINIMUM_CONNECTOR_CHECKPOINT_BUFFER,
            Math.min(configuredBuffer, MAXIMUM_CONNECTOR_CHECKPOINT_BUFFER)
        );
    }

    /**
     * Adjacent connector steps may differ by one block vertically. Repeated
     * helix shafts are separated by at least three blocks, so this accepts an
     * in-progress legal step without confusing a different shaft level.
     */
    public static boolean isConnectorStepHeightReachable(
        double playerFeetY,
        double targetFeetY
    ) {
        return Double.isFinite(playerFeetY)
            && Double.isFinite(targetFeetY)
            && Math.abs(playerFeetY - targetFeetY)
                <= MAXIMUM_CONNECTOR_STEP_HEIGHT_DISTANCE;
    }

    private static void validateBuffer(double configuredBuffer) {
        if (!Double.isFinite(configuredBuffer) || configuredBuffer < 0) {
            throw new IllegalArgumentException(
                "Checkpoint buffer must be finite and non-negative."
            );
        }
    }
}
