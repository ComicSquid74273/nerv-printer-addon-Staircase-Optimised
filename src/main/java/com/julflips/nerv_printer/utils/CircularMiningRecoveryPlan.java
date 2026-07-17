package com.julflips.nerv_printer.utils;

import java.util.List;
import java.util.Objects;

/**
 * Classifies the remaining support blocks of one ordered U route.
 *
 * <p>A recoverable interrupted route has exactly one continuous walkable
 * segment attached to at least one of its two north-side endpoints. A gap
 * between two walkable segments is not safe for U traversal.</p>
 */
public final class CircularMiningRecoveryPlan {
    private CircularMiningRecoveryPlan() {
    }

    public enum Cell {
        AIR,
        WALKABLE,
        BLOCKED
    }

    public enum Mode {
        COMPLETE,
        FORWARD,
        RECOVER_FROM_START,
        RECOVER_FROM_END,
        FALLBACK
    }

    public record Result(Mode mode, int firstWalkable, int lastWalkable) {
    }

    public static Result analyze(List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty()) throw new IllegalArgumentException("A U route cannot be empty.");

        int first = -1;
        int last = -1;
        for (int index = 0; index < cells.size(); index++) {
            Cell cell = Objects.requireNonNull(cells.get(index), "cells[" + index + "]");
            if (cell == Cell.BLOCKED) return new Result(Mode.FALLBACK, -1, -1);
            if (cell == Cell.WALKABLE) {
                if (first < 0) first = index;
                last = index;
            }
        }

        if (first < 0) return new Result(Mode.COMPLETE, -1, -1);
        for (int index = first; index <= last; index++) {
            if (cells.get(index) != Cell.WALKABLE) {
                return new Result(Mode.FALLBACK, first, last);
            }
        }

        boolean touchesStart = first == 0;
        boolean touchesEnd = last == cells.size() - 1;
        if (touchesStart && touchesEnd) return new Result(Mode.FORWARD, first, last);
        if (touchesStart) return new Result(Mode.RECOVER_FROM_START, first, last);
        if (touchesEnd) return new Result(Mode.RECOVER_FROM_END, first, last);
        return new Result(Mode.FALLBACK, first, last);
    }
}
