package com.julflips.nerv_printer.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compares one frozen inventory target with authoritative on-hand counts.
 */
public final class InventoryDemandSatisfaction {
    private InventoryDemandSatisfaction() {
    }

    public static <M> Map<M, Integer> missingAmounts(
        Map<M, Integer> required,
        Map<M, Integer> onHand
    ) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(onHand, "onHand");
        LinkedHashMap<M, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<M, Integer> entry :
            required.entrySet()) {
            M material =
                Objects.requireNonNull(entry.getKey(), "material");
            int target = Objects.requireNonNull(
                entry.getValue(),
                "required amount"
            );
            int available = onHand.getOrDefault(material, 0);
            if (target < 0 || available < 0) {
                throw new IllegalArgumentException(
                    "Inventory amounts cannot be negative."
                );
            }
            if (available < target) {
                missing.put(material, target - available);
            }
        }
        return Collections.unmodifiableMap(missing);
    }
}
