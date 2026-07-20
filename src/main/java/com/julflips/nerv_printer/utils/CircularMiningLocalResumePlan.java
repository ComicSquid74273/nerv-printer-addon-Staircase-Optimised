package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds a teardown continuation from a player who is already standing on a
 * verified remaining U support.
 *
 * <p>The plan first walks, without removing support, to the boundary from
 * which ordered behind-the-player removal is safe. It then consumes the one
 * continuous remaining segment and exits through its connected endpoint.</p>
 */
public final class CircularMiningLocalResumePlan {
    private CircularMiningLocalResumePlan() {
    }

    public record Plan(
        List<CircularMiningTraversalPlan.Step> steps,
        CircularMiningTraversalPlan.Endpoint exit,
        int finalRemoveIndex
    ) {
        public Plan {
            steps = List.copyOf(steps);
            Objects.requireNonNull(exit, "exit");
            if (steps.isEmpty() || finalRemoveIndex < 0) {
                throw new IllegalArgumentException(
                    "A local mining-resume plan cannot be empty."
                );
            }
        }
    }

    public record EgressPlan(
        List<Integer> supportIndices,
        CircularMiningTraversalPlan.Endpoint exit
    ) {
        public EgressPlan {
            supportIndices = List.copyOf(supportIndices);
            Objects.requireNonNull(exit, "exit");
            if (supportIndices.isEmpty()) {
                throw new IllegalArgumentException(
                    "A local mining egress cannot be empty."
                );
            }
        }
    }

    public static Optional<Plan> create(
        int targetCount,
        CircularMiningRecoveryPlan.Result recovery,
        int currentSupportIndex
    ) {
        if (targetCount <= 0) {
            throw new IllegalArgumentException(
                "Target count must be positive."
            );
        }
        Objects.requireNonNull(recovery, "recovery");
        if (currentSupportIndex < 0
            || currentSupportIndex >= targetCount) {
            return Optional.empty();
        }

        int lastIndex = targetCount - 1;
        ArrayList<CircularMiningTraversalPlan.Step> steps =
            new ArrayList<>();
        switch (recovery.mode()) {
            case FORWARD -> {
                for (int index = currentSupportIndex;
                     index >= 0;
                     index--) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            -1
                        )
                    );
                }
                for (int index = 1;
                     index <= lastIndex;
                     index++) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            index - 1
                        )
                    );
                }
                return Optional.of(
                    new Plan(
                        steps,
                        CircularMiningTraversalPlan.Endpoint.END,
                        lastIndex
                    )
                );
            }
            case RECOVER_FROM_END -> {
                if (currentSupportIndex < recovery.firstWalkable()
                    || currentSupportIndex
                        > recovery.lastWalkable()) {
                    return Optional.empty();
                }
                for (int index = currentSupportIndex;
                     index >= recovery.firstWalkable();
                     index--) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            -1
                        )
                    );
                }
                for (int index = recovery.firstWalkable() + 1;
                     index <= lastIndex;
                     index++) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            index - 1
                        )
                    );
                }
                return Optional.of(
                    new Plan(
                        steps,
                        CircularMiningTraversalPlan.Endpoint.END,
                        lastIndex
                    )
                );
            }
            case RECOVER_FROM_START -> {
                if (currentSupportIndex < recovery.firstWalkable()
                    || currentSupportIndex
                        > recovery.lastWalkable()) {
                    return Optional.empty();
                }
                for (int index = currentSupportIndex;
                     index <= recovery.lastWalkable();
                     index++) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            -1
                        )
                    );
                }
                for (int index = recovery.lastWalkable() - 1;
                     index >= 0;
                     index--) {
                    steps.add(
                        new CircularMiningTraversalPlan.Step(
                            index,
                            index + 1
                        )
                    );
                }
                return Optional.of(
                    new Plan(
                        steps,
                        CircularMiningTraversalPlan.Endpoint.START,
                        0
                    )
                );
            }
            case COMPLETE, FALLBACK -> {
                return Optional.empty();
            }
        }
        throw new IllegalStateException(
            "Unhandled mining recovery mode " + recovery.mode() + "."
        );
    }

    /**
     * Returns a removal-free path from the current verified support to the
     * connected north endpoint. This is used before a tool-restock detour.
     */
    public static Optional<EgressPlan> createEgress(
        int targetCount,
        CircularMiningRecoveryPlan.Result recovery,
        int currentSupportIndex
    ) {
        if (create(
            targetCount,
            recovery,
            currentSupportIndex
        ).isEmpty()) {
            return Optional.empty();
        }
        ArrayList<Integer> supports = new ArrayList<>();
        if (recovery.mode()
            == CircularMiningRecoveryPlan.Mode.RECOVER_FROM_END) {
            for (int index = currentSupportIndex;
                 index < targetCount;
                 index++) {
                supports.add(index);
            }
            return Optional.of(
                new EgressPlan(
                    supports,
                    CircularMiningTraversalPlan.Endpoint.END
                )
            );
        }
        for (int index = currentSupportIndex;
             index >= 0;
             index--) {
            supports.add(index);
        }
        return Optional.of(
            new EgressPlan(
                supports,
                CircularMiningTraversalPlan.Endpoint.START
            )
        );
    }
}
