package com.julflips.nerv_printer.utils;

/** Small immutable pair retained for configuration and route data. */
public record Tuple<A, B>(A a, B b) {
    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}
