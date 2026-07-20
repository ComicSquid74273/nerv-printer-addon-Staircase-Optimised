package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBuildSupportPathTest {
    @Test
    void keepsBackwardAlignmentAndBothStructuralEndpointsInOrder() {
        assertEquals(
            List.of(
                "alignment-z-minus-2",
                "outbound-walkway-z-minus-1",
                "outbound-0",
                "outbound-1",
                "connector",
                "return-1",
                "return-0",
                "return-walkway-z-minus-1"
            ),
            CircularBuildSupportPath.create(
                "alignment-z-minus-2",
                "outbound-walkway-z-minus-1",
                List.of(
                    "outbound-0",
                    "outbound-1",
                    "connector",
                    "return-1",
                    "return-0"
                ),
                "return-walkway-z-minus-1"
            )
        );
    }

    @Test
    void rejectsMissingOrDuplicateRouteSupports() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildSupportPath.create(
                "alignment",
                "walkway",
                List.of(),
                "return"
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildSupportPath.create(
                "walkway",
                "walkway",
                List.of("target"),
                "return"
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CircularBuildSupportPath.create(
                "alignment",
                "walkway",
                List.of("target", "target"),
                "return"
            )
        );
    }

    @Test
    void movementDependsOnlyOnTheImmediateNextSupport() {
        List<String> path = List.of(
            "alignment",
            "walkway",
            "first",
            "second",
            "exit"
        );
        Set<String> confirmed = Set.of(
            "alignment",
            "walkway",
            "first",
            "exit"
        );

        CircularBuildSupportPath.MovementDecision<String> fromAlignment =
            CircularBuildSupportPath.decideMovement(
                path,
                "alignment",
                confirmed::contains
            );
        assertEquals(
            CircularBuildSupportPath.MovementStatus.READY,
            fromAlignment.status()
        );
        assertEquals("walkway", fromAlignment.requiredSupport());

        CircularBuildSupportPath.MovementDecision<String> fromFirst =
            CircularBuildSupportPath.decideMovement(
                path,
                "first",
                confirmed::contains
            );
        assertEquals(
            CircularBuildSupportPath.MovementStatus
                .WAITING_FOR_NEXT_SUPPORT,
            fromFirst.status()
        );
        assertEquals("second", fromFirst.requiredSupport());
    }

    @Test
    void reportsEndAndOffPathWithoutInventingASupport() {
        List<String> path =
            List.of("alignment", "walkway", "target", "exit");

        assertEquals(
            CircularBuildSupportPath.MovementStatus.COMPLETE,
            CircularBuildSupportPath.decideMovement(
                path,
                "exit",
                ignored -> false
            ).status()
        );
        assertEquals(
            CircularBuildSupportPath.MovementStatus.OFF_PATH,
            CircularBuildSupportPath.decideMovement(
                path,
                "unknown",
                ignored -> true
            ).status()
        );
        assertEquals(
            CircularBuildSupportPath.MovementStatus.OFF_PATH,
            CircularBuildSupportPath.decideMovement(
                path,
                -1,
                ignored -> true
            ).status()
        );
    }

    @Test
    void restartCanReplanFromAlignmentOrEitherNorthWalkway() {
        List<String> path = List.of(
            "alignment",
            "outbound-walkway",
            "outbound-target",
            "connector",
            "return-target",
            "return-walkway"
        );

        assertTrue(
            CircularBuildSupportPath.isDirectReplanSupport(
                path,
                "alignment"
            )
        );
        assertTrue(
            CircularBuildSupportPath.isDirectReplanSupport(
                path,
                "outbound-walkway"
            )
        );
        assertTrue(
            CircularBuildSupportPath.isDirectReplanSupport(
                path,
                "return-walkway"
            )
        );
        assertFalse(
            CircularBuildSupportPath.isDirectReplanSupport(
                path,
                "outbound-target"
            )
        );
    }
}
