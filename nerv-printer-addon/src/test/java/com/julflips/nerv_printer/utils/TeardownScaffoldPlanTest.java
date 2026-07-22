package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.julflips.nerv_printer.utils.TeardownScaffoldPlan.Cell.AIR;
import static com.julflips.nerv_printer.utils.TeardownScaffoldPlan.Cell.BLOCKED;
import static com.julflips.nerv_printer.utils.TeardownScaffoldPlan.Cell.OWNED;
import static com.julflips.nerv_printer.utils.TeardownScaffoldPlan.Endpoint.END;
import static com.julflips.nerv_printer.utils.TeardownScaffoldPlan.Endpoint.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeardownScaffoldPlanTest {
    @Test
    void choosesTheClosestEndpointAndStopsBeforeTheCleanupTarget() {
        var plan = TeardownScaffoldPlan.create(
            List.of(AIR, AIR, AIR, AIR, AIR, AIR, OWNED, AIR),
            3
        ).orElseThrow();

        assertEquals(END, plan.endpoint());
        assertEquals(List.of(7), plan.outwardSupportIndices());
        assertEquals(List.of(7), plan.scaffoldIndices());
        assertEquals(6, plan.terminalCleanupIndex());
    }

    @Test
    void groupsOwnedSupportsAlongTheSelectedHalfForReturnCleanup() {
        var plan = TeardownScaffoldPlan.create(
            List.of(AIR, OWNED, AIR, OWNED, AIR, AIR, AIR, AIR, AIR),
            3
        ).orElseThrow();

        assertEquals(START, plan.endpoint());
        assertEquals(List.of(0, 1, 2), plan.outwardSupportIndices());
        assertEquals(List.of(0, 2), plan.scaffoldIndices());
        assertEquals(List.of(0, 1, 2, 3), plan.cleanupIndices());
        assertEquals(2, plan.ownedCleanupCount());
    }

    @Test
    void blockedPrefixCannotBeUsedAsAHiddenScaffoldFallback() {
        var plan = TeardownScaffoldPlan.create(
            List.of(AIR, BLOCKED, OWNED, AIR, AIR, OWNED, AIR),
            4
        ).orElseThrow();

        assertEquals(END, plan.endpoint());
        assertEquals(5, plan.terminalCleanupIndex());
    }

    @Test
    void refusesOwnedWorkBeyondTheAvailableScaffoldReserve() {
        assertTrue(
            TeardownScaffoldPlan.create(
                List.of(AIR, AIR, AIR, OWNED, AIR, AIR, AIR),
                2
            ).isEmpty()
        );
    }
}
