package com.julflips.nerv_printer.utils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Moves a source NBT and its optional generated compact NBT into one finished
 * folder transaction.
 *
 * <p>Destination names are reserved with create-new semantics before either
 * source is moved. If either unsuffixed name is occupied, the same numeric
 * suffix is applied to both names. This keeps the original/generated pair
 * identifiable and never replaces an existing finished file.</p>
 */
public final class FinishedNbtArchiver {
    public static final String FINISHED_DIRECTORY_NAME = "_finished_maps";

    private FinishedNbtArchiver() {
    }

    public enum Status {
        ARCHIVED,
        ALREADY_ARCHIVED
    }

    /**
     * {@code collisionIndex} is present for a move: zero means the original
     * names were free, while a positive value is the shared " (n)" suffix.
     * It is empty for an already-archived no-op.
     */
    public record Result(
        Status status,
        Path finishedDirectory,
        Path archivedSource,
        Optional<Path> archivedGenerated,
        OptionalInt collisionIndex
    ) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(finishedDirectory, "finishedDirectory");
            Objects.requireNonNull(archivedSource, "archivedSource");
            Objects.requireNonNull(archivedGenerated, "archivedGenerated");
            Objects.requireNonNull(collisionIndex, "collisionIndex");
            if (status == Status.ARCHIVED && collisionIndex.isEmpty()) {
                throw new IllegalArgumentException(
                    "An archived result must include its collision index."
                );
            }
            if (status == Status.ALREADY_ARCHIVED && collisionIndex.isPresent()) {
                throw new IllegalArgumentException(
                    "An already-archived result cannot include a collision index."
                );
            }
        }

        public boolean moved() {
            return status == Status.ARCHIVED;
        }
    }

    public record LocatedPair(
        Path archivedSource,
        Optional<Path> archivedGenerated,
        int collisionIndex
    ) {
        public LocatedPair {
            Objects.requireNonNull(archivedSource, "archivedSource");
            Objects.requireNonNull(
                archivedGenerated,
                "archivedGenerated"
            );
            if (collisionIndex < 0) {
                throw new IllegalArgumentException(
                    "collisionIndex cannot be negative."
                );
            }
        }
    }

    /**
     * Recovers an archive destination after a process stopped between the
     * filesystem move and its coordination checkpoint. The source digest is
     * authoritative because a pre-existing finished file can force a numeric
     * suffix. Ambiguous digest matches fail closed.
     */
    public static Optional<LocatedPair> locateArchivedPair(
        Path mapFolder,
        String logicalSourceName,
        String logicalGeneratedName,
        String sourceSha256
    ) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        requireSimpleFileName(logicalSourceName, "logical source");
        if (logicalGeneratedName != null) {
            requireSimpleFileName(
                logicalGeneratedName,
                "logical generated file"
            );
        }
        if (!FileFingerprint.isSha256(sourceSha256)) {
            throw new IllegalArgumentException(
                "sourceSha256 must be a lowercase SHA-256 digest."
            );
        }

        Path finishedDirectory = normalize(mapFolder)
            .resolve(FINISHED_DIRECTORY_NAME);
        if (!Files.isDirectory(finishedDirectory)) {
            return Optional.empty();
        }

        List<LocatedSource> matches = new ArrayList<>();
        try (var entries = Files.list(finishedDirectory)) {
            for (Path candidate : entries
                .filter(path -> Files.isRegularFile(
                    path,
                    LinkOption.NOFOLLOW_LINKS
                ))
                .toList()) {
                OptionalInt collisionIndex = collisionIndexForName(
                    logicalSourceName,
                    candidate.getFileName().toString()
                );
                if (collisionIndex.isEmpty()) continue;
                if (sourceSha256.equals(
                    FileFingerprint.sha256(candidate)
                )) {
                    matches.add(
                        new LocatedSource(
                            candidate,
                            collisionIndex.getAsInt()
                        )
                    );
                }
            }
        }
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() > 1) {
            throw new IOException(
                "Multiple archived NBTs match logical source "
                    + logicalSourceName + " and its SHA-256."
            );
        }

        LocatedSource source = matches.getFirst();
        Optional<Path> generated;
        if (logicalGeneratedName == null) {
            generated = Optional.empty();
        } else if (logicalGeneratedName.equals(logicalSourceName)) {
            generated = Optional.of(source.path());
        } else {
            Path generatedPath = finishedDirectory.resolve(
                suffixedName(
                    logicalGeneratedName,
                    source.collisionIndex()
                )
            );
            if (Files.isRegularFile(
                generatedPath,
                LinkOption.NOFOLLOW_LINKS
            ) && Files.size(generatedPath) == 0L) {
                Path originalGenerated = normalize(mapFolder)
                    .resolve("_generated_compact")
                    .resolve(logicalGeneratedName)
                    .normalize();
                if (!Files.isRegularFile(
                    originalGenerated,
                    LinkOption.NOFOLLOW_LINKS
                ) || Files.size(originalGenerated) == 0L) {
                    throw new IOException(
                        "Archived source was found, but its generated "
                            + "destination is an incomplete reservation and "
                            + "the original generated NBT is unavailable: "
                            + generatedPath
                    );
                }
                moveWithAtomicFallback(
                    originalGenerated,
                    generatedPath,
                    Files::move,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
            if (!Files.isRegularFile(
                generatedPath,
                LinkOption.NOFOLLOW_LINKS
            ) || Files.size(generatedPath) == 0L) {
                throw new IOException(
                    "Archived source was found, but its generated compact "
                        + "pair is missing or empty: " + generatedPath
                );
            }
            generated = Optional.of(generatedPath);
        }
        return Optional.of(
            new LocatedPair(
                source.path(),
                generated,
                source.collisionIndex()
            )
        );
    }

    /**
     * Archives {@code sourceNbt} plus {@code generatedCompactNbt}, when
     * supplied. Passing the same file for both arguments moves it only once
     * and reports the same destination for both roles.
     *
     * <p>Passing paths returned by a previous result is idempotent and returns
     * {@link Status#ALREADY_ARCHIVED}. A partially archived pair is rejected
     * so a caller cannot silently split the two files across name groups.</p>
     */
    public static Result archive(
        Path mapFolder,
        Path sourceNbt,
        Path generatedCompactNbt
    ) throws IOException {
        return archive(
            mapFolder,
            sourceNbt,
            generatedCompactNbt,
            (source, destination, options) -> {
                Files.move(source, destination, options);
            }
        );
    }

    static Result archive(
        Path mapFolder,
        Path sourceNbt,
        Path generatedCompactNbt,
        MoveExecutor moveExecutor
    ) throws IOException {
        Objects.requireNonNull(mapFolder, "mapFolder");
        Objects.requireNonNull(sourceNbt, "sourceNbt");
        Objects.requireNonNull(moveExecutor, "moveExecutor");

        Path absoluteMapFolder = normalize(mapFolder);
        Path finishedDirectory = absoluteMapFolder
            .resolve(FINISHED_DIRECTORY_NAME)
            .normalize();
        Path source = requireRegularFile(sourceNbt, "source NBT");
        Path generated = generatedCompactNbt == null
            ? null
            : requireRegularFile(generatedCompactNbt, "generated compact NBT");
        boolean generatedAliasesSource =
            generated != null && sameFile(source, generated);

        List<Path> uniqueSources = new ArrayList<>();
        uniqueSources.add(source);
        if (generated != null && !generatedAliasesSource) {
            uniqueSources.add(generated);
        }

        long archivedSourceCount = uniqueSources.stream()
            .filter(path -> isDirectChild(path, finishedDirectory))
            .count();
        if (archivedSourceCount == uniqueSources.size()) {
            return new Result(
                Status.ALREADY_ARCHIVED,
                finishedDirectory,
                source,
                Optional.ofNullable(generated),
                OptionalInt.empty()
            );
        }
        if (archivedSourceCount != 0) {
            throw new IOException(
                "Cannot archive a partially archived source/generated NBT pair."
            );
        }

        ensureDistinctDestinationNames(uniqueSources);
        Files.createDirectories(finishedDirectory);
        Reservation reservation = reserveDestinations(
            finishedDirectory,
            uniqueSources
        );

        List<MovePair> completedMoves = new ArrayList<>();
        Set<Path> unconsumedReservations =
            new LinkedHashSet<>(reservation.destinations());
        try {
            for (int index = 0; index < uniqueSources.size(); index++) {
                Path input = uniqueSources.get(index);
                Path destination = reservation.destinations().get(index);
                moveIntoReservation(input, destination, moveExecutor);
                completedMoves.add(new MovePair(input, destination));
                unconsumedReservations.remove(destination);
            }
        } catch (IOException moveFailure) {
            List<IOException> rollbackFailures = rollback(
                completedMoves,
                moveExecutor
            );
            cleanupUnusedReservations(
                uniqueSources,
                reservation.destinations(),
                unconsumedReservations,
                rollbackFailures
            );

            IOException transactionFailure = new IOException(
                rollbackFailures.isEmpty()
                    ? "Failed to archive the NBT pair; completed moves were rolled back."
                    : "Failed to archive the NBT pair and could not fully roll it back.",
                moveFailure
            );
            rollbackFailures.forEach(transactionFailure::addSuppressed);
            throw transactionFailure;
        }

        Path archivedSource = reservation.destinations().getFirst();
        Optional<Path> archivedGenerated;
        if (generated == null) {
            archivedGenerated = Optional.empty();
        } else if (generatedAliasesSource) {
            archivedGenerated = Optional.of(archivedSource);
        } else {
            archivedGenerated = Optional.of(reservation.destinations().get(1));
        }
        return new Result(
            Status.ARCHIVED,
            finishedDirectory,
            archivedSource,
            archivedGenerated,
            OptionalInt.of(reservation.collisionIndex())
        );
    }

    @FunctionalInterface
    interface MoveExecutor {
        void move(
            Path source,
            Path destination,
            CopyOption... options
        ) throws IOException;
    }

    private record Reservation(List<Path> destinations, int collisionIndex) {
        private Reservation {
            destinations = List.copyOf(destinations);
        }
    }

    private record MovePair(Path source, Path destination) {
    }

    private record LocatedSource(Path path, int collisionIndex) {
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path requireRegularFile(Path input, String label)
        throws IOException {
        Path normalized = normalize(input);
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(
                normalized.toString(),
                null,
                "Missing " + label
            );
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileSystemException(
                normalized.toString(),
                null,
                "The " + label + " is not a regular file"
            );
        }
        return normalized;
    }

    private static boolean sameFile(Path first, Path second)
        throws IOException {
        return first.equals(second) || Files.isSameFile(first, second);
    }

    private static boolean isDirectChild(Path path, Path directory) {
        return directory.equals(path.getParent());
    }

    private static void ensureDistinctDestinationNames(List<Path> sources)
        throws IOException {
        Set<Path> fileNames = new LinkedHashSet<>();
        for (Path source : sources) {
            Path fileName = source.getFileName();
            if (fileName == null) {
                throw new IOException("An NBT source has no file name.");
            }
            if (!fileNames.add(fileName)) {
                throw new IOException(
                    "Distinct NBT sources cannot share the same file name: "
                        + fileName
                );
            }
        }
    }

    private static Reservation reserveDestinations(
        Path finishedDirectory,
        List<Path> sources
    ) throws IOException {
        for (int collisionIndex = 0; ; collisionIndex++) {
            List<Path> destinations = new ArrayList<>(sources.size());
            for (Path source : sources) {
                destinations.add(
                    finishedDirectory.resolve(
                        suffixedName(source.getFileName().toString(), collisionIndex)
                    )
                );
            }

            List<Path> reservations = new ArrayList<>(destinations.size());
            try {
                for (Path destination : destinations) {
                    Files.createFile(destination);
                    reservations.add(destination);
                }
                return new Reservation(destinations, collisionIndex);
            } catch (FileAlreadyExistsException collision) {
                deleteReservations(reservations, collision);
                if (collisionIndex == Integer.MAX_VALUE) {
                    throw new IOException(
                        "Could not find collision-free finished NBT names.",
                        collision
                    );
                }
            } catch (IOException failure) {
                deleteReservations(reservations, failure);
                throw failure;
            }
        }
    }

    private static String suffixedName(String fileName, int collisionIndex) {
        if (collisionIndex == 0) return fileName;
        String suffix = " (" + collisionIndex + ")";
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) return fileName + suffix;
        return fileName.substring(0, extensionIndex)
            + suffix
            + fileName.substring(extensionIndex);
    }

    private static OptionalInt collisionIndexForName(
        String logicalName,
        String candidateName
    ) {
        if (candidateName.equals(logicalName)) return OptionalInt.of(0);
        int extensionIndex = logicalName.lastIndexOf('.');
        String base = extensionIndex <= 0
            ? logicalName
            : logicalName.substring(0, extensionIndex);
        String extension = extensionIndex <= 0
            ? ""
            : logicalName.substring(extensionIndex);
        String prefix = base + " (";
        if (!candidateName.startsWith(prefix)
            || !candidateName.endsWith(")" + extension)) {
            return OptionalInt.empty();
        }
        int numberEnd = candidateName.length() - extension.length() - 1;
        String number = candidateName.substring(
            prefix.length(),
            numberEnd
        );
        try {
            int parsed = Integer.parseInt(number);
            return parsed > 0
                ? OptionalInt.of(parsed)
                : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static void requireSimpleFileName(String name, String label) {
        Objects.requireNonNull(name, label);
        if (name.isBlank()
            || !Path.of(name).getFileName().toString().equals(name)
            || name.contains("/")
            || name.contains("\\")) {
            throw new IllegalArgumentException(
                label + " must be a simple file name."
            );
        }
    }

    private static void deleteReservations(
        List<Path> reservations,
        IOException originalFailure
    ) throws IOException {
        IOException cleanupFailure = null;
        for (int index = reservations.size() - 1; index >= 0; index--) {
            try {
                Files.deleteIfExists(reservations.get(index));
            } catch (IOException failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure != null) {
            cleanupFailure.addSuppressed(originalFailure);
            throw cleanupFailure;
        }
    }

    private static void moveIntoReservation(
        Path source,
        Path destination,
        MoveExecutor moveExecutor
    ) throws IOException {
        moveWithAtomicFallback(
            source,
            destination,
            moveExecutor,
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void moveWithAtomicFallback(
        Path source,
        Path destination,
        MoveExecutor moveExecutor,
        CopyOption... additionalOptions
    ) throws IOException {
        CopyOption[] atomicOptions =
            new CopyOption[additionalOptions.length + 1];
        System.arraycopy(
            additionalOptions,
            0,
            atomicOptions,
            0,
            additionalOptions.length
        );
        atomicOptions[additionalOptions.length] =
            StandardCopyOption.ATOMIC_MOVE;
        try {
            moveExecutor.move(source, destination, atomicOptions);
        } catch (AtomicMoveNotSupportedException atomicFailure) {
            try {
                moveExecutor.move(source, destination, additionalOptions);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }

    private static List<IOException> rollback(
        List<MovePair> completedMoves,
        MoveExecutor moveExecutor
    ) {
        List<IOException> failures = new ArrayList<>();
        for (int index = completedMoves.size() - 1; index >= 0; index--) {
            MovePair completed = completedMoves.get(index);
            boolean rollbackReserved = false;
            try {
                Files.createFile(completed.source());
                rollbackReserved = true;
                moveIntoReservation(
                    completed.destination(),
                    completed.source(),
                    moveExecutor
                );
            } catch (IOException failure) {
                failures.add(new IOException(
                    "Failed to restore " + completed.source() + ".",
                    failure
                ));
                if (rollbackReserved
                    && Files.exists(
                        completed.destination(),
                        LinkOption.NOFOLLOW_LINKS
                    )) {
                    try {
                        Files.deleteIfExists(completed.source());
                    } catch (IOException cleanupFailure) {
                        failures.add(cleanupFailure);
                    }
                }
            }
        }
        return failures;
    }

    private static void cleanupUnusedReservations(
        List<Path> sources,
        List<Path> destinations,
        Set<Path> unconsumedReservations,
        List<IOException> failures
    ) {
        for (Path reservation : unconsumedReservations) {
            int index = destinations.indexOf(reservation);
            if (index < 0) continue;
            Path source = sources.get(index);
            try {
                if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(
                        reservation,
                        LinkOption.NOFOLLOW_LINKS
                    )
                    && Files.size(reservation) == 0L) {
                    Files.deleteIfExists(reservation);
                }
            } catch (IOException cleanupFailure) {
                failures.add(cleanupFailure);
            }
        }
    }
}
