package com.julflips.nerv_printer.utils;

import java.util.List;

/**
 * Pure planning and reconciliation rules for Boat Raster restocks. Network
 * clicks remain owned by Nerv's authoritative inventory interaction path.
 */
public final class RasterRestockTransferPolicy {
    private RasterRestockTransferPolicy() {
    }

    public enum Confirmation {
        PROGRESS,
        RETRY,
        FAIL
    }

    public enum ReturnConfirmation {
        COMPLETE,
        RETRY,
        FAIL
    }

    public static int plannedAmount(
        int remainingDemand,
        int lookaheadDemand,
        int maximumStackSize,
        int inventoryCapacity,
        int sourceAvailable
    ) {
        if (remainingDemand <= 0
            || maximumStackSize <= 0
            || inventoryCapacity <= 0
            || sourceAvailable <= 0) {
            return 0;
        }
        int boundedLookahead = Math.max(0, lookaheadDemand);
        int minimumUsefulBatch = Math.min(maximumStackSize, remainingDemand);
        int requested = Math.min(
            remainingDemand,
            Math.max(minimumUsefulBatch, boundedLookahead)
        );
        return Math.min(requested, Math.min(inventoryCapacity, sourceAvailable));
    }

    public static boolean targetReached(
        int authoritativePlayerCount,
        int targetPlayerCount
    ) {
        return targetPlayerCount > 0
            && authoritativePlayerCount >= targetPlayerCount;
    }

    /** Chooses the largest source stack that does not exceed the remaining target. */
    public static int bestSourceStackIndex(
        List<Integer> sourceStackCounts,
        int remainingTarget
    ) {
        int bestWithin = -1;
        int bestWithinCount = -1;
        int smallestOvershoot = -1;
        int smallestOvershootCount = Integer.MAX_VALUE;
        for (int index = 0; index < sourceStackCounts.size(); index++) {
            int count = sourceStackCounts.get(index);
            if (count <= 0) continue;
            if (count <= remainingTarget && count > bestWithinCount) {
                bestWithin = index;
                bestWithinCount = count;
            } else if (count > remainingTarget && count < smallestOvershootCount) {
                smallestOvershoot = index;
                smallestOvershootCount = count;
            }
        }
        return bestWithin >= 0 ? bestWithin : smallestOvershoot;
    }

    /** Chooses the fullest shulker, retaining stable slot order for ties. */
    public static int fullestShulkerIndex(List<Integer> containedCounts) {
        int best = -1;
        int bestCount = -1;
        for (int index = 0; index < containedCounts.size(); index++) {
            int count = containedCounts.get(index);
            if (count > bestCount) {
                best = index;
                bestCount = count;
            }
        }
        return bestCount > 0 ? best : -1;
    }

    public static Confirmation confirmAfterReopen(
        int beforePlayerCount,
        int authoritativePlayerCount,
        int completedNoProgressRetries,
        int maximumNoProgressRetries
    ) {
        if (authoritativePlayerCount > beforePlayerCount) {
            return Confirmation.PROGRESS;
        }
        return completedNoProgressRetries >= maximumNoProgressRetries
            ? Confirmation.FAIL
            : Confirmation.RETRY;
    }

    public static ReturnConfirmation confirmReturnAfterReopen(
        int playerShulkerCount,
        int sourceChestShulkerCount,
        int completedNoProgressRetries,
        int maximumNoProgressRetries
    ) {
        if (playerShulkerCount <= 0) {
            return sourceChestShulkerCount > 0
                ? ReturnConfirmation.COMPLETE
                : ReturnConfirmation.FAIL;
        }
        return completedNoProgressRetries >= maximumNoProgressRetries
            ? ReturnConfirmation.FAIL
            : ReturnConfirmation.RETRY;
    }
}
