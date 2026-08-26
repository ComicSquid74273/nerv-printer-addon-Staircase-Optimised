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
    void derivesEvenTwoMapCornerFromTheWestMiddleSeam() {
        Set<Point> safe = row(-960, 76, 2495);

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
        assertEquals(-960, resolution.anchor().centerStartX());
        assertEquals(128, resolution.anchor().relativeStartX());
    }

    @Test
    void doesNotAcceptA64BlockRow() {
        Set<Point> safe = new HashSet<>();
        for (int x = -960; x < -896; x++) {
            safe.add(new Point(x, 76, 2495));
        }

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
    void derivesEvenTwoMapCornerFromTheEastMiddleSeam() {
        Set<Point> safe = row(-960, 76, 2495);

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                2 * 128,
                -840,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertTrue(resolution.resolved());
        assertEquals(-960, resolution.anchor().mapCornerX());
        assertEquals(-960, resolution.anchor().centerStartX());
        assertEquals(0, resolution.anchor().relativeStartX());
    }

    @Test
    void rejectsACompleteNonCanonicalXRun() {
        Set<Point> safe = row(-959, 76, 2495);

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

        assertEquals(
            CenteredNorthWalkwayLocator.Status.NOT_FOUND,
            resolution.status()
        );
        assertNull(resolution.anchor());
    }

    @Test
    void asksForAnEvenGridSideWhenPlayerIsAtAnchorCenter() {
        Set<Point> safe = row(-960, 76, 2495);

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                2 * 128,
                -896,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertEquals(
            CenteredNorthWalkwayLocator.Status.AMBIGUOUS,
            resolution.status()
        );
        assertNull(resolution.anchor());
    }

    @Test
    void keepsSixMapGridAndItsMiddleSeamCanonical() {
        Set<Point> safe = row(-960, 76, 2495);

        CenteredNorthWalkwayLocator.Resolution resolution =
            CenteredNorthWalkwayLocator.locate(
                6 * 128,
                -951,
                77,
                2495,
                192,
                3,
                64,
                probe(safe)
            );

        assertTrue(resolution.resolved());
        assertEquals(-1344, resolution.anchor().mapCornerX());
        assertEquals(384, resolution.anchor().relativeStartX());
        assertEquals(
            -960,
            resolution.anchor().mapCornerX() + 6 * 64
        );
        assertTrue(CenteredNorthWalkwayLocator.isMapBoundary(
            resolution.anchor().mapCornerX()
        ));
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
