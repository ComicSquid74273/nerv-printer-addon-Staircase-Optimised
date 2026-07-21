package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Builds the exact ordered supports used by circular print movement.
 *
 * <p>The entry and exit alignment supports are deliberately separate from
 * their validated north-walkway endpoints. This gives the printer a complete
 * one-block exterior approach and departure without changing either
 * structural U endpoint.</p>
 */
public final class CircularBuildSupportPath {
    private CircularBuildSupportPath() {
    }

    public static <T> List<T> create(
        T alignmentSupport,
        T outboundNorthSupport,
        List<T> orderedUTargets,
        T returnNorthSupport,
        T exitAlignmentSupport
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
        Objects.requireNonNull(
            exitAlignmentSupport,
            "exitAlignmentSupport"
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
        if (returnNorthSupport.equals(exitAlignmentSupport)) {
            throw new IllegalArgumentException(
                "Return north and exit alignment supports must be distinct."
            );
        }

        ArrayList<T> path = new ArrayList<>(
            orderedUTargets.size() + 4
        );
        path.add(alignmentSupport);
        path.add(outboundNorthSupport);
        for (T target : orderedUTargets) {
            path.add(Objects.requireNonNull(target, "ordered U target"));
        }
        path.add(returnNorthSupport);
        path.add(exitAlignmentSupport);
        if (new HashSet<>(path).size() != path.size()) {
            throw new IllegalArgumentException(
                "A circular support path cannot contain duplicate supports."
            );
        }
        return List.copyOf(path);
    }

    /**
     * Both exterior alignments and both north-walkway supports are outside the
     * printed U. Restarting on one of them can replan directly instead of
     * attempting an in-route egress.
     */
    public static <T> boolean isDirectReplanSupport(
        List<T> orderedSupports,
        T support
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(support, "support");
        if (orderedSupports.size() < 4) {
            throw new IllegalArgumentException(
                "A circular support path requires alignment and endpoints."
            );
        }
        return support.equals(orderedSupports.getFirst())
            || support.equals(orderedSupports.get(1))
            || support.equals(orderedSupports.get(
                orderedSupports.size() - 2
            ))
            || support.equals(orderedSupports.getLast());
    }
}
