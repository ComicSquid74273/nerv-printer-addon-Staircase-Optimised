package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCyclePhaseTest {
    @Test
    void noPreClearPhaseCanArchive() {
        EnumSet.of(
            MapCyclePhase.IDLE,
            MapCyclePhase.BUILDING,
            MapCyclePhase.MAP_HANDOFF,
            MapCyclePhase.MAP_DEPOSITED,
            MapCyclePhase.MINING
        ).forEach(phase ->
            assertFalse(phase.canArchive(), () -> phase + " must not archive")
        );
    }

    @Test
    void verifiedClearCanArchive() {
        assertTrue(MapCyclePhase.VERIFIED_CLEAR.canArchive());
    }

    @Test
    void postMiningCannotRearchive() {
        assertFalse(MapCyclePhase.POST_MINING.canArchive());
    }

    @Test
    void everyNonIdlePhasePreservesReconnectState() {
        for (MapCyclePhase phase : MapCyclePhase.values()) {
            if (phase == MapCyclePhase.IDLE) {
                assertFalse(phase.isInProgress());
            } else {
                assertTrue(
                    phase.isInProgress(),
                    () -> phase + " must preserve reconnect state"
                );
            }
        }
    }

    @Test
    void verifiedClearIsTheOnlyArchivablePhase() {
        for (MapCyclePhase phase : MapCyclePhase.values()) {
            if (phase == MapCyclePhase.VERIFIED_CLEAR) {
                assertTrue(phase.canArchive());
            } else {
                assertFalse(
                    phase.canArchive(),
                    () -> phase + " must not archive"
                );
            }
        }
    }
}
