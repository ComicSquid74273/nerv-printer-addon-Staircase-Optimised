package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;

/**
 * Index movement for hidden circular-build recovery steering.
 */
public final class CircularBuildRecoveryCursor {
    private CircularBuildRecoveryCursor() {
    }

    /**
     * Selects one safe endpoint once for the entire recovery egress.
     *
     * <p>The direction cannot change again while walking. When both sides are
     * safe, the shorter ordered segment wins; ties prefer the outbound
     * endpoint.</p>
     */
    public static int chooseDirection(
        boolean prefixSafe,
        int prefixSteps,
        boolean suffixSafe,
        int suffixSteps
    ) {
        if (prefixSteps < 0 || suffixSteps < 0) {
            throw new IllegalArgumentException(
                "Recovery step counts cannot be negative."
            );
        }
        if (!prefixSafe && !suffixSafe) {
            throw new IllegalArgumentException(
                "Recovery requires at least one safe endpoint."
            );
        }
        if (!prefixSafe) return 1;
        if (!suffixSafe) return -1;
        return prefixSteps <= suffixSteps ? -1 : 1;
    }

    public static int advance(int currentIndex, int direction, int targetCount) {
        if (targetCount <= 0
            || currentIndex < 0
            || currentIndex >= targetCount) {
            throw new IllegalArgumentException(
                "Recovery cursor must start on a route target."
            );
        }
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException(
                "Recovery direction must be -1 or 1."
            );
        }
        return currentIndex + direction;
    }

    public static boolean complete(int index, int targetCount) {
        if (targetCount <= 0) {
            throw new IllegalArgumentException(
                "Recovery route must contain a target."
            );
        }
        return index < 0 || index >= targetCount;
    }

    /**
     * Resolves the route support under the player's horizontal cell.
     *
     * <p>Server position corrections can report a transient or corrected Y
     * while leaving the player on the same ordered U cell. Recovery therefore
     * uses the same horizontal ownership rule as normal route movement. When
     * a helix contains more than one support in the same horizontal cell, the
     * support nearest the retained cursor wins deterministically.</p>
     */
    public static OptionalInt resolveHorizontalSupport(
        List<BlockPos> orderedSupports,
        int retainedIndex,
        double playerX,
        double playerZ
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        if (orderedSupports.isEmpty()
            || retainedIndex < 0
            || retainedIndex >= orderedSupports.size()
            || !Double.isFinite(playerX)
            || !Double.isFinite(playerZ)) {
            throw new IllegalArgumentException(
                "Invalid circular recovery cursor."
            );
        }

        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < orderedSupports.size(); index++) {
            BlockPos support = Objects.requireNonNull(
                orderedSupports.get(index),
                "ordered support"
            );
            if (playerX < support.getX()
                || playerX >= support.getX() + 1.0
                || playerZ < support.getZ()
                || playerZ >= support.getZ() + 1.0) {
                continue;
            }
            int distance = Math.abs(index - retainedIndex);
            if (distance < bestDistance
                || (distance == bestDistance && index < bestIndex)) {
                bestIndex = index;
                bestDistance = distance;
            }
        }
        return bestIndex < 0
            ? OptionalInt.empty()
            : OptionalInt.of(bestIndex);
    }
}
