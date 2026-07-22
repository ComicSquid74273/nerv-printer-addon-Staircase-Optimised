package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerInventoryTransferSnapshotTest {
    private static final ServerInventoryTransferSnapshot.SlotState EMPTY =
        new ServerInventoryTransferSnapshot.SlotState(0, true);
    private static final ServerInventoryTransferSnapshot.SlotState BLOCKED =
        new ServerInventoryTransferSnapshot.SlotState(0, false);

    @Test
    void sourceFirstSlotUpdateDoesNotConfirmQuickMove() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            7,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                BLOCKED,
                EMPTY,
                BLOCKED
            )
        );

        assertTrue(snapshot.updateSlot(7, 0, EMPTY));
        assertFalse(snapshot.confirmsTransfer(0, 64, 0));

        assertTrue(
            snapshot.updateSlot(
                7,
                2,
                new ServerInventoryTransferSnapshot.SlotState(64, false)
            )
        );
        assertTrue(snapshot.confirmsTransfer(0, 64, 0));
    }

    @Test
    void playerFirstSlotUpdateWaitsForSourceDecrease() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            4,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(32, false),
                EMPTY,
                EMPTY
            )
        );

        snapshot.updateSlot(
            4,
            1,
            new ServerInventoryTransferSnapshot.SlotState(32, false)
        );
        assertFalse(snapshot.confirmsTransfer(0, 32, 0));

        snapshot.updateSlot(4, 0, EMPTY);
        assertTrue(snapshot.confirmsTransfer(0, 32, 0));
    }

    @Test
    void fullSnapshotAcceptsPlayerGainWhenSupplierRestoresSourceCount() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            5,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                BLOCKED,
                EMPTY,
                BLOCKED
            )
        );

        snapshot.replace(
            5,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                BLOCKED,
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                BLOCKED
            )
        );

        assertFalse(snapshot.confirmsTransfer(0, 64, 0));
        assertTrue(snapshot.confirmsCompatiblePlayerProgress(0));
    }

    @Test
    void fullSnapshotAcceptsPlayerGainWhenSupplierRaisesSourceCount() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            6,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                BLOCKED,
                new ServerInventoryTransferSnapshot.SlotState(32, false),
                BLOCKED
            )
        );

        assertFalse(snapshot.confirmsTransfer(0, 32, 0));
        assertTrue(snapshot.confirmsCompatiblePlayerProgress(0));
    }

    @Test
    void successiveTransfersRemainConfirmedWhenSupplierRefillsSameSlot() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(4);
        ServerInventoryTransferSnapshot.SlotState fullStack =
            new ServerInventoryTransferSnapshot.SlotState(64, false);
        snapshot.replace(
            10,
            List.of(
                fullStack,
                BLOCKED,
                EMPTY,
                EMPTY,
                EMPTY,
                BLOCKED
            )
        );

        for (int transferredStacks = 1;
             transferredStacks <= 3;
             transferredStacks++) {
            int beforePlayerCount = (transferredStacks - 1) * 64;
            snapshot.replace(
                10,
                List.of(
                    fullStack,
                    BLOCKED,
                    fullStack,
                    transferredStacks >= 2 ? fullStack : EMPTY,
                    transferredStacks >= 3 ? fullStack : EMPTY,
                    BLOCKED
                )
            );

            assertFalse(
                snapshot.confirmsTransfer(
                    0,
                    64,
                    beforePlayerCount
                )
            );
            assertTrue(
                snapshot.confirmsCompatiblePlayerProgress(
                    beforePlayerCount
                )
            );
        }
    }

    @Test
    void fullSnapshotRejectsSourceChangeWithoutPlayerProgress() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            8,
            List.of(
                EMPTY,
                BLOCKED,
                EMPTY,
                BLOCKED
            )
        );

        assertFalse(snapshot.confirmsCompatiblePlayerProgress(0));
        assertFalse(snapshot.confirmsCompatiblePlayerProgress(-1));
    }

    @Test
    void ignoresOtherHandlerAndCursorUpdates() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(1);
        snapshot.replace(
            9,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(1, false),
                EMPTY
            )
        );

        assertFalse(snapshot.updateSlot(8, 0, EMPTY));
        assertFalse(snapshot.updateSlot(9, -1, EMPTY));
        assertEquals(1, snapshot.compatibleCountAt(0));
        assertFalse(snapshot.confirmsTransfer(0, 1, 0));
    }

    @Test
    void fullSnapshotReplacementSupportsNonzeroSyncIdsAndCapacity() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            12,
            List.of(
                BLOCKED,
                BLOCKED,
                new ServerInventoryTransferSnapshot.SlotState(20, true),
                BLOCKED
            )
        );

        assertEquals(12, snapshot.syncId());
        assertEquals(2, snapshot.containerSlotCount());
        assertEquals(20, snapshot.compatiblePlayerCount());
        assertTrue(snapshot.playerHasCapacity());
        assertEquals(-1, snapshot.firstCompatibleContainerSlot());
    }

    @Test
    void acceptedDeferredResponseDistinguishesLostHandlerFromRejection() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            12,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                EMPTY,
                BLOCKED
            )
        );

        assertEquals(
            ServerInventoryTransferSnapshot.HandlerDisposition.CURRENT,
            snapshot.handlerDisposition(12, 12)
        );
        assertEquals(
            ServerInventoryTransferSnapshot.HandlerDisposition
                .ACCEPTED_HANDLER_NOT_CURRENT,
            snapshot.handlerDisposition(12, 0)
        );
        assertEquals(
            ServerInventoryTransferSnapshot.HandlerDisposition.REJECTED,
            snapshot.handlerDisposition(11, 12)
        );

        snapshot.clear();
        assertEquals(
            ServerInventoryTransferSnapshot.HandlerDisposition.REJECTED,
            snapshot.handlerDisposition(12, 12)
        );
    }

    @Test
    void compatibleSourceSelectionRotatesAcrossEveryChestSlot() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            14,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                new ServerInventoryTransferSnapshot.SlotState(32, false),
                new ServerInventoryTransferSnapshot.SlotState(16, false),
                EMPTY,
                BLOCKED
            )
        );

        assertEquals(0, snapshot.nextCompatibleContainerSlot(-1));
        assertEquals(1, snapshot.nextCompatibleContainerSlot(0));
        assertEquals(2, snapshot.nextCompatibleContainerSlot(1));
        assertEquals(0, snapshot.nextCompatibleContainerSlot(2));
        assertEquals(-1, snapshot.nextCompatibleContainerSlot(3));
    }

    @Test
    void readySourceSelectionSkipsPartialStacksAndChecksEverySlot() {
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        snapshot.replace(
            15,
            List.of(
                new ServerInventoryTransferSnapshot.SlotState(32, false),
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                new ServerInventoryTransferSnapshot.SlotState(16, false),
                new ServerInventoryTransferSnapshot.SlotState(64, false),
                EMPTY,
                BLOCKED
            )
        );

        assertEquals(
            1,
            snapshot.nextCompatibleContainerSlot(-1, 64)
        );
        assertEquals(
            3,
            snapshot.nextCompatibleContainerSlot(1, 64)
        );
        assertEquals(
            1,
            snapshot.nextCompatibleContainerSlot(3, 64)
        );
        assertEquals(
            0,
            snapshot.nextCompatibleContainerSlot(-1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> snapshot.nextCompatibleContainerSlot(-1, 0)
        );
    }

    @Test
    void validatesSnapshotShapeAndCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ServerInventoryTransferSnapshot(0)
        );
        ServerInventoryTransferSnapshot snapshot =
            new ServerInventoryTransferSnapshot(2);
        assertThrows(
            IllegalArgumentException.class,
            () -> snapshot.replace(1, List.of(EMPTY))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new ServerInventoryTransferSnapshot.SlotState(-1, false)
        );
    }
}
