package com.julflips.nerv_printer.utils;

import java.util.Collection;
import java.util.Objects;

/**
 * Pure inventory-capacity arithmetic shared by runtime planning and tests.
 */
public final class InventoryCapacity {
    private InventoryCapacity() {
    }

    public record Requirement(int amount, int maximumStackSize) {
        public Requirement {
            if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative.");
            if (maximumStackSize <= 0) {
                throw new IllegalArgumentException("Maximum stack size must be positive.");
            }
        }
    }

    public static int slotsForAmount(int amount, int maximumStackSize) {
        return slotsRequired(java.util.List.of(new Requirement(amount, maximumStackSize)));
    }

    public static int slotsRequired(Collection<Requirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        long slots = 0;
        for (Requirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirement");
            slots += ((long) requirement.amount() + requirement.maximumStackSize() - 1)
                / requirement.maximumStackSize();
            if (slots > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Required slot count is too large.");
            }
        }
        return (int) slots;
    }
}
