package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Pure route-order packing for one inventory-sized Boat Raster run. */
public final class RasterInventoryRunPlanner {
    private RasterInventoryRunPlanner() {
    }

    public record Slot<T>(T item, int count, int maximumCount) {
        public Slot {
            if (count < 0 || maximumCount < count) {
                throw new IllegalArgumentException("Invalid inventory slot count.");
            }
        }

        public static <T> Slot<T> empty() {
            return new Slot<>(null, 0, 0);
        }
    }

    public record Plan<T>(
        Map<T, Integer> additions,
        int coveredTargets,
        Set<T> missingMinimumMaterials
    ) {
        public Plan {
            additions = Collections.unmodifiableMap(new LinkedHashMap<>(additions));
            missingMinimumMaterials = Collections.unmodifiableSet(
                new LinkedHashSet<>(missingMinimumMaterials)
            );
            if (coveredTargets < 0) {
                throw new IllegalArgumentException("Covered target count cannot be negative.");
            }
        }

        public Plan(Map<T, Integer> additions, int coveredTargets) {
            this(additions, coveredTargets, Set.of());
        }
    }

    public record PresencePlan<T>(
        Map<T, Integer> desiredCounts,
        Set<T> missingMaterials
    ) {
        public PresencePlan {
            desiredCounts = Collections.unmodifiableMap(
                new LinkedHashMap<>(desiredCounts)
            );
            missingMaterials = Collections.unmodifiableSet(
                new LinkedHashSet<>(missingMaterials)
            );
        }

        public boolean ready() {
            return missingMaterials.isEmpty();
        }
    }

    /** Requires presence, not full-run quantity, for initial deployment. */
    public static <T> PresencePlan<T> minimumPresence(
        Collection<T> requiredMaterials,
        Map<T, Integer> carriedCounts
    ) {
        LinkedHashMap<T, Integer> desired = new LinkedHashMap<>();
        LinkedHashSet<T> missing = new LinkedHashSet<>();
        for (T material : requiredMaterials) {
            if (material == null) {
                throw new IllegalArgumentException("Required material cannot be null.");
            }
            desired.put(material, 1);
            int carried = carriedCounts.getOrDefault(material, 0);
            if (carried < 0) {
                throw new IllegalArgumentException("Carried counts cannot be negative.");
            }
            if (carried == 0) missing.add(material);
        }
        return new PresencePlan<>(desired, missing);
    }

    public static <T> Plan<T> create(
        List<Slot<T>> inventory,
        List<T> unfinishedRoute,
        ToIntFunction<T> maximumStackSize
    ) {
        return create(inventory, unfinishedRoute, maximumStackSize, 0);
    }

    public static <T> Plan<T> create(
        List<Slot<T>> inventory,
        List<T> unfinishedRoute,
        ToIntFunction<T> maximumStackSize,
        int reservedEmptySlots
    ) {
        return create(
            inventory,
            unfinishedRoute,
            maximumStackSize,
            reservedEmptySlots,
            Set.of()
        );
    }

    public static <T> Plan<T> create(
        List<Slot<T>> inventory,
        List<T> unfinishedRoute,
        ToIntFunction<T> maximumStackSize,
        int reservedEmptySlots,
        Collection<T> minimumPresentMaterials
    ) {
        if (reservedEmptySlots < 0) {
            throw new IllegalArgumentException("Reserved empty slots cannot be negative.");
        }
        ArrayList<T> simulatedItems = new ArrayList<>(inventory.size());
        int[] simulatedCounts = new int[inventory.size()];
        boolean[] reserved = new boolean[inventory.size()];
        LinkedHashMap<T, Integer> carried = new LinkedHashMap<>();
        LinkedHashSet<T> routeMaterials = new LinkedHashSet<>(unfinishedRoute);
        routeMaterials.addAll(minimumPresentMaterials);
        for (int slot = 0; slot < inventory.size(); slot++) {
            Slot<T> current = inventory.get(slot);
            simulatedItems.add(current.item());
            simulatedCounts[slot] = current.count();
            if (current.item() != null && routeMaterials.contains(current.item())) {
                carried.merge(current.item(), current.count(), Integer::sum);
            }
        }
        int remainingReservations = reservedEmptySlots;
        for (int slot = inventory.size() - 1;
             slot >= 0 && remainingReservations > 0;
             slot--) {
            if (simulatedItems.get(slot) == null) {
                reserved[slot] = true;
                remainingReservations--;
            }
        }

        LinkedHashMap<T, Integer> additions = new LinkedHashMap<>();
        LinkedHashSet<T> missingMinimumMaterials = new LinkedHashSet<>();
        for (T material : new LinkedHashSet<>(minimumPresentMaterials)) {
            if (material == null) {
                throw new IllegalArgumentException(
                    "Minimum-present material cannot be null."
                );
            }
            if (carried.getOrDefault(material, 0) > 0) continue;
            int maximum = maximumStackSize.applyAsInt(material);
            if (maximum <= 0) {
                throw new IllegalArgumentException(
                    "Minimum-present material has no usable stack capacity."
                );
            }
            int targetSlot = -1;
            for (int slot = 0; slot < simulatedItems.size(); slot++) {
                if (simulatedItems.get(slot) == null && !reserved[slot]) {
                    targetSlot = slot;
                    break;
                }
            }
            if (targetSlot < 0) {
                missingMinimumMaterials.add(material);
                continue;
            }
            simulatedItems.set(targetSlot, material);
            simulatedCounts[targetSlot] = maximum;
            additions.merge(material, maximum, Integer::sum);
            carried.merge(material, maximum, Integer::sum);
        }
        int covered = 0;
        for (T material : unfinishedRoute) {
            int alreadyCarried = carried.getOrDefault(material, 0);
            if (alreadyCarried > 0) {
                carried.put(material, alreadyCarried - 1);
                covered++;
                continue;
            }

            int maximum = maximumStackSize.applyAsInt(material);
            if (maximum <= 0) {
                throw new IllegalArgumentException("Material has no usable stack capacity.");
            }
            int availableCapacity = 0;
            for (int slot = 0; slot < simulatedItems.size(); slot++) {
                if (material.equals(simulatedItems.get(slot))
                    && simulatedCounts[slot] < maximum) {
                    availableCapacity += maximum - simulatedCounts[slot];
                } else if (simulatedItems.get(slot) == null && !reserved[slot]) {
                    availableCapacity += maximum;
                }
            }
            if (availableCapacity < maximum) break;

            int toInsert = maximum;
            for (int slot = 0; slot < simulatedItems.size() && toInsert > 0; slot++) {
                if (!material.equals(simulatedItems.get(slot))) continue;
                int inserted = Math.min(
                    toInsert,
                    maximum - simulatedCounts[slot]
                );
                simulatedCounts[slot] += inserted;
                toInsert -= inserted;
            }
            for (int slot = 0; slot < simulatedItems.size() && toInsert > 0; slot++) {
                if (simulatedItems.get(slot) != null || reserved[slot]) continue;
                simulatedItems.set(slot, material);
                int inserted = Math.min(toInsert, maximum);
                simulatedCounts[slot] = inserted;
                toInsert -= inserted;
            }
            if (toInsert != 0) {
                throw new IllegalStateException("Inventory capacity simulation diverged.");
            }
            additions.merge(material, maximum, Integer::sum);
            carried.merge(material, maximum, Integer::sum);
            alreadyCarried = carried.getOrDefault(material, 0);
            if (alreadyCarried <= 0) {
                throw new IllegalStateException("Inserted material was not available to the route.");
            }
            carried.put(material, alreadyCarried - 1);
            covered++;
        }
        return new Plan<>(additions, covered, missingMinimumMaterials);
    }
}
