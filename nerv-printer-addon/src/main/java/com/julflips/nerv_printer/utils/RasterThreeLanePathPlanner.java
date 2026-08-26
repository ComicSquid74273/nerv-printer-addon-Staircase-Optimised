package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles each three-lane strip into deterministic placement passes. The
 * lowest lane is followed first. If that path cannot reach the whole strip,
 * the highest lane is reserved for a complete return pass. A third pass is
 * emitted only when a middle height is outside the reach of both envelopes.
 */
public final class RasterThreeLanePathPlanner {
    private static final double REACH_MARGIN = 0.15;

    private RasterThreeLanePathPlanner() {
    }

    public record Target<T>(int x, int y, int z, T payload) {
        public Target {
            Objects.requireNonNull(payload, "payload");
        }
    }

    public record Slice<T>(
        int band,
        int direction,
        List<Target<T>> targets
    ) {
        public Slice {
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException("Slice direction must be -1 or 1.");
            }
            if (targets == null || targets.isEmpty() || targets.size() > 3) {
                throw new IllegalArgumentException("A strip slice requires one to three targets.");
            }
            targets = List.copyOf(targets);
        }
    }

    public record Assignment<T>(
        Target<T> target,
        int band,
        int pass,
        int direction,
        int pathX,
        int pathSurfaceY,
        int pathZ
    ) {
    }

    /**
     * One physical boat pose in a complete strip pass. Unlike an assignment,
     * a pass point is retained even when every target at that slice was
     * already reachable from an earlier envelope. Keeping those empty points
     * prevents a later cleanup pass from teleporting across the strip.
     */
    public record PassPoint<T>(
        int band,
        int pass,
        int direction,
        int pathX,
        int pathSurfaceY,
        int pathZ,
        List<Target<T>> targets
    ) {
        public PassPoint {
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException(
                    "Pass-point direction must be -1 or 1."
                );
            }
            targets = List.copyOf(targets);
        }
    }

    public record Plan<T>(
        List<Assignment<T>> assignments,
        List<PassPoint<T>> passPoints,
        int passCount
    ) {
        public Plan {
            assignments = List.copyOf(assignments);
            passPoints = List.copyOf(passPoints);
        }
    }

    public static <T> Plan<T> create(
        List<Slice<T>> slices,
        double interactionRange
    ) {
        Objects.requireNonNull(slices, "slices");
        if (slices.isEmpty() || !(interactionRange > 0.0)) {
            throw new IllegalArgumentException("Three-lane path inputs are invalid.");
        }
        LinkedHashMap<Integer, List<Slice<T>>> byBand = new LinkedHashMap<>();
        for (Slice<T> slice : slices) {
            Objects.requireNonNull(slice, "slice");
            byBand.computeIfAbsent(slice.band(), ignored -> new ArrayList<>())
                .add(slice);
        }

        ArrayList<Assignment<T>> result = new ArrayList<>();
        ArrayList<PassPoint<T>> passPoints = new ArrayList<>();
        int maximumPasses = 0;
        for (Map.Entry<Integer, List<Slice<T>>> entry : byBand.entrySet()) {
            List<Slice<T>> bandSlices = entry.getValue();
            LinkedHashMap<Slice<T>, LinkedHashSet<Target<T>>> remaining =
                new LinkedHashMap<>();
            boolean returnRequired = false;
            for (Slice<T> slice : bandSlices) {
                remaining.put(slice, new LinkedHashSet<>(slice.targets()));
                Target<T> low = lowest(slice.targets());
                if (slice.targets().stream().anyMatch(target ->
                    !reachable(low, target, interactionRange))) {
                    returnRequired = true;
                }
            }

            if (returnRequired) {
                // Guarantee a continuous high-envelope return over the whole
                // strip, even at slices where the low pass could reach all.
                for (Slice<T> slice : bandSlices) {
                    Target<T> reservedHigh = highest(slice.targets());
                    remaining.get(slice).remove(reservedHigh);
                }
            }

            emitPass(
                result,
                passPoints,
                bandSlices,
                remaining,
                0,
                false,
                Envelope.LOW,
                interactionRange
            );
            int passes = 1;

            if (returnRequired) {
                // Restore the reserved high target, plus anything the low
                // envelope could not reach, for a full reverse pass.
                for (Slice<T> slice : bandSlices) {
                    remaining.get(slice).add(highest(slice.targets()));
                }
                emitPass(
                    result,
                    passPoints,
                    bandSlices,
                    remaining,
                    1,
                    true,
                    Envelope.HIGH,
                    interactionRange
                );
                passes = 2;
            }

            if (remaining.values().stream().anyMatch(set -> !set.isEmpty())) {
                emitPass(
                    result,
                    passPoints,
                    bandSlices,
                    remaining,
                    2,
                    false,
                    Envelope.MIDDLE,
                    interactionRange
                );
                passes = 3;
            }
            if (remaining.values().stream().anyMatch(set -> !set.isEmpty())) {
                throw new IllegalArgumentException(
                    "Three-lane strip contains a target unreachable from its own cleanup envelope."
                );
            }
            maximumPasses = Math.max(maximumPasses, passes);
        }
        return new Plan<>(result, passPoints, maximumPasses);
    }

    private enum Envelope {
        LOW,
        HIGH,
        MIDDLE
    }

    private static <T> void emitPass(
        List<Assignment<T>> output,
        List<PassPoint<T>> passPoints,
        List<Slice<T>> bandSlices,
        Map<Slice<T>, LinkedHashSet<Target<T>>> remaining,
        int pass,
        boolean reverse,
        Envelope envelope,
        double interactionRange
    ) {
        for (int offset = 0; offset < bandSlices.size(); offset++) {
            int index = reverse ? bandSlices.size() - 1 - offset : offset;
            Slice<T> slice = bandSlices.get(index);
            LinkedHashSet<Target<T>> pending = remaining.get(slice);
            Target<T> anchor = switch (envelope) {
                case LOW -> lowest(slice.targets());
                case HIGH -> highest(slice.targets());
                case MIDDLE -> pending.isEmpty()
                    ? middle(slice.targets()) : middle(pending);
            };
            ArrayList<Target<T>> assigned = new ArrayList<>();
            for (Target<T> target : pending) {
                if (reachable(anchor, target, interactionRange)) {
                    assigned.add(target);
                }
            }
            for (Target<T> target : assigned) {
                output.add(new Assignment<>(
                    target,
                    slice.band(),
                    pass,
                    reverse ? -slice.direction() : slice.direction(),
                    anchor.x(),
                    anchor.y(),
                    anchor.z()
                ));
                pending.remove(target);
            }
            passPoints.add(new PassPoint<>(
                slice.band(),
                pass,
                reverse ? -slice.direction() : slice.direction(),
                anchor.x(),
                anchor.y(),
                anchor.z(),
                assigned
            ));
        }
    }

    private static <T> boolean reachable(
        Target<T> anchor,
        Target<T> target,
        double interactionRange
    ) {
        double dx = target.x() - anchor.x();
        double dz = target.z() - anchor.z();
        double eyeY = anchor.y()
            - RasterFlightPlan.EYE_CLEARANCE_BELOW_SURFACE;
        double dy = target.y() + 0.5 - eyeY;
        double usableRange = Math.max(0.0, interactionRange - REACH_MARGIN);
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
            <= usableRange;
    }

    private static <T> Target<T> lowest(List<Target<T>> targets) {
        return targets.stream()
            .min(java.util.Comparator.comparingInt(Target<T>::y))
            .orElseThrow();
    }

    private static <T> Target<T> highest(List<Target<T>> targets) {
        return targets.stream()
            .max(java.util.Comparator.comparingInt(Target<T>::y))
            .orElseThrow();
    }

    private static <T> Target<T> middle(LinkedHashSet<Target<T>> targets) {
        if (targets.isEmpty()) return null;
        ArrayList<Target<T>> ordered = new ArrayList<>(targets);
        ordered.sort(java.util.Comparator.comparingInt(Target<T>::y));
        return ordered.get(ordered.size() / 2);
    }

    private static <T> Target<T> middle(List<Target<T>> targets) {
        ArrayList<Target<T>> ordered = new ArrayList<>(targets);
        ordered.sort(java.util.Comparator.comparingInt(Target<T>::y));
        return ordered.get(ordered.size() / 2);
    }
}
