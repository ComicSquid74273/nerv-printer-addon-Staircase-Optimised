package com.julflips.nerv_printer.utils;

/** Timing and coverage policy for recovering an incomplete automatic supply scan. */
public final class AutomaticShulkerRescanPolicy {
    public static final int RETRY_INTERVAL_TICKS = 40;
    public static final int KNOWN_STATION_REINSPECTION_PASSES = 5;

    private AutomaticShulkerRescanPolicy() {
    }

    public static long nextRetryTick(long currentTick) {
        return Math.addExact(currentTick, RETRY_INTERVAL_TICKS);
    }

    public static boolean shouldReinspectKnownStations(int rescanPass) {
        return rescanPass > 0
            && rescanPass % KNOWN_STATION_REINSPECTION_PASSES == 0;
    }
}
