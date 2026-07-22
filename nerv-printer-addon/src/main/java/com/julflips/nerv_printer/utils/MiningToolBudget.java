package com.julflips.nerv_printer.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure durability budgeting for mining-tool restocks.
 */
public final class MiningToolBudget {
    private MiningToolBudget() {
    }

    public static int toolsRequired(
        int rawUses,
        int unbreakingLevel,
        int maximumDamage,
        double durabilityBuffer,
        double workShare
    ) {
        if (rawUses < 0) throw new IllegalArgumentException("Raw uses cannot be negative.");
        if (unbreakingLevel < 0) {
            throw new IllegalArgumentException("Unbreaking level cannot be negative.");
        }
        if (maximumDamage <= 0) {
            throw new IllegalArgumentException("Maximum damage must be positive.");
        }
        if (durabilityBuffer < 0) {
            throw new IllegalArgumentException("Durability buffer cannot be negative.");
        }
        if (workShare < 0) throw new IllegalArgumentException("Work share cannot be negative.");

        double expectedDurability = rawUses * workShare / (unbreakingLevel + 1.0);
        return (int) Math.ceil(expectedDurability * (1.0 + durabilityBuffer) / maximumDamage);
    }

    /**
     * Returns the number of fresh tools still required before committing to a
     * traversal.
     *
     * <p>This is deliberately a worst-case calculation: every mined block is
     * charged as one durability use, regardless of Unbreaking. Existing tools
     * contribute only their summed actual remaining durability. The buffered
     * requirement is rounded up before existing durability is subtracted.</p>
     */
    public static int missingFreshToolsForTraversal(
        int rawRequiredUses,
        double durabilityBuffer,
        int maximumDamage,
        long actualRemainingDurability
    ) {
        if (rawRequiredUses < 0) {
            throw new IllegalArgumentException("Raw required uses cannot be negative.");
        }
        if (!Double.isFinite(durabilityBuffer) || durabilityBuffer < 0) {
            throw new IllegalArgumentException(
                "Durability buffer must be finite and non-negative."
            );
        }
        if (maximumDamage <= 0) {
            throw new IllegalArgumentException("Maximum damage must be positive.");
        }
        if (actualRemainingDurability < 0) {
            throw new IllegalArgumentException(
                "Actual remaining durability cannot be negative."
            );
        }

        long bufferedRequiredDurability;
        try {
            bufferedRequiredDurability = BigDecimal.valueOf(rawRequiredUses)
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(durabilityBuffer)))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                "Buffered durability requirement is too large.",
                exception
            );
        }

        if (actualRemainingDurability >= bufferedRequiredDurability) return 0;
        long missingDurability =
            bufferedRequiredDurability - actualRemainingDurability;
        long missingTools = Math.ceilDiv(missingDurability, (long) maximumDamage);
        if (missingTools > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Required fresh tool count is too large.");
        }
        return (int) missingTools;
    }
}
