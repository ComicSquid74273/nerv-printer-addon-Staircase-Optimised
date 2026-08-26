package com.julflips.nerv_printer.utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Produces a raster-only staircased surface without the vertical offsets that
 * are needed by the legacy connector route. Each X column may be translated
 * independently because map shading compares north/south neighbours within
 * the same column; translating a whole column preserves every height delta.
 */
public final class RasterSurfaceNormalizer {
    public static final int MAX_MAP_SPAN = 128;

    private RasterSurfaceNormalizer() {
    }

    public record Result(
        int[][] heights,
        int visibleMinimumY,
        int visibleMaximumY,
        int visibleSpanY,
        int structuralMinimumY,
        int structuralMaximumY,
        int structuralSpanY
    ) {
        public Result {
            heights = copy(heights);
        }

        public int height(int x, int z) {
            return heights[x][z];
        }

        @Override
        public int[][] heights() {
            return copy(heights);
        }
    }

    /**
     * @param sourceY heights indexed as [x][z]
     * @param referenceZ auxiliary north reference row
     * @param firstVisibleZ first visible map row, inclusive
     * @param lastVisibleZ last visible map row, inclusive
     */
    public static Result normalize(
        int[][] sourceY,
        int referenceZ,
        int firstVisibleZ,
        int lastVisibleZ
    ) {
        Objects.requireNonNull(sourceY, "sourceY");
        if (sourceY.length == 0 || firstVisibleZ > lastVisibleZ) {
            throw new IllegalArgumentException("Raster surface dimensions are empty.");
        }

        int requiredZ = Math.max(referenceZ, lastVisibleZ) + 1;
        int[][] normalized = new int[sourceY.length][];
        int visibleMinimum = Integer.MAX_VALUE;
        int visibleMaximum = Integer.MIN_VALUE;
        int structuralMinimum = Integer.MAX_VALUE;
        int structuralMaximum = Integer.MIN_VALUE;

        for (int x = 0; x < sourceY.length; x++) {
            if (sourceY[x] == null || sourceY[x].length < requiredZ) {
                throw new IllegalArgumentException(
                    "Raster source column " + x + " is missing required rows."
                );
            }
            int columnMinimum = Integer.MAX_VALUE;
            int columnMaximum = Integer.MIN_VALUE;
            for (int z = firstVisibleZ; z <= lastVisibleZ; z++) {
                columnMinimum = Math.min(columnMinimum, sourceY[x][z]);
                columnMaximum = Math.max(columnMaximum, sourceY[x][z]);
            }
            int columnSpan = columnMaximum - columnMinimum + 1;
            if (columnSpan > MAX_MAP_SPAN) {
                throw new IllegalArgumentException(
                    "visible column " + x + " needs " + columnSpan
                        + " vertical blocks (maximum " + MAX_MAP_SPAN + ")"
                );
            }

            normalized[x] = Arrays.copyOf(sourceY[x], sourceY[x].length);
            for (int z = 0; z < normalized[x].length; z++) {
                normalized[x][z] -= columnMinimum;
            }
            for (int z = firstVisibleZ; z <= lastVisibleZ; z++) {
                visibleMinimum = Math.min(visibleMinimum, normalized[x][z]);
                visibleMaximum = Math.max(visibleMaximum, normalized[x][z]);
            }
            for (int z = referenceZ; z <= lastVisibleZ; z++) {
                structuralMinimum = Math.min(structuralMinimum, normalized[x][z]);
                structuralMaximum = Math.max(structuralMaximum, normalized[x][z]);
            }
        }

        int visibleSpan = visibleMaximum - visibleMinimum + 1;
        if (visibleSpan > MAX_MAP_SPAN) {
            throw new IllegalArgumentException(
                "visible map needs " + visibleSpan
                    + " vertical blocks after normalization (maximum "
                    + MAX_MAP_SPAN + ")"
            );
        }
        return new Result(
            normalized,
            visibleMinimum,
            visibleMaximum,
            visibleSpan,
            structuralMinimum,
            structuralMaximum,
            structuralMaximum - structuralMinimum + 1
        );
    }

    private static int[][] copy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = Arrays.copyOf(source[index], source[index].length);
        }
        return copy;
    }
}
