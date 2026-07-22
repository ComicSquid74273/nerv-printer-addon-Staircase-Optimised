package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Tracks one persistent main-inventory to hotbar swap until a server inventory
 * revision confirms the expected destination item.
 *
 * <p>The controller deliberately does not perform inventory clicks. Runtime
 * code owns slot lookup and dispatch, while this class owns the acknowledgement
 * and bounded-retry contract.</p>
 */
public final class ConfirmedHotbarSwap<T> {
    public enum Observation {
        IDLE,
        WAITING,
        CONFIRMED,
        RETRY_REQUIRED,
        FAILED
    }

    private Request<T> request;

    public void begin(
        int targetHotbarSlot,
        T expected,
        long inventoryRevision,
        long tick
    ) {
        if (targetHotbarSlot < 0 || targetHotbarSlot > 8) {
            throw new IllegalArgumentException(
                "Target hotbar slot must be between 0 and 8."
            );
        }
        if (request != null) {
            throw new IllegalStateException("A hotbar swap is already pending.");
        }
        request = new Request<>(
            targetHotbarSlot,
            Objects.requireNonNull(expected, "expected"),
            inventoryRevision,
            tick,
            1
        );
    }

    public Observation observe(
        T destinationItem,
        long inventoryRevision,
        long tick,
        int timeoutTicks,
        int maximumAttempts
    ) {
        if (timeoutTicks < 1) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException(
                "Maximum attempts must be positive."
            );
        }
        if (request == null) return Observation.IDLE;

        if (Objects.equals(request.expected(), destinationItem)
            && inventoryRevision > request.inventoryRevision()) {
            request = null;
            return Observation.CONFIRMED;
        }
        if (tick - request.startedAtTick() < timeoutTicks) {
            return Observation.WAITING;
        }
        if (request.attempts() >= maximumAttempts) {
            request = null;
            return Observation.FAILED;
        }
        return Observation.RETRY_REQUIRED;
    }

    public void markRetried(long inventoryRevision, long tick) {
        if (request == null) {
            throw new IllegalStateException("No hotbar swap is pending.");
        }
        request = new Request<>(
            request.targetHotbarSlot(),
            request.expected(),
            inventoryRevision,
            tick,
            request.attempts() + 1
        );
    }

    public boolean isPending() {
        return request != null;
    }

    public int targetHotbarSlot() {
        return request == null ? -1 : request.targetHotbarSlot();
    }

    public T expected() {
        return request == null ? null : request.expected();
    }

    public int attempts() {
        return request == null ? 0 : request.attempts();
    }

    public void clear() {
        request = null;
    }

    private record Request<T>(
        int targetHotbarSlot,
        T expected,
        long inventoryRevision,
        long startedAtTick,
        int attempts
    ) {
    }
}
