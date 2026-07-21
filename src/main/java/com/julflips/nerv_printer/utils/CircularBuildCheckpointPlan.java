package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;

/**
 * Separates the three structural U endpoints and exterior departure from the
 * exact connector steering steps required between the two far endpoints.
 */
public final class CircularBuildCheckpointPlan {
    private CircularBuildCheckpointPlan() {
    }

    public enum TraversalPhase {
        OUTBOUND,
        CONNECTOR,
        RETURN
    }

    public record Plan<T>(
        List<T> structuralCheckpoints,
        List<T> connectorTraversalSteps
    ) {
        public Plan {
            structuralCheckpoints = List.copyOf(structuralCheckpoints);
            connectorTraversalSteps = List.copyOf(connectorTraversalSteps);
            if (structuralCheckpoints.size() != 4) {
                throw new IllegalArgumentException(
                    "A circular build must have exactly four structural checkpoints."
                );
            }
            if (connectorTraversalSteps.isEmpty()) {
                throw new IllegalArgumentException(
                    "A circular connector must reach its return endpoint."
                );
            }
        }
    }

    /**
     * The connector path includes both far endpoints. The outbound far
     * endpoint is already reached by normal printing, so internal traversal
     * starts at path index one and includes the return far endpoint.
     */
    public static <T> Plan<T> create(
        T outboundNorth,
        List<T> connectorPath,
        T returnExit
    ) {
        Objects.requireNonNull(outboundNorth, "outboundNorth");
        Objects.requireNonNull(connectorPath, "connectorPath");
        Objects.requireNonNull(returnExit, "returnExit");
        if (connectorPath.size() < 2) {
            throw new IllegalArgumentException(
                "A connector path must contain both far endpoints."
            );
        }
        for (T point : connectorPath) {
            Objects.requireNonNull(point, "connectorPath point");
        }

        T connectorStart = connectorPath.getFirst();
        T connectorEnd = connectorPath.getLast();
        return new Plan<>(
            List.of(
                outboundNorth,
                connectorStart,
                connectorEnd,
                returnExit
            ),
            connectorPath.subList(1, connectorPath.size())
        );
    }

    /**
     * Outbound and connector handoffs must retain their checkpoint as the
     * steering goal until it is actually reached. Merely entering the final
     * support cell is not enough: changing direction at that point can leave
     * the structural checkpoint queued behind the player. Reverse movement is
     * an ordered placement backtrack and therefore continues to use the shared
     * route goal.
     */
    public static boolean checkpointOwnsSteering(
        TraversalPhase phase,
        int movementDirection
    ) {
        Objects.requireNonNull(phase, "phase");
        if (movementDirection != -1 && movementDirection != 1) {
            throw new IllegalArgumentException(
                "Circular build movement direction must be -1 or 1."
            );
        }
        return movementDirection > 0 && phase != TraversalPhase.RETURN;
    }

    /**
     * Completion of the complete ordered U may satisfy only the final exterior
     * exit. It must never consume a stale outbound or connector checkpoint.
     */
    public static boolean routeCompletionReachesCheckpoint(
        boolean finalExitCheckpoint,
        boolean completeOrderedRoute
    ) {
        return finalExitCheckpoint && completeOrderedRoute;
    }

    /**
     * Entering the ordered return leg authoritatively completes the connector
     * handoff. Requiring another point-center sample after that cell entry can
     * leave the connector checkpoint behind a moving player.
     */
    public static boolean connectorHandoffReachesCheckpoint(
        boolean connectorExitCheckpoint,
        boolean returnLegEntered
    ) {
        return connectorExitCheckpoint && returnLegEntered;
    }
}
