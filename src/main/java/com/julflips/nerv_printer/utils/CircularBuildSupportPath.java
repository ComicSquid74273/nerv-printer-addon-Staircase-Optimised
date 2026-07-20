package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

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

    public enum MovementStatus {
        READY,
        COMPLETE,
        OFF_PATH,
        WAITING_FOR_NEXT_SUPPORT
    }

    public record MovementDecision<T>(
        MovementStatus status,
        T requiredSupport
    ) {
        public MovementDecision {
            Objects.requireNonNull(status, "status");
            boolean requiresSupport =
                status == MovementStatus.READY
                    || status
                        == MovementStatus.WAITING_FOR_NEXT_SUPPORT;
            if (requiresSupport != (requiredSupport != null)) {
                throw new IllegalArgumentException(
                    "Movement status and required support disagree."
                );
            }
        }

        public boolean mayMove() {
            return status == MovementStatus.READY
                || status == MovementStatus.COMPLETE;
        }
    }

    /**
     * Checks only the immediate next support. A later gap is deliberately
     * irrelevant until it becomes the next route step.
     */
    public static <T> MovementDecision<T> decideMovement(
        List<T> orderedSupports,
        T currentSupport,
        Predicate<? super T> isConfirmedReady
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(currentSupport, "currentSupport");
        Objects.requireNonNull(isConfirmedReady, "isConfirmedReady");
        int currentIndex = orderedSupports.indexOf(currentSupport);
        if (currentIndex < 0) {
            return new MovementDecision<>(
                MovementStatus.OFF_PATH,
                null
            );
        }
        return decideMovement(
            orderedSupports,
            currentIndex,
            isConfirmedReady
        );
    }

    /**
     * Checks the immediate next support from a monotonic route cursor.
     */
    public static <T> MovementDecision<T> decideMovement(
        List<T> orderedSupports,
        int currentIndex,
        Predicate<? super T> isConfirmedReady
    ) {
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        Objects.requireNonNull(isConfirmedReady, "isConfirmedReady");
        if (currentIndex < 0 || currentIndex >= orderedSupports.size()) {
            return new MovementDecision<>(
                MovementStatus.OFF_PATH,
                null
            );
        }
        if (currentIndex + 1 >= orderedSupports.size()) {
            return new MovementDecision<>(
                MovementStatus.COMPLETE,
                null
            );
        }
        T nextSupport = Objects.requireNonNull(
            orderedSupports.get(currentIndex + 1),
            "next support"
        );
        return new MovementDecision<>(
            isConfirmedReady.test(nextSupport)
                ? MovementStatus.READY
                : MovementStatus.WAITING_FOR_NEXT_SUPPORT,
            nextSupport
        );
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
