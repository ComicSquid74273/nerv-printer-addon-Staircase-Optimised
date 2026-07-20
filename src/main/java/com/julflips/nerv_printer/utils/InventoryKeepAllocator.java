package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure aggregate allocator for deciding which inventory stacks to keep.
 *
 * <p>Stacks are grouped by material. Within a material, the largest stacks
 * are retained first until their aggregate count covers the requested demand.
 * Equal-sized stacks are resolved by ascending slot number. This minimizes
 * occupied slots without discarding fragmented inventory that is sufficient
 * only in aggregate.</p>
 */
public final class InventoryKeepAllocator {
    private InventoryKeepAllocator() {
    }

    public record StackEntry<K>(
        int slot,
        K material,
        int count,
        long quality
    ) {
        public StackEntry(int slot, K material, int count) {
            this(slot, material, count, 0L);
        }

        public StackEntry {
            if (slot < 0) {
                throw new IllegalArgumentException(
                    "Inventory slot cannot be negative."
                );
            }
            Objects.requireNonNull(material, "material");
            if (count <= 0) {
                throw new IllegalArgumentException(
                    "Stack count must be positive."
                );
            }
            if (quality < 0L) {
                throw new IllegalArgumentException(
                    "Stack quality cannot be negative."
                );
            }
        }
    }

    public record Allocation<K>(
        List<Integer> keptSlots,
        List<Integer> dumpSlots,
        Map<K, Integer> keptCounts,
        Map<K, Integer> missingDemand
    ) {
        public Allocation {
            keptSlots = List.copyOf(keptSlots);
            dumpSlots = List.copyOf(dumpSlots);
            keptCounts = immutableLinkedMap(keptCounts);
            missingDemand = immutableLinkedMap(missingDemand);
        }
    }

    public static <K> Allocation<K> allocate(
        Map<? extends K, Integer> requiredDemand,
        List<StackEntry<K>> inventoryStacks
    ) {
        Objects.requireNonNull(requiredDemand, "requiredDemand");
        Objects.requireNonNull(inventoryStacks, "inventoryStacks");

        LinkedHashMap<K, Integer> demand = copyDemand(requiredDemand);
        LinkedHashMap<K, ArrayList<StackEntry<K>>> stacksByMaterial =
            new LinkedHashMap<>();
        Set<Integer> encounteredSlots = new HashSet<>();
        for (StackEntry<K> stack : inventoryStacks) {
            Objects.requireNonNull(stack, "inventory stack");
            if (!encounteredSlots.add(stack.slot())) {
                throw new IllegalArgumentException(
                    "Inventory slot is listed more than once: " + stack.slot()
                );
            }
            stacksByMaterial.computeIfAbsent(
                stack.material(),
                ignored -> new ArrayList<>()
            ).add(stack);
        }

        HashSet<Integer> keptSlotSet = new HashSet<>();
        LinkedHashMap<K, Integer> keptCounts = new LinkedHashMap<>();
        Comparator<StackEntry<K>> keepOrder =
            Comparator.<StackEntry<K>>comparingInt(StackEntry::count)
                .reversed()
                .thenComparing(
                    Comparator.comparingLong(
                        (StackEntry<K> stack) -> stack.quality()
                    ).reversed()
                )
                .thenComparingInt(StackEntry::slot);

        for (Map.Entry<K, Integer> requirement : demand.entrySet()) {
            int required = requirement.getValue();
            if (required == 0) continue;

            ArrayList<StackEntry<K>> candidates =
                stacksByMaterial.get(requirement.getKey());
            if (candidates == null) continue;
            candidates.sort(keepOrder);

            int kept = 0;
            for (StackEntry<K> candidate : candidates) {
                if (kept >= required) break;
                kept = Math.addExact(kept, candidate.count());
                keptSlotSet.add(candidate.slot());
            }
            if (kept > 0) keptCounts.put(requirement.getKey(), kept);
        }

        ArrayList<Integer> keptSlots = new ArrayList<>(keptSlotSet);
        ArrayList<Integer> dumpSlots = new ArrayList<>();
        for (StackEntry<K> stack : inventoryStacks) {
            if (!keptSlotSet.contains(stack.slot())) {
                dumpSlots.add(stack.slot());
            }
        }
        Collections.sort(keptSlots);
        Collections.sort(dumpSlots);

        LinkedHashMap<K, Integer> missingDemand = new LinkedHashMap<>();
        for (Map.Entry<K, Integer> requirement : demand.entrySet()) {
            missingDemand.put(
                requirement.getKey(),
                Math.max(
                    0,
                    requirement.getValue()
                        - keptCounts.getOrDefault(requirement.getKey(), 0)
                )
            );
        }

        return new Allocation<>(
            keptSlots,
            dumpSlots,
            keptCounts,
            missingDemand
        );
    }

    /**
     * Allocates existing stacks and fresh restock demand by physical slot cost.
     *
     * <p>For each required material this minimizes:</p>
     *
     * <pre>
     * kept existing slots + ceil(missing item count / fresh max stack size)
     * </pre>
     *
     * <p>When multiple choices occupy the same number of slots, the allocator
     * retains the greatest existing item count, then the greatest aggregate
     * stack quality, then the lowest inventory slots. This deliberately may
     * dump fragments when replacing them with one fresh full stack occupies
     * fewer slots. The two-argument {@link #allocate(Map, List)} method keeps
     * its original aggregate-retention behavior.</p>
     */
    public static <K> Allocation<K> allocate(
        Map<? extends K, Integer> requiredDemand,
        Map<? extends K, Integer> maxStackSizes,
        List<StackEntry<K>> inventoryStacks
    ) {
        Objects.requireNonNull(requiredDemand, "requiredDemand");
        Objects.requireNonNull(maxStackSizes, "maxStackSizes");
        Objects.requireNonNull(inventoryStacks, "inventoryStacks");

        LinkedHashMap<K, Integer> demand = copyDemand(requiredDemand);
        LinkedHashMap<K, Integer> freshStackSizes =
            copyMaxStackSizes(maxStackSizes);
        for (K material : demand.keySet()) {
            if (!freshStackSizes.containsKey(material)) {
                throw new IllegalArgumentException(
                    "No maximum stack size was provided for required material: "
                        + material
                );
            }
        }

        LinkedHashMap<K, ArrayList<StackEntry<K>>> stacksByMaterial =
            new LinkedHashMap<>();
        Set<Integer> encounteredSlots = new HashSet<>();
        for (StackEntry<K> stack : inventoryStacks) {
            Objects.requireNonNull(stack, "inventory stack");
            if (!encounteredSlots.add(stack.slot())) {
                throw new IllegalArgumentException(
                    "Inventory slot is listed more than once: " + stack.slot()
                );
            }
            stacksByMaterial.computeIfAbsent(
                stack.material(),
                ignored -> new ArrayList<>()
            ).add(stack);
        }

        Comparator<StackEntry<K>> keepOrder =
            Comparator.<StackEntry<K>>comparingInt(StackEntry::count)
                .reversed()
                .thenComparing(
                    Comparator.comparingLong(
                        (StackEntry<K> stack) -> stack.quality()
                    ).reversed()
                )
                .thenComparingInt(StackEntry::slot);

        HashSet<Integer> keptSlotSet = new HashSet<>();
        LinkedHashMap<K, Integer> keptCounts = new LinkedHashMap<>();
        LinkedHashMap<K, Integer> missingDemand = new LinkedHashMap<>();

        for (Map.Entry<K, Integer> requirement : demand.entrySet()) {
            K material = requirement.getKey();
            int required = requirement.getValue();
            int maxStackSize = freshStackSizes.get(material);
            ArrayList<StackEntry<K>> candidates =
                stacksByMaterial.get(material);
            if (candidates == null || candidates.isEmpty() || required == 0) {
                missingDemand.put(material, required);
                continue;
            }
            candidates.sort(keepOrder);

            int bestOccupiedSlots = ceilingDivision(required, maxStackSize);
            int bestKeepCount = 0;
            int bestKeptItems = 0;
            long bestQuality = 0L;
            int runningItems = 0;
            long runningQuality = 0L;

            for (int index = 0; index < candidates.size(); index++) {
                StackEntry<K> candidate = candidates.get(index);
                runningItems = Math.addExact(
                    runningItems,
                    candidate.count()
                );
                runningQuality = Math.addExact(
                    runningQuality,
                    candidate.quality()
                );
                int keepCount = index + 1;
                int missing = Math.max(0, required - runningItems);
                int occupiedSlots = Math.addExact(
                    keepCount,
                    ceilingDivision(missing, maxStackSize)
                );

                if (occupiedSlots < bestOccupiedSlots
                    || (occupiedSlots == bestOccupiedSlots
                        && runningItems > bestKeptItems)
                    || (occupiedSlots == bestOccupiedSlots
                        && runningItems == bestKeptItems
                        && runningQuality > bestQuality)) {
                    bestOccupiedSlots = occupiedSlots;
                    bestKeepCount = keepCount;
                    bestKeptItems = runningItems;
                    bestQuality = runningQuality;
                }
            }

            for (int index = 0; index < bestKeepCount; index++) {
                keptSlotSet.add(candidates.get(index).slot());
            }
            if (bestKeptItems > 0) {
                keptCounts.put(material, bestKeptItems);
            }
            missingDemand.put(
                material,
                Math.max(0, required - bestKeptItems)
            );
        }

        ArrayList<Integer> keptSlots = new ArrayList<>(keptSlotSet);
        ArrayList<Integer> dumpSlots = new ArrayList<>();
        for (StackEntry<K> stack : inventoryStacks) {
            if (!keptSlotSet.contains(stack.slot())) {
                dumpSlots.add(stack.slot());
            }
        }
        Collections.sort(keptSlots);
        Collections.sort(dumpSlots);

        return new Allocation<>(
            keptSlots,
            dumpSlots,
            keptCounts,
            missingDemand
        );
    }

    private static <K> LinkedHashMap<K, Integer> copyDemand(
        Map<? extends K, Integer> requiredDemand
    ) {
        LinkedHashMap<K, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<? extends K, Integer> entry
            : requiredDemand.entrySet()) {
            K material = Objects.requireNonNull(
                entry.getKey(),
                "required material"
            );
            Integer amount = Objects.requireNonNull(
                entry.getValue(),
                "required amount"
            );
            if (amount < 0) {
                throw new IllegalArgumentException(
                    "Required amount cannot be negative."
                );
            }
            result.put(material, amount);
        }
        return result;
    }

    private static <K> LinkedHashMap<K, Integer> copyMaxStackSizes(
        Map<? extends K, Integer> maxStackSizes
    ) {
        LinkedHashMap<K, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<? extends K, Integer> entry
            : maxStackSizes.entrySet()) {
            K material = Objects.requireNonNull(
                entry.getKey(),
                "stack-size material"
            );
            Integer maxStackSize = Objects.requireNonNull(
                entry.getValue(),
                "maximum stack size"
            );
            if (maxStackSize <= 0) {
                throw new IllegalArgumentException(
                    "Maximum stack size must be positive."
                );
            }
            result.put(material, maxStackSize);
        }
        return result;
    }

    private static int ceilingDivision(int dividend, int divisor) {
        if (dividend == 0) return 0;
        return 1 + (dividend - 1) / divisor;
    }

    private static <K> Map<K, Integer> immutableLinkedMap(
        Map<K, Integer> source
    ) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
