package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactCircularNbtPlanTest {
    @Test
    void flatMapUsesDirectPairEdgesAndAddsNoConnectorBlocks() {
        CompactCircularNbtPlan.Result result = CompactCircularNbtPlan.generate(
            sourceWithProfiles(new int[CompactCircularNbtPlan.MAP_WIDTH][CompactCircularNbtPlan.SOURCE_Z_SIZE]),
            List.of("minecraft:stone")
        );

        assertEquals(CompactCircularNbtPlan.PAIR_COUNT, result.pairRoutes().size());
        assertEquals(0, result.connectorBlocks().size());
        assertEquals(CompactCircularNbtPlan.SOURCE_Z_SIZE, result.sizeZ());
        assertEquals(1, result.sizeY());
        assertEquals(1, result.cobblestoneState());
        assertTrue(result.pairRoutes().stream().allMatch(route -> route.minimumEdges() == 1));
        assertTrue(result.pairRoutes().stream().allMatch(route -> route.routeFamily().equals("simple_u")));
    }

    @Test
    void twentyTwoBlockEndpointDifferenceUsesExactMinimumCircularRoute() {
        int[][] heights = new int[CompactCircularNbtPlan.MAP_WIDTH][CompactCircularNbtPlan.SOURCE_Z_SIZE];
        for (int z = 1; z <= 22; z++) heights[1][z] = z;
        for (int z = 23; z < CompactCircularNbtPlan.SOURCE_Z_SIZE; z++) heights[1][z] = 22;

        CompactCircularNbtPlan.Result result = CompactCircularNbtPlan.generate(
            sourceWithProfiles(heights),
            List.of("minecraft:stone", CompactCircularNbtPlan.COBBLESTONE_NAME)
        );
        CompactCircularNbtPlan.PairRoute route = result.pairRoutes().getFirst();

        assertEquals(22, route.heightDifference());
        assertEquals(23, route.minimumEdges());
        assertEquals("7_plus_4k", route.routeFamily());
        assertEquals(4, route.fullCircularTurns());
        assertEquals(3, route.maximumExtension());
        assertEquals(24, route.relativePath().size());
        assertEquals(new CompactCircularNbtPlan.Position(0, 0, 128), route.relativePath().getFirst());
        assertEquals(new CompactCircularNbtPlan.Position(1, 22, 128), route.relativePath().getLast());
        assertEquals(new CompactCircularNbtPlan.Position(0, 1, 129), route.relativePath().get(1));
        assertEquals(new CompactCircularNbtPlan.Position(1, 22, 129), route.relativePath().get(22));
        assertEquals(22, route.relativeInterior().size());
    }

    @Test
    void bothCircularFamiliesAndNegativeHeightChangesAreDeterministic() {
        int[][] heights = new int[CompactCircularNbtPlan.MAP_WIDTH][CompactCircularNbtPlan.SOURCE_Z_SIZE];
        applyRamp(heights, 1, 6);
        applyRamp(heights, 3, 8);
        applyRamp(heights, 5, 17);
        applyRamp(heights, 7, -10);

        CompactCircularNbtPlan.Result result = CompactCircularNbtPlan.generate(
            sourceWithProfiles(heights),
            List.of("minecraft:stone", CompactCircularNbtPlan.COBBLESTONE_NAME)
        );

        assertRoute(result.pairRoutes().get(0), 6, 7, "7_plus_4k", 0);
        assertRoute(result.pairRoutes().get(1), 8, 9, "5_plus_4k", 1);
        assertRoute(result.pairRoutes().get(2), 17, 17, "5_plus_4k", 3);
        assertRoute(result.pairRoutes().get(3), -10, 11, "7_plus_4k", 1);

        CompactCircularNbtPlan.PairRoute evenDifference = result.pairRoutes().get(1);
        assertEquals(
            evenDifference.relativePath().get(evenDifference.relativePath().size() - 2).y(),
            evenDifference.relativePath().getLast().y()
        );
        assertTrue(result.pairRoutes().stream().allMatch(route -> route.maximumExtension() <= 3));
    }

    @Test
    void completeVerticalStackMovesWithoutChangingItsDepthOrState() {
        int[][] heights = new int[CompactCircularNbtPlan.MAP_WIDTH][CompactCircularNbtPlan.SOURCE_Z_SIZE];
        heights[0][1] = 10;
        List<CompactCircularNbtPlan.SourceBlock> blocks = sourceWithProfiles(heights);
        blocks.add(new CompactCircularNbtPlan.SourceBlock(
            blocks.size(),
            new CompactCircularNbtPlan.Position(0, 7, 1),
            1
        ));

        CompactCircularNbtPlan.Result result = CompactCircularNbtPlan.generate(
            blocks,
            List.of("minecraft:stone", "minecraft:glass", CompactCircularNbtPlan.COBBLESTONE_NAME)
        );
        CompactCircularNbtPlan.GeneratedBlock top = result.generatedBlocks().stream()
            .filter(block -> block.sourceIndex() == 1)
            .findFirst()
            .orElseThrow();
        CompactCircularNbtPlan.GeneratedBlock support = result.generatedBlocks().stream()
            .filter(block -> block.sourceIndex() == blocks.size() - 1)
            .findFirst()
            .orElseThrow();

        assertEquals(3, top.position().y() - support.position().y());
        assertEquals(1, support.state());
    }

    @Test
    void invalidOrIncompleteSourcesFailClosed() {
        int[][] heights = new int[CompactCircularNbtPlan.MAP_WIDTH][CompactCircularNbtPlan.SOURCE_Z_SIZE];
        List<CompactCircularNbtPlan.SourceBlock> missing = sourceWithProfiles(heights);
        missing.removeLast();
        for (int i = 0; i < missing.size(); i++) {
            CompactCircularNbtPlan.SourceBlock old = missing.get(i);
            missing.set(i, new CompactCircularNbtPlan.SourceBlock(i, old.position(), old.state()));
        }

        IllegalArgumentException missingError = assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtPlan.generate(missing, List.of("minecraft:stone"))
        );
        assertTrue(missingError.getMessage().contains("0..128")
            || missingError.getMessage().contains("empty X/Z shaft"));

        List<CompactCircularNbtPlan.SourceBlock> duplicate = sourceWithProfiles(heights);
        CompactCircularNbtPlan.SourceBlock first = duplicate.getFirst();
        duplicate.add(new CompactCircularNbtPlan.SourceBlock(duplicate.size(), first.position(), first.state()));
        IllegalArgumentException duplicateError = assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtPlan.generate(duplicate, List.of("minecraft:stone"))
        );
        assertTrue(duplicateError.getMessage().contains("Duplicate block position"));
    }

    private static List<CompactCircularNbtPlan.SourceBlock> sourceWithProfiles(int[][] heights) {
        List<CompactCircularNbtPlan.SourceBlock> blocks = new ArrayList<>();
        for (int x = 0; x < CompactCircularNbtPlan.MAP_WIDTH; x++) {
            for (int z = 0; z < CompactCircularNbtPlan.SOURCE_Z_SIZE; z++) {
                blocks.add(new CompactCircularNbtPlan.SourceBlock(
                    blocks.size(),
                    new CompactCircularNbtPlan.Position(x, heights[x][z], z),
                    0
                ));
            }
        }
        assertFalse(blocks.isEmpty());
        return blocks;
    }

    private static void applyRamp(int[][] heights, int x, int difference) {
        int direction = Integer.signum(difference);
        int magnitude = Math.abs(difference);
        for (int z = 1; z < CompactCircularNbtPlan.SOURCE_Z_SIZE; z++) {
            heights[x][z] = direction * Math.min(z, magnitude);
        }
    }

    private static void assertRoute(
        CompactCircularNbtPlan.PairRoute route,
        int difference,
        int edges,
        String family,
        int turns
    ) {
        assertEquals(difference, route.heightDifference());
        assertEquals(edges, route.minimumEdges());
        assertEquals(family, route.routeFamily());
        assertEquals(turns, route.fullCircularTurns());
        assertEquals(edges + 1, route.relativePath().size());
    }
}
