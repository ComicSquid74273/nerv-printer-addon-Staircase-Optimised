package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedFileCoordinationBusTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sendsAndAcknowledgesMessagesInBothDirections() throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "slave-one"
            );
        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "slave-one"
            );

        SharedFileCoordinationBus.Envelope command = master.enqueue(
            "slave-one",
            "BUILD:map-12"
        );
        master.flush(100L);
        slave.flush(101L);

        assertEquals(List.of(command), slave.poll());
        slave.acknowledge(command);
        SharedFileCoordinationBus.Envelope response = slave.enqueue(
            "master",
            "STARTED:map-12"
        );
        slave.flush(102L);
        master.flush(103L);

        assertEquals(List.of(response), master.poll());
        master.acknowledge(response);
        master.flush(104L);
        slave.flush(105L);
        assertTrue(slave.poll().isEmpty());
    }

    @Test
    void acknowledgementPrunesOutboxAndRestartsPreserveNextId()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus.Envelope first = master.enqueue(
            "worker",
            "first"
        );
        SharedFileCoordinationBus.Envelope second = master.enqueue(
            "worker",
            "second"
        );
        master.flush(10L);
        slave.flush(11L);
        List<SharedFileCoordinationBus.Envelope> received = slave.poll();
        assertEquals(List.of(first, second), received);

        slave.acknowledge(received.get(0));
        slave.acknowledge(received.get(1));
        slave.flush(12L);
        master.flush(13L);

        SharedFileCoordinationBus restarted =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus.Envelope third = restarted.enqueue(
            "worker",
            "third"
        );
        assertEquals(3L, third.id());
        restarted.flush(14L);

        String persisted = Files.readString(restarted.localStateFile());
        assertFalse(persisted.contains("\"payload\": \"first\""));
        assertFalse(persisted.contains("\"payload\": \"second\""));
        assertTrue(persisted.contains("\"payload\": \"third\""));
    }

    @Test
    void corruptLocalMasterStateFailsClosedWithoutOverwritingIt()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        master.setLocalMetadata("jobId", "map-17");
        master.flush(15L);

        Path stateFile = master.localStateFile();
        byte[] corruptBytes = "{broken-local-master-state"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(stateFile, corruptBytes);

        assertThrows(
            IOException.class,
            () -> SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            )
        );
        assertArrayEquals(corruptBytes, Files.readAllBytes(stateFile));
    }

    @Test
    void changedMasterPeerMembershipFailsClosedWithoutOverwritingState()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                List.of("alpha", "beta")
            );
        master.setLocalMetadata("jobId", "map-18");
        master.flush(16L);

        Path stateFile = master.localStateFile();
        byte[] persistedBytes = Files.readAllBytes(stateFile);

        assertThrows(
            IOException.class,
            () -> SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                List.of("alpha", "gamma")
            )
        );
        assertArrayEquals(persistedBytes, Files.readAllBytes(stateFile));
    }

    @Test
    void unacknowledgedMessagesRedeliverAfterBothSidesRestart()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus.Envelope pending = master.enqueue(
            "worker",
            "durable-job"
        );
        master.flush(20L);

        SharedFileCoordinationBus firstSlave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        firstSlave.flush(21L);
        assertEquals(List.of(pending), firstSlave.poll());

        SharedFileCoordinationBus restartedMaster =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        restartedMaster.flush(22L);
        SharedFileCoordinationBus restartedSlave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        restartedSlave.flush(23L);

        assertEquals(List.of(pending), restartedSlave.poll());
    }

    @Test
    void acknowledgedMessagesDoNotRedeliverAfterReceiverRestart()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus.Envelope message = master.enqueue(
            "worker",
            "only-once-after-ack"
        );
        master.flush(30L);
        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        slave.flush(31L);
        assertEquals(List.of(message), slave.poll());
        slave.acknowledge(message);
        slave.flush(32L);

        SharedFileCoordinationBus restartedSlave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        restartedSlave.flush(33L);
        assertTrue(restartedSlave.poll().isEmpty());
    }

    @Test
    void sharesMetadataTimestampAndFreshness() throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        master.setLocalMetadata("phase", "MINING");
        master.setLocalMetadata("map", "picture.nbt");
        master.flush(1_000L);
        slave.flush(1_001L);

        assertEquals(
            Map.of("phase", "MINING", "map", "picture.nbt"),
            slave.remoteMetadata("master")
        );
        assertEquals(
            1_000L,
            slave.remoteTimestampMs("master").orElseThrow()
        );
        assertTrue(slave.isPeerFresh("master", 1_050L, 50L));
        assertFalse(slave.isPeerFresh("master", 1_051L, 50L));
        assertFalse(
            slave.isPeerFresh("master", 999L, 50L),
            "A future-dated peer state must not be considered fresh."
        );

        master.removeLocalMetadata("map");
        master.flush(1_100L);
        slave.flush(1_101L);
        assertEquals(Map.of("phase", "MINING"), slave.remoteMetadata("master"));
    }

    @Test
    void ignoresCorruptIncompatibleWrongMasterAndWrongMembershipFiles()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        Path workerFile = SharedFileCoordinationBus.slaveStateFile(
            temporaryDirectory,
            "worker"
        );

        Files.writeString(workerFile, "{not-json");
        master.flush(40L);
        assertTrue(master.poll().isEmpty());
        assertTrue(master.remoteMetadata("worker").isEmpty());

        Files.writeString(workerFile, "{}");
        master.flush(40L);
        assertTrue(master.poll().isEmpty());

        Files.writeString(
            workerFile,
            slaveDocument(999, "master", "worker", "master", "bad-schema")
        );
        master.flush(41L);
        assertTrue(master.poll().isEmpty());

        Files.writeString(
            workerFile,
            slaveDocument(1, "other-master", "worker", "other-master", "wrong")
        );
        master.flush(42L);
        assertTrue(master.poll().isEmpty());

        Files.writeString(
            workerFile,
            slaveDocument(1, "master", "worker", "somebody-else", "wrong-peer")
        );
        master.flush(43L);
        assertTrue(master.poll().isEmpty());
        assertTrue(master.remoteMetadata("worker").isEmpty());
    }

    @Test
    void oneMasterCoordinatesMultipleIndependentSlaveWriters()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                List.of("alpha", "beta")
            );
        SharedFileCoordinationBus alpha =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "alpha"
            );
        SharedFileCoordinationBus beta =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "beta"
            );

        SharedFileCoordinationBus.Envelope alphaJob = master.enqueue(
            "alpha",
            "column-1"
        );
        SharedFileCoordinationBus.Envelope betaJob = master.enqueue(
            "beta",
            "column-2"
        );
        assertEquals(1L, alphaJob.id());
        assertEquals(1L, betaJob.id());
        master.flush(50L);
        alpha.flush(51L);
        beta.flush(52L);
        assertEquals(List.of(alphaJob), alpha.poll());
        assertEquals(List.of(betaJob), beta.poll());

        SharedFileCoordinationBus.Envelope alphaReply = alpha.enqueue(
            "master",
            "alpha-done"
        );
        SharedFileCoordinationBus.Envelope betaReply = beta.enqueue(
            "master",
            "beta-done"
        );
        alpha.flush(53L);
        beta.flush(54L);
        master.flush(55L);
        assertEquals(List.of(alphaReply, betaReply), master.poll());

        assertNotEquals(alpha.localStateFile(), beta.localStateFile());
        assertTrue(Files.isRegularFile(alpha.localStateFile()));
        assertTrue(Files.isRegularFile(beta.localStateFile()));
    }

    @Test
    void sanitizesSlaveFileNamesWithoutTraversalOrCommonCollisions()
        throws Exception {
        String hostile = "../Bot:A\\B";
        String similarlyShaped = ".._Bot_A_B";
        String sanitized = SharedFileCoordinationBus.sanitizeFileComponent(
            hostile
        );
        Path first = SharedFileCoordinationBus.slaveStateFile(
            temporaryDirectory,
            hostile
        );
        Path second = SharedFileCoordinationBus.slaveStateFile(
            temporaryDirectory,
            similarlyShaped
        );

        assertEquals(temporaryDirectory.toAbsolutePath(), first.getParent());
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
        assertFalse(sanitized.contains(":"));
        assertNotEquals(first, second);
        assertEquals(
            "slave_worker-1_state.json",
            SharedFileCoordinationBus.slaveStateFile(
                temporaryDirectory,
                "worker-1"
            ).getFileName().toString()
        );

        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                hostile
            );
        slave.flush(60L);
        assertEquals(first, slave.localStateFile());
        assertTrue(Files.isRegularFile(first));
        try (var entries = Files.list(temporaryDirectory)) {
            assertFalse(
                entries.anyMatch(
                    path -> path.getFileName().toString().endsWith(".tmp")
                )
            );
        }
    }

    @Test
    void enforcesConfiguredMembershipAndFifoAcknowledgements()
        throws Exception {
        SharedFileCoordinationBus master =
            SharedFileCoordinationBus.openMaster(
                temporaryDirectory,
                "master",
                "worker"
            );
        assertThrows(
            IllegalArgumentException.class,
            () -> master.enqueue("intruder", "no")
        );

        SharedFileCoordinationBus.Envelope first = master.enqueue(
            "worker",
            "one"
        );
        master.enqueue("worker", "two");
        master.flush(70L);
        SharedFileCoordinationBus slave =
            SharedFileCoordinationBus.openSlave(
                temporaryDirectory,
                "master",
                "worker"
            );
        slave.flush(71L);
        List<SharedFileCoordinationBus.Envelope> messages = slave.poll();
        assertThrows(
            IllegalStateException.class,
            () -> slave.acknowledge(messages.get(1))
        );
        slave.acknowledge(first);
        slave.acknowledge(messages.get(1));
    }

    private static String slaveDocument(
        int schema,
        String masterId,
        String nodeId,
        String configuredPeer,
        String metadataValue
    ) {
        return """
            {
              "schemaVersion": %d,
              "role": "SLAVE",
              "nodeId": "%s",
              "masterId": "%s",
              "configuredPeers": ["%s"],
              "writtenAtMs": 1,
              "metadata": {"test": "%s"},
              "nextIds": {"%s": 1},
              "acknowledgements": {"%s": 0},
              "observedAcknowledgements": {"%s": 0},
              "outbox": []
            }
            """.formatted(
            schema,
            nodeId,
            masterId,
            configuredPeer,
            metadataValue,
            configuredPeer,
            configuredPeer,
            configuredPeer
        );
    }
}
