package com.julflips.nerv_printer.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * Immutable, structural key for an {@link ItemStack}'s item and components.
 *
 * <p>Minecraft's exposed {@code ComponentMap} implementations are snapshots,
 * but their object identity is not a valid cross-packet equality contract.
 * This key delegates comparison to Minecraft's own structural ItemStack
 * comparator instead.</p>
 */
public final class StructuralItemStackKey {
    private final ItemStack stack;

    private StructuralItemStackKey(
        ItemStack source,
        boolean ignoreDamage
    ) {
        Objects.requireNonNull(source, "source");
        stack = source.isEmpty()
            ? ItemStack.EMPTY
            : source.copyWithCount(1);
        if (!stack.isEmpty() && ignoreDamage) {
            stack.remove(DataComponentTypes.DAMAGE);
        }
    }

    public static StructuralItemStackKey exact(ItemStack stack) {
        return new StructuralItemStackKey(stack, false);
    }

    public static StructuralItemStackKey withoutDamage(
        ItemStack stack
    ) {
        return new StructuralItemStackKey(stack, true);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || (other instanceof StructuralItemStackKey key
                && ItemStack.areItemsAndComponentsEqual(
                    stack,
                    key.stack
                ));
    }

    @Override
    public int hashCode() {
        // Structural ItemStack hashing is not exposed. Item identity is a
        // stable lower-resolution hash that preserves the equals contract.
        return stack.isEmpty() ? 0 : stack.getItem().hashCode();
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
