package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningToolInventoryPlanTest {
    private record ToolSpec(Set<String> capabilities, int efficiency) {
    }

    private record Requirement(String capability, int minimumEfficiency) {
    }

    @Test
    void createsAbsoluteDemandFromCompatibleOnHandAndMissingUsableTools() {
        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = plan(
            List.of(
                tool("pickaxe", 19, 100, "stone", 5),
                tool("pickaxe", 100, 100, "stone", 5),
                tool("pickaxe", 80, 100, "dirt", 5),
                tool("shovel", 100, 100, "stone", 5)
            ),
            Map.of("pickaxe", 2)
        );

        assertEquals(2, plan.compatibleOnHandCount("pickaxe"));
        assertEquals(
            new RestockDemand<>("pickaxe", 4, 2),
            plan.restockDemand("pickaxe").orElseThrow()
        );
    }

    @Test
    void carriedAndChestToolsShareTheTenPercentFloor() {
        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = plan(
            List.of(tool("pickaxe", 20, 100, "stone", 5)),
            Map.of("pickaxe", 1)
        );
        MiningToolInventoryPlan.Tool<String, ToolSpec> sixtyPercent =
            tool("pickaxe", 60, 100, "stone", 5);
        MiningToolInventoryPlan.Tool<String, ToolSpec> boundary =
            tool("pickaxe", 10, 100, "stone", 5);
        MiningToolInventoryPlan.Tool<String, ToolSpec> belowFloor =
            tool("pickaxe", 9, 100, "stone", 5);

        assertTrue(plan.isUsableCompatiblePlayerTool(sixtyPercent));
        assertTrue(plan.isUsableCompatibleChestCandidate(sixtyPercent));
        assertTrue(plan.isUsableCompatiblePlayerTool(boundary));
        assertTrue(plan.isUsableCompatibleChestCandidate(boundary));
        assertFalse(plan.isUsableCompatiblePlayerTool(belowFloor));
        assertFalse(plan.isUsableCompatibleChestCandidate(belowFloor));

        RestockDemand<String> demand =
            plan.restockDemand("pickaxe").orElseThrow();
        assertEquals(2, demand.targetCompatiblePlayerCount());
        assertEquals(1, demand.remainingAmount());
    }

    @Test
    void reservesFinalDurabilityPointForBothClassifications() {
        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = plan(List.of(), Map.of("pickaxe", 1));
        MiningToolInventoryPlan.Tool<String, ToolSpec> exhausted =
            tool("pickaxe", 1, 100, "stone", 5);

        assertFalse(plan.isUsableCompatiblePlayerTool(exhausted));
        assertFalse(
            plan.isUsableCompatibleChestCandidate(exhausted)
        );
    }

    @Test
    void candidateMustSatisfyEveryPerItemRequirement() {
        LinkedHashMap<String, List<Requirement>> requirements =
            new LinkedHashMap<>();
        requirements.put(
            "pickaxe",
            List.of(
                new Requirement("stone", 4),
                new Requirement("ore", 5)
            )
        );
        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = MiningToolInventoryPlan.plan(
            requirements,
            List.of(),
            Map.of("pickaxe", 1),
            0.10,
            MiningToolInventoryPlanTest::isCompatible
        );

        assertFalse(
            plan.isUsableCompatibleChestCandidate(
                tool("pickaxe", 100, 100, "stone", 5)
            )
        );
        assertFalse(
            plan.isUsableCompatibleChestCandidate(
                new MiningToolInventoryPlan.Tool<>(
                    "pickaxe",
                    new ToolSpec(Set.of("stone", "ore"), 4),
                    100,
                    100
                )
            )
        );
        assertTrue(
            plan.isUsableCompatibleChestCandidate(
                new MiningToolInventoryPlan.Tool<>(
                    "pickaxe",
                    new ToolSpec(Set.of("stone", "ore"), 5),
                    100,
                    100
                )
            )
        );
    }

    @Test
    void authoritativeRecountCanReconcileExactDemand() {
        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = plan(
            List.of(tool("pickaxe", 25, 100, "stone", 5)),
            Map.of("pickaxe", 2)
        );
        List<MiningToolInventoryPlan.Tool<String, ToolSpec>>
            confirmedPlayerTools = List.of(
                tool("pickaxe", 25, 100, "stone", 5),
                tool("pickaxe", 100, 100, "stone", 5)
            );

        int confirmed = plan.compatiblePlayerCount(
            "pickaxe",
            confirmedPlayerTools
        );
        RestockDemand<String> reconciled =
            plan.restockDemand("pickaxe")
                .orElseThrow()
                .reconcile(confirmed);

        assertEquals(2, confirmed);
        assertEquals(3, reconciled.targetCompatiblePlayerCount());
        assertEquals(1, reconciled.remainingAmount());
    }

    @Test
    void preservesItemOrderAndReturnsImmutableViews() {
        LinkedHashMap<String, List<Requirement>> requirements =
            new LinkedHashMap<>();
        requirements.put(
            "shovel",
            List.of(new Requirement("dirt", 1))
        );
        requirements.put(
            "pickaxe",
            List.of(new Requirement("stone", 4))
        );
        LinkedHashMap<String, Integer> missing =
            new LinkedHashMap<>();
        missing.put("shovel", 0);
        missing.put("pickaxe", 1);

        MiningToolInventoryPlan<
            String,
            ToolSpec,
            Requirement
        > plan = MiningToolInventoryPlan.plan(
            requirements,
            List.of(tool("shovel", 30, 100, "dirt", 1)),
            missing,
            0.10,
            MiningToolInventoryPlanTest::isCompatible
        );

        assertEquals(
            List.of("shovel", "pickaxe"),
            plan.restockDemands().keySet().stream().toList()
        );
        assertEquals(
            new RestockDemand<>("shovel", 1, 0),
            plan.restockDemand("shovel").orElseThrow()
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.restockDemands().clear()
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.compatibilityRequirements()
                .get("shovel")
                .clear()
        );
    }

    @Test
    void validatesDemandRequirementsToolsAndDurability() {
        Map<String, List<Requirement>> requirements = Map.of(
            "pickaxe",
            List.of(new Requirement("stone", 4))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolInventoryPlan.plan(
                requirements,
                List.of(),
                Map.of("shovel", 1),
                0.10,
                MiningToolInventoryPlanTest::isCompatible
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolInventoryPlan.plan(
                Map.of("pickaxe", List.of()),
                List.of(),
                Map.of("pickaxe", 1),
                0.10,
                MiningToolInventoryPlanTest::isCompatible
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MiningToolInventoryPlan.plan(
                requirements,
                List.of(),
                Map.of("pickaxe", -1),
                0.10,
                MiningToolInventoryPlanTest::isCompatible
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tool("pickaxe", -1, 100, "stone", 5)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tool("pickaxe", 101, 100, "stone", 5)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tool("pickaxe", 1, 1, "stone", 5)
        );
        assertThrows(
            NullPointerException.class,
            () -> MiningToolInventoryPlan.plan(
                requirements,
                List.of(),
                Map.of("pickaxe", 1),
                0.10,
                null
            )
        );
    }

    private static MiningToolInventoryPlan<
        String,
        ToolSpec,
        Requirement
    > plan(
        List<MiningToolInventoryPlan.Tool<String, ToolSpec>> carried,
        Map<String, Integer> missing
    ) {
        return MiningToolInventoryPlan.plan(
            Map.of(
                "pickaxe",
                List.of(new Requirement("stone", 4))
            ),
            carried,
            missing,
            0.10,
            MiningToolInventoryPlanTest::isCompatible
        );
    }

    private static MiningToolInventoryPlan.Tool<String, ToolSpec> tool(
        String item,
        int remainingDurability,
        int maximumDurability,
        String capability,
        int efficiency
    ) {
        return new MiningToolInventoryPlan.Tool<>(
            item,
            new ToolSpec(Set.of(capability), efficiency),
            remainingDurability,
            maximumDurability
        );
    }

    private static boolean isCompatible(
        ToolSpec tool,
        Requirement requirement
    ) {
        return tool.capabilities().contains(requirement.capability())
            && tool.efficiency() >= requirement.minimumEfficiency();
    }
}
