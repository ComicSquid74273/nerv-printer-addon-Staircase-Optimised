package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;

/** Deterministic launch offsets that keep boat placement clear of the player. */
public final class RasterLaunchPointPlan {
    private RasterLaunchPointPlan() {
    }

    public record Offset(int dx, int dz) {
        public int distanceSquared() {
            return dx * dx + dz * dz;
        }
    }

    public static List<Offset> candidateOffsets(int forwardX, int forwardZ) {
        if (Math.abs(forwardX) + Math.abs(forwardZ) != 1) {
            throw new IllegalArgumentException("Forward direction must be cardinal.");
        }
        int rightX = -forwardZ;
        int rightZ = forwardX;
        ArrayList<Offset> result = new ArrayList<>();
        for (int radius = 3; radius <= 4; radius++) {
            result.add(new Offset(forwardX * radius, forwardZ * radius));
            result.add(new Offset(rightX * radius, rightZ * radius));
            result.add(new Offset(-rightX * radius, -rightZ * radius));
            result.add(new Offset(-forwardX * radius, -forwardZ * radius));
        }
        return List.copyOf(result);
    }

    /**
     * Fixed north/south launch exits used before the boat descends from its
     * support block.  The preferred side is tried first; the one-block X
     * variants keep the route deterministic while allowing a full-width boat
     * to clear a locally occupied edge without invoking a voxel search.
     */
    public static List<Offset> boatEscapeOffsets(int preferredZDirection) {
        if (Math.abs(preferredZDirection) != 1) {
            throw new IllegalArgumentException("Preferred Z direction must be north or south.");
        }
        int z = 3 * preferredZDirection;
        return List.of(
            new Offset(0, z),
            new Offset(1, z),
            new Offset(-1, z),
            new Offset(0, -z),
            new Offset(1, -z),
            new Offset(-1, -z)
        );
    }
}
