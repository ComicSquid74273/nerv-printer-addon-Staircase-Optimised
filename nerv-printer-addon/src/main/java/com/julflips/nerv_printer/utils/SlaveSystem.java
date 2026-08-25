package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.interfaces.MapPrinter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SlaveSystem {

    public enum CoordinationMode {
        Chat,
        FileMaster,
        FileSlave,
        FileCluster
    }

    public static int commandDelay = 0;
    public static String directMessageCommand = "w";
    public static String senderPrefix = "";
    public static String senderSuffix = "";
    public static int randomLength = 0;
    public static ArrayList<String> slaves = new ArrayList<>();
    public static HashMap<String, Boolean> activeSlavesDict = new HashMap<>();
    public static HashMap<String, Boolean> finishedSlavesDict = new HashMap<>();
    public static HashSet<String> pausedSlaves = new HashSet<>();
    public static SlaveTableController tableController = null;

    private static MapPrinter printerModule = null;
    private static int timeout = 0;
    private static ArrayList<String> toBeSentMessages = new ArrayList<>();
    private static ArrayList<String> toBeConfirmedSlaves = new ArrayList<>();
    private static HashSet<String> removedSlaves = new HashSet<>();
    private static HashMap<String, Long> pendingRemovalSince = new HashMap<>();
    private static HashMap<String, Tuple<Integer, Integer>> assignedIntervals =
        new HashMap<>();
    private static HashSet<String> acknowledgedIntervals = new HashSet<>();
    private static String master = null;
    private static final long FORCE_REMOVAL_DELAY_MS = 10_000L;
    private static final long FILE_ERROR_REPORT_INTERVAL_MS = 10_000L;
    private static final long FILE_MASTER_MAX_AGE_MS = 30_000L;
    private static final String FILE_ENVELOPE_PREFIX = "nerv-file-v2|";
    private static final String FILE_META_COORDINATOR_ROLE = "coordinatorRole";
    private static final String FILE_META_CLUSTER_PRIMARY = "clusterPrimary";
    private static final String FILE_META_CLUSTER_PARTICIPANTS =
        "clusterParticipants";
    private static final String FILE_META_HANDOFF_REQUEST = "handoffRequest";
    private static final String FILE_META_HANDOFF_READY = "handoffReady";
    private static CoordinationMode coordinationMode = CoordinationMode.Chat;
    private static SharedFileCoordinationBus fileBus = null;
    private static String fileEpoch = null;
    private static String fileLocalPlayerName = null;
    private static String fileClusterPrimary = null;
    private static List<String> fileClusterParticipants = List.of();
    private static long filePeerMaximumAgeMs = FILE_MASTER_MAX_AGE_MS;
    private static long fileMembershipGraceUntilMs = 0L;
    private static long fileCoordinatorMissingSinceMs = -1L;
    private static ArrayList<PendingFileMessage> pendingFileMessages =
        new ArrayList<>();
    private static ArrayList<SharedFileCoordinationBus.Envelope>
        deferredIncomingFileMessages = new ArrayList<>();
    private static int filePollTicks = 20;
    private static int filePollCountdown = 0;
    private static long lastFileErrorReportMs = Long.MIN_VALUE;

    public static void setupSlaveSystem(MapPrinter module, int delay, String dmCommand, String prefix, String suffix, int randomSuffixLength) {
        printerModule = module;
        commandDelay = delay;
        directMessageCommand = dmCommand;
        senderPrefix = prefix;
        senderSuffix = suffix;
        randomLength = randomSuffixLength;
        slaves.clear();
        toBeSentMessages.clear();
        toBeConfirmedSlaves.clear();
        removedSlaves.clear();
        pendingRemovalSince.clear();
        assignedIntervals.clear();
        acknowledgedIntervals.clear();
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        pausedSlaves.clear();
        master = null;
        coordinationMode = CoordinationMode.Chat;
        fileBus = null;
        fileEpoch = null;
        fileLocalPlayerName = null;
        fileClusterPrimary = null;
        fileClusterParticipants = List.of();
        filePeerMaximumAgeMs = FILE_MASTER_MAX_AGE_MS;
        fileMembershipGraceUntilMs = 0L;
        fileCoordinatorMissingSinceMs = -1L;
        pendingFileMessages.clear();
        deferredIncomingFileMessages.clear();
        filePollCountdown = 0;
        lastFileErrorReportMs = Long.MIN_VALUE;
    }

    /**
     * Switches the existing master/slave protocol from chat messages to a
     * durable shared-folder transport. The payload protocol is deliberately
     * unchanged: interval acknowledgements, U-pair mining task IDs, connector
     * reports, pause/removal tombstones, and finalization acknowledgements all
     * continue through {@link #handleMessage(String, String)}.
     */
    public static void configureFileCoordination(
        CoordinationMode mode,
        Path sharedFolder,
        String localPlayerName,
        String masterPlayerName,
        Collection<String> configuredSlaveNames,
        int pollTicks
    ) throws IOException {
        configureFileCoordination(
            mode,
            sharedFolder,
            localPlayerName,
            masterPlayerName,
            configuredSlaveNames,
            pollTicks,
            FILE_MASTER_MAX_AGE_MS
        );
    }

    public static void configureFileCoordination(
        CoordinationMode mode,
        Path sharedFolder,
        String localPlayerName,
        String masterPlayerName,
        Collection<String> configuredSlaveNames,
        int pollTicks,
        long peerMaximumAgeMs
    ) throws IOException {
        if (mode == null || mode == CoordinationMode.Chat) {
            coordinationMode = CoordinationMode.Chat;
            fileBus = null;
            return;
        }
        if (printerModule == null) {
            throw new IllegalStateException(
                "The slave system must be initialized before file coordination."
            );
        }
        if (sharedFolder == null) {
            throw new IllegalArgumentException(
                "The shared coordination folder is required."
            );
        }
        String local = requirePlayerName(localPlayerName, "local player");
        fileLocalPlayerName = local;
        filePeerMaximumAgeMs = Math.max(2_000L, peerMaximumAgeMs);
        filePollTicks = Math.max(1, pollTicks);
        filePollCountdown = 0;

        slaves.clear();
        toBeSentMessages.clear();
        toBeConfirmedSlaves.clear();
        removedSlaves.clear();
        pendingRemovalSince.clear();
        assignedIntervals.clear();
        acknowledgedIntervals.clear();
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        pausedSlaves.clear();
        pendingFileMessages.clear();
        deferredIncomingFileMessages.clear();
        fileEpoch = null;
        fileClusterPrimary = null;
        fileClusterParticipants = List.of();
        fileCoordinatorMissingSinceMs = -1L;
        fileMembershipGraceUntilMs =
            System.currentTimeMillis() + filePeerMaximumAgeMs;

        if (mode == CoordinationMode.FileCluster) {
            String configuredPrimary = requirePlayerName(
                masterPlayerName,
                "cluster primary master"
            );
            List<String> participants = normalizeClusterParticipants(
                configuredSlaveNames,
                configuredPrimary,
                local
            );
            if (participants.size() > 64) {
                throw new IllegalArgumentException(
                    "Compact U ownership supports at most 64 file-cluster bots."
                );
            }
            fileClusterPrimary = configuredPrimary;
            fileClusterParticipants = participants;
            List<String> peers = clusterPeers(local);
            long now = System.currentTimeMillis();
            Optional<SharedFileCoordinationBus.MasterSnapshot> existing =
                SharedFileCoordinationBus.inspectMaster(sharedFolder);
            SharedFileCoordinationBus.MasterSnapshot snapshot =
                existing.orElse(null);
            boolean healthyCoordinator = isHealthyCoordinatorSnapshot(
                snapshot,
                now
            );
            String startupMaster = healthyCoordinator
                ? snapshot.nodeId()
                : configuredPrimary;
            boolean startAsMaster = local.equalsIgnoreCase(configuredPrimary)
                && (!healthyCoordinator
                    || snapshot.nodeId().equalsIgnoreCase(local));

            if (startAsMaster) {
                fileBus = SharedFileCoordinationBus.openMasterForTakeover(
                    sharedFolder,
                    local,
                    peers
                );
                if (snapshot != null
                    && isConfiguredClusterParticipant(snapshot.nodeId())) {
                    copyFileMetadata(snapshot.metadata());
                }
                master = null;
                slaves.addAll(peers);
                for (String slave : peers) {
                    activeSlavesDict.put(slave, false);
                    finishedSlavesDict.put(slave, false);
                }
                fileEpoch = UUID.randomUUID().toString();
            } else {
                fileBus = SharedFileCoordinationBus.openSlaveForHandoff(
                    sharedFolder,
                    startupMaster,
                    local,
                    peers
                );
                master = startupMaster;
                if (snapshot != null
                    && startupMaster.equalsIgnoreCase(snapshot.nodeId())) {
                    fileEpoch = snapshot.metadata().get("epoch");
                }
                printerModule.masterRelationshipChanged();
            }
            coordinationMode = mode;
        } else if (mode == CoordinationMode.FileMaster) {
            List<String> configured = normalizeConfiguredSlaves(
                configuredSlaveNames,
                local
            );
            if (configured.isEmpty()) {
                throw new IllegalArgumentException(
                    "File Master requires at least one configured slave name."
                );
            }
            if (configured.size() > 63) {
                throw new IllegalArgumentException(
                    "Compact U ownership supports at most 63 file slaves "
                        + "(64 bots including the master)."
                );
            }
            fileBus = SharedFileCoordinationBus.openMaster(
                sharedFolder,
                local,
                configured
            );
            master = null;
            slaves.addAll(configured);
            for (String slave : configured) {
                activeSlavesDict.put(slave, false);
                finishedSlavesDict.put(slave, false);
            }
            coordinationMode = mode;
            fileEpoch = UUID.randomUUID().toString();
        } else {
            String configuredMaster = requirePlayerName(
                masterPlayerName,
                "master player"
            );
            if (configuredMaster.equalsIgnoreCase(local)) {
                throw new IllegalArgumentException(
                    "The master and slave player names must be different."
                );
            }
            fileBus = SharedFileCoordinationBus.openSlave(
                sharedFolder,
                configuredMaster,
                local
            );
            master = configuredMaster;
            coordinationMode = mode;
            printerModule.masterRelationshipChanged();
        }

        fileBus.setLocalMetadata("transport", "nerv-printer-file-v1");
        fileBus.setLocalMetadata("player", local);
        publishClusterIdentityMetadata();
        if (fileEpoch != null) {
            fileBus.setLocalMetadata("epoch", fileEpoch);
        }
        long now = System.currentTimeMillis();
        flushFileBus(now);
        adoptMasterFileEpoch();
        if (isFileMaster()) {
            generateIntervals();
        }
        flushPendingFileMessages();
        flushFileBus(now);
        if (tableController != null) tableController.rebuild();
    }

    private static String requirePlayerName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " name cannot be blank.");
        }
        return value.trim();
    }

    private static List<String> normalizeConfiguredSlaves(
        Collection<String> values,
        String localPlayerName
    ) {
        LinkedHashSet<String> exactNames = new LinkedHashSet<>();
        HashSet<String> caseFoldedNames = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                String name = value.trim();
                String folded = name.toLowerCase(java.util.Locale.ROOT);
                if (name.equalsIgnoreCase(localPlayerName)) {
                    throw new IllegalArgumentException(
                        "The master cannot also be listed as a slave."
                    );
                }
                if (!caseFoldedNames.add(folded)) {
                    throw new IllegalArgumentException(
                        "Duplicate slave player name: " + name
                    );
                }
                exactNames.add(name);
            }
        }
        return List.copyOf(exactNames);
    }

    private static List<String> normalizeClusterParticipants(
        Collection<String> configuredSlaveNames,
        String primary,
        String local
    ) {
        LinkedHashSet<String> exactNames = new LinkedHashSet<>();
        HashMap<String, String> caseFoldedNames = new HashMap<>();
        if (configuredSlaveNames != null) {
            for (String value : configuredSlaveNames) {
                if (value == null || value.isBlank()) continue;
                addClusterParticipant(
                    exactNames,
                    caseFoldedNames,
                    value.trim()
                );
            }
        }
        addClusterParticipant(exactNames, caseFoldedNames, primary);
        addClusterParticipant(exactNames, caseFoldedNames, local);
        ArrayList<String> participants = new ArrayList<>(exactNames);
        participants.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(participants);
    }

    private static void addClusterParticipant(
        LinkedHashSet<String> exactNames,
        HashMap<String, String> caseFoldedNames,
        String name
    ) {
        String folded = name.toLowerCase(java.util.Locale.ROOT);
        String existing = caseFoldedNames.get(folded);
        if (existing != null) {
            if (!existing.equals(name)) {
                throw new IllegalArgumentException(
                    "Player names differ only by letter case: "
                        + existing + " and " + name
                );
            }
            return;
        }
        caseFoldedNames.put(folded, name);
        exactNames.add(name);
    }

    public static CoordinationMode coordinationMode() {
        return coordinationMode;
    }

    public static boolean isFileMode() {
        return coordinationMode != CoordinationMode.Chat && fileBus != null;
    }

    public static boolean isFileMaster() {
        return fileBus != null
            && fileBus.role() == SharedFileCoordinationBus.Role.MASTER;
    }

    public static boolean isFileSlave() {
        return fileBus != null
            && fileBus.role() == SharedFileCoordinationBus.Role.SLAVE;
    }

    public static Path fileCoordinationFolder() {
        return fileBus == null ? null : fileBus.directory();
    }

    public static void setFileMetadata(String key, @Nullable String value) {
        if (fileBus == null) return;
        if (value == null) {
            fileBus.removeLocalMetadata(key);
        } else {
            fileBus.setLocalMetadata(key, value);
        }
    }

    public static Map<String, String> localFileMetadata() {
        return fileBus == null ? Map.of() : fileBus.localMetadata();
    }

    public static Map<String, String> remoteFileMetadata(String playerName) {
        if (fileBus == null) return Map.of();
        try {
            return fileBus.remoteMetadata(playerName);
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    public static Map<String, String> masterFileMetadata() {
        return isFileSlave() && master != null
            ? remoteFileMetadata(master)
            : Map.of();
    }

    public static boolean isFilePeerFresh(
        String playerName,
        long maximumAgeMs
    ) {
        if (fileBus == null) return false;
        try {
            return fileBus.isPeerFresh(
                playerName,
                System.currentTimeMillis(),
                maximumAgeMs
            );
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isFileMasterAvailable(long maximumAgeMs) {
        if (!isFileSlave() || master == null) return false;
        return isFilePeerFresh(master, maximumAgeMs)
            && "true".equalsIgnoreCase(
                masterFileMetadata().get("active")
            );
    }

    public static OptionalLong filePeerTimestamp(String playerName) {
        if (fileBus == null) return OptionalLong.empty();
        try {
            return fileBus.remoteTimestampMs(playerName);
        } catch (IllegalArgumentException ignored) {
            return OptionalLong.empty();
        }
    }

    public static void flushFileCoordinationNow() throws IOException {
        if (fileBus == null) return;
        flushPendingFileMessages();
        fileBus.flush(System.currentTimeMillis());
    }

    public static void queueMasterDM(String message) {
        if (master != null) {
            queueDM(master, message);
        }
    }

    public static void queueDM(String recipient, String message) {
        if (removedSlaves.contains(recipient)) return;
        if (fileBus != null) {
            if (fileEpoch == null) {
                pendingFileMessages.add(
                    new PendingFileMessage(recipient, message)
                );
                return;
            }
            try {
                fileBus.enqueue(recipient, encodeFilePayload(message));
            } catch (IllegalArgumentException | IllegalStateException failure) {
                reportFileFailure(
                    "Could not queue file-coordination message for "
                        + recipient + ": " + failure.getMessage(),
                    System.currentTimeMillis()
                );
            }
            return;
        }
        toBeSentMessages.add(directMessagePrefix(recipient) + message);
    }

    private static String directMessagePrefix(String recipient) {
        return directMessageCommand + " " + recipient + " ";
    }

    private static void removeQueuedDMs(String recipient) {
        String prefix = directMessagePrefix(recipient);
        toBeSentMessages.removeIf(message -> message.startsWith(prefix));
    }

    private static void queuePriorityDM(String recipient, String message) {
        if (fileBus != null) {
            if (fileEpoch == null) {
                pendingFileMessages.add(
                    new PendingFileMessage(recipient, message)
                );
                filePollCountdown = 0;
                return;
            }
            try {
                // The durable bus is FIFO and intentionally does not reorder
                // already-published commands. Removal is only allowed outside
                // an active build, so appending the tombstone preserves causal
                // ordering without racing another writer.
                fileBus.enqueue(recipient, encodeFilePayload(message));
            } catch (IllegalArgumentException | IllegalStateException failure) {
                reportFileFailure(
                    "Could not queue file-coordination control message for "
                        + recipient + ": " + failure.getMessage(),
                    System.currentTimeMillis()
                );
            }
            filePollCountdown = 0;
            return;
        }
        removeQueuedDMs(recipient);
        toBeSentMessages.add(0, directMessagePrefix(recipient) + message);
        timeout = 0;
    }

    public static boolean allSlavesFinished() {
        for (String slave : finishedSlavesDict.keySet()) {
            if (!finishedSlavesDict.get(slave)) return false;
        }
        return true;
    }

    public static void setAllSlavesUnfinished() {
        for (String slave : finishedSlavesDict.keySet()) {
            finishedSlavesDict.put(slave, false);
        }
    }

    public static boolean isSlave() {
        return master != null;
    }

    public static boolean hasRelationship() {
        // A pending removal is still part of the relationship protocol. Keep it
        // across reconnects until the removed bot acknowledges the tombstone.
        return master != null || !slaves.isEmpty() || !removedSlaves.isEmpty();
    }

    public static void sendToAllSlaves(String message) {
        for (String slave : slaves) {
            SlaveSystem.queueDM(slave, message);
        }
    }

    public static void startAllSlaves() {
        for (String slave : new ArrayList<>(slaves)) {
            if (removedSlaves.contains(slave)) continue;
            pausedSlaves.remove(slave);
            queueAssignedInterval(slave);
            if (isIntervalAcknowledged(slave)) releaseSlave(slave);
        }
        if (printerModule != null && !printerModule.isActive() && !printerModule.getActivationReset())
            printerModule.toggle();
    }

    public static void pauseAllSlaves() {
        sendToAllSlaves("pause");
        for (String slave : activeSlavesDict.keySet()) {
            activeSlavesDict.put(slave, false);
            pausedSlaves.add(slave);
        }
        if (printerModule != null && printerModule.isActive() && !printerModule.getActivationReset())
            printerModule.toggle();
    }

    public static void skipNextBuilding() {
        sendToAllSlaves("skip");
        if (printerModule != null) printerModule.skipBuilding();
    }

    public static boolean isSlavePaused(String slave) {
        return pausedSlaves.contains(slave);
    }

    public static boolean isRemovalPending(String slave) {
        return removedSlaves.contains(slave) && slaves.contains(slave);
    }

    public static boolean hasPendingRemoval() {
        return !pendingRemovalSince.isEmpty();
    }

    public static void setSlavePaused(String slave, boolean paused) {
        if (!slaves.contains(slave) || removedSlaves.contains(slave)) return;
        if (paused) {
            pausedSlaves.add(slave);
            activeSlavesDict.put(slave, false);
            queueDM(slave, "pause");
        } else {
            pausedSlaves.remove(slave);
            queueAssignedInterval(slave);
            if (isIntervalAcknowledged(slave)) releaseSlave(slave);
        }
    }

    public static boolean isIntervalAcknowledged(String slave) {
        return assignedIntervals.containsKey(slave)
            && acknowledgedIntervals.contains(slave);
    }

    public static Tuple<Integer, Integer> assignedIntervalFor(String slave) {
        return assignedIntervals.get(slave);
    }

    public static void queueAssignedInterval(String slave) {
        Tuple<Integer, Integer> interval = assignedIntervals.get(slave);
        if (interval == null) return;
        queueDM(
            slave,
            "interval:" + interval.getA() + ":" + interval.getB()
        );
    }

    public static void releaseSlave(String slave) {
        if (!slaves.contains(slave)
            || pausedSlaves.contains(slave)
            || removedSlaves.contains(slave)) {
            return;
        }
        queueDM(slave, "start");
        activeSlavesDict.put(slave, true);
        if (printerModule != null) printerModule.slaveResumed(slave);
    }

    public static void generateIntervals() {
        if (printerModule != null
            && printerModule.isBuildingInProgress()
            && coordinationMode != CoordinationMode.FileCluster) {
            ChatUtils.warning(
                "Slave intervals cannot be redistributed while a map is being built. "
                    + "Wait until every bot has finished the build."
            );
            return;
        }
        // Compact circular traversal owns columns in inseparable (even, odd)
        // pairs. Distribute 64 pairs so no connector is split between bots.
        ArrayList<String> participatingSlaves = new ArrayList<>();
        for (String slave : slaves) {
            if (!removedSlaves.contains(slave)) participatingSlaves.add(slave);
        }
        ArrayList<Tuple<Integer, Integer>> intervals = new ArrayList<>(
            partitionCircularColumns(participatingSlaves.size() + 1)
        );
        assignedIntervals.clear();
        acknowledgedIntervals.clear();

        printerModule.setInterval(intervals.remove((intervals.size() - 1) / 2));

        // Remove all previously queued interval messages
        ArrayList<String> toBeRemoved = new ArrayList<>();
        for (String message : toBeSentMessages) {
            if (message.contains("interval")) toBeRemoved.add(message);
        }
        toBeRemoved.forEach((message) -> toBeSentMessages.remove(message));

        // Sort slaves deterministically
        ArrayList<String> sortedSlaves = new ArrayList<>(participatingSlaves);
        Collections.sort(sortedSlaves, String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < intervals.size(); i++) {
            String slave = sortedSlaves.get(i);
            Tuple<Integer, Integer> interval = intervals.get(i);
            assignedIntervals.put(slave, interval);
            SlaveSystem.queueDM(
                slave,
                "interval:" + interval.getA() + ":" + interval.getB()
            );
        }
    }

    public static void registerSlaves() {
        if (isFileMode()) {
            ChatUtils.warning(
                "File-coordinated bots are registered from the configured player-name list. "
                    + "Change the file role/names and restart the module to change membership."
            );
            return;
        }
        if (printerModule == null) {
            ChatUtils.warning("The module needs to be enabled to register new slaves.");
            return;
        }
        if (printerModule.isWorkInProgress()) {
            ChatUtils.warning(
                "New slaves cannot be registered while building or mining is active. "
                    + "Register them before the next map starts."
            );
            return;
        }
        ArrayList<String> foundPlayers = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && !mc.player.equals(player)) {
                foundPlayers.add(player.getName().getString());
            }
        }
        if (foundPlayers.isEmpty()) {
            ChatUtils.warning("No players found in render distance.");
        }
        toBeConfirmedSlaves = foundPlayers;
        for (String slave : foundPlayers) {
            if (slaves.contains(slave)) continue;
            if (removedSlaves.contains(slave)) {
                queuePriorityDM(slave, "remove");
                continue;
            }
            SlaveSystem.queueDM(slave, "register");
        }
    }

    public static void removeSlave(String slave) {
        if (printerModule != null && printerModule.isBuildingInProgress()) {
            ChatUtils.warning(
                "A slave cannot be removed during a map build because that would "
                    + "change pair ownership mid-traversal."
            );
            return;
        }
        if (!slaves.contains(slave)) return;
        if (removedSlaves.contains(slave)) {
            long requestedAt = pendingRemovalSince.getOrDefault(
                slave,
                System.currentTimeMillis()
            );
            long elapsed = System.currentTimeMillis() - requestedAt;
            if (elapsed < FORCE_REMOVAL_DELAY_MS) {
                long seconds = Math.max(
                    1L,
                    (FORCE_REMOVAL_DELAY_MS - elapsed + 999L) / 1000L
                );
                ChatUtils.warning(
                    "Waiting for " + slave + " to acknowledge removal. "
                        + "If it is definitely offline, click remove again in "
                        + seconds + " second(s) to force-release its assignment."
                );
                queuePriorityDM(slave, "remove");
                return;
            }
            ChatUtils.warning(
                "Force-released " + slave + "'s quarantined assignment. "
                    + "Its removal tombstone will remain until it reconnects."
            );
            finalizeSlaveRemoval(slave, false);
            return;
        }

        removedSlaves.add(slave);
        pendingRemovalSince.put(slave, System.currentTimeMillis());
        pausedSlaves.add(slave);
        activeSlavesDict.put(slave, false);
        queuePriorityDM(slave, "remove");
        ChatUtils.info(
            "Removal requested for " + slave
                + ". Its mining assignment stays reserved until removal is acknowledged."
        );
    }

    private static void finalizeSlaveRemoval(
        String slave,
        boolean acknowledged
    ) {
        removeQueuedDMs(slave);
        if (acknowledged) removedSlaves.remove(slave);
        pendingRemovalSince.remove(slave);
        toBeConfirmedSlaves.remove(slave);

        boolean wasRegistered = slaves.remove(slave);
        pausedSlaves.remove(slave);
        assignedIntervals.remove(slave);
        acknowledgedIntervals.remove(slave);
        activeSlavesDict.remove(slave);
        finishedSlavesDict.remove(slave);

        if (wasRegistered && printerModule != null) {
            printerModule.slaveRemoved(slave);
            generateIntervals();
        }
        if (!acknowledged) queuePriorityDM(slave, "remove");
        if (tableController != null) tableController.rebuild();
    }

    public static boolean canSeePlayer(String playerName) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player.getName().getString().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private static void handleMessage(String rawMessage, @Nullable String sender) {
        String content;
        // Extract sender from message if not provided in packet
        if (sender != null) {
            content = rawMessage;
        } else {
            int prefixIndex = rawMessage.indexOf(senderPrefix);
            int suffixIndex = rawMessage.indexOf(senderSuffix);
            if (prefixIndex == -1 || suffixIndex == -1) return;

            sender = rawMessage.substring(prefixIndex + senderPrefix.length(), suffixIndex);
            if (sender == mc.player.getName().getString()) return;
            content = rawMessage.substring(suffixIndex + senderSuffix.length());
        }

        String[] colonSplit = content.replace(" ", "").split(":");
        String command = colonSplit[0];
        if (removedSlaves.contains(sender)) {
            if (command.equals("removeAck")) {
                finalizeSlaveRemoval(sender, true);
            } else {
                queuePriorityDM(sender, "remove");
            }
            return;
        }
        // Register
        if (command.equals("register") && master == null && toBeConfirmedSlaves.isEmpty()
            && slaves.isEmpty() && canSeePlayer(sender)) {
            master = sender;
            if (printerModule != null) printerModule.masterRelationshipChanged();
            SlaveSystem.queueMasterDM("accept");
        }
        // Master to Client message
        if (sender.equals(master)) {
            switch (command) {
                case "interval":
                    if (colonSplit.length < 3) break;
                    Tuple<Integer, Integer> interval = new Tuple<>(Integer.valueOf(colonSplit[1]), Integer.valueOf(colonSplit[2]));
                    printerModule.setInterval(interval);
                    queueMasterDM(
                        "intervalAck:" + interval.getA() + ":" + interval.getB()
                    );
                    break;
                case "pause":
                    printerModule.pause();
                    break;
                case "start":
                    printerModule.start();
                    break;
                case "remove":
                    String previousMaster = master;
                    master = null;
                    printerModule.masterRelationshipChanged();
                    queuePriorityDM(previousMaster, "removeAck");
                    if (printerModule.isActive()) printerModule.toggle();
                    break;
                case "skip":
                    printerModule.skipBuilding();
                    break;
                case "mine": {
                    if (colonSplit.length < 2) break;
                    MiningAssignmentMode.Decision mode = colonSplit.length >= 3
                        ? MiningAssignmentMode.parseWireName(colonSplit[2])
                            .orElse(null)
                        : new MiningAssignmentMode.Decision(false, false);
                    if (mode == null) break;
                    printerModule.mineLine(
                        Integer.parseInt(colonSplit[1]),
                        mode.pairedTraversal(),
                        mode.wholePair()
                    );
                    break;
                }
                case "mine2": {
                    if (colonSplit.length < 4) break;
                    Long taskId = tryParseLong(colonSplit[1]);
                    if (taskId == null) break;
                    MiningAssignmentMode.Decision mode =
                        MiningAssignmentMode.parseWireName(colonSplit[3])
                            .orElse(null);
                    if (mode == null) break;
                    printerModule.mineLine(
                        Integer.parseInt(colonSplit[2]),
                        mode.pairedTraversal(),
                        mode.wholePair(),
                        taskId
                    );
                    break;
                }
                case "miningDone": {
                    if (colonSplit.length < 2) break;
                    Long sessionId = tryParseLong(colonSplit[1]);
                    if (sessionId == null) break;
                    printerModule.finishMiningCycle(sessionId);
                    break;
                }
                case "recovery": {
                    if (colonSplit.length < 3) break;
                    Long recoveryToken = tryParseLong(colonSplit[1]);
                    if (recoveryToken == null) break;
                    final MapCyclePhase phase;
                    try {
                        phase = MapCyclePhase.valueOf(colonSplit[2]);
                    } catch (IllegalArgumentException ignored) {
                        break;
                    }
                    printerModule.prepareFileRecovery(
                        phase,
                        recoveryToken
                    );
                    break;
                }
            }
        }
        // Client to Master message
        if (slaves.contains(sender) || toBeConfirmedSlaves.contains(sender)) {
            switch (command) {
                case "accept":
                    if (slaves.contains(sender)) {
                        toBeConfirmedSlaves.remove(sender);
                        break;
                    }
                    if (printerModule != null && printerModule.isWorkInProgress()) {
                        toBeConfirmedSlaves.remove(sender);
                        removedSlaves.add(sender);
                        queuePriorityDM(sender, "remove");
                        ChatUtils.warning(
                            "Ignored a late slave registration while building or mining is active."
                        );
                        break;
                    }
                    slaves.add(sender);
                    pausedSlaves.remove(sender);
                    finishedSlavesDict.put(sender, false);
                    activeSlavesDict.put(sender, false);
                    toBeConfirmedSlaves.remove(sender);
                    ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                    generateIntervals();
                    if (tableController != null) tableController.rebuild();
                    break;
                case "finished":
                    finishedSlavesDict.put(sender, true);
                    activeSlavesDict.put(sender, false);
                    printerModule.slaveFinished(sender);
                    if (tableController != null) tableController.rebuild();
                    break;
                case "mined":
                    if (colonSplit.length < 2) break;
                    Long taskId = tryParseLong(colonSplit[1]);
                    if (taskId == null) break;
                    boolean assignedConnectorsClear = colonSplit.length >= 3
                        && colonSplit[2].equalsIgnoreCase("clear");
                    printerModule.slaveMined(
                        sender,
                        taskId,
                        assignedConnectorsClear
                    );
                    if (tableController != null) tableController.rebuild();
                    break;
                case "miningDoneAck": {
                    if (colonSplit.length < 2) break;
                    Long sessionId = tryParseLong(colonSplit[1]);
                    if (sessionId == null) break;
                    printerModule.slaveMiningCycleFinalized(sender, sessionId);
                    break;
                }
                case "recoveryAck": {
                    if (colonSplit.length < 2) break;
                    Long recoveryToken = tryParseLong(colonSplit[1]);
                    if (recoveryToken == null) break;
                    printerModule.slaveFileRecoveryReady(
                        sender,
                        recoveryToken
                    );
                    break;
                }
                case "sync":
                    queueAssignedInterval(sender);
                    printerModule.slaveSync(sender);
                    break;
                case "intervalAck": {
                    if (colonSplit.length < 3) break;
                    Tuple<Integer, Integer> expected = assignedIntervals.get(sender);
                    if (expected == null) break;
                    Integer left = Integer.valueOf(colonSplit[1]);
                    Integer right = Integer.valueOf(colonSplit[2]);
                    if (expected.getA().equals(left)
                        && expected.getB().equals(right)) {
                        acknowledgedIntervals.add(sender);
                        printerModule.slaveIntervalReady(sender);
                    }
                    break;
                }
                case "error":
                    if (colonSplit.length < 3) break;
                    BlockPos relativeErrorPos;
                    if (colonSplit.length >= 4) {
                        relativeErrorPos = new BlockPos(
                            Integer.valueOf(colonSplit[1]),
                            Integer.valueOf(colonSplit[2]),
                            Integer.valueOf(colonSplit[3])
                        );
                    } else {
                        // Backward compatibility with old surface-only clients.
                        relativeErrorPos = new BlockPos(
                            Integer.valueOf(colonSplit[1]),
                            0,
                            Integer.valueOf(colonSplit[2])
                        );
                    }
                    printerModule.addError(relativeErrorPos);
                    break;
            }
        }
    }

    private static Long tryParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Splits all 64 inseparable U-pairs into exactly {@code botCount}
     * non-empty, gap-free intervals. Integer boundary partitioning is
     * important here: ceil-sized chunks can produce fewer chunks than bots
     * for counts such as 9 or 12, leaving a configured slave unassigned.
     */
    static List<Tuple<Integer, Integer>> partitionCircularColumns(
        int botCount
    ) {
        if (botCount < 1 || botCount > 64) {
            throw new IllegalArgumentException(
                "botCount must be between 1 and 64."
            );
        }
        ArrayList<Tuple<Integer, Integer>> intervals =
            new ArrayList<>(botCount);
        for (int index = 0; index < botCount; index++) {
            int startPair = index * 64 / botCount;
            int endPair = (index + 1) * 64 / botCount - 1;
            intervals.add(
                new Tuple<>(startPair * 2, endPair * 2 + 1)
            );
        }
        return List.copyOf(intervals);
    }

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (printerModule == null || isFileMode()) return;

        if (event.packet instanceof ClientboundPlayerChatPacket packet) {
            handleMessage(packet.body().content(), packet.chatType().name().getString());
        }

        if (event.packet instanceof ClientboundSystemChatPacket packet) {
            handleMessage(packet.content().getString(), null);
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (fileBus != null) {
            if (filePollCountdown > 0) {
                filePollCountdown--;
                return;
            }
            filePollCountdown = Math.max(0, filePollTicks - 1);
            pollFileBus();
            return;
        }
        if (mc.getConnection() == null) return;
        if (timeout > 0) timeout--;
        if (!toBeSentMessages.isEmpty()) {
            if (timeout <= 0) {
                String message = toBeSentMessages.remove(0);
                if (randomLength > 0) message += ":" + UUID.randomUUID().toString().substring(0, randomLength);
                mc.getConnection().sendCommand(message);
                timeout = commandDelay;
            }
        }
    }

    private static void pollFileBus() {
        long now = System.currentTimeMillis();
        try {
            reconcileClusterLeadership(now);
            // First refresh peer snapshots and publish messages queued since
            // the previous poll.
            fileBus.flush(now);
            reconcileClusterMembership(now);
            adoptMasterFileEpoch();
            flushPendingFileMessages();
            ArrayList<SharedFileCoordinationBus.Envelope> incoming =
                new ArrayList<>(deferredIncomingFileMessages);
            deferredIncomingFileMessages.clear();
            incoming.addAll(fileBus.poll());
            boolean deferRemaining = false;
            for (SharedFileCoordinationBus.Envelope envelope : incoming) {
                String decoded = decodeFilePayload(envelope.payload());
                if (decoded != null
                    && (deferRemaining || shouldDeferFileCommand(decoded))) {
                    deferRemaining = true;
                    deferredIncomingFileMessages.add(envelope);
                    continue;
                }
                try {
                    if (decoded != null) {
                        handleMessage(decoded, envelope.sender());
                    }
                } catch (RuntimeException malformedMessage) {
                    reportFileFailure(
                        "Ignored malformed file-coordination message "
                            + envelope.id() + " from " + envelope.sender()
                            + ": " + malformedMessage.getMessage(),
                        now
                    );
                }
                // A malformed command from a configured peer is a poison
                // message. Acknowledge it after reporting so later valid FIFO
                // messages are not blocked forever.
                fileBus.acknowledge(envelope);
            }
            // Persist acknowledgements and any responses generated while
            // dispatching the incoming batch.
            fileBus.flush(now);
        } catch (IOException | RuntimeException failure) {
            reportFileFailure(
                "File coordination is temporarily unavailable: "
                    + failure.getMessage(),
                now
            );
        }
    }

    private static boolean shouldDeferFileCommand(String payload) {
        if (!isFileSlave()) {
            return false;
        }
        String compact = payload.replace(" ", "");
        int separator = compact.indexOf(':');
        String command = separator < 0
            ? compact
            : compact.substring(0, separator);
        // Interval negotiation is what lets the master eventually release the
        // slave. Pause/removal are always safe control-plane operations.
        if (command.equals("interval")
            || command.equals("pause")
            || command.equals("remove")) {
            return false;
        }
        Map<String, String> masterMetadata = fileBus.remoteMetadata(master);
        boolean masterAvailable =
            "true".equalsIgnoreCase(masterMetadata.get("active"))
                && fileBus.isPeerFresh(
                    master,
                    System.currentTimeMillis(),
                    filePeerMaximumAgeMs
                );
        boolean localReady = "true".equalsIgnoreCase(
            fileBus.localMetadata().get("ready")
        );
        return !masterAvailable || !localReady;
    }

    private static void reconcileClusterLeadership(long now)
        throws IOException {
        if (coordinationMode != CoordinationMode.FileCluster
            || fileBus == null
            || fileLocalPlayerName == null) {
            return;
        }
        SharedFileCoordinationBus.MasterSnapshot snapshot =
            SharedFileCoordinationBus.inspectMaster(fileBus.directory())
                .orElse(null);
        boolean coordinatorHealthy = isHealthyCoordinatorSnapshot(
            snapshot,
            now
        );

        if (isFileMaster()) {
            if (coordinatorHealthy
                && !snapshot.nodeId().equalsIgnoreCase(fileLocalPlayerName)) {
                boolean preferred = snapshot.nodeId().equalsIgnoreCase(
                        fileClusterPrimary
                    )
                    || (!fileLocalPlayerName.equalsIgnoreCase(
                            fileClusterPrimary
                        )
                        && snapshot.nodeId().compareToIgnoreCase(
                            fileLocalPlayerName
                        ) < 0);
                if (preferred) {
                    printerModule.prepareFileCoordinatorRoleChange();
                    if (printerModule.isFileCoordinatorRoleChangeReady()) {
                        becomeClusterSlave(snapshot);
                    }
                    return;
                }
            }
            handlePrimaryHandBackRequest();
            return;
        }

        if (coordinatorHealthy) {
            fileCoordinatorMissingSinceMs = -1L;
            if (!snapshot.nodeId().equalsIgnoreCase(master)) {
                becomeClusterSlave(snapshot);
            }
            if (fileLocalPlayerName.equalsIgnoreCase(fileClusterPrimary)
                && !snapshot.nodeId().equalsIgnoreCase(
                    fileLocalPlayerName
                )) {
                fileBus.setLocalMetadata(
                    FILE_META_HANDOFF_REQUEST,
                    fileLocalPlayerName
                );
                printerModule.prepareFileCoordinatorRoleChange();
                if (fileLocalPlayerName.equalsIgnoreCase(
                        snapshot.metadata().get(FILE_META_HANDOFF_READY)
                    )
                    && printerModule.isFileCoordinatorRoleChangeReady()) {
                    becomeClusterMaster(snapshot, now);
                }
            } else {
                fileBus.removeLocalMetadata(FILE_META_HANDOFF_REQUEST);
            }
            return;
        }

        if (fileCoordinatorMissingSinceMs < 0) {
            fileCoordinatorMissingSinceMs = now;
            return;
        }
        if (now - fileCoordinatorMissingSinceMs < filePeerMaximumAgeMs) return;

        String elected = electClusterCoordinator(now);
        if (elected != null
            && elected.equalsIgnoreCase(fileLocalPlayerName)) {
            printerModule.prepareFileCoordinatorRoleChange();
            if (printerModule.isFileCoordinatorRoleChangeReady()) {
                becomeClusterMaster(snapshot, now);
            }
        }
    }

    private static void handlePrimaryHandBackRequest() {
        if (fileLocalPlayerName.equalsIgnoreCase(fileClusterPrimary)) {
            fileBus.removeLocalMetadata(FILE_META_HANDOFF_READY);
            return;
        }
        Map<String, String> primaryMetadata = remoteFileMetadata(
            fileClusterPrimary
        );
        boolean requested = isFilePeerFresh(
                fileClusterPrimary,
                filePeerMaximumAgeMs
            )
            && "true".equalsIgnoreCase(primaryMetadata.get("active"))
            && fileClusterPrimary.equalsIgnoreCase(
                primaryMetadata.get(FILE_META_HANDOFF_REQUEST)
            );
        if (!requested) {
            fileBus.removeLocalMetadata(FILE_META_HANDOFF_READY);
            return;
        }
        printerModule.prepareFileCoordinatorRoleChange();
        if (printerModule.isFileCoordinatorRoleChangeReady()) {
            fileBus.setLocalMetadata(
                FILE_META_HANDOFF_READY,
                fileClusterPrimary
            );
        }
    }

    private static String electClusterCoordinator(long now) {
        ArrayList<String> healthy = new ArrayList<>();
        healthy.add(fileLocalPlayerName);
        for (String participant : fileClusterParticipants) {
            if (participant.equalsIgnoreCase(fileLocalPlayerName)) continue;
            if (isFilePeerFresh(participant, filePeerMaximumAgeMs)
                && "true".equalsIgnoreCase(
                    remoteFileMetadata(participant).get("active")
                )) {
                healthy.add(participant);
            }
        }
        for (String participant : healthy) {
            if (participant.equalsIgnoreCase(fileClusterPrimary)) {
                return participant;
            }
        }
        healthy.sort(String.CASE_INSENSITIVE_ORDER);
        return healthy.isEmpty() ? null : healthy.get(0);
    }

    private static void becomeClusterMaster(
        SharedFileCoordinationBus.MasterSnapshot previousMaster,
        long now
    ) throws IOException {
        Map<String, String> inherited = previousMaster == null
            ? fileBus.localMetadata()
            : previousMaster.metadata();
        ArrayList<String> healthyPeers = new ArrayList<>();
        for (String peer : clusterPeers(fileLocalPlayerName)) {
            if (isFilePeerFresh(peer, filePeerMaximumAgeMs)
                && "true".equalsIgnoreCase(
                    remoteFileMetadata(peer).get("active")
                )) {
                healthyPeers.add(peer);
            }
        }
        Path directory = fileBus.directory();
        fileBus = SharedFileCoordinationBus.openMasterForTakeover(
            directory,
            fileLocalPlayerName,
            clusterPeers(fileLocalPlayerName)
        );
        copyFileMetadata(inherited);
        fileBus.removeLocalMetadata("ready");
        fileBus.removeLocalMetadata(FILE_META_HANDOFF_REQUEST);
        fileBus.removeLocalMetadata(FILE_META_HANDOFF_READY);
        master = null;
        slaves.clear();
        slaves.addAll(healthyPeers);
        resetMasterPeerState();
        fileEpoch = UUID.randomUUID().toString();
        fileBus.setLocalMetadata("epoch", fileEpoch);
        publishClusterIdentityMetadata();
        fileCoordinatorMissingSinceMs = -1L;
        fileMembershipGraceUntilMs = now + filePeerMaximumAgeMs;
        pendingFileMessages.clear();
        deferredIncomingFileMessages.clear();
        printerModule.masterRelationshipChanged();
        generateIntervals();
    }

    private static void becomeClusterSlave(
        SharedFileCoordinationBus.MasterSnapshot newMaster
    ) throws IOException {
        String coordinator = newMaster.nodeId();
        if (coordinator.equalsIgnoreCase(fileLocalPlayerName)) return;
        Map<String, String> localMetadata = fileBus.localMetadata();
        for (String slave : new ArrayList<>(slaves)) {
            printerModule.slaveRemoved(slave);
        }
        Path directory = fileBus.directory();
        fileBus = SharedFileCoordinationBus.openSlaveForHandoff(
            directory,
            coordinator,
            fileLocalPlayerName,
            clusterPeers(fileLocalPlayerName)
        );
        copyFileMetadata(localMetadata);
        master = coordinator;
        slaves.clear();
        resetMasterPeerState();
        fileEpoch = newMaster.metadata().get("epoch");
        if (fileEpoch != null) fileBus.setLocalMetadata("epoch", fileEpoch);
        fileBus.removeLocalMetadata(FILE_META_HANDOFF_READY);
        publishClusterIdentityMetadata();
        fileCoordinatorMissingSinceMs = -1L;
        pendingFileMessages.clear();
        deferredIncomingFileMessages.clear();
        printerModule.masterRelationshipChanged();
        pendingFileMessages.add(new PendingFileMessage(master, "sync"));
    }

    private static void reconcileClusterMembership(long now) {
        if (coordinationMode != CoordinationMode.FileCluster
            || !isFileMaster()) {
            return;
        }
        boolean changed = false;
        for (String participant : clusterPeers(fileLocalPlayerName)) {
            boolean hasSnapshot = fileBus.remoteTimestampMs(participant)
                .isPresent();
            boolean present = isFilePeerFresh(
                    participant,
                    filePeerMaximumAgeMs
                )
                && "true".equalsIgnoreCase(
                    remoteFileMetadata(participant).get("active")
                );
            if (present && !slaves.contains(participant)) {
                slaves.add(participant);
                activeSlavesDict.put(participant, false);
                finishedSlavesDict.put(participant, false);
                pausedSlaves.remove(participant);
                changed = true;
            } else if (!present
                && slaves.contains(participant)
                && (hasSnapshot || now >= fileMembershipGraceUntilMs)) {
                slaves.remove(participant);
                activeSlavesDict.remove(participant);
                finishedSlavesDict.remove(participant);
                pausedSlaves.remove(participant);
                assignedIntervals.remove(participant);
                acknowledgedIntervals.remove(participant);
                pendingFileMessages.removeIf(
                    pending -> pending.recipient().equals(participant)
                );
                printerModule.slaveRemoved(participant);
                changed = true;
            }
        }
        if (!changed) return;
        slaves.sort(String.CASE_INSENSITIVE_ORDER);
        generateIntervals();
        if (tableController != null) tableController.rebuild();
    }

    private static boolean isHealthyCoordinatorSnapshot(
        SharedFileCoordinationBus.MasterSnapshot snapshot,
        long now
    ) {
        if (snapshot == null
            || !isConfiguredClusterParticipant(snapshot.nodeId())) {
            return false;
        }
        long age = now - snapshot.writtenAtMs();
        return age >= 0
            && age <= filePeerMaximumAgeMs
            && "true".equalsIgnoreCase(snapshot.metadata().get("active"));
    }

    private static boolean isConfiguredClusterParticipant(String player) {
        if (player == null) return false;
        return fileClusterParticipants.stream().anyMatch(
            participant -> participant.equalsIgnoreCase(player)
        );
    }

    private static List<String> clusterPeers(String local) {
        return fileClusterParticipants.stream()
            .filter(participant -> !participant.equalsIgnoreCase(local))
            .toList();
    }

    private static void copyFileMetadata(Map<String, String> metadata) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                fileBus.setLocalMetadata(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void publishClusterIdentityMetadata() {
        if (fileBus == null) return;
        fileBus.setLocalMetadata(
            FILE_META_COORDINATOR_ROLE,
            isFileMaster() ? "master" : "slave"
        );
        if (coordinationMode == CoordinationMode.FileCluster) {
            fileBus.setLocalMetadata(
                FILE_META_CLUSTER_PRIMARY,
                fileClusterPrimary
            );
            fileBus.setLocalMetadata(
                FILE_META_CLUSTER_PARTICIPANTS,
                String.join(",", fileClusterParticipants)
            );
        }
    }

    private static void resetMasterPeerState() {
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        pausedSlaves.clear();
        assignedIntervals.clear();
        acknowledgedIntervals.clear();
        for (String slave : slaves) {
            activeSlavesDict.put(slave, false);
            finishedSlavesDict.put(slave, false);
        }
    }

    private static void flushFileBus(long now) throws IOException {
        if (fileBus != null) fileBus.flush(now);
    }

    private static void adoptMasterFileEpoch() {
        if (!isFileSlave() || master == null) return;
        String advertisedEpoch = fileBus.remoteMetadata(master).get("epoch");
        if (advertisedEpoch == null
            || advertisedEpoch.isBlank()
            || advertisedEpoch.equals(fileEpoch)) {
            return;
        }
        fileEpoch = advertisedEpoch;
        fileBus.setLocalMetadata("epoch", fileEpoch);
        pendingFileMessages.clear();
        printerModule.masterRelationshipChanged();
        pendingFileMessages.add(new PendingFileMessage(master, "sync"));
    }

    private static void flushPendingFileMessages() {
        if (fileBus == null || fileEpoch == null || pendingFileMessages.isEmpty()) {
            return;
        }
        ArrayList<PendingFileMessage> pending =
            new ArrayList<>(pendingFileMessages);
        pendingFileMessages.clear();
        for (PendingFileMessage message : pending) {
            try {
                fileBus.enqueue(
                    message.recipient(),
                    encodeFilePayload(message.payload())
                );
            } catch (IllegalArgumentException | IllegalStateException failure) {
                reportFileFailure(
                    "Could not queue deferred file-coordination message for "
                        + message.recipient() + ": " + failure.getMessage(),
                    System.currentTimeMillis()
                );
            }
        }
    }

    private static String encodeFilePayload(String payload) {
        Map<String, String> metadata = fileBus.localMetadata();
        String jobId = metadata.getOrDefault("jobId", "");
        String generation = metadata.getOrDefault("generation", "");
        return FILE_ENVELOPE_PREFIX
            + fileEpoch + "|" + jobId + "|" + generation + "|" + payload;
    }

    /**
     * Returns null for a stale coordinator epoch. Those envelopes are still
     * acknowledged by the caller so a previous run cannot block the current
     * FIFO forever.
     */
    private static String decodeFilePayload(String payload) {
        if (payload == null || !payload.startsWith(FILE_ENVELOPE_PREFIX)) {
            return null;
        }
        int epochEnd = payload.indexOf('|', FILE_ENVELOPE_PREFIX.length());
        if (epochEnd < 0) return null;
        String envelopeEpoch = payload.substring(
            FILE_ENVELOPE_PREFIX.length(),
            epochEnd
        );
        if (!envelopeEpoch.equals(fileEpoch)) return null;
        int jobEnd = payload.indexOf('|', epochEnd + 1);
        if (jobEnd < 0) return null;
        int generationEnd = payload.indexOf('|', jobEnd + 1);
        if (generationEnd < 0) return null;
        String envelopeJob = payload.substring(epochEnd + 1, jobEnd);
        String envelopeGeneration = payload.substring(
            jobEnd + 1,
            generationEnd
        );
        Map<String, String> expectedMetadata = isFileMaster()
            ? fileBus.localMetadata()
            : fileBus.remoteMetadata(master);
        if (!envelopeJob.equals(expectedMetadata.get("jobId"))
            || !envelopeGeneration.equals(
                expectedMetadata.get("generation")
            )) {
            return null;
        }
        return payload.substring(generationEnd + 1);
    }

    private record PendingFileMessage(String recipient, String payload) {
    }

    private static void reportFileFailure(String message, long now) {
        if (lastFileErrorReportMs != Long.MIN_VALUE
            && now - lastFileErrorReportMs < FILE_ERROR_REPORT_INTERVAL_MS) {
            return;
        }
        lastFileErrorReportMs = now;
        ChatUtils.warning(message);
    }
}
