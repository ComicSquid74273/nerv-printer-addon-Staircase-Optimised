package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtTimingSummaryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndReadsFirstCompletedCycle() throws Exception {
        NbtTimingSummary.CycleCompletion completion = completion(
            "print-job",
            7L,
            "art.nbt",
            "_generated_compact/art_compact_circular_u.nbt",
            1_000L,
            63_345L,
            false,
            "MasterBot",
            3
        );

        NbtTimingSummary.WriteResult result =
            NbtTimingSummary.recordCycle(temporaryDirectory, completion);

        assertEquals(
            NbtTimingSummary.WriteStatus.INSERTED,
            result.status()
        );
        assertTrue(result.changed());
        assertEquals(62_345L, result.record().elapsedMs());
        assertEquals("1m 2.345s", result.record().elapsedText());

        NbtTimingSummary.Summary restored =
            NbtTimingSummary.read(temporaryDirectory);
        assertEquals(NbtTimingSummary.SCHEMA_VERSION, restored.schemaVersion());
        assertEquals(List.of(result.record()), restored.records());
        assertEquals(
            result.record(),
            restored.find("print-job", 7L).orElseThrow()
        );

        String json = Files.readString(
            NbtTimingSummary.summaryPath(temporaryDirectory),
            StandardCharsets.UTF_8
        );
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"startedAtUtc\":"));
        assertTrue(json.contains("\"completedAtUtc\":"));
        assertTrue(json.contains("\"elapsed\": \"1m 2.345s\""));
        assertTrue(json.contains(System.lineSeparator()));
    }

    @Test
    void preservesMultipleCyclesInInsertionOrder() throws Exception {
        NbtTimingSummary.recordCycle(
            temporaryDirectory,
            completion(
                "job-a",
                0L,
                "first.nbt",
                null,
                100L,
                200L,
                false,
                null,
                1
            )
        );
        NbtTimingSummary.recordCycle(
            temporaryDirectory,
            completion(
                "job-a",
                1L,
                "second.nbt",
                "second_generated.nbt",
                300L,
                900L,
                true,
                "Coordinator",
                2
            )
        );
        NbtTimingSummary.recordCycle(
            temporaryDirectory,
            completion(
                "job-b",
                0L,
                "third.nbt",
                null,
                1_000L,
                2_000L,
                false,
                null,
                1
            )
        );

        NbtTimingSummary.Summary summary =
            NbtTimingSummary.read(temporaryDirectory);

        assertEquals(
            List.of("job-a:0", "job-a:1", "job-b:0"),
            summary.records().stream()
                .map(NbtTimingSummary.CycleTiming::cycleKey)
                .toList()
        );
        assertEquals(
            "second_generated.nbt",
            summary.find("job-a", 1L)
                .orElseThrow()
                .optionalPrintingNbt()
                .orElseThrow()
        );
        assertTrue(summary.find("job-a", 1L).orElseThrow().recovered());
    }

    @Test
    void identicalCompletionRetryIsAnExactlyOnceNoOp() throws Exception {
        NbtTimingSummary.CycleCompletion completion = completion(
            "stable-job",
            2L,
            "same.nbt",
            null,
            1_000L,
            2_000L,
            true,
            "Bot",
            1
        );
        NbtTimingSummary.recordCycle(temporaryDirectory, completion);
        Path summaryPath = NbtTimingSummary.summaryPath(temporaryDirectory);
        byte[] before = Files.readAllBytes(summaryPath);

        NbtTimingSummary.WriteResult retry =
            NbtTimingSummary.recordCycle(temporaryDirectory, completion);

        assertEquals(
            NbtTimingSummary.WriteStatus.UNCHANGED,
            retry.status()
        );
        assertFalse(retry.changed());
        assertEquals(1, retry.summary().records().size());
        assertTrue(java.util.Arrays.equals(
            before,
            Files.readAllBytes(summaryPath)
        ));
    }

    @Test
    void conflictingRecordRetryCannotRewriteTheFirstCompletion()
        throws Exception {
        NbtTimingSummary.CycleCompletion original = completion(
            "stable-job",
            5L,
            "map.nbt",
            null,
            100L,
            200L,
            false,
            null,
            1
        );
        NbtTimingSummary.recordCycle(temporaryDirectory, original);
        Path summaryPath = NbtTimingSummary.summaryPath(temporaryDirectory);
        byte[] before = Files.readAllBytes(summaryPath);

        IOException failure = assertThrows(
            IOException.class,
            () -> NbtTimingSummary.recordCycle(
                temporaryDirectory,
                completion(
                    "stable-job",
                    5L,
                    "map.nbt",
                    null,
                    100L,
                    250L,
                    true,
                    null,
                    1
                )
            )
        );

        assertTrue(failure.getMessage().contains("already recorded"));
        assertTrue(java.util.Arrays.equals(
            before,
            Files.readAllBytes(summaryPath)
        ));
        assertEquals(
            200L,
            NbtTimingSummary.read(temporaryDirectory)
                .find("stable-job", 5L)
                .orElseThrow()
                .completedAtMs()
        );
    }

    @Test
    void explicitUpsertReplacesOneRecordWithoutAddingADuplicate()
        throws Exception {
        NbtTimingSummary.recordCycle(
            temporaryDirectory,
            completion(
                "repairable",
                4L,
                "map.nbt",
                null,
                10L,
                20L,
                false,
                null,
                1
            )
        );
        NbtTimingSummary.CycleCompletion correction = completion(
            "repairable",
            4L,
            "map.nbt",
            "generated.nbt",
            10L,
            40L,
            true,
            "Master",
            4
        );

        NbtTimingSummary.WriteResult result =
            NbtTimingSummary.upsertCycle(temporaryDirectory, correction);

        assertEquals(
            NbtTimingSummary.WriteStatus.UPDATED,
            result.status()
        );
        assertEquals(1, result.summary().records().size());
        NbtTimingSummary.CycleTiming stored =
            result.summary().find("repairable", 4L).orElseThrow();
        assertEquals(30L, stored.elapsedMs());
        assertTrue(stored.recovered());
        assertEquals(4, stored.botCount());
        assertEquals("generated.nbt", stored.printingNbt());

        assertEquals(
            NbtTimingSummary.WriteStatus.UNCHANGED,
            NbtTimingSummary.upsertCycle(
                temporaryDirectory,
                correction
            ).status()
        );
    }

    @Test
    void malformedExistingSummaryIsReportedAndNeverClobbered()
        throws Exception {
        Path summaryPath = NbtTimingSummary.summaryPath(temporaryDirectory);
        byte[] malformed = (
            "{ \"schemaVersion\": 1, \"records\": [ not-json ] }"
        ).getBytes(StandardCharsets.UTF_8);
        Files.write(summaryPath, malformed);

        IOException readFailure = assertThrows(
            IOException.class,
            () -> NbtTimingSummary.read(temporaryDirectory)
        );
        IOException writeFailure = assertThrows(
            IOException.class,
            () -> NbtTimingSummary.recordCycle(
                temporaryDirectory,
                completion(
                    "job",
                    0L,
                    "map.nbt",
                    null,
                    1L,
                    2L,
                    false,
                    null,
                    1
                )
            )
        );

        assertTrue(readFailure.getMessage().contains("Malformed"));
        assertTrue(writeFailure.getMessage().contains("Malformed"));
        assertTrue(java.util.Arrays.equals(
            malformed,
            Files.readAllBytes(summaryPath)
        ));
    }

    @Test
    void roundTripsEscapedUnicodeAndPlatformStylePaths() throws Exception {
        String source = "D:\\maps\\quote-\"雪\"\\line\nbreak.nbt";
        String printing = "_generated\\u-shaped\\雪_compact.nbt";
        String coordinator = "Bot \"A\" \\\\ north";

        NbtTimingSummary.recordCycle(
            temporaryDirectory,
            completion(
                "job:\"雪\"",
                12L,
                source,
                printing,
                10L,
                20L,
                true,
                coordinator,
                2
            )
        );

        NbtTimingSummary.CycleTiming restored =
            NbtTimingSummary.read(temporaryDirectory)
                .find("job:\"雪\"", 12L)
                .orElseThrow();
        assertEquals(source, restored.sourceNbt());
        assertEquals(printing, restored.printingNbt());
        assertEquals(coordinator, restored.coordinator());

        String json = Files.readString(
            NbtTimingSummary.summaryPath(temporaryDirectory)
        );
        assertTrue(json.contains("\\\\maps\\\\quote-\\\""));
        assertTrue(json.contains("line\\nbreak.nbt"));
    }

    @Test
    void computesDurationFromValidatedCompletionTimestamps() {
        NbtTimingSummary.CycleCompletion completion = completion(
            "duration",
            0L,
            "map.nbt",
            null,
            1_000L,
            90_062_007L,
            false,
            null,
            1
        );

        assertEquals(90_061_007L, completion.elapsedMs());
        NbtTimingSummary.CycleTiming timing = new NbtTimingSummary.CycleTiming(
            "duration:0",
            "duration",
            0L,
            "map.nbt",
            null,
            1_000L,
            90_062_007L,
            90_061_007L,
            false,
            null,
            1
        );
        assertEquals("1d 1h 1m 1.007s", timing.elapsedText());
        assertThrows(
            IllegalArgumentException.class,
            () -> completion(
                "bad",
                0L,
                "map.nbt",
                null,
                2_000L,
                1_999L,
                false,
                null,
                1
            )
        );
    }

    @Test
    void serializesConcurrentSameProcessWritersWithoutDroppingCycles()
        throws Exception {
        int taskCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<NbtTimingSummary.WriteResult>> tasks =
                IntStream.range(0, taskCount)
                    .<Callable<NbtTimingSummary.WriteResult>>mapToObj(index ->
                        () -> NbtTimingSummary.recordCycle(
                            temporaryDirectory,
                            completion(
                                "parallel",
                                index,
                                "map-" + index + ".nbt",
                                null,
                                index,
                                index + 10L,
                                false,
                                null,
                                1
                            )
                        )
                    )
                    .toList();
            List<Future<NbtTimingSummary.WriteResult>> futures =
                executor.invokeAll(tasks);
            for (Future<NbtTimingSummary.WriteResult> future : futures) {
                assertEquals(
                    NbtTimingSummary.WriteStatus.INSERTED,
                    future.get().status()
                );
            }
        } finally {
            executor.shutdownNow();
        }

        NbtTimingSummary.Summary summary =
            NbtTimingSummary.read(temporaryDirectory);
        assertEquals(taskCount, summary.records().size());
        IntStream.range(0, taskCount).forEach(index ->
            assertTrue(summary.find("parallel", index).isPresent())
        );
    }

    private static NbtTimingSummary.CycleCompletion completion(
        String jobId,
        long generation,
        String sourceNbt,
        String printingNbt,
        long startedAtMs,
        long completedAtMs,
        boolean recovered,
        String coordinator,
        int botCount
    ) {
        return new NbtTimingSummary.CycleCompletion(
            jobId,
            generation,
            sourceNbt,
            printingNbt,
            startedAtMs,
            completedAtMs,
            recovered,
            coordinator,
            botCount
        );
    }
}
