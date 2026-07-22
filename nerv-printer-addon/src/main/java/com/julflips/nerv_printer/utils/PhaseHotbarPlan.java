package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic hotbar planning for build and teardown phases.
 */
public final class PhaseHotbarPlan {
    private PhaseHotbarPlan() {
    }

    public record BuildLayout(
        List<Integer> materialSlots,
        int toolSlot
    ) {
        public BuildLayout {
            materialSlots = List.copyOf(materialSlots);
            if (toolSlot < 0 || toolSlot > 8) {
                throw new IllegalArgumentException(
                    "Tool slot must be in the hotbar."
                );
            }
            if (materialSlots.contains(toolSlot)) {
                throw new IllegalArgumentException(
                    "The tool slot cannot also hold build materials."
                );
            }
        }
    }

    public static BuildLayout buildLayout(
        List<Integer> managedHotbarSlots,
        int materialSlotCount
    ) {
        Objects.requireNonNull(
            managedHotbarSlots,
            "managedHotbarSlots"
        );
        if (materialSlotCount < 1) {
            throw new IllegalArgumentException(
                "At least one material slot is required."
            );
        }

        ArrayList<Integer> sorted =
            sortedDistinctHotbarSlots(managedHotbarSlots);
        if (sorted.size() < materialSlotCount + 1) {
            throw new IllegalArgumentException(
                "The hotbar does not have enough managed slots for "
                    + materialSlotCount + " materials and one tool."
            );
        }
        return new BuildLayout(
            sorted.subList(0, materialSlotCount),
            sorted.get(materialSlotCount)
        );
    }

    /**
     * Converts ordered block uses into the ordered stack units worth
     * preloading. A material adds a new stack unit on uses 1, max+1,
     * 2*max+1, and so on.
     */
    public static <K> List<K> orderedStackUnits(
        List<? extends K> orderedPrimaryUses,
        List<? extends K> orderedOptionalUses,
        Map<? super K, Integer> maximumStackSizes,
        int maximumUnits
    ) {
        Objects.requireNonNull(
            orderedPrimaryUses,
            "orderedPrimaryUses"
        );
        Objects.requireNonNull(
            orderedOptionalUses,
            "orderedOptionalUses"
        );
        Objects.requireNonNull(
            maximumStackSizes,
            "maximumStackSizes"
        );
        if (maximumUnits < 0) {
            throw new IllegalArgumentException(
                "Maximum stack units cannot be negative."
            );
        }

        ArrayList<K> result = new ArrayList<>();
        HashMap<K, Integer> seen = new HashMap<>();
        appendStackUnits(
            orderedPrimaryUses,
            maximumStackSizes,
            maximumUnits,
            seen,
            result
        );
        if (result.size() < maximumUnits) {
            appendStackUnits(
                orderedOptionalUses,
                maximumStackSizes,
                maximumUnits,
                seen,
                result
            );
        }
        return List.copyOf(result);
    }

    /**
     * Assigns required items to hotbar slots while preserving already-correct
     * stacks wherever possible. Duplicate required items are supported.
     */
    public static <K> Map<Integer, K> assignRequiredItems(
        List<Integer> candidateSlots,
        List<? extends K> requiredItems,
        Map<Integer, ? extends K> currentItems
    ) {
        Objects.requireNonNull(candidateSlots, "candidateSlots");
        Objects.requireNonNull(requiredItems, "requiredItems");
        Objects.requireNonNull(currentItems, "currentItems");

        ArrayList<Integer> slots =
            sortedDistinctHotbarSlots(candidateSlots);
        if (requiredItems.size() > slots.size()) {
            throw new IllegalArgumentException(
                "Required hotbar items exceed candidate slots."
            );
        }

        ArrayList<K> remaining = new ArrayList<>();
        for (K item : requiredItems) {
            remaining.add(
                Objects.requireNonNull(item, "required item")
            );
        }
        LinkedHashMap<Integer, K> assignments =
            new LinkedHashMap<>();
        LinkedHashSet<Integer> unusedSlots =
            new LinkedHashSet<>(slots);

        for (int slot : slots) {
            K current = currentItems.get(slot);
            int match = remaining.indexOf(current);
            if (match < 0) continue;
            assignments.put(slot, remaining.remove(match));
            unusedSlots.remove(slot);
        }
        for (K item : remaining) {
            int slot = unusedSlots.removeFirst();
            assignments.put(slot, item);
        }

        ArrayList<Integer> orderedAssignedSlots =
            new ArrayList<>(assignments.keySet());
        Collections.sort(orderedAssignedSlots);
        LinkedHashMap<Integer, K> ordered =
            new LinkedHashMap<>();
        for (int slot : orderedAssignedSlots) {
            ordered.put(slot, assignments.get(slot));
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static <K> void appendStackUnits(
        List<? extends K> orderedUses,
        Map<? super K, Integer> maximumStackSizes,
        int maximumUnits,
        Map<K, Integer> seen,
        List<K> result
    ) {
        for (K item : orderedUses) {
            K requiredItem =
                Objects.requireNonNull(item, "ordered item use");
            Integer maximumStackSize =
                maximumStackSizes.get(requiredItem);
            if (maximumStackSize == null
                || maximumStackSize < 1) {
                throw new IllegalArgumentException(
                    "Missing positive stack size for item: "
                        + requiredItem
                );
            }
            int previous = seen.getOrDefault(requiredItem, 0);
            int next = Math.addExact(previous, 1);
            seen.put(requiredItem, next);
            if (previous % maximumStackSize == 0) {
                result.add(requiredItem);
                if (result.size() == maximumUnits) return;
            }
        }
    }

    private static ArrayList<Integer> sortedDistinctHotbarSlots(
        List<Integer> source
    ) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer slot : source) {
            if (slot == null || slot < 0 || slot > 8) {
                throw new IllegalArgumentException(
                    "Managed slot must be in the hotbar."
                );
            }
            if (!unique.add(slot)) {
                throw new IllegalArgumentException(
                    "Managed hotbar slot is duplicated: " + slot
                );
            }
        }
        ArrayList<Integer> sorted = new ArrayList<>(unique);
        Collections.sort(sorted);
        return sorted;
    }
}
