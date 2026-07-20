package com.julflips.nerv_printer.utils;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Pure single-target ownership and confirmation control for an ordered
 * teardown traversal.
 *
 * <p>The caller remains responsible for route selection, reach checks, tool
 * selection, Minecraft interaction calls, and authoritative packet
 * observation. This controller guarantees that a second route target cannot
 * be acquired while the first is owned, that every initial or retry dispatch
 * has consumed a caller-supplied action-budget permit, and that only a newer
 * authoritative AIR observation completes a submitted target.</p>
 *
 * <p>A progressive continuation is deliberately not a new dispatch. Once an
 * accelerated or slow progressive target has a recorded initial dispatch,
 * {@link #planNext(BooleanSupplier)} returns
 * {@link PlanAction#CONTINUE_PROGRESSIVE} without consulting the supplied
 * action-budget callback.</p>
 *
 * @param <K> immutable route key, normally a block position
 * @param <E> immutable expected-block identity
 */
public final class OrderedTeardownMineController<K, E> {
    public enum ClaimResult {
        ACQUIRED,
        ALREADY_OWNED,
        BLOCKED_BY_OWNED_TARGET
    }

    public enum Phase {
        PENDING_INITIAL_DISPATCH,
        PENDING_RETRY_DISPATCH,
        DISPATCH_RESERVED,
        AWAITING_AUTHORITATIVE_AIR,
        COMPLETED
    }

    public enum DispatchKind {
        INITIAL,
        RETRY
    }

    public enum PlanAction {
        NO_TARGET,
        WAITING_FOR_ACTION_BUDGET,
        DISPATCH,
        DISPATCH_ALREADY_RESERVED,
        CONTINUE_PROGRESSIVE,
        AWAIT_AUTHORITATIVE_AIR,
        COMPLETED
    }

    public enum ObservationSource {
        AUTHORITATIVE,
        PREDICTED
    }

    public enum ObservedBlock {
        AIR,
        NON_AIR
    }

    public enum ObservationResult {
        NO_TARGET,
        DIFFERENT_TARGET,
        PREDICTED_IGNORED,
        STALE_IGNORED,
        PRE_SUBMISSION_AIR_IGNORED,
        NON_AIR,
        COMPLETED,
        ALREADY_COMPLETED
    }

    /**
     * Full route-target identity. Including the expected block prevents a
     * delayed observation for an older plan at the same position from being
     * applied to a newly planned block identity.
     */
    public record Target<K, E>(K key, E expectedBlock) {
        public Target {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(expectedBlock, "expectedBlock");
        }
    }

    /**
     * A world observation associated with the exact owned target identity.
     */
    public record Observation<K, E>(
        Target<K, E> target,
        long sequence,
        ObservationSource source,
        ObservedBlock observedBlock
    ) {
        public Observation {
            Objects.requireNonNull(target, "target");
            if (sequence < 0L) {
                throw new IllegalArgumentException(
                    "Observation sequence must be non-negative."
                );
            }
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(observedBlock, "observedBlock");
        }

        public static <K, E> Observation<K, E> authoritativeAir(
            Target<K, E> target,
            long sequence
        ) {
            return new Observation<>(
                target,
                sequence,
                ObservationSource.AUTHORITATIVE,
                ObservedBlock.AIR
            );
        }

        public static <K, E> Observation<K, E> authoritativeNonAir(
            Target<K, E> target,
            long sequence
        ) {
            return new Observation<>(
                target,
                sequence,
                ObservationSource.AUTHORITATIVE,
                ObservedBlock.NON_AIR
            );
        }

        public static <K, E> Observation<K, E> predictedAir(
            Target<K, E> target,
            long sequence
        ) {
            return new Observation<>(
                target,
                sequence,
                ObservationSource.PREDICTED,
                ObservedBlock.AIR
            );
        }
    }

    /**
     * Budget-backed permission to submit one initial or retry break.
     *
     * <p>The lease id and attempt number make a decision stale after abandon,
     * reset, completion, or a later retry.</p>
     */
    public record DispatchDecision<K, E>(
        Target<K, E> target,
        RepairMiningClassification classification,
        DispatchKind kind,
        int attemptNumber,
        long leaseId,
        long reservationId
    ) {
        public DispatchDecision {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(kind, "kind");
            if (attemptNumber <= 0) {
                throw new IllegalArgumentException(
                    "Attempt number must be positive."
                );
            }
            if (leaseId <= 0L) {
                throw new IllegalArgumentException(
                    "Lease id must be positive."
                );
            }
            if (reservationId <= 0L) {
                throw new IllegalArgumentException(
                    "Reservation id must be positive."
                );
            }
        }
    }

    /**
     * The next caller action. A dispatch decision is present only for
     * {@link PlanAction#DISPATCH} and
     * {@link PlanAction#DISPATCH_ALREADY_RESERVED}.
     */
    public record Plan<K, E>(
        PlanAction action,
        Optional<DispatchDecision<K, E>> dispatch
    ) {
        public Plan {
            Objects.requireNonNull(action, "action");
            dispatch = Objects.requireNonNull(dispatch, "dispatch");
            boolean dispatchAction =
                action == PlanAction.DISPATCH
                    || action == PlanAction.DISPATCH_ALREADY_RESERVED;
            if (dispatchAction != dispatch.isPresent()) {
                throw new IllegalArgumentException(
                    "Dispatch plans must contain exactly one decision."
                );
            }
        }
    }

    public record Snapshot<K, E>(
        Target<K, E> target,
        RepairMiningClassification classification,
        Phase phase,
        int attempts,
        long latestSubmissionSequence,
        long latestAuthoritativeObservationSequence,
        long leaseId
    ) {
        public Snapshot {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(phase, "phase");
        }
    }

    private static final long NO_SEQUENCE = -1L;

    private Lease active;
    private long nextLeaseId = 1L;
    private long nextReservationId = 1L;

    /**
     * Acquires the sole teardown lease.
     *
     * @param latestAuthoritativeObservationSequence latest server observation
     * sequence known when the route selected this target
     */
    public ClaimResult claim(
        Target<K, E> target,
        RepairMiningClassification classification,
        long latestAuthoritativeObservationSequence
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(classification, "classification");
        validateSequence(
            latestAuthoritativeObservationSequence,
            "Latest authoritative observation sequence"
        );

        if (active != null) {
            return active.target.equals(target)
                && active.classification == classification
                    ? ClaimResult.ALREADY_OWNED
                    : ClaimResult.BLOCKED_BY_OWNED_TARGET;
        }
        if (nextLeaseId == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Ordered teardown lease id exhausted."
            );
        }

        active = new Lease(
            target,
            classification,
            latestAuthoritativeObservationSequence,
            nextLeaseId++
        );
        return ClaimResult.ACQUIRED;
    }

    /**
     * Plans the next action for the sole owned target.
     *
     * <p>The supplied callback is invoked only when an initial or retry
     * dispatch is due. Returning {@code true} reserves one dispatch decision;
     * returning {@code false} leaves the target pending. Continuation, wait,
     * completed, and no-target plans never invoke it.</p>
     */
    public Plan<K, E> planNext(BooleanSupplier tryAcquireActionBudgetPermit) {
        Objects.requireNonNull(
            tryAcquireActionBudgetPermit,
            "tryAcquireActionBudgetPermit"
        );
        if (active == null) return plan(PlanAction.NO_TARGET);
        if (active.phase == Phase.COMPLETED) {
            return plan(PlanAction.COMPLETED);
        }
        if (active.outstandingDecision != null) {
            return plan(
                PlanAction.DISPATCH_ALREADY_RESERVED,
                active.outstandingDecision
            );
        }

        if (active.phase == Phase.PENDING_INITIAL_DISPATCH
            || active.phase == Phase.PENDING_RETRY_DISPATCH) {
            if (!tryAcquireActionBudgetPermit.getAsBoolean()) {
                return plan(PlanAction.WAITING_FOR_ACTION_BUDGET);
            }

            DispatchKind kind =
                active.phase == Phase.PENDING_INITIAL_DISPATCH
                    ? DispatchKind.INITIAL
                    : DispatchKind.RETRY;
            if (nextReservationId == Long.MAX_VALUE) {
                throw new IllegalStateException(
                    "Ordered teardown reservation id exhausted."
                );
            }
            DispatchDecision<K, E> decision = new DispatchDecision<>(
                active.target,
                active.classification,
                kind,
                Math.addExact(active.attempts, 1),
                active.leaseId,
                nextReservationId++
            );
            active.outstandingDecision = decision;
            active.phase = Phase.DISPATCH_RESERVED;
            return plan(PlanAction.DISPATCH, decision);
        }

        if (active.classification.requiresProgressiveContinuation()) {
            return plan(PlanAction.CONTINUE_PROGRESSIVE);
        }
        return plan(PlanAction.AWAIT_AUTHORITATIVE_AIR);
    }

    /**
     * Commits a reserved decision after the caller actually submitted it.
     *
     * @param submissionSequence latest authoritative server observation
     * sequence at submission time
     * @return false when the decision is stale, was not reserved, or carries a
     * sequence older than an observation already processed for this lease
     */
    public boolean recordDispatched(
        DispatchDecision<K, E> decision,
        long submissionSequence
    ) {
        Objects.requireNonNull(decision, "decision");
        validateSequence(submissionSequence, "Submission sequence");
        if (active == null
            || !decision.equals(active.outstandingDecision)
            || submissionSequence
                < active.latestAuthoritativeObservationSequence) {
            return false;
        }

        active.attempts = decision.attemptNumber();
        active.latestSubmissionSequence = submissionSequence;
        active.outstandingDecision = null;
        active.phase = Phase.AWAITING_AUTHORITATIVE_AIR;
        return true;
    }

    /**
     * Releases a reservation when the caller could not submit the break.
     * The already-consumed action credit is intentionally not recreated.
     */
    public boolean rejectDispatch(DispatchDecision<K, E> decision) {
        Objects.requireNonNull(decision, "decision");
        if (active == null
            || !decision.equals(active.outstandingDecision)) {
            return false;
        }

        active.outstandingDecision = null;
        active.phase = decision.kind() == DispatchKind.INITIAL
            ? Phase.PENDING_INITIAL_DISPATCH
            : Phase.PENDING_RETRY_DISPATCH;
        return true;
    }

    /**
     * Requests a new budget-backed dispatch for a submitted target. Retry
     * timing and packet cancellation remain caller-owned.
     */
    public boolean requestRetry() {
        if (active == null || active.phase == Phase.COMPLETED) return false;
        if (active.phase == Phase.PENDING_RETRY_DISPATCH) return true;
        if (active.phase != Phase.AWAITING_AUTHORITATIVE_AIR
            || active.attempts == 0) {
            return false;
        }

        active.phase = Phase.PENDING_RETRY_DISPATCH;
        return true;
    }

    /**
     * Applies one observed block state to the current lease.
     *
     * <p>Predicted observations never affect authoritative sequence state.
     * An authoritative AIR observation completes only after at least one
     * recorded dispatch and only when its sequence is strictly greater than
     * both the latest submission sequence and every authoritative observation
     * already processed for this lease.</p>
     */
    public ObservationResult observe(Observation<K, E> observation) {
        Objects.requireNonNull(observation, "observation");
        if (active == null) return ObservationResult.NO_TARGET;
        if (!active.target.equals(observation.target())) {
            return ObservationResult.DIFFERENT_TARGET;
        }
        if (observation.source() == ObservationSource.PREDICTED) {
            return ObservationResult.PREDICTED_IGNORED;
        }
        if (observation.sequence()
            <= active.latestAuthoritativeObservationSequence) {
            return ObservationResult.STALE_IGNORED;
        }

        active.latestAuthoritativeObservationSequence =
            observation.sequence();
        if (observation.observedBlock() != ObservedBlock.AIR) {
            return ObservationResult.NON_AIR;
        }
        if (active.phase == Phase.COMPLETED) {
            return ObservationResult.ALREADY_COMPLETED;
        }
        if (active.attempts == 0
            || active.latestSubmissionSequence == NO_SEQUENCE
            || observation.sequence()
                <= active.latestSubmissionSequence) {
            return ObservationResult.PRE_SUBMISSION_AIR_IGNORED;
        }

        active.outstandingDecision = null;
        active.phase = Phase.COMPLETED;
        return ObservationResult.COMPLETED;
    }

    public boolean hasOwnedTarget() {
        return active != null;
    }

    public Optional<Target<K, E>> target() {
        return active == null
            ? Optional.empty()
            : Optional.of(active.target);
    }

    public Optional<Snapshot<K, E>> snapshot() {
        return active == null
            ? Optional.empty()
            : Optional.of(snapshot(active));
    }

    /**
     * Deliberately releases the current route target and returns its final
     * snapshot for diagnostics.
     */
    public Optional<Snapshot<K, E>> abandon() {
        if (active == null) return Optional.empty();
        Snapshot<K, E> abandoned = snapshot(active);
        active = null;
        return Optional.of(abandoned);
    }

    /**
     * Clears all ownership for phase transition, recovery, disconnect, or
     * deactivation. Lease ids remain monotonic so old decisions stay stale.
     */
    public void reset() {
        active = null;
    }

    private Plan<K, E> plan(PlanAction action) {
        return new Plan<>(action, Optional.empty());
    }

    private Plan<K, E> plan(
        PlanAction action,
        DispatchDecision<K, E> decision
    ) {
        return new Plan<>(action, Optional.of(decision));
    }

    private Snapshot<K, E> snapshot(Lease lease) {
        return new Snapshot<>(
            lease.target,
            lease.classification,
            lease.phase,
            lease.attempts,
            lease.latestSubmissionSequence,
            lease.latestAuthoritativeObservationSequence,
            lease.leaseId
        );
    }

    private void validateSequence(long sequence, String label) {
        if (sequence < 0L) {
            throw new IllegalArgumentException(
                label + " must be non-negative."
            );
        }
    }

    private final class Lease {
        private final Target<K, E> target;
        private final RepairMiningClassification classification;
        private Phase phase = Phase.PENDING_INITIAL_DISPATCH;
        private int attempts;
        private long latestSubmissionSequence = NO_SEQUENCE;
        private long latestAuthoritativeObservationSequence;
        private final long leaseId;
        private DispatchDecision<K, E> outstandingDecision;

        private Lease(
            Target<K, E> target,
            RepairMiningClassification classification,
            long latestAuthoritativeObservationSequence,
            long leaseId
        ) {
            this.target = target;
            this.classification = classification;
            this.latestAuthoritativeObservationSequence =
                latestAuthoritativeObservationSequence;
            this.leaseId = leaseId;
        }
    }
}
