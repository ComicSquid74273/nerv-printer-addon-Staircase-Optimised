package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Durable progress rules for an authoritative raster. A cursor may look ahead,
 * but the frontier advances only across server-confirmed cells.
 */
public final class RasterProgress {
    private RasterProgress() {
    }

    public record Snapshot(int cursor, int confirmedFrontier, int firstUnfinished) {
    }

    public static <T> Snapshot reconcile(
        List<T> ordered,
        int cursor,
        int confirmedFrontier,
        Predicate<T> confirmed
    ) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(confirmed, "confirmed");
        int size = ordered.size();
        int frontier = Math.max(0, Math.min(confirmedFrontier, size));
        while (frontier < size && confirmed.test(ordered.get(frontier))) frontier++;

        int firstUnfinished = frontier;
        for (int i = frontier; i < size; i++) {
            if (!confirmed.test(ordered.get(i))) {
                firstUnfinished = i;
                break;
            }
        }
        int safeCursor = Math.max(firstUnfinished, Math.min(cursor, size));
        return new Snapshot(safeCursor, frontier, firstUnfinished);
    }

    public static <T> List<T> lookahead(
        List<T> ordered,
        int firstUnfinished,
        int count,
        Predicate<T> confirmed
    ) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(confirmed, "confirmed");
        if (count <= 0 || firstUnfinished >= ordered.size()) return List.of();
        ArrayList<T> result = new ArrayList<>(count);
        for (int i = Math.max(0, firstUnfinished);
             i < ordered.size() && result.size() < count;
             i++) {
            T value = ordered.get(i);
            if (!confirmed.test(value)) result.add(value);
        }
        return List.copyOf(result);
    }
}
