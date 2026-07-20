package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedTeardownMineControllerTest {
    @Test
    void ownsExactlyOnePositionAndExpectedBlockIdentity() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target first = target("1,64,1", "obsidian");
        Target changedExpectation = target("1,64,1", "stone");
        Target later = target("2,64,1", "obsidian");

        assertEquals(
            OrderedTeardownMineController.ClaimResult.ACQUIRED,
            controller.claim(first.value(), vanillaInstant(), 7)
        );
        assertEquals(
            OrderedTeardownMineController.ClaimResult.ALREADY_OWNED,
            controller.claim(first.value(), vanillaInstant(), 7)
        );
        assertEquals(
            OrderedTeardownMineController.ClaimResult
                .BLOCKED_BY_OWNED_TARGET,
            controller.claim(first.value(), slowProgressive(), 8)
        );
        assertEquals(
            OrderedTeardownMineController.ClaimResult
                .BLOCKED_BY_OWNED_TARGET,
            controller.claim(changedExpectation.value(), vanillaInstant(), 8)
        );
        assertEquals(
            OrderedTeardownMineController.ClaimResult
                .BLOCKED_BY_OWNED_TARGET,
            controller.claim(later.value(), vanillaInstant(), 8)
        );

        assertTrue(controller.hasOwnedTarget());
        assertEquals(first.value(), controller.target().orElseThrow());
        assertEquals(
            7,
            controller.snapshot()
                .orElseThrow()
                .latestAuthoritativeObservationSequence()
        );
    }

    @Test
    void initialDispatchConsumesOnePermitAndReservationIsStable() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), vanillaInstant(), 10);
        AtomicInteger permitCalls = new AtomicInteger();

        OrderedTeardownMineController.Plan<String, String> blocked =
            controller.planNext(() -> {
                permitCalls.incrementAndGet();
                return false;
            });
        assertEquals(
            OrderedTeardownMineController.PlanAction
                .WAITING_FOR_ACTION_BUDGET,
            blocked.action()
        );
        assertTrue(blocked.dispatch().isEmpty());
        assertEquals(1, permitCalls.get());
        assertEquals(
            OrderedTeardownMineController.Phase
                .PENDING_INITIAL_DISPATCH,
            controller.snapshot().orElseThrow().phase()
        );

        OrderedTeardownMineController.Plan<String, String> admitted =
            controller.planNext(() -> {
                permitCalls.incrementAndGet();
                return true;
            });
        OrderedTeardownMineController.DispatchDecision<String, String>
            decision = admitted.dispatch().orElseThrow();
        assertEquals(
            OrderedTeardownMineController.PlanAction.DISPATCH,
            admitted.action()
        );
        assertEquals(
            OrderedTeardownMineController.DispatchKind.INITIAL,
            decision.kind()
        );
        assertEquals(1, decision.attemptNumber());
        assertEquals(2, permitCalls.get());

        OrderedTeardownMineController.Plan<String, String> reserved =
            controller.planNext(OrderedTeardownMineControllerTest::failPermit);
        assertEquals(
            OrderedTeardownMineController.PlanAction
                .DISPATCH_ALREADY_RESERVED,
            reserved.action()
        );
        assertEquals(decision, reserved.dispatch().orElseThrow());

        assertTrue(controller.recordDispatched(decision, 10));
        OrderedTeardownMineController.Snapshot<String, String> submitted =
            controller.snapshot().orElseThrow();
        assertEquals(1, submitted.attempts());
        assertEquals(10, submitted.latestSubmissionSequence());
        assertEquals(
            OrderedTeardownMineController.Phase
                .AWAITING_AUTHORITATIVE_AIR,
            submitted.phase()
        );
    }

    @Test
    void onlyVanillaInstantWaitsWithoutProgressiveContinuation() {
        assertPostDispatchPlan(
            vanillaInstant(),
            OrderedTeardownMineController.PlanAction
                .AWAIT_AUTHORITATIVE_AIR
        );
        assertPostDispatchPlan(
            speedMineProgressive(),
            OrderedTeardownMineController.PlanAction
                .CONTINUE_PROGRESSIVE
        );
        assertPostDispatchPlan(
            slowProgressive(),
            OrderedTeardownMineController.PlanAction
                .CONTINUE_PROGRESSIVE
        );
    }

    @Test
    void completionRequiresStrictlyNewerAuthoritativeAir() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), speedMineProgressive(), 10);

        assertEquals(
            OrderedTeardownMineController.ObservationResult
                .PREDICTED_IGNORED,
            controller.observe(
                OrderedTeardownMineController.Observation.predictedAir(
                    target.value(),
                    100
                )
            )
        );
        assertEquals(
            10,
            controller.snapshot()
                .orElseThrow()
                .latestAuthoritativeObservationSequence()
        );

        assertEquals(
            OrderedTeardownMineController.ObservationResult
                .PRE_SUBMISSION_AIR_IGNORED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    11
                )
            )
        );
        OrderedTeardownMineController.DispatchDecision<String, String>
            decision = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.recordDispatched(decision, 11));

        assertEquals(
            OrderedTeardownMineController.ObservationResult.STALE_IGNORED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    11
                )
            )
        );
        assertEquals(
            OrderedTeardownMineController.ObservationResult.NON_AIR,
            controller.observe(
                OrderedTeardownMineController.Observation
                    .authoritativeNonAir(target.value(), 12)
            )
        );
        assertEquals(
            OrderedTeardownMineController.ObservationResult.STALE_IGNORED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    11
                )
            )
        );
        assertEquals(
            OrderedTeardownMineController.Phase
                .AWAITING_AUTHORITATIVE_AIR,
            controller.snapshot().orElseThrow().phase()
        );

        assertEquals(
            OrderedTeardownMineController.ObservationResult.COMPLETED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    13
                )
            )
        );
        assertEquals(
            OrderedTeardownMineController.Phase.COMPLETED,
            controller.snapshot().orElseThrow().phase()
        );
        assertEquals(
            OrderedTeardownMineController.PlanAction.COMPLETED,
            controller.planNext(
                OrderedTeardownMineControllerTest::failPermit
            ).action()
        );
        assertEquals(
            OrderedTeardownMineController.ObservationResult
                .ALREADY_COMPLETED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    14
                )
            )
        );
    }

    @Test
    void observationsForAnotherExpectedIdentityCannotAdvanceTheLease() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target owned = target("1,64,1", "obsidian");
        Target samePositionDifferentBlock = target("1,64,1", "stone");
        controller.claim(owned.value(), vanillaInstant(), 4);
        OrderedTeardownMineController.DispatchDecision<String, String>
            decision = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.recordDispatched(decision, 4));

        assertEquals(
            OrderedTeardownMineController.ObservationResult
                .DIFFERENT_TARGET,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    samePositionDifferentBlock.value(),
                    100
                )
            )
        );
        assertEquals(
            4,
            controller.snapshot()
                .orElseThrow()
                .latestAuthoritativeObservationSequence()
        );
        assertEquals(
            OrderedTeardownMineController.Phase
                .AWAITING_AUTHORITATIVE_AIR,
            controller.snapshot().orElseThrow().phase()
        );
    }

    @Test
    void retryNeedsANewPermitAndANewSubmissionSequence() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), vanillaInstant(), 0);
        OrderedTeardownMineController.DispatchDecision<String, String>
            initial = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.recordDispatched(initial, 0));
        assertTrue(controller.requestRetry());
        assertTrue(controller.requestRetry());

        AtomicInteger retryPermitCalls = new AtomicInteger();
        assertEquals(
            OrderedTeardownMineController.PlanAction
                .WAITING_FOR_ACTION_BUDGET,
            controller.planNext(() -> {
                retryPermitCalls.incrementAndGet();
                return false;
            }).action()
        );
        OrderedTeardownMineController.DispatchDecision<String, String> retry =
            controller.planNext(() -> {
                retryPermitCalls.incrementAndGet();
                return true;
            }).dispatch().orElseThrow();

        assertEquals(2, retryPermitCalls.get());
        assertEquals(
            OrderedTeardownMineController.DispatchKind.RETRY,
            retry.kind()
        );
        assertEquals(2, retry.attemptNumber());
        assertNotEquals(initial.reservationId(), retry.reservationId());
        assertTrue(controller.recordDispatched(retry, 1));
        assertFalse(controller.recordDispatched(initial, 1));

        assertEquals(
            OrderedTeardownMineController.ObservationResult
                .PRE_SUBMISSION_AIR_IGNORED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    1
                )
            )
        );
        assertEquals(
            OrderedTeardownMineController.ObservationResult.COMPLETED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    2
                )
            )
        );
        assertFalse(controller.requestRetry());
    }

    @Test
    void authoritativeAirCanCompleteWhileARetryIsPendingOrReserved() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), slowProgressive(), 5);
        OrderedTeardownMineController.DispatchDecision<String, String>
            initial = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.recordDispatched(initial, 5));
        assertTrue(controller.requestRetry());
        OrderedTeardownMineController.DispatchDecision<String, String>
            reservedRetry = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();

        assertEquals(
            OrderedTeardownMineController.ObservationResult.COMPLETED,
            controller.observe(
                OrderedTeardownMineController.Observation.authoritativeAir(
                    target.value(),
                    6
                )
            )
        );
        assertFalse(controller.recordDispatched(reservedRetry, 6));
        assertEquals(
            OrderedTeardownMineController.PlanAction.COMPLETED,
            controller.planNext(
                OrderedTeardownMineControllerTest::failPermit
            ).action()
        );
    }

    @Test
    void rejectedDispatchDoesNotCountAndOldReservationStaysInvalid() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), vanillaInstant(), 3);

        OrderedTeardownMineController.DispatchDecision<String, String>
            rejected = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.rejectDispatch(rejected));
        assertFalse(controller.rejectDispatch(rejected));
        assertEquals(0, controller.snapshot().orElseThrow().attempts());
        assertEquals(
            OrderedTeardownMineController.Phase
                .PENDING_INITIAL_DISPATCH,
            controller.snapshot().orElseThrow().phase()
        );

        OrderedTeardownMineController.DispatchDecision<String, String>
            replacement = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertNotEquals(rejected.reservationId(), replacement.reservationId());
        assertFalse(controller.recordDispatched(rejected, 3));
        assertTrue(controller.recordDispatched(replacement, 3));
    }

    @Test
    void dispatchCannotBeRecordedBehindAnAlreadyObservedRevision() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), vanillaInstant(), 20);
        OrderedTeardownMineController.DispatchDecision<String, String>
            decision = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();

        assertFalse(controller.recordDispatched(decision, 19));
        assertEquals(
            OrderedTeardownMineController.PlanAction
                .DISPATCH_ALREADY_RESERVED,
            controller.planNext(
                OrderedTeardownMineControllerTest::failPermit
            ).action()
        );
        assertTrue(controller.recordDispatched(decision, 20));
    }

    @Test
    void abandonAndResetInvalidateDecisionsWithoutReusingLeaseIds() {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), vanillaInstant(), 9);
        OrderedTeardownMineController.DispatchDecision<String, String> old =
            controller.planNext(() -> true).dispatch().orElseThrow();

        OrderedTeardownMineController.Snapshot<String, String> abandoned =
            controller.abandon().orElseThrow();
        assertEquals(
            OrderedTeardownMineController.Phase.DISPATCH_RESERVED,
            abandoned.phase()
        );
        assertFalse(controller.hasOwnedTarget());
        assertFalse(controller.recordDispatched(old, 9));
        assertEquals(
            OrderedTeardownMineController.PlanAction.NO_TARGET,
            controller.planNext(
                OrderedTeardownMineControllerTest::failPermit
            ).action()
        );

        controller.claim(target.value(), vanillaInstant(), 0);
        OrderedTeardownMineController.DispatchDecision<String, String> fresh =
            controller.planNext(() -> true).dispatch().orElseThrow();
        assertTrue(fresh.leaseId() > old.leaseId());
        assertNotEquals(fresh.reservationId(), old.reservationId());
        assertFalse(controller.recordDispatched(old, 0));

        controller.reset();
        assertTrue(controller.snapshot().isEmpty());
        assertTrue(controller.abandon().isEmpty());
        assertFalse(controller.recordDispatched(fresh, 0));
    }

    @Test
    void invalidIdentityAndSequenceInputsFailClosed() {
        assertThrows(
            NullPointerException.class,
            () -> new OrderedTeardownMineController.Target<>(
                null,
                "obsidian"
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> new OrderedTeardownMineController.Target<>(
                "1,64,1",
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new OrderedTeardownMineController.Observation<>(
                target("1,64,1", "obsidian").value(),
                -1,
                OrderedTeardownMineController.ObservationSource.AUTHORITATIVE,
                OrderedTeardownMineController.ObservedBlock.AIR
            )
        );

        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        assertThrows(
            IllegalArgumentException.class,
            () -> controller.claim(
                target("1,64,1", "obsidian").value(),
                vanillaInstant(),
                -1
            )
        );
    }

    private static void assertPostDispatchPlan(
        RepairMiningClassification classification,
        OrderedTeardownMineController.PlanAction expected
    ) {
        OrderedTeardownMineController<String, String> controller =
            new OrderedTeardownMineController<>();
        Target target = target("1,64,1", "obsidian");
        controller.claim(target.value(), classification, 0);
        OrderedTeardownMineController.DispatchDecision<String, String>
            decision = controller.planNext(() -> true)
                .dispatch()
                .orElseThrow();
        assertTrue(controller.recordDispatched(decision, 0));

        assertEquals(
            expected,
            controller.planNext(
                OrderedTeardownMineControllerTest::failPermit
            ).action()
        );
    }

    private static boolean failPermit() {
        throw new AssertionError(
            "This controller action must not request an action-budget permit."
        );
    }

    private static RepairMiningClassification vanillaInstant() {
        return RepairMiningClassification.VANILLA_BATCH_INSTANT;
    }

    private static RepairMiningClassification speedMineProgressive() {
        return RepairMiningClassification
            .SPEED_MINE_ACCELERATED_PROGRESSIVE;
    }

    private static RepairMiningClassification slowProgressive() {
        return RepairMiningClassification.SLOW_PROGRESSIVE;
    }

    private static Target target(String key, String expectedBlock) {
        return new Target(
            new OrderedTeardownMineController.Target<>(
                key,
                expectedBlock
            )
        );
    }

    private record Target(
        OrderedTeardownMineController.Target<String, String> value
    ) {
    }
}
