package com.julflips.nerv_printer.utils;

/**
 * Index movement for hidden circular-build recovery steering.
 */
public final class CircularBuildRecoveryCursor {
    private CircularBuildRecoveryCursor() {
    }

    public static int advance(int currentIndex, int direction, int targetCount) {
        if (targetCount <= 0
            || currentIndex < 0
            || currentIndex >= targetCount) {
            throw new IllegalArgumentException(
                "Recovery cursor must start on a route target."
            );
        }
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException(
                "Recovery direction must be -1 or 1."
            );
        }
        return currentIndex + direction;
    }

    public static boolean complete(int index, int targetCount) {
        if (targetCount <= 0) {
            throw new IllegalArgumentException(
                "Recovery route must contain a target."
            );
        }
        return index < 0 || index >= targetCount;
    }
}
