package com.julflips.nerv_printer.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coalesces high-frequency diagnostics independently by category.
 *
 * <p>The first message for a category is emitted immediately. Later messages
 * are suppressed until the configured interval has elapsed, at which point
 * the newest message is emitted together with the number of intermediate
 * messages that were coalesced.</p>
 */
public final class CategorizedDebugLogLimiter {
    private final Map<String, CategoryState> categories = new HashMap<>();

    public Optional<Emission> submit(
        long tick,
        int intervalTicks,
        String category,
        String message
    ) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (intervalTicks < 1) {
            throw new IllegalArgumentException(
                "intervalTicks must be at least one"
            );
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");

        CategoryState previous = categories.get(category);
        if (previous == null || tick < previous.lastEmissionTick()) {
            categories.put(category, new CategoryState(tick, 0));
            return Optional.of(new Emission(message, 0));
        }

        if (tick - previous.lastEmissionTick() >= intervalTicks) {
            categories.put(category, new CategoryState(tick, 0));
            return Optional.of(
                new Emission(message, previous.suppressedMessages())
            );
        }

        categories.put(
            category,
            new CategoryState(
                previous.lastEmissionTick(),
                Math.addExact(previous.suppressedMessages(), 1)
            )
        );
        return Optional.empty();
    }

    public void clear() {
        categories.clear();
    }

    public record Emission(String message, long suppressedMessages) {
        public Emission {
            Objects.requireNonNull(message, "message");
            if (suppressedMessages < 0) {
                throw new IllegalArgumentException(
                    "suppressedMessages must not be negative"
                );
            }
        }
    }

    private record CategoryState(
        long lastEmissionTick,
        long suppressedMessages
    ) {}
}
