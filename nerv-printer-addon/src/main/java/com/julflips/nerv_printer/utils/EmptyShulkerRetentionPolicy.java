package com.julflips.nerv_printer.utils;

/**
 * Keeps collected empty shulker boxes until a refill cannot make progress
 * without releasing inventory capacity.
 */
public final class EmptyShulkerRetentionPolicy {
    private EmptyShulkerRetentionPolicy() {
    }

    public record Candidate(int slot, boolean emptyShulker) {
    }

    public static int selectDumpSlot(
        Iterable<Candidate> candidates,
        boolean releaseEmptyShulkerForCapacity
    ) {
        int retainedEmptyShulkerSlot = -1;
        for (Candidate candidate : candidates) {
            if (!candidate.emptyShulker()) return candidate.slot();
            if (retainedEmptyShulkerSlot < 0) {
                retainedEmptyShulkerSlot = candidate.slot();
            }
        }
        return releaseEmptyShulkerForCapacity
            ? retainedEmptyShulkerSlot
            : -1;
    }
}
