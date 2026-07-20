package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaircasedCycleCheckpointStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsActiveMiningOwnership()
        throws IOException {
        StaircasedCycleCheckpointStore store =
            StaircasedCycleCheckpointStore.open(
                temporaryDirectory,
                "Builder_1"
            );
        StaircasedCycleCheckpointStore.Snapshot snapshot =
            miningSnapshot("Builder_1");

        store.save(snapshot);

        assertEquals(
            Optional.of(snapshot),
            store.read()
        );
        assertTrue(Files.isRegularFile(store.stateFile()));
    }

    @Test
    void rejectsAStateBelongingToAnotherPlayer()
        throws IOException {
        StaircasedCycleCheckpointStore store =
            StaircasedCycleCheckpointStore.open(
                temporaryDirectory,
                "Builder_1"
            );

        assertThrows(
            IOException.class,
            () -> store.save(miningSnapshot("Builder_2"))
        );
        assertFalse(Files.exists(store.stateFile()));
    }

    @Test
    void malformedStateFailsClosedInsteadOfLookingAbsent()
        throws IOException {
        StaircasedCycleCheckpointStore store =
            StaircasedCycleCheckpointStore.open(
                temporaryDirectory,
                "Builder_1"
            );
        Files.writeString(store.stateFile(), "{not-json");

        assertThrows(IOException.class, store::read);
    }

    @Test
    void clearRemovesTheCompletedCycle()
        throws IOException {
        StaircasedCycleCheckpointStore store =
            StaircasedCycleCheckpointStore.open(
                temporaryDirectory,
                "Builder_1"
            );
        store.save(miningSnapshot("Builder_1"));

        store.clear();

        assertTrue(store.read().isEmpty());
    }

    private static StaircasedCycleCheckpointStore.Snapshot
        miningSnapshot(String playerId) {
        String hash = "a".repeat(64);
        return new StaircasedCycleCheckpointStore.Snapshot(
            StaircasedCycleCheckpointStore.SCHEMA_VERSION,
            playerId,
            UUID.randomUUID().toString(),
            3,
            "map.nbt",
            hash,
            "map_compact.nbt",
            null,
            null,
            hash,
            hash,
            true,
            "server.example:25565",
            "minecraft:overworld",
            "1,64,2",
            MapCyclePhase.MINING,
            MapHandoffStage.DEPOSITED,
            12,
            13,
            1000,
            -1,
            "MiningUTraversal",
            7,
            142,
            "mining-progress",
            2000
        );
    }
}
