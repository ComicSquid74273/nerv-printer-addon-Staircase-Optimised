package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestockRefillWaitPolicyTest {
    @Test
    void availableSourceDoesNotStartAWait() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.empty(),
            true,
            40,
            20,
            5
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.SOURCE_AVAILABLE,
            decision.action()
        );
        assertTrue(decision.state().isEmpty());
    }

    @Test
    void startsWaitAndSchedulesFirstProbe() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.empty(),
            false,
            100,
            20,
            5
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.START_WAIT,
            decision.action()
        );
        assertEquals(
            new RestockRefillWaitPolicy.State(100, 105),
            decision.state().orElseThrow()
        );
    }

    @Test
    void ordinaryWaitPreservesExactState() {
        RestockRefillWaitPolicy.State state =
            new RestockRefillWaitPolicy.State(100, 105);

        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.of(state),
            false,
            104,
            20,
            5
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.WAIT,
            decision.action()
        );
        assertEquals(state, decision.state().orElseThrow());
    }

    @Test
    void probePreservesStartAndSchedulesFromCurrentTick() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.of(
                new RestockRefillWaitPolicy.State(100, 105)
            ),
            false,
            108,
            30,
            5
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.PROBE,
            decision.action()
        );
        assertEquals(
            new RestockRefillWaitPolicy.State(100, 113),
            decision.state().orElseThrow()
        );
    }

    @Test
    void probeSnapshotReschedulesWithoutChangingAnchoredStart() {
        RestockRefillWaitPolicy.State rescheduled =
            RestockRefillWaitPolicy.rescheduleProbeAfterSnapshot(
                new RestockRefillWaitPolicy.State(100, 120),
                115,
                20
            );

        assertEquals(
            new RestockRefillWaitPolicy.State(100, 135),
            rescheduled
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            evaluate(
                Optional.of(rescheduled),
                false,
                140,
                40,
                20
            ).action()
        );
    }

    @Test
    void inFlightProbeCannotOverlapAndStillReachesDeadline() {
        Optional<RestockRefillWaitPolicy.State> state = Optional.of(
            new RestockRefillWaitPolicy.State(100, 105)
        );

        RestockRefillWaitPolicy.Decision held =
            RestockRefillWaitPolicy.evaluate(
                state,
                false,
                106,
                10,
                5,
                true
            );
        RestockRefillWaitPolicy.Decision timedOut =
            RestockRefillWaitPolicy.evaluate(
                held.state(),
                false,
                110,
                10,
                5,
                true
            );

        assertEquals(
            RestockRefillWaitPolicy.Action.WAIT,
            held.action()
        );
        assertEquals(state, held.state());
        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            timedOut.action()
        );
    }

    @Test
    void irrelevantEvaluationsAndProbesCannotExtendDeadline() {
        Optional<RestockRefillWaitPolicy.State> state =
            evaluate(Optional.empty(), false, 100, 10, 3).state();

        RestockRefillWaitPolicy.Decision firstProbe =
            evaluate(state, false, 103, 10, 3);
        RestockRefillWaitPolicy.Decision secondProbe =
            evaluate(firstProbe.state(), false, 106, 10, 3);
        RestockRefillWaitPolicy.Decision thirdProbe =
            evaluate(secondProbe.state(), false, 109, 10, 3);
        RestockRefillWaitPolicy.Decision timeout =
            evaluate(thirdProbe.state(), false, 110, 10, 3);

        assertEquals(
            RestockRefillWaitPolicy.Action.PROBE,
            firstProbe.action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.PROBE,
            secondProbe.action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.PROBE,
            thirdProbe.action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            timeout.action()
        );
        assertEquals(
            100,
            timeout.state().orElseThrow().startedAtTick()
        );
    }

    @Test
    void timeoutWinsWhenProbeAndDeadlineCoincide() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.of(
                new RestockRefillWaitPolicy.State(100, 110)
            ),
            false,
            110,
            10,
            10
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            decision.action()
        );
    }

    @Test
    void authoritativeSourceAvailabilityWinsAtDeadline() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.of(
                new RestockRefillWaitPolicy.State(100, 105)
            ),
            true,
            110,
            10,
            5
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.SOURCE_AVAILABLE,
            decision.action()
        );
        assertTrue(decision.state().isEmpty());
    }

    @Test
    void lateEvaluationEmitsOneProbeWithoutCatchUpBurst() {
        RestockRefillWaitPolicy.Decision decision = evaluate(
            Optional.of(
                new RestockRefillWaitPolicy.State(0, 3)
            ),
            false,
            8,
            20,
            3
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.PROBE,
            decision.action()
        );
        assertEquals(
            new RestockRefillWaitPolicy.State(0, 11),
            decision.state().orElseThrow()
        );
    }

    @Test
    void timedOutStateRemainsTerminalUntilExplicitReset() {
        RestockRefillWaitPolicy.Decision timedOut = evaluate(
            Optional.of(
                new RestockRefillWaitPolicy.State(5, 8)
            ),
            false,
            15,
            10,
            3
        );
        RestockRefillWaitPolicy.Decision stillTimedOut = evaluate(
            timedOut.state(),
            false,
            20,
            10,
            3
        );
        RestockRefillWaitPolicy.Decision reset = evaluate(
            Optional.empty(),
            false,
            20,
            10,
            3
        );

        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            timedOut.action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            stillTimedOut.action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.START_WAIT,
            reset.action()
        );
        assertEquals(
            20,
            reset.state().orElseThrow().startedAtTick()
        );
    }

    @Test
    void clockArithmeticSaturatesWithoutWrapping() {
        long start = Long.MAX_VALUE - 2;
        RestockRefillWaitPolicy.Decision started = evaluate(
            Optional.empty(),
            false,
            start,
            5,
            5
        );

        assertEquals(
            Long.MAX_VALUE,
            started.state().orElseThrow().nextProbeAtTick()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.WAIT,
            evaluate(
                started.state(),
                false,
                Long.MAX_VALUE - 1,
                5,
                5
            ).action()
        );
        assertEquals(
            RestockRefillWaitPolicy.Action.TIMED_OUT,
            evaluate(
                started.state(),
                false,
                Long.MAX_VALUE,
                5,
                5
            ).action()
        );
    }

    @Test
    void validatesArgumentsAndStateShape() {
        assertThrows(
            NullPointerException.class,
            () -> evaluate(null, false, 0, 1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> evaluate(Optional.empty(), false, -1, 1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> evaluate(Optional.empty(), false, 0, 0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> evaluate(Optional.empty(), false, 0, 1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestockRefillWaitPolicy.State(-1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestockRefillWaitPolicy.State(10, 9)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> evaluate(
                Optional.of(
                    new RestockRefillWaitPolicy.State(10, 12)
                ),
                false,
                9,
                20,
                5
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> RestockRefillWaitPolicy
                .rescheduleProbeAfterSnapshot(null, 0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockRefillWaitPolicy
                .rescheduleProbeAfterSnapshot(
                    new RestockRefillWaitPolicy.State(10, 12),
                    9,
                    1
                )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> RestockRefillWaitPolicy
                .rescheduleProbeAfterSnapshot(
                    new RestockRefillWaitPolicy.State(10, 12),
                    12,
                    0
                )
        );
    }

    private static RestockRefillWaitPolicy.Decision evaluate(
        Optional<RestockRefillWaitPolicy.State> state,
        boolean sourceAvailable,
        long currentTick,
        int timeoutTicks,
        int probeIntervalTicks
    ) {
        return RestockRefillWaitPolicy.evaluate(
            state,
            sourceAvailable,
            currentTick,
            timeoutTicks,
            probeIntervalTicks
        );
    }
}
