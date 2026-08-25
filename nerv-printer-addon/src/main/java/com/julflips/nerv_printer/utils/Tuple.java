package com.julflips.nerv_printer.utils;

/**
 * Small immutable pair used by the printer's internal plans and configuration.
 * Minecraft removed its former {@code Tuple} helper in 26.2, so the add-on owns
 * this value type instead of depending on a game-internal utility class.
 */
public record Tuple<A, B>(A first, B second) {
    public A getA() {
        return first;
    }

    public B getB() {
        return second;
    }
}
