package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;

/**
 * Separates the four user-visible U endpoints from the exact connector
 * steering steps required between the two far endpoints.
 */
public final class CircularBuildCheckpointPlan {
    private CircularBuildCheckpointPlan() {
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
        T returnNorth
    ) {
        Objects.requireNonNull(outboundNorth, "outboundNorth");
        Objects.requireNonNull(connectorPath, "connectorPath");
        Objects.requireNonNull(returnNorth, "returnNorth");
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
                returnNorth
            ),
            connectorPath.subList(1, connectorPath.size())
        );
    }
}
