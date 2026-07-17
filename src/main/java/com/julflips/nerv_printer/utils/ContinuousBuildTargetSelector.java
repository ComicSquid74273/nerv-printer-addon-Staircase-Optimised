package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Selects the first missing target in traversal order for the normal
 * continuous printer loop.
 */
public final class ContinuousBuildTargetSelector {
    private ContinuousBuildTargetSelector() {
    }

    public static <T> Optional<T> firstMissing(
        List<T> orderedTargets,
        int startIndex,
        Predicate<? super T> isEligible,
        Predicate<? super T> isMissing
    ) {
        return firstMissing(
            orderedTargets,
            startIndex,
            orderedTargets.size(),
            isEligible,
            isMissing
        );
    }

    public static <T> Optional<T> firstMissing(
        List<T> orderedTargets,
        int startIndex,
        int endIndexExclusive,
        Predicate<? super T> isEligible,
        Predicate<? super T> isMissing
    ) {
        Objects.requireNonNull(orderedTargets, "orderedTargets");
        Objects.requireNonNull(isEligible, "isEligible");
        Objects.requireNonNull(isMissing, "isMissing");
        if (startIndex < 0
            || endIndexExclusive < startIndex
            || endIndexExclusive > orderedTargets.size()) {
            throw new IllegalArgumentException(
                "Selection bounds must be inside the ordered target list."
            );
        }

        for (int index = startIndex; index < endIndexExclusive; index++) {
            T target = Objects.requireNonNull(
                orderedTargets.get(index),
                "ordered target"
            );
            if (isEligible.test(target) && isMissing.test(target)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
