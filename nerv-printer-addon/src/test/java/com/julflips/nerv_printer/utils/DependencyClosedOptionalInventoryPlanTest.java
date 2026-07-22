package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyClosedOptionalInventoryPlanTest {
    @Test
    void anchorlessEarlyTargetCannotWasteCapacityNeededByLaterRoot() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                repeat("primary", 64),
                List.of(
                    target("orphan", "orphan-material", false),
                    target("root", "root-material", true)
                ),
                Map.of(
                    "primary", 64,
                    "orphan-material", 64,
                    "root-material", 64
                ),
                2
            );

        assertTrue(plan.primaryFits());
        assertEquals(List.of("root"), plan.plannedOptionalKeys());
        assertEquals(List.of(1), plan.plannedOptionalIndices());
        assertEquals(
            Map.of("root-material", 1),
            plan.optionalDemand()
        );
        assertEquals(0, plan.remainingSlots());
    }

    @Test
    void capacityRejectedPrerequisiteDoesNotUnlockItsDependant() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                repeat("primary", 64),
                List.of(
                    target(
                        "prerequisite",
                        "new-slot",
                        true
                    ),
                    target(
                        "dependant",
                        "primary",
                        false,
                        "prerequisite"
                    )
                ),
                Map.of("primary", 64, "new-slot", 1),
                1
            );

        assertEquals(List.of(), plan.plannedOptionalKeys());
        assertEquals(Map.of(), plan.optionalDemand());
        assertEquals(Map.of("primary", 64), plan.totalDemand());
    }

    @Test
    void laterRootUnlocksEarlierTargetAfterTheRoot() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                List.of(),
                List.of(
                    target(
                        "earlier-dependant",
                        "block",
                        false,
                        "later-root"
                    ),
                    target("later-root", "block", true)
                ),
                Map.of("block", 64),
                1
            );

        assertEquals(
            List.of("later-root", "earlier-dependant"),
            plan.plannedOptionalKeys()
        );
        assertEquals(List.of(1, 0), plan.plannedOptionalIndices());
        assertEquals(Map.of("block", 2), plan.optionalDemand());
    }

    @Test
    void guaranteedPrimaryAnchorCanSeedOptionalExpansion() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                List.of("primary"),
                List.of(
                    target("beside-u", "block", true),
                    target(
                        "outward",
                        "block",
                        false,
                        "beside-u"
                    )
                ),
                Map.of("primary", 64, "block", 64),
                2
            );

        assertEquals(
            List.of("beside-u", "outward"),
            plan.plannedOptionalKeys()
        );
        assertEquals(2, plan.totalSlotsRequired());
    }

    @Test
    void impossiblePrimaryAdmitsNoOptionalTargets() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                repeat("primary", 65),
                List.of(target("root", "optional", true)),
                Map.of("primary", 64, "optional", 64),
                1
            );

        assertFalse(plan.primaryFits());
        assertEquals(1, plan.primarySlotDeficit());
        assertEquals(List.of(), plan.plannedOptionalKeys());
        assertEquals(Map.of(), plan.optionalDemand());
    }

    @Test
    void laterSameMaterialUsesAnAlreadyAllocatedPartialStack() {
        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                repeat("primary", 64),
                List.of(
                    target("sign-1", "sign", true),
                    target("unfit", "single", true),
                    target("sign-2", "sign", true)
                ),
                Map.of(
                    "primary", 64,
                    "sign", 16,
                    "single", 1
                ),
                2
            );

        assertEquals(
            List.of("sign-1", "sign-2"),
            plan.plannedOptionalKeys()
        );
        assertEquals(List.of(0, 2), plan.plannedOptionalIndices());
        assertEquals(Map.of("sign", 2), plan.optionalDemand());
    }

    @Test
    void fillsAllManagedCapacityAcrossForwardSurfacesAndConnectors() {
        ArrayList<
            DependencyClosedOptionalInventoryPlan.Target<String, String>
        > optional = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            optional.add(
                target("next-outbound-" + index, "black", true)
            );
        }
        for (int index = 0; index < 64; index++) {
            optional.add(
                target("next-connector-" + index, "cobblestone", true)
            );
        }
        for (int index = 0; index < 64; index++) {
            optional.add(
                target("next-return-" + index, "white", true)
            );
        }

        DependencyClosedOptionalInventoryPlan.Result<String, String> plan =
            DependencyClosedOptionalInventoryPlan.plan(
                repeat("active-u", 64),
                optional,
                Map.of(
                    "active-u", 64,
                    "black", 64,
                    "cobblestone", 64,
                    "white", 64
                ),
                4
            );

        assertTrue(plan.primaryFits());
        assertEquals(192, plan.plannedOptionalKeys().size());
        assertEquals(
            Map.of(
                "black", 64,
                "cobblestone", 64,
                "white", 64
            ),
            plan.optionalDemand()
        );
        assertEquals(4, plan.totalSlotsRequired());
        assertEquals(0, plan.remainingSlots());
    }

    @Test
    void validatesKeysDependenciesMaterialsAndSlots() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DependencyClosedOptionalInventoryPlan.plan(
                List.of(),
                List.of(
                    target("duplicate", "block", true),
                    target("duplicate", "block", true)
                ),
                Map.of("block", 64),
                1
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DependencyClosedOptionalInventoryPlan.plan(
                List.of(),
                List.of(
                    target(
                        "dependant",
                        "block",
                        false,
                        "missing"
                    )
                ),
                Map.of("block", 64),
                1
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DependencyClosedOptionalInventoryPlan.plan(
                List.of(),
                List.of(target("root", "missing-size", true)),
                Map.of(),
                1
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DependencyClosedOptionalInventoryPlan.plan(
                List.of(),
                List.of(),
                Map.of(),
                -1
            )
        );
    }

    private static DependencyClosedOptionalInventoryPlan.Target<
        String,
        String
    > target(
        String key,
        String material,
        boolean initiallyAnchored,
        String... optionalAnchors
    ) {
        return new DependencyClosedOptionalInventoryPlan.Target<>(
            key,
            material,
            initiallyAnchored,
            Set.of(optionalAnchors)
        );
    }

    private static List<String> repeat(
        String material,
        int count
    ) {
        ArrayList<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(material);
        }
        return result;
    }
}
