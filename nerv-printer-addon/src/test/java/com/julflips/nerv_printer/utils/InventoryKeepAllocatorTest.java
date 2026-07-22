package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryKeepAllocatorTest {
    @Test
    void equalCountToolsKeepHighestRemainingDurability() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("pickaxe", 1),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(
                        2,
                        "pickaxe",
                        1,
                        10
                    ),
                    new InventoryKeepAllocator.StackEntry<>(
                        7,
                        "pickaxe",
                        1,
                        200
                    )
                )
            );

        assertEquals(List.of(7), allocation.keptSlots());
        assertEquals(List.of(2), allocation.dumpSlots());
    }

    @Test
    void fragmentedStacksCanSatisfyDemandInAggregate() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 64),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(1, "block", 32)
                )
            );

        assertEquals(List.of(0, 1), allocation.keptSlots());
        assertEquals(List.of(), allocation.dumpSlots());
        assertEquals(Map.of("block", 64), allocation.keptCounts());
        assertEquals(Map.of("block", 0), allocation.missingDemand());
    }

    @Test
    void largestStacksAreKeptFirstAndExcessStacksAreDumped() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 40),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 20),
                    new InventoryKeepAllocator.StackEntry<>(1, "block", 64),
                    new InventoryKeepAllocator.StackEntry<>(2, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(3, "junk", 64)
                )
            );

        assertEquals(List.of(1), allocation.keptSlots());
        assertEquals(List.of(0, 2, 3), allocation.dumpSlots());
        assertEquals(Map.of("block", 64), allocation.keptCounts());
        assertEquals(Map.of("block", 0), allocation.missingDemand());
    }

    @Test
    void equalStacksUseLowestSlotAsDeterministicTieBreaker() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 32),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(8, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(2, "block", 32)
                )
            );

        assertEquals(List.of(2), allocation.keptSlots());
        assertEquals(List.of(8), allocation.dumpSlots());
    }

    @Test
    void reportsKeptCountsAndMissingDemandPerMaterial() {
        LinkedHashMap<String, Integer> demand = new LinkedHashMap<>();
        demand.put("block", 64);
        demand.put("sign", 20);
        demand.put("missing", 5);

        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                demand,
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(5, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(1, "sign", 16),
                    new InventoryKeepAllocator.StackEntry<>(4, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(3, "junk", 10)
                )
            );

        assertEquals(List.of(1, 4, 5), allocation.keptSlots());
        assertEquals(List.of(3), allocation.dumpSlots());
        assertEquals(
            Map.of("block", 64, "sign", 16),
            allocation.keptCounts()
        );
        assertEquals(
            Map.of("block", 0, "sign", 4, "missing", 5),
            allocation.missingDemand()
        );
    }

    @Test
    void zeroAndNonRequiredMaterialsAreDumped() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 0),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 64),
                    new InventoryKeepAllocator.StackEntry<>(1, "junk", 1)
                )
            );

        assertEquals(List.of(), allocation.keptSlots());
        assertEquals(List.of(0, 1), allocation.dumpSlots());
        assertEquals(Map.of(), allocation.keptCounts());
        assertEquals(Map.of("block", 0), allocation.missingDemand());
    }

    @Test
    void validatesDemandAndInventorySlots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryKeepAllocator.allocate(
                Map.of("block", -1),
                List.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryKeepAllocator.allocate(
                Map.of("block", 1),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 1),
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 1)
                )
            )
        );
    }

    @Test
    void restockAllocationReplacesCostlyFragmentsWithOneFreshStack() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 64),
                Map.of("block", 64),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 32),
                    new InventoryKeepAllocator.StackEntry<>(1, "block", 32)
                )
            );

        assertEquals(List.of(), allocation.keptSlots());
        assertEquals(List.of(0, 1), allocation.dumpSlots());
        assertEquals(Map.of(), allocation.keptCounts());
        assertEquals(Map.of("block", 64), allocation.missingDemand());
    }

    @Test
    void restockAllocationRetainsMostItemsWhenSlotCostTies() {
        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                Map.of("block", 96),
                Map.of("block", 64),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(0, "block", 24),
                    new InventoryKeepAllocator.StackEntry<>(1, "block", 40)
                )
            );

        assertEquals(List.of(1), allocation.keptSlots());
        assertEquals(List.of(0), allocation.dumpSlots());
        assertEquals(Map.of("block", 40), allocation.keptCounts());
        assertEquals(Map.of("block", 56), allocation.missingDemand());
    }

    @Test
    void restockAllocationUsesQualityThenSlotAsTieBreakers() {
        InventoryKeepAllocator.Allocation<String> higherQuality =
            InventoryKeepAllocator.allocate(
                Map.of("tool", 2),
                Map.of("tool", 1),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(
                        2,
                        "tool",
                        1,
                        10
                    ),
                    new InventoryKeepAllocator.StackEntry<>(
                        7,
                        "tool",
                        1,
                        200
                    ),
                    new InventoryKeepAllocator.StackEntry<>(
                        8,
                        "tool",
                        1,
                        100
                    )
                )
            );
        InventoryKeepAllocator.Allocation<String> lowerSlot =
            InventoryKeepAllocator.allocate(
                Map.of("block", 96),
                Map.of("block", 64),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(
                        8,
                        "block",
                        40,
                        5
                    ),
                    new InventoryKeepAllocator.StackEntry<>(
                        2,
                        "block",
                        40,
                        5
                    )
                )
            );

        assertEquals(List.of(7, 8), higherQuality.keptSlots());
        assertEquals(List.of(2), higherQuality.dumpSlots());
        assertEquals(List.of(2), lowerSlot.keptSlots());
        assertEquals(List.of(8), lowerSlot.dumpSlots());
    }

    @Test
    void restockAllocationUsesPerMaterialFreshStackSizes() {
        LinkedHashMap<String, Integer> demand = new LinkedHashMap<>();
        demand.put("block", 96);
        demand.put("tool", 2);

        InventoryKeepAllocator.Allocation<String> allocation =
            InventoryKeepAllocator.allocate(
                demand,
                Map.of("block", 64, "tool", 1),
                List.of(
                    new InventoryKeepAllocator.StackEntry<>(4, "block", 40),
                    new InventoryKeepAllocator.StackEntry<>(
                        1,
                        "tool",
                        1,
                        100
                    ),
                    new InventoryKeepAllocator.StackEntry<>(9, "junk", 64)
                )
            );

        assertEquals(List.of(1, 4), allocation.keptSlots());
        assertEquals(List.of(9), allocation.dumpSlots());
        assertEquals(Map.of("block", 40, "tool", 1), allocation.keptCounts());
        assertEquals(Map.of("block", 56, "tool", 1), allocation.missingDemand());
    }

    @Test
    void restockAllocationValidatesFreshStackSizes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryKeepAllocator.allocate(
                Map.of("block", 1),
                Map.of(),
                List.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> InventoryKeepAllocator.allocate(
                Map.of("block", 1),
                Map.of("block", 0),
                List.of()
            )
        );
    }
}
