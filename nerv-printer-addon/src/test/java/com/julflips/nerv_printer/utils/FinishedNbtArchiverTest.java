package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishedNbtArchiverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void archivesRawAndGeneratedNbtAsOnePair() throws Exception {
        Path source = write("picture.nbt", "raw");
        Path generated = write(
            "_generated_compact/picture_compact_circular_u.nbt",
            "compact"
        );

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            generated
        );

        Path finished = temporaryDirectory.resolve("_finished_maps");
        assertEquals(FinishedNbtArchiver.Status.ARCHIVED, result.status());
        assertTrue(result.moved());
        assertEquals(OptionalInt.of(0), result.collisionIndex());
        assertEquals(finished, result.finishedDirectory());
        assertEquals(finished.resolve("picture.nbt"), result.archivedSource());
        assertEquals(
            finished.resolve("picture_compact_circular_u.nbt"),
            result.archivedGenerated().orElseThrow()
        );
        assertEquals("raw", Files.readString(result.archivedSource()));
        assertEquals(
            "compact",
            Files.readString(result.archivedGenerated().orElseThrow())
        );
        assertFalse(Files.exists(source));
        assertFalse(Files.exists(generated));
    }

    @Test
    void archivesRawNbtWithoutAGeneratedArtifact() throws Exception {
        Path source = write("raw-only.nbt", "raw-only");

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            null
        );

        assertTrue(result.moved());
        assertTrue(result.archivedGenerated().isEmpty());
        assertEquals("raw-only", Files.readString(result.archivedSource()));
        assertEquals(1, entryCount(result.finishedDirectory()));
    }

    @Test
    void appliesOneCommonSuffixWhenEitherDestinationCollides()
        throws Exception {
        Path source = write("poster.nbt", "new-raw");
        Path generated = write(
            "_generated_compact/poster_compact_circular_u.nbt",
            "new-compact"
        );
        Path finished = temporaryDirectory.resolve("_finished_maps");
        Files.createDirectories(finished);
        Path existing = finished.resolve("poster.nbt");
        Files.writeString(existing, "keep-me");

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            generated
        );

        assertEquals(OptionalInt.of(1), result.collisionIndex());
        assertEquals(finished.resolve("poster (1).nbt"), result.archivedSource());
        assertEquals(
            finished.resolve("poster_compact_circular_u (1).nbt"),
            result.archivedGenerated().orElseThrow()
        );
        assertEquals("keep-me", Files.readString(existing));
        assertEquals("new-raw", Files.readString(result.archivedSource()));
        assertEquals(
            "new-compact",
            Files.readString(result.archivedGenerated().orElseThrow())
        );
    }

    @Test
    void generatedNameCollisionAlsoSuffixesTheWholePair() throws Exception {
        Path source = write("canvas.nbt", "new-raw");
        Path generated = write(
            "_generated_compact/canvas_compact_circular_u.nbt",
            "new-compact"
        );
        Path finished = temporaryDirectory.resolve("_finished_maps");
        Files.createDirectories(finished);
        Files.writeString(
            finished.resolve("canvas_compact_circular_u.nbt"),
            "old-compact"
        );

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            generated
        );

        assertEquals(OptionalInt.of(1), result.collisionIndex());
        assertEquals(
            finished.resolve("canvas (1).nbt"),
            result.archivedSource()
        );
        assertEquals(
            finished.resolve("canvas_compact_circular_u (1).nbt"),
            result.archivedGenerated().orElseThrow()
        );
        assertEquals(
            "old-compact",
            Files.readString(
                finished.resolve("canvas_compact_circular_u.nbt")
            )
        );
    }

    @Test
    void locatesCollisionSuffixedPairByHashAfterCheckpointGap()
        throws Exception {
        Path source = write("recover.nbt", "new-raw");
        Path generated = write(
            "_generated_compact/recover_compact_circular_u.nbt",
            "new-compact"
        );
        Path finished = temporaryDirectory.resolve("_finished_maps");
        Files.createDirectories(finished);
        Files.writeString(finished.resolve("recover.nbt"), "old-raw");

        FinishedNbtArchiver.Result archived =
            FinishedNbtArchiver.archive(
                temporaryDirectory,
                source,
                generated
            );
        FinishedNbtArchiver.LocatedPair located =
            FinishedNbtArchiver.locateArchivedPair(
                temporaryDirectory,
                "recover.nbt",
                "recover_compact_circular_u.nbt",
                FileFingerprint.sha256(archived.archivedSource())
            ).orElseThrow();

        assertEquals(1, located.collisionIndex());
        assertEquals(archived.archivedSource(), located.archivedSource());
        assertEquals(
            archived.archivedGenerated(),
            located.archivedGenerated()
        );
    }

    @Test
    void archivedHashRecoveryFailsClosedWhenTwoCandidatesMatch()
        throws Exception {
        Path finished = temporaryDirectory.resolve("_finished_maps");
        Files.createDirectories(finished);
        Files.writeString(finished.resolve("ambiguous.nbt"), "same");
        Files.writeString(finished.resolve("ambiguous (1).nbt"), "same");

        assertThrows(
            IOException.class,
            () -> FinishedNbtArchiver.locateArchivedPair(
                temporaryDirectory,
                "ambiguous.nbt",
                null,
                FileFingerprint.sha256(
                    finished.resolve("ambiguous.nbt")
                )
            )
        );
    }

    @Test
    void hashRecoveryCompletesPartnerAfterProcessDiesBetweenMoves()
        throws Exception {
        Path source = write("interrupted.nbt", "raw");
        Path generated = write(
            "_generated_compact/interrupted_compact_circular_u.nbt",
            "compact"
        );
        String sourceHash = FileFingerprint.sha256(source);
        AtomicInteger moves = new AtomicInteger();

        assertThrows(
            AssertionError.class,
            () -> FinishedNbtArchiver.archive(
                temporaryDirectory,
                source,
                generated,
                (moveSource, destination, options) -> {
                    if (moves.getAndIncrement() == 1) {
                        throw new AssertionError("simulated process death");
                    }
                    Files.move(moveSource, destination, options);
                }
            )
        );

        Path reservedGenerated = temporaryDirectory.resolve(
            "_finished_maps/interrupted_compact_circular_u.nbt"
        );
        assertTrue(Files.exists(reservedGenerated));
        assertEquals(0L, Files.size(reservedGenerated));
        assertTrue(Files.exists(generated));

        FinishedNbtArchiver.LocatedPair recovered =
            FinishedNbtArchiver.locateArchivedPair(
                temporaryDirectory,
                "interrupted.nbt",
                "interrupted_compact_circular_u.nbt",
                sourceHash
            ).orElseThrow();

        assertEquals("compact", Files.readString(
            recovered.archivedGenerated().orElseThrow()
        ));
        assertFalse(Files.exists(generated));
    }

    @Test
    void deduplicatesTheSameFileUsedForBothRoles() throws Exception {
        Path source = write("already-compact.nbt", "one-file");

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            source
        );

        assertTrue(result.moved());
        assertEquals(
            result.archivedSource(),
            result.archivedGenerated().orElseThrow()
        );
        assertEquals(1, entryCount(result.finishedDirectory()));
        assertEquals("one-file", Files.readString(result.archivedSource()));
    }

    @Test
    void returnedPathsMakeARepeatedArchiveAnIdempotentNoOp()
        throws Exception {
        Path source = write("repeat.nbt", "raw");
        Path generated = write(
            "_generated_compact/repeat_compact_circular_u.nbt",
            "compact"
        );
        FinishedNbtArchiver.Result first = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            generated
        );

        FinishedNbtArchiver.Result repeated = FinishedNbtArchiver.archive(
            temporaryDirectory,
            first.archivedSource(),
            first.archivedGenerated().orElseThrow()
        );

        assertEquals(
            FinishedNbtArchiver.Status.ALREADY_ARCHIVED,
            repeated.status()
        );
        assertFalse(repeated.moved());
        assertTrue(repeated.collisionIndex().isEmpty());
        assertEquals(first.archivedSource(), repeated.archivedSource());
        assertEquals(
            first.archivedGenerated(),
            repeated.archivedGenerated()
        );
        assertEquals(2, entryCount(repeated.finishedDirectory()));
    }

    @Test
    void missingGeneratedInputFailsBeforeMovingTheSource() throws Exception {
        Path source = write("source.nbt", "raw");
        Path missingGenerated = temporaryDirectory
            .resolve("_generated_compact/missing.nbt");

        assertThrows(
            NoSuchFileException.class,
            () -> FinishedNbtArchiver.archive(
                temporaryDirectory,
                source,
                missingGenerated
            )
        );

        assertTrue(Files.exists(source));
        assertEquals("raw", Files.readString(source));
        assertFalse(Files.exists(temporaryDirectory.resolve("_finished_maps")));
    }

    @Test
    void rejectsAPartiallyArchivedPairWithoutMovingTheSource()
        throws Exception {
        Path source = write("partial.nbt", "raw");
        Path archivedGenerated = write(
            "_finished_maps/partial_compact_circular_u.nbt",
            "compact"
        );

        IOException failure = assertThrows(
            IOException.class,
            () -> FinishedNbtArchiver.archive(
                temporaryDirectory,
                source,
                archivedGenerated
            )
        );

        assertTrue(failure.getMessage().contains("partially archived"));
        assertEquals("raw", Files.readString(source));
        assertEquals("compact", Files.readString(archivedGenerated));
    }

    @Test
    void missingSourceInputFailsWithoutCreatingTheFinishedFolder() {
        Path missingSource = temporaryDirectory.resolve("missing-source.nbt");

        assertThrows(
            NoSuchFileException.class,
            () -> FinishedNbtArchiver.archive(
                temporaryDirectory,
                missingSource,
                null
            )
        );

        assertFalse(Files.exists(temporaryDirectory.resolve("_finished_maps")));
    }

    @Test
    void failureMovingTheSecondFileRollsBackTheFirst() throws Exception {
        Path source = write("rollback.nbt", "raw");
        Path generated = write(
            "_generated_compact/rollback_compact_circular_u.nbt",
            "compact"
        );
        Path normalizedGenerated = generated.toAbsolutePath().normalize();
        FinishedNbtArchiver.MoveExecutor failGenerated =
            (moveSource, destination, options) -> {
                if (moveSource.equals(normalizedGenerated)) {
                    throw new IOException("simulated generated move failure");
                }
                Files.move(moveSource, destination, options);
            };

        IOException failure = assertThrows(
            IOException.class,
            () -> FinishedNbtArchiver.archive(
                temporaryDirectory,
                source,
                generated,
                failGenerated
            )
        );

        assertTrue(failure.getMessage().contains("rolled back"));
        assertEquals("raw", Files.readString(source));
        assertEquals("compact", Files.readString(generated));
        assertEquals(
            0,
            entryCount(temporaryDirectory.resolve("_finished_maps"))
        );
    }

    @Test
    void fallsBackWhenAtomicMoveIsUnsupported() throws Exception {
        Path source = write("fallback.nbt", "raw");
        AtomicInteger atomicAttempts = new AtomicInteger();
        FinishedNbtArchiver.MoveExecutor noAtomicMoves =
            (moveSource, destination, options) -> {
                if (hasOption(options, StandardCopyOption.ATOMIC_MOVE)) {
                    atomicAttempts.incrementAndGet();
                    throw new AtomicMoveNotSupportedException(
                        moveSource.toString(),
                        destination.toString(),
                        "simulated"
                    );
                }
                Files.move(moveSource, destination, options);
            };

        FinishedNbtArchiver.Result result = FinishedNbtArchiver.archive(
            temporaryDirectory,
            source,
            null,
            noAtomicMoves
        );

        assertEquals(1, atomicAttempts.get());
        assertEquals("raw", Files.readString(result.archivedSource()));
    }

    private Path write(String relativePath, String content) throws IOException {
        Path path = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private static long entryCount(Path directory) throws IOException {
        if (!Files.exists(directory)) return 0;
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.count();
        }
    }

    private static boolean hasOption(
        CopyOption[] options,
        CopyOption expected
    ) {
        for (CopyOption option : options) {
            if (option == expected) return true;
        }
        return false;
    }
}
