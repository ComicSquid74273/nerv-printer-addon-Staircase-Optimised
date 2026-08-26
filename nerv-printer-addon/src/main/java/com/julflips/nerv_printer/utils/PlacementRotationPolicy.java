package com.julflips.nerv_printer.utils;

import java.util.Collection;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Identifies blocks whose placement result is derived from player yaw/pitch.
 *
 * <p>Properties such as waterlogging, leaf distance, wall connections, slab
 * half, or pillar axis do not need player rotation. They are driven by world
 * state or the packet's clicked face. Facing, orientation, and standing
 * rotation are the vanilla properties that actually consult player look
 * direction during placement.</p>
 */
public final class PlacementRotationPolicy {
    private static final Set<String> PLAYER_ROTATION_PROPERTY_NAMES =
        Set.of("facing", "orientation", "rotation");

    private PlacementRotationPolicy() {
    }

    public static boolean requiresPlayerRotation(BlockState targetState) {
        if (targetState == null) {
            throw new IllegalArgumentException(
                "Target block state is required."
            );
        }
        return requiresPlayerRotationNames(
            targetState.getProperties().stream()
                .map(property -> property.getName())
                .toList()
        );
    }

    static boolean requiresPlayerRotationNames(
        Collection<String> propertyNames
    ) {
        if (propertyNames == null) {
            throw new IllegalArgumentException(
                "Target property names are required."
            );
        }
        return propertyNames.stream()
            .anyMatch(PLAYER_ROTATION_PROPERTY_NAMES::contains);
    }
}
