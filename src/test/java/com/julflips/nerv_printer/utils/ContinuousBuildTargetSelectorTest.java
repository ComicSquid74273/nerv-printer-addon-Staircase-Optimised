package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousBuildTargetSelectorTest {
    @Test
    void selectsTheFirstMissingTargetAfterAFilledPrefix() {
        List<String> route = List.of("start", "turn-1", "turn-2", "end");
        Set<String> filled = Set.of("start", "turn-1");

        assertEquals(
            "turn-2",
            ContinuousBuildTargetSelector.firstMissing(
                route,
                0,
                ignored -> true,
                target -> !filled.contains(target)
            ).orElseThrow()
        );
    }

    @Test
    void connectorCursorDoesNotRevisitTargetsBeforeItsCurrentIndex() {
        List<String> route = List.of("old-missing", "current", "next");

        assertEquals(
            "current",
            ContinuousBuildTargetSelector.firstMissing(
                route,
                1,
                ignored -> true,
                ignored -> true
            ).orElseThrow()
        );
    }

    @Test
    void preservesTheSuppliedReverseTraversalOrder() {
        List<Integer> reverseRoute = List.of(128, 127, 126, 125);
        Set<Integer> filled = new HashSet<>(Set.of(128, 127));

        assertEquals(
            126,
            ContinuousBuildTargetSelector.firstMissing(
                reverseRoute,
                0,
                ignored -> true,
                target -> !filled.contains(target)
            ).orElseThrow()
        );
    }

    @Test
    void normalAndConnectorListsMakeTheSameSelection() {
        List<String> normalTargets = List.of("a", "b", "c", "d");
        List<String> connectorTargets = List.copyOf(normalTargets);
        Set<String> filled = Set.of("a", "b");

        String normal = ContinuousBuildTargetSelector.firstMissing(
            normalTargets,
            0,
            ignored -> true,
            target -> !filled.contains(target)
        ).orElseThrow();
        String connector = ContinuousBuildTargetSelector.firstMissing(
            connectorTargets,
            0,
            ignored -> true,
            target -> !filled.contains(target)
        ).orElseThrow();

        assertEquals(normal, connector);
    }

    @Test
    void looksAheadFromTheOutboundLegIntoTheConnector() {
        List<String> pairRoute = List.of(
            "outbound-1",
            "outbound-2",
            "connector-1",
            "connector-2",
            "return-2",
            "return-1"
        );
        Set<String> filled = Set.of("outbound-1", "outbound-2");

        assertEquals(
            "connector-1",
            ContinuousBuildTargetSelector.firstMissing(
                pairRoute,
                0,
                ignored -> true,
                target -> !filled.contains(target)
            ).orElseThrow()
        );
    }

    @Test
    void looksAheadFromTheConnectorIntoTheReturnLeg() {
        List<String> pairRoute = List.of(
            "outbound-1",
            "outbound-2",
            "connector-1",
            "connector-2",
            "return-2",
            "return-1"
        );
        Set<String> filled = Set.of(
            "outbound-1",
            "outbound-2",
            "connector-1",
            "connector-2"
        );

        assertEquals(
            "return-2",
            ContinuousBuildTargetSelector.firstMissing(
                pairRoute,
                0,
                ignored -> true,
                target -> !filled.contains(target)
            ).orElseThrow()
        );
    }

    @Test
    void boundedConnectorSelectionCannotRotateTowardALaterTurn() {
        List<String> pairRoute = List.of(
            "outbound",
            "current-connector",
            "later-turn",
            "return"
        );
        Set<String> filled = Set.of("outbound", "current-connector");

        assertTrue(
            ContinuousBuildTargetSelector.firstMissing(
                pairRoute,
                0,
                2,
                ignored -> true,
                target -> !filled.contains(target)
            ).isEmpty()
        );
    }

    @Test
    void boundedOutboundSelectionAllowsOnlyTheFirstConnectorStep() {
        List<String> pairRoute = List.of(
            "outbound",
            "first-connector",
            "later-turn",
            "return"
        );
        Set<String> filled = Set.of("outbound");

        assertEquals(
            "first-connector",
            ContinuousBuildTargetSelector.firstMissing(
                pairRoute,
                0,
                2,
                ignored -> true,
                target -> !filled.contains(target)
            ).orElseThrow()
        );
    }

    @Test
    void returnsEmptyWhenEveryEligibleTargetIsFilled() {
        assertTrue(
            ContinuousBuildTargetSelector.firstMissing(
                List.of("outside", "inside"),
                0,
                target -> target.equals("inside"),
                target -> target.equals("outside")
            ).isEmpty()
        );
    }

    @Test
    void rejectsAnInvalidCursor() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ContinuousBuildTargetSelector.firstMissing(
                List.of("target"),
                2,
                ignored -> true,
                ignored -> true
            )
        );
    }

    @Test
    void rejectsAnInvalidSelectionRange() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ContinuousBuildTargetSelector.firstMissing(
                List.of("target"),
                1,
                0,
                ignored -> true,
                ignored -> true
            )
        );
    }
}
