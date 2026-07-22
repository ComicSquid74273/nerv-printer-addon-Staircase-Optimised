package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Plans optional inventory demand while preserving placement dependencies.
 *
 * <p>The complete primary demand is reserved first. Optional targets then
 * enter a deterministic frontier only when they have an existing/guaranteed
 * anchor or an already admitted optional anchor. A target is admitted only
 * when its material still fits the usable slot count. Rejected targets never
 * unlock their dependants, so the returned optional order is placement-safe
 * by construction.</p>
 *
 * <p>Target priority is the encounter order of {@code orderedOptionalTargets}
 * among the targets that are currently dependency-feasible. A later root can
 * therefore unlock an earlier target, while still being returned before the
 * target that depends on it.</p>
 */
public final class DependencyClosedOptionalInventoryPlan {
    private DependencyClosedOptionalInventoryPlan() {
    }

    /**
     * An optional placement candidate.
     *
     * @param key stable target identity
     * @param material inventory material consumed by the target
     * @param initiallyAnchored whether current world state or guaranteed
     *     primary work provides a placement anchor
     * @param optionalAnchorKeys optional targets of which any one, once
     *     admitted, can provide an anchor for this target
     */
    public record Target<K, M>(
        K key,
        M material,
        boolean initiallyAnchored,
        Set<K> optionalAnchorKeys
    ) {
        public Target {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(material, "material");
            optionalAnchorKeys = Set.copyOf(
                Objects.requireNonNull(
                    optionalAnchorKeys,
                    "optionalAnchorKeys"
                )
            );
            if (optionalAnchorKeys.contains(key)) {
                throw new IllegalArgumentException(
                    "An optional target cannot anchor itself: " + key
                );
            }
        }
    }

    public record Result<K, M>(
        Map<M, Integer> primaryDemand,
        Map<M, Integer> optionalDemand,
        Map<M, Integer> totalDemand,
        List<K> plannedOptionalKeys,
        List<Integer> plannedOptionalIndices,
        int optionalCandidateCount,
        int usableSlots,
        int primarySlotsRequired,
        int totalSlotsRequired
    ) {
        public Result {
            primaryDemand = immutableLinkedMap(primaryDemand);
            optionalDemand = immutableLinkedMap(optionalDemand);
            totalDemand = immutableLinkedMap(totalDemand);
            plannedOptionalKeys = List.copyOf(plannedOptionalKeys);
            plannedOptionalIndices = List.copyOf(
                plannedOptionalIndices
            );
            if (optionalCandidateCount < 0
                || usableSlots < 0
                || primarySlotsRequired < 0
                || totalSlotsRequired < 0) {
                throw new IllegalArgumentException(
                    "Plan counts cannot be negative."
                );
            }
        }

        public boolean primaryFits() {
            return primarySlotsRequired <= usableSlots;
        }

        public int primarySlotDeficit() {
            return Math.max(0, primarySlotsRequired - usableSlots);
        }

        public int remainingSlots() {
            return Math.max(0, usableSlots - totalSlotsRequired);
        }
    }

    public static <K, M> Result<K, M> plan(
        List<? extends M> orderedPrimaryMaterials,
        List<? extends Target<K, M>> orderedOptionalTargets,
        Map<? super M, Integer> maximumStackSizes,
        int usableSlots
    ) {
        Objects.requireNonNull(
            orderedPrimaryMaterials,
            "orderedPrimaryMaterials"
        );
        Objects.requireNonNull(
            orderedOptionalTargets,
            "orderedOptionalTargets"
        );
        Objects.requireNonNull(
            maximumStackSizes,
            "maximumStackSizes"
        );
        if (usableSlots < 0) {
            throw new IllegalArgumentException(
                "Usable slot count cannot be negative."
            );
        }

        ArrayList<M> primaryMaterials = new ArrayList<>(
            orderedPrimaryMaterials.size()
        );
        for (M material : orderedPrimaryMaterials) {
            primaryMaterials.add(
                Objects.requireNonNull(
                    material,
                    "primary material"
                )
            );
        }

        ArrayList<Target<K, M>> optionalTargets = new ArrayList<>(
            orderedOptionalTargets.size()
        );
        LinkedHashMap<K, Integer> targetIndices =
            new LinkedHashMap<>();
        for (Target<K, M> target : orderedOptionalTargets) {
            Target<K, M> candidate =
                Objects.requireNonNull(target, "optional target");
            int index = optionalTargets.size();
            if (targetIndices.putIfAbsent(
                candidate.key(),
                index
            ) != null) {
                throw new IllegalArgumentException(
                    "Optional target key is duplicated: "
                        + candidate.key()
                );
            }
            optionalTargets.add(candidate);
        }
        for (Target<K, M> target : optionalTargets) {
            for (K anchor : target.optionalAnchorKeys()) {
                if (!targetIndices.containsKey(anchor)) {
                    throw new IllegalArgumentException(
                        "Optional target " + target.key()
                            + " references unknown anchor " + anchor + "."
                    );
                }
            }
        }

        LinkedHashMap<M, Integer> resolvedStackSizes =
            new LinkedHashMap<>();
        for (M material : primaryMaterials) {
            resolveStackSize(
                material,
                maximumStackSizes,
                resolvedStackSizes
            );
        }
        for (Target<K, M> target : optionalTargets) {
            resolveStackSize(
                target.material(),
                maximumStackSizes,
                resolvedStackSizes
            );
        }

        LinkedHashMap<M, Integer> primaryDemand =
            new LinkedHashMap<>();
        for (M material : primaryMaterials) {
            primaryDemand.merge(material, 1, Math::addExact);
        }
        int primarySlotsRequired = slotsRequired(
            primaryDemand,
            resolvedStackSizes
        );
        LinkedHashMap<M, Integer> optionalDemand =
            new LinkedHashMap<>();
        LinkedHashMap<M, Integer> totalDemand =
            new LinkedHashMap<>(primaryDemand);
        ArrayList<K> plannedKeys = new ArrayList<>();
        ArrayList<Integer> plannedIndices = new ArrayList<>();
        int totalSlotsRequired = primarySlotsRequired;

        if (primarySlotsRequired <= usableSlots
            && !optionalTargets.isEmpty()) {
            HashMap<K, ArrayList<Integer>> dependants =
                new HashMap<>();
            for (int index = 0;
                 index < optionalTargets.size();
                 index++) {
                for (K anchor :
                    optionalTargets.get(index)
                        .optionalAnchorKeys()) {
                    dependants.computeIfAbsent(
                        anchor,
                        ignored -> new ArrayList<>()
                    ).add(index);
                }
            }

            PriorityQueue<Integer> frontier =
                new PriorityQueue<>();
            boolean[] queued =
                new boolean[optionalTargets.size()];
            boolean[] resolved =
                new boolean[optionalTargets.size()];
            for (int index = 0;
                 index < optionalTargets.size();
                 index++) {
                if (optionalTargets.get(index).initiallyAnchored()) {
                    frontier.add(index);
                    queued[index] = true;
                }
            }

            while (!frontier.isEmpty()) {
                int index = frontier.remove();
                if (resolved[index]) continue;
                resolved[index] = true;

                Target<K, M> target = optionalTargets.get(index);
                M material = target.material();
                int oldCount = totalDemand.getOrDefault(
                    material,
                    0
                );
                int newCount = Math.addExact(oldCount, 1);
                int maximumStackSize =
                    resolvedStackSizes.get(material);
                int candidateSlots = Math.addExact(
                    totalSlotsRequired,
                    slotsForCount(newCount, maximumStackSize)
                        - slotsForCount(oldCount, maximumStackSize)
                );
                if (candidateSlots > usableSlots) continue;

                totalDemand.put(material, newCount);
                optionalDemand.merge(
                    material,
                    1,
                    Math::addExact
                );
                plannedKeys.add(target.key());
                plannedIndices.add(index);
                totalSlotsRequired = candidateSlots;

                for (int dependant :
                    dependants.getOrDefault(
                        target.key(),
                        new ArrayList<>()
                    )) {
                    if (!resolved[dependant]
                        && !queued[dependant]) {
                        frontier.add(dependant);
                        queued[dependant] = true;
                    }
                }
            }
        }

        return new Result<>(
            primaryDemand,
            optionalDemand,
            totalDemand,
            plannedKeys,
            plannedIndices,
            optionalTargets.size(),
            usableSlots,
            primarySlotsRequired,
            totalSlotsRequired
        );
    }

    private static <M> void resolveStackSize(
        M material,
        Map<? super M, Integer> maximumStackSizes,
        Map<M, Integer> resolved
    ) {
        if (resolved.containsKey(material)) return;
        Integer maximumStackSize = maximumStackSizes.get(material);
        if (maximumStackSize == null) {
            throw new IllegalArgumentException(
                "Missing maximum stack size for material: " + material
            );
        }
        if (maximumStackSize <= 0) {
            throw new IllegalArgumentException(
                "Maximum stack size must be positive for material: "
                    + material
            );
        }
        resolved.put(material, maximumStackSize);
    }

    private static <M> int slotsRequired(
        Map<M, Integer> demand,
        Map<M, Integer> maximumStackSizes
    ) {
        long slots = 0L;
        for (Map.Entry<M, Integer> entry : demand.entrySet()) {
            slots += slotsForCount(
                entry.getValue(),
                maximumStackSizes.get(entry.getKey())
            );
        }
        if (slots > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Required slot count is too large."
            );
        }
        return (int) slots;
    }

    private static int slotsForCount(
        int count,
        int maximumStackSize
    ) {
        return Math.ceilDiv(count, maximumStackSize);
    }

    private static <K, V> Map<K, V> immutableLinkedMap(
        Map<K, V> source
    ) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(
            new LinkedHashMap<>(source)
        );
    }
}
