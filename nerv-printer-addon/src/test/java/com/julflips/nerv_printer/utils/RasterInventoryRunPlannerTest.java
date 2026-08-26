package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterInventoryRunPlannerTest {
    @Test
    void fillsEveryUsableSlotWithTheExactRouteOrderedMix() {
        ArrayList<String> route = new ArrayList<>();
        route.addAll(java.util.Collections.nCopies(64, "black"));
        route.add("white");
        RasterInventoryRunPlanner.Plan<String> plan =
            RasterInventoryRunPlanner.create(
                List.of(
                    new RasterInventoryRunPlanner.Slot<>("black", 63, 64),
                    RasterInventoryRunPlanner.Slot.empty(),
                    RasterInventoryRunPlanner.Slot.empty(),
                    new RasterInventoryRunPlanner.Slot<>("tool", 1, 1)
                ),
                route,
                ignored -> 64
            );

        assertEquals(65, plan.coveredTargets());
        assertEquals(64, plan.additions().get("black"));
        assertEquals(64, plan.additions().get("white"));
    }

    @Test
    void stopsAtTheFirstTargetThatWillNotFit() {
        RasterInventoryRunPlanner.Plan<String> plan =
            RasterInventoryRunPlanner.create(
                List.of(
                    new RasterInventoryRunPlanner.Slot<>("black", 64, 64),
                    RasterInventoryRunPlanner.Slot.empty()
                ),
                List.of("black", "white", "gray"),
                ignored -> 1
            );

        assertEquals(2, plan.coveredTargets());
        assertEquals(1, plan.additions().get("white"));
    }

    @Test
    void reservesOneEmptySlotForTheStagedShulkerShell() {
        RasterInventoryRunPlanner.Plan<String> plan =
            RasterInventoryRunPlanner.create(
                List.of(
                    RasterInventoryRunPlanner.Slot.empty(),
                    RasterInventoryRunPlanner.Slot.empty(),
                    new RasterInventoryRunPlanner.Slot<>("tool", 1, 1)
                ),
                List.of("black", "gray"),
                ignored -> 64,
                1
            );

        assertEquals(1, plan.coveredTargets());
        assertEquals(64, plan.additions().get("black"));
    }

    @Test
    void oneOfEveryRequiredMaterialIsEnoughForImmediateStartup() {
        var plan = RasterInventoryRunPlanner.minimumPresence(
            List.of("black", "white", "gray"),
            java.util.Map.of("black", 1, "white", 64, "gray", 2)
        );

        assertTrue(plan.ready());
        assertEquals(java.util.Map.of("black", 1, "white", 1, "gray", 1),
            plan.desiredCounts());
    }

    @Test
    void startupPresenceReportsOnlyCompletelyAbsentTypes() {
        var plan = RasterInventoryRunPlanner.minimumPresence(
            List.of("black", "white", "gray"),
            java.util.Map.of("black", 1, "white", 0)
        );

        assertEquals(java.util.Set.of("white", "gray"), plan.missingMaterials());
    }

    @Test
    void maximumStartupTripSeedsMissingTypesThenFillsRouteAndLeavesOneSlot() {
        ArrayList<String> route = new ArrayList<>();
        route.addAll(java.util.Collections.nCopies(64, "black"));
        route.addAll(java.util.Collections.nCopies(64, "white"));

        var plan = RasterInventoryRunPlanner.create(
            List.of(
                RasterInventoryRunPlanner.Slot.empty(),
                RasterInventoryRunPlanner.Slot.empty(),
                RasterInventoryRunPlanner.Slot.empty(),
                new RasterInventoryRunPlanner.Slot<>("tool", 1, 1)
            ),
            route,
            ignored -> 64,
            1,
            List.of("white")
        );

        assertEquals(java.util.Map.of("white", 64, "black", 64),
            plan.additions());
        assertEquals(128, plan.coveredTargets());
        assertTrue(plan.missingMinimumMaterials().isEmpty());
    }

    @Test
    void reportsAMissingMinimumWhenOnlyTheReservedSlotRemains() {
        var plan = RasterInventoryRunPlanner.create(
            List.of(
                RasterInventoryRunPlanner.Slot.empty(),
                new RasterInventoryRunPlanner.Slot<>("tool", 1, 1)
            ),
            List.of("white"),
            ignored -> 64,
            1,
            List.of("white")
        );

        assertEquals(java.util.Set.of("white"),
            plan.missingMinimumMaterials());
        assertTrue(plan.additions().isEmpty());
    }
}
