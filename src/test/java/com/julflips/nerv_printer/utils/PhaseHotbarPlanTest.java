package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseHotbarPlanTest {
    @Test
    void reservesEightMaterialSlotsAndOneToolSlot() {
        PhaseHotbarPlan.BuildLayout layout =
            PhaseHotbarPlan.buildLayout(
                List.of(8, 4, 0, 1, 2, 3, 5, 6, 7),
                8
            );

        assertEquals(
            List.of(0, 1, 2, 3, 4, 5, 6, 7),
            layout.materialSlots()
        );
        assertEquals(8, layout.toolSlot());
    }

    @Test
    void stackUnitsFollowActualOrderedUses() {
        ArrayList<String> primary = new ArrayList<>();
        primary.add("white");
        primary.add("black");
        for (int index = 0; index < 64; index++) {
            primary.add("white");
        }

        assertEquals(
            List.of("white", "black", "white", "optional"),
            PhaseHotbarPlan.orderedStackUnits(
                primary,
                List.of("optional"),
                Map.of(
                    "white", 64,
                    "black", 64,
                    "optional", 64
                ),
                8
            )
        );
    }

    @Test
    void preservesExistingRequiredItemsIncludingDuplicates() {
        Map<Integer, String> assignments =
            PhaseHotbarPlan.assignRequiredItems(
                List.of(0, 1, 2, 3, 4),
                List.of("pickaxe", "pickaxe", "axe"),
                Map.of(
                    0, "material",
                    1, "pickaxe",
                    2, "material",
                    3, "axe",
                    4, "pickaxe"
                )
            );

        assertEquals(
            Map.of(
                1, "pickaxe",
                3, "axe",
                4, "pickaxe"
            ),
            assignments
        );
    }

    @Test
    void fillsMissingRequirementsIntoLowestUnusedSlots() {
        assertEquals(
            Map.of(0, "pickaxe", 1, "axe"),
            PhaseHotbarPlan.assignRequiredItems(
                List.of(0, 1, 2),
                List.of("pickaxe", "axe"),
                Map.of(2, "shovel")
            )
        );
    }

    @Test
    void rejectsLayoutsWithoutNineManagedSlots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PhaseHotbarPlan.buildLayout(
                List.of(0, 1, 2, 3, 4, 5, 6, 7),
                8
            )
        );
    }
}
