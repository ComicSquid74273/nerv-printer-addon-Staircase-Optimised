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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Atomic local persistence for compiled circular teardown reach topology. */
public final class CircularTeardownReachTopologyStore {
    public static final String DIRECTORY_NAME = "_teardown_reach_plans";
    public static final String FILE_SUFFIX = "_circular_teardown_reach.json";

    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private CircularTeardownReachTopologyStore() {
    }

    public static Path pathFor(
        Path mapFolder,
        String compactPlanSha256,
        double standingEyeHeight,
        double maximumReach
    ) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        if (!FileFingerprint.isSha256(compactPlanSha256)) {
            throw new IOException(
                "Cannot name a teardown reach plan without a valid "
                    + "compact-plan hash."
            );
        }
        requireProfile(standingEyeHeight, maximumReach);
        String profile = String.format(
            Locale.ROOT,
            "schema=%d|algorithm=%d|plan=%s|eye=%.9f|reach=%.9f",
            CircularTeardownReachTopology.SCHEMA_VERSION,
            CircularTeardownReachTopology.ALGORITHM_VERSION,
            compactPlanSha256,
            standingEyeHeight,
            maximumReach
        );
        String profileHash = FileFingerprint.sha256(
            profile.getBytes(StandardCharsets.UTF_8)
        );
        Path directory = mapFolder.toAbsolutePath().normalize()
            .resolve(DIRECTORY_NAME);
        Files.createDirectories(directory);
        return directory.resolve(
            compactPlanSha256.substring(0, 16)
                + "_" + profileHash.substring(0, 16)
                + FILE_SUFFIX
        );
    }

    public static CircularTeardownReachTopology.Snapshot read(
        Path file,
        String expectedCompactPlanSha256,
        double expectedStandingEyeHeight,
        double expectedMaximumReach
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException(
                "The compiled teardown reach plan does not exist: "
                    + file
            );
        }
        long size = Files.size(file);
        if (size <= 0L || size > MAX_FILE_BYTES) {
            throw new IOException(
                "The compiled teardown reach plan has invalid size: "
                    + file
            );
        }

        final CircularTeardownReachTopology.Snapshot snapshot;
        try {
            snapshot = GSON.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                CircularTeardownReachTopology.Snapshot.class
            );
        } catch (RuntimeException failure) {
            throw new IOException(
                "The compiled teardown reach plan is malformed: " + file,
                failure
            );
        }
        if (snapshot == null) {
            throw new IOException(
                "The compiled teardown reach plan is empty: " + file
            );
        }
        if (!Objects.equals(
                snapshot.compactPlanSha256(),
                expectedCompactPlanSha256
            )
            || Double.compare(
                snapshot.standingEyeHeight(),
                expectedStandingEyeHeight
            ) != 0
            || Double.compare(
                snapshot.maximumReach(),
                expectedMaximumReach
            ) != 0) {
            throw new IOException(
                "The compiled teardown reach plan belongs to another "
                    + "NBT or reach profile."
            );
        }
        return snapshot;
    }

    public static void save(
        Path file,
        CircularTeardownReachTopology.Snapshot snapshot
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(snapshot, "snapshot");
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException(
                "The teardown reach plan has no parent directory."
            );
        }
        Files.createDirectories(parent);
        byte[] data = GSON.toJson(snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (data.length <= 0 || data.length > MAX_FILE_BYTES) {
            throw new IOException(
                "The compiled teardown reach plan exceeds its size limit."
            );
        }

        Path temporary = parent.resolve(
            file.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.write(
                temporary,
                data,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE
            )) {
                channel.force(true);
            }
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void requireProfile(
        double standingEyeHeight,
        double maximumReach
    ) throws IOException {
        if (!Double.isFinite(standingEyeHeight)
            || standingEyeHeight <= 0.0
            || !Double.isFinite(maximumReach)
            || maximumReach <= 0.0) {
            throw new IOException(
                "The teardown reach profile must be finite and positive."
            );
        }
    }
}
