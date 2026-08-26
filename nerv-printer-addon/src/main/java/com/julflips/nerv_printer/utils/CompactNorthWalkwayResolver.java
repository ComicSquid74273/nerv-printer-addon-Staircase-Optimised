package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the flat physical north walkway independently from the virtual
 * Z=0 baseline used by the compact NBT geometry.
 */
public final class CompactNorthWalkwayResolver {
    private CompactNorthWalkwayResolver() {
    }

    public enum Cell {
        SAFE,
        UNSAFE,
        UNAVAILABLE
    }

    public enum Status {
        RESOLVED,
        NO_WALKABLE_HEIGHT,
        NO_SAFE_ROW,
        AMBIGUOUS,
        UNAVAILABLE
    }

    @FunctionalInterface
    public interface Probe {
        Cell probe(int x, int relativeY);
    }

    public record Resolution(
        Status status,
        Integer relativeY,
        List<Integer> candidates,
        List<Integer> safeRows
    ) {
        public Resolution {
            candidates = List.copyOf(candidates);
            safeRows = List.copyOf(safeRows);
        }

        public boolean resolved() {
            return status == Status.RESOLVED && relativeY != null;
        }
    }

    /**
     * Candidate heights are the intersection of every first-row support's
     * one-block walking range. World safety may be checked for only the active
     * interval, but the candidate geometry always covers the full map width.
     */
    public static Resolution resolve(
        int[] firstVisibleRowY,
        int minimumX,
        int maximumX,
        Probe probe
    ) {
        Objects.requireNonNull(firstVisibleRowY, "firstVisibleRowY");
        Objects.requireNonNull(probe, "probe");
        if (firstVisibleRowY.length < 2) {
            throw new IllegalArgumentException("Expected at least two first-row heights.");
        }
        if (minimumX < 0
            || maximumX >= firstVisibleRowY.length
            || minimumX > maximumX) {
            throw new IllegalArgumentException("Invalid north-walkway X interval.");
        }

        List<Integer> candidates = candidateHeights(firstVisibleRowY);
        if (candidates.isEmpty()) {
            return new Resolution(
                Status.NO_WALKABLE_HEIGHT,
                null,
                List.of(),
                List.of()
            );
        }

        List<Integer> safeRows = new ArrayList<>();
        boolean unavailable = false;
        for (int relativeY : candidates) {
            boolean safe = true;
            for (int x = minimumX; x <= maximumX; x++) {
                Cell cell = Objects.requireNonNull(
                    probe.probe(x, relativeY),
                    "The north-walkway probe returned null."
                );
                if (cell == Cell.UNAVAILABLE) {
                    unavailable = true;
                    safe = false;
                    break;
                }
                if (cell == Cell.UNSAFE) {
                    safe = false;
                    break;
                }
            }
            if (safe) safeRows.add(relativeY);
        }

        if (unavailable) {
            return new Resolution(Status.UNAVAILABLE, null, candidates, safeRows);
        }
        if (safeRows.isEmpty()) {
            return new Resolution(Status.NO_SAFE_ROW, null, candidates, safeRows);
        }
        if (safeRows.size() > 1) {
            return new Resolution(Status.AMBIGUOUS, null, candidates, safeRows);
        }
        return new Resolution(
            Status.RESOLVED,
            safeRows.getFirst(),
            candidates,
            safeRows
        );
    }

    /** Returns every flat walkway height that stays within one block of the first visible row. */
    public static List<Integer> candidateHeights(int[] firstVisibleRowY) {
        Objects.requireNonNull(firstVisibleRowY, "firstVisibleRowY");
        if (firstVisibleRowY.length < 2) {
            throw new IllegalArgumentException("Expected at least two first-row heights.");
        }
        int minimumCandidate = Integer.MIN_VALUE;
        int maximumCandidate = Integer.MAX_VALUE;
        for (int firstY : firstVisibleRowY) {
            minimumCandidate = Math.max(minimumCandidate, firstY - 1);
            maximumCandidate = Math.min(maximumCandidate, firstY + 1);
        }
        if (minimumCandidate > maximumCandidate) return List.of();
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int relativeY = minimumCandidate;
             relativeY <= maximumCandidate;
             relativeY++) {
            candidates.add(relativeY);
        }
        return List.copyOf(candidates);
    }
}
