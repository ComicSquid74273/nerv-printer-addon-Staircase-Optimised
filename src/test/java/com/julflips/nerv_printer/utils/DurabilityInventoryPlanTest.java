package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurabilityInventoryPlanTest {
    @Test
    void keepsFewestHighestDurabilityStacksThatCoverTraversal() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(120, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        5,
                        "pickaxe",
                        30
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        2,
                        "pickaxe",
                        90
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        7,
                        "pickaxe",
                        40
                    )
                ),
                0.0
            );

        assertEquals(List.of(2, 7), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 2), plan.requiredItemCounts());
        assertEquals(Map.of(), plan.missingFreshCounts());
    }

    @Test
    void includesKeptAndMissingFreshToolsInRequiredItemCount() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(220, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        4,
                        "pickaxe",
                        40
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        1,
                        "pickaxe",
                        90
                    )
                ),
                0.0
            );

        assertEquals(List.of(1, 4), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 3), plan.requiredItemCounts());
        assertEquals(Map.of("pickaxe", 1), plan.missingFreshCounts());
    }

    @Test
    void doesNotKeepTinyStackThatCannotReduceFreshToolCount() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(100, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        3,
                        "pickaxe",
                        1
                    )
                ),
                0.0
            );

        assertEquals(List.of(), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 1), plan.requiredItemCounts());
        assertEquals(Map.of("pickaxe", 1), plan.missingFreshCounts());
    }

    @Test
    void replacesFragmentedToolsWhenOneFreshToolUsesFewerSlots() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(100, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        4,
                        "pickaxe",
                        60
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        2,
                        "pickaxe",
                        50
                    )
                ),
                0.0
            );

        assertEquals(List.of(), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 1), plan.requiredItemCounts());
        assertEquals(Map.of("pickaxe", 1), plan.missingFreshCounts());
    }

    @Test
    void equalSlotPlanPrefersRetainingExistingUsableTools() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(150, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        6,
                        "pickaxe",
                        60
                    )
                ),
                0.0
            );

        assertEquals(List.of(6), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 2), plan.requiredItemCounts());
        assertEquals(Map.of("pickaxe", 1), plan.missingFreshCounts());
    }

    @Test
    void appliesBufferBeforeSelectingExistingAndFreshTools() {
        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                Map.of(
                    "pickaxe",
                    new DurabilityInventoryPlan.Requirement(100, 100)
                ),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        8,
                        "pickaxe",
                        70
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        6,
                        "pickaxe",
                        50
                    )
                ),
                0.2
            );

        assertEquals(List.of(6, 8), plan.keepSlots());
        assertEquals(Map.of("pickaxe", 2), plan.requiredItemCounts());
        assertEquals(Map.of(), plan.missingFreshCounts());
    }

    @Test
    void preservesRequirementOrderAndUsesSlotTieBreaker() {
        LinkedHashMap<String, DurabilityInventoryPlan.Requirement>
            requirements = new LinkedHashMap<>();
        requirements.put(
            "shovel",
            new DurabilityInventoryPlan.Requirement(30, 100)
        );
        requirements.put(
            "pickaxe",
            new DurabilityInventoryPlan.Requirement(50, 100)
        );

        DurabilityInventoryPlan<String> plan =
            DurabilityInventoryPlan.plan(
                requirements,
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        9,
                        "pickaxe",
                        50
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        7,
                        "pickaxe",
                        50
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        4,
                        "shovel",
                        30
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        1,
                        "unneeded",
                        100
                    )
                ),
                0.0
            );

        assertEquals(List.of(4, 7), plan.keepSlots());
        assertEquals(
            List.of("shovel", "pickaxe"),
            plan.requiredItemCounts().keySet().stream().toList()
        );
        assertEquals(
            Map.of("shovel", 1, "pickaxe", 1),
            plan.requiredItemCounts()
        );
    }

    @Test
    void validatesDuplicateSlotsAndInvalidInputs() {
        DurabilityInventoryPlan.Requirement requirement =
            new DurabilityInventoryPlan.Requirement(1, 100);

        assertThrows(
            IllegalArgumentException.class,
            () -> DurabilityInventoryPlan.plan(
                Map.of("pickaxe", requirement),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        2,
                        "pickaxe",
                        50
                    ),
                    new DurabilityInventoryPlan.ToolStack<>(
                        2,
                        "pickaxe",
                        40
                    )
                ),
                0.0
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DurabilityInventoryPlan.plan(
                Map.of("pickaxe", requirement),
                List.of(
                    new DurabilityInventoryPlan.ToolStack<>(
                        2,
                        "pickaxe",
                        101
                    )
                ),
                0.0
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DurabilityInventoryPlan.plan(
                Map.of("pickaxe", requirement),
                List.of(),
                Double.NaN
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new DurabilityInventoryPlan.Requirement(-1, 100)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new DurabilityInventoryPlan.ToolStack<>(
                0,
                "pickaxe",
                -1
            )
        );
    }
}
