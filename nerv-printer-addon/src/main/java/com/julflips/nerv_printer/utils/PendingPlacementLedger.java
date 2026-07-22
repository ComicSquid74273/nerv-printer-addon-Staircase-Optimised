package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks submitted placement attempts until matching world state confirms them.
 *
 * <p>All time is supplied by the caller as a deterministic, monotonically
 * increasing tick value. {@link #advance(long, int)} reserves retry attempts
 * only up to the supplied retry budget and expires entries after their
 * configured retry allowance is exhausted.</p>
 */
public final class PendingPlacementLedger<K, V> {
    public enum ObservationStatus {
        NOT_PENDING,
        UNRESOLVED,
        CONFIRMED,
        CONFLICT
    }

    public enum TimeoutAction {
        RETRY,
        EXPIRED
    }

    public record PendingAttempt<K, V>(
        K key,
        V expected,
        long firstSubmittedTick,
        long lastAttemptTick,
        int retriesUsed
    ) {
        public PendingAttempt {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(expected, "expected");
            if (firstSubmittedTick < 0 || lastAttemptTick < firstSubmittedTick) {
                throw new IllegalArgumentException(
                    "Pending attempt ticks are invalid."
                );
            }
            if (retriesUsed < 0) {
                throw new IllegalArgumentException(
                    "Retries used cannot be negative."
                );
            }
        }

        public int totalAttempts() {
            return retriesUsed + 1;
        }
    }

    public record Observation<K, V>(
        K key,
        ObservationStatus status,
        Optional<V> expected,
        Optional<V> observed,
        int retriesUsed
    ) {
        public Observation {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(status, "status");
            expected = Objects.requireNonNull(expected, "expected");
            observed = Objects.requireNonNull(observed, "observed");
            if (retriesUsed < 0) {
                throw new IllegalArgumentException(
                    "Retries used cannot be negative."
                );
            }
        }
    }

    public record TimeoutDecision<K, V>(
        TimeoutAction action,
        PendingAttempt<K, V> attempt
    ) {
        public TimeoutDecision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(attempt, "attempt");
        }
    }

    private static final class MutableAttempt<K, V> {
        private final K key;
        private final V expected;
        private final long firstSubmittedTick;
        private long lastAttemptTick;
        private int retriesUsed;

        private MutableAttempt(K key, V expected, long submittedTick) {
            this.key = key;
            this.expected = expected;
            firstSubmittedTick = submittedTick;
            lastAttemptTick = submittedTick;
        }

        private PendingAttempt<K, V> snapshot() {
            return new PendingAttempt<>(
                key,
                expected,
                firstSubmittedTick,
                lastAttemptTick,
                retriesUsed
            );
        }
    }

    private final long retryAfterTicks;
    private final int maximumRetries;
    private final LinkedHashMap<K, MutableAttempt<K, V>> pending =
        new LinkedHashMap<>();

    public PendingPlacementLedger(long retryAfterTicks, int maximumRetries) {
        if (retryAfterTicks <= 0) {
            throw new IllegalArgumentException(
                "Retry timeout must be positive."
            );
        }
        if (maximumRetries < 0) {
            throw new IllegalArgumentException(
                "Maximum retries cannot be negative."
            );
        }
        this.retryAfterTicks = retryAfterTicks;
        this.maximumRetries = maximumRetries;
    }

    public long retryAfterTicks() {
        return retryAfterTicks;
    }

    public int maximumRetries() {
        return maximumRetries;
    }

    /**
     * Records an initial placement attempt.
     *
     * @return {@code false} when the key is already pending; the existing
     * attempt is never overwritten.
     */
    public boolean submit(K key, V expected, long nowTick) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expected, "expected");
        requireTick(nowTick);
        if (pending.containsKey(key)) return false;
        pending.put(key, new MutableAttempt<>(key, expected, nowTick));
        return true;
    }

    public boolean isPending(K key) {
        Objects.requireNonNull(key, "key");
        return pending.containsKey(key);
    }

    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Observes a loaded world state. An empty observation means the placement
     * is still unresolved. A matching value confirms and removes the attempt;
     * a different present value is reported as a conflict and remains pending
     * until the caller cancels, confirms, retries, or expires it.
     */
    public Observation<K, V> observe(
        K key,
        Optional<? extends V> observedState
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(observedState, "observedState");
        Optional<V> observed = optionalCopy(observedState);
        MutableAttempt<K, V> attempt = pending.get(key);
        if (attempt == null) {
            return new Observation<>(
                key,
                ObservationStatus.NOT_PENDING,
                Optional.empty(),
                observed,
                0
            );
        }
        if (observed.isEmpty()) {
            return observation(
                attempt,
                ObservationStatus.UNRESOLVED,
                observed
            );
        }
        if (Objects.equals(attempt.expected, observed.get())) {
            pending.remove(key);
            return observation(
                attempt,
                ObservationStatus.CONFIRMED,
                observed
            );
        }
        return observation(
            attempt,
            ObservationStatus.CONFLICT,
            observed
        );
    }

    public Observation<K, V> observePresent(K key, V observedState) {
        return observe(
            key,
            Optional.of(Objects.requireNonNull(observedState, "observedState"))
        );
    }

    public Observation<K, V> observeUnresolved(K key) {
        return observe(key, Optional.empty());
    }

    /**
     * Reserves a due retry for one caller-selected key.
     *
     * <p>This is the preferred API when reach, placement priority, or a hotbar
     * swap determines which pending target can actually be dispatched. A
     * returned retry is already counted and starts a new timeout window at
     * {@code nowTick}. A retry-exhausted entry is removed and returned as
     * expired. Missing and not-yet-due keys return an empty result.</p>
     */
    public Optional<TimeoutDecision<K, V>> reserveRetry(
        K key,
        long nowTick
    ) {
        Objects.requireNonNull(key, "key");
        requireTick(nowTick);
        MutableAttempt<K, V> attempt = pending.get(key);
        if (attempt == null) return Optional.empty();
        if (nowTick < attempt.lastAttemptTick) {
            throw new IllegalArgumentException(
                "Tick input cannot move backwards."
            );
        }
        if (nowTick - attempt.lastAttemptTick < retryAfterTicks) {
            return Optional.empty();
        }
        if (attempt.retriesUsed >= maximumRetries) {
            PendingAttempt<K, V> snapshot = attempt.snapshot();
            pending.remove(key);
            return Optional.of(
                new TimeoutDecision<>(TimeoutAction.EXPIRED, snapshot)
            );
        }

        attempt.retriesUsed++;
        attempt.lastAttemptTick = nowTick;
        return Optional.of(
            new TimeoutDecision<>(TimeoutAction.RETRY, attempt.snapshot())
        );
    }

    /**
     * Advances timeout handling.
     *
     * <p>A returned retry is already counted and its timeout window starts at
     * {@code nowTick}; callers should therefore invoke this method only when
     * they can dispatch the returned retry decisions. Due retries beyond
     * {@code retryBudget} remain pending and due. Exhausted attempts expire
     * regardless of retry budget.</p>
     */
    public List<TimeoutDecision<K, V>> advance(
        long nowTick,
        int retryBudget
    ) {
        requireTick(nowTick);
        if (retryBudget < 0) {
            throw new IllegalArgumentException(
                "Retry budget cannot be negative."
            );
        }
        for (MutableAttempt<K, V> attempt : pending.values()) {
            if (nowTick < attempt.lastAttemptTick) {
                throw new IllegalArgumentException(
                    "Tick input cannot move backwards."
                );
            }
        }

        ArrayList<TimeoutDecision<K, V>> decisions = new ArrayList<>();
        int remainingRetryBudget = retryBudget;
        Iterator<Map.Entry<K, MutableAttempt<K, V>>> iterator =
            pending.entrySet().iterator();
        while (iterator.hasNext()) {
            MutableAttempt<K, V> attempt = iterator.next().getValue();
            if (nowTick - attempt.lastAttemptTick < retryAfterTicks) {
                continue;
            }
            if (attempt.retriesUsed >= maximumRetries) {
                PendingAttempt<K, V> snapshot = attempt.snapshot();
                iterator.remove();
                decisions.add(
                    new TimeoutDecision<>(TimeoutAction.EXPIRED, snapshot)
                );
                continue;
            }
            if (remainingRetryBudget <= 0) continue;

            attempt.retriesUsed++;
            attempt.lastAttemptTick = nowTick;
            remainingRetryBudget--;
            decisions.add(
                new TimeoutDecision<>(
                    TimeoutAction.RETRY,
                    attempt.snapshot()
                )
            );
        }
        return List.copyOf(decisions);
    }

    public Optional<PendingAttempt<K, V>> remove(K key) {
        Objects.requireNonNull(key, "key");
        MutableAttempt<K, V> removed = pending.remove(key);
        return removed == null
            ? Optional.empty()
            : Optional.of(removed.snapshot());
    }

    public List<PendingAttempt<K, V>> pendingAttempts() {
        return pending.values().stream()
            .map(MutableAttempt::snapshot)
            .toList();
    }

    public void clear() {
        pending.clear();
    }

    public void reset() {
        clear();
    }

    private Observation<K, V> observation(
        MutableAttempt<K, V> attempt,
        ObservationStatus status,
        Optional<V> observed
    ) {
        return new Observation<>(
            attempt.key,
            status,
            Optional.of(attempt.expected),
            observed,
            attempt.retriesUsed
        );
    }

    private static <V> Optional<V> optionalCopy(
        Optional<? extends V> source
    ) {
        if (source.isEmpty()) return Optional.empty();
        V value = Objects.requireNonNull(source.get(), "observed value");
        return Optional.of(value);
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Tick cannot be negative.");
        }
    }
}
