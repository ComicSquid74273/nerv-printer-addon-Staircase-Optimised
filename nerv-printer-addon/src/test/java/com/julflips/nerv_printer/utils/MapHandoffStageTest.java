package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapHandoffStageTest {
    @Test
    void lifecycleOnlyAcceptsProvableHandoffStages() {
        assertTrue(
            MapHandoffStage.NONE.isValidFor(MapCyclePhase.BUILDING)
        );
        assertTrue(
            MapHandoffStage.NEED_SUPPLIES.isValidFor(
                MapCyclePhase.MAP_HANDOFF
            )
        );
        assertTrue(
            MapHandoffStage.LOCKED_MAP_CONFIRMED.isValidFor(
                MapCyclePhase.MAP_HANDOFF
            )
        );
        assertTrue(
            MapHandoffStage.DEPOSIT_REQUESTED.isValidFor(
                MapCyclePhase.MAP_HANDOFF
            )
        );
        assertTrue(
            MapHandoffStage.DEPOSITED.isValidFor(
                MapCyclePhase.MINING
            )
        );
        assertTrue(
            MapHandoffStage.SKIPPED.isValidFor(
                MapCyclePhase.MINING
            )
        );

        assertFalse(
            MapHandoffStage.NONE.isValidFor(
                MapCyclePhase.MAP_HANDOFF
            )
        );
        assertFalse(
            MapHandoffStage.SUPPLIES_CONFIRMED.isValidFor(
                MapCyclePhase.MINING
            )
        );
        assertFalse(
            MapHandoffStage.DEPOSITED.isValidFor(
                MapCyclePhase.MAP_HANDOFF
            )
        );
    }

    @Test
    void mapIdsBecomeMandatoryAtTheCorrectBoundaries() {
        assertFalse(
            MapHandoffStage.SUPPLIES_CONFIRMED
                .requiresSourceMapId()
        );
        assertTrue(
            MapHandoffStage.SOURCE_MAP_CONFIRMED
                .requiresSourceMapId()
        );
        assertFalse(
            MapHandoffStage.SOURCE_MAP_CONFIRMED
                .requiresLockedMapId()
        );
        assertTrue(
            MapHandoffStage.LOCKED_MAP_CONFIRMED
                .requiresLockedMapId()
        );
    }

    @Test
    void durableMapIdentitiesMustExistAndBeDistinct() {
        assertTrue(
            MapHandoffStage.SUPPLIES_CONFIRMED
                .hasValidMapIds(null, null)
        );
        assertTrue(
            MapHandoffStage.SOURCE_MAP_CONFIRMED
                .hasValidMapIds(41, null)
        );
        assertTrue(
            MapHandoffStage.DEPOSIT_REQUESTED
                .hasValidMapIds(41, 42)
        );

        assertFalse(
            MapHandoffStage.SOURCE_MAP_CONFIRMED
                .hasValidMapIds(null, null)
        );
        assertFalse(
            MapHandoffStage.LOCKED_MAP_CONFIRMED
                .hasValidMapIds(41, null)
        );
        assertFalse(
            MapHandoffStage.DEPOSIT_REQUESTED
                .hasValidMapIds(41, 41)
        );
        assertFalse(
            MapHandoffStage.DEPOSITED
                .hasValidMapIds(-1, 42)
        );
    }
}
