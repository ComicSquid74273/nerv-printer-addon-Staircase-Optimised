package com.julflips.nerv_printer.utils;

import java.util.Objects;
import java.util.Set;

/** Pure invariant for a vertical lift away from launch-support contacts. */
public final class RasterSeparatingLiftPolicy {
    private RasterSeparatingLiftPolicy() {
    }

    public static <T> boolean shedsOnly(
        Set<T> initialContacts,
        Set<T> currentContacts
    ) {
        Objects.requireNonNull(initialContacts, "initialContacts");
        Objects.requireNonNull(currentContacts, "currentContacts");
        return !initialContacts.isEmpty()
            && initialContacts.containsAll(currentContacts);
    }
}
