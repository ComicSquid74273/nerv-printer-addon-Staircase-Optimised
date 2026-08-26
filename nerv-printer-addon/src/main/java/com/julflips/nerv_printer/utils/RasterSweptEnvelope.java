package com.julflips.nerv_printer.utils;

/** Exact line-versus-expanded-AABB test for a translated mounted envelope. */
public final class RasterSweptEnvelope {
    private static final double EPSILON = 1.0e-9;

    private RasterSweptEnvelope() {
    }

    public record Point(double x, double y, double z) {
    }

    public record Bounds(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Bounds are inverted.");
            }
        }
    }

    public static boolean intersects(
        Point start,
        Point target,
        Bounds envelopeOffsets,
        Bounds obstacle
    ) {
        Bounds expanded = new Bounds(
            obstacle.minX() - envelopeOffsets.maxX(),
            obstacle.minY() - envelopeOffsets.maxY(),
            obstacle.minZ() - envelopeOffsets.maxZ(),
            obstacle.maxX() - envelopeOffsets.minX(),
            obstacle.maxY() - envelopeOffsets.minY(),
            obstacle.maxZ() - envelopeOffsets.minZ()
        );
        double[] interval = {0.0, 1.0};
        return clip(start.x(), target.x() - start.x(),
                expanded.minX(), expanded.maxX(), interval)
            && clip(start.y(), target.y() - start.y(),
                expanded.minY(), expanded.maxY(), interval)
            && clip(start.z(), target.z() - start.z(),
                expanded.minZ(), expanded.maxZ(), interval);
    }

    private static boolean clip(
        double origin,
        double delta,
        double minimum,
        double maximum,
        double[] interval
    ) {
        if (Math.abs(delta) <= EPSILON) {
            return origin >= minimum - EPSILON
                && origin <= maximum + EPSILON;
        }
        double first = (minimum - origin) / delta;
        double second = (maximum - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1] + EPSILON;
    }
}
