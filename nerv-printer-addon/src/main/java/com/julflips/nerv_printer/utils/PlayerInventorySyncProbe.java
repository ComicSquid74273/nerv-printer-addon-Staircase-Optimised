package com.julflips.nerv_printer.utils;

/**
 * Maps player hotbar indexes to player-screen handler slots for a SWAP click
 * whose source and destination are the same inventory slot. The click is a
 * no-op, but the forced stale revision makes the server return an
 * authoritative player-inventory snapshot.
 */
public final class PlayerInventorySyncProbe {
    private PlayerInventorySyncProbe() {
    }

    public static int handlerSlotForHotbar(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) {
            throw new IllegalArgumentException(
                "Hotbar slot must be in the range 0..8."
            );
        }
        return 36 + hotbarSlot;
    }
}
