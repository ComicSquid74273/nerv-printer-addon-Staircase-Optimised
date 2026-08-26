package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Deterministic, single-head boustrophedon route used by the BoatFly printer.
 * The plan deliberately contains structural targets only; movement scaffolds
 * and the old compact-U connector blocks never enter this representation.
 */
public final class RasterRoutePlan {
    private RasterRoutePlan() {
    }

    public record Cell<T>(int x, int y, int z, T payload, boolean reference) {
        public Cell {
            Objects.requireNonNull(payload, "payload");
        }
    }

    public record Row<T>(int z, boolean eastbound, List<Cell<T>> cells) {
        public Row {
            cells = List.copyOf(cells);
        }
    }

    public record Plan<T>(
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ,
        List<Row<T>> rows,
        List<Cell<T>> orderedCells,
        Map<Long, Integer> indexByHorizontalCell
    ) {
        public Plan {
            rows = List.copyOf(rows);
            orderedCells = List.copyOf(orderedCells);
            indexByHorizontalCell = Map.copyOf(indexByHorizontalCell);
        }

        public int size() {
            return orderedCells.size();
        }

        public OptionalInt indexOf(int x, int z) {
            Integer index = indexByHorizontalCell.get(horizontalKey(x, z));
            return index == null ? OptionalInt.empty() : OptionalInt.of(index);
        }
    }

    public static <T> Plan<T> create(
        Collection<Cell<T>> source,
        int minimumX,
        int maximumX,
        int minimumZ,
        int maximumZ
    ) {
        Objects.requireNonNull(source, "source");
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Raster bounds are inverted.");
        }

        Map<Integer, List<Cell<T>>> byRow = new HashMap<>();
        Set<Long> occupied = new HashSet<>();
        for (Cell<T> cell : source) {
            Objects.requireNonNull(cell, "cell");
            if (cell.x() < minimumX || cell.x() > maximumX
                || cell.z() < minimumZ || cell.z() > maximumZ) {
                throw new IllegalArgumentException(
                    "Raster cell lies outside the declared bounds: " + cell
                );
            }
            if (!occupied.add(horizontalKey(cell.x(), cell.z()))) {
                throw new IllegalArgumentException(
                    "Duplicate raster X/Z cell at " + cell.x() + "," + cell.z()
                );
            }
            byRow.computeIfAbsent(cell.z(), ignored -> new ArrayList<>())
                .add(cell);
        }

        ArrayList<Row<T>> rows = new ArrayList<>();
        ArrayList<Cell<T>> ordered = new ArrayList<>(source.size());
        HashMap<Long, Integer> indices = new HashMap<>();
        boolean eastbound = true;
        for (int z = minimumZ; z <= maximumZ; z++) {
            ArrayList<Cell<T>> row = new ArrayList<>(
                byRow.getOrDefault(z, List.of())
            );
            Comparator<Cell<T>> comparator = Comparator.comparingInt(Cell::x);
            if (!eastbound) comparator = comparator.reversed();
            row.sort(comparator);

            for (Cell<T> cell : row) {
                indices.put(horizontalKey(cell.x(), cell.z()), ordered.size());
                ordered.add(cell);
            }
            rows.add(new Row<>(z, eastbound, row));
            eastbound = !eastbound;
        }

        return new Plan<>(
            minimumX,
            maximumX,
            minimumZ,
            maximumZ,
            rows,
            ordered,
            indices
        );
    }

    private static long horizontalKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
