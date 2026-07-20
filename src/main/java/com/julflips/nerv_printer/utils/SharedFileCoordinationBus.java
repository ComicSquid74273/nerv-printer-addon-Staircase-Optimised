package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A small, durable coordination bus for printer instances which share a
 * directory.
 *
 * <p>Every process writes exactly one file: a master writes
 * {@value #MASTER_STATE_FILE}, and a slave writes only its own
 * {@code slave_<id>_state.json}. Peers only read those files. This avoids the
 * lost-update races caused by several processes editing one shared JSON
 * document.</p>
 *
 * <p>Message IDs are monotonic per sender/recipient channel. An envelope stays
 * in the sender's persisted outbox until the recipient explicitly
 * acknowledges it and publishes that acknowledgement with {@link #flush(long)}.
 * Consequently, an envelope can be delivered more than once after a crash,
 * but it is not lost. Consumers should process payloads idempotently and
 * acknowledge envelopes in the FIFO order returned by {@link #poll()}.</p>
 *
 * <p>This class is internally synchronized. The single-writer rule still
 * requires callers to open no more than one live bus for the same local node
 * and coordination directory.</p>
 */
public final class SharedFileCoordinationBus {
    public static final int SCHEMA_VERSION = 1;
    public static final String MASTER_STATE_FILE = "master_state.json";

    private static final long MAX_STATE_BYTES = 64L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public enum Role {
        MASTER,
        SLAVE
    }

    /**
     * An immutable application message. IDs are scoped to the
     * sender/recipient pair.
     */
    public record Envelope(
        long id,
        String sender,
        String recipient,
        String payload
    ) {
        public Envelope {
            if (id <= 0) {
                throw new IllegalArgumentException(
                    "Envelope IDs must be positive."
                );
            }
            requireIdentifier(sender, "sender");
            requireIdentifier(recipient, "recipient");
            Objects.requireNonNull(payload, "payload");
        }
    }

    private record DeliveryKey(String sender, long id) {
    }

    private record RemoteSnapshot(
        long writtenAtMs,
        Map<String, String> metadata,
        List<Envelope> envelopes
    ) {
        private RemoteSnapshot {
            metadata = Map.copyOf(metadata);
            envelopes = List.copyOf(envelopes);
        }
    }

    /*
     * Gson wire types deliberately remain simple mutable data objects. All
     * fields are validated before a document is trusted.
     */
    private static final class StateDocument {
        int schemaVersion;
        String role;
        String nodeId;
        String masterId;
        List<String> configuredPeers;
        long writtenAtMs;
        Map<String, String> metadata;
        Map<String, Long> nextIds;
        Map<String, Long> acknowledgements;
        Map<String, Long> observedAcknowledgements;
        List<WireEnvelope> outbox;
    }

    private static final class WireEnvelope {
        long id;
        String sender;
        String recipient;
        String payload;

        private WireEnvelope() {
        }

        private WireEnvelope(Envelope envelope) {
            id = envelope.id();
            sender = envelope.sender();
            recipient = envelope.recipient();
            payload = envelope.payload();
        }

        private Envelope toEnvelope() {
            return new Envelope(id, sender, recipient, payload);
        }
    }

    private final Path directory;
    private final Path localStateFile;
    private final Role role;
    private final String localId;
    private final String masterId;
    private final Set<String> configuredPeers;

    private final Map<String, String> localMetadata = new TreeMap<>();
    private final Map<String, Long> nextIds = new TreeMap<>();
    private final Map<String, Long> localAcknowledgements = new TreeMap<>();
    private final Map<String, Long> observedAcknowledgements = new TreeMap<>();
    private final Map<String, List<Envelope>> outbox = new TreeMap<>();
    private final Map<String, RemoteSnapshot> remoteSnapshots = new HashMap<>();
    private final Set<DeliveryKey> deliveredThisRun = new HashSet<>();

    private long lastWrittenAtMs;

    private SharedFileCoordinationBus(
        Path directory,
        Role role,
        String localId,
        String masterId,
        Collection<String> configuredPeers
    ) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory")
            .toAbsolutePath()
            .normalize();
        this.role = Objects.requireNonNull(role, "role");
        this.localId = requireIdentifier(localId, "localId");
        this.masterId = requireIdentifier(masterId, "masterId");

        TreeSet<String> normalizedPeers = new TreeSet<>();
        Objects.requireNonNull(configuredPeers, "configuredPeers");
        for (String peer : configuredPeers) {
            String identifier = requireIdentifier(peer, "configured peer");
            if (identifier.equals(this.localId)) {
                throw new IllegalArgumentException(
                    "A node cannot be a member of its own peer set."
                );
            }
            normalizedPeers.add(identifier);
        }
        if (role == Role.MASTER && !this.localId.equals(this.masterId)) {
            throw new IllegalArgumentException(
                "A master's local ID must equal its master ID."
            );
        }
        if (role == Role.SLAVE
            && (!normalizedPeers.equals(Set.of(this.masterId))
                || this.localId.equals(this.masterId))) {
            throw new IllegalArgumentException(
                "A slave must have exactly its distinct master as a peer."
            );
        }
        this.configuredPeers = Collections.unmodifiableSet(normalizedPeers);
        this.localStateFile = role == Role.MASTER
            ? this.directory.resolve(MASTER_STATE_FILE)
            : slaveStateFile(this.directory, this.localId);

        Files.createDirectories(this.directory);
        initializeEmptyChannels();
        restoreOwnState();
    }

    public static SharedFileCoordinationBus openMaster(
        Path directory,
        String masterId,
        Collection<String> slaveIds
    ) throws IOException {
        return new SharedFileCoordinationBus(
            directory,
            Role.MASTER,
            masterId,
            masterId,
            slaveIds
        );
    }

    public static SharedFileCoordinationBus openMaster(
        Path directory,
        String masterId,
        String... slaveIds
    ) throws IOException {
        Objects.requireNonNull(slaveIds, "slaveIds");
        return openMaster(directory, masterId, List.of(slaveIds));
    }

    public static SharedFileCoordinationBus openSlave(
        Path directory,
        String masterId,
        String slaveId
    ) throws IOException {
        return new SharedFileCoordinationBus(
            directory,
            Role.SLAVE,
            slaveId,
            masterId,
            Set.of(masterId)
        );
    }

    public Role role() {
        return role;
    }

    public String localId() {
        return localId;
    }

    public String masterId() {
        return masterId;
    }

    public Set<String> configuredPeers() {
        return configuredPeers;
    }

    public Path directory() {
        return directory;
    }

    public Path localStateFile() {
        return localStateFile;
    }

    /**
     * Adds a durable message to the in-memory outbox. Call
     * {@link #flush(long)} to publish it.
     */
    public synchronized Envelope enqueue(String recipient, String payload) {
        requireConfiguredPeer(recipient);
        Objects.requireNonNull(payload, "payload");

        long id = nextIds.get(recipient);
        if (id == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "The message ID space is exhausted for " + recipient + "."
            );
        }
        Envelope envelope = new Envelope(id, localId, recipient, payload);
        outbox.get(recipient).add(envelope);
        nextIds.put(recipient, id + 1L);
        return envelope;
    }

    /**
     * Returns each currently available envelope once in this process. An
     * unacknowledged envelope is returned again after this bus is reopened.
     * Ordering is FIFO for each sender.
     */
    public synchronized List<Envelope> poll() {
        List<Envelope> result = new ArrayList<>();
        for (String peer : configuredPeers) {
            RemoteSnapshot snapshot = remoteSnapshots.get(peer);
            if (snapshot == null) continue;
            long acknowledged = localAcknowledgements.get(peer);
            for (Envelope envelope : snapshot.envelopes()) {
                if (envelope.id() <= acknowledged) continue;
                DeliveryKey key = new DeliveryKey(peer, envelope.id());
                if (deliveredThisRun.add(key)) result.add(envelope);
            }
        }
        result.sort(
            Comparator.comparing(Envelope::sender)
                .thenComparingLong(Envelope::id)
        );
        return List.copyOf(result);
    }

    public synchronized Optional<Envelope> pollFirst() {
        for (String peer : configuredPeers) {
            RemoteSnapshot snapshot = remoteSnapshots.get(peer);
            if (snapshot == null) continue;
            long acknowledged = localAcknowledgements.get(peer);
            for (Envelope envelope : snapshot.envelopes()) {
                if (envelope.id() <= acknowledged) continue;
                DeliveryKey key = new DeliveryKey(peer, envelope.id());
                if (deliveredThisRun.add(key)) {
                    return Optional.of(envelope);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Acknowledges one delivered message. Acknowledgements are cumulative and
     * must therefore be made in sender FIFO order.
     */
    public synchronized void acknowledge(Envelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        requireConfiguredPeer(envelope.sender());
        if (!localId.equals(envelope.recipient())) {
            throw new IllegalArgumentException(
                "The envelope is not addressed to this node."
            );
        }
        DeliveryKey key = new DeliveryKey(envelope.sender(), envelope.id());
        if (!deliveredThisRun.contains(key)) {
            throw new IllegalArgumentException(
                "Only an envelope returned by poll() can be acknowledged."
            );
        }

        long current = localAcknowledgements.get(envelope.sender());
        if (envelope.id() != current + 1L) {
            throw new IllegalStateException(
                "Envelopes from "
                    + envelope.sender()
                    + " must be acknowledged in FIFO order; expected "
                    + (current + 1L)
                    + " but received "
                    + envelope.id()
                    + "."
            );
        }
        localAcknowledgements.put(envelope.sender(), envelope.id());
    }

    public synchronized void setLocalMetadata(String key, String value) {
        requireMetadataPart(key, "metadata key");
        requireMetadataPart(value, "metadata value");
        localMetadata.put(key, value);
    }

    public synchronized void removeLocalMetadata(String key) {
        requireMetadataPart(key, "metadata key");
        localMetadata.remove(key);
    }

    public synchronized Map<String, String> localMetadata() {
        return Map.copyOf(localMetadata);
    }

    public synchronized Map<String, String> remoteMetadata(String peer) {
        requireConfiguredPeer(peer);
        RemoteSnapshot snapshot = remoteSnapshots.get(peer);
        return snapshot == null ? Map.of() : snapshot.metadata();
    }

    public synchronized OptionalLong remoteTimestampMs(String peer) {
        requireConfiguredPeer(peer);
        RemoteSnapshot snapshot = remoteSnapshots.get(peer);
        return snapshot == null
            ? OptionalLong.empty()
            : OptionalLong.of(snapshot.writtenAtMs());
    }

    public synchronized boolean isPeerFresh(
        String peer,
        long nowMs,
        long maximumAgeMs
    ) {
        if (nowMs < 0) {
            throw new IllegalArgumentException("nowMs cannot be negative.");
        }
        if (maximumAgeMs < 0) {
            throw new IllegalArgumentException(
                "maximumAgeMs cannot be negative."
            );
        }
        OptionalLong timestamp = remoteTimestampMs(peer);
        if (timestamp.isEmpty()) return false;
        long age = nowMs - timestamp.getAsLong();
        return age >= 0 && age <= maximumAgeMs;
    }

    /**
     * Reads peer snapshots, applies their acknowledgements, then atomically
     * publishes this node's complete state.
     */
    public synchronized void flush(long nowMs) throws IOException {
        if (nowMs < 0) {
            throw new IllegalArgumentException("nowMs cannot be negative.");
        }
        for (String peer : configuredPeers) refreshPeer(peer);
        lastWrittenAtMs = nowMs;
        writeAtomically(localStateFile, GSON.toJson(toStateDocument()));
    }

    /**
     * Returns the state path used by a slave without allowing its ID to escape
     * the coordination directory.
     */
    public static Path slaveStateFile(Path directory, String slaveId) {
        Path normalizedDirectory = Objects.requireNonNull(
            directory,
            "directory"
        ).toAbsolutePath().normalize();
        return normalizedDirectory.resolve(
            "slave_"
                + sanitizeFileComponent(slaveId)
                + "_state.json"
        );
    }

    /**
     * Produces a deterministic, case-insensitive-filesystem-safe component.
     * Lowercase ASCII letters, digits and hyphens are retained; every other
     * UTF-8 byte is escaped. Very long values are bounded with a SHA-256
     * suffix.
     */
    public static String sanitizeFileComponent(String value) {
        String identifier = requireIdentifier(value, "file identifier");
        byte[] bytes = identifier.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte raw : bytes) {
            int unsigned = Byte.toUnsignedInt(raw);
            char character = (char) unsigned;
            if ((character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-') {
                result.append(character);
            } else {
                result.append('_');
                appendHexByte(result, unsigned);
                result.append('_');
            }
        }
        if (result.length() <= 96) return result.toString();
        return result.substring(0, 48) + "-" + sha256Hex(bytes);
    }

    private void initializeEmptyChannels() {
        for (String peer : configuredPeers) {
            nextIds.put(peer, 1L);
            localAcknowledgements.put(peer, 0L);
            observedAcknowledgements.put(peer, 0L);
            outbox.put(peer, new ArrayList<>());
        }
    }

    private void restoreOwnState() throws IOException {
        if (!Files.exists(localStateFile)) return;
        Optional<StateDocument> candidate = readState(localStateFile);
        if (candidate.isEmpty()) {
            throw new IOException(
                "The existing local coordination state is unreadable or "
                    + "incompatible: " + localStateFile
                    + ". Preserve it for diagnosis, then remove it only when "
                    + "you intentionally want to start a new coordination state."
            );
        }
        StateDocument document = candidate.get();
        if (!matchesOwnIdentity(document)) {
            throw new IOException(
                "The existing local coordination state belongs to a different "
                    + "role, player, master, or configured peer set: "
                    + localStateFile
            );
        }

        localMetadata.clear();
        localMetadata.putAll(document.metadata);
        lastWrittenAtMs = document.writtenAtMs;

        Map<String, List<Envelope>> restoredOutbox = groupOutbox(document);
        for (String peer : configuredPeers) {
            long next = document.nextIds.getOrDefault(peer, 1L);
            long localAck = document.acknowledgements.getOrDefault(peer, 0L);
            long observedAck = document.observedAcknowledgements.getOrDefault(
                peer,
                0L
            );
            List<Envelope> pending = restoredOutbox.getOrDefault(
                peer,
                List.of()
            );

            nextIds.put(peer, next);
            localAcknowledgements.put(peer, localAck);
            observedAcknowledgements.put(peer, observedAck);
            outbox.get(peer).clear();
            for (Envelope envelope : pending) {
                if (envelope.id() > observedAck) {
                    outbox.get(peer).add(envelope);
                }
            }
        }
    }

    private boolean matchesOwnIdentity(StateDocument document) {
        if (document.schemaVersion != SCHEMA_VERSION) return false;
        if (!role.name().equals(document.role)) return false;
        if (!localId.equals(document.nodeId)) return false;
        if (!masterId.equals(document.masterId)) return false;
        return Set.copyOf(document.configuredPeers).equals(
            configuredPeers
        );
    }

    private void refreshPeer(String peer) {
        Path peerFile = role == Role.MASTER
            ? slaveStateFile(directory, peer)
            : directory.resolve(MASTER_STATE_FILE);
        final Optional<StateDocument> candidate;
        try {
            candidate = readState(peerFile);
        } catch (IOException ignored) {
            return;
        }
        if (candidate.isEmpty()) return;
        StateDocument document = candidate.get();
        if (!matchesRemoteIdentity(peer, document)) return;

        long remoteAck = document.acknowledgements.getOrDefault(localId, 0L);
        long maximumSentId = nextIds.get(peer) - 1L;
        long boundedAck = Math.min(remoteAck, maximumSentId);
        long previousAck = observedAcknowledgements.get(peer);
        if (boundedAck > previousAck) {
            observedAcknowledgements.put(peer, boundedAck);
            outbox.get(peer).removeIf(
                envelope -> envelope.id() <= boundedAck
            );
        }

        List<Envelope> incoming = groupOutbox(document).getOrDefault(
            localId,
            List.of()
        );
        remoteSnapshots.put(
            peer,
            new RemoteSnapshot(
                document.writtenAtMs,
                document.metadata,
                incoming
            )
        );
    }

    private boolean matchesRemoteIdentity(
        String expectedPeer,
        StateDocument document
    ) {
        if (document.schemaVersion != SCHEMA_VERSION) return false;
        if (!masterId.equals(document.masterId)) return false;
        if (!expectedPeer.equals(document.nodeId)) return false;
        if (!document.configuredPeers.contains(localId)) return false;
        if (role == Role.MASTER) {
            return Role.SLAVE.name().equals(document.role)
                && Set.copyOf(document.configuredPeers).equals(Set.of(localId));
        }
        return Role.MASTER.name().equals(document.role);
    }

    private StateDocument toStateDocument() {
        StateDocument document = new StateDocument();
        document.schemaVersion = SCHEMA_VERSION;
        document.role = role.name();
        document.nodeId = localId;
        document.masterId = masterId;
        document.configuredPeers = List.copyOf(configuredPeers);
        document.writtenAtMs = lastWrittenAtMs;
        document.metadata = new LinkedHashMap<>(localMetadata);
        document.nextIds = new LinkedHashMap<>(nextIds);
        document.acknowledgements = new LinkedHashMap<>(
            localAcknowledgements
        );
        document.observedAcknowledgements = new LinkedHashMap<>(
            observedAcknowledgements
        );
        document.outbox = new ArrayList<>();
        for (String peer : configuredPeers) {
            for (Envelope envelope : outbox.get(peer)) {
                document.outbox.add(new WireEnvelope(envelope));
            }
        }
        return document;
    }

    private static Optional<StateDocument> readState(Path file)
        throws IOException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        long size = Files.size(file);
        if (size <= 0 || size > MAX_STATE_BYTES) return Optional.empty();

        final StateDocument document;
        try {
            document = GSON.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                StateDocument.class
            );
            validateDocument(document);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        return Optional.of(document);
    }

    private static void validateDocument(StateDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Missing state document.");
        }
        if (document.schemaVersion <= 0) {
            throw new IllegalArgumentException("Invalid schema version.");
        }
        Role.valueOf(Objects.requireNonNull(document.role, "role"));
        requireIdentifier(document.nodeId, "nodeId");
        requireIdentifier(document.masterId, "masterId");
        Objects.requireNonNull(document.configuredPeers, "configuredPeers");
        Objects.requireNonNull(document.metadata, "metadata");
        Objects.requireNonNull(document.nextIds, "nextIds");
        Objects.requireNonNull(
            document.acknowledgements,
            "acknowledgements"
        );
        Objects.requireNonNull(
            document.observedAcknowledgements,
            "observedAcknowledgements"
        );
        Objects.requireNonNull(document.outbox, "outbox");
        if (document.writtenAtMs < 0) {
            throw new IllegalArgumentException("Invalid written timestamp.");
        }

        Set<String> peers = new TreeSet<>();
        for (String peer : document.configuredPeers) {
            String valid = requireIdentifier(peer, "configured peer");
            if (valid.equals(document.nodeId) || !peers.add(valid)) {
                throw new IllegalArgumentException(
                    "Invalid configured peer membership."
                );
            }
        }
        validateMetadata(document.metadata);
        validateCounterMap(document.nextIds, peers, 1L);
        validateCounterMap(document.acknowledgements, peers, 0L);
        validateCounterMap(
            document.observedAcknowledgements,
            peers,
            0L
        );

        Map<String, Long> lastIds = new HashMap<>();
        for (WireEnvelope wire : document.outbox) {
            if (wire == null) {
                throw new IllegalArgumentException("Null outbox envelope.");
            }
            Envelope envelope = wire.toEnvelope();
            if (!document.nodeId.equals(envelope.sender())
                || !peers.contains(envelope.recipient())) {
                throw new IllegalArgumentException(
                    "An outbox envelope has invalid routing."
                );
            }
            long previous = lastIds.getOrDefault(envelope.recipient(), 0L);
            if (envelope.id() <= previous) {
                throw new IllegalArgumentException(
                    "Outbox IDs are not strictly increasing."
                );
            }
            long next = document.nextIds.getOrDefault(
                envelope.recipient(),
                1L
            );
            if (envelope.id() >= next) {
                throw new IllegalArgumentException(
                    "An outbox ID is not below its next ID."
                );
            }
            lastIds.put(envelope.recipient(), envelope.id());
        }
        for (String peer : peers) {
            long observed = document.observedAcknowledgements.getOrDefault(
                peer,
                0L
            );
            long maximumAssigned = document.nextIds.getOrDefault(peer, 1L)
                - 1L;
            if (observed > maximumAssigned) {
                throw new IllegalArgumentException(
                    "An observed acknowledgement exceeds sent messages."
                );
            }
        }
    }

    private static void validateMetadata(Map<String, String> metadata) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            requireMetadataPart(entry.getKey(), "metadata key");
            requireMetadataPart(entry.getValue(), "metadata value");
        }
    }

    private static void validateCounterMap(
        Map<String, Long> counters,
        Set<String> peers,
        long minimum
    ) {
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            if (!peers.contains(entry.getKey())
                || entry.getValue() == null
                || entry.getValue() < minimum) {
                throw new IllegalArgumentException("Invalid peer counter.");
            }
        }
    }

    private static Map<String, List<Envelope>> groupOutbox(
        StateDocument document
    ) {
        Map<String, List<Envelope>> result = new HashMap<>();
        for (WireEnvelope wire : document.outbox) {
            Envelope envelope = wire.toEnvelope();
            result.computeIfAbsent(
                envelope.recipient(),
                ignored -> new ArrayList<>()
            ).add(envelope);
        }
        for (List<Envelope> envelopes : result.values()) {
            envelopes.sort(Comparator.comparingLong(Envelope::id));
        }
        return result;
    }

    private void requireConfiguredPeer(String peer) {
        requireIdentifier(peer, "peer");
        if (!configuredPeers.contains(peer)) {
            throw new IllegalArgumentException(
                peer + " is not a configured peer of " + localId + "."
            );
        }
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value;
    }

    private static String requireMetadataPart(String value, String label) {
        Objects.requireNonNull(value, label);
        return value;
    }

    private static void writeAtomically(Path destination, String content)
        throws IOException {
        Files.createDirectories(destination.getParent());
        String temporaryName = "."
            + destination.getFileName()
            + "."
            + UUID.randomUUID()
            + ".tmp";
        Path temporary = destination.getParent().resolve(temporaryName);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        try {
            try (
                FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                )
            ) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void appendHexByte(StringBuilder output, int value) {
        final char[] digits = "0123456789abcdef".toCharArray();
        output.append(digits[(value >>> 4) & 0x0f]);
        output.append(digits[value & 0x0f]);
    }

    private static String sha256Hex(byte[] bytes) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
        byte[] hashed = digest.digest(bytes);
        StringBuilder output = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            appendHexByte(output, Byte.toUnsignedInt(value));
        }
        return output.toString();
    }
}
