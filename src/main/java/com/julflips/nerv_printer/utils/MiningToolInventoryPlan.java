package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Pure strict-restock plan for damageable mining tools.
 *
 * <p>Compatible tools already carried by the player may contribute even when
 * worn, provided more than the reserved final durability point remains. A
 * chest candidate is deliberately stricter: it must be fully fresh as well
 * as compatible. The resulting {@link RestockDemand} therefore keeps its
 * absolute target based on usable compatible tools already on hand, while
 * fresh missing tools are the only admissible restock sources.</p>
 *
 * @param <K> item identity
 * @param <T> tool descriptor inspected for compatibility
 * @param <R> per-item compatibility requirement
 */
public final class MiningToolInventoryPlan<K, T, R> {
    /**
     * One observed damageable tool.
     *
     * <p>{@code maximumDurability} is the tool's undamaged remaining
     * durability. The final point is reserved, so usable tools must have more
     * than one point remaining.</p>
     */
    public record Tool<K, T>(
        K item,
        T descriptor,
        int remainingDurability,
        int maximumDurability
    ) {
        public Tool {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(descriptor, "descriptor");
            if (maximumDurability <= 1) {
                throw new IllegalArgumentException(
                    "Maximum tool durability must exceed the reserved "
                        + "final point."
                );
            }
            if (remainingDurability < 0
                || remainingDurability > maximumDurability) {
                throw new IllegalArgumentException(
                    "Remaining tool durability must be between zero and "
                        + "the maximum."
                );
            }
        }

        public boolean hasUsableDurability() {
            return remainingDurability > 1;
        }

        public boolean isFresh() {
            return remainingDurability == maximumDurability;
        }
    }

    private final Map<K, List<R>> compatibilityRequirements;
    private final Map<K, Integer> compatibleOnHandCounts;
    private final Map<K, RestockDemand<K>> restockDemands;
    private final BiPredicate<? super T, ? super R> compatibility;

    private MiningToolInventoryPlan(
        Map<K, List<R>> compatibilityRequirements,
        Map<K, Integer> compatibleOnHandCounts,
        Map<K, RestockDemand<K>> restockDemands,
        BiPredicate<? super T, ? super R> compatibility
    ) {
        this.compatibilityRequirements =
            immutableRequirements(compatibilityRequirements);
        this.compatibleOnHandCounts =
            immutableLinkedMap(compatibleOnHandCounts);
        this.restockDemands = immutableLinkedMap(restockDemands);
        this.compatibility = compatibility;
    }

    /**
     * Creates a plan from authoritative player tools and missing fresh counts.
     *
     * <p>Every item with a missing-count entry must have at least one
     * compatibility requirement. A zero missing count is retained as a
     * completed demand, which lets callers keep one uniform per-item data
     * path.</p>
     */
    public static <K, T, R> MiningToolInventoryPlan<K, T, R> plan(
        Map<
            ? extends K,
            ? extends Collection<? extends R>
        > compatibilityRequirements,
        List<? extends Tool<K, T>> carriedTools,
        Map<? extends K, Integer> missingFreshCounts,
        BiPredicate<? super T, ? super R> compatibility
    ) {
        Objects.requireNonNull(
            compatibilityRequirements,
            "compatibilityRequirements"
        );
        Objects.requireNonNull(carriedTools, "carriedTools");
        Objects.requireNonNull(
            missingFreshCounts,
            "missingFreshCounts"
        );
        Objects.requireNonNull(compatibility, "compatibility");

        LinkedHashMap<K, List<R>> requirements =
            copyRequirements(compatibilityRequirements);
        ArrayList<Tool<K, T>> tools = copyTools(carriedTools);

        LinkedHashMap<K, Integer> onHandCounts =
            new LinkedHashMap<>();
        for (K item : requirements.keySet()) {
            onHandCounts.put(item, 0);
        }

        MiningToolInventoryPlan<K, T, R> classifier =
            new MiningToolInventoryPlan<>(
                requirements,
                onHandCounts,
                Map.of(),
                compatibility
            );
        for (Tool<K, T> tool : tools) {
            if (!classifier.isUsableCompatiblePlayerTool(tool)) continue;
            onHandCounts.merge(tool.item(), 1, Math::addExact);
        }

        LinkedHashMap<K, RestockDemand<K>> demands =
            new LinkedHashMap<>();
        for (Map.Entry<? extends K, Integer> entry
            : missingFreshCounts.entrySet()) {
            K item = Objects.requireNonNull(
                entry.getKey(),
                "missing-fresh item"
            );
            Integer missing = Objects.requireNonNull(
                entry.getValue(),
                "missing-fresh count"
            );
            if (missing < 0) {
                throw new IllegalArgumentException(
                    "Missing fresh tool count cannot be negative."
                );
            }
            List<R> itemRequirements = requirements.get(item);
            if (itemRequirements == null || itemRequirements.isEmpty()) {
                throw new IllegalArgumentException(
                    "Missing fresh tool demand has no compatibility "
                        + "requirements for item: " + item
                );
            }
            demands.put(
                item,
                RestockDemand.fromOnHandAndMissing(
                    item,
                    onHandCounts.getOrDefault(item, 0),
                    missing
                )
            );
        }

        return new MiningToolInventoryPlan<>(
            requirements,
            onHandCounts,
            demands,
            compatibility
        );
    }

    /**
     * Returns whether a player-inventory tool may contribute to the
     * authoritative compatible count. Worn tools are allowed here.
     */
    public boolean isUsableCompatiblePlayerTool(Tool<K, T> tool) {
        Objects.requireNonNull(tool, "tool");
        return tool.hasUsableDurability() && satisfiesRequirements(tool);
    }

    /**
     * Returns whether a chest tool may be transferred for a missing fresh
     * demand. A merely usable but worn tool always returns {@code false}.
     */
    public boolean isFreshCompatibleChestCandidate(Tool<K, T> tool) {
        Objects.requireNonNull(tool, "tool");
        return tool.isFresh()
            && tool.hasUsableDurability()
            && satisfiesRequirements(tool);
    }

    /**
     * Recounts one item's compatible player tools from an authoritative
     * inventory observation.
     */
    public int compatiblePlayerCount(
        K item,
        List<? extends Tool<K, T>> authoritativePlayerTools
    ) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(
            authoritativePlayerTools,
            "authoritativePlayerTools"
        );
        int count = 0;
        for (Tool<K, T> tool : authoritativePlayerTools) {
            Objects.requireNonNull(tool, "authoritative player tool");
            if (Objects.equals(item, tool.item())
                && isUsableCompatiblePlayerTool(tool)) {
                count = Math.addExact(count, 1);
            }
        }
        return count;
    }

    public Map<K, List<R>> compatibilityRequirements() {
        return compatibilityRequirements;
    }

    public Map<K, Integer> compatibleOnHandCounts() {
        return compatibleOnHandCounts;
    }

    public int compatibleOnHandCount(K item) {
        Objects.requireNonNull(item, "item");
        return compatibleOnHandCounts.getOrDefault(item, 0);
    }

    public Map<K, RestockDemand<K>> restockDemands() {
        return restockDemands;
    }

    public Optional<RestockDemand<K>> restockDemand(K item) {
        Objects.requireNonNull(item, "item");
        return Optional.ofNullable(restockDemands.get(item));
    }

    private boolean satisfiesRequirements(Tool<K, T> tool) {
        List<R> requirements =
            compatibilityRequirements.get(tool.item());
        if (requirements == null || requirements.isEmpty()) return false;
        for (R requirement : requirements) {
            if (!compatibility.test(tool.descriptor(), requirement)) {
                return false;
            }
        }
        return true;
    }

    private static <K, R> LinkedHashMap<K, List<R>> copyRequirements(
        Map<
            ? extends K,
            ? extends Collection<? extends R>
        > source
    ) {
        LinkedHashMap<K, List<R>> result = new LinkedHashMap<>();
        for (Map.Entry<
            ? extends K,
            ? extends Collection<? extends R>
        > entry : source.entrySet()) {
            K item = Objects.requireNonNull(
                entry.getKey(),
                "compatibility item"
            );
            Collection<? extends R> itemRequirements =
                Objects.requireNonNull(
                    entry.getValue(),
                    "item compatibility requirements"
                );
            ArrayList<R> copy =
                new ArrayList<>(itemRequirements.size());
            for (R requirement : itemRequirements) {
                copy.add(
                    Objects.requireNonNull(
                        requirement,
                        "compatibility requirement"
                    )
                );
            }
            result.put(item, List.copyOf(copy));
        }
        return result;
    }

    private static <K, T> ArrayList<Tool<K, T>> copyTools(
        List<? extends Tool<K, T>> source
    ) {
        ArrayList<Tool<K, T>> result =
            new ArrayList<>(source.size());
        for (Tool<K, T> tool : source) {
            result.add(Objects.requireNonNull(tool, "carried tool"));
        }
        return result;
    }

    private static <K, V> Map<K, V> immutableLinkedMap(
        Map<K, V> source
    ) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static <K, R> Map<K, List<R>> immutableRequirements(
        Map<K, List<R>> source
    ) {
        LinkedHashMap<K, List<R>> copy = new LinkedHashMap<>();
        source.forEach(
            (item, requirements) ->
                copy.put(item, List.copyOf(requirements))
        );
        return Collections.unmodifiableMap(copy);
    }
}
