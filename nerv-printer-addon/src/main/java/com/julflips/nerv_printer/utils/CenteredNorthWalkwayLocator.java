package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Locates the player-built 128-block center section of a map's north
 * cobblestone walkway. The complete multi-map width is derived from this
 * center anchor and does not need to exist yet.
 */
public final class CenteredNorthWalkwayLocator {
    public static final int ANCHOR_LENGTH = MapGridLayout.TILE_SIZE;

    private CenteredNorthWalkwayLocator() {
    }

    public enum Cell {
        SAFE,
        UNSAFE,
        UNAVAILABLE
    }

    public enum Status {
        RESOLVED,
        NOT_FOUND,
        UNAVAILABLE,
        AMBIGUOUS
    }

    @FunctionalInterface
    public interface Probe {
        Cell probe(int x, int y, int z);
    }

    public record Anchor(
        int mapCornerX,
        int walkwayY,
        int mapCornerZ,
        int centerStartX
    ) {
        public int walkwayZ() {
            return mapCornerZ - 1;
        }

        public int centerEndX() {
            return centerStartX + ANCHOR_LENGTH - 1;
        }
    }

    public record Resolution(Status status, Anchor anchor, int candidates) {
        public boolean resolved() {
            return status == Status.RESOLVED && anchor != null;
        }
    }

    /**
     * Searches only canonical 128-block map boundaries. This keeps the scan
     * bounded even for large grids and prevents an arbitrary cobblestone line
     * from shifting the map by a partial map tile.
     */
    public static Resolution locate(
        int mapWidth,
        int playerX,
        int playerY,
        int playerZ,
        int horizontalRadius,
        int verticalRadius,
        int depthRadius,
        Probe probe
    ) {
        if (mapWidth < ANCHOR_LENGTH
            || mapWidth % ANCHOR_LENGTH != 0) {
            throw new IllegalArgumentException(
                "Map width must be a positive multiple of 128."
            );
        }
        if (horizontalRadius < 0 || verticalRadius < 0 || depthRadius < 0) {
            throw new IllegalArgumentException("Search radii cannot be negative.");
        }
        Objects.requireNonNull(probe, "probe");

        int centerOffset = (mapWidth - ANCHOR_LENGTH) / 2;
        int minimumCenterStart = Math.subtractExact(
            playerX,
            horizontalRadius + ANCHOR_LENGTH - 1
        );
        int maximumCenterStart = Math.addExact(playerX, horizontalRadius);
        int minimumCornerX = Math.subtractExact(
            minimumCenterStart,
            centerOffset
        );
        int maximumCornerX = Math.subtractExact(
            maximumCenterStart,
            centerOffset
        );

        ArrayList<Anchor> safe = new ArrayList<>();
        boolean unavailable = false;
        for (int mapCornerZ = playerZ - depthRadius + 1;
             mapCornerZ <= playerZ + depthRadius + 1;
             mapCornerZ++) {
            if (!isMapBoundary(mapCornerZ)) continue;
            int walkwayZ = mapCornerZ - 1;
            for (int mapCornerX = firstBoundaryAtOrAfter(minimumCornerX);
                 mapCornerX <= maximumCornerX;
                 mapCornerX += ANCHOR_LENGTH) {
                int centerStartX = mapCornerX + centerOffset;
                for (int walkwayY = playerY - verticalRadius;
                     walkwayY <= playerY + verticalRadius;
                     walkwayY++) {
                    boolean complete = true;
                    for (int offset = 0; offset < ANCHOR_LENGTH; offset++) {
                        Cell cell = Objects.requireNonNull(
                            probe.probe(
                                centerStartX + offset,
                                walkwayY,
                                walkwayZ
                            ),
                            "The centered walkway probe returned null."
                        );
                        if (cell == Cell.UNAVAILABLE) unavailable = true;
                        if (cell != Cell.SAFE) {
                            complete = false;
                            break;
                        }
                    }
                    if (complete) {
                        safe.add(
                            new Anchor(
                                mapCornerX,
                                walkwayY,
                                mapCornerZ,
                                centerStartX
                            )
                        );
                    }
                }
            }
        }

        if (safe.isEmpty()) {
            return new Resolution(
                unavailable ? Status.UNAVAILABLE : Status.NOT_FOUND,
                null,
                0
            );
        }
        safe.sort(
            Comparator
                .comparingLong((Anchor anchor) -> distanceScore(
                    anchor,
                    playerX,
                    playerY,
                    playerZ
                ))
                .thenComparingInt(Anchor::mapCornerZ)
                .thenComparingInt(Anchor::mapCornerX)
                .thenComparingInt(Anchor::walkwayY)
        );
        Anchor nearest = safe.getFirst();
        long nearestScore = distanceScore(
            nearest,
            playerX,
            playerY,
            playerZ
        );
        long equallyNear = safe.stream()
            .filter(anchor ->
                distanceScore(anchor, playerX, playerY, playerZ)
                    == nearestScore)
            .count();
        if (equallyNear > 1) {
            return new Resolution(Status.AMBIGUOUS, null, safe.size());
        }
        return new Resolution(Status.RESOLVED, nearest, safe.size());
    }

    public static boolean isMapBoundary(int coordinate) {
        return Math.floorMod(coordinate + 64, ANCHOR_LENGTH) == 0;
    }

    private static int firstBoundaryAtOrAfter(int coordinate) {
        int remainder = Math.floorMod(coordinate + 64, ANCHOR_LENGTH);
        return remainder == 0
            ? coordinate
            : Math.addExact(coordinate, ANCHOR_LENGTH - remainder);
    }

    private static long distanceScore(
        Anchor anchor,
        int playerX,
        int playerY,
        int playerZ
    ) {
        long doubledCenterX = 2L * anchor.centerStartX()
            + ANCHOR_LENGTH - 1L;
        long doubledDx = 2L * playerX - doubledCenterX;
        long dy = (long) playerY - anchor.walkwayY();
        long dz = (long) playerZ - anchor.walkwayZ();
        return doubledDx * doubledDx + 4L * dy * dy + 4L * dz * dz;
    }
}
