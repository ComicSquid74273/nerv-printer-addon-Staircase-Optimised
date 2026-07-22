package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;

/** Pure stack and slot arithmetic for the teardown scaffold reserve. */
public final class TeardownScaffoldMaterialPlan {
    private TeardownScaffoldMaterialPlan() {
    }

    public record Plan(
        int targetAmount,
        int onHandAmount,
        int missingAmount,
        int additionalSlotsRequired
    ) {
    }

    public static Plan create(
        int reserveStacks,
        int maximumStackSize,
        List<Integer> managedStackCounts
    ) {
        if (reserveStacks < 0 || maximumStackSize <= 0) {
            throw new IllegalArgumentException(
                "Scaffold reserve and stack size are invalid."
            );
        }
        Objects.requireNonNull(
            managedStackCounts,
            "managedStackCounts"
        );
        int target = Math.multiplyExact(
            reserveStacks,
            maximumStackSize
        );
        int onHand = 0;
        int mergeCapacity = 0;
        for (int count : managedStackCounts) {
            if (count <= 0 || count > maximumStackSize) {
                throw new IllegalArgumentException(
                    "Managed stack counts must be within stack bounds."
                );
            }
            onHand = Math.addExact(onHand, count);
            mergeCapacity = Math.addExact(
                mergeCapacity,
                maximumStackSize - count
            );
        }
        int missing = Math.max(0, target - onHand);
        int newSlotItems = Math.max(0, missing - mergeCapacity);
        int additionalSlots = Math.ceilDiv(
            newSlotItems,
            maximumStackSize
        );
        return new Plan(target, onHand, missing, additionalSlots);
    }
}
