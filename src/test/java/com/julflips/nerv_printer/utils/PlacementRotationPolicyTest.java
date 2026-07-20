package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRotationPolicyTest {
    @Test
    void ordinaryFullBlocksDoNotRotate() {
        assertFalse(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of()
            )
        );
    }

    @Test
    void nonFacingStatePropertiesDoNotCauseRotation() {
        assertFalse(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of()
            )
        );
        assertFalse(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of(
                    "persistent",
                    "distance",
                    "waterlogged"
                )
            )
        );
        assertFalse(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of("axis")
            )
        );
    }

    @Test
    void facingAndStandingRotationPropertiesRequireRotation() {
        assertTrue(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of("facing")
            )
        );
        assertTrue(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of("waterlogged", "facing")
            )
        );
        assertTrue(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of("rotation")
            )
        );
        assertTrue(
            PlacementRotationPolicy.requiresPlayerRotationNames(
                List.of("orientation")
            )
        );
    }
}
