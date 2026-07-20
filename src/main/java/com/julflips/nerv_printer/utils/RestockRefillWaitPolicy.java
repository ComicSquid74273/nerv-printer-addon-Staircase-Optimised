package com.julflips.nerv_printer.utils;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure bounded-wait policy for a temporarily empty restock source.
 *
 * <p>The caller must retain the {@link Decision#state()} returned by each
 * evaluation and supply it to the next evaluation. The original start tick is
 * preserved across ordinary waits and reopen probes, so unrelated inventory
 * updates cannot extend the timeout. Supplying {@link Optional#empty()} is an
 * explicit reset, suitable only when beginning a new chest wait.</p>
 */
public final class RestockRefillWaitPolicy {
    public enum Action {
        SOURCE_AVAILABLE,
        START_WAIT,
        WAIT,
        PROBE,
        TIMED_OUT
    }

    /**
     * Active wait state. The next probe tick may be {@link Long#MAX_VALUE}
     * when saturating clock arithmetic reaches the end of the supported
     * range.
     */
    public record State(long startedAtTick, long nextProbeAtTick) {
        public State {
            if (startedAtTick < 0) {
                throw new IllegalArgumentException(
                    "Wait start tick cannot be negative."
                );
            }
            if (nextProbeAtTick < startedAtTick) {
                throw new IllegalArgumentException(
                    "Next probe tick cannot precede the wait start."
                );
            }
        }
    }

    public record Decision(Action action, Optional<State> state) {
        public Decision {
            Objects.requireNonNull(action, "action");
            state = Objects.requireNonNull(state, "state");
            if (action == Action.SOURCE_AVAILABLE && state.isPresent()) {
                throw new IllegalArgumentException(
                    "An available source cannot retain refill-wait state."
                );
            }
            if (action != Action.SOURCE_AVAILABLE && state.isEmpty()) {
                throw new IllegalArgumentException(
                    "An unavailable source must retain refill-wait state."
                );
            }
        }
    }

    private RestockRefillWaitPolicy() {
    }

    /**
     * Evaluates one client tick of an empty-source wait.
     *
     * @param currentState state returned by the previous evaluation, or empty
     *                     to explicitly begin a new wait
     * @param sourceAvailable whether the latest authoritative handler
     *                        snapshot has a compatible source stack
     * @param currentTick monotonic client action tick
     * @param timeoutTicks maximum ticks from the original wait start
     * @param probeIntervalTicks ticks between bounded reopen probes
     */
    public static Decision evaluate(
        Optional<State> currentState,
        boolean sourceAvailable,
        long currentTick,
        int timeoutTicks,
        int probeIntervalTicks
    ) {
        return evaluate(
            currentState,
            sourceAvailable,
            currentTick,
            timeoutTicks,
            probeIntervalTicks,
            false
        );
    }

    /**
     * Evaluates one client tick while optionally holding an exact-chest probe
     * in flight. An in-flight probe suppresses overlapping reopen requests,
     * but never suppresses source availability or the anchored deadline.
     */
    public static Decision evaluate(
        Optional<State> currentState,
        boolean sourceAvailable,
        long currentTick,
        int timeoutTicks,
        int probeIntervalTicks,
        boolean probeInFlight
    ) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentTick < 0) {
            throw new IllegalArgumentException(
                "Current tick cannot be negative."
            );
        }
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException(
                "Refill timeout must be positive."
            );
        }
        if (probeIntervalTicks <= 0) {
            throw new IllegalArgumentException(
                "Refill probe interval must be positive."
            );
        }
        if (currentState.isPresent()
            && currentTick < currentState.orElseThrow().startedAtTick()) {
            throw new IllegalArgumentException(
                "Current tick cannot precede the wait start."
            );
        }

        if (sourceAvailable) {
            return new Decision(
                Action.SOURCE_AVAILABLE,
                Optional.empty()
            );
        }

        if (currentState.isEmpty()) {
            State started = new State(
                currentTick,
                saturatedAdd(currentTick, probeIntervalTicks)
            );
            return new Decision(
                Action.START_WAIT,
                Optional.of(started)
            );
        }

        State state = currentState.orElseThrow();
        long deadline =
            saturatedAdd(state.startedAtTick(), timeoutTicks);
        if (currentTick >= deadline) {
            return new Decision(Action.TIMED_OUT, Optional.of(state));
        }
        if (currentTick >= state.nextProbeAtTick()) {
            if (probeInFlight) {
                return new Decision(Action.WAIT, Optional.of(state));
            }
            State probed = new State(
                state.startedAtTick(),
                saturatedAdd(currentTick, probeIntervalTicks)
            );
            return new Decision(Action.PROBE, Optional.of(probed));
        }
        return new Decision(Action.WAIT, Optional.of(state));
    }

    /**
     * Acknowledges the full snapshot produced by one exact-chest probe and
     * schedules the next probe from that observation. The original start tick
     * is retained, so repeated empty snapshots cannot extend the timeout.
     */
    public static State rescheduleProbeAfterSnapshot(
        State state,
        long currentTick,
        int probeIntervalTicks
    ) {
        Objects.requireNonNull(state, "state");
        if (currentTick < state.startedAtTick()) {
            throw new IllegalArgumentException(
                "Current tick cannot precede the wait start."
            );
        }
        if (probeIntervalTicks <= 0) {
            throw new IllegalArgumentException(
                "Refill probe interval must be positive."
            );
        }
        return new State(
            state.startedAtTick(),
            saturatedAdd(currentTick, probeIntervalTicks)
        );
    }

    private static long saturatedAdd(long value, int increment) {
        if (value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
