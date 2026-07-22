package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Resolves the strict material demand for a route after earlier preprinting.
 *
 * <p>An existing block does not release guaranteed inventory merely because a
 * cache currently reports it present. A target is released only when this run
 * previously received an authoritative confirmation for that exact key and
 * the latest observation still matches. If it later changes, it immediately
 * returns to the outstanding route demand.</p>
 */
public final class ConfirmedBuildInventoryDemand {
    private ConfirmedBuildInventoryDemand() {
    }

    public record Target<K, M>(K key, M material) {
        public Target {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(material, "material");
        }
    }

    public record Result<K, M>(
        List<K> outstandingKeys,
        List<M> outstandingMaterials,
        List<K> releasedKeys
    ) {
        public Result {
            outstandingKeys = List.copyOf(outstandingKeys);
            outstandingMaterials = List.copyOf(outstandingMaterials);
            releasedKeys = List.copyOf(releasedKeys);
        }
    }

    public static <K, M> Result<K, M> resolve(
        List<? extends Target<K, M>> orderedTargets,
        Set<? extends K> confirmedThisRun,
        Predicate<? super K> latestObservationMatches
    ) {
        Objects.requireNonNull(orderedTargets, "orderedTargets");
        Objects.requireNonNull(confirmedThisRun, "confirmedThisRun");
        Objects.requireNonNull(
            latestObservationMatches,
            "latestObservationMatches"
        );

        ArrayList<K> outstandingKeys = new ArrayList<>();
        ArrayList<M> outstandingMaterials = new ArrayList<>();
        ArrayList<K> releasedKeys = new ArrayList<>();
        HashSet<K> seen = new HashSet<>();
        for (Target<K, M> target : orderedTargets) {
            Target<K, M> candidate =
                Objects.requireNonNull(target, "target");
            if (!seen.add(candidate.key())) {
                throw new IllegalArgumentException(
                    "Build target key is duplicated: " + candidate.key()
                );
            }

            boolean released =
                confirmedThisRun.contains(candidate.key())
                    && latestObservationMatches.test(candidate.key());
            if (released) {
                releasedKeys.add(candidate.key());
            } else {
                outstandingKeys.add(candidate.key());
                outstandingMaterials.add(candidate.material());
            }
        }
        return new Result<>(
            outstandingKeys,
            outstandingMaterials,
            releasedKeys
        );
    }
}
