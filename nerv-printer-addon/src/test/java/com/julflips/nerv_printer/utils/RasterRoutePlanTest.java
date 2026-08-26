package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RasterRoutePlanTest {
    @Test
    void coversReferenceAndVisibleRowsExactlyOnce() {
        ArrayList<RasterRoutePlan.Cell<String>> cells = new ArrayList<>();
        for (int z = -1; z <= 2; z++) {
            for (int x = 0; x < 4; x++) {
                cells.add(new RasterRoutePlan.Cell<>(x, x + z, z, "block", z == -1));
            }
        }

        RasterRoutePlan.Plan<String> plan = RasterRoutePlan.create(cells, 0, 3, -1, 2);

        assertEquals(16, plan.size());
        assertEquals(List.of(0, 1, 2, 3), plan.rows().get(0).cells().stream().map(RasterRoutePlan.Cell::x).toList());
        assertEquals(List.of(3, 2, 1, 0), plan.rows().get(1).cells().stream().map(RasterRoutePlan.Cell::x).toList());
        assertTrue(plan.rows().getFirst().cells().stream().allMatch(RasterRoutePlan.Cell::reference));
        assertEquals(0, plan.indexOf(0, -1).orElseThrow());
        assertEquals(7, plan.indexOf(0, 0).orElseThrow());
    }

    @Test
    void rejectsDuplicateHorizontalCells() {
        List<RasterRoutePlan.Cell<String>> cells = List.of(
            new RasterRoutePlan.Cell<>(0, 1, 0, "a", false),
            new RasterRoutePlan.Cell<>(0, 2, 0, "b", false)
        );
        assertThrows(IllegalArgumentException.class, () ->
            RasterRoutePlan.create(cells, 0, 0, 0, 0));
    }

    @Test
    void coversAFull128By129MapWithoutTraversalConnectors() {
        ArrayList<RasterRoutePlan.Cell<Integer>> cells = new ArrayList<>();
        for (int z = -1; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                cells.add(new RasterRoutePlan.Cell<>(x, 64, z, 1, z == -1));
            }
        }
        RasterRoutePlan.Plan<Integer> plan = RasterRoutePlan.create(
            cells, 0, 127, -1, 127
        );
        assertEquals(128 * 129, plan.size());
        assertEquals(129, plan.rows().size());
        assertEquals(128L, plan.orderedCells().stream()
            .filter(RasterRoutePlan.Cell::reference).count());
        assertEquals(128 * 129L, plan.orderedCells().stream()
            .map(cell -> cell.x() + ":" + cell.z())
            .distinct().count());
    }
}
