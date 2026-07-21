package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousTeardownRoutePlanTest {
    @Test
    void fullUUsesInternalStagesAndOneStructuralExit() {
        List<String> targets = List.of("u0", "u1", "u2", "u3");
        CircularMiningTraversalPlan.Plan traversal =
            CircularMiningTraversalPlan.create(
                targets.size(),
                CircularMiningRecoveryPlan.analyze(
                    List.of(
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE
                    )
                )
            );

        var plan = ContinuousTeardownRoutePlan.create(
            targets,
            traversal.steps(),
            Map.of(),
            0,
            List.of("alignment", "walkway"),
            "exit",
            targets.get(traversal.finalRemoveIndex())
        );

        assertEquals(
            List.of(
                "alignment", "walkway", "u0", "u1", "u2", "u3", "exit"
            ),
            plan.stages().stream()
                .map(ContinuousTeardownRoutePlan.Stage::support)
                .toList()
        );
        assertEquals(
            List.of(
                List.of(),
                List.of(),
                List.of(),
                List.of("u0"),
                List.of("u1"),
                List.of("u2"),
                List.of("u3")
            ),
            plan.stages().stream()
                .map(ContinuousTeardownRoutePlan.Stage::breakTargets)
                .toList()
        );
    }

    @Test
    void remoteTargetsAtOneSupportDoNotCreateRepeatedMovementStages() {
        List<String> targets = List.of("u0", "u1", "u2");
        CircularMiningTraversalPlan.Plan traversal =
            CircularMiningTraversalPlan.create(
                targets.size(),
                CircularMiningRecoveryPlan.analyze(
                    List.of(
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE
                    )
                )
            );

        var plan = ContinuousTeardownRoutePlan.create(
            targets,
            traversal.steps(),
            Map.of(1, List.of("remote0", "remote1")),
            0,
            List.of("alignment", "walkway"),
            "exit",
            targets.get(traversal.finalRemoveIndex())
        );

        assertEquals(6, plan.stages().size());
        assertEquals(
            List.of("u0", "remote0", "remote1"),
            plan.stages().get(3).breakTargets()
        );
    }

    @Test
    void recoveryTurnaroundRemainsInternalToOneEntryExitRoute() {
        List<String> targets =
            List.of("u0", "u1", "u2", "u3", "u4");
        CircularMiningTraversalPlan.Plan traversal =
            CircularMiningTraversalPlan.create(
                targets.size(),
                CircularMiningRecoveryPlan.analyze(
                    List.of(
                        CircularMiningRecoveryPlan.Cell.AIR,
                        CircularMiningRecoveryPlan.Cell.AIR,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE
                    )
                )
            );

        var plan = ContinuousTeardownRoutePlan.create(
            targets,
            traversal.steps(),
            Map.of(),
            traversal.steps().get(2).standIndex(),
            List.of("u4"),
            "exit",
            targets.get(traversal.finalRemoveIndex())
        );

        assertEquals(
            List.of("u4", "u3", "u2", "u3", "u4", "exit"),
            plan.stages().stream()
                .map(ContinuousTeardownRoutePlan.Stage::support)
                .toList()
        );
    }

    @Test
    void localResumeDefersRemoteWorkUntilTheForwardPass() {
        List<String> targets = List.of("u0", "u1", "u2", "u3");
        CircularMiningLocalResumePlan.Plan traversal =
            CircularMiningLocalResumePlan.create(
                targets.size(),
                CircularMiningRecoveryPlan.analyze(
                    List.of(
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE,
                        CircularMiningRecoveryPlan.Cell.WALKABLE
                    )
                ),
                2
            ).orElseThrow();

        var plan = ContinuousTeardownRoutePlan.create(
            targets,
            traversal.steps(),
            Map.of(2, List.of("remote2")),
            0,
            List.of("u2"),
            "exit",
            targets.get(traversal.finalRemoveIndex())
        );

        assertEquals(
            List.of(),
            plan.stages().getFirst().breakTargets()
        );
        assertEquals(
            List.of("u1", "remote2"),
            plan.stages().get(4).breakTargets()
        );
    }

    @Test
    void rejectsRemovingTheCurrentSupportOrSchedulingOneTargetTwice() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousTeardownRoutePlan.Stage<>(
                "support",
                List.of("support")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ContinuousTeardownRoutePlan.create(
                List.of("u0", "u1"),
                List.of(
                    new CircularMiningTraversalPlan.Step(0, -1),
                    new CircularMiningTraversalPlan.Step(1, 0)
                ),
                Map.of(1, List.of("u0")),
                0,
                List.of("alignment", "walkway"),
                "exit",
                "u1"
            )
        );
    }

    @Test
    void rejectsWorkThatRemovesASupportNeededLaterInTheRoute() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ContinuousTeardownRoutePlan.create(
                List.of("u0", "u1", "u2"),
                List.of(
                    new CircularMiningTraversalPlan.Step(0, -1),
                    new CircularMiningTraversalPlan.Step(1, -1),
                    new CircularMiningTraversalPlan.Step(2, 1)
                ),
                Map.of(0, List.of("u2")),
                0,
                List.of("alignment", "walkway"),
                "exit",
                "u0"
            )
        );
    }

    @Test
    void runtimeProtectionTracksCurrentAndFutureSupportsOnly() {
        List<ContinuousTeardownRoutePlan.Stage<String>> stages =
            List.of(
                new ContinuousTeardownRoutePlan.Stage<>(
                    "u0",
                    List.of()
                ),
                new ContinuousTeardownRoutePlan.Stage<>(
                    "u1",
                    List.of("u0")
                ),
                new ContinuousTeardownRoutePlan.Stage<>(
                    "u2",
                    List.of("u1")
                )
            );

        assertEquals(
            1,
            ContinuousTeardownRoutePlan
                .requiredSupportIndexAtOrAfter(
                    stages,
                    1,
                    "u1"
                ).orElseThrow()
        );
        assertEquals(
            2,
            ContinuousTeardownRoutePlan
                .requiredSupportIndexAtOrAfter(
                    stages,
                    1,
                    "u2"
                ).orElseThrow()
        );
        assertTrue(
            ContinuousTeardownRoutePlan
                .requiredSupportIndexAtOrAfter(
                    stages,
                    1,
                    "u0"
                ).isEmpty()
        );
    }
}
