package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Server-authoritative view of one open screen handler used to confirm a
 * container-to-player inventory transfer.
 *
 * <p>A normally predicted and accepted screen click may emit no packet at
 * all. Printer-owned transfers therefore force a full handler
 * resynchronization; bounded recovery may also observe corrective individual
 * slot updates. This snapshot accepts either authoritative form while binding
 * every update to the handler sync id.</p>
 */
public final class ServerInventoryTransferSnapshot {
    /**
     * Relationship between a deferred full response, the snapshot accepted
     * from it, and the screen handler that is live when the response is later
     * processed.
     *
     * <p>A response is accepted only while its handler is current. Callers may
     * deliberately delay the resulting inventory action, however, and the
     * screen can close during that delay. That does not make the already
     * accepted server observation invalid; it means a fresh handler must be
     * opened before another click can be submitted.</p>
     */
    public enum HandlerDisposition {
        REJECTED,
        CURRENT,
        ACCEPTED_HANDLER_NOT_CURRENT
    }

    public record SlotState(int compatibleCount, boolean canReceive) {
        public SlotState {
            if (compatibleCount < 0) {
                throw new IllegalArgumentException(
                    "Compatible item count cannot be negative."
                );
            }
        }
    }

    private final int playerSlotCount;
    private int syncId = -1;
    private int containerSlotCount = -1;
    private final ArrayList<SlotState> slots = new ArrayList<>();

    public ServerInventoryTransferSnapshot(int playerSlotCount) {
        if (playerSlotCount <= 0) {
            throw new IllegalArgumentException(
                "Player slot count must be positive."
            );
        }
        this.playerSlotCount = playerSlotCount;
    }

    public void clear() {
        syncId = -1;
        containerSlotCount = -1;
        slots.clear();
    }

    public void replace(int syncId, List<SlotState> contents) {
        if (syncId < 0) {
            throw new IllegalArgumentException(
                "Screen-handler sync id cannot be negative."
            );
        }
        Objects.requireNonNull(contents, "contents");
        if (contents.size() < playerSlotCount) {
            throw new IllegalArgumentException(
                "Screen contents do not include every player inventory slot."
            );
        }

        this.syncId = syncId;
        this.containerSlotCount = contents.size() - playerSlotCount;
        slots.clear();
        for (SlotState state : contents) {
            slots.add(Objects.requireNonNull(state, "slot state"));
        }
    }

    /**
     * Applies a single authoritative slot update when it belongs to the
     * currently tracked screen. Cursor and out-of-range updates are ignored.
     */
    public boolean updateSlot(
        int syncId,
        int slot,
        SlotState state
    ) {
        Objects.requireNonNull(state, "state");
        if (!initialized()
            || this.syncId != syncId
            || slot < 0
            || slot >= slots.size()) {
            return false;
        }
        slots.set(slot, state);
        return true;
    }

    public boolean initialized() {
        return syncId >= 0 && containerSlotCount >= 0;
    }

    public int syncId() {
        return syncId;
    }

    public int containerSlotCount() {
        return containerSlotCount;
    }

    public HandlerDisposition handlerDisposition(
        int responseSyncId,
        int currentHandlerSyncId
    ) {
        if (!initialized() || responseSyncId != syncId) {
            return HandlerDisposition.REJECTED;
        }
        return currentHandlerSyncId == responseSyncId
            ? HandlerDisposition.CURRENT
            : HandlerDisposition.ACCEPTED_HANDLER_NOT_CURRENT;
    }

    public int compatibleCountAt(int slot) {
        if (slot < 0 || slot >= slots.size()) return 0;
        return slots.get(slot).compatibleCount();
    }

    public int compatiblePlayerCount() {
        if (!initialized()) return 0;
        int count = 0;
        for (int slot = containerSlotCount; slot < slots.size(); slot++) {
            count = Math.addExact(
                count,
                slots.get(slot).compatibleCount()
            );
        }
        return count;
    }

    public int firstCompatibleContainerSlot() {
        return nextCompatibleContainerSlot(-1);
    }

    /**
     * Selects the next compatible container slot in cyclic handler order.
     *
     * <p>Restock callers retain the last submitted source slot and pass it
     * here, ensuring that an automatically replenished low-numbered slot
     * cannot starve other compatible stacks in the same chest.</p>
     */
    public int nextCompatibleContainerSlot(int previousSlot) {
        return nextCompatibleContainerSlot(previousSlot, 1);
    }

    /**
     * Selects the next compatible source whose authoritative count has
     * reached the required transfer size.
     *
     * <p>This mirrors the original printer's material-chest behavior: partial
     * block stacks are not clicked while a sorter is still filling them, and
     * every other container slot is checked before cyclically returning to the
     * previous source.</p>
     */
    public int nextCompatibleContainerSlot(
        int previousSlot,
        int requiredCount
    ) {
        if (requiredCount <= 0) {
            throw new IllegalArgumentException(
                "Required source count must be positive."
            );
        }
        if (!initialized()
            || previousSlot < -1
            || previousSlot >= containerSlotCount) {
            return -1;
        }
        for (int offset = 1;
             offset <= containerSlotCount;
             offset++) {
            int slot = (previousSlot + offset) % containerSlotCount;
            if (slots.get(slot).compatibleCount() >= requiredCount) {
                return slot;
            }
        }
        return -1;
    }

    public boolean playerHasCapacity() {
        if (!initialized()) return false;
        for (int slot = containerSlotCount; slot < slots.size(); slot++) {
            if (slots.get(slot).canReceive()) return true;
        }
        return false;
    }

    /**
     * Confirms that the submitted source stack decreased and the compatible
     * player total increased. Requiring both observations prevents an
     * unrelated inventory gain or the source-first half of a QUICK_MOVE
     * response from completing the transaction.
     */
    public boolean confirmsTransfer(
        int sourceSlot,
        int beforeSourceCount,
        int beforePlayerCount
    ) {
        if (!initialized()
            || sourceSlot < 0
            || sourceSlot >= containerSlotCount
            || beforeSourceCount <= 0
            || beforePlayerCount < 0) {
            return false;
        }
        return compatibleCountAt(sourceSlot) < beforeSourceCount
            && compatiblePlayerCount() > beforePlayerCount;
    }

    /**
     * Confirms progress from one coherent full-handler snapshot.
     *
     * <p>The compatible player total is the restock transaction's actual
     * objective. An automated supplier may replenish or replace the clicked
     * container slot before the server sends its forced full snapshot, so the
     * post-click source count is not a reliable completion signal. Callers
     * must use this method only for a newer full snapshot; piecemeal slot
     * updates should continue to use {@link #confirmsTransfer(int, int, int)}
     * until both halves have been observed.</p>
     */
    public boolean confirmsCompatiblePlayerProgress(
        int beforePlayerCount
    ) {
        return initialized()
            && beforePlayerCount >= 0
            && compatiblePlayerCount() > beforePlayerCount;
    }
}
