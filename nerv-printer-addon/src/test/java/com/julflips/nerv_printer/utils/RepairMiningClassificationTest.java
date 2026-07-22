package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairMiningClassificationTest {
    @Test
    void vanillaInstantIsTheOnlyBatchEligibleClassification() {
        RepairMiningClassification vanilla =
            RepairMiningClassification.classify(true, true, true, 0.75F);
        RepairMiningClassification accelerated =
            RepairMiningClassification.classify(false, true, true, 0.75F);
        RepairMiningClassification slow =
            RepairMiningClassification.classify(false, false, true, 0.75F);

        assertEquals(
            RepairMiningClassification.VANILLA_BATCH_INSTANT,
            vanilla
        );
        assertTrue(vanilla.allowsBatchDispatch());
        assertFalse(vanilla.requiresProgressiveContinuation());

        assertEquals(
            RepairMiningClassification.SPEED_MINE_ACCELERATED_PROGRESSIVE,
            accelerated
        );
        assertFalse(accelerated.allowsBatchDispatch());
        assertTrue(accelerated.requiresProgressiveContinuation());

        assertEquals(RepairMiningClassification.SLOW_PROGRESSIVE, slow);
        assertFalse(slow.allowsBatchDispatch());
        assertTrue(slow.requiresProgressiveContinuation());
    }

    @Test
    void acceleratedProgressCanOverlapRouteMovementWithoutAllowingAnotherTarget() {
        RepairMiningClassification vanilla =
            RepairMiningClassification.VANILLA_BATCH_INSTANT;
        RepairMiningClassification accelerated =
            RepairMiningClassification.SPEED_MINE_ACCELERATED_PROGRESSIVE;
        RepairMiningClassification slow =
            RepairMiningClassification.SLOW_PROGRESSIVE;

        assertTrue(vanilla.allowsOwnedRouteMovementOverlap());
        assertTrue(accelerated.allowsOwnedRouteMovementOverlap());
        assertFalse(accelerated.allowsBatchDispatch());
        assertTrue(accelerated.requiresProgressiveContinuation());
        assertFalse(slow.allowsOwnedRouteMovementOverlap());
    }

    @Test
    void speedMineThresholdIsStrictAndRequiresAnAdmittedLease() {
        assertEquals(
            RepairMiningClassification.SLOW_PROGRESSIVE,
            RepairMiningClassification.classify(
                false,
                true,
                true,
                0.5F
            )
        );
        assertEquals(
            RepairMiningClassification.SLOW_PROGRESSIVE,
            RepairMiningClassification.classify(
                false,
                false,
                true,
                0.75F
            )
        );
        assertEquals(
            RepairMiningClassification.SLOW_PROGRESSIVE,
            RepairMiningClassification.classify(
                false,
                true,
                false,
                0.75F
            )
        );
    }

    @Test
    void acceleratedTargetRemainsAControllerBarrierUntilAir() {
        RepairMineController<String> controller = new RepairMineController<>(
            new RepairMineController.RetryPolicy(2, 3, 20)
        );
        controller.observe("accelerated", RepairMineController.Observation.WRONG, 0);
        controller.observe("later", RepairMineController.Observation.WRONG, 0);

        RepairMiningClassification accelerated =
            RepairMiningClassification.classify(false, true, true, 0.75F);
        RepairMiningClassification later =
            RepairMiningClassification.classify(true, true, true, 1.0F);
        RepairMineController.BreakBatch<String> first =
            controller.planBreakBatch(
                List.of(
                    candidate("accelerated", accelerated),
                    candidate("later", later)
                ),
                2,
                0
            );

        assertEquals(
            List.of("accelerated"),
            first.decisions().stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
        assertEquals(
            RepairMineController.StopReason.SLOW_TARGET,
            first.stopReason()
        );
        assertTrue(
            controller.recordBreakDispatched(first.decisions().getFirst(), 0)
        );

        RepairMineController.BreakBatch<String> waiting =
            controller.planBreakBatch(
                List.of(
                    candidate("accelerated", accelerated),
                    candidate("later", later)
                ),
                2,
                1
            );
        assertTrue(waiting.decisions().isEmpty());
        assertEquals(
            RepairMineController.StopReason.SLOW_TARGET,
            waiting.stopReason()
        );

        controller.observe(
            "accelerated",
            RepairMineController.Observation.AIR,
            2
        );
        RepairMineController.BreakBatch<String> afterAir =
            controller.planBreakBatch(
                List.of(
                    candidate("accelerated", accelerated),
                    candidate("later", later)
                ),
                2,
                2
            );
        assertEquals(
            List.of("later"),
            afterAir.decisions().stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
    }

    @Test
    void vanillaInstantTargetsCanStillShareABatch() {
        RepairMineController<String> controller = new RepairMineController<>(
            new RepairMineController.RetryPolicy(2, 3, 20)
        );
        controller.observe("first", RepairMineController.Observation.WRONG, 0);
        controller.observe("second", RepairMineController.Observation.WRONG, 0);
        RepairMiningClassification classification =
            RepairMiningClassification.classify(true, false, false, 1.0F);

        RepairMineController.BreakBatch<String> batch =
            controller.planBreakBatch(
                List.of(
                    candidate("first", classification),
                    candidate("second", classification)
                ),
                2,
                0
            );

        assertEquals(
            List.of("first", "second"),
            batch.decisions().stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
        assertEquals(
            RepairMineController.StopReason.EXHAUSTED,
            batch.stopReason()
        );
    }

    private static RepairMineController.BreakCandidate<String> candidate(
        String target,
        RepairMiningClassification classification
    ) {
        return new RepairMineController.BreakCandidate<>(
            target,
            classification.allowsBatchDispatch()
        );
    }
}
