package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildHeightSelectorTest {
    @Test
    void choosesLeastClearingThenClosestThenLowest() {
        BuildHeightSelector.Candidate selected = BuildHeightSelector.select(
            List.of(
                new BuildHeightSelector.Candidate(40, 4, true, true, true),
                new BuildHeightSelector.Candidate(80, 2, true, true, true),
                new BuildHeightSelector.Candidate(70, 2, true, true, true),
                new BuildHeightSelector.Candidate(65, 0, false, true, true)
            ),
            72
        ).orElseThrow();
        assertEquals(70, selected.baseY());
    }

    @Test
    void rejectsUnavailableCandidates() {
        assertTrue(BuildHeightSelector.select(
            List.of(
                new BuildHeightSelector.Candidate(0, 0, false, true, true),
                new BuildHeightSelector.Candidate(1, 0, true, false, true),
                new BuildHeightSelector.Candidate(2, 0, true, true, false)
            ),
            0
        ).isEmpty());
    }

    @Test
    void choosesTheLowerHeightAfterAnExactScoreAndDistanceTie() {
        BuildHeightSelector.Candidate selected = BuildHeightSelector.select(
            List.of(
                new BuildHeightSelector.Candidate(66, 3, true, true, true),
                new BuildHeightSelector.Candidate(62, 3, true, true, true)
            ),
            64
        ).orElseThrow();
        assertEquals(62, selected.baseY());
    }
}
