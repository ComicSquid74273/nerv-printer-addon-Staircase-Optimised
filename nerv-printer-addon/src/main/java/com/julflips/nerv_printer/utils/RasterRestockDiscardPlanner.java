package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure whole-stack disposal planning for a Boat Raster restock trip. */
public final class RasterRestockDiscardPlanner {
    private RasterRestockDiscardPlanner() {
    }

    public record Slot<T>(T item, int count) {
        public Slot {
            if (count < 0) {
                throw new IllegalArgumentException("Slot count cannot be negative.");
            }
        }

        public static <T> Slot<T> empty() {
            return new Slot<>(null, 0);
        }
    }

    /**
     * Selects complete inventory stacks that may be discarded without taking
     * any managed material below its protected count. Items outside
     * {@code managedMaterials} are never selected.
     */
    public static <T> List<Integer> selectSlots(
        List<Slot<T>> inventory,
        Collection<T> managedMaterials,
        Map<T, Integer> protectedCounts
    ) {
        Set<T> managed = new LinkedHashSet<>(managedMaterials);
        HashMap<T, Integer> remaining = new HashMap<>();
        for (Slot<T> slot : inventory) {
            if (slot.item() != null && managed.contains(slot.item())) {
                remaining.merge(slot.item(), slot.count(), Integer::sum);
            }
        }
        for (Map.Entry<T, Integer> entry : protectedCounts.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(
                    "Protected material count cannot be negative."
                );
            }
        }

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            Slot<T> stack = inventory.get(slot);
            if (stack.item() == null
                || stack.count() == 0
                || !managed.contains(stack.item())) {
                continue;
            }
            candidates.add(slot);
        }
        candidates.sort((left, right) -> {
            Slot<T> a = inventory.get(left);
            Slot<T> b = inventory.get(right);
            int aSurplus = remaining.getOrDefault(a.item(), 0)
                - protectedCounts.getOrDefault(a.item(), 0);
            int bSurplus = remaining.getOrDefault(b.item(), 0)
                - protectedCounts.getOrDefault(b.item(), 0);
            int bySurplus = Integer.compare(bSurplus, aSurplus);
            if (bySurplus != 0) return bySurplus;
            int byStack = Integer.compare(b.count(), a.count());
            return byStack != 0 ? byStack : Integer.compare(left, right);
        });

        ArrayList<Integer> selected = new ArrayList<>();
        for (int slot : candidates) {
            Slot<T> stack = inventory.get(slot);
            int total = remaining.getOrDefault(stack.item(), 0);
            int protectedCount = protectedCounts.getOrDefault(stack.item(), 0);
            if (total - stack.count() < protectedCount) continue;
            selected.add(slot);
            remaining.put(stack.item(), total - stack.count());
        }
        return List.copyOf(selected);
    }
}
