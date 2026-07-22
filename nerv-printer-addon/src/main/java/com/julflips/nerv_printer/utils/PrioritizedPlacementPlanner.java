package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure placement selection with a mandatory primary tier and a best-effort
 * optional tier.
 *
 * <p>Primary decisions consume both on-hand material and the corresponding
 * primary reservation. Optional decisions are admitted only while the
 * simulated on-hand count remains strictly above the still-outstanding primary
 * reservation. This lets callers reserve a complete traversal while spending
 * only genuine surplus on nearby work.</p>
 */
public final class PrioritizedPlacementPlanner {
    private PrioritizedPlacementPlanner() {
    }

    public enum Tier {
        PRIMARY,
        OPTIONAL
    }

    public record Target<K, M>(K key, M material) {
        public Target {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(material, "material");
        }
    }

    public record Decision<K, M>(K key, M material, Tier tier) {
        public Decision {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(tier, "tier");
        }
    }

    public record Plan<K, M>(
        List<Decision<K, M>> decisions,
        Map<M, Integer> remainingOnHand,
        Map<M, Integer> remainingPrimaryReserve
    ) {
        public Plan {
            decisions = List.copyOf(decisions);
            remainingOnHand = Map.copyOf(remainingOnHand);
            remainingPrimaryReserve = Map.copyOf(remainingPrimaryReserve);
        }
    }

    public static <K, M> Plan<K, M> plan(
        List<Target<K, M>> orderedPrimaryTargets,
        List<Target<K, M>> orderedOptionalTargets,
        int attemptBudget,
        Map<M, Integer> onHand,
        Map<M, Integer> remainingPrimaryReserve,
        Predicate<? super Target<K, M>> isEligible,
        Predicate<? super K> isPending
    ) {
        Objects.requireNonNull(orderedPrimaryTargets, "orderedPrimaryTargets");
        Objects.requireNonNull(orderedOptionalTargets, "orderedOptionalTargets");
        Objects.requireNonNull(isEligible, "isEligible");
        Objects.requireNonNull(isPending, "isPending");
        if (attemptBudget < 0) {
            throw new IllegalArgumentException("Attempt budget cannot be negative.");
        }

        List<Target<K, M>> primaryTargets =
            List.copyOf(orderedPrimaryTargets);
        List<Target<K, M>> optionalTargets =
            List.copyOf(orderedOptionalTargets);
        HashMap<M, Integer> simulatedOnHand =
            copyCounts(onHand, "onHand");
        HashMap<M, Integer> simulatedReserve =
            copyCounts(remainingPrimaryReserve, "remainingPrimaryReserve");
        ArrayList<Decision<K, M>> decisions = new ArrayList<>(
            Math.min(
                attemptBudget,
                primaryTargets.size() + optionalTargets.size()
            )
        );
        HashSet<K> selectedKeys = new HashSet<>();
        HashSet<K> primaryKeys = new HashSet<>();
        for (Target<K, M> target : primaryTargets) {
            primaryKeys.add(target.key());
        }

        for (Target<K, M> target : primaryTargets) {
            if (decisions.size() >= attemptBudget) break;
            if (!isSelectable(target, isEligible, isPending, selectedKeys)) {
                continue;
            }

            int available = simulatedOnHand.getOrDefault(
                target.material(),
                0
            );
            if (available <= 0) continue;

            selectedKeys.add(target.key());
            simulatedOnHand.put(target.material(), available - 1);
            int reserved = simulatedReserve.getOrDefault(
                target.material(),
                0
            );
            if (reserved > 0) {
                simulatedReserve.put(target.material(), reserved - 1);
            }
            decisions.add(
                new Decision<>(
                    target.key(),
                    target.material(),
                    Tier.PRIMARY
                )
            );
        }

        for (Target<K, M> target : optionalTargets) {
            if (decisions.size() >= attemptBudget) break;
            // A primary key cannot be downgraded into optional work merely
            // because its primary attempt was ineligible, pending, or starved.
            if (primaryKeys.contains(target.key())) continue;
            if (!isSelectable(target, isEligible, isPending, selectedKeys)) {
                continue;
            }

            int available = simulatedOnHand.getOrDefault(
                target.material(),
                0
            );
            int reserved = simulatedReserve.getOrDefault(
                target.material(),
                0
            );
            if (available <= reserved) continue;

            selectedKeys.add(target.key());
            simulatedOnHand.put(target.material(), available - 1);
            decisions.add(
                new Decision<>(
                    target.key(),
                    target.material(),
                    Tier.OPTIONAL
                )
            );
        }

        return new Plan<>(decisions, simulatedOnHand, simulatedReserve);
    }

    /**
     * Reports whether a fresh planner pass could select at least one target
     * from the supplied tier.
     *
     * <p>This deliberately applies the same pending, eligibility, and
     * material-availability gates as {@link #plan}. Callers can therefore
     * decide whether a higher-priority tier still has actionable work without
     * mistaking an already-submitted target for work that should block a lower
     * tier or movement.</p>
     */
    public static <K, M> boolean hasSelectableTarget(
        List<Target<K, M>> orderedTargets,
        Map<M, Integer> onHand,
        Predicate<? super Target<K, M>> isEligible,
        Predicate<? super K> isPending
    ) {
        Objects.requireNonNull(orderedTargets, "orderedTargets");
        Objects.requireNonNull(isEligible, "isEligible");
        Objects.requireNonNull(isPending, "isPending");
        HashMap<M, Integer> available = copyCounts(onHand, "onHand");
        for (Target<K, M> target : orderedTargets) {
            Objects.requireNonNull(target, "ordered target");
            if (!isPending.test(target.key())
                && available.getOrDefault(target.material(), 0) > 0
                && isEligible.test(target)) {
                return true;
            }
        }
        return false;
    }

    private static <K, M> boolean isSelectable(
        Target<K, M> target,
        Predicate<? super Target<K, M>> isEligible,
        Predicate<? super K> isPending,
        Set<K> selectedKeys
    ) {
        return !selectedKeys.contains(target.key())
            && !isPending.test(target.key())
            && isEligible.test(target);
    }

    private static <M> HashMap<M, Integer> copyCounts(
        Map<M, Integer> source,
        String label
    ) {
        Objects.requireNonNull(source, label);
        HashMap<M, Integer> copy = new HashMap<>();
        for (Map.Entry<M, Integer> entry : source.entrySet()) {
            M material = Objects.requireNonNull(
                entry.getKey(),
                label + " material"
            );
            Integer count = Objects.requireNonNull(
                entry.getValue(),
                label + " count"
            );
            if (count < 0) {
                throw new IllegalArgumentException(
                    label + " counts cannot be negative."
                );
            }
            copy.put(material, count);
        }
        return copy;
    }
}
