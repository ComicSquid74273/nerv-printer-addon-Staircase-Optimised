package com.julflips.nerv_printer.utils;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Deterministic scoring for the registered map area's feasible Y offsets. */
public final class BuildHeightSelector {
    private BuildHeightSelector() {
    }

    public record Candidate(
        int baseY,
        int obstructionCount,
        boolean loaded,
        boolean insideWorld,
        boolean breakable
    ) {
        public boolean valid() {
            return loaded && insideWorld && breakable && obstructionCount >= 0;
        }
    }

    public static Optional<Candidate> select(
        Collection<Candidate> candidates,
        double playerY
    ) {
        Objects.requireNonNull(candidates, "candidates");
        return candidates.stream()
            .filter(Candidate::valid)
            .min(
                Comparator.comparingInt(Candidate::obstructionCount)
                    .thenComparingDouble(candidate ->
                        Math.abs(candidate.baseY() - playerY))
                    .thenComparingInt(Candidate::baseY)
            );
    }
}
