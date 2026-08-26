package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenteredNorthWalkwayLocatorTest {
    @Test
    void derivesOddFiveMapCornerFromTheCenteredMapTile() {
        Set<Point> safe = row(-960, 76, 2495);

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                5 * 128,
                -951,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertTrue(resolution.resolved());
        assertEquals(-1216, resolution.anchor().mapCornerX());
        assertEquals(76, resolution.anchor().walkwayY());
        assertEquals(2496, resolution.anchor().mapCornerZ());
        assertEquals(-960, resolution.anchor().centerStartX());
    }

    @Test
    void derivesEvenTwoMapCornerFromASeamCenteredAnchor() {
        Set<Point> safe = row(-1024, 76, 2495);

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                2 * 128,
                -951,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertTrue(resolution.resolved());
        assertEquals(-1088, resolution.anchor().mapCornerX());
        assertEquals(-1024, resolution.anchor().centerStartX());
    }

    @Test
    void doesNotAcceptA64BlockOrMisalignedRow() {
        Set<Point> safe = new HashSet<>();
        for (int x = -960; x < -896; x++) {
            safe.add(new Point(x, 76, 2495));
        }
        safe.addAll(row(-959, 75, 2495));

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                5 * 128,
                -951,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertEquals(
            CenteredNorthWalkwayLocator.Status.NOT_FOUND,
            resolution.status()
        );
        assertNull(resolution.anchor());
    }

    @Test
    void reportsUnavailableWhenTheMatchingRowLeavesLoadedChunks() {
        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                128,
                -951,
                77,
                2495,
                192,
                3,
                64,
                (x, y, z) -> {
                    if (y != 76 || z != 2495
                        || x < -960 || x > -833) {
                        return CenteredNorthWalkwayLocator.Cell.UNSAFE;
                    }
                    return x < -900
                        ? CenteredNorthWalkwayLocator.Cell.SAFE
                        : CenteredNorthWalkwayLocator.Cell.UNAVAILABLE;
                }
            );

        assertEquals(
            CenteredNorthWalkwayLocator.Status.UNAVAILABLE,
            resolution.status()
        );
    }

    private static Set<Point> row(int startX, int y, int z) {
        Set<Point> safe = new HashSet<>();
        for (int x = startX;
             x < startX + CenteredNorthWalkwayLocator.ANCHOR_LENGTH;
             x++) {
            safe.add(new Point(x, y, z));
        }
        return safe;
    }

    private static CenteredNorthWalkwayLocator.Probe probe(Set<Point> safe) {
        return (x, y, z) -> safe.contains(new Point(x, y, z))
            ? CenteredNorthWalkwayLocator.Cell.SAFE
            : CenteredNorthWalkwayLocator.Cell.UNSAFE;
    }

    private record Point(int x, int y, int z) {
    }
}
