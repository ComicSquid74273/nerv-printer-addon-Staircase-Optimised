package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildAssignmentPolicyTest {
    @Test
    void enabledAssignedPairUsesCircularModeWhenItFits() {
        assertTrue(
            CircularBuildAssignmentPolicy.useCircular(true, true, true)
        );
    }

    @Test
    void disabledUnassignedOrOversizedPairUsesIndependentMode() {
        assertFalse(
            CircularBuildAssignmentPolicy.useCircular(true, false, true)
        );
        assertFalse(
            CircularBuildAssignmentPolicy.useCircular(false, true, true)
        );
        assertFalse(
            CircularBuildAssignmentPolicy.useCircular(true, true, false)
        );
    }
}
