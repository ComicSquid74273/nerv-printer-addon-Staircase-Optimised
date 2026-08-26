package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterTickActionGateTest {
    @Test
    void pendingAuthoritativeActionRunsBeforeRasterAndCanHoldTheTick() {
        AtomicBoolean processed = new AtomicBoolean();

        boolean allowed = RasterTickActionGate.allowRasterStateMachine(
            true,
            () -> {
                processed.set(true);
                return true;
            }
        );

        assertTrue(processed.get());
        assertFalse(allowed);
    }

    @Test
    void rasterRunsImmediatelyWhenNoActionIsPending() {
        AtomicBoolean processed = new AtomicBoolean();

        boolean allowed = RasterTickActionGate.allowRasterStateMachine(
            false,
            () -> {
                processed.set(true);
                return true;
            }
        );

        assertTrue(allowed);
        assertFalse(processed.get());
    }
}
