package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircularBuildHorizonTest {
    @Test
    void exposesEveryForwardAssignedColumnToCapacityPlanning() {
        assertEquals(
            List.of(6, 7, 8, 9, 10),
            CircularBuildHorizon.forwardOptionalColumns(
                4,
                5,
                0,
                10,
                128
            )
        );
    }

    @Test
    void clipsTheForwardHorizonAtTheAssignedInterval() {
        assertEquals(
            List.of(6),
            CircularBuildHorizon.forwardOptionalColumns(
                4,
                5,
                4,
                6,
                128
            )
        );
    }

    @Test
    void rejectsMalformedPairAndIntervalInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildHorizon.forwardOptionalColumns(
                4,
                7,
                0,
                127,
                128
            )
        );
    }
}
