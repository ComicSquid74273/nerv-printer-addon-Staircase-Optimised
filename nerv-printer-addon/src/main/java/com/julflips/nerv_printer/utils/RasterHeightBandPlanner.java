package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Partitions one logical raster band into vertically reachable lane groups. */
public final class RasterHeightBandPlanner {
    private static final double REACH_MARGIN = 0.15;

    private RasterHeightBandPlanner() {
    }

    public record Lane(int coordinate, int height) {
    }

    public record Cluster(
        List<Lane> lanes,
        int centerCoordinate,
        int safeHeight
    ) {
        public Cluster {
            lanes = List.copyOf(lanes);
        }
    }

    public static List<Cluster> create(
        List<Lane> orderedLanes,
        double interactionRange
    ) {
        Objects.requireNonNull(orderedLanes, "orderedLanes");
        if (orderedLanes.isEmpty() || !(interactionRange > 0.0)) {
            throw new IllegalArgumentException("Height-band inputs are invalid.");
        }
        ArrayList<Cluster> result = new ArrayList<>();
        int start = 0;
        while (start < orderedLanes.size()) {
            int end = start + 1;
            while (end < orderedLanes.size()
                && isMutuallyReachable(
                    orderedLanes.subList(start, end + 1),
                    interactionRange
                )) {
                end++;
            }
            result.add(cluster(orderedLanes.subList(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }

    public static boolean sameLookaheadEnvelope(
        double firstEyeY,
        double candidateEyeY,
        double maximumEyeDelta
    ) {
        if (maximumEyeDelta < 0.0) {
            throw new IllegalArgumentException("Maximum eye delta cannot be negative.");
        }
        return Math.abs(firstEyeY - candidateEyeY) <= maximumEyeDelta;
    }

    private static boolean isMutuallyReachable(
        List<Lane> lanes,
        double interactionRange
    ) {
        Cluster cluster = cluster(lanes);
        double eyeY = cluster.safeHeight()
            - RasterFlightPlan.EYE_CLEARANCE_BELOW_SURFACE;
        double usableRange = Math.max(0.0, interactionRange - REACH_MARGIN);
        for (Lane lane : lanes) {
            double horizontal = lane.coordinate() - cluster.centerCoordinate();
            double vertical = lane.height() + 0.5 - eyeY;
            if (Math.hypot(horizontal, vertical) > usableRange) return false;
        }
        return true;
    }

    private static Cluster cluster(List<Lane> lanes) {
        ArrayList<Lane> copy = new ArrayList<>(lanes);
        int safeHeight = copy.stream().mapToInt(Lane::height).min().orElseThrow();
        int centerCoordinate = copy.get(copy.size() / 2).coordinate();
        return new Cluster(copy, centerCoordinate, safeHeight);
    }
}
