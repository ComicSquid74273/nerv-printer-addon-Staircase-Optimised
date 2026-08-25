package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;

/**
 * Resolves monotonic progress along an ordered walking route from the
 * player's horizontal cell.
 *
 * <p>The original Nerv printer treats checkpoint progress as horizontal:
 * vanilla gravity follows lower terrain and the generic obstacle auto-jump
 * follows higher terrain. Route progress must therefore not depend on a
 * grounded tick or on classifying an individual step as upward or downward.
 * This resolver keeps that contract while allowing a caller to retain an
 * exact-next-support safety gate.</p>
 */
public final class OrderedRouteProgressResolver {
    private OrderedRouteProgressResolver() {
    }

    /**
     * Resolves either the current route support or its immediate successor.
     *
     * <p>Only monotonic one-cell progress is accepted. This prevents a nearby
     * return leg or another height of a helix from stealing the cursor while
     * still allowing any valid mixed terrain profile without per-slope state.
     * Player Y and {@code onGround} are intentionally absent, matching the
     * original printer's horizontal checkpoint contract.</p>
     */
    public static OptionalInt resolve(
        List<BlockPos> orderedSupports,
        int currentIndex,
        double playerX,
        double playerZ
    ) {
        return resolve(
            orderedSupports,
            currentIndex,
            1,
            playerX,
            playerZ
        );
    }

    /**
     * Resolves either the current route support or the immediate support in
     * the caller-owned traversal direction.
     *
     * <p>A direction of {@code -1} is used by ordered recovery egress. The
     * cursor still advances by exactly one horizontal cell, so walking back
     * over mixed-height terrain cannot oscillate between point targets.</p>
     */
    public static OptionalInt resolve(
        List<BlockPos> orderedSupports,
        int currentIndex,
        int direction,
        double playerX,
        double playerZ
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        if (orderedSupports.isEmpty()) {
            throw new IllegalArgumentException(
                "An ordered route requires at least one support."
            );
        }
        if (currentIndex < 0 || currentIndex >= orderedSupports.size()) {
            throw new IllegalArgumentException(
                "The route cursor is outside the ordered supports."
            );
        }
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException(
                "The route direction must be -1 or 1."
            );
        }
        if (!Double.isFinite(playerX)
            || !Double.isFinite(playerZ)) {
            throw new IllegalArgumentException(
                "Invalid horizontal player position."
            );
        }

        BlockPos current = Objects.requireNonNull(
            orderedSupports.get(currentIndex),
            "ordered support"
        );
        if (containsHorizontally(current, playerX, playerZ)) {
            return OptionalInt.of(currentIndex);
        }

        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= orderedSupports.size()) {
            return OptionalInt.empty();
        }
        BlockPos next = Objects.requireNonNull(
            orderedSupports.get(nextIndex),
            "ordered support"
        );
        if (!isWalkableRouteStep(current, next)
            || !containsHorizontally(next, playerX, playerZ)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(nextIndex);
    }

    private static boolean containsHorizontally(
        BlockPos support,
        double playerX,
        double playerZ
    ) {
        return playerX >= support.getX()
            && playerX < support.getX() + 1.0
            && playerZ >= support.getZ()
            && playerZ < support.getZ() + 1.0;
    }

    private static boolean isWalkableRouteStep(
        BlockPos current,
        BlockPos next
    ) {
        int horizontalDistance =
            Math.abs(next.getX() - current.getX())
                + Math.abs(next.getZ() - current.getZ());
        return horizontalDistance == 1
            && Math.abs(next.getY() - current.getY()) <= 1;
    }
}
