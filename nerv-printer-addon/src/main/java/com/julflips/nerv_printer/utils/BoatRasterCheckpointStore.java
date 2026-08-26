package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Atomic durable state for one Boat Raster cycle. */
public final class BoatRasterCheckpointStore {
    public static final int SCHEMA_VERSION = 7;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Snapshot(
        int schemaVersion,
        String sourceFingerprint,
        String configFingerprint,
        int mapOriginX,
        int mapOriginZ,
        int selectedY,
        int row,
        int direction,
        int cursor,
        int routeCursor,
        int confirmedFrontier,
        String phase,
        Double waypointX,
        Double waypointY,
        Double waypointZ,
        String boatPosition,
        String launchBlock,
        String boatSource,
        String restockMaterial,
        String restockSource,
        Boolean restockBeforeDeployment,
        List<String> ownedTemporaryBlocks,
        Map<String, Integer> inventoryBaseline,
        long savedAtMs
    ) {
        public Snapshot {
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Boat Raster checkpoint schema.");
            }
            if (cursor < 0 || routeCursor < 0
                || confirmedFrontier < 0 || row < 0
                || (direction != -1 && direction != 1)) {
                throw new IllegalArgumentException("Invalid Boat Raster checkpoint progress.");
            }
            Objects.requireNonNull(phase, "phase");
            ownedTemporaryBlocks = List.copyOf(ownedTemporaryBlocks == null
                ? List.of() : ownedTemporaryBlocks);
            inventoryBaseline = Map.copyOf(inventoryBaseline == null
                ? Map.of() : inventoryBaseline);
        }
    }

    private final Path file;

    private BoatRasterCheckpointStore(Path folder, String playerId) throws IOException {
        Path directory = folder.toAbsolutePath().normalize().resolve("_staircased_state");
        Files.createDirectories(directory);
        String safe = playerId.replaceAll("[^A-Za-z0-9_]", "_");
        file = directory.resolve(safe + "_boat_raster.json");
    }

    public static BoatRasterCheckpointStore open(Path folder, String playerId) throws IOException {
        return new BoatRasterCheckpointStore(folder, playerId);
    }

    public Optional<Snapshot> read() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        Snapshot snapshot;
        try {
            snapshot = GSON.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                Snapshot.class
            );
        } catch (RuntimeException failure) {
            throw new IOException(
                "Boat Raster checkpoint schema or JSON is incompatible.",
                failure
            );
        }
        if (snapshot == null) throw new IOException("Boat Raster checkpoint is empty.");
        try {
            return Optional.of(new Snapshot(
                snapshot.schemaVersion(), snapshot.sourceFingerprint(),
                snapshot.configFingerprint(), snapshot.mapOriginX(),
                snapshot.mapOriginZ(), snapshot.selectedY(), snapshot.row(),
                snapshot.direction(), snapshot.cursor(), snapshot.routeCursor(),
                snapshot.confirmedFrontier(),
                snapshot.phase(), snapshot.waypointX(), snapshot.waypointY(),
                snapshot.waypointZ(), snapshot.boatPosition(),
                snapshot.launchBlock(), snapshot.boatSource(),
                snapshot.restockMaterial(), snapshot.restockSource(),
                snapshot.restockBeforeDeployment(), snapshot.ownedTemporaryBlocks(),
                snapshot.inventoryBaseline(), snapshot.savedAtMs()
            ));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Boat Raster checkpoint is invalid.", failure);
        }
    }

    public synchronized void save(Snapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Path temporary = Files.createTempFile(
            file.getParent(),
            file.getFileName().toString() + ".",
            ".tmp"
        );
        try {
            Files.writeString(
                temporary,
                GSON.toJson(snapshot),
                StandardCharsets.UTF_8
            );
            replaceWithRetry(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void replaceWithRetry(Path temporary) throws IOException {
        IOException atomicFailure;
        try {
            Files.move(
                temporary,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
            return;
        } catch (AtomicMoveNotSupportedException failure) {
            atomicFailure = failure;
        } catch (IOException failure) {
            // Windows can transiently deny replacement while the destination
            // is being scanned or indexed. Retry with the portable move below.
            atomicFailure = failure;
        }

        IOException lastFailure = atomicFailure;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                try {
                    Thread.sleep(5L * (attempt + 1L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
        lastFailure.addSuppressed(atomicFailure);
        throw lastFailure;
    }

    public synchronized void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    public Path file() {
        return file;
    }
}
