package com.julflips.nerv_printer.utils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure recovery policy for interrupted used-tool deposits.
 *
 * <p>A destination is recoverable only while a deposit transaction was in
 * flight. The last interacted destination is authoritative when it still
 * belongs to the deposit plan; otherwise the policy can recover the one
 * planned destination that is no longer present in the queued work.</p>
 */
public final class UsedToolDepositRecoveryPolicy {
    private UsedToolDepositRecoveryPolicy() {
    }

    public enum ResolutionStatus {
        RESOLVED,
        NO_CURRENT_WORK,
        AMBIGUOUS
    }

    /**
     * Immutable destination-resolution result.
     *
     * <p>Only {@link ResolutionStatus#RESOLVED} carries a destination.
     * {@link ResolutionStatus#NO_CURRENT_WORK} is intentionally distinct from
     * an ambiguous or inconsistent in-flight plan.</p>
     */
    public record DestinationResolution<K>(
        ResolutionStatus status,
        Optional<K> destination
    ) {
        public DestinationResolution {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(destination, "destination");
            if ((status == ResolutionStatus.RESOLVED)
                != destination.isPresent()) {
                throw new IllegalArgumentException(
                    "Only a resolved result may carry a destination."
                );
            }
        }
    }

    /**
     * Resolves the destination of an interrupted deposit without mutating the
     * plan or queue.
     */
    public static <K> DestinationResolution<K> resolveDestination(
        Set<K> plannedDestinations,
        Collection<K> queuedDestinations,
        Optional<K> lastInteractedDestination,
        boolean transactionInFlight
    ) {
        Objects.requireNonNull(
            plannedDestinations,
            "plannedDestinations"
        );
        Objects.requireNonNull(queuedDestinations, "queuedDestinations");
        Objects.requireNonNull(
            lastInteractedDestination,
            "lastInteractedDestination"
        );

        if (!transactionInFlight) {
            return new DestinationResolution<>(
                ResolutionStatus.NO_CURRENT_WORK,
                Optional.empty()
            );
        }

        if (lastInteractedDestination
            .filter(plannedDestinations::contains)
            .isPresent()) {
            return new DestinationResolution<>(
                ResolutionStatus.RESOLVED,
                lastInteractedDestination
            );
        }

        K missingDestination = null;
        int missingCount = 0;
        for (K destination : plannedDestinations) {
            if (queuedDestinations.contains(destination)) continue;
            missingDestination = destination;
            missingCount++;
            if (missingCount > 1) break;
        }

        if (missingCount == 1) {
            return new DestinationResolution<>(
                ResolutionStatus.RESOLVED,
                Optional.of(missingDestination)
            );
        }
        return new DestinationResolution<>(
            ResolutionStatus.AMBIGUOUS,
            Optional.empty()
        );
    }

    /**
     * Accepts a server handler snapshot only for the exact planned
     * destination and its non-empty item plan.
     */
    public static <K> boolean acceptsHandlerSnapshot(
        K expectedDestination,
        K interactedDestination,
        Set<K> plannedDestinations,
        Collection<?> itemPlan,
        int packetSyncId,
        int currentHandlerSyncId,
        int totalSlots
    ) {
        Objects.requireNonNull(
            plannedDestinations,
            "plannedDestinations"
        );
        Objects.requireNonNull(itemPlan, "itemPlan");
        return expectedDestination != null
            && Objects.equals(expectedDestination, interactedDestination)
            && plannedDestinations.contains(expectedDestination)
            && !itemPlan.isEmpty()
            && packetSyncId > 0
            && packetSyncId == currentHandlerSyncId
            && (totalSlots == 63 || totalSlots == 90);
    }
}
