package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Durable local lifecycle ownership for one Staircased Printer process.
 *
 * <p>The shared file-coordination transport persists a File Master's
 * lifecycle, but a solo or chat-coordinated process still needs the same
 * crash boundary. This store is deliberately independent of the transport:
 * it records which logical NBT cycle owns the local player, while recovery
 * rebuilds transient movement, inventory and block-action state from current
 * server-authoritative observations.</p>
 */
public final class StaircasedCycleCheckpointStore {
    public static final int SCHEMA_VERSION = 1;
    public static final String DIRECTORY_NAME = "_staircased_state";
    public static final String FILE_SUFFIX = "_active_cycle.json";

    private static final long MAX_STATE_BYTES = 1024L * 1024L;
    private static final Pattern SHA_256 =
        Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern PLAYER_ID =
        Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public record Snapshot(
        int schemaVersion,
        String playerId,
        String jobId,
        long generation,
        String sourceNbt,
        String sourceSha256,
        String printingNbt,
        String archivedSourceNbt,
        String archivedPrintingNbt,
        String configSha256,
        String compactPlanSha256,
        boolean circularTraversal,
        String server,
        String dimension,
        String mapCorner,
        MapCyclePhase phase,
        MapHandoffStage handoffStage,
        Integer handoffSourceMapId,
        Integer handoffLockedMapId,
        long startedAtMs,
        long completedAtMs,
        String runtimeState,
        Integer activeMiningPair,
        Integer activeMiningTargetIndex,
        String checkpoint,
        long savedAtMs
    ) {
    }

    private final Path stateFile;
    private final Path temporaryFile;
    private final String playerId;

    private StaircasedCycleCheckpointStore(
        Path mapFolder,
        String playerId
    ) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        this.playerId = requirePlayerId(playerId);
        Path directory = mapFolder.toAbsolutePath().normalize()
            .resolve(DIRECTORY_NAME);
        Files.createDirectories(directory);
        stateFile = directory.resolve(
            this.playerId + FILE_SUFFIX
        );
        temporaryFile = directory.resolve(
            this.playerId + FILE_SUFFIX + ".tmp"
        );
    }

    public static StaircasedCycleCheckpointStore open(
        Path mapFolder,
        String playerId
    ) throws IOException {
        return new StaircasedCycleCheckpointStore(
            mapFolder,
            playerId
        );
    }

    public Path stateFile() {
        return stateFile;
    }

    public Optional<Snapshot> read() throws IOException {
        if (!Files.exists(stateFile)) return Optional.empty();
        long size = Files.size(stateFile);
        if (size <= 0 || size > MAX_STATE_BYTES) {
            throw new IOException(
                "Local Staircased checkpoint has invalid size: "
                    + stateFile
            );
        }

        final Snapshot snapshot;
        try {
            snapshot = GSON.fromJson(
                Files.readString(
                    stateFile,
                    StandardCharsets.UTF_8
                ),
                Snapshot.class
            );
        } catch (JsonParseException failure) {
            throw new IOException(
                "Local Staircased checkpoint is malformed: "
                    + stateFile,
                failure
            );
        }
        validate(snapshot, playerId);
        return Optional.of(snapshot);
    }

    public void save(Snapshot snapshot) throws IOException {
        validate(snapshot, playerId);
        byte[] data = GSON.toJson(snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_STATE_BYTES) {
            throw new IOException(
                "Local Staircased checkpoint exceeds the size limit."
            );
        }

        Files.write(
            temporaryFile,
            data,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        try (FileChannel channel = FileChannel.open(
            temporaryFile,
            StandardOpenOption.WRITE
        )) {
            channel.force(true);
        }
        try {
            Files.move(
                temporaryFile,
                stateFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(
                temporaryFile,
                stateFile,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(temporaryFile);
        Files.deleteIfExists(stateFile);
    }

    private static void validate(
        Snapshot snapshot,
        String expectedPlayerId
    ) throws IOException {
        if (snapshot == null) {
            throw new IOException(
                "Local Staircased checkpoint is empty."
            );
        }
        if (snapshot.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException(
                "Unsupported local Staircased checkpoint schema "
                    + snapshot.schemaVersion() + "."
            );
        }
        if (!Objects.equals(
            requirePlayerId(snapshot.playerId()),
            expectedPlayerId
        )) {
            throw new IOException(
                "Local Staircased checkpoint belongs to another player."
            );
        }
        try {
            UUID.fromString(snapshot.jobId());
        } catch (RuntimeException invalidJobId) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid job ID.",
                invalidJobId
            );
        }
        if (snapshot.generation() < 1) {
            throw new IOException(
                "Local Staircased checkpoint generation must be positive."
            );
        }
        requireNbtName(snapshot.sourceNbt(), "source NBT");
        requireOptionalNbtName(
            snapshot.printingNbt(),
            "printing NBT"
        );
        requireOptionalNbtName(
            snapshot.archivedSourceNbt(),
            "archived source NBT"
        );
        requireOptionalNbtName(
            snapshot.archivedPrintingNbt(),
            "archived printing NBT"
        );
        requireSha(snapshot.sourceSha256(), "source SHA-256");
        requireSha(snapshot.configSha256(), "config SHA-256");
        requireSha(
            snapshot.compactPlanSha256(),
            "compact-plan SHA-256"
        );
        requireText(snapshot.server(), "server");
        requireText(snapshot.dimension(), "dimension");
        requireText(snapshot.mapCorner(), "map corner");
        requireText(snapshot.runtimeState(), "runtime state");
        requireText(snapshot.checkpoint(), "checkpoint name");
        if (snapshot.phase() == null
            || !snapshot.phase().isInProgress()) {
            throw new IOException(
                "Local Staircased checkpoint must own an active lifecycle phase."
            );
        }
        if (snapshot.handoffStage() == null
            || !snapshot.handoffStage().isValidFor(
                snapshot.phase()
            )
            || !snapshot.handoffStage().hasValidMapIds(
                snapshot.handoffSourceMapId(),
                snapshot.handoffLockedMapId()
            )) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid map-handoff state."
            );
        }
        if (snapshot.startedAtMs() < 0
            || snapshot.completedAtMs() < -1
            || snapshot.savedAtMs() <= 0) {
            throw new IOException(
                "Local Staircased checkpoint has invalid timestamps."
            );
        }
        if (snapshot.activeMiningPair() != null
            && (snapshot.phase() != MapCyclePhase.MINING
                || snapshot.activeMiningPair() < 0
                || snapshot.activeMiningPair() >= 64)) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid mining pair."
            );
        }
        if (snapshot.activeMiningTargetIndex() != null
            && (snapshot.activeMiningPair() == null
                || snapshot.activeMiningTargetIndex() < 0)) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid mining target index."
            );
        }
    }

    private static String requirePlayerId(String value)
        throws IOException {
        if (value == null || !PLAYER_ID.matcher(value).matches()) {
            throw new IOException(
                "The local player ID cannot be used for a Staircased checkpoint."
            );
        }
        return value;
    }

    private static void requireSha(
        String value,
        String label
    ) throws IOException {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid "
                    + label + "."
            );
        }
    }

    private static void requireNbtName(
        String value,
        String label
    ) throws IOException {
        if (value == null
            || value.isBlank()
            || !value.toLowerCase(java.util.Locale.ROOT)
                .endsWith(".nbt")
            || !Path.of(value).getFileName().toString()
                .equals(value)
            || value.contains("/")
            || value.contains("\\")) {
            throw new IOException(
                "Local Staircased checkpoint has an invalid "
                    + label + " filename."
            );
        }
    }

    private static void requireOptionalNbtName(
        String value,
        String label
    ) throws IOException {
        if (value != null) requireNbtName(value, label);
    }

    private static void requireText(
        String value,
        String label
    ) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(
                "Local Staircased checkpoint is missing " + label + "."
            );
        }
    }
}
