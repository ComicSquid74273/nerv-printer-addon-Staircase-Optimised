package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildPlacementPolicyTest {
    @Test
    void usesAdjacentPlacementWhenAConfirmedFaceExists() {
        assertEquals(
            BuildPlacementPolicy.Mode.ADJACENT,
            BuildPlacementPolicy.select(
                true,
                true,
                true,
                true,
                false,
                true,
                true
            )
        );
    }

    @Test
    void authorizedTargetUsesSmartAirPlacementWithoutAFace() {
        assertEquals(
            BuildPlacementPolicy.Mode.SMART_AIR,
            BuildPlacementPolicy.select(
                true,
                true,
                true,
                false,
                false,
                true,
                true
            )
        );
    }

    @Test
    void nonRotatingTargetUsesSmartAirEvenWithAdjacentFace() {
        assertEquals(
            BuildPlacementPolicy.Mode.SMART_AIR,
            BuildPlacementPolicy.select(
                true,
                true,
                true,
                true,
                true,
                true,
                false
            )
        );
    }

    @Test
    void unauthorizedTargetCannotUseSmartAirPlacement() {
        assertEquals(
            BuildPlacementPolicy.Mode.BLOCKED,
            BuildPlacementPolicy.select(
                true,
                true,
                true,
                false,
                false,
                false,
                false
            )
        );
    }

    @Test
    void pendingAdjacentSupportBlocksDependentSubmission() {
        assertEquals(
            BuildPlacementPolicy.Mode.BLOCKED,
            BuildPlacementPolicy.select(
                true,
                true,
                true,
                true,
                true,
                true,
                true
            )
        );
    }

    @Test
    void worldAndReachChecksRemainHardRequirements() {
        assertEquals(
            BuildPlacementPolicy.Mode.BLOCKED,
            BuildPlacementPolicy.select(
                false,
                true,
                true,
                false,
                false,
                true,
                false
            )
        );
        assertEquals(
            BuildPlacementPolicy.Mode.BLOCKED,
            BuildPlacementPolicy.select(
                true,
                false,
                true,
                false,
                false,
                true,
                false
            )
        );
        assertEquals(
            BuildPlacementPolicy.Mode.BLOCKED,
            BuildPlacementPolicy.select(
                true,
                true,
                false,
                false,
                false,
                true,
                false
            )
        );
    }
}
