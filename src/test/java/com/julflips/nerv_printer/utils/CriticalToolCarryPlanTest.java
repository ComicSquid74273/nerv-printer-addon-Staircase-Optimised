package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CriticalToolCarryPlanTest {
    @Test
    void reusesRequiredToolsAboveTheCriticalThreshold() {
        CriticalToolCarryPlan.Result<String> result =
            CriticalToolCarryPlan.plan(
                Set.of("pickaxe", "axe"),
                List.of(
                    new CriticalToolCarryPlan.ToolStack<>(
                        3,
                        "pickaxe",
                        21,
                        true
                    ),
                    new CriticalToolCarryPlan.ToolStack<>(
                        5,
                        "axe",
                        200,
                        true
                    )
                ),
                20
            );

        assertEquals(
            Map.of("pickaxe", 1, "axe", 1),
            result.requiredItemCounts()
        );
        assertEquals(Set.of(3, 5), result.requiredKeepSlots());
        assertEquals(Set.of(3, 5), result.keepSlots());
        assertEquals(Set.of(), result.usedToolSlots());
    }

    @Test
    void replacesToolsAtTheCriticalThreshold() {
        CriticalToolCarryPlan.Result<String> result =
            CriticalToolCarryPlan.plan(
                Set.of("pickaxe"),
                List.of(
                    new CriticalToolCarryPlan.ToolStack<>(
                        7,
                        "pickaxe",
                        20,
                        true
                    )
                ),
                20
            );

        assertEquals(Map.of("pickaxe", 1), result.requiredItemCounts());
        assertEquals(Set.of(), result.requiredKeepSlots());
        assertEquals(Set.of(), result.keepSlots());
        assertEquals(Set.of(7), result.usedToolSlots());
    }

    @Test
    void doesNotDemandEveryRegisteredToolType() {
        CriticalToolCarryPlan.Result<String> result =
            CriticalToolCarryPlan.plan(
                Set.of("pickaxe", "axe"),
                List.of(
                    new CriticalToolCarryPlan.ToolStack<>(
                        1,
                        "hoe",
                        100,
                        true
                    ),
                    new CriticalToolCarryPlan.ToolStack<>(
                        2,
                        "shears",
                        10,
                        true
                    )
                ),
                20
            );

        assertEquals(
            Map.of("pickaxe", 1, "axe", 1),
            result.requiredItemCounts()
        );
        assertEquals(Set.of(1), result.keepSlots());
        assertEquals(Set.of(2), result.usedToolSlots());
    }

    @Test
    void selectsTheStrongestCompatibleCarriedTool() {
        CriticalToolCarryPlan.Result<String> result =
            CriticalToolCarryPlan.plan(
                Set.of("pickaxe"),
                List.of(
                    new CriticalToolCarryPlan.ToolStack<>(
                        4,
                        "pickaxe",
                        200,
                        false
                    ),
                    new CriticalToolCarryPlan.ToolStack<>(
                        6,
                        "pickaxe",
                        40,
                        true
                    ),
                    new CriticalToolCarryPlan.ToolStack<>(
                        8,
                        "pickaxe",
                        300,
                        true
                    )
                ),
                20
            );

        assertEquals(Set.of(8), result.requiredKeepSlots());
        assertEquals(Set.of(4, 6, 8), result.keepSlots());
        assertEquals(Set.of(), result.usedToolSlots());
    }
}
