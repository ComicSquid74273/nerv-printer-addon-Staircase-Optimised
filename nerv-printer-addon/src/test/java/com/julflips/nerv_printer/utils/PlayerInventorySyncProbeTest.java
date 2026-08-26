package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerInventorySyncProbeTest {
    @Test
    void mapsEveryHotbarSlotToTheSamePlayerHandlerInventorySlot() {
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            assertEquals(
                36 + hotbarSlot,
                PlayerInventorySyncProbe.handlerSlotForHotbar(hotbarSlot)
            );
        }
    }

    @Test
    void rejectsSlotsOutsideTheHotbar() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PlayerInventorySyncProbe.handlerSlotForHotbar(-1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PlayerInventorySyncProbe.handlerSlotForHotbar(9)
        );
    }
}
