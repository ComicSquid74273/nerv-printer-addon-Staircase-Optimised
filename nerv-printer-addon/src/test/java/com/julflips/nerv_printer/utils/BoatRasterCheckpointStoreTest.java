package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatRasterCheckpointStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsAndClears() throws Exception {
        BoatRasterCheckpointStore store = BoatRasterCheckpointStore.open(directory, "Player_1");
        BoatRasterCheckpointStore.Snapshot snapshot = new BoatRasterCheckpointStore.Snapshot(
            BoatRasterCheckpointStore.SCHEMA_VERSION,
            "source", "config", 12, -34, 64, 17, -1, 2200, 4100, 2197,
            "RasterPrinting", 1.5, 70.0, 4.5,
            "7,8,9", "10,11,12", "1,2,3", null, null, true,
            List.of("4,5,6"), Map.of("minecraft:stone", 4), 10L
        );
        store.save(snapshot);
        assertEquals(snapshot, store.read().orElseThrow());
        store.clear();
        assertTrue(store.read().isEmpty());
    }

    @Test
    void reportsOldSchemaAsIoFailureSoCallerCanDiscardIt() throws Exception {
        BoatRasterCheckpointStore store = BoatRasterCheckpointStore.open(directory, "Player_1");
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(), """
            {
              "schemaVersion": 1,
              "selectedY": 64,
              "row": 0,
              "direction": 1,
              "cursor": 0,
              "confirmedFrontier": 0,
              "phase": "RasterPrinting"
            }
            """);

        assertThrows(IOException.class, store::read);
    }

    @Test
    void concurrentHeartbeatSavesLeaveOneValidCheckpoint() throws Exception {
        BoatRasterCheckpointStore store = BoatRasterCheckpointStore.open(
            directory, "Player_1"
        );
        var saves = new ArrayList<java.util.concurrent.Future<Void>>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int cursor = 0; cursor < 32; cursor++) {
                int savedCursor = cursor;
                saves.add(executor.submit(() -> {
                    store.save(new BoatRasterCheckpointStore.Snapshot(
                        BoatRasterCheckpointStore.SCHEMA_VERSION,
                        "source", "config", 12, -34, 64, 17, 1,
                        savedCursor, savedCursor, savedCursor,
                        "RasterPrinting", 1.5, 70.0, 4.5,
                        null, null, null, null, null, false,
                        List.of(), Map.of(), savedCursor
                    ));
                    return null;
                }));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        for (var save : saves) save.get();

        BoatRasterCheckpointStore.Snapshot saved = store.read().orElseThrow();
        assertTrue(saved.cursor() >= 0 && saved.cursor() < 32);
        try (var files = Files.list(store.file().getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
