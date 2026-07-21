package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeardownScaffoldMaterialPlanTest {
    @Test
    void threeStackReserveUsesPartialStacksBeforeNewSlots() {
        var plan = TeardownScaffoldMaterialPlan.create(
            3,
            64,
            List.of(32, 64)
        );

        assertEquals(192, plan.targetAmount());
        assertEquals(96, plan.onHandAmount());
        assertEquals(96, plan.missingAmount());
        assertEquals(1, plan.additionalSlotsRequired());
    }

    @Test
    void satisfiedReserveNeedsNoAdditionalSlot() {
        var plan = TeardownScaffoldMaterialPlan.create(
            2,
            64,
            List.of(64, 64, 12)
        );

        assertEquals(0, plan.missingAmount());
        assertEquals(0, plan.additionalSlotsRequired());
    }
}
