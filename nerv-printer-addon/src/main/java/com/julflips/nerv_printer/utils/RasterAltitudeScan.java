package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;

/** Highest-first altitude candidates for entry routing. */
public final class RasterAltitudeScan {
    private RasterAltitudeScan() {
    }

    public static List<Double> candidates(
        double currentY,
        double conservativeSafeY,
        double extraClearance,
        double step
    ) {
        if (!(step > 0.0) || extraClearance < 0.0) {
            throw new IllegalArgumentException("Altitude scan parameters are invalid.");
        }
        double lowest = Math.min(currentY, conservativeSafeY) - extraClearance;
        ArrayList<Double> result = new ArrayList<>();
        for (double y = currentY; y >= lowest - 0.0001; y -= step) {
            result.add(y);
        }
        return List.copyOf(result);
    }
}
