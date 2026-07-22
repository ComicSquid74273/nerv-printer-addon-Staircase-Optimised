package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure state and retry control for breaking an unexpected block before a repair
 * placement.
 *
 * <p>The caller owns world inspection, reach checks, tool selection, and packet
 * dispatch. This controller only records explicit target ownership and enforces
 * the transition:</p>
 *
 * <pre>
 * observed wrong block -> BREAKING -> observed air -> READY_TO_PLACE
 * </pre>
 *
 * <p>A planned or submitted break never advances a target to
 * {@link Phase#READY_TO_PLACE}; only a later {@link Observation#AIR} observation
 * can do that. Likewise, a placement remains owned until the caller observes
 * {@link Observation#EXPECTED}.</p>
 *
 * @param <T> immutable target identity, normally a block position
 */
public final class RepairMineController<T> {
    public enum Observation {
        WRONG,
        AIR,
        EXPECTED
    }

    public enum Phase {
        BREAKING,
        READY_TO_PLACE,
        EXPIRED
    }

    public enum StopReason {
        EXHAUSTED,
        ACTION_BUDGET,
        SLOW_TARGET,
        EXPIRED_TARGET
    }

    /**
     * Retry bounds expressed in caller-owned monotonic ticks.
     */
    public record RetryPolicy(
        int retryDelayTicks,
        int maxAttempts,
        int timeoutTicks,
        int maximumLifetimeTicks
    ) {
        public RetryPolicy(
            int retryDelayTicks,
            int maxAttempts,
            int timeoutTicks
        ) {
            this(
                retryDelayTicks,
                maxAttempts,
                timeoutTicks,
                Math.multiplyExact(timeoutTicks, 10)
            );
        }

        public RetryPolicy {
            if (retryDelayTicks <= 0) {
                throw new IllegalArgumentException(
                    "Retry delay must be positive."
                );
            }
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException(
                    "Maximum attempts must be positive."
                );
            }
            if (timeoutTicks <= 0) {
                throw new IllegalArgumentException(
                    "Timeout must be positive."
                );
            }
            if (maximumLifetimeTicks < timeoutTicks) {
                throw new IllegalArgumentException(
                    "Maximum lifetime must be at least the idle timeout."
                );
            }
        }
    }

    /**
     * A currently actionable repair target and its classification using the
     * exact tool that the caller will dispatch with.
     */
    public record BreakCandidate<T>(T target, boolean trueInstant) {
        public BreakCandidate {
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * A non-confirming permit for one break dispatch.
     *
     * <p>The cycle id prevents a stale decision from an earlier repair cycle
     * being recorded against a target that became wrong again later.</p>
     */
    public record BreakDecision<T>(
        T target,
        boolean trueInstant,
        int attemptNumber,
        long cycleId
    ) {
        public BreakDecision {
            Objects.requireNonNull(target, "target");
            if (attemptNumber <= 0) {
                throw new IllegalArgumentException(
                    "Attempt number must be positive."
                );
            }
            if (cycleId <= 0) {
                throw new IllegalArgumentException(
                    "Cycle id must be positive."
                );
            }
        }
    }

    /**
     * Ordered decisions plus the reason scanning stopped.
     */
    public record BreakBatch<T>(
        List<BreakDecision<T>> decisions,
        StopReason stopReason
    ) {
        public BreakBatch {
            decisions = List.copyOf(
                Objects.requireNonNull(decisions, "decisions")
            );
            Objects.requireNonNull(stopReason, "stopReason");
        }
    }

    public record TargetSnapshot<T>(
        T target,
        Phase phase,
        int attempts,
        long firstObservedTick,
        long lastObservedTick,
        long lastDispatchTick,
        long cycleId
    ) {
        public TargetSnapshot {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(phase, "phase");
        }
    }

    private static final long NO_TICK = -1L;

    private final RetryPolicy retryPolicy;
    private final LinkedHashMap<T, Entry> entries = new LinkedHashMap<>();

    private long lastTick = NO_TICK;
    private long nextCycleId = 1L;

    public RepairMineController(RetryPolicy retryPolicy) {
        this.retryPolicy = Objects.requireNonNull(
            retryPolicy,
            "retryPolicy"
        );
    }

    /**
     * Applies one authoritative world observation.
     *
     * <p>An AIR observation only creates placement readiness for a target that
     * was already owned in BREAKING. A generally missing print block therefore
     * remains the normal printer's responsibility rather than being
     * accidentally claimed as a repair mine.</p>
     */
    public void observe(T target, Observation observation, long tick) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(observation, "observation");
        validateTick(tick);

        Entry entry = entries.get(target);
        switch (observation) {
            case EXPECTED -> entries.remove(target);
            case AIR -> {
                if (entry == null || entry.phase == Phase.EXPIRED) return;
                entry.lastObservedTick = tick;
                if (entry.phase == Phase.BREAKING) {
                    entry.phase = Phase.READY_TO_PLACE;
                }
            }
            case WRONG -> {
                if (entry == null || entry.phase == Phase.READY_TO_PLACE) {
                    entries.put(target, newEntry(tick));
                    return;
                }

                entry.lastObservedTick = tick;
                expireIfRequired(entry, tick);
            }
        }
    }

    /**
     * Advances retry/timeout state without implying a new world observation.
     */
    public void advance(long tick) {
        validateTick(tick);
        for (Entry entry : entries.values()) {
            expireIfRequired(entry, tick);
        }
    }

    /**
     * Plans break dispatches in caller-provided traversal order.
     *
     * <p>Pending true-instant targets do not prevent other targets in the same
     * batch from being considered. A slow target is a hard barrier whether it
     * is newly dispatchable or already waiting for confirmation/retry. This
     * prevents bulk work from being scheduled behind an active slow repair.</p>
     *
     * <p>This method does not consume retry attempts. After a returned decision
     * is actually submitted, call
     * {@link #recordBreakDispatched(BreakDecision, long)}.</p>
     */
    public BreakBatch<T> planBreakBatch(
        List<BreakCandidate<T>> orderedCandidates,
        int maxActions,
        long tick
    ) {
        Objects.requireNonNull(orderedCandidates, "orderedCandidates");
        if (maxActions < 0) {
            throw new IllegalArgumentException(
                "Maximum actions must be non-negative."
            );
        }

        advance(tick);
        if (maxActions == 0) {
            return new BreakBatch<>(List.of(), StopReason.ACTION_BUDGET);
        }

        List<BreakDecision<T>> decisions = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        for (BreakCandidate<T> candidate : orderedCandidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!visited.add(candidate.target())) continue;

            Entry entry = entries.get(candidate.target());
            if (entry == null || entry.phase == Phase.READY_TO_PLACE) continue;
            if (entry.phase == Phase.EXPIRED) {
                return new BreakBatch<>(
                    decisions,
                    StopReason.EXPIRED_TARGET
                );
            }

            boolean dispatchDue = isDispatchDue(entry, tick);
            if (!candidate.trueInstant()) {
                if (dispatchDue) {
                    if (decisions.size() >= maxActions) {
                        return new BreakBatch<>(
                            decisions,
                            StopReason.ACTION_BUDGET
                        );
                    }
                    decisions.add(decision(candidate, entry));
                }
                return new BreakBatch<>(
                    decisions,
                    StopReason.SLOW_TARGET
                );
            }

            if (!dispatchDue) continue;
            if (decisions.size() >= maxActions) {
                return new BreakBatch<>(
                    decisions,
                    StopReason.ACTION_BUDGET
                );
            }
            decisions.add(decision(candidate, entry));
        }

        return new BreakBatch<>(decisions, StopReason.EXHAUSTED);
    }

    /**
     * Records that one planned action was submitted.
     *
     * @return true when the decision still matched the current repair cycle and
     * was due; false for stale, duplicate, expired, or otherwise invalid
     * decisions
     */
    public boolean recordBreakDispatched(
        BreakDecision<T> decision,
        long tick
    ) {
        Objects.requireNonNull(decision, "decision");
        advance(tick);

        Entry entry = entries.get(decision.target());
        if (entry == null || entry.phase != Phase.BREAKING) return false;
        if (entry.cycleId != decision.cycleId()) return false;
        if (decision.attemptNumber() != entry.attempts + 1) return false;
        if (!isDispatchDue(entry, tick)) return false;

        entry.attempts++;
        entry.lastDispatchTick = tick;
        return true;
    }

    /**
     * Extends the idle watchdog for one owned slow mine that the caller
     * successfully advanced this tick. A separate maximum-lifetime watchdog
     * still bounds a target whose client progress never receives server
     * confirmation.
     */
    public boolean recordSlowProgress(T target, long tick) {
        Objects.requireNonNull(target, "target");
        advance(tick);

        Entry entry = entries.get(target);
        if (entry == null
            || entry.phase != Phase.BREAKING
            || entry.attempts == 0) {
            return false;
        }
        entry.lastObservedTick = tick;
        return true;
    }

    /**
     * Returns placement-ready targets in the caller's authoritative traversal
     * order.
     */
    public List<T> readyToPlaceInOrder(
        List<T> orderedTargets,
        int limit
    ) {
        Objects.requireNonNull(orderedTargets, "orderedTargets");
        if (limit < 0) {
            throw new IllegalArgumentException(
                "Ready-target limit must be non-negative."
            );
        }
        if (limit == 0) return List.of();

        List<T> ready = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        for (T target : orderedTargets) {
            Objects.requireNonNull(target, "target");
            if (!visited.add(target)) continue;

            Entry entry = entries.get(target);
            if (entry != null && entry.phase == Phase.READY_TO_PLACE) {
                ready.add(target);
                if (ready.size() >= limit) break;
            }
        }
        return List.copyOf(ready);
    }

    public Optional<Phase> phaseOf(T target) {
        Objects.requireNonNull(target, "target");
        Entry entry = entries.get(target);
        return entry == null ? Optional.empty() : Optional.of(entry.phase);
    }

    public Optional<TargetSnapshot<T>> snapshot(T target) {
        Objects.requireNonNull(target, "target");
        Entry entry = entries.get(target);
        return entry == null
            ? Optional.empty()
            : Optional.of(snapshot(target, entry));
    }

    public List<TargetSnapshot<T>> snapshots() {
        List<TargetSnapshot<T>> snapshots = new ArrayList<>(entries.size());
        for (Map.Entry<T, Entry> entry : entries.entrySet()) {
            snapshots.add(snapshot(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(snapshots);
    }

    public boolean hasExpiredTargets() {
        for (Entry entry : entries.values()) {
            if (entry.phase == Phase.EXPIRED) return true;
        }
        return false;
    }

    public int size() {
        return entries.size();
    }

    /**
     * Releases every repair owner and permits a fresh caller tick timeline.
     * Intended for recovery, deactivation, or a deliberate current-pair reset.
     */
    public void reset() {
        entries.clear();
        lastTick = NO_TICK;
    }

    private Entry newEntry(long tick) {
        if (nextCycleId == Long.MAX_VALUE) {
            throw new IllegalStateException("Repair cycle id exhausted.");
        }
        return new Entry(tick, nextCycleId++);
    }

    private BreakDecision<T> decision(
        BreakCandidate<T> candidate,
        Entry entry
    ) {
        return new BreakDecision<>(
            candidate.target(),
            candidate.trueInstant(),
            entry.attempts + 1,
            entry.cycleId
        );
    }

    private boolean isDispatchDue(Entry entry, long tick) {
        if (entry.phase != Phase.BREAKING) return false;
        if (entry.attempts == 0) return true;
        if (entry.attempts >= retryPolicy.maxAttempts()) return false;
        return tick - entry.lastDispatchTick
            >= retryPolicy.retryDelayTicks();
    }

    private void expireIfRequired(Entry entry, long tick) {
        if (entry.phase == Phase.READY_TO_PLACE) {
            if (tick - entry.lastObservedTick >= retryPolicy.timeoutTicks()) {
                entry.phase = Phase.EXPIRED;
            }
            return;
        }
        if (entry.phase != Phase.BREAKING) return;

        boolean timedOut =
            tick - entry.lastObservedTick >= retryPolicy.timeoutTicks();
        boolean lifetimeExhausted =
            tick - entry.firstObservedTick
                >= retryPolicy.maximumLifetimeTicks();
        boolean attemptsExhausted =
            entry.attempts >= retryPolicy.maxAttempts()
                && entry.lastDispatchTick != NO_TICK
                && tick - entry.lastDispatchTick
                    >= retryPolicy.retryDelayTicks();
        if (timedOut || lifetimeExhausted || attemptsExhausted) {
            entry.phase = Phase.EXPIRED;
        }
    }

    private TargetSnapshot<T> snapshot(T target, Entry entry) {
        return new TargetSnapshot<>(
            target,
            entry.phase,
            entry.attempts,
            entry.firstObservedTick,
            entry.lastObservedTick,
            entry.lastDispatchTick,
            entry.cycleId
        );
    }

    private void validateTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException(
                "Tick must be non-negative."
            );
        }
        if (lastTick != NO_TICK && tick < lastTick) {
            throw new IllegalArgumentException(
                "Ticks must be monotonic until reset."
            );
        }
        lastTick = tick;
    }

    private final class Entry {
        private Phase phase = Phase.BREAKING;
        private int attempts;
        private final long firstObservedTick;
        private long lastObservedTick;
        private long lastDispatchTick = NO_TICK;
        private final long cycleId;

        private Entry(long tick, long cycleId) {
            this.firstObservedTick = tick;
            this.lastObservedTick = tick;
            this.cycleId = cycleId;
        }
    }
}
