package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapGridLayoutTest {
    @Test
    void oneByOneRetainsTheLegacyDimensionsAndStationPosition() {
        MapGridLayout layout = new MapGridLayout(1, 1);

        assertEquals(128, layout.visibleWidth());
        assertEquals(128, layout.visibleDepth());
        assertEquals(128, layout.structureWidth());
        assertEquals(129, layout.sourceDepth());
        assertEquals(64, layout.stationCenterOffsetX());
        assertEquals(0, layout.stationShiftFromLegacyX());
        assertTrue(layout.matchesStructureSize(128, 128));
        assertTrue(layout.matchesStructureSize(128, 129));
    }

    @Test
    void detectsExactAndReferenceRowSingleNbtGrids() {
        MapGridLayout.Detected exactTwoByTwo =
            MapGridLayout.detectRawStructure(256, 256);
        MapGridLayout.Detected referencedTwoByTwo =
            MapGridLayout.detectRawStructure(256, 257);
        MapGridLayout.Detected exactFiveByFive =
            MapGridLayout.detectRawStructure(640, 640);
        MapGridLayout.Detected referencedFiveByFive =
            MapGridLayout.detectRawStructure(640, 641);

        assertEquals(new MapGridLayout(2, 2), exactTwoByTwo.layout());
        assertFalse(exactTwoByTwo.includesNorthernReferenceRow());
        assertEquals(
            new MapGridLayout(2, 2),
            referencedTwoByTwo.layout()
        );
        assertTrue(referencedTwoByTwo.includesNorthernReferenceRow());
        assertEquals(new MapGridLayout(5, 5), exactFiveByFive.layout());
        assertFalse(exactFiveByFive.includesNorthernReferenceRow());
        assertEquals(
            new MapGridLayout(5, 5),
            referencedFiveByFive.layout()
        );
        assertTrue(referencedFiveByFive.includesNorthernReferenceRow());
    }

    @Test
    void validatesTheSuppliedTwoByTwoStructure() {
        MapGridLayout layout =
            MapGridLayout.validateConfiguredStructure(
                2,
                2,
                256,
                257
            );

        assertEquals(256, layout.visibleWidth());
        assertEquals(256, layout.visibleDepth());
        assertEquals(256, layout.structureWidth());
        assertEquals(257, layout.sourceDepth());
        assertEquals(128, layout.stationCenterOffsetX());
        assertEquals(64, layout.stationShiftFromLegacyX());
    }

    @Test
    void calculatesFiveBySevenContiguousDimensions() {
        MapGridLayout layout = new MapGridLayout(5, 7);

        assertEquals(640, layout.visibleWidth());
        assertEquals(896, layout.visibleDepth());
        assertEquals(640, layout.structureWidth());
        assertEquals(897, layout.sourceDepth());
        assertEquals(320, layout.stationCenterOffsetX());
        assertEquals(256, layout.stationShiftFromLegacyX());
        layout.validateStructureSize(640, 897);
    }

    @Test
    void calculatesSixByTenContiguousDimensions() {
        MapGridLayout layout = new MapGridLayout(6, 10);

        assertEquals(768, layout.visibleWidth());
        assertEquals(1280, layout.visibleDepth());
        assertEquals(768, layout.structureWidth());
        assertEquals(1281, layout.sourceDepth());
        assertEquals(384, layout.stationCenterOffsetX());
        assertEquals(320, layout.stationShiftFromLegacyX());
        layout.validateStructureSize(768, 1281);
    }

    @Test
    void oddColumnsCenterTheStationOnTheMiddleTile() {
        MapGridLayout layout = new MapGridLayout(5, 7);
        int middleTile = layout.columns() / 2;
        int middleTileCenter =
            middleTile * MapGridLayout.TILE_SIZE
                + MapGridLayout.TILE_SIZE / 2;

        assertEquals(
            middleTileCenter,
            layout.stationCenterOffsetX()
        );
    }

    @Test
    void evenColumnsCenterTheStationOnTheMiddleSeam() {
        MapGridLayout layout = new MapGridLayout(6, 10);
        int middleSeam =
            layout.columns() / 2 * MapGridLayout.TILE_SIZE;

        assertEquals(middleSeam, layout.stationCenterOffsetX());
    }

    @Test
    void rejectsNonPositiveConfiguredDimensions() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(1, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(-1, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(1, -1)
        );
    }

    @Test
    void rejectsMismatchedStructureWidthOrSourceDepth() {
        MapGridLayout layout = new MapGridLayout(2, 2);

        assertFalse(layout.matchesStructureSize(128, 257));
        assertFalse(layout.matchesStructureSize(256, 129));
        assertThrows(
            IllegalArgumentException.class,
            () -> layout.validateStructureSize(128, 257)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> layout.validateStructureSize(256, 129)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MapGridLayout.validateConfiguredStructure(
                2,
                2,
                256,
                258
            )
        );
    }

    @Test
    void rejectsRawDimensionsThatAreNotCompleteMapFactors() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MapGridLayout.detectRawStructure(255, 256)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MapGridLayout.detectRawStructure(256, 255)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MapGridLayout.detectRawStructure(256, 1)
        );
    }

    @Test
    void acceptsTheLargestExactlyRepresentableDimensions() {
        int maximumTiles = Integer.MAX_VALUE / MapGridLayout.TILE_SIZE;
        MapGridLayout layout = new MapGridLayout(
            maximumTiles,
            maximumTiles
        );

        assertEquals(
            MapGridLayout.TILE_SIZE * maximumTiles,
            layout.structureWidth()
        );
        assertEquals(
            MapGridLayout.TILE_SIZE * maximumTiles + 1,
            layout.sourceDepth()
        );
    }

    @Test
    void rejectsStructureDimensionArithmeticOverflow() {
        int overflowingTiles =
            Integer.MAX_VALUE / MapGridLayout.TILE_SIZE + 1;

        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(overflowingTiles, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(1, overflowingTiles)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapGridLayout(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
            )
        );
    }
}
