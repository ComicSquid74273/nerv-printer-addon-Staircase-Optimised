package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildReachTopologyTest {
    private static final String PLAN_HASH = "d".repeat(64);

    @Test
    void persistsIndividualNeighborBlocksWithoutRequiringAWholeLane() {
        CircularBuildReachTopology.Snapshot topology =
            CircularBuildReachTopology.compile(
                PLAN_HASH,
                List.of(
                    route(
                        0,
                        List.of(
                            cell(0, 0),
                            cell(0, 1),
                            cell(0, 2)
                        ),
                        List.of(cell(0, 0))
                    ),
                    route(
                        1,
                        List.of(cell(3, 0)),
                        List.of(
                            cell(3, 0),
                            cell(3, 2),
                            cell(3, 20)
                        )
                    )
                ),
                1.62,
                5.0
            );

        CircularBuildReachTopology.RoutePlan host =
            topology.routePlan(0);
        assertEquals(2, host.reachableForeignTargets().size());
        assertEquals(
            List.of(0, 1),
            host.reachableForeignTargets().stream()
                .map(CircularBuildReachTopology.TargetReach::targetIndex)
                .sorted()
                .toList()
        );
        assertTrue(host.target(1, 2).isEmpty());
    }

    @Test
    void ordersTargetsByTheirPersistedPlacementDeadline() {
        CircularBuildReachTopology.Snapshot topology =
            CircularBuildReachTopology.compile(
                PLAN_HASH,
                List.of(
                    route(
                        0,
                        List.of(
                            cell(0, 0),
                            cell(0, 1),
                            cell(0, 2),
                            cell(0, 3),
                            cell(0, 4)
                        ),
                        List.of(cell(0, 0))
                    ),
                    route(
                        1,
                        List.of(cell(3, 0)),
                        List.of(cell(3, 4), cell(3, 0))
                    )
                ),
                1.62,
                5.0
            );

        assertEquals(
            List.of(1, 0),
            topology.routePlan(0).reachableForeignTargets().stream()
                .map(CircularBuildReachTopology.TargetReach::targetIndex)
                .toList()
        );
    }

    private static CircularBuildReachTopology.Route route(
        int index,
        List<BlockReachWindow.Cell> supports,
        List<BlockReachWindow.Cell> targets
    ) {
        return new CircularBuildReachTopology.Route(
            index,
            supports,
            targets
        );
    }

    private static BlockReachWindow.Cell cell(int x, int z) {
        return new BlockReachWindow.Cell(x, 64, z);
    }
}
