package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintingOnlyConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsReusableStationGeometry()
        throws IOException {
        PrintingOnlyConfigStore store =
            PrintingOnlyConfigStore.open(temporaryDirectory);
        PrintingOnlyConfigStore.Snapshot snapshot = validSnapshot();

        store.save(snapshot);

        assertEquals(Optional.of(snapshot), store.read());
        assertEquals(
            temporaryDirectory.toAbsolutePath().normalize()
                .resolve(PrintingOnlyConfigStore.DIRECTORY_NAME)
                .resolve(PrintingOnlyConfigStore.FILE_NAME),
            store.configFile()
        );
        assertTrue(Files.isRegularFile(store.configFile()));
        assertFalse(
            Files.exists(
                store.configFile().resolveSibling(
                    PrintingOnlyConfigStore.FILE_NAME + ".tmp"
                )
            )
        );
    }

    @Test
    void environmentMatchRequiresServerDimensionAndGrid() {
        PrintingOnlyConfigStore.Snapshot snapshot = validSnapshot();

        assertTrue(PrintingOnlyConfigStore.matchesEnvironment(
            snapshot,
            "server.example:25565",
            "minecraft:overworld",
            6,
            10
        ));
        assertFalse(PrintingOnlyConfigStore.matchesEnvironment(
            snapshot,
            "other.example:25565",
            "minecraft:overworld",
            6,
            10
        ));
        assertFalse(PrintingOnlyConfigStore.matchesEnvironment(
            snapshot,
            "server.example:25565",
            "minecraft:the_nether",
            6,
            10
        ));
        assertFalse(PrintingOnlyConfigStore.matchesEnvironment(
            snapshot,
            "server.example:25565",
            "minecraft:overworld",
            5,
            7
        ));
    }

    @Test
    void malformedConfigFailsClosedInsteadOfLookingAbsent()
        throws IOException {
        PrintingOnlyConfigStore store =
            PrintingOnlyConfigStore.open(temporaryDirectory);
        Files.writeString(store.configFile(), "{not-json");

        assertThrows(IOException.class, store::read);
    }

    @Test
    void refusesStationOutsideSavedHorizontalOrYScanBox()
        throws IOException {
        PrintingOnlyConfigStore store =
            PrintingOnlyConfigStore.open(temporaryDirectory);
        PrintingOnlyConfigStore.Snapshot valid = validSnapshot();
        PrintingOnlyConfigStore.Station outside = station(
            181,
            67,
            200,
            181.5,
            67,
            201.5
        );
        PrintingOnlyConfigStore.Snapshot invalid =
            new PrintingOnlyConfigStore.Snapshot(
                valid.schemaVersion(),
                valid.server(),
                valid.dimension(),
                valid.mapColumns(),
                valid.mapRows(),
                valid.scanRadius(),
                valid.mapCorner(),
                valid.dumpStation(),
                valid.bed(),
                valid.shulkerLineAnchor(),
                List.of(valid.shulkerStations().getFirst(), outside),
                valid.savedAtMs()
            );

        assertThrows(IOException.class, () -> store.save(invalid));
        assertFalse(Files.exists(store.configFile()));
    }

    @Test
    void refusesConfigWhoseAnchorIsNotARegisteredStation()
        throws IOException {
        PrintingOnlyConfigStore store =
            PrintingOnlyConfigStore.open(temporaryDirectory);
        PrintingOnlyConfigStore.Snapshot valid = validSnapshot();
        PrintingOnlyConfigStore.Snapshot invalid =
            new PrintingOnlyConfigStore.Snapshot(
                valid.schemaVersion(),
                valid.server(),
                valid.dimension(),
                valid.mapColumns(),
                valid.mapRows(),
                valid.scanRadius(),
                valid.mapCorner(),
                valid.dumpStation(),
                valid.bed(),
                new PrintingOnlyConfigStore.Position(90, 64, 200),
                valid.shulkerStations(),
                valid.savedAtMs()
            );

        assertThrows(IOException.class, () -> store.save(invalid));
    }

    @Test
    void clearRemovesSavedAndTemporaryFiles() throws IOException {
        PrintingOnlyConfigStore store =
            PrintingOnlyConfigStore.open(temporaryDirectory);
        store.save(validSnapshot());
        Path temporary = store.configFile().resolveSibling(
            PrintingOnlyConfigStore.FILE_NAME + ".tmp"
        );
        Files.writeString(temporary, "stale");

        store.clear();

        assertTrue(store.read().isEmpty());
        assertFalse(Files.exists(temporary));
    }

    private static PrintingOnlyConfigStore.Snapshot validSnapshot() {
        PrintingOnlyConfigStore.Station anchor = station(
            100,
            64,
            200,
            100.5,
            64,
            201.5
        );
        return new PrintingOnlyConfigStore.Snapshot(
            PrintingOnlyConfigStore.SCHEMA_VERSION,
            "server.example:25565",
            "minecraft:overworld",
            6,
            10,
            80,
            new PrintingOnlyConfigStore.Position(0, 63, 0),
            new PrintingOnlyConfigStore.DumpStation(
                new PrintingOnlyConfigStore.Point(12.5, 64, -4.5),
                90,
                15
            ),
            station(20, 64, -2, 20.5, 64, -1.5),
            anchor.block(),
            List.of(
                anchor,
                station(140, 66, 200, 140.5, 66, 201.5)
            ),
            1_700_000_000_000L
        );
    }

    private static PrintingOnlyConfigStore.Station station(
        int x,
        int y,
        int z,
        double openX,
        double openY,
        double openZ
    ) {
        return new PrintingOnlyConfigStore.Station(
            new PrintingOnlyConfigStore.Position(x, y, z),
            new PrintingOnlyConfigStore.Point(
                openX,
                openY,
                openZ
            )
        );
    }
}
