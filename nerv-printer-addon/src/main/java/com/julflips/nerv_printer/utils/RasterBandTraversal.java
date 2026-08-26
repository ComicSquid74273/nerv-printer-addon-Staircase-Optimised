package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;

/** Pure three-line Nerv-style serpentine band traversal. */
public final class RasterBandTraversal {
    private RasterBandTraversal() {
    }

    public record Step(
        int band,
        int direction,
        int inner,
        int centerOuter,
        List<Integer> outerLanes
    ) {
        public Step {
            outerLanes = List.copyOf(outerLanes);
        }
    }

    public static List<Step> create(
        int outerMinimum,
        int outerMaximum,
        int innerMinimum,
        int innerMaximum,
        int bandWidth,
        boolean outerAscending,
        boolean firstInnerAscending
    ) {
        return create(
            outerMinimum,
            outerMaximum,
            innerMinimum,
            innerMaximum,
            bandWidth,
            outerAscending,
            firstInnerAscending,
            true
        );
    }

    public static List<Step> create(
        int outerMinimum,
        int outerMaximum,
        int innerMinimum,
        int innerMaximum,
        int bandWidth,
        boolean outerAscending,
        boolean firstInnerAscending,
        boolean alternateInnerDirection
    ) {
        if (outerMinimum > outerMaximum || innerMinimum > innerMaximum
            || bandWidth < 1) {
            throw new IllegalArgumentException("Raster band bounds are invalid.");
        }
        int outerCount = outerMaximum - outerMinimum + 1;
        int bandCount = (outerCount + bandWidth - 1) / bandWidth;
        ArrayList<Step> result = new ArrayList<>();
        for (int band = 0; band < bandCount; band++) {
            ArrayList<Integer> lanes = new ArrayList<>(bandWidth);
            for (int lane = 0; lane < bandWidth; lane++) {
                int outer = outerAscending
                    ? outerMinimum + band * bandWidth + lane
                    : outerMaximum - band * bandWidth - lane;
                if (outer >= outerMinimum && outer <= outerMaximum) {
                    lanes.add(outer);
                }
            }
            boolean innerAscending = (!alternateInnerDirection
                || (band & 1) == 0)
                ? firstInnerAscending : !firstInnerAscending;
            int direction = innerAscending ? 1 : -1;
            int centerOuter = lanes.get(lanes.size() / 2);
            for (int offset = 0; offset <= innerMaximum - innerMinimum; offset++) {
                int inner = innerAscending
                    ? innerMinimum + offset : innerMaximum - offset;
                result.add(new Step(
                    band,
                    direction,
                    inner,
                    centerOuter,
                    lanes
                ));
            }
        }
        return List.copyOf(result);
    }
}
