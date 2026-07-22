package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfirmedBuildInventoryDemandTest {
    @Test
    void onlyLiveAuthoritativeConfirmationsReleaseStrictDemand() {
        ConfirmedBuildInventoryDemand.Result<String, String> result =
            ConfirmedBuildInventoryDemand.resolve(
                List.of(
                    target("preexisting", "white"),
                    target("confirmed", "black"),
                    target("changed", "cobble")
                ),
                Set.of("confirmed", "changed"),
                key -> key.equals("confirmed")
            );

        assertEquals(
            List.of("preexisting", "changed"),
            result.outstandingKeys()
        );
        assertEquals(
            List.of("white", "cobble"),
            result.outstandingMaterials()
        );
        assertEquals(List.of("confirmed"), result.releasedKeys());
    }

    @Test
    void preservesRouteOrderAndRejectsDuplicateKeys() {
        ConfirmedBuildInventoryDemand.Result<String, String> result =
            ConfirmedBuildInventoryDemand.resolve(
                List.of(
                    target("first", "a"),
                    target("second", "b")
                ),
                Set.of(),
                ignored -> true
            );
        assertEquals(
            List.of("first", "second"),
            result.outstandingKeys()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> ConfirmedBuildInventoryDemand.resolve(
                List.of(
                    target("same", "a"),
                    target("same", "b")
                ),
                Set.of(),
                ignored -> false
            )
        );
    }

    private static ConfirmedBuildInventoryDemand.Target<String, String>
        target(String key, String material) {
        return new ConfirmedBuildInventoryDemand.Target<>(key, material);
    }
}
