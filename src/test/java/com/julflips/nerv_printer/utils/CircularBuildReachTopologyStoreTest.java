package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircularBuildReachTopologyStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsCompiledPerBlockWindows()
        throws IOException {
        String hash = "e".repeat(64);
        CircularBuildReachTopology.Snapshot snapshot =
            CircularBuildReachTopology.compile(
                hash,
                List.of(route(0, 0), route(1, 2)),
                1.62,
                5.0
            );
        Path file = CircularBuildReachTopologyStore.pathFor(
            temporaryDirectory,
            hash,
            1.62,
            5.0
        );

        CircularBuildReachTopologyStore.save(file, snapshot);

        assertEquals(
            snapshot,
            CircularBuildReachTopologyStore.read(
                file,
                hash,
                1.62,
                5.0
            )
        );
        assertThrows(
            IOException.class,
            () -> CircularBuildReachTopologyStore.read(
                file,
                hash,
                1.62,
                4.9
            )
        );
    }

    private static CircularBuildReachTopology.Route route(
        int routeIndex,
        int x
    ) {
        List<BlockReachWindow.Cell> cells = List.of(
            new BlockReachWindow.Cell(x, 64, 0),
            new BlockReachWindow.Cell(x, 64, 1)
        );
        return new CircularBuildReachTopology.Route(
            routeIndex,
            cells,
            cells
        );
    }
}
