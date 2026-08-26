package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterRowMaterialBudgetTest {
    @Test
    void requiresEnoughOfEveryMaterialToFinishTheRow() {
        assertEquals("white", RasterRowMaterialBudget.firstShortage(
            List.of("white", "black", "white", "gray"),
            Map.of("white", 1, "black", 8, "gray", 8)
        ).orElseThrow());
    }

    @Test
    void acceptsACompleteRowReserve() {
        assertTrue(RasterRowMaterialBudget.firstShortage(
            List.of("white", "black", "white", "gray"),
            Map.of("white", 2, "black", 1, "gray", 1)
        ).isEmpty());
    }
}
