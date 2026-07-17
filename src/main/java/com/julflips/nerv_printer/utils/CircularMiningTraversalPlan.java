package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure checkpoint/removal ordering for a validated U recovery result.
 */
public final class CircularMiningTraversalPlan {
    private CircularMiningTraversalPlan() {
    }

    public enum Endpoint {
        START,
        END
    }

    public record Step(int standIndex, int removeIndex) {
        public boolean removesBlock() {
            return removeIndex >= 0;
        }
    }

    public record Plan(
        Endpoint entry,
        Endpoint exit,
        List<Step> steps,
        int finalRemoveIndex
    ) {
        public Plan {
            steps = List.copyOf(steps);
        }
    }

    public static Plan create(
        int targetCount,
        CircularMiningRecoveryPlan.Result recovery
    ) {
        if (targetCount <= 0) throw new IllegalArgumentException("Target count must be positive.");
        Objects.requireNonNull(recovery, "recovery");
        int lastIndex = targetCount - 1;
        ArrayList<Step> steps = new ArrayList<>();

        return switch (recovery.mode()) {
            case FORWARD -> {
                steps.add(new Step(0, -1));
                for (int index = 1; index <= lastIndex; index++) {
                    steps.add(new Step(index, index - 1));
                }
                yield new Plan(Endpoint.START, Endpoint.END, steps, lastIndex);
            }
            case RECOVER_FROM_END -> {
                validateRange(recovery, lastIndex);
                for (int index = lastIndex; index >= recovery.firstWalkable(); index--) {
                    steps.add(new Step(index, -1));
                }
                for (int index = recovery.firstWalkable() + 1; index <= lastIndex; index++) {
                    steps.add(new Step(index, index - 1));
                }
                yield new Plan(Endpoint.END, Endpoint.END, steps, lastIndex);
            }
            case RECOVER_FROM_START -> {
                validateRange(recovery, lastIndex);
                for (int index = 0; index <= recovery.lastWalkable(); index++) {
                    steps.add(new Step(index, -1));
                }
                for (int index = recovery.lastWalkable() - 1; index >= 0; index--) {
                    steps.add(new Step(index, index + 1));
                }
                yield new Plan(Endpoint.START, Endpoint.START, steps, 0);
            }
            case COMPLETE, FALLBACK ->
                throw new IllegalArgumentException(
                    "Cannot create a traversal for mode " + recovery.mode() + "."
                );
        };
    }

    private static void validateRange(
        CircularMiningRecoveryPlan.Result recovery,
        int lastIndex
    ) {
        if (recovery.firstWalkable() < 0
            || recovery.lastWalkable() < recovery.firstWalkable()
            || recovery.lastWalkable() > lastIndex) {
            throw new IllegalArgumentException("Recovery range is outside the U route.");
        }
    }
}
