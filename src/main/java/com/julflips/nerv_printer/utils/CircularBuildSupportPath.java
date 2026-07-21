package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Builds the exact ordered supports used by circular print movement.
 *
 * <p>The alignment support is deliberately separate from the validated north
 * walkway entry. This gives the printer a one-block approach without changing
 * either structural U endpoint.</p>
 */
public final class CircularBuildSupportPath {
    private CircularBuildSupportPath() {
    }

    public static <T> List<T> create(
        T alignmentSupport,
        T outboundNorthSupport,
        List<T> orderedUTargets,
        T returnNorthSupport
    ) {
        Objects.requireNonNull(alignmentSupport, "alignmentSupport");
        Objects.requireNonNull(
            outboundNorthSupport,
            "outboundNorthSupport"
        );
        Objects.requireNonNull(orderedUTargets, "orderedUTargets");
        Objects.requireNonNull(
            returnNorthSupport,
            "returnNorthSupport"
        );
        if (alignmentSupport.equals(outboundNorthSupport)) {
            throw new IllegalArgumentException(
                "Alignment and outbound north supports must be distinct."
            );
        }
        if (orderedUTargets.isEmpty()) {
            throw new IllegalArgumentException(
                "A circular support path requires U targets."
            );
        }

        ArrayList<T> path = new ArrayList<>(
            orderedUTargets.size() + 3
        );
        path.add(alignmentSupport);
        path.add(outboundNorthSupport);
        for (T target : orderedUTargets) {
            path.add(Objects.requireNonNull(target, "ordered U target"));
        }
        path.add(returnNorthSupport);
        if (new HashSet<>(path).size() != path.size()) {
            throw new IllegalArgumentException(
                "A circular support path cannot contain duplicate supports."
            );
        }
        return List.copyOf(path);
    }

    /**
     * The approach alignment and both north-walkway supports are outside the
     * printed U. Restarting on one of them can replan directly instead of
     * attempting an in-route egress.
     */
    public static <T> boolean isDirectReplanSupport(
        List<T> orderedSupports,
        T support
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(support, "support");
        if (orderedSupports.size() < 3) {
            throw new IllegalArgumentException(
                "A circular support path requires alignment and endpoints."
            );
        }
        return support.equals(orderedSupports.getFirst())
            || support.equals(orderedSupports.get(1))
            || support.equals(orderedSupports.getLast());
    }
}
