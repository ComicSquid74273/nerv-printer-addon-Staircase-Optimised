package com.julflips.nerv_printer.utils;

import java.util.Locale;
import java.util.Optional;

/**
 * Keeps the master/slave wire mode and runtime traversal decision identical.
 */
public final class MiningAssignmentMode {
    private MiningAssignmentMode() {
    }

    public record Decision(boolean pairedTraversal, boolean wholePair) {
    }

    public static String wireName(boolean pairedTraversal, boolean wholePair) {
        if (pairedTraversal && !wholePair) {
            throw new IllegalArgumentException(
                "Circular mining requires a complete two-line pair."
            );
        }
        if (pairedTraversal) return "pair";
        return wholePair ? "fallback" : "single";
    }

    public static boolean usesCircularTraversal(
        boolean pairedTraversal,
        boolean wholePair
    ) {
        if (pairedTraversal && !wholePair) {
            throw new IllegalArgumentException(
                "Circular mining requires a complete two-line pair."
            );
        }
        return pairedTraversal;
    }

    public static Optional<Decision> parseWireName(String wireName) {
        if (wireName == null) return Optional.empty();
        return switch (wireName.toLowerCase(Locale.ROOT)) {
            case "pair" -> Optional.of(new Decision(true, true));
            case "fallback" -> Optional.of(new Decision(false, true));
            case "single" -> Optional.of(new Decision(false, false));
            default -> Optional.empty();
        };
    }
}
