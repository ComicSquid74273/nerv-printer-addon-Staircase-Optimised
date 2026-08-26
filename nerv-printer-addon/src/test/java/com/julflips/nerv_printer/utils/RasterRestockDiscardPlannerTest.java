package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterRestockDiscardPlannerTest {
    @Test
    void discardsOnlyWholeSurplusStacks() {
        List<Integer> selected = RasterRestockDiscardPlanner.selectSlots(
            List.of(
                new RasterRestockDiscardPlanner.Slot<>("green", 64),
                new RasterRestockDiscardPlanner.Slot<>("green", 32),
                new RasterRestockDiscardPlanner.Slot<>("white", 64)
            ),
            Set.of("green", "white"),
            Map.of("green", 32, "white", 64)
        );

        assertEquals(List.of(0), selected);
    }

    @Test
    void neverSelectsToolsBoatsOrShulkersOutsideManagedMaterials() {
        List<Integer> selected = RasterRestockDiscardPlanner.selectSlots(
            List.of(
                new RasterRestockDiscardPlanner.Slot<>("pickaxe", 1),
                new RasterRestockDiscardPlanner.Slot<>("boat", 1),
                new RasterRestockDiscardPlanner.Slot<>("shulker", 1),
                new RasterRestockDiscardPlanner.Slot<>("green", 64)
            ),
            Set.of("green"),
            Map.of()
        );

        assertEquals(List.of(3), selected);
    }

    @Test
    void preservesOneOfALaterMaterialEvenWhenItIsNotInTheImmediateRun() {
        List<Integer> selected = RasterRestockDiscardPlanner.selectSlots(
            List.of(
                new RasterRestockDiscardPlanner.Slot<>("later", 64),
                new RasterRestockDiscardPlanner.Slot<>("later", 1)
            ),
            Set.of("later"),
            Map.of("later", 1)
        );

        assertEquals(List.of(0), selected);
    }

    @Test
    void retainsASinglePartialStackWhenDroppingItWouldCrossTheKeepFloor() {
        List<Integer> selected = RasterRestockDiscardPlanner.selectSlots(
            List.of(
                new RasterRestockDiscardPlanner.Slot<>("green", 40),
                new RasterRestockDiscardPlanner.Slot<>("green", 20)
            ),
            Set.of("green"),
            Map.of("green", 32)
        );

        assertEquals(List.of(1), selected);
    }
}
