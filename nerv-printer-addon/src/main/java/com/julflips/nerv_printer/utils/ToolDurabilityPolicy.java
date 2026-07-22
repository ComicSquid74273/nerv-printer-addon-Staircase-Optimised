package com.julflips.nerv_printer.utils;

/** Shared percentage floor for reusable damageable tools. */
public final class ToolDurabilityPolicy {
    private ToolDurabilityPolicy() {
    }

    /**
     * Returns the first remaining-durability value which is reusable.
     * The final durability point is always reserved even when the configured
     * fraction is zero.
     */
    public static int minimumRemaining(
        int maximumDurability,
        double minimumRemainingFraction
    ) {
        if (maximumDurability <= 1) {
            throw new IllegalArgumentException(
                "Maximum tool durability must exceed one."
            );
        }
        if (!Double.isFinite(minimumRemainingFraction)
            || minimumRemainingFraction < 0.0
            || minimumRemainingFraction > 1.0) {
            throw new IllegalArgumentException(
                "Minimum remaining durability fraction must be between zero and one."
            );
        }
        return Math.max(
            2,
            (int) Math.ceil(
                maximumDurability * minimumRemainingFraction
            )
        );
    }

    /** Tools at the configured percentage remain reusable; only lower tools are replaced. */
    public static boolean isReusable(
        int remainingDurability,
        int maximumDurability,
        double minimumRemainingFraction
    ) {
        if (remainingDurability < 0
            || remainingDurability > maximumDurability) {
            throw new IllegalArgumentException(
                "Remaining durability must be between zero and the maximum."
            );
        }
        return remainingDurability >= minimumRemaining(
            maximumDurability,
            minimumRemainingFraction
        );
    }
}
