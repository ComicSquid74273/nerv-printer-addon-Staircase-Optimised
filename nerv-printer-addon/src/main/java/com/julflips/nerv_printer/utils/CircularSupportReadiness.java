package com.julflips.nerv_printer.utils;

/**
 * Classifies every reason a printed circular-route support may not yet be
 * walkable.
 *
 * <p>Missing or pending supports remain placement work. A present unexpected
 * support and blocked headroom are repair work. Keeping those states distinct
 * prevents a permanent obstruction from being treated as an unbounded
 * placement-confirmation wait.</p>
 */
public final class CircularSupportReadiness {
    private CircularSupportReadiness() {
    }

    public enum Status {
        READY,
        WAITING_FOR_SUPPORT,
        WRONG_SUPPORT,
        LOWER_HEADROOM_BLOCKED,
        UPPER_HEADROOM_BLOCKED
    }

    public record Assessment(Status status, int obstructionOffset) {
        public Assessment {
            if (status == null) {
                throw new NullPointerException("status");
            }
            int expectedOffset = switch (status) {
                case WRONG_SUPPORT -> 0;
                case LOWER_HEADROOM_BLOCKED -> 1;
                case UPPER_HEADROOM_BLOCKED -> 2;
                case READY, WAITING_FOR_SUPPORT -> -1;
            };
            if (obstructionOffset != expectedOffset) {
                throw new IllegalArgumentException(
                    "Support status and obstruction offset disagree."
                );
            }
        }

        public boolean ready() {
            return status == Status.READY;
        }

        public boolean repairRequired() {
            return obstructionOffset >= 0;
        }
    }

    public static Assessment assess(
        boolean supportAir,
        boolean expectedSupportPresent,
        boolean placementPending,
        boolean lowerHeadroomAir,
        boolean upperHeadroomAir
    ) {
        if (!supportAir && !expectedSupportPresent) {
            return new Assessment(Status.WRONG_SUPPORT, 0);
        }
        if (supportAir || placementPending) {
            return new Assessment(
                Status.WAITING_FOR_SUPPORT,
                -1
            );
        }
        if (!lowerHeadroomAir) {
            return new Assessment(
                Status.LOWER_HEADROOM_BLOCKED,
                1
            );
        }
        if (!upperHeadroomAir) {
            return new Assessment(
                Status.UPPER_HEADROOM_BLOCKED,
                2
            );
        }
        return new Assessment(Status.READY, -1);
    }
}
