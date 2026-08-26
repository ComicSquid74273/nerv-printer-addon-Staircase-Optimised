package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/**
 * Locates the player-built 128-block map tile on a map mosaic's north
 * cobblestone walkway. The complete multi-map width is derived from this
 * canonical map-grid anchor and does not need to exist yet.
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

        public int relativeStartX() {
            return Math.subtractExact(centerStartX, mapCornerX);
        }
    }

    public record Resolution(Status status, Anchor anchor, int candidates) {
        public boolean resolved() {
            return status == Status.RESOLVED && anchor != null;
        }
    }

    /**
     * Finds a complete 128-block X run that occupies one real scale-zero
     * Minecraft map tile. Map centers are multiples of 128, so tile starts
     * are always {@code 128*n - 64}; arbitrary or seam-straddling runs are
     * rejected.
     *
     * <p>An odd-width mosaic has one middle tile. An even-width mosaic has
     * two middle tiles instead, so the end of the supplied tile nearest the
     * player is treated as the middle seam. The player therefore chooses the
     * correct canonical half simply by standing near the chest/platform end
     * before enabling the printer.</p>
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

        int mapColumns = mapWidth / ANCHOR_LENGTH;
        int minimumAnchorStart = Math.subtractExact(
            playerX,
            horizontalRadius + ANCHOR_LENGTH - 1
        );
        int maximumAnchorStart = Math.addExact(playerX, horizontalRadius);
        int firstAnchorStart = firstMapBoundaryAtOrAfter(
            minimumAnchorStart
        );

        ArrayList<Anchor> safe = new ArrayList<>();
        boolean unavailable = false;
        boolean ambiguousEvenSide = false;
        for (int mapCornerZ = playerZ - depthRadius + 1;
             mapCornerZ <= playerZ + depthRadius + 1;
             mapCornerZ++) {
            if (!isMapBoundary(mapCornerZ)) continue;
            int walkwayZ = mapCornerZ - 1;
            for (int walkwayY = playerY - verticalRadius;
                 walkwayY <= playerY + verticalRadius;
                 walkwayY++) {
                for (int anchorStartX = firstAnchorStart;
                     anchorStartX <= maximumAnchorStart;
                     anchorStartX = Math.addExact(
                         anchorStartX,
                         ANCHOR_LENGTH
                     )) {
                    ProbeResult probeResult = probeAnchor(
                        anchorStartX,
                        walkwayY,
                        walkwayZ,
                        probe
                    );
                    unavailable |= probeResult.unavailable();
                    if (!probeResult.safe()) continue;

                    Integer mapCornerX = mapCornerForAnchor(
                        anchorStartX,
                        mapColumns,
                        playerX
                    );
                    if (mapCornerX == null) {
                        ambiguousEvenSide = true;
                        continue;
                    }
                    safe.add(
                        new Anchor(
                            mapCornerX,
                            walkwayY,
                            mapCornerZ,
                            anchorStartX
                        )
                    );
                }
            }
        }

        if (safe.isEmpty()) {
            if (ambiguousEvenSide) {
                return new Resolution(Status.AMBIGUOUS, null, 1);
            }
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

    private static int firstMapBoundaryAtOrAfter(int coordinate) {
        int remainder = Math.floorMod(
            coordinate + ANCHOR_LENGTH / 2,
            ANCHOR_LENGTH
        );
        return remainder == 0
            ? coordinate
            : Math.addExact(coordinate, ANCHOR_LENGTH - remainder);
    }

    private static ProbeResult probeAnchor(
        int startX,
        int y,
        int z,
        Probe probe
    ) {
        boolean unavailable = false;
        for (int offset = 0; offset < ANCHOR_LENGTH; offset++) {
            Cell cell = Objects.requireNonNull(
                probe.probe(Math.addExact(startX, offset), y, z),
                "The centered walkway probe returned null."
            );
            if (cell == Cell.UNAVAILABLE) unavailable = true;
            if (cell != Cell.SAFE) {
                return new ProbeResult(false, unavailable);
            }
        }
        return new ProbeResult(true, false);
    }

    private static Integer mapCornerForAnchor(
        int anchorStartX,
        int mapColumns,
        int playerX
    ) {
        int anchorColumn;
        if (mapColumns % 2 != 0) {
            anchorColumn = mapColumns / 2;
        } else {
            long westDistance = Math.abs((long) playerX - anchorStartX);
            long eastSeam = Math.addExact(anchorStartX, ANCHOR_LENGTH);
            long eastDistance = Math.abs((long) playerX - eastSeam);
            if (westDistance == eastDistance) return null;
            anchorColumn = westDistance < eastDistance
                ? mapColumns / 2
                : mapColumns / 2 - 1;
        }
        return Math.subtractExact(
            anchorStartX,
            Math.multiplyExact(anchorColumn, ANCHOR_LENGTH)
        );
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

    private record ProbeResult(boolean safe, boolean unavailable) {
    }
}
