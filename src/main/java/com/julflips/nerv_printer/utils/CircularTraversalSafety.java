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

    public enum MiningCheckpointProgress {
        APPROACHING,
        HOLD_FOR_LANDING,
        REACHED
    }

    public record HorizontalPoint(double x, double z) {
        public HorizontalPoint {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException(
                    "A horizontal steering point must be finite."
                );
            }
        }
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

    /**
     * Returns true once movement from {@code previous} has reached or crossed
     * the plane through the center of {@code checkpoint}. This keeps an
     * overshot route cell from becoming a goal behind the player.
     */
    public static boolean hasCrossedCheckpointCenter(
        double playerX,
        double playerZ,
        double checkpointX,
        double checkpointZ,
        double previousX,
        double previousZ
    ) {
        if (!Double.isFinite(playerX)
            || !Double.isFinite(playerZ)
            || !Double.isFinite(checkpointX)
            || !Double.isFinite(checkpointZ)
            || !Double.isFinite(previousX)
            || !Double.isFinite(previousZ)) {
            return false;
        }

        double routeX = checkpointX - previousX;
        double routeZ = checkpointZ - previousZ;
        if (routeX * routeX + routeZ * routeZ == 0) return false;

        return (playerX - checkpointX) * routeX
            + (playerZ - checkpointZ) * routeZ >= 0;
    }

    /**
     * Keeps an ordered route moving in its established direction after the
     * player crosses a mixed-height checkpoint. Steering back to the point
     * center while gravity is completing a one-block descent produces a
     * needless 180-degree turn and can strand the player on the block edge.
     */
    public static HorizontalPoint orderedForwardSteeringPoint(
        double playerX,
        double playerZ,
        double checkpointX,
        double checkpointZ,
        double previousX,
        double previousZ,
        double forwardExtension
    ) {
        if (!Double.isFinite(forwardExtension)
            || forwardExtension < 0) {
            throw new IllegalArgumentException(
                "Forward steering extension must be finite and non-negative."
            );
        }
        if (!hasCrossedCheckpointCenter(
            playerX,
            playerZ,
            checkpointX,
            checkpointZ,
            previousX,
            previousZ
        )) {
            return new HorizontalPoint(checkpointX, checkpointZ);
        }

        double routeX = checkpointX - previousX;
        double routeZ = checkpointZ - previousZ;
        double length = Math.hypot(routeX, routeZ);
        if (length == 0) {
            return new HorizontalPoint(checkpointX, checkpointZ);
        }
        double unitX = routeX / length;
        double unitZ = routeZ / length;
        double playerForwardProjection =
            (playerX - checkpointX) * unitX
                + (playerZ - checkpointZ) * unitZ;
        double steeringForwardProjection = Math.max(
            forwardExtension,
            playerForwardProjection + forwardExtension
        );
        return new HorizontalPoint(
            checkpointX + unitX * steeringForwardProjection,
            checkpointZ + unitZ * steeringForwardProjection
        );
    }

    /**
     * A route cell is consumed only from stable support. Crossing it while
     * airborne holds horizontal movement until the player lands.
     */
    public static MiningCheckpointProgress miningCheckpointProgress(
        boolean horizontallyOverCheckpoint,
        boolean stablyStandingOnCheckpoint,
        boolean nearCheckpointCenter,
        boolean crossedCheckpointCenter
    ) {
        return miningCheckpointProgress(
            horizontallyOverCheckpoint,
            stablyStandingOnCheckpoint,
            nearCheckpointCenter,
            crossedCheckpointCenter,
            false
        );
    }

    /**
     * A committed one-block ascent keeps its forward movement until the player
     * is stably supported by the raised route cell. Entering that cell while
     * still airborne must not clear forward/jump input and turn the ascent into
     * a repeated edge fall.
     */
    public static MiningCheckpointProgress miningCheckpointProgress(
        boolean horizontallyOverCheckpoint,
        boolean stablyStandingOnCheckpoint,
        boolean nearCheckpointCenter,
        boolean crossedCheckpointCenter,
        boolean orderedStepUp
    ) {
        if (!nearCheckpointCenter && !crossedCheckpointCenter) {
            return MiningCheckpointProgress.APPROACHING;
        }
        if (stablyStandingOnCheckpoint) {
            return MiningCheckpointProgress.REACHED;
        }
        if (orderedStepUp && horizontallyOverCheckpoint) {
            return MiningCheckpointProgress.APPROACHING;
        }
        return horizontallyOverCheckpoint
            ? MiningCheckpointProgress.HOLD_FOR_LANDING
            : MiningCheckpointProgress.APPROACHING;
    }

    /**
     * Identifies a normal one-block ordered ascent both while approaching the
     * target cell and after horizontal momentum has entered that cell.
     */
    public static boolean isOrderedStepUpTarget(
        int orderedHorizontalCellDistance,
        int previousSupportY,
        int targetSupportY,
        int playerHorizontalDistanceFromTarget
    ) {
        if (orderedHorizontalCellDistance < 0
            || playerHorizontalDistanceFromTarget < 0) {
            throw new IllegalArgumentException(
                "Horizontal cell distances cannot be negative."
            );
        }
        return orderedHorizontalCellDistance == 1
            && targetSupportY == previousSupportY + 1
            && playerHorizontalDistanceFromTarget <= 1;
    }

    /**
     * A verified ordered ascent owns jump input until stable support confirms
     * the landing. The generic fixed-duration jump timer is not authoritative
     * route progress and must not release the key in the middle of an ascent.
     */
    public static boolean shouldHoldOrderedStepUpJump(
        boolean targetSupportSafe,
        boolean stablyStandingOnTarget,
        int orderedHorizontalCellDistance,
        int previousSupportY,
        int targetSupportY,
        int playerHorizontalDistanceFromTarget
    ) {
        return targetSupportSafe
            && !stablyStandingOnTarget
            && isOrderedStepUpTarget(
                orderedHorizontalCellDistance,
                previousSupportY,
                targetSupportY,
                playerHorizontalDistanceFromTarget
            );
    }

    /**
     * Detects the single intentional 180-degree turn in an interrupted U-route
     * recovery without treating normal straight steps or corners as reversals.
     */
    public static boolean isRouteReversal(
        double previousX,
        double previousZ,
        double checkpointX,
        double checkpointZ,
        double nextX,
        double nextZ
    ) {
        if (!Double.isFinite(previousX)
            || !Double.isFinite(previousZ)
            || !Double.isFinite(checkpointX)
            || !Double.isFinite(checkpointZ)
            || !Double.isFinite(nextX)
            || !Double.isFinite(nextZ)) {
            return false;
        }

        double incomingX = checkpointX - previousX;
        double incomingZ = checkpointZ - previousZ;
        double outgoingX = nextX - checkpointX;
        double outgoingZ = nextZ - checkpointZ;
        if (incomingX * incomingX + incomingZ * incomingZ == 0
            || outgoingX * outgoingX + outgoingZ * outgoingZ == 0) {
            return false;
        }
        return incomingX * outgoingX + incomingZ * outgoingZ < 0;
    }

    private static void validateBuffer(double configuredBuffer) {
        if (!Double.isFinite(configuredBuffer) || configuredBuffer < 0) {
            throw new IllegalArgumentException(
                "Checkpoint buffer must be finite and non-negative."
            );
        }
    }
}
