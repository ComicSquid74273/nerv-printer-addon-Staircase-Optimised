package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorizedDebugLogLimiterTest {
    @Test
    void emitsFirstMessageImmediatelyAndCoalescesUntilInterval() {
        CategorizedDebugLogLimiter limiter =
            new CategorizedDebugLogLimiter();

        CategorizedDebugLogLimiter.Emission first = limiter.submit(
            0,
            200,
            "Movement",
            "started"
        ).orElseThrow();
        assertEquals("started", first.message());
        assertEquals(0, first.suppressedMessages());

        assertTrue(limiter.submit(
            20,
            200,
            "Movement",
            "walking"
        ).isEmpty());
        assertTrue(limiter.submit(
            199,
            200,
            "Movement",
            "still walking"
        ).isEmpty());

        CategorizedDebugLogLimiter.Emission periodic = limiter.submit(
            200,
            200,
            "Movement",
            "checkpoint"
        ).orElseThrow();
        assertEquals("checkpoint", periodic.message());
        assertEquals(2, periodic.suppressedMessages());
    }

    @Test
    void categoriesHaveIndependentIntervals() {
        CategorizedDebugLogLimiter limiter =
            new CategorizedDebugLogLimiter();

        assertTrue(limiter.submit(
            10,
            500,
            "Movement",
            "walking"
        ).isPresent());
        assertTrue(limiter.submit(
            10,
            500,
            "Placement",
            "submitted"
        ).isPresent());
        assertTrue(limiter.submit(
            20,
            500,
            "Movement",
            "walking again"
        ).isEmpty());
    }

    @Test
    void clearAllowsAFormerCategoryToEmitImmediately() {
        CategorizedDebugLogLimiter limiter =
            new CategorizedDebugLogLimiter();
        limiter.submit(0, 200, "Restock", "started");
        assertTrue(limiter.submit(
            10,
            200,
            "Restock",
            "waiting"
        ).isEmpty());

        limiter.clear();

        assertTrue(limiter.submit(
            11,
            200,
            "Restock",
            "resumed"
        ).isPresent());
    }

    @Test
    void rejectsInvalidClockAndInterval() {
        CategorizedDebugLogLimiter limiter =
            new CategorizedDebugLogLimiter();

        assertThrows(
            IllegalArgumentException.class,
            () -> limiter.submit(-1, 200, "A", "message")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> limiter.submit(0, 0, "A", "message")
        );
    }
}
