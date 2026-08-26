package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Converts block-cell walking paths into precise player movement waypoints. */
public final class RasterWalkRoute {
    private static final double DUPLICATE_DISTANCE_SQUARED = 0.01;
    private static final double SAFE_CELL_INSET = 0.31;

    private RasterWalkRoute() {
    }

    public record Point(double x, double y, double z) {
    }

    public static List<Point> waypoints(
        List<RasterVoxelPathfinder.Cell> path,
        Point requestedTarget
    ) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(requestedTarget, "requestedTarget");
        if (path.isEmpty()) return List.of();

        ArrayList<Point> result = new ArrayList<>();
        for (int index = 1; index < path.size(); index++) {
            RasterVoxelPathfinder.Cell cell = path.get(index);
            result.add(new Point(cell.x() + 0.5, cell.y(), cell.z() + 0.5));
        }

        RasterVoxelPathfinder.Cell requestedCell = new RasterVoxelPathfinder.Cell(
            (int) Math.floor(requestedTarget.x()),
            (int) Math.floor(requestedTarget.y()),
            (int) Math.floor(requestedTarget.z())
        );
        Point safeRequestedTarget = insetToSupportedCell(requestedTarget);
        if (path.getLast().equals(requestedCell)
            && (result.isEmpty()
                || distanceSquared(result.getLast(), safeRequestedTarget)
                    > DUPLICATE_DISTANCE_SQUARED)) {
            result.add(safeRequestedTarget);
        }
        return List.copyOf(result);
    }

    private static Point insetToSupportedCell(Point target) {
        double minX = Math.floor(target.x()) + SAFE_CELL_INSET;
        double maxX = Math.floor(target.x()) + 1.0 - SAFE_CELL_INSET;
        double minZ = Math.floor(target.z()) + SAFE_CELL_INSET;
        double maxZ = Math.floor(target.z()) + 1.0 - SAFE_CELL_INSET;
        return new Point(
            Math.max(minX, Math.min(maxX, target.x())),
            target.y(),
            Math.max(minZ, Math.min(maxZ, target.z()))
        );
    }

    private static double distanceSquared(Point left, Point right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
