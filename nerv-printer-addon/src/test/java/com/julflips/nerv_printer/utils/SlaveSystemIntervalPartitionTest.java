package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.utils.Tuple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaveSystemIntervalPartitionTest {
    @Test
    void everySupportedBotCountOwnsEveryPairExactlyOnce() {
        for (int botCount = 1; botCount <= 64; botCount++) {
            List<Tuple<Integer, Integer>> intervals =
                SlaveSystem.partitionCircularColumns(botCount);

            assertEquals(botCount, intervals.size());
            int nextColumn = 0;
            int ownedPairs = 0;
            for (Tuple<Integer, Integer> interval : intervals) {
                int start = interval.getA();
                int end = interval.getB();
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

    @Test
    void partitionsAContiguousSixMapWideGridWithoutGaps() {
        int totalColumns = 6 * MapGridLayout.TILE_SIZE;
        List<Tuple<Integer, Integer>> intervals =
            SlaveSystem.partitionCircularColumns(7, totalColumns);

        assertEquals(7, intervals.size());
        assertEquals(0, intervals.getFirst().getA());
        assertEquals(totalColumns - 1, intervals.getLast().getB());
        for (int index = 0; index < intervals.size(); index++) {
            Tuple<Integer, Integer> interval = intervals.get(index);
            assertEquals(0, interval.getA() % 2);
            assertEquals(1, interval.getB() % 2);
            if (index > 0) {
                assertEquals(
                    intervals.get(index - 1).getB() + 1,
                    interval.getA()
                );
            }
        }
    }
}
