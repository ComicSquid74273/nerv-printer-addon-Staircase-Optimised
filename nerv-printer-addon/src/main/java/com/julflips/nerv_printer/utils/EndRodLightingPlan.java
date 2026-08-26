package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Produces a sparse, validated end-rod lighting layout above a heightfield.
 *
 * <p>The model deliberately proves a clear rectilinear path through the air
 * above the surface instead of relying only on horizontal distance. This
 * makes steep staircasing conservative: every visible surface cell receives
 * at least block light one from a rod in the returned plan.</p>
 */
public final class EndRodLightingPlan {
    public static final int END_ROD_LIGHT_LEVEL = 14;
    public static final int HEIGHT_ABOVE_SURFACE = 4;
    public static final int MINIMUM_SURFACE_LIGHT = 1;
    public static final int MAXIMUM_LIGHT_PATH =
        END_ROD_LIGHT_LEVEL - MINIMUM_SURFACE_LIGHT;
    public static final int FLAT_GRID_SPACING =
        END_ROD_LIGHT_LEVEL
            - (HEIGHT_ABOVE_SURFACE - 1)
            - MINIMUM_SURFACE_LIGHT;

    private EndRodLightingPlan() {
    }

    public record Rod(int x, int y, int z) {
    }

    public record Result(List<Rod> rods, int minimumGuaranteedLight) {
        public Result {
            rods = List.copyOf(rods);
            if (minimumGuaranteedLight < MINIMUM_SURFACE_LIGHT) {
                throw new IllegalArgumentException(
                    "The lighting result does not mob-proof the surface."
                );
            }
        }
    }

    public static Result generate(int[][] surfaceY) {
        validateSurface(surfaceY);
        int width = surfaceY.length;
        int depth = surfaceY[0].length;
        RodIndex index = new RodIndex(width, depth);

        List<Integer> seedXs = seedAxis(width);
        List<Integer> seedZs = seedAxis(depth);
        for (int x : seedXs) {
            for (int z : seedZs) {
                index.add(rodAt(surfaceY, x, z));
            }
        }

        // Terrain cliffs and repeated staircase rises can consume more light
        // than the flat-grid estimate. Fill only cells that are not already
        // proven to receive non-zero block light.
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (guaranteedLightAt(surfaceY, x, z, index) <= 0) {
                    index.add(rodAt(surfaceY, x, z));
                }
            }
        }

        int minimumLight = END_ROD_LIGHT_LEVEL;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int light = guaranteedLightAt(surfaceY, x, z, index);
                if (light < MINIMUM_SURFACE_LIGHT) {
                    throw new IllegalArgumentException(
                        "End-rod lighting validation failed at X=" + x
                            + ", Z=" + z + "."
                    );
                }
                minimumLight = Math.min(minimumLight, light);
            }
        }
        return new Result(index.rods(), minimumLight);
    }

    static int guaranteedLightAt(
        int[][] surfaceY,
        int targetX,
        int targetZ,
        List<Rod> rods
    ) {
        validateSurface(surfaceY);
        Objects.requireNonNull(rods, "rods");
        int shortest = Integer.MAX_VALUE;
        for (Rod rod : rods) {
            if (Math.abs(rod.x() - targetX)
                    + Math.abs(rod.z() - targetZ)
                > MAXIMUM_LIGHT_PATH) {
                continue;
            }
            shortest = Math.min(
                shortest,
                safePathLength(surfaceY, rod, targetX, targetZ)
            );
        }
        return shortest == Integer.MAX_VALUE
            ? 0
            : Math.max(0, END_ROD_LIGHT_LEVEL - shortest);
    }

    private static int guaranteedLightAt(
        int[][] surfaceY,
        int targetX,
        int targetZ,
        RodIndex index
    ) {
        int shortest = Integer.MAX_VALUE;
        for (Rod rod : index.near(targetX, targetZ)) {
            int horizontal = Math.abs(rod.x() - targetX)
                + Math.abs(rod.z() - targetZ);
            if (horizontal > MAXIMUM_LIGHT_PATH) continue;
            shortest = Math.min(
                shortest,
                safePathLength(surfaceY, rod, targetX, targetZ)
            );
        }
        return shortest == Integer.MAX_VALUE
            ? 0
            : Math.max(0, END_ROD_LIGHT_LEVEL - shortest);
    }

    private static int safePathLength(
        int[][] surfaceY,
        Rod rod,
        int targetX,
        int targetZ
    ) {
        int targetY = surfaceY[targetX][targetZ] + 1;
        int xThenZClearance = Math.max(
            maximumClearanceAlongX(
                surfaceY,
                rod.x(),
                targetX,
                rod.z()
            ),
            maximumClearanceAlongZ(
                surfaceY,
                rod.z(),
                targetZ,
                targetX
            )
        );
        int zThenXClearance = Math.max(
            maximumClearanceAlongZ(
                surfaceY,
                rod.z(),
                targetZ,
                rod.x()
            ),
            maximumClearanceAlongX(
                surfaceY,
                rod.x(),
                targetX,
                targetZ
            )
        );
        int horizontal = Math.abs(rod.x() - targetX)
            + Math.abs(rod.z() - targetZ);
        return Math.min(
            pathLength(
                horizontal,
                rod.y(),
                targetY,
                xThenZClearance
            ),
            pathLength(
                horizontal,
                rod.y(),
                targetY,
                zThenXClearance
            )
        );
    }

    private static int pathLength(
        int horizontal,
        int sourceY,
        int targetY,
        int surfaceClearanceY
    ) {
        int travelY = Math.max(
            Math.max(sourceY, targetY),
            surfaceClearanceY
        );
        return horizontal
            + travelY - sourceY
            + travelY - targetY;
    }

    private static int maximumClearanceAlongX(
        int[][] surfaceY,
        int fromX,
        int toX,
        int z
    ) {
        int maximum = Integer.MIN_VALUE;
        int step = Integer.compare(toX, fromX);
        for (int x = fromX; ; x += step) {
            maximum = Math.max(maximum, surfaceY[x][z] + 1);
            if (x == toX) return maximum;
        }
    }

    private static int maximumClearanceAlongZ(
        int[][] surfaceY,
        int fromZ,
        int toZ,
        int x
    ) {
        int maximum = Integer.MIN_VALUE;
        int step = Integer.compare(toZ, fromZ);
        for (int z = fromZ; ; z += step) {
            maximum = Math.max(maximum, surfaceY[x][z] + 1);
            if (z == toZ) return maximum;
        }
    }

    private static Rod rodAt(int[][] surfaceY, int x, int z) {
        return new Rod(
            x,
            Math.addExact(
                surfaceY[x][z],
                HEIGHT_ABOVE_SURFACE
            ),
            z
        );
    }

    private static List<Integer> seedAxis(int length) {
        ArrayList<Integer> result = new ArrayList<>();
        int last = length - 1;
        int first = Math.min(FLAT_GRID_SPACING / 2, last);
        for (int position = first;
             position <= last;
             position += FLAT_GRID_SPACING) {
            result.add(position);
        }
        if (last - result.getLast() > FLAT_GRID_SPACING / 2) {
            result.add(last);
        }
        return List.copyOf(result);
    }

    private static void validateSurface(int[][] surfaceY) {
        Objects.requireNonNull(surfaceY, "surfaceY");
        if (surfaceY.length == 0 || surfaceY[0] == null
            || surfaceY[0].length == 0) {
            throw new IllegalArgumentException(
                "The lighting surface must not be empty."
            );
        }
        int depth = surfaceY[0].length;
        for (int x = 0; x < surfaceY.length; x++) {
            if (surfaceY[x] == null || surfaceY[x].length != depth) {
                throw new IllegalArgumentException(
                    "The lighting surface must be rectangular."
                );
            }
        }
    }

    private static final class RodIndex {
        private static final int BUCKET_SIZE = MAXIMUM_LIGHT_PATH + 1;
        private final ArrayList<Rod> rods = new ArrayList<>();
        private final Set<Long> horizontalPositions = new HashSet<>();
        private final ArrayList<Rod>[][] buckets;

        @SuppressWarnings("unchecked")
        private RodIndex(int width, int depth) {
            buckets = new ArrayList[
                (width + BUCKET_SIZE - 1) / BUCKET_SIZE
            ][
                (depth + BUCKET_SIZE - 1) / BUCKET_SIZE
            ];
        }

        private void add(Rod rod) {
            long key = ((long) rod.x() << 32)
                ^ (rod.z() & 0xffffffffL);
            if (!horizontalPositions.add(key)) return;
            rods.add(rod);
            int bucketX = rod.x() / BUCKET_SIZE;
            int bucketZ = rod.z() / BUCKET_SIZE;
            ArrayList<Rod> bucket = buckets[bucketX][bucketZ];
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets[bucketX][bucketZ] = bucket;
            }
            bucket.add(rod);
        }

        private List<Rod> near(int x, int z) {
            int centerX = x / BUCKET_SIZE;
            int centerZ = z / BUCKET_SIZE;
            ArrayList<Rod> result = new ArrayList<>();
            for (int bucketX = Math.max(0, centerX - 1);
                 bucketX <= Math.min(
                     buckets.length - 1,
                     centerX + 1
                 );
                 bucketX++) {
                for (int bucketZ = Math.max(0, centerZ - 1);
                     bucketZ <= Math.min(
                         buckets[0].length - 1,
                         centerZ + 1
                     );
                     bucketZ++) {
                    List<Rod> bucket = buckets[bucketX][bucketZ];
                    if (bucket != null) result.addAll(bucket);
                }
            }
            return result;
        }

        private List<Rod> rods() {
            return List.copyOf(rods);
        }
    }
}
