package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryCapacityTest {
    @Test
    void honorsEachMaterialsActualMaximumStackSize() {
        assertEquals(
            7,
            InventoryCapacity.slotsRequired(List.of(
                new InventoryCapacity.Requirement(65, 64),
                new InventoryCapacity.Requirement(17, 16),
                new InventoryCapacity.Requirement(3, 1)
            ))
        );
    }

    @Test
    void exactMultiplesDoNotAllocateAnExtraSlot() {
        assertEquals(0, InventoryCapacity.slotsForAmount(0, 64));
        assertEquals(1, InventoryCapacity.slotsForAmount(64, 64));
        assertEquals(2, InventoryCapacity.slotsForAmount(32, 16));
    }

    @Test
    void rejectsInvalidStackSizes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryCapacity.slotsForAmount(1, 0)
        );
    }
}
