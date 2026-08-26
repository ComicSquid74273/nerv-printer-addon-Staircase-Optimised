package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RasterSurfaceNormalizerTest {
    @Test
    void removesIndependentConnectorOffsetsWithoutChangingMapDeltas() {
        int[][] source = {
            {40, 41, 42, 41},
            {-20, -19, -18, -19}
        };

        RasterSurfaceNormalizer.Result result =
            RasterSurfaceNormalizer.normalize(source, 0, 1, 3);

        assertEquals(0, result.height(0, 1));
        assertEquals(1, result.height(0, 2));
        assertEquals(0, result.height(0, 3));
        assertEquals(0, result.height(1, 1));
        assertEquals(1, result.height(1, 2));
        assertEquals(0, result.height(1, 3));
        for (int x = 0; x < source.length; x++) {
            for (int z = 2; z <= 3; z++) {
                assertEquals(
                    source[x][z] - source[x][z - 1],
                    result.height(x, z) - result.height(x, z - 1)
                );
            }
        }
    }

    @Test
    void acceptsExact128HighVisibleMapWithAuxiliary129thReferenceLevel() {
        int[][] source = new int[1][129];
        source[0][0] = 128;
        for (int z = 1; z <= 128; z++) source[0][z] = z - 1;

        RasterSurfaceNormalizer.Result result =
            RasterSurfaceNormalizer.normalize(source, 0, 1, 128);

        assertEquals(128, result.visibleSpanY());
        assertEquals(129, result.structuralSpanY());
        assertEquals(128, result.height(0, 0));
    }

    @Test
    void rejectsAVisibleColumnThatCannotFitIn128Levels() {
        int[][] source = new int[1][130];
        for (int z = 1; z <= 129; z++) source[0][z] = z - 1;

        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> RasterSurfaceNormalizer.normalize(source, 0, 1, 129)
        );
        assertEquals(
            "visible column 0 needs 129 vertical blocks (maximum 128)",
            failure.getMessage()
        );
    }
}
