package com.julflips.nerv_printer.utils;

/**
 * Pair-level activation rule for compact U building.
 */
public final class CircularBuildAssignmentPolicy {
    private CircularBuildAssignmentPolicy() {
    }

    public static boolean useCircular(
        boolean assignedToThisBot,
        boolean circularEnabled,
        boolean fitsUsableInventory
    ) {
        return assignedToThisBot && circularEnabled && fitsUsableInventory;
    }
}
