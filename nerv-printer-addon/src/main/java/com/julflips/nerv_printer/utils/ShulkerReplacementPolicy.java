package com.julflips.nerv_printer.utils;

/**
 * Pure decision policy for an emptied shulker station. Only observations newer
 * than the phase baseline may advance the station; this prevents local client
 * prediction or a stale cached block from confirming a destructive action.
 */
public final class ShulkerReplacementPolicy {
    private ShulkerReplacementPolicy() {
    }

    public enum Phase {
        BREAKING_EMPTY_BOX,
        WAITING_FOR_REPLACEMENT
    }

    public enum ObservedBlock {
        UNKNOWN,
        SHULKER,
        AIR,
        OTHER
    }

    public enum Decision {
        CONTINUE,
        EMPTY_BOX_CONFIRMED,
        REPLACEMENT_CONFIRMED,
        UNEXPECTED_BLOCK
    }

    public static Decision decide(
        Phase phase,
        boolean breakAttempted,
        long authoritativeBaseline,
        long observationSequence,
        ObservedBlock observedBlock
    ) {
        if (observationSequence <= authoritativeBaseline
            || observedBlock == ObservedBlock.UNKNOWN) {
            return Decision.CONTINUE;
        }
        if (observedBlock == ObservedBlock.OTHER) {
            return Decision.UNEXPECTED_BLOCK;
        }
        if (phase == Phase.WAITING_FOR_REPLACEMENT) {
            return observedBlock == ObservedBlock.SHULKER
                ? Decision.REPLACEMENT_CONFIRMED
                : Decision.CONTINUE;
        }
        if (observedBlock == ObservedBlock.AIR) {
            return Decision.EMPTY_BOX_CONFIRMED;
        }
        return breakAttempted && observedBlock == ObservedBlock.SHULKER
            ? Decision.REPLACEMENT_CONFIRMED
            : Decision.CONTINUE;
    }
}
