package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Immutable demand for restocking one compatible item into player inventory.
 *
 * <p>The target is an absolute compatible-player-inventory count captured
 * when the demand is created. Reconciliation compares that unchanged target
 * with a later authoritative compatible count, so unrelated partial transfer
 * sizes do not accumulate rounding or prediction errors.</p>
 *
 * @param item item identity used for compatibility checks
 * @param targetCompatiblePlayerCount absolute compatible count to reach
 * @param remainingAmount amount still required to reach the target
 */
public record RestockDemand<K>(
    K item,
    int targetCompatiblePlayerCount,
    int remainingAmount
) {
    public RestockDemand {
        Objects.requireNonNull(item, "item");
        if (targetCompatiblePlayerCount < 0) {
            throw new IllegalArgumentException(
                "Target compatible player count cannot be negative."
            );
        }
        if (remainingAmount < 0) {
            throw new IllegalArgumentException(
                "Remaining restock amount cannot be negative."
            );
        }
        if (remainingAmount > targetCompatiblePlayerCount) {
            throw new IllegalArgumentException(
                "Remaining restock amount cannot exceed the absolute target."
            );
        }
    }

    /**
     * Captures a demand from the compatible count already on hand and the
     * amount missing from the current inventory plan.
     */
    public static <K> RestockDemand<K> fromOnHandAndMissing(
        K item,
        int compatibleOnHand,
        int missingAmount
    ) {
        Objects.requireNonNull(item, "item");
        if (compatibleOnHand < 0) {
            throw new IllegalArgumentException(
                "Compatible on-hand count cannot be negative."
            );
        }
        if (missingAmount < 0) {
            throw new IllegalArgumentException(
                "Missing restock amount cannot be negative."
            );
        }

        long target = (long) compatibleOnHand + missingAmount;
        if (target > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Absolute restock target exceeds the supported item count."
            );
        }
        return new RestockDemand<>(item, (int) target, missingAmount);
    }

    /**
     * Returns a new demand reconciled against an authoritative compatible
     * player-inventory count. Counts at or above the target complete it.
     */
    public RestockDemand<K> reconcile(
        int confirmedCompatiblePlayerCount
    ) {
        if (confirmedCompatiblePlayerCount < 0) {
            throw new IllegalArgumentException(
                "Confirmed compatible player count cannot be negative."
            );
        }
        int reconciledRemaining =
            confirmedCompatiblePlayerCount >= targetCompatiblePlayerCount
                ? 0
                : targetCompatiblePlayerCount
                    - confirmedCompatiblePlayerCount;
        return new RestockDemand<>(
            item,
            targetCompatiblePlayerCount,
            reconciledRemaining
        );
    }

    /**
     * Returns how many source stacks are still needed at the supplied maximum
     * stack size.
     */
    public int remainingStacks(int maximumStackSize) {
        if (maximumStackSize <= 0) {
            throw new IllegalArgumentException(
                "Maximum stack size must be positive."
            );
        }
        return Math.ceilDiv(remainingAmount, maximumStackSize);
    }
}
