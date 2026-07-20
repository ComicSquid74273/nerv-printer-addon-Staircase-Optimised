package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the conservative support-center reach window for one block.
 */
public final class BlockReachWindow {
    private static final double DISTANCE_EPSILON = 1.0e-9;

    private BlockReachWindow() {
    }

    public record Cell(int x, int y, int z) {
    }

    public record Window(
        int firstSupportIndex,
        int lastSupportIndex,
        List<Integer> reachableSupportIndices
    ) {
        public Window {
            reachableSupportIndices = List.copyOf(
                reachableSupportIndices
            );
            if (firstSupportIndex < 0
                || lastSupportIndex < firstSupportIndex
                || reachableSupportIndices.isEmpty()
                || reachableSupportIndices.getFirst()
                    != firstSupportIndex
                || reachableSupportIndices.getLast()
                    != lastSupportIndex) {
                throw new IllegalArgumentException(
                    "Reach-window boundaries are invalid."
                );
            }
        }
    }

    /**
     * The eye is modeled over the center of each supporting block. This is
     * deliberately conservative: movement between centers can only add more
     * opportunities and is never required by the proof.
     */
    public static Optional<Window> find(
        Cell target,
        List<Cell> orderedSupports,
        double standingEyeHeight,
        double maximumReach
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(orderedSupports, "orderedSupports");
        if (!Double.isFinite(standingEyeHeight)
            || standingEyeHeight <= 0.0
            || !Double.isFinite(maximumReach)
            || maximumReach <= 0.0) {
            throw new IllegalArgumentException(
                "Eye height and reach must be finite and positive."
            );
        }

        ArrayList<Integer> reachable = new ArrayList<>();
        double maximumSquared = maximumReach * maximumReach;
        for (int index = 0; index < orderedSupports.size(); index++) {
            Cell support = Objects.requireNonNull(
                orderedSupports.get(index),
                "support"
            );
            double dx = support.x() - target.x();
            double dz = support.z() - target.z();
            double eyeY =
                support.y() + 1.0 + standingEyeHeight;
            double targetCenterY = target.y() + 0.5;
            double dy = eyeY - targetCenterY;
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared <= maximumSquared + DISTANCE_EPSILON) {
                reachable.add(index);
            }
        }
        if (reachable.isEmpty()) return Optional.empty();
        return Optional.of(
            new Window(
                reachable.getFirst(),
                reachable.getLast(),
                reachable
            )
        );
    }
}
