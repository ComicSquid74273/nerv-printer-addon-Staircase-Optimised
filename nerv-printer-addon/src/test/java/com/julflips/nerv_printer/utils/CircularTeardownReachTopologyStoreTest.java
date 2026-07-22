package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularTeardownReachTopologyStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsAReachProfile() throws IOException {
        String hash = "b".repeat(64);
        CircularTeardownReachTopology.Snapshot snapshot =
            CircularTeardownReachTopology.compile(
                hash,
                List.of(
                    route(0, 0),
                    route(1, 1)
                ),
                1.62,
                5.0
            );
        Path file = CircularTeardownReachTopologyStore.pathFor(
            temporaryDirectory,
            hash,
            1.62,
            5.0
        );

        CircularTeardownReachTopologyStore.save(file, snapshot);

        assertEquals(
            snapshot,
            CircularTeardownReachTopologyStore.read(
                file,
                hash,
                1.62,
                5.0
            )
        );
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void rejectsAnotherReachProfile() throws IOException {
        String hash = "c".repeat(64);
        CircularTeardownReachTopology.Snapshot snapshot =
            CircularTeardownReachTopology.compile(
                hash,
                List.of(route(0, 0), route(1, 1)),
                1.62,
                5.0
            );
        Path file = CircularTeardownReachTopologyStore.pathFor(
            temporaryDirectory,
            hash,
            1.62,
            5.0
        );
        CircularTeardownReachTopologyStore.save(file, snapshot);

        assertThrows(
            IOException.class,
            () -> CircularTeardownReachTopologyStore.read(
                file,
                hash,
                1.62,
                4.8
            )
        );
    }

    private static CircularTeardownReachTopology.Route route(
        int routeIndex,
        int x
    ) {
        return new CircularTeardownReachTopology.Route(
            routeIndex,
            List.of(
                new BlockReachWindow.Cell(x, 64, 0),
                new BlockReachWindow.Cell(x, 64, 1)
            )
        );
    }
}
