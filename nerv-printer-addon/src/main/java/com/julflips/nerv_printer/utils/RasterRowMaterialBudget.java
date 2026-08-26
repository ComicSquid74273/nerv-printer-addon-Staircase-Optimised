package com.julflips.nerv_printer.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure full-row inventory gate used before lateral-lane entry. */
public final class RasterRowMaterialBudget {
    private RasterRowMaterialBudget() {
    }

    public static <T> Optional<T> firstShortage(
        List<T> unfinishedRow,
        Map<T, Integer> available
    ) {
        Objects.requireNonNull(unfinishedRow, "unfinishedRow");
        Objects.requireNonNull(available, "available");
        LinkedHashMap<T, Integer> needed = new LinkedHashMap<>();
        for (T material : unfinishedRow) {
            needed.merge(Objects.requireNonNull(material, "material"), 1, Integer::sum);
        }
        return needed.entrySet().stream()
            .filter(entry -> available.getOrDefault(entry.getKey(), 0)
                < entry.getValue())
            .map(Map.Entry::getKey)
            .findFirst();
    }
}
