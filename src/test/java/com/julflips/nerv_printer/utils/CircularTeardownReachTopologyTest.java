package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularTeardownReachTopologyTest {
    private static final String PLAN_HASH = "a".repeat(64);

    @Test
    void compilesWholeMapLaneOwnershipBeforeRuntime() {
        CircularTeardownReachTopology.Snapshot topology =
            CircularTeardownReachTopology.compile(
                PLAN_HASH,
                List.of(uRoute(0, 0), uRoute(1, 2), uRoute(2, 4)),
                1.62,
                4.0
            );

        assertEquals(List.of(1), topology.fullMapTraversalRoutes());
        assertEquals(
            List.of(
                new CircularTeardownReachTopology.RouteAssignment(0, 1),
                new CircularTeardownReachTopology.RouteAssignment(2, 1)
            ),
            topology.fullMapRouteAssignments()
        );
        assertTrue(
            topology.relation(0, 1).orElseThrow()
                .preserveStartFullyReachable()
        );
        assertTrue(
            topology.relation(2, 1).orElseThrow()
                .preserveStartFullyReachable()
        );
        assertFalse(
            topology.relation(0, 2).orElseThrow()
                .preserveStartFullyReachable()
        );
    }

    @Test
    void derivesAPlanForAnAuthoritativeRemainingSubset() {
        CircularTeardownReachTopology.Snapshot topology =
            CircularTeardownReachTopology.compile(
                PLAN_HASH,
                List.of(uRoute(0, 0), uRoute(1, 2)),
                1.62,
                2.4
            );

        assertEquals(
            List.of(0, 1, 2),
            topology.monotonicSchedule(
                0,
                List.of(5, 4, 3),
                1
            ).orElseThrow()
        );
        assertTrue(
            topology.monotonicSchedule(
                0,
                List.of(3, 4, 5),
                1
            ).isEmpty()
        );
    }

    @Test
    void compilesAllSixtyFourCircularPairsAsOneCoveredPlan() {
        ArrayList<CircularTeardownReachTopology.Route> routes =
            new ArrayList<>();
        for (int pair = 0;
             pair < CompactCircularNbtPlan.PAIR_COUNT;
             pair++) {
            ArrayList<BlockReachWindow.Cell> targets = new ArrayList<>();
            for (int z = 0;
                 z < CompactCircularNbtPlan.FAR_Z;
                 z++) {
                targets.add(
                    new BlockReachWindow.Cell(pair * 2, 64, z)
                );
            }
            for (int z = CompactCircularNbtPlan.FAR_Z - 1;
                 z >= 0;
                 z--) {
                targets.add(
                    new BlockReachWindow.Cell(pair * 2 + 1, 64, z)
                );
            }
            routes.add(
                new CircularTeardownReachTopology.Route(pair, targets)
            );
        }

        CircularTeardownReachTopology.Snapshot topology =
            CircularTeardownReachTopology.compile(
                PLAN_HASH,
                routes,
                1.62,
                4.8
            );

        HashSet<Integer> covered = new HashSet<>(
            topology.fullMapTraversalRoutes()
        );
        topology.fullMapRouteAssignments().forEach(assignment -> {
            assertTrue(
                topology.fullMapTraversalRoutes().contains(
                    assignment.destinationRouteIndex()
                )
            );
            assertTrue(covered.add(assignment.sourceRouteIndex()));
        });
        assertEquals(
            CompactCircularNbtPlan.PAIR_COUNT,
            covered.size()
        );
        assertEquals(
            CompactCircularNbtPlan.PAIR_COUNT,
            topology.targetCounts().size()
        );
    }

    private static CircularTeardownReachTopology.Route uRoute(
        int routeIndex,
        int outboundX
    ) {
        ArrayList<BlockReachWindow.Cell> targets = new ArrayList<>();
        for (int z = 0; z <= 2; z++) {
            targets.add(new BlockReachWindow.Cell(outboundX, 64, z));
        }
        for (int z = 2; z >= 0; z--) {
            targets.add(
                new BlockReachWindow.Cell(outboundX + 1, 64, z)
            );
        }
        return new CircularTeardownReachTopology.Route(
            routeIndex,
            targets
        );
    }
}
