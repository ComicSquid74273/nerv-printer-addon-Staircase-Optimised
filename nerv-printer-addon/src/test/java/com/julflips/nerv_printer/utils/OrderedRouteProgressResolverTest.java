package com.julflips.nerv_printer.utils;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedRouteProgressResolverTest {
    @Test
    void followsAnArbitraryMixedTerrainProfileWithoutSlopeCases() {
        int[] heights = {10, 9, 8, 8, 9, 10, 9, 9, 10, 9};
        ArrayList<BlockPos> route = new ArrayList<>();
        for (int z = 0; z < heights.length; z++) {
            route.add(new BlockPos(0, heights[z], z));
        }

        int cursor = 0;
        for (int index = 1; index < route.size(); index++) {
            BlockPos target = route.get(index);
            cursor = OrderedRouteProgressResolver.resolve(
                route,
                cursor,
                target.getX() + 0.5,
                target.getZ() + 0.5
            ).orElseThrow();
            assertEquals(index, cursor);
        }
    }

    @Test
    void progressDoesNotRequireGroundedLandingBetweenDescents() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 9, 1),
            new BlockPos(0, 8, 2),
            new BlockPos(0, 7, 3)
        );

        int cursor = 0;
        for (int index = 1; index < route.size(); index++) {
            cursor = OrderedRouteProgressResolver.resolve(
                route,
                cursor,
                0.5,
                index + 0.5
            ).orElseThrow();
        }
        assertEquals(route.size() - 1, cursor);
    }

    @Test
    void recoveryMovesMonotonicallyBackwardAcrossMixedTerrain() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 9, 1),
            new BlockPos(0, 10, 2),
            new BlockPos(0, 9, 3)
        );

        int cursor = route.size() - 1;
        for (int index = route.size() - 2; index >= 0; index--) {
            BlockPos target = route.get(index);
            cursor = OrderedRouteProgressResolver.resolve(
                route,
                cursor,
                -1,
                target.getX() + 0.5,
                target.getZ() + 0.5
            ).orElseThrow();
            assertEquals(index, cursor);
        }
    }

    @Test
    void backwardRecoveryRejectsForwardReentryAndSkippedCells() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(0, 10, 2),
            new BlockPos(0, 10, 3)
        );

        assertTrue(
            OrderedRouteProgressResolver.resolve(
                route,
                2,
                -1,
                0.5,
                3.5
            ).isEmpty()
        );
        assertTrue(
            OrderedRouteProgressResolver.resolve(
                route,
                2,
                -1,
                0.5,
                0.5
            ).isEmpty()
        );
    }

    @Test
    void rejectsSidewaysDepartureAndSkippedCells() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 9, 1),
            new BlockPos(0, 8, 2)
        );

        assertTrue(
            OrderedRouteProgressResolver.resolve(
                route,
                0,
                1.5,
                1.5
            ).isEmpty()
        );
        assertTrue(
            OrderedRouteProgressResolver.resolve(
                route,
                0,
                0.5,
                2.5
            ).isEmpty()
        );
    }

    @Test
    void nearbyReturnLegAndLaterHelixLevelCannotStealTheCursor() {
        List<BlockPos> route = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 10, 1),
            new BlockPos(1, 11, 1),
            new BlockPos(1, 11, 0),
            new BlockPos(0, 12, 0)
        );

        assertEquals(
            0,
            OrderedRouteProgressResolver.resolve(
                route,
                0,
                0.5,
                0.5
            ).orElseThrow()
        );
        assertTrue(
            OrderedRouteProgressResolver.resolve(
                route,
                0,
                1.5,
                0.5
            ).isEmpty()
        );
    }

    @Test
    void rejectsNonWalkableRouteGeometryAndInvalidInputs() {
        List<BlockPos> gap = List.of(
            new BlockPos(0, 10, 0),
            new BlockPos(0, 7, 1)
        );
        assertTrue(
            OrderedRouteProgressResolver.resolve(
                gap,
                0,
                0.5,
                1.5
            ).isEmpty()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> OrderedRouteProgressResolver.resolve(
                gap,
                -1,
                0.5,
                0.5
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> OrderedRouteProgressResolver.resolve(
                List.of(),
                0,
                0.5,
                0.5
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> OrderedRouteProgressResolver.resolve(
                gap,
                0,
                0,
                0.5,
                0.5
            )
        );
    }
}
