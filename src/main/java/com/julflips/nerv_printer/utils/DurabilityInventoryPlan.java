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
 * Pure inventory plan for retaining damaged tools and supplementing them with
 * the minimum missing fresh tools required by a traversal.
 *
 * <p>For each item, actual stacks are considered from highest remaining
 * durability to lowest, with slot number as the deterministic tie-breaker.
 * The plan minimizes the total occupied slots across retained existing tools
 * and missing fresh tools. Equal-slot plans retain as many usable existing
 * tools as possible. Tiny or fragmented tools that would consume more total
 * slots than replacing them with fresh tools are therefore not retained.</p>
 */
public final class DurabilityInventoryPlan<K> {
    public record Requirement(int rawUses, int maximumDurability) {
        public Requirement {
            if (rawUses < 0) {
                throw new IllegalArgumentException(
                    "Raw uses cannot be negative."
                );
            }
            if (maximumDurability <= 0) {
                throw new IllegalArgumentException(
                    "Maximum durability must be positive."
                );
            }
        }
    }

    public record ToolStack<K>(
        int slot,
        K item,
        int remainingDurability
    ) {
        public ToolStack {
            if (slot < 0) {
                throw new IllegalArgumentException(
                    "Inventory slot cannot be negative."
                );
            }
            Objects.requireNonNull(item, "item");
            if (remainingDurability < 0) {
                throw new IllegalArgumentException(
                    "Remaining durability cannot be negative."
                );
            }
        }
    }

    private final List<Integer> keepSlots;
    private final Map<K, Integer> requiredItemCounts;
    private final Map<K, Integer> missingFreshCounts;

    private DurabilityInventoryPlan(
        List<Integer> keepSlots,
        Map<K, Integer> requiredItemCounts,
        Map<K, Integer> missingFreshCounts
    ) {
        this.keepSlots = List.copyOf(keepSlots);
        this.requiredItemCounts =
            immutableLinkedMap(requiredItemCounts);
        this.missingFreshCounts =
            immutableLinkedMap(missingFreshCounts);
    }

    /**
     * Builds a plan in the encounter order of {@code requirements}.
     */
    public static <K> DurabilityInventoryPlan<K> plan(
        Map<? extends K, Requirement> requirements,
        List<ToolStack<K>> inventoryTools,
        double durabilityBuffer
    ) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(inventoryTools, "inventoryTools");
        if (!Double.isFinite(durabilityBuffer) || durabilityBuffer < 0) {
            throw new IllegalArgumentException(
                "Durability buffer must be finite and non-negative."
            );
        }

        LinkedHashMap<K, Requirement> resolvedRequirements =
            copyRequirements(requirements);
        LinkedHashMap<K, ArrayList<ToolStack<K>>> toolsByItem =
            groupAndValidateTools(inventoryTools, resolvedRequirements);
        Comparator<ToolStack<K>> keepOrder =
            Comparator
                .<ToolStack<K>>comparingInt(
                    ToolStack::remainingDurability
                )
                .reversed()
                .thenComparingInt(ToolStack::slot);

        ArrayList<Integer> keepSlots = new ArrayList<>();
        LinkedHashMap<K, Integer> requiredItemCounts =
            new LinkedHashMap<>();
        LinkedHashMap<K, Integer> missingFreshCounts =
            new LinkedHashMap<>();

        for (Map.Entry<K, Requirement> entry
            : resolvedRequirements.entrySet()) {
            K item = entry.getKey();
            Requirement requirement = entry.getValue();
            ArrayList<ToolStack<K>> candidates =
                toolsByItem.getOrDefault(item, new ArrayList<>());
            candidates.sort(keepOrder);

            int selectedExisting = 0;
            int missingFresh =
                MiningToolBudget.missingFreshToolsForTraversal(
                    requirement.rawUses(),
                    durabilityBuffer,
                    requirement.maximumDurability(),
                    0L
                );
            int occupiedSlots = missingFresh;
            long prefixDurability = 0L;
            for (int candidateCount = 1;
                 candidateCount <= candidates.size();
                 candidateCount++) {
                ToolStack<K> candidate =
                    candidates.get(candidateCount - 1);
                prefixDurability = Math.addExact(
                    prefixDurability,
                    candidate.remainingDurability()
                );
                int prefixMissingFresh =
                    MiningToolBudget.missingFreshToolsForTraversal(
                        requirement.rawUses(),
                        durabilityBuffer,
                        requirement.maximumDurability(),
                        prefixDurability
                    );
                int prefixOccupiedSlots = Math.addExact(
                    candidateCount,
                    prefixMissingFresh
                );
                if (prefixOccupiedSlots < occupiedSlots
                    || (prefixOccupiedSlots == occupiedSlots
                        && candidateCount > selectedExisting)) {
                    occupiedSlots = prefixOccupiedSlots;
                    selectedExisting = candidateCount;
                    missingFresh = prefixMissingFresh;
                }
            }
            for (int index = 0; index < selectedExisting; index++) {
                keepSlots.add(candidates.get(index).slot());
            }

            int requiredItems = Math.addExact(
                selectedExisting,
                missingFresh
            );
            if (requiredItems > 0) {
                requiredItemCounts.put(item, requiredItems);
            }
            if (missingFresh > 0) {
                missingFreshCounts.put(item, missingFresh);
            }
        }

        Collections.sort(keepSlots);
        return new DurabilityInventoryPlan<>(
            keepSlots,
            requiredItemCounts,
            missingFreshCounts
        );
    }

    public List<Integer> keepSlots() {
        return keepSlots;
    }

    public Map<K, Integer> requiredItemCounts() {
        return requiredItemCounts;
    }

    public Map<K, Integer> missingFreshCounts() {
        return missingFreshCounts;
    }

    private static <K> LinkedHashMap<K, Requirement> copyRequirements(
        Map<? extends K, Requirement> requirements
    ) {
        LinkedHashMap<K, Requirement> result = new LinkedHashMap<>();
        for (Map.Entry<? extends K, Requirement> entry
            : requirements.entrySet()) {
            K item = Objects.requireNonNull(
                entry.getKey(),
                "required item"
            );
            Requirement requirement = Objects.requireNonNull(
                entry.getValue(),
                "requirement"
            );
            result.put(item, requirement);
        }
        return result;
    }

    private static <K>
        LinkedHashMap<K, ArrayList<ToolStack<K>>> groupAndValidateTools(
            List<ToolStack<K>> inventoryTools,
            Map<K, Requirement> requirements
        ) {
        LinkedHashMap<K, ArrayList<ToolStack<K>>> result =
            new LinkedHashMap<>();
        Set<Integer> encounteredSlots = new HashSet<>();
        for (ToolStack<K> tool : inventoryTools) {
            Objects.requireNonNull(tool, "inventory tool");
            if (!encounteredSlots.add(tool.slot())) {
                throw new IllegalArgumentException(
                    "Inventory slot is listed more than once: " + tool.slot()
                );
            }

            Requirement requirement = requirements.get(tool.item());
            if (requirement != null
                && tool.remainingDurability()
                    > requirement.maximumDurability()) {
                throw new IllegalArgumentException(
                    "Remaining durability exceeds the configured maximum "
                        + "for item: " + tool.item()
                );
            }
            if (requirement == null
                || tool.remainingDurability() == 0) {
                continue;
            }
            result.computeIfAbsent(
                tool.item(),
                ignored -> new ArrayList<>()
            ).add(tool);
        }
        return result;
    }

    private static <K> Map<K, Integer> immutableLinkedMap(
        Map<K, Integer> source
    ) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
