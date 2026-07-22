package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable, process-coordinated summary of completed NBT print cycles.
 *
 * <p>Each record is identified by the collision-free pair
 * {@code (jobId, generation)}. {@link #recordCycle(Path, CycleCompletion)}
 * provides first-writer-wins, exactly-once behavior for normal completion
 * retries. {@link #upsertCycle(Path, CycleCompletion)} is available for an
 * intentional correction of an existing record.</p>
 *
 * <p>Writes use a unique temporary file in the summary directory followed by
 * an atomic replacement when the file system supports it. A companion lock
 * file serializes cooperating printer processes, while an in-process lock
 * prevents overlapping Java file locks from racing in the same client.</p>
 */
public final class NbtTimingSummary {
    public static final int SCHEMA_VERSION = 1;
    public static final String FILE_NAME = "nbt_timing_summary.json";
    public static final String LOCK_FILE_NAME = ".nbt_timing_summary.lock";

    private static final Gson PRETTY_GSON =
        new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter UTC_FORMATTER =
        DateTimeFormatter.ISO_INSTANT;
    private static final Map<Path, ReentrantLock> JVM_LOCKS =
        new ConcurrentHashMap<>();

    private NbtTimingSummary() {
    }

    public enum WriteStatus {
        INSERTED,
        UPDATED,
        UNCHANGED
    }

    /**
     * Input supplied when a complete map cycle has reached its verified-clear
     * point. {@code printingNbt} may be {@code null} when the source NBT was
     * printed directly. {@code coordinator} may be {@code null} for a solo
     * run.
     */
    public record CycleCompletion(
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
        public CycleCompletion {
            jobId = requireNonBlank(jobId, "jobId");
            if (generation < 0L) {
                throw new IllegalArgumentException(
                    "generation must not be negative."
                );
            }
            sourceNbt = requireNonBlank(sourceNbt, "sourceNbt");
            printingNbt = optionalNonBlank(printingNbt, "printingNbt");
            if (startedAtMs < 0L) {
                throw new IllegalArgumentException(
                    "startedAtMs must not be negative."
                );
            }
            if (completedAtMs < startedAtMs) {
                throw new IllegalArgumentException(
                    "completedAtMs must be at or after startedAtMs."
                );
            }
            coordinator = optionalNonBlank(coordinator, "coordinator");
            if (botCount < 1) {
                throw new IllegalArgumentException(
                    "botCount must be at least one."
                );
            }
        }

        public static CycleCompletion fromPaths(
            String jobId,
            long generation,
            Path sourceNbt,
            Path printingNbt,
            long startedAtMs,
            long completedAtMs,
            boolean recovered,
            String coordinator,
            int botCount
        ) {
            Objects.requireNonNull(sourceNbt, "sourceNbt");
            return new CycleCompletion(
                jobId,
                generation,
                sourceNbt.toString(),
                printingNbt == null ? null : printingNbt.toString(),
                startedAtMs,
                completedAtMs,
                recovered,
                coordinator,
                botCount
            );
        }

        public long elapsedMs() {
            return completedAtMs - startedAtMs;
        }
    }

    /**
     * One immutable row in the summary. Optional string fields are represented
     * as {@code null} in this low-level persistence model.
     */
    public record CycleTiming(
        String cycleKey,
        String jobId,
        long generation,
        String sourceNbt,
        String printingNbt,
        long startedAtMs,
        long completedAtMs,
        long elapsedMs,
        boolean recovered,
        String coordinator,
        int botCount
    ) {
        public CycleTiming {
            jobId = requireNonBlank(jobId, "jobId");
            if (generation < 0L) {
                throw new IllegalArgumentException(
                    "generation must not be negative."
                );
            }
            String expectedKey = NbtTimingSummary.cycleKey(
                jobId,
                generation
            );
            if (!expectedKey.equals(cycleKey)) {
                throw new IllegalArgumentException(
                    "cycleKey does not match jobId and generation."
                );
            }
            sourceNbt = requireNonBlank(sourceNbt, "sourceNbt");
            printingNbt = optionalNonBlank(printingNbt, "printingNbt");
            if (startedAtMs < 0L || completedAtMs < startedAtMs) {
                throw new IllegalArgumentException(
                    "The completion timestamps are not ordered."
                );
            }
            if (elapsedMs != completedAtMs - startedAtMs) {
                throw new IllegalArgumentException(
                    "elapsedMs does not match the completion timestamps."
                );
            }
            coordinator = optionalNonBlank(coordinator, "coordinator");
            if (botCount < 1) {
                throw new IllegalArgumentException(
                    "botCount must be at least one."
                );
            }
        }

        public Optional<String> optionalPrintingNbt() {
            return Optional.ofNullable(printingNbt);
        }

        public Optional<String> optionalCoordinator() {
            return Optional.ofNullable(coordinator);
        }

        public String startedAtUtc() {
            return UTC_FORMATTER.format(Instant.ofEpochMilli(startedAtMs));
        }

        public String completedAtUtc() {
            return UTC_FORMATTER.format(Instant.ofEpochMilli(completedAtMs));
        }

        public String elapsedText() {
            return formatElapsed(elapsedMs);
        }
    }

    public record Summary(int schemaVersion, List<CycleTiming> records) {
        public Summary {
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                    "Unsupported timing summary schema: " + schemaVersion
                );
            }
            Objects.requireNonNull(records, "records");
            records = List.copyOf(records);

            Map<String, CycleTiming> unique = new LinkedHashMap<>();
            for (CycleTiming record : records) {
                Objects.requireNonNull(record, "record");
                if (unique.putIfAbsent(record.cycleKey(), record) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate timing cycle key: " + record.cycleKey()
                    );
                }
            }
        }

        public Optional<CycleTiming> find(String jobId, long generation) {
            String key = cycleKey(jobId, generation);
            return records.stream()
                .filter(record -> record.cycleKey().equals(key))
                .findFirst();
        }
    }

    public record WriteResult(
        WriteStatus status,
        CycleTiming record,
        Summary summary
    ) {
        public WriteResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(summary, "summary");
        }

        public boolean changed() {
            return status != WriteStatus.UNCHANGED;
        }
    }

    /**
     * Records a completed cycle exactly once.
     *
     * <p>An identical retry is an {@link WriteStatus#UNCHANGED} no-op. A
     * different payload using an existing job/generation key is rejected
     * instead of silently changing the first recorded completion time.</p>
     */
    public static WriteResult recordCycle(
        Path summaryDirectory,
        CycleCompletion completion
    ) throws IOException {
        return mutate(summaryDirectory, completion, false);
    }

    /**
     * Inserts or intentionally replaces the row for this job/generation key.
     * Use {@link #recordCycle(Path, CycleCompletion)} for ordinary completion
     * retries.
     */
    public static WriteResult upsertCycle(
        Path summaryDirectory,
        CycleCompletion completion
    ) throws IOException {
        return mutate(summaryDirectory, completion, true);
    }

    /**
     * Reads and validates the complete summary. A missing summary is returned
     * as an empty schema-current value.
     */
    public static Summary read(Path summaryDirectory) throws IOException {
        return withFileLock(
            summaryDirectory,
            (summaryPath) -> readUnlocked(summaryPath)
        );
    }

    public static Path summaryPath(Path summaryDirectory) {
        Objects.requireNonNull(summaryDirectory, "summaryDirectory");
        return summaryDirectory
            .toAbsolutePath()
            .normalize()
            .resolve(FILE_NAME);
    }

    public static String cycleKey(String jobId, long generation) {
        String checkedJobId = requireNonBlank(jobId, "jobId");
        if (generation < 0L) {
            throw new IllegalArgumentException(
                "generation must not be negative."
            );
        }
        return checkedJobId + ":" + generation;
    }

    private static WriteResult mutate(
        Path summaryDirectory,
        CycleCompletion completion,
        boolean replaceExisting
    ) throws IOException {
        Objects.requireNonNull(completion, "completion");
        CycleTiming proposed = toTiming(completion);
        return withFileLock(summaryDirectory, (summaryPath) -> {
            Summary before = readUnlocked(summaryPath);
            List<CycleTiming> records = new ArrayList<>(before.records());
            int existingIndex = indexOf(records, proposed.cycleKey());

            WriteStatus status;
            CycleTiming resultRecord;
            if (existingIndex < 0) {
                records.add(proposed);
                status = WriteStatus.INSERTED;
                resultRecord = proposed;
            } else {
                CycleTiming existing = records.get(existingIndex);
                if (existing.equals(proposed)) {
                    return new WriteResult(
                        WriteStatus.UNCHANGED,
                        existing,
                        before
                    );
                }
                if (!replaceExisting) {
                    throw new IOException(
                        "Timing cycle " + proposed.cycleKey()
                            + " is already recorded with different data."
                    );
                }
                records.set(existingIndex, proposed);
                status = WriteStatus.UPDATED;
                resultRecord = proposed;
            }

            Summary after = new Summary(SCHEMA_VERSION, records);
            writeUnlocked(summaryPath, after);
            return new WriteResult(status, resultRecord, after);
        });
    }

    private static CycleTiming toTiming(CycleCompletion completion) {
        return new CycleTiming(
            cycleKey(completion.jobId(), completion.generation()),
            completion.jobId(),
            completion.generation(),
            completion.sourceNbt(),
            completion.printingNbt(),
            completion.startedAtMs(),
            completion.completedAtMs(),
            completion.elapsedMs(),
            completion.recovered(),
            completion.coordinator(),
            completion.botCount()
        );
    }

    private static int indexOf(List<CycleTiming> records, String cycleKey) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).cycleKey().equals(cycleKey)) return index;
        }
        return -1;
    }

    private static Summary readUnlocked(Path summaryPath) throws IOException {
        if (!Files.exists(summaryPath)) {
            return new Summary(SCHEMA_VERSION, List.of());
        }
        if (!Files.isRegularFile(summaryPath)) {
            throw new IOException(
                "NBT timing summary is not a regular file: " + summaryPath
            );
        }

        try (Reader reader = Files.newBufferedReader(
            summaryPath,
            StandardCharsets.UTF_8
        )) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw malformed(summaryPath, "The root must be a JSON object.");
            }
            JsonObject root = parsed.getAsJsonObject();
            int schemaVersion = requiredInt(
                root,
                "schemaVersion",
                summaryPath
            );
            if (schemaVersion != SCHEMA_VERSION) {
                throw malformed(
                    summaryPath,
                    "Unsupported schemaVersion " + schemaVersion + "."
                );
            }
            JsonElement recordsElement = root.get("records");
            if (recordsElement == null || !recordsElement.isJsonArray()) {
                throw malformed(
                    summaryPath,
                    "records must be a JSON array."
                );
            }

            List<CycleTiming> records = new ArrayList<>();
            for (JsonElement recordElement : recordsElement.getAsJsonArray()) {
                if (!recordElement.isJsonObject()) {
                    throw malformed(
                        summaryPath,
                        "Every records entry must be a JSON object."
                    );
                }
                records.add(parseTiming(
                    recordElement.getAsJsonObject(),
                    summaryPath
                ));
            }
            try {
                return new Summary(schemaVersion, records);
            } catch (IllegalArgumentException invalidSummary) {
                throw malformed(
                    summaryPath,
                    invalidSummary.getMessage(),
                    invalidSummary
                );
            }
        } catch (JsonParseException | IllegalStateException parseFailure) {
            throw malformed(
                summaryPath,
                "Invalid JSON.",
                parseFailure
            );
        }
    }

    private static CycleTiming parseTiming(
        JsonObject object,
        Path summaryPath
    ) throws IOException {
        String storedKey = requiredString(object, "cycleKey", summaryPath);
        String jobId = requiredString(object, "jobId", summaryPath);
        long generation = requiredLong(object, "generation", summaryPath);
        String expectedKey;
        try {
            expectedKey = cycleKey(jobId, generation);
        } catch (IllegalArgumentException invalidKey) {
            throw malformed(
                summaryPath,
                invalidKey.getMessage(),
                invalidKey
            );
        }
        if (!expectedKey.equals(storedKey)) {
            throw malformed(
                summaryPath,
                "cycleKey does not match jobId and generation."
            );
        }

        try {
            return new CycleTiming(
                storedKey,
                jobId,
                generation,
                requiredString(object, "sourceNbt", summaryPath),
                optionalString(object, "printingNbt", summaryPath),
                requiredLong(object, "startedAtMs", summaryPath),
                requiredLong(object, "completedAtMs", summaryPath),
                requiredLong(object, "elapsedMs", summaryPath),
                requiredBoolean(object, "recovered", summaryPath),
                optionalString(object, "coordinator", summaryPath),
                requiredInt(object, "botCount", summaryPath)
            );
        } catch (IllegalArgumentException invalidRecord) {
            throw malformed(
                summaryPath,
                invalidRecord.getMessage(),
                invalidRecord
            );
        }
    }

    private static void writeUnlocked(Path summaryPath, Summary summary)
        throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", summary.schemaVersion());
        JsonArray records = new JsonArray();
        for (CycleTiming timing : summary.records()) {
            records.add(toJson(timing));
        }
        root.add("records", records);
        byte[] bytes = (
            PRETTY_GSON.toJson(root) + System.lineSeparator()
        ).getBytes(StandardCharsets.UTF_8);

        Path parent = summaryPath.getParent();
        Path temporary = Files.createTempFile(
            parent,
            "." + FILE_NAME + ".",
            ".tmp"
        );
        IOException operationFailure = null;
        try {
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            moveWithAtomicFallback(temporary, summaryPath);
        } catch (IOException failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                if (operationFailure != null) {
                    operationFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static JsonObject toJson(CycleTiming timing) {
        JsonObject object = new JsonObject();
        object.addProperty("cycleKey", timing.cycleKey());
        object.addProperty("jobId", timing.jobId());
        object.addProperty("generation", timing.generation());
        object.addProperty("sourceNbt", timing.sourceNbt());
        if (timing.printingNbt() != null) {
            object.addProperty("printingNbt", timing.printingNbt());
        }
        object.addProperty("startedAtMs", timing.startedAtMs());
        object.addProperty("startedAtUtc", timing.startedAtUtc());
        object.addProperty("completedAtMs", timing.completedAtMs());
        object.addProperty("completedAtUtc", timing.completedAtUtc());
        object.addProperty("elapsedMs", timing.elapsedMs());
        object.addProperty("elapsed", timing.elapsedText());
        object.addProperty("recovered", timing.recovered());
        if (timing.coordinator() != null) {
            object.addProperty("coordinator", timing.coordinator());
        }
        object.addProperty("botCount", timing.botCount());
        return object;
    }

    private static void moveWithAtomicFallback(Path source, Path destination)
        throws IOException {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException atomicFailure) {
            try {
                Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }

    private static <T> T withFileLock(
        Path summaryDirectory,
        LockedOperation<T> operation
    ) throws IOException {
        Objects.requireNonNull(summaryDirectory, "summaryDirectory");
        Objects.requireNonNull(operation, "operation");

        Path requestedDirectory = summaryDirectory
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(requestedDirectory);
        Path directory = requestedDirectory.toRealPath();
        Path summaryPath = directory.resolve(FILE_NAME);
        Path lockPath = directory.resolve(LOCK_FILE_NAME);
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(
            summaryPath,
            ignored -> new ReentrantLock()
        );

        jvmLock.lock();
        try (FileChannel lockChannel = FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        ); FileLock ignored = lockChannel.lock()) {
            return operation.run(summaryPath);
        } finally {
            jvmLock.unlock();
        }
    }

    @FunctionalInterface
    private interface LockedOperation<T> {
        T run(Path summaryPath) throws IOException;
    }

    private static String requiredString(
        JsonObject object,
        String field,
        Path path
    ) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()
            || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()) {
            throw malformed(path, field + " must be a JSON string.");
        }
        String string = value.getAsString();
        if (string.isBlank()) {
            throw malformed(path, field + " must not be blank.");
        }
        return string;
    }

    private static String optionalString(
        JsonObject object,
        String field,
        Path path
    ) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isString()) {
            throw malformed(path, field + " must be a JSON string or null.");
        }
        String string = value.getAsString();
        if (string.isBlank()) {
            throw malformed(path, field + " must not be blank.");
        }
        return string;
    }

    private static boolean requiredBoolean(
        JsonObject object,
        String field,
        Path path
    ) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw malformed(path, field + " must be a JSON boolean.");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw malformed(path, field + " must be a JSON boolean.");
        }
        return primitive.getAsBoolean();
    }

    private static int requiredInt(
        JsonObject object,
        String field,
        Path path
    ) throws IOException {
        long value = requiredLong(object, field, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw malformed(path, field + " is outside the integer range.");
        }
        return (int) value;
    }

    private static long requiredLong(
        JsonObject object,
        String field,
        Path path
    ) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isNumber()) {
            throw malformed(path, field + " must be a JSON integer.");
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalidNumber) {
            throw malformed(
                path,
                field + " must be a JSON integer in the long range.",
                invalidNumber
            );
        }
    }

    private static IOException malformed(Path path, String detail) {
        return new IOException(
            "Malformed NBT timing summary " + path + ": " + detail
        );
    }

    private static IOException malformed(
        Path path,
        String detail,
        Throwable cause
    ) {
        return new IOException(
            "Malformed NBT timing summary " + path + ": " + detail,
            cause
        );
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }

    private static String optionalNonBlank(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }

    private static String formatElapsed(long elapsedMs) {
        Duration duration = Duration.ofMillis(elapsedMs);
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        int milliseconds = duration.toMillisPart();

        StringBuilder text = new StringBuilder();
        if (days > 0L) text.append(days).append("d ");
        if (days > 0L || hours > 0) {
            text.append(hours).append("h ");
        }
        if (days > 0L || hours > 0 || minutes > 0) {
            text.append(minutes).append("m ");
        }
        text.append(seconds);
        if (milliseconds > 0) {
            text.append('.').append(
                String.format(Locale.ROOT, "%03d", milliseconds)
            );
        }
        return text.append('s').toString();
    }
}
