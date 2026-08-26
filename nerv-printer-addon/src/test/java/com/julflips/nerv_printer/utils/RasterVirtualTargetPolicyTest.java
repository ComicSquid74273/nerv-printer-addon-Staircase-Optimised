package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterVirtualTargetPolicyTest {
    @Test
    void virtualizesOnlyTheActiveRow() {
        assertTrue(RasterVirtualTargetPolicy.isActiveRowVirtualSolid(7, 7));
        assertFalse(RasterVirtualTargetPolicy.isActiveRowVirtualSolid(6, 7));
        assertFalse(RasterVirtualTargetPolicy.isActiveRowVirtualSolid(8, 7));
    }

    @Test
    void routeBandOwnsVirtualSolidsUntilExteriorTransitionCompletes() {
        assertEquals(
            6,
            RasterVirtualTargetPolicy.movementOwnedBand(7, 6, true)
        );
        assertEquals(
            7,
            RasterVirtualTargetPolicy.movementOwnedBand(7, 6, false)
        );
    }
}
