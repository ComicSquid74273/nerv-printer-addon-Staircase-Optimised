package com.julflips.nerv_printer.utils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Captures metadata attached to two inventory slots and applies the metadata
 * exchange only after the corresponding server-authoritative stack swap is
 * observed.
 */
public final class InventorySlotMetadataSwap {
    private InventorySlotMetadataSwap() {
    }

    public static <T> Captured<T> capture(
        int sourceSlot,
        int targetSlot,
        Map<Integer, T> slotValues,
        Set<Integer> reservedSlots
    ) {
        if (sourceSlot < 0 || targetSlot < 0 || sourceSlot == targetSlot) {
            throw new IllegalArgumentException(
                "Metadata swap slots must be distinct and nonnegative."
            );
        }
        Objects.requireNonNull(slotValues, "slotValues");
        Objects.requireNonNull(reservedSlots, "reservedSlots");
        return new Captured<>(
            sourceSlot,
            targetSlot,
            slotValues.get(sourceSlot),
            slotValues.get(targetSlot),
            reservedSlots.contains(sourceSlot),
            reservedSlots.contains(targetSlot)
        );
    }

    /**
     * Applying a captured exchange is idempotent: it writes the captured
     * post-swap state rather than swapping whatever happens to be present at
     * application time.
     */
    public record Captured<T>(
        int sourceSlot,
        int targetSlot,
        T sourceValue,
        T targetValue,
        boolean sourceReserved,
        boolean targetReserved
    ) {
        public Captured {
            if (sourceSlot < 0
                || targetSlot < 0
                || sourceSlot == targetSlot) {
                throw new IllegalArgumentException(
                    "Metadata swap slots must be distinct and nonnegative."
                );
            }
        }

        public void applyTo(
            Map<Integer, T> slotValues,
            Set<Integer> reservedSlots
        ) {
            Objects.requireNonNull(slotValues, "slotValues");
            Objects.requireNonNull(reservedSlots, "reservedSlots");
            write(slotValues, sourceSlot, targetValue);
            write(slotValues, targetSlot, sourceValue);
            write(reservedSlots, sourceSlot, targetReserved);
            write(reservedSlots, targetSlot, sourceReserved);
        }

        private static <T> void write(
            Map<Integer, T> values,
            int slot,
            T value
        ) {
            if (value == null) {
                values.remove(slot);
            } else {
                values.put(slot, value);
            }
        }

        private static void write(
            Set<Integer> values,
            int slot,
            boolean present
        ) {
            if (present) {
                values.add(slot);
            } else {
                values.remove(slot);
            }
        }
    }
}
