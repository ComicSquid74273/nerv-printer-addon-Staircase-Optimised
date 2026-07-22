package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects every assigned forward column available to circular inventory
 * planning.
 *
 * <p>The active U already owns two columns. Nearby work may extend that
 * frozen inventory plan through the remaining assigned interval. Inventory
 * capacity, rather than an arbitrary column count, is the bound: the
 * dependency-closed material planner decides how many targets actually fit.</p>
 */
public final class CircularBuildHorizon {
    private CircularBuildHorizon() {
    }

    public static List<Integer> forwardOptionalColumns(
        int activeOutboundX,
        int activeReturnX,
        int intervalLeft,
        int intervalRight,
        int mapWidth
    ) {
        if (activeOutboundX < 0
            || activeReturnX != activeOutboundX + 1
            || intervalLeft < 0
            || intervalRight < intervalLeft
            || mapWidth <= 0
            || intervalRight >= mapWidth) {
            throw new IllegalArgumentException(
                "Invalid circular placement horizon."
            );
        }

        ArrayList<Integer> columns = new ArrayList<>(
            Math.max(0, intervalRight - activeReturnX)
        );
        for (int x = activeReturnX + 1;
             x <= intervalRight
                 && x < mapWidth;
             x++) {
            columns.add(x);
        }
        return List.copyOf(columns);
    }
}
