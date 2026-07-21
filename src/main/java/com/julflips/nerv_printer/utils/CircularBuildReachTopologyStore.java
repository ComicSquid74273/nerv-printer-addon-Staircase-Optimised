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

/** Atomic local persistence for compiled circular printing reach topology. */
public final class CircularBuildReachTopologyStore {
    public static final String DIRECTORY_NAME = "_build_reach_plans";
    public static final String FILE_SUFFIX = "_circular_build_reach.json";

    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private CircularBuildReachTopologyStore() {
    }

    public static Path pathFor(
        Path mapFolder,
        String compactPlanSha256,
        double standingEyeHeight,
        double maximumReach
    ) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        requireIdentity(compactPlanSha256);
        requireProfile(standingEyeHeight, maximumReach);
        String profile = String.format(
            Locale.ROOT,
            "schema=%d|algorithm=%d|plan=%s|eye=%.9f|reach=%.9f",
            CircularBuildReachTopology.SCHEMA_VERSION,
            CircularBuildReachTopology.ALGORITHM_VERSION,
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

    public static CircularBuildReachTopology.Snapshot read(
        Path file,
        String expectedCompactPlanSha256,
        double expectedStandingEyeHeight,
        double expectedMaximumReach
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException(
                "The compiled build reach plan does not exist: " + file
            );
        }
        long size = Files.size(file);
        if (size <= 0L || size > MAX_FILE_BYTES) {
            throw new IOException(
                "The compiled build reach plan has invalid size: " + file
            );
        }

        final CircularBuildReachTopology.Snapshot snapshot;
        try {
            snapshot = GSON.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                CircularBuildReachTopology.Snapshot.class
            );
        } catch (RuntimeException failure) {
            throw new IOException(
                "The compiled build reach plan is malformed: " + file,
                failure
            );
        }
        if (snapshot == null
            || !Objects.equals(
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
                "The compiled build reach plan belongs to another NBT "
                    + "or reach profile."
            );
        }
        return snapshot;
    }

    public static void save(
        Path file,
        CircularBuildReachTopology.Snapshot snapshot
    ) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(snapshot, "snapshot");
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException(
                "The build reach plan has no parent directory."
            );
        }
        Files.createDirectories(parent);
        byte[] data = GSON.toJson(snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (data.length <= 0 || data.length > MAX_FILE_BYTES) {
            throw new IOException(
                "The compiled build reach plan exceeds its size limit."
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

    private static void requireIdentity(String compactPlanSha256)
        throws IOException {
        if (!FileFingerprint.isSha256(compactPlanSha256)) {
            throw new IOException(
                "Cannot identify a build reach plan without a valid hash."
            );
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
                "The build reach profile must be finite and positive."
            );
        }
    }
}
