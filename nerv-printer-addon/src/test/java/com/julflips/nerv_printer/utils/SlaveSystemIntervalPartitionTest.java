package com.julflips.nerv_printer.utils;

import net.minecraft.util.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaveSystemIntervalPartitionTest {
    @Test
    void everySupportedBotCountOwnsEveryPairExactlyOnce() {
        for (int botCount = 1; botCount <= 64; botCount++) {
            List<Pair<Integer, Integer>> intervals =
                SlaveSystem.partitionCircularColumns(botCount);

            assertEquals(botCount, intervals.size());
            int nextColumn = 0;
            int ownedPairs = 0;
            for (Pair<Integer, Integer> interval : intervals) {
                int start = interval.getLeft();
                int end = interval.getRight();
                assertEquals(nextColumn, start);
                assertEquals(0, start % 2);
                assertEquals(1, end % 2);
                assertTrue(end >= start);
                ownedPairs += (end - start + 1) / 2;
                nextColumn = end + 1;
            }
            assertEquals(128, nextColumn);
            assertEquals(64, ownedPairs);
        }
    }

    @Test
    void rejectsImpossibleBotCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SlaveSystem.partitionCircularColumns(0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> SlaveSystem.partitionCircularColumns(65)
        );
    }
}
