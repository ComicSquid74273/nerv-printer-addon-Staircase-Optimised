package com.julflips.nerv_printer.utils;

/**
 * Dimensions and station alignment for one contiguous multi-map NBT.
 *
 * <p>Each map tile contributes a {@value #TILE_SIZE} by
 * {@value #TILE_SIZE} visible area. Raw inputs may contain exactly that
 * visible footprint or one shared northern reference row. The planner
 * normalizes both to the latter canonical form.</p>
 */
public record MapGridLayout(int columns, int rows) {
    public static final int TILE_SIZE = 128;
    public static final int SOURCE_REFERENCE_DEPTH = 1;

    /**
     * Detected raw input geometry. Some exporters include the northern
     * reference row while others export only the exact 128x128 map tiles.
     */
    public record Detected(
        MapGridLayout layout,
        boolean includesNorthernReferenceRow
    ) {
    }

    public MapGridLayout {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException(
                "Map-grid columns and rows must be positive."
            );
        }
        try {
            Math.multiplyExact(TILE_SIZE, columns);
            Math.addExact(
                Math.multiplyExact(TILE_SIZE, rows),
                SOURCE_REFERENCE_DEPTH
            );
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                "Map-grid dimensions exceed the supported integer range.",
                overflow
            );
        }
    }

    /** Visible west-to-east block count. */
    public int visibleWidth() {
        return Math.multiplyExact(TILE_SIZE, columns);
    }

    /** Visible north-to-south block count. */
    public int visibleDepth() {
        return Math.multiplyExact(TILE_SIZE, rows);
    }

    /**
     * Required NBT structure width. This is identical to the visible width.
     */
    public int structureWidth() {
        return visibleWidth();
    }

    /**
     * Canonical planner depth, including its one northern reference row.
     */
    public int sourceDepth() {
        return Math.addExact(
            visibleDepth(),
            SOURCE_REFERENCE_DEPTH
        );
    }

    /**
     * Horizontal station center measured east from the grid's west edge.
     * Odd widths center on the middle tile; even widths center on the seam
     * between the two middle tiles.
     */
    public int stationCenterOffsetX() {
        return Math.multiplyExact(TILE_SIZE / 2, columns);
    }

    /**
     * Eastward station shift compared with the legacy centered 1x1 layout.
     */
    public int stationShiftFromLegacyX() {
        return Math.multiplyExact(TILE_SIZE / 2, columns - 1);
    }

    /**
     * Whether a raw NBT has this grid's exact visible footprint or the same
     * footprint plus one shared northern reference row.
     */
    public boolean matchesStructureSize(
        int actualStructureWidth,
        int actualSourceDepth
    ) {
        return actualStructureWidth == structureWidth()
            && (actualSourceDepth == visibleDepth()
                || actualSourceDepth == sourceDepth());
    }

    /**
     * Rejects an NBT structure whose width or source depth does not match the
     * configured grid.
     */
    public void validateStructureSize(
        int actualStructureWidth,
        int actualSourceDepth
    ) {
        if (matchesStructureSize(
            actualStructureWidth,
            actualSourceDepth
        )) {
            return;
        }
        throw new IllegalArgumentException(
            "Configured " + columns + "x" + rows
                + " map grid requires an NBT structure of "
                + structureWidth() + "x" + visibleDepth()
                + " or " + structureWidth() + "x" + sourceDepth()
                + ", but found " + actualStructureWidth
                + "x" + actualSourceDepth + "."
        );
    }

    /** Detects a contiguous grid directly from a raw single-NBT size. */
    public static Detected detectRawStructure(
        int actualStructureWidth,
        int actualSourceDepth
    ) {
        if (actualStructureWidth <= 0
            || actualStructureWidth % TILE_SIZE != 0) {
            throw new IllegalArgumentException(
                "NBT width must be a positive multiple of 128."
            );
        }
        if (actualSourceDepth <= 0) {
            throw new IllegalArgumentException(
                "NBT depth must be positive."
            );
        }

        boolean includesReference;
        int visibleDepth;
        if (actualSourceDepth % TILE_SIZE == 0) {
            includesReference = false;
            visibleDepth = actualSourceDepth;
        } else if ((actualSourceDepth - SOURCE_REFERENCE_DEPTH)
            % TILE_SIZE == 0) {
            includesReference = true;
            visibleDepth = actualSourceDepth
                - SOURCE_REFERENCE_DEPTH;
        } else {
            throw new IllegalArgumentException(
                "NBT depth must be a positive multiple of 128, optionally "
                    + "followed by one shared northern reference row."
            );
        }
        if (visibleDepth <= 0) {
            throw new IllegalArgumentException(
                "NBT depth does not contain a complete 128-block map row."
            );
        }
        MapGridLayout layout = new MapGridLayout(
            actualStructureWidth / TILE_SIZE,
            visibleDepth / TILE_SIZE
        );
        return new Detected(layout, includesReference);
    }

    /**
     * Creates a configured layout and validates the supplied NBT dimensions.
     */
    public static MapGridLayout validateConfiguredStructure(
        int columns,
        int rows,
        int actualStructureWidth,
        int actualSourceDepth
    ) {
        MapGridLayout layout = new MapGridLayout(columns, rows);
        layout.validateStructureSize(
            actualStructureWidth,
            actualSourceDepth
        );
        return layout;
    }
}
