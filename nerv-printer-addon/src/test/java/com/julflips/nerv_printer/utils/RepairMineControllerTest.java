package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairMineControllerTest {
    @Test
    void trueInstantTargetsCanShareOneBreakBatch() {
        RepairMineController<String> controller = controller(2, 3, 20);
        controller.observe("a", RepairMineController.Observation.WRONG, 0);
        controller.observe("b", RepairMineController.Observation.WRONG, 0);
        controller.observe("c", RepairMineController.Observation.WRONG, 0);

        RepairMineController.BreakBatch<String> batch =
            controller.planBreakBatch(
                List.of(instant("a"), instant("b"), instant("c")),
                3,
                0
            );

        assertEquals(
            List.of("a", "b", "c"),
            batch.decisions().stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
        assertEquals(
            RepairMineController.StopReason.EXHAUSTED,
            batch.stopReason()
        );

        for (RepairMineController.BreakDecision<String> decision
            : batch.decisions()) {
            assertTrue(controller.recordBreakDispatched(decision, 0));
        }

        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("a").orElseThrow()
        );
        assertEquals(1, controller.snapshot("a").orElseThrow().attempts());
        assertFalse(controller.readyToPlaceInOrder(
            List.of("a", "b", "c"),
            3
        ).contains("a"));
    }

    @Test
    void slowTargetIsTheLastBreakAdmittedAndRemainsABarrier() {
        RepairMineController<String> controller = controller(2, 3, 20);
        controller.observe("instant", RepairMineController.Observation.WRONG, 0);
        controller.observe("slow", RepairMineController.Observation.WRONG, 0);
        controller.observe("later", RepairMineController.Observation.WRONG, 0);

        RepairMineController.BreakBatch<String> first =
            controller.planBreakBatch(
                List.of(
                    instant("instant"),
                    slow("slow"),
                    instant("later")
                ),
                5,
                0
            );

        assertEquals(
            List.of("instant", "slow"),
            first.decisions().stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
        assertEquals(
            RepairMineController.StopReason.SLOW_TARGET,
            first.stopReason()
        );
        first.decisions().forEach(
            decision -> assertTrue(
                controller.recordBreakDispatched(decision, 0)
            )
        );

        RepairMineController.BreakBatch<String> waiting =
            controller.planBreakBatch(
                List.of(
                    instant("instant"),
                    slow("slow"),
                    instant("later")
                ),
                5,
                1
            );

        assertTrue(waiting.decisions().isEmpty());
        assertEquals(
            RepairMineController.StopReason.SLOW_TARGET,
            waiting.stopReason()
        );

        RepairMineController<String> slowFirst = controller(2, 3, 20);
        slowFirst.observe("slow", RepairMineController.Observation.WRONG, 0);
        slowFirst.observe("later", RepairMineController.Observation.WRONG, 0);
        assertEquals(
            List.of("slow"),
            slowFirst.planBreakBatch(
                    List.of(slow("slow"), instant("later")),
                    5,
                    0
                )
                .decisions()
                .stream()
                .map(RepairMineController.BreakDecision::target)
                .toList()
        );
    }

    @Test
    void onlyObservedAirAdvancesBreakingToReadyToPlace() {
        RepairMineController<String> controller = controller(2, 3, 20);
        controller.observe("repair", RepairMineController.Observation.WRONG, 0);
        RepairMineController.BreakDecision<String> decision =
            controller.planBreakBatch(
                    List.of(instant("repair")),
                    1,
                    0
                )
                .decisions()
                .getFirst();

        assertTrue(controller.recordBreakDispatched(decision, 0));
        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("repair").orElseThrow()
        );

        controller.observe("repair", RepairMineController.Observation.WRONG, 1);
        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("repair").orElseThrow()
        );

        controller.observe("repair", RepairMineController.Observation.AIR, 2);
        assertEquals(
            RepairMineController.Phase.READY_TO_PLACE,
            controller.phaseOf("repair").orElseThrow()
        );
        assertEquals(
            List.of("repair"),
            controller.readyToPlaceInOrder(
                List.of("other", "repair", "repair"),
                2
            )
        );

        controller.observe("repair", RepairMineController.Observation.AIR, 3);
        assertEquals(
            RepairMineController.Phase.READY_TO_PLACE,
            controller.phaseOf("repair").orElseThrow()
        );

        controller.observe(
            "repair",
            RepairMineController.Observation.EXPECTED,
            4
        );
        assertTrue(controller.phaseOf("repair").isEmpty());
    }

    @Test
    void persistentWrongBlockRetriesThenExpiresWithoutConfirmation() {
        RepairMineController<String> controller = controller(2, 2, 20);
        controller.observe("stuck", RepairMineController.Observation.WRONG, 0);

        RepairMineController.BreakDecision<String> first =
            controller.planBreakBatch(
                    List.of(instant("stuck")),
                    1,
                    0
                )
                .decisions()
                .getFirst();
        assertEquals(1, first.attemptNumber());
        assertTrue(controller.recordBreakDispatched(first, 0));

        controller.observe("stuck", RepairMineController.Observation.WRONG, 1);
        assertTrue(
            controller.planBreakBatch(
                List.of(instant("stuck")),
                1,
                1
            ).decisions().isEmpty()
        );

        controller.observe("stuck", RepairMineController.Observation.WRONG, 2);
        RepairMineController.BreakDecision<String> retry =
            controller.planBreakBatch(
                    List.of(instant("stuck")),
                    1,
                    2
                )
                .decisions()
                .getFirst();
        assertEquals(2, retry.attemptNumber());
        assertTrue(controller.recordBreakDispatched(retry, 2));

        controller.observe("stuck", RepairMineController.Observation.WRONG, 3);
        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("stuck").orElseThrow()
        );
        controller.observe("stuck", RepairMineController.Observation.WRONG, 4);
        assertEquals(
            RepairMineController.Phase.EXPIRED,
            controller.phaseOf("stuck").orElseThrow()
        );
        assertEquals(2, controller.snapshot("stuck").orElseThrow().attempts());

        controller.observe("later", RepairMineController.Observation.WRONG, 4);
        RepairMineController.BreakBatch<String> blocked =
            controller.planBreakBatch(
                List.of(instant("stuck"), instant("later")),
                2,
                4
            );
        assertTrue(blocked.decisions().isEmpty());
        assertEquals(
            RepairMineController.StopReason.EXPIRED_TARGET,
            blocked.stopReason()
        );
    }

    @Test
    void absoluteTimeoutExpiresAnUnconfirmedRepair() {
        RepairMineController<String> controller = controller(10, 5, 3);
        controller.observe("stuck", RepairMineController.Observation.WRONG, 0);
        RepairMineController.BreakDecision<String> decision =
            controller.planBreakBatch(
                    List.of(instant("stuck")),
                    1,
                    0
                )
                .decisions()
                .getFirst();
        assertTrue(controller.recordBreakDispatched(decision, 0));

        controller.advance(2);
        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("stuck").orElseThrow()
        );
        controller.advance(3);
        assertEquals(
            RepairMineController.Phase.EXPIRED,
            controller.phaseOf("stuck").orElseThrow()
        );
    }

    @Test
    void readyToPlaceExpiresWhenReplacementCannotBeConfirmed() {
        RepairMineController<String> controller = controller(2, 3, 4);
        controller.observe("repair", RepairMineController.Observation.WRONG, 0);
        controller.observe("repair", RepairMineController.Observation.AIR, 1);

        controller.advance(4);
        assertEquals(
            RepairMineController.Phase.READY_TO_PLACE,
            controller.phaseOf("repair").orElseThrow()
        );
        controller.advance(5);
        assertEquals(
            RepairMineController.Phase.EXPIRED,
            controller.phaseOf("repair").orElseThrow()
        );
    }

    @Test
    void authoritativeWrongAfterAirStartsANewRepairCycle() {
        RepairMineController<String> controller = controller(2, 3, 20);
        controller.observe("repair", RepairMineController.Observation.WRONG, 0);
        RepairMineController.BreakDecision<String> first =
            controller.planBreakBatch(
                    List.of(instant("repair")),
                    1,
                    0
                )
                .decisions()
                .getFirst();
        assertTrue(controller.recordBreakDispatched(first, 0));
        controller.observe("repair", RepairMineController.Observation.AIR, 1);
        long firstCycle =
            controller.snapshot("repair").orElseThrow().cycleId();

        controller.observe("repair", RepairMineController.Observation.WRONG, 2);

        RepairMineController.TargetSnapshot<String> restarted =
            controller.snapshot("repair").orElseThrow();
        assertEquals(RepairMineController.Phase.BREAKING, restarted.phase());
        assertEquals(0, restarted.attempts());
        assertTrue(restarted.cycleId() > firstCycle);
    }

    @Test
    void slowProgressExtendsIdleTimeoutButNotMaximumLifetime() {
        RepairMineController<String> controller =
            new RepairMineController<>(
                new RepairMineController.RetryPolicy(2, 3, 3, 8)
            );
        controller.observe("slow", RepairMineController.Observation.WRONG, 0);
        RepairMineController.BreakDecision<String> first =
            controller.planBreakBatch(
                    List.of(slow("slow")),
                    1,
                    0
                )
                .decisions()
                .getFirst();
        assertTrue(controller.recordBreakDispatched(first, 0));

        for (long tick = 1; tick < 8; tick++) {
            assertTrue(controller.recordSlowProgress("slow", tick));
            assertEquals(
                RepairMineController.Phase.BREAKING,
                controller.phaseOf("slow").orElseThrow()
            );
        }

        assertFalse(controller.recordSlowProgress("slow", 8));
        assertEquals(
            RepairMineController.Phase.EXPIRED,
            controller.phaseOf("slow").orElseThrow()
        );
    }

    @Test
    void recoveryResetReleasesOwnershipAndStartsAFreshTimeline() {
        RepairMineController<String> controller = controller(2, 3, 20);
        controller.observe("repair", RepairMineController.Observation.WRONG, 100);
        RepairMineController.BreakDecision<String> oldDecision =
            controller.planBreakBatch(
                    List.of(instant("repair")),
                    1,
                    100
                )
                .decisions()
                .getFirst();

        controller.reset();

        assertEquals(0, controller.size());
        assertFalse(controller.hasExpiredTargets());
        controller.observe("repair", RepairMineController.Observation.WRONG, 0);
        assertFalse(controller.recordBreakDispatched(oldDecision, 0));
        assertEquals(
            0,
            controller.snapshot("repair").orElseThrow().attempts()
        );
        assertEquals(
            RepairMineController.Phase.BREAKING,
            controller.phaseOf("repair").orElseThrow()
        );
    }

    private static RepairMineController<String> controller(
        int retryDelay,
        int maxAttempts,
        int timeout
    ) {
        return new RepairMineController<>(
            new RepairMineController.RetryPolicy(
                retryDelay,
                maxAttempts,
                timeout
            )
        );
    }

    private static RepairMineController.BreakCandidate<String> instant(
        String target
    ) {
        return new RepairMineController.BreakCandidate<>(target, true);
    }

    private static RepairMineController.BreakCandidate<String> slow(
        String target
    ) {
        return new RepairMineController.BreakCandidate<>(target, false);
    }
}
