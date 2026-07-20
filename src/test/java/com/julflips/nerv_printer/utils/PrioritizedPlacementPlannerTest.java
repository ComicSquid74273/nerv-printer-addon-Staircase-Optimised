package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrioritizedPlacementPlannerTest {
    @Test
    void primaryTargetsAlwaysConsumeTheBudgetBeforeOptionalTargets() {
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(
                    target("primary-1", "stone"),
                    target("primary-2", "dirt")
                ),
                List.of(target("optional", "stone")),
                2,
                Map.of("stone", 2, "dirt", 1),
                Map.of("stone", 1, "dirt", 1),
                ignored -> true,
                ignored -> false
            );

        assertEquals(
            List.of(
                decision(
                    "primary-1",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                ),
                decision(
                    "primary-2",
                    "dirt",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                )
            ),
            plan.decisions()
        );
    }

    @Test
    void optionalTargetsCanSpendOnlyStrictSurplusAbovePrimaryReserve() {
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(target("pending-primary", "stone")),
                List.of(
                    target("optional-1", "stone"),
                    target("optional-2", "stone")
                ),
                3,
                Map.of("stone", 2),
                Map.of("stone", 1),
                ignored -> true,
                key -> key.equals("pending-primary")
            );

        assertEquals(
            List.of(
                decision(
                    "optional-1",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.OPTIONAL
                )
            ),
            plan.decisions()
        );
        assertEquals(1, plan.remainingOnHand().get("stone"));
        assertEquals(1, plan.remainingPrimaryReserve().get("stone"));
    }

    @Test
    void primaryConsumptionAlsoConsumesItsReservation() {
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(target("primary", "stone")),
                List.of(target("optional", "stone")),
                2,
                Map.of("stone", 2),
                Map.of("stone", 1),
                ignored -> true,
                ignored -> false
            );

        assertEquals(
            List.of(
                decision(
                    "primary",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                ),
                decision(
                    "optional",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.OPTIONAL
                )
            ),
            plan.decisions()
        );
        assertEquals(0, plan.remainingOnHand().get("stone"));
        assertEquals(0, plan.remainingPrimaryReserve().get("stone"));
    }

    @Test
    void decisionsAreDistinctAndPrimaryKeysCannotBeDowngraded() {
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(
                    target("same", "stone"),
                    target("same", "stone")
                ),
                List.of(
                    target("same", "stone"),
                    target("other", "stone")
                ),
                4,
                Map.of("stone", 4),
                Map.of("stone", 2),
                ignored -> true,
                ignored -> false
            );

        assertEquals(
            List.of(
                decision(
                    "same",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                ),
                decision(
                    "other",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.OPTIONAL
                )
            ),
            plan.decisions()
        );
    }

    @Test
    void pendingIneligibleAndMaterialExhaustedTargetsAreExcluded() {
        Set<String> pending = Set.of("pending");
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(
                    target("pending", "stone"),
                    target("ineligible", "stone"),
                    target("exhausted", "dirt"),
                    target("selected", "stone")
                ),
                List.of(target("optional-exhausted", "dirt")),
                5,
                Map.of("stone", 1, "dirt", 0),
                Map.of("stone", 1, "dirt", 0),
                target -> !target.key().equals("ineligible"),
                pending::contains
            );

        assertEquals(
            List.of(
                decision(
                    "selected",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                )
            ),
            plan.decisions()
        );
    }

    @Test
    void laterAnchoredPrimaryTargetsAreSelectedPastAnUnanchoredTarget() {
        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(
                    target("unanchored-first", "stone"),
                    target("forward-left", "stone"),
                    target("forward-right", "stone")
                ),
                List.of(target("nearby-optional", "stone")),
                2,
                Map.of("stone", 4),
                Map.of("stone", 3),
                target -> !target.key().equals("unanchored-first"),
                ignored -> false
            );

        assertEquals(
            List.of(
                decision(
                    "forward-left",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                ),
                decision(
                    "forward-right",
                    "stone",
                    PrioritizedPlacementPlanner.Tier.PRIMARY
                )
            ),
            plan.decisions()
        );
    }

    @Test
    void selectableProbeExcludesPendingAndUnavailableTargets() {
        List<PrioritizedPlacementPlanner.Target<String, String>> targets =
            List.of(
                target("pending", "stone"),
                target("unavailable", "dirt")
            );

        assertEquals(
            false,
            PrioritizedPlacementPlanner.hasSelectableTarget(
                targets,
                Map.of("stone", 1, "dirt", 0),
                ignored -> true,
                key -> key.equals("pending")
            )
        );
        assertEquals(
            true,
            PrioritizedPlacementPlanner.hasSelectableTarget(
                targets,
                Map.of("stone", 1, "dirt", 1),
                target -> target.key().equals("unavailable"),
                ignored -> false
            )
        );
    }

    @Test
    void validatesBudgetAndCountsAndReturnsImmutableResults() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PrioritizedPlacementPlanner.plan(
                List.of(),
                List.of(),
                -1,
                Map.of(),
                Map.of(),
                ignored -> true,
                ignored -> false
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrioritizedPlacementPlanner.plan(
                List.of(),
                List.of(),
                1,
                Map.of("stone", -1),
                Map.of(),
                ignored -> true,
                ignored -> false
            )
        );

        PrioritizedPlacementPlanner.Plan<String, String> plan =
            PrioritizedPlacementPlanner.plan(
                List.of(target("primary", "stone")),
                List.of(),
                1,
                Map.of("stone", 1),
                Map.of("stone", 1),
                ignored -> true,
                ignored -> false
            );
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.decisions().clear()
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.remainingOnHand().put("stone", 2)
        );
    }

    private static PrioritizedPlacementPlanner.Target<String, String> target(
        String key,
        String material
    ) {
        return new PrioritizedPlacementPlanner.Target<>(key, material);
    }

    private static PrioritizedPlacementPlanner.Decision<String, String> decision(
        String key,
        String material,
        PrioritizedPlacementPlanner.Tier tier
    ) {
        return new PrioritizedPlacementPlanner.Decision<>(
            key,
            material,
            tier
        );
    }
}
