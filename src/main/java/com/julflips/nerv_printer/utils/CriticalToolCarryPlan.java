package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plans the small tool inventory carried while building.
 *
 * <p>Exactly one compatible stack is required for every requested tool item.
 * Any tool above the critical durability threshold remains in inventory.
 * Tools at or below the threshold are routed to used-tool storage and do not
 * satisfy the carry requirement.</p>
 */
public final class CriticalToolCarryPlan {
    private CriticalToolCarryPlan() {
    }

    public record ToolStack<K>(
        int slot,
        K item,
        int remainingDurability,
        boolean compatible
    ) {
        public ToolStack {
            if (slot < 0 || remainingDurability < 0) {
                throw new IllegalArgumentException(
                    "Invalid critical tool stack."
                );
            }
            Objects.requireNonNull(item, "item");
        }
    }

    public record Result<K>(
        Map<K, Integer> requiredItemCounts,
        Set<Integer> requiredKeepSlots,
        Set<Integer> keepSlots,
        Set<Integer> usedToolSlots
    ) {
        public Result {
            requiredItemCounts = Collections.unmodifiableMap(
                new LinkedHashMap<>(requiredItemCounts)
            );
            requiredKeepSlots = immutableSortedSet(requiredKeepSlots);
            keepSlots = immutableSortedSet(keepSlots);
            usedToolSlots = immutableSortedSet(usedToolSlots);

            if (!keepSlots.containsAll(requiredKeepSlots)) {
                throw new IllegalArgumentException(
                    "Required tool slots must also be retained."
                );
            }
            LinkedHashSet<Integer> overlap =
                new LinkedHashSet<>(keepSlots);
            overlap.retainAll(usedToolSlots);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                    "A tool slot cannot be both kept and deposited."
                );
            }
        }
    }

    public static <K> Result<K> plan(
        Set<? extends K> requiredItems,
        List<ToolStack<K>> inventoryTools,
        int criticalDurability
    ) {
        Objects.requireNonNull(requiredItems, "requiredItems");
        Objects.requireNonNull(inventoryTools, "inventoryTools");
        if (criticalDurability < 0) {
            throw new IllegalArgumentException(
                "Critical durability cannot be negative."
            );
        }

        LinkedHashMap<K, Integer> requiredCounts =
            new LinkedHashMap<>();
        for (K item : requiredItems) {
            requiredCounts.put(
                Objects.requireNonNull(item, "required item"),
                1
            );
        }

        LinkedHashSet<Integer> encountered = new LinkedHashSet<>();
        LinkedHashSet<Integer> keep = new LinkedHashSet<>();
        LinkedHashSet<Integer> used = new LinkedHashSet<>();
        LinkedHashMap<K, ArrayList<ToolStack<K>>> candidates =
            new LinkedHashMap<>();
        for (ToolStack<K> tool : inventoryTools) {
            Objects.requireNonNull(tool, "inventory tool");
            if (!encountered.add(tool.slot())) {
                throw new IllegalArgumentException(
                    "Inventory tool slot is duplicated: " + tool.slot()
                );
            }
            if (tool.remainingDurability() <= criticalDurability) {
                used.add(tool.slot());
                continue;
            }

            keep.add(tool.slot());
            if (tool.compatible()
                && requiredCounts.containsKey(tool.item())) {
                candidates.computeIfAbsent(
                    tool.item(),
                    ignored -> new ArrayList<>()
                ).add(tool);
            }
        }

        Comparator<ToolStack<K>> bestFirst =
            Comparator
                .comparingInt(
                    (ToolStack<K> tool) ->
                        tool.remainingDurability()
                )
                .reversed()
                .thenComparingInt(ToolStack::slot);
        LinkedHashSet<Integer> requiredKeep = new LinkedHashSet<>();
        for (K item : requiredCounts.keySet()) {
            ArrayList<ToolStack<K>> itemCandidates =
                candidates.get(item);
            if (itemCandidates == null || itemCandidates.isEmpty()) {
                continue;
            }
            itemCandidates.sort(bestFirst);
            requiredKeep.add(itemCandidates.getFirst().slot());
        }

        return new Result<>(
            requiredCounts,
            requiredKeep,
            keep,
            used
        );
    }

    private static Set<Integer> immutableSortedSet(
        Set<Integer> source
    ) {
        Objects.requireNonNull(source, "source");
        ArrayList<Integer> sorted = new ArrayList<>(source);
        Collections.sort(sorted);
        return Collections.unmodifiableSet(
            new LinkedHashSet<>(sorted)
        );
    }
}
