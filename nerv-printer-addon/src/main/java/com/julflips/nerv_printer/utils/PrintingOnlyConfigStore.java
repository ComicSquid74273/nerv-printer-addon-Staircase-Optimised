package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Durable printing-only station geometry and verified shulker registry. */
public final class PrintingOnlyConfigStore {
    public static final int SCHEMA_VERSION = 1;
    public static final String DIRECTORY_NAME = "_configs";
    public static final String FILE_NAME =
        "printing-only-shulker-line.json";

    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;
    private static final int MAX_STATIONS = 4096;
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public record Position(int x, int y, int z) {
    }

    public record Point(double x, double y, double z) {
    }

    public record DumpStation(
        Point position,
        float yaw,
        float pitch
    ) {
    }

    public record Station(Position block, Point openPosition) {
    }

    public record StoredItem(String itemId, int count) {
    }

    public record StoredTool(String itemId, int minimumEfficiency) {
    }

    public record StationInventory(
        Position block,
        List<StoredItem> items,
        List<StoredTool> tools
    ) {
        public StationInventory {
            if (items != null) items = List.copyOf(items);
            if (tools != null) tools = List.copyOf(tools);
        }
    }

    public record Snapshot(
        int schemaVersion,
        String server,
        String dimension,
        int mapColumns,
        int mapRows,
        int scanRadius,
        Position mapCorner,
        DumpStation dumpStation,
        Station bed,
        Position shulkerLineAnchor,
        List<Station> shulkerStations,
        List<StationInventory> shulkerInventories,
        long savedAtMs
    ) {
        public Snapshot {
            if (shulkerStations != null) {
                shulkerStations = List.copyOf(shulkerStations);
            }
            if (shulkerInventories != null) {
                shulkerInventories = List.copyOf(shulkerInventories);
            }
        }

        /** Backward-compatible constructor for geometry-only schema-1 files. */
        public Snapshot(
            int schemaVersion,
            String server,
            String dimension,
            int mapColumns,
            int mapRows,
            int scanRadius,
            Position mapCorner,
            DumpStation dumpStation,
            Station bed,
            Position shulkerLineAnchor,
            List<Station> shulkerStations,
            long savedAtMs
        ) {
            this(
                schemaVersion,
                server,
                dimension,
                mapColumns,
                mapRows,
                scanRadius,
                mapCorner,
                dumpStation,
                bed,
                shulkerLineAnchor,
                shulkerStations,
                null,
                savedAtMs
            );
        }
    }

    private final Path configFile;
    private final Path temporaryFile;

    private PrintingOnlyConfigStore(Path mapFolder) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        Path directory = mapFolder.toAbsolutePath().normalize()
            .resolve(DIRECTORY_NAME);
        Files.createDirectories(directory);
        configFile = directory.resolve(FILE_NAME);
        temporaryFile = directory.resolve(FILE_NAME + ".tmp");
    }

    public static PrintingOnlyConfigStore open(Path mapFolder)
        throws IOException {
        return new PrintingOnlyConfigStore(mapFolder);
    }

    public Path configFile() {
        return configFile;
    }

    public Optional<Snapshot> read() throws IOException {
        if (!Files.exists(configFile)) return Optional.empty();
        long size = Files.size(configFile);
        if (size <= 0 || size > MAX_CONFIG_BYTES) {
            throw new IOException(
                "Printing-only config has an invalid size: " + configFile
            );
        }

        final Snapshot snapshot;
        try {
            snapshot = GSON.fromJson(
                Files.readString(configFile, StandardCharsets.UTF_8),
                Snapshot.class
            );
        } catch (RuntimeException failure) {
            throw new IOException(
                "Printing-only config is malformed: " + configFile,
                failure
            );
        }
        validate(snapshot);
        return Optional.of(snapshot);
    }

    public void save(Snapshot snapshot) throws IOException {
        validate(snapshot);
        byte[] data = GSON.toJson(snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_CONFIG_BYTES) {
            throw new IOException(
                "Printing-only config exceeds the size limit."
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
                configFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(
                temporaryFile,
                configFile,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(temporaryFile);
        Files.deleteIfExists(configFile);
    }

    public static boolean matchesEnvironment(
        Snapshot snapshot,
        String server,
        String dimension,
        int mapColumns,
        int mapRows
    ) {
        return snapshot != null
            && Objects.equals(snapshot.server(), server)
            && Objects.equals(snapshot.dimension(), dimension)
            && snapshot.mapColumns() == mapColumns
            && snapshot.mapRows() == mapRows;
    }

    public static boolean hasReusableShulkerRegistry(
        Snapshot snapshot
    ) {
        return snapshot != null
            && snapshot.shulkerStations() != null
            && snapshot.shulkerInventories() != null
            && snapshot.shulkerInventories().size()
                == snapshot.shulkerStations().size();
    }

    private static void validate(Snapshot snapshot) throws IOException {
        if (snapshot == null) {
            throw new IOException("Printing-only config is empty.");
        }
        if (snapshot.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException(
                "Unsupported printing-only config schema "
                    + snapshot.schemaVersion() + "."
            );
        }
        requireIdentity(snapshot.server(), "server", 512);
        requireIdentity(snapshot.dimension(), "dimension", 256);
        if (snapshot.mapColumns() < 1 || snapshot.mapColumns() > 64
            || snapshot.mapRows() < 1 || snapshot.mapRows() > 64) {
            throw new IOException(
                "Printing-only config has invalid map dimensions."
            );
        }
        if (snapshot.scanRadius() < 1 || snapshot.scanRadius() > 512) {
            throw new IOException(
                "Printing-only config has an invalid scan radius."
            );
        }
        requirePosition(snapshot.mapCorner(), "map corner");
        requireDumpStation(snapshot.dumpStation());
        if (snapshot.bed() != null) {
            requireStation(snapshot.bed(), "bed");
        }
        Position anchor = requirePosition(
            snapshot.shulkerLineAnchor(),
            "shulker-line anchor"
        );
        List<Station> stations = snapshot.shulkerStations();
        if (stations == null || stations.isEmpty()
            || stations.size() > MAX_STATIONS) {
            throw new IOException(
                "Printing-only config has an invalid shulker station count."
            );
        }
        Set<Position> unique = new HashSet<>();
        for (Station station : stations) {
            requireStation(station, "shulker station");
            if (!unique.add(station.block())) {
                throw new IOException(
                    "Printing-only config contains a duplicate shulker station."
                );
            }
            long horizontalDistance = Math.max(
                Math.abs((long) station.block().x() - anchor.x()),
                Math.abs((long) station.block().z() - anchor.z())
            );
            if (horizontalDistance > snapshot.scanRadius()
                || Math.abs(
                    (long) station.block().y() - anchor.y()
                ) > 2) {
                throw new IOException(
                    "Printing-only config contains a shulker outside its "
                        + "saved scan box."
                );
            }
        }
        if (!unique.contains(anchor)) {
            throw new IOException(
                "Printing-only config does not contain its shulker anchor."
            );
        }
        validateShulkerInventories(snapshot, unique);
        if (snapshot.savedAtMs() <= 0) {
            throw new IOException(
                "Printing-only config has an invalid save timestamp."
            );
        }
    }

    private static void validateShulkerInventories(
        Snapshot snapshot,
        Set<Position> stationPositions
    ) throws IOException {
        List<StationInventory> inventories =
            snapshot.shulkerInventories();
        // Older schema-1 files contain geometry only. They remain readable so
        // the runtime can inspect them once and atomically upgrade the file.
        if (inventories == null) return;
        if (inventories.size() != stationPositions.size()) {
            throw new IOException(
                "Printing-only config does not have one registry entry per "
                    + "shulker station."
            );
        }

        Set<Position> registeredPositions = new HashSet<>();
        for (StationInventory inventory : inventories) {
            if (inventory == null) {
                throw new IOException(
                    "Printing-only config contains an empty shulker registry entry."
                );
            }
            Position block = requirePosition(
                inventory.block(),
                "shulker inventory block"
            );
            if (!stationPositions.contains(block)
                || !registeredPositions.add(block)) {
                throw new IOException(
                    "Printing-only config contains an unmatched or duplicate "
                        + "shulker registry entry."
                );
            }
            if (inventory.items() == null || inventory.tools() == null) {
                throw new IOException(
                    "Printing-only config contains an incomplete shulker registry."
                );
            }
            Set<String> itemIds = new HashSet<>();
            for (StoredItem item : inventory.items()) {
                if (item == null) {
                    throw new IOException(
                        "Printing-only config contains an empty stored item."
                    );
                }
                requireIdentity(item.itemId(), "stored item id", 256);
                if (item.count() <= 0 || !itemIds.add(item.itemId())) {
                    throw new IOException(
                        "Printing-only config contains an invalid or duplicate "
                            + "stored item."
                    );
                }
            }
            Set<String> toolIds = new HashSet<>();
            for (StoredTool tool : inventory.tools()) {
                if (tool == null) {
                    throw new IOException(
                        "Printing-only config contains an empty stored tool."
                    );
                }
                requireIdentity(tool.itemId(), "stored tool id", 256);
                if (tool.minimumEfficiency() < 0
                    || !toolIds.add(tool.itemId())
                    || !itemIds.contains(tool.itemId())) {
                    throw new IOException(
                        "Printing-only config contains an invalid, duplicate, "
                            + "or uncounted stored tool."
                    );
                }
            }
        }
    }

    private static Position requirePosition(
        Position position,
        String label
    ) throws IOException {
        if (position == null) {
            throw new IOException(
                "Printing-only config is missing the " + label + "."
            );
        }
        return position;
    }

    private static void requirePoint(Point point, String label)
        throws IOException {
        if (point == null
            || !Double.isFinite(point.x())
            || !Double.isFinite(point.y())
            || !Double.isFinite(point.z())) {
            throw new IOException(
                "Printing-only config has an invalid " + label + "."
            );
        }
    }

    private static void requireDumpStation(DumpStation dump)
        throws IOException {
        if (dump == null) {
            throw new IOException(
                "Printing-only config is missing the dump station."
            );
        }
        requirePoint(dump.position(), "dump-station position");
        if (!Float.isFinite(dump.yaw())
            || !Float.isFinite(dump.pitch())) {
            throw new IOException(
                "Printing-only config has an invalid dump-station rotation."
            );
        }
    }

    private static void requireStation(Station station, String label)
        throws IOException {
        if (station == null) {
            throw new IOException(
                "Printing-only config is missing a " + label + "."
            );
        }
        requirePosition(station.block(), label + " block");
        requirePoint(station.openPosition(), label + " open position");
    }

    private static void requireIdentity(
        String value,
        String label,
        int maximumLength
    ) throws IOException {
        if (value == null || value.isBlank()
            || value.length() > maximumLength) {
            throw new IOException(
                "Printing-only config has an invalid " + label + "."
            );
        }
    }
}
