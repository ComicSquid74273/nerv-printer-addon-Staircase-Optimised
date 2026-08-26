package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRodLightingPlanTest {
    @Test
    void flatOneByOneMapUsesAnElevenBlockGrid() {
        int[][] surface = new int[128][128];

        EndRodLightingPlan.Result result =
            EndRodLightingPlan.generate(surface);

        assertEquals(144, result.rods().size());
        assertTrue(result.minimumGuaranteedLight() >= 1);
        assertEquals(
            result.rods().size(),
            new HashSet<>(result.rods()).size()
        );
        assertTrue(result.rods().stream().allMatch(rod -> rod.y() == 3));
    }

    @Test
    void addsOnlyTheExtraRodsNeededAcrossSevereColumnCliffs() {
        int[][] surface = new int[32][32];
        for (int x = 0; x < surface.length; x++) {
            int height = (x & 1) == 0 ? -80 : 80;
            for (int z = 0; z < surface[x].length; z++) {
                surface[x][z] = height + Math.min(z, 12);
            }
        }

        EndRodLightingPlan.Result result =
            EndRodLightingPlan.generate(surface);

        assertTrue(result.rods().size() > 9);
        assertTrue(result.minimumGuaranteedLight() >= 1);
        for (EndRodLightingPlan.Rod rod : result.rods()) {
            assertEquals(
                surface[rod.x()][rod.z()] + 3,
                rod.y()
            );
        }
    }

    @Test
    void everyCellHasAConservativeNonZeroLightPath() {
        int[][] surface = new int[48][67];
        for (int x = 0; x < surface.length; x++) {
            for (int z = 0; z < surface[x].length; z++) {
                surface[x][z] = (x % 7) * 9
                    + (z % 19 <= 9 ? z % 19 : 18 - z % 19);
            }
        }

        EndRodLightingPlan.Result result =
            EndRodLightingPlan.generate(surface);

        for (int x = 0; x < surface.length; x++) {
            for (int z = 0; z < surface[x].length; z++) {
                assertTrue(
                    EndRodLightingPlan.guaranteedLightAt(
                        surface,
                        x,
                        z,
                        result.rods()
                    ) >= 1,
                    "dark surface cell at " + x + "," + z
                );
            }
        }
    }
}
