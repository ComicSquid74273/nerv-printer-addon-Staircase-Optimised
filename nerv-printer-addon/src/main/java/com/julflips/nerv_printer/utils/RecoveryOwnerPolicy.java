package com.julflips.nerv_printer.utils;

import java.util.Objects;

/**
 * Arbitrates the only subsystem allowed to move during restart recovery.
 */
public final class RecoveryOwnerPolicy {
    private RecoveryOwnerPolicy() {
    }

    public enum Owner {
        BUILD,
        MINING,
        LOGISTICS
    }

    public static Owner decide(
        MapCyclePhase phase,
        boolean buildingActive,
        boolean miningRuntimeActive
    ) {
        Objects.requireNonNull(phase, "phase");

        // Concrete mining assignment state is stronger than a stale build
        // flag. The persisted MINING lifecycle is likewise authoritative.
        if (miningRuntimeActive
            || phase == MapCyclePhase.MINING) {
            return Owner.MINING;
        }
        if (buildingActive) return Owner.BUILD;
        return Owner.LOGISTICS;
    }
}
