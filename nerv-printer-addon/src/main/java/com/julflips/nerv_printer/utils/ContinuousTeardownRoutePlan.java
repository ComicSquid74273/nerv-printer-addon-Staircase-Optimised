package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Converts ordered teardown work into an internal support-cursor plan.
 *
 * <p>Unlike the global navigation queue, a stage is not a user-visible
 * checkpoint. It records which intact support the player must reach and which
 * targets become safe to remove from there. This mirrors circular printing:
 * structural entry/exit checkpoints remain in the navigation queue while the
 * exact U supports are consumed by a monotonic runtime cursor.</p>
 */
public final class ContinuousTeardownRoutePlan {
    private ContinuousTeardownRoutePlan() {
    }

    public record Stage<T>(T support, List<T> breakTargets) {
        public Stage {
            Objects.requireNonNull(support, "support");
            breakTargets = List.copyOf(
                Objects.requireNonNull(breakTargets, "breakTargets")
            );
            for (T target : breakTargets) {
                Objects.requireNonNull(target, "break target");
                if (support.equals(target)) {
                    throw new IllegalArgumentException(
                        "A teardown stage cannot remove its standing support."
                    );
                }
            }
        }
    }

    public record Plan<T>(List<Stage<T>> stages) {
        public Plan {
            stages = List.copyOf(
                Objects.requireNonNull(stages, "stages")
            );
            if (stages.isEmpty()) {
                throw new IllegalArgumentException(
                    "A continuous teardown route cannot be empty."
                );
            }
        }
    }

    /**
     * Builds optional entry-route stages, one internal stage per ordered
     * standing support, and the ordered exit supports. Endpoint entry and
     * exit supply the same exterior approach/departure plus north-walkway
     * supports used by printing; local resume supplies only the already
     * occupied U support. Remotely assigned targets are grouped with their
     * destination support instead of creating repeated navigation checkpoints
     * there.
     */
    public static <T> Plan<T> create(
        List<T> orderedRouteTargets,
        List<CircularMiningTraversalPlan.Step> traversalSteps,
        Map<Integer, ? extends List<T>> remoteTargetsBySupport,
        int remoteResumeSupportIndex,
        List<T> entrySupports,
        List<T> exitSupports,
        T finalBreakTarget
    ) {
        Objects.requireNonNull(
            orderedRouteTargets,
            "orderedRouteTargets"
        );
        Objects.requireNonNull(traversalSteps, "traversalSteps");
        Objects.requireNonNull(
            remoteTargetsBySupport,
            "remoteTargetsBySupport"
        );
        Objects.requireNonNull(entrySupports, "entrySupports");
        Objects.requireNonNull(exitSupports, "exitSupports");
        Objects.requireNonNull(finalBreakTarget, "finalBreakTarget");
        if (orderedRouteTargets.isEmpty() || traversalSteps.isEmpty()) {
            throw new IllegalArgumentException(
                "A teardown route requires targets and traversal steps."
            );
        }
        if (exitSupports.isEmpty()) {
            throw new IllegalArgumentException(
                "A teardown route requires an exit support."
            );
        }
        if (remoteResumeSupportIndex < 0
            && remoteResumeSupportIndex != Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "The remote teardown resume index is invalid."
            );
        }

        ArrayList<Stage<T>> stages = new ArrayList<>(
            traversalSteps.size()
                + entrySupports.size()
                + exitSupports.size()
        );
        HashSet<T> scheduledTargets = new HashSet<>();
        // Always wait until the traversal actually enters the resume support.
        // Endpoint traversal reaches index zero immediately; a local recovery
        // can first walk backward through higher indices and must not execute
        // their forward-pass remote work at the start checkpoint.
        boolean remoteScheduleEnabled = false;

        validateIndex(
            traversalSteps.getFirst().standIndex(),
            orderedRouteTargets.size(),
            "first standing"
        );
        T firstRouteSupport = Objects.requireNonNull(
            orderedRouteTargets.get(
                traversalSteps.getFirst().standIndex()
            ),
            "first route support"
        );
        for (T entrySupport : entrySupports) {
            T support = Objects.requireNonNull(
                entrySupport,
                "entry support"
            );
            if ((stages.isEmpty()
                    || !stages.getLast().support().equals(support))
                && !support.equals(firstRouteSupport)) {
                stages.add(new Stage<>(support, List.of()));
            }
        }

        for (CircularMiningTraversalPlan.Step step : traversalSteps) {
            validateIndex(
                step.standIndex(),
                orderedRouteTargets.size(),
                "standing"
            );
            if (!remoteScheduleEnabled
                && step.standIndex() == remoteResumeSupportIndex) {
                remoteScheduleEnabled = true;
            }

            T support = Objects.requireNonNull(
                orderedRouteTargets.get(step.standIndex()),
                "route support"
            );
            ArrayList<T> stageTargets = new ArrayList<>();
            // The walked U has the same priority as printing's primary
            // traversal lane. Reach-assigned work may consume remaining
            // capacity only after the support behind the bot is owned.
            if (step.removesBlock()) {
                validateIndex(
                    step.removeIndex(),
                    orderedRouteTargets.size(),
                    "removal"
                );
                addUniqueTarget(
                    stageTargets,
                    scheduledTargets,
                    orderedRouteTargets.get(step.removeIndex())
                );
            }
            if (remoteScheduleEnabled) {
                List<T> remoteTargets =
                    remoteTargetsBySupport.get(step.standIndex());
                if (remoteTargets == null) {
                    remoteTargets = List.of();
                }
                for (T remote : remoteTargets) {
                    addUniqueTarget(
                        stageTargets,
                        scheduledTargets,
                        remote
                    );
                }
            }
            stages.add(new Stage<>(support, stageTargets));
        }

        for (int index = 0; index < exitSupports.size(); index++) {
            T support = Objects.requireNonNull(
                exitSupports.get(index),
                "exit support"
            );
            List<T> breakTargets;
            if (index == 0) {
                ArrayList<T> finalTargets = new ArrayList<>(1);
                addUniqueTarget(
                    finalTargets,
                    scheduledTargets,
                    finalBreakTarget
                );
                breakTargets = finalTargets;
            } else {
                breakTargets = List.of();
            }
            stages.add(new Stage<>(support, breakTargets));
        }
        validateFutureSupportsRemainIntact(stages);
        return new Plan<>(stages);
    }

    private static <T> void validateFutureSupportsRemainIntact(
        List<Stage<T>> stages
    ) {
        for (int stageIndex = 0;
             stageIndex < stages.size();
             stageIndex++) {
            Stage<T> stage = stages.get(stageIndex);
            for (T target : stage.breakTargets()) {
                for (int futureIndex = stageIndex + 1;
                     futureIndex < stages.size();
                     futureIndex++) {
                    if (target.equals(
                        stages.get(futureIndex).support()
                    )) {
                        throw new IllegalArgumentException(
                            "Teardown work cannot remove a future "
                                + "movement support."
                        );
                    }
                }
            }
        }
    }

    /**
     * Finds whether a target is still required as the current or a future
     * walking support. Runtime dispatch uses this after every movement update,
     * so a delayed teardown action cannot mine beneath the player.
     */
    public static <T> OptionalInt requiredSupportIndexAtOrAfter(
        List<Stage<T>> stages,
        int currentSupportIndex,
        T target
    ) {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(target, "target");
        if (currentSupportIndex < 0
            || currentSupportIndex >= stages.size()) {
            throw new IllegalArgumentException(
                "The current teardown support index is outside its route."
            );
        }
        for (int index = currentSupportIndex;
             index < stages.size();
             index++) {
            if (target.equals(stages.get(index).support())) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private static void validateIndex(
        int index,
        int size,
        String label
    ) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                "The teardown " + label + " index is outside its route."
            );
        }
    }

    private static <T> void addUniqueTarget(
        List<T> stageTargets,
        HashSet<T> scheduledTargets,
        T target
    ) {
        T candidate = Objects.requireNonNull(target, "break target");
        if (!scheduledTargets.add(candidate)) {
            throw new IllegalArgumentException(
                "A teardown target cannot be scheduled more than once."
            );
        }
        stageTargets.add(candidate);
    }
}
