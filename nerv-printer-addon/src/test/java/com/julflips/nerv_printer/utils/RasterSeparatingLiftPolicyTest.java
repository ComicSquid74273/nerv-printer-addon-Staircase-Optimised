package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterSeparatingLiftPolicyTest {
    @Test
    void permitsMonotonicReleaseOfInitialSupportContacts() {
        Set<String> initial = Set.of("dock-left", "dock-right");
        assertTrue(RasterSeparatingLiftPolicy.shedsOnly(
            initial, initial
        ));
        assertTrue(RasterSeparatingLiftPolicy.shedsOnly(
            initial, Set.of("dock-left")
        ));
        assertTrue(RasterSeparatingLiftPolicy.shedsOnly(
            initial, Set.of()
        ));
    }

    @Test
    void rejectsEveryNewSideOrOverheadContact() {
        assertFalse(RasterSeparatingLiftPolicy.shedsOnly(
            Set.of("dock"), Set.of("dock", "ceiling")
        ));
        assertFalse(RasterSeparatingLiftPolicy.shedsOnly(
            Set.of(), Set.of()
        ));
    }
}
