package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RasterProgressTest {
    @Test
    void advancesOnlyAcrossContiguousConfirmedTargets() {
        List<Integer> targets = List.of(0, 1, 2, 3, 4);
        Set<Integer> confirmed = Set.of(0, 1, 3);
        RasterProgress.Snapshot result = RasterProgress.reconcile(
            targets,
            4,
            0,
            confirmed::contains
        );
        assertEquals(2, result.confirmedFrontier());
        assertEquals(2, result.firstUnfinished());
        assertEquals(4, result.cursor());
    }

    @Test
    void lookaheadSkipsAlreadyConfirmedHoles() {
        assertEquals(
            List.of(1, 3),
            RasterProgress.lookahead(
                List.of(0, 1, 2, 3, 4),
                0,
                2,
                Set.of(0, 2, 4)::contains
            )
        );
    }
}
