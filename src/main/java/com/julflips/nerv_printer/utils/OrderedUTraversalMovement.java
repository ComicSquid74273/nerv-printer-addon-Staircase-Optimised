package com.julflips.nerv_printer.utils;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Shared ordered-support movement model for circular printing and teardown.
 *
 * <p>This class owns no block action. It resolves horizontal route progress,
 * checks only the immediate next support, and selects the end of the current
 * straight segment as the steering goal. Placement, repair, and teardown are
 * consumers of the resulting support cursor; none of them define a separate
 * walking algorithm.</p>
 */
public final class OrderedUTraversalMovement {
    private OrderedUTraversalMovement() {
    }

    public record Progress(
        int previousIndex,
        int currentIndex,
        MovementDecision<BlockPos> movement
    ) {
        public Progress {
            if (previousIndex < 0
                || currentIndex < 0
                || Math.abs(currentIndex - previousIndex) > 1) {
                throw new IllegalArgumentException(
                    "Ordered U progress may reconcile only one adjacent support."
                );
            }
            Objects.requireNonNull(movement, "movement");
        }

        public boolean enteredNextSupport() {
            return currentIndex > previousIndex;
        }

        public boolean reconciledPreviousSupport() {
            return currentIndex < previousIndex;
        }
    }

    public enum MovementStatus {
        READY,
        COMPLETE,
        OFF_PATH,
        WAITING_FOR_NEXT_SUPPORT
    }

    /**
     * Reports whether the final horizontal route cell has been entered.
     * Ordered U movement deliberately leaves vertical landing to vanilla
     * gravity and auto-jump; requiring a separate grounded tick here can
     * deadlock the route after its movement cursor has already stopped.
     */
    public enum EndpointProgress {
        APPROACHING,
        REACHED
    }

    /**
     * Runtime handoff at the intentional 180-degree pivot of an interrupted
     * teardown route. The ordered cursor is intentionally horizontal across
     * mixed-height terrain, so the pivot spends one tick with horizontal
     * motion braked without adding a separate grounded-Y requirement.
     */
    public enum TurnaroundProgress {
        NOT_A_TURNAROUND,
        BRAKE_AND_MARK_SETTLED,
        READY
    }

    public record MovementDecision<T>(
        MovementStatus status,
        T requiredSupport
    ) {
        public MovementDecision {
            Objects.requireNonNull(status, "status");
            boolean requiresSupport =
                status == MovementStatus.READY
                    || status
                        == MovementStatus.WAITING_FOR_NEXT_SUPPORT;
            if (requiresSupport != (requiredSupport != null)) {
                throw new IllegalArgumentException(
                    "Movement status and required support disagree."
                );
            }
        }

        public boolean mayMove() {
            return status == MovementStatus.READY;
        }
    }

    public static EndpointProgress endpointProgress(
        boolean finalSupportCellEntered
    ) {
        return finalSupportCellEntered
            ? EndpointProgress.REACHED
            : EndpointProgress.APPROACHING;
    }

    public static TurnaroundProgress turnaroundProgress(
        boolean routeReversal,
        boolean alreadySettled
    ) {
        if (!routeReversal) {
            return TurnaroundProgress.NOT_A_TURNAROUND;
        }
        if (alreadySettled) return TurnaroundProgress.READY;
        return TurnaroundProgress.BRAKE_AND_MARK_SETTLED;
    }

    /**
     * Returns whether a route-derived 180-degree pivot is at or within the
     * requested number of ordered supports. Normal U corners are not pivots.
     */
    public static boolean hasRouteReversalWithin(
        List<BlockPos> orderedSupports,
        int currentIndex,
        int direction,
        int lookahead
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        requireDirection(direction);
        if (currentIndex < 0 || currentIndex >= orderedSupports.size()) {
            throw new IllegalArgumentException(
                "The ordered U cursor is outside its support path."
            );
        }
        if (lookahead < 0) {
            throw new IllegalArgumentException(
                "A route-reversal lookahead cannot be negative."
            );
        }
        for (int distance = 0; distance <= lookahead; distance++) {
            int index = currentIndex + distance * direction;
            if (index < 0 || index >= orderedSupports.size()) break;
            if (isRouteReversal(orderedSupports, index)) return true;
        }
        return false;
    }

    public static boolean isRouteReversal(
        List<BlockPos> orderedSupports,
        int index
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        if (index <= 0 || index >= orderedSupports.size() - 1) {
            return false;
        }
        BlockPos previous = Objects.requireNonNull(
            orderedSupports.get(index - 1),
            "previous support"
        );
        BlockPos pivot = Objects.requireNonNull(
            orderedSupports.get(index),
            "pivot support"
        );
        BlockPos next = Objects.requireNonNull(
            orderedSupports.get(index + 1),
            "next support"
        );
        return CircularTraversalSafety.isRouteReversal(
            previous.getX() + 0.5,
            previous.getZ() + 0.5,
            pivot.getX() + 0.5,
            pivot.getZ() + 0.5,
            next.getX() + 0.5,
            next.getZ() + 0.5
        );
    }

    /**
     * Structural checkpoint validation follows the frozen route endpoints.
     * An interrupted recovery can enter and exit through the same exterior
     * support, so endpoint ownership must not depend on those positions being
     * distinct.
     */
    public static <T> boolean ownsStructuralEndpoint(
        List<T> orderedSupports,
        T support
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(support, "support");
        return !orderedSupports.isEmpty()
            && (support.equals(orderedSupports.getFirst())
                || support.equals(orderedSupports.getLast()));
    }

    public static Progress resolve(
        List<BlockPos> orderedSupports,
        int currentIndex,
        double playerX,
        double playerZ,
        Predicate<? super BlockPos> isConfirmedReady
    ) {
        return resolve(
            orderedSupports,
            currentIndex,
            1,
            playerX,
            playerZ,
            isConfirmedReady
        );
    }

    public static Progress resolve(
        List<BlockPos> orderedSupports,
        int currentIndex,
        int direction,
        double playerX,
        double playerZ,
        Predicate<? super BlockPos> isConfirmedReady
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(isConfirmedReady, "isConfirmedReady");
        requireDirection(direction);
        OptionalInt resolved = OrderedRouteProgressResolver.resolve(
            orderedSupports,
            currentIndex,
            direction,
            playerX,
            playerZ
        );
        int correctionIndex = currentIndex - direction;
        if (resolved.isEmpty()
            && correctionIndex >= 0
            && correctionIndex < orderedSupports.size()) {
            // A server correction may put the player back on the immediately
            // previous support after the client cursor entered the next one.
            // Accept only that adjacent route cell; arbitrary backward scans
            // remain recovery work and cannot steal the cursor.
            resolved = OrderedRouteProgressResolver.resolve(
                orderedSupports,
                currentIndex,
                -direction,
                playerX,
                playerZ
            );
        }
        if (resolved.isEmpty()) {
            return new Progress(
                currentIndex,
                currentIndex,
                new MovementDecision<>(
                    MovementStatus.OFF_PATH,
                    null
                )
            );
        }
        int resolvedIndex = resolved.getAsInt();
        return new Progress(
            currentIndex,
            resolvedIndex,
            decideMovement(
                orderedSupports,
                resolvedIndex,
                direction,
                isConfirmedReady
            )
        );
    }

    /**
     * Checks only the immediate next support from the monotonic route cursor.
     * Placement and teardown therefore receive the same movement gate.
     */
    public static <T> MovementDecision<T> decideMovement(
        List<T> orderedSupports,
        T currentSupport,
        Predicate<? super T> isConfirmedReady
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(currentSupport, "currentSupport");
        int currentIndex = orderedSupports.indexOf(currentSupport);
        if (currentIndex < 0) {
            return new MovementDecision<>(
                MovementStatus.OFF_PATH,
                null
            );
        }
        return decideMovement(
            orderedSupports,
            currentIndex,
            isConfirmedReady
        );
    }

    /**
     * Checks only the immediate next support from the monotonic route cursor.
     * Placement and teardown therefore receive the same movement gate.
     */
    public static <T> MovementDecision<T> decideMovement(
        List<T> orderedSupports,
        int currentIndex,
        Predicate<? super T> isConfirmedReady
    ) {
        return decideMovement(
            orderedSupports,
            currentIndex,
            1,
            isConfirmedReady
        );
    }

    public static <T> MovementDecision<T> decideMovement(
        List<T> orderedSupports,
        int currentIndex,
        int direction,
        Predicate<? super T> isConfirmedReady
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(isConfirmedReady, "isConfirmedReady");
        requireDirection(direction);
        if (currentIndex < 0 || currentIndex >= orderedSupports.size()) {
            return new MovementDecision<>(
                MovementStatus.OFF_PATH,
                null
            );
        }
        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= orderedSupports.size()) {
            return new MovementDecision<>(
                MovementStatus.COMPLETE,
                null
            );
        }
        T nextSupport = Objects.requireNonNull(
            orderedSupports.get(nextIndex),
            "next support"
        );
        return new MovementDecision<>(
            isConfirmedReady.test(nextSupport)
                ? MovementStatus.READY
                : MovementStatus.WAITING_FOR_NEXT_SUPPORT,
            nextSupport
        );
    }

    /**
     * Returns the farthest support reachable without changing horizontal
     * direction. Steering at segment scale is the behavior used by printing;
     * it avoids point-centering at every U block.
     */
    public static int steeringGoalIndex(
        List<BlockPos> orderedSupports,
        int currentIndex
    ) {
        return steeringGoalIndex(
            orderedSupports,
            currentIndex,
            1
        );
    }

    public static int steeringGoalIndex(
        List<BlockPos> orderedSupports,
        int currentIndex,
        int direction
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        requireDirection(direction);
        if (currentIndex < 0 || currentIndex >= orderedSupports.size()) {
            throw new IllegalArgumentException(
                "The ordered U cursor is outside its support path."
            );
        }
        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= orderedSupports.size()) {
            return currentIndex;
        }

        BlockPos current = orderedSupports.get(currentIndex);
        BlockPos next = orderedSupports.get(nextIndex);
        int directionX = next.getX() - current.getX();
        int directionZ = next.getZ() - current.getZ();
        requireHorizontalStep(directionX, directionZ);

        int goalIndex = nextIndex;
        for (int index = currentIndex + 2 * direction;
             index >= 0 && index < orderedSupports.size();
             index += direction) {
            BlockPos previous = orderedSupports.get(index - direction);
            BlockPos candidate = orderedSupports.get(index);
            int stepX = candidate.getX() - previous.getX();
            int stepZ = candidate.getZ() - previous.getZ();
            requireHorizontalStep(stepX, stepZ);
            if (stepX != directionX || stepZ != directionZ) break;
            goalIndex = index;
        }
        return goalIndex;
    }

    /**
     * Extends an endpoint one route step away from its first U support. This
     * is printing's approach support expressed from route geometry rather
     * than a map direction or coordinate constant.
     */
    public static BlockPos entryApproachSupport(
        BlockPos entryEndpoint,
        BlockPos firstRouteSupport
    ) {
        return exteriorEndpointSupport(
            entryEndpoint,
            firstRouteSupport,
            "first"
        );
    }

    /**
     * Extends the return endpoint one route step away from the final U
     * support. Entry and exit therefore use the same route-derived geometry
     * even when a map is rotated or a leg changes elevation.
     */
    public static BlockPos exitDepartureSupport(
        BlockPos exitEndpoint,
        BlockPos finalRouteSupport
    ) {
        return exteriorEndpointSupport(
            exitEndpoint,
            finalRouteSupport,
            "final"
        );
    }

    private static BlockPos exteriorEndpointSupport(
        BlockPos endpoint,
        BlockPos adjacentRouteSupport,
        String routeSupportName
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(
            adjacentRouteSupport,
            "adjacentRouteSupport"
        );
        int directionX = adjacentRouteSupport.getX() - endpoint.getX();
        int directionZ = adjacentRouteSupport.getZ() - endpoint.getZ();
        requireHorizontalStep(directionX, directionZ);
        if (Math.abs(
            adjacentRouteSupport.getY() - endpoint.getY()
        ) > 1) {
            throw new IllegalArgumentException(
                "The " + routeSupportName
                    + " ordered U support is not walkable from its endpoint."
            );
        }
        return endpoint.add(-directionX, 0, -directionZ);
    }

    private static void requireHorizontalStep(int deltaX, int deltaZ) {
        if (Math.abs(deltaX) + Math.abs(deltaZ) != 1) {
            throw new IllegalArgumentException(
                "Ordered U supports must be horizontally adjacent."
            );
        }
    }

    private static void requireDirection(int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException(
                "Ordered U direction must be -1 or 1."
            );
        }
    }

}
