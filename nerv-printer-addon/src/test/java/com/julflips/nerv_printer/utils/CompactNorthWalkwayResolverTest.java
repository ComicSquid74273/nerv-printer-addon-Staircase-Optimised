package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompactNorthWalkwayResolverTest {
    @Test
    void resolvesTheRealMinusOneProfileWithoutMovingTheMapOrigin() {
        int[] firstRow = new int[CompactCircularNbtPlan.MAP_WIDTH];
        Arrays.fill(firstRow, -1);
        firstRow[59] = 0;

        CompactNorthWalkwayResolver.Resolution resolution =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> relativeY == -1
                    ? CompactNorthWalkwayResolver.Cell.SAFE
                    : CompactNorthWalkwayResolver.Cell.UNSAFE
            );

        assertEquals(CompactNorthWalkwayResolver.Status.RESOLVED, resolution.status());
        assertEquals(-1, resolution.relativeY());
        assertEquals(196, 197 + resolution.relativeY());
        assertEquals(196, 197 + firstRow[0]);
        assertEquals(197, 197 + firstRow[59]);
    }

    @Test
    void retainsCompatibilityWithAPlatformAtTheVirtualBaseline() {
        int[] firstRow = new int[CompactCircularNbtPlan.MAP_WIDTH];

        CompactNorthWalkwayResolver.Resolution resolution =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> relativeY == 0
                    ? CompactNorthWalkwayResolver.Cell.SAFE
                    : CompactNorthWalkwayResolver.Cell.UNSAFE
            );

        assertEquals(CompactNorthWalkwayResolver.Status.RESOLVED, resolution.status());
        assertEquals(0, resolution.relativeY());
    }

    @Test
    void rejectsPartialBlockedUnavailableAndAmbiguousRows() {
        int[] firstRow = new int[CompactCircularNbtPlan.MAP_WIDTH];
        Arrays.fill(firstRow, -1);

        CompactNorthWalkwayResolver.Resolution partial =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> relativeY == -1 && x != 64
                    ? CompactNorthWalkwayResolver.Cell.SAFE
                    : CompactNorthWalkwayResolver.Cell.UNSAFE
            );
        assertEquals(CompactNorthWalkwayResolver.Status.NO_SAFE_ROW, partial.status());
        assertNull(partial.relativeY());

        CompactNorthWalkwayResolver.Resolution unavailable =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> x == 64
                    ? CompactNorthWalkwayResolver.Cell.UNAVAILABLE
                    : CompactNorthWalkwayResolver.Cell.SAFE
            );
        assertEquals(
            CompactNorthWalkwayResolver.Status.UNAVAILABLE,
            unavailable.status()
        );

        CompactNorthWalkwayResolver.Resolution ambiguous =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> relativeY == -2 || relativeY == 0
                    ? CompactNorthWalkwayResolver.Cell.SAFE
                    : CompactNorthWalkwayResolver.Cell.UNSAFE
            );
        assertEquals(CompactNorthWalkwayResolver.Status.AMBIGUOUS, ambiguous.status());
        assertEquals(2, ambiguous.safeRows().size());
    }

    @Test
    void rejectsProfilesWithNoCommonOneBlockEntryHeight() {
        int[] firstRow = new int[CompactCircularNbtPlan.MAP_WIDTH];
        Arrays.fill(firstRow, -2);
        firstRow[127] = 2;

        CompactNorthWalkwayResolver.Resolution resolution =
            CompactNorthWalkwayResolver.resolve(
                firstRow,
                0,
                127,
                (x, relativeY) -> CompactNorthWalkwayResolver.Cell.SAFE
            );

        assertEquals(
            CompactNorthWalkwayResolver.Status.NO_WALKABLE_HEIGHT,
            resolution.status()
        );
        assertNull(resolution.relativeY());
    }

    @Test
    void exposesCandidateHeightsForAutomaticWorldOriginSelection() {
        int[] firstRow = new int[CompactCircularNbtPlan.MAP_WIDTH];
        Arrays.fill(firstRow, 0);

        assertEquals(
            java.util.List.of(-1, 0, 1),
            CompactNorthWalkwayResolver.candidateHeights(firstRow)
        );
    }
}
