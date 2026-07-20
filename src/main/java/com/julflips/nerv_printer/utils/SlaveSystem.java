package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.interfaces.MapPrinter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
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
import java.util.OptionalLong;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SlaveSystem {

    public enum CoordinationMode {
        Chat,
        FileMaster,
        FileSlave
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
    private static HashMap<String, Pair<Integer, Integer>> assignedIntervals =
        new HashMap<>();
    private static HashSet<String> acknowledgedIntervals = new HashSet<>();
    private static String master = null;
    private static final long FORCE_REMOVAL_DELAY_MS = 10_000L;
    private static final long FILE_ERROR_REPORT_INTERVAL_MS = 10_000L;
    private static final long FILE_MASTER_MAX_AGE_MS = 30_000L;
    private static final String FILE_ENVELOPE_PREFIX = "nerv-file-v2|";
    private static CoordinationMode coordinationMode = CoordinationMode.Chat;
    private static SharedFileCoordinationBus fileBus = null;
    private static String fileEpoch = null;
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

        if (mode == CoordinationMode.FileMaster) {
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

    public static CoordinationMode coordinationMode() {
        return coordinationMode;
    }

    public static boolean isFileMode() {
        return coordinationMode != CoordinationMode.Chat && fileBus != null;
    }

    public static boolean isFileMaster() {
        return coordinationMode == CoordinationMode.FileMaster
            && fileBus != null;
    }

    public static boolean isFileSlave() {
        return coordinationMode == CoordinationMode.FileSlave
            && fileBus != null;
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

    public static void queueAssignedInterval(String slave) {
        Pair<Integer, Integer> interval = assignedIntervals.get(slave);
        if (interval == null) return;
        queueDM(
            slave,
            "interval:" + interval.getLeft() + ":" + interval.getRight()
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
        if (printerModule != null && printerModule.isBuildingInProgress()) {
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
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>(
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
            Pair<Integer, Integer> interval = intervals.get(i);
            assignedIntervals.put(slave, interval);
            SlaveSystem.queueDM(
                slave,
                "interval:" + interval.getLeft() + ":" + interval.getRight()
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
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && !mc.player.equals(player)) {
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
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player.getName().getString().equals(playerName)) {
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
                    Pair<Integer, Integer> interval = new Pair<>(Integer.valueOf(colonSplit[1]), Integer.valueOf(colonSplit[2]));
                    printerModule.setInterval(interval);
                    queueMasterDM(
                        "intervalAck:" + interval.getLeft() + ":" + interval.getRight()
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
                    Pair<Integer, Integer> expected = assignedIntervals.get(sender);
                    if (expected == null) break;
                    Integer left = Integer.valueOf(colonSplit[1]);
                    Integer right = Integer.valueOf(colonSplit[2]);
                    if (expected.getLeft().equals(left)
                        && expected.getRight().equals(right)) {
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
    static List<Pair<Integer, Integer>> partitionCircularColumns(
        int botCount
    ) {
        if (botCount < 1 || botCount > 64) {
            throw new IllegalArgumentException(
                "botCount must be between 1 and 64."
            );
        }
        ArrayList<Pair<Integer, Integer>> intervals =
            new ArrayList<>(botCount);
        for (int index = 0; index < botCount; index++) {
            int startPair = index * 64 / botCount;
            int endPair = (index + 1) * 64 / botCount - 1;
            intervals.add(
                new Pair<>(startPair * 2, endPair * 2 + 1)
            );
        }
        return List.copyOf(intervals);
    }

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (printerModule == null || isFileMode()) return;

        if (event.packet instanceof ChatMessageS2CPacket packet) {
            handleMessage(packet.body().content(), packet.serializedParameters().name().getString());
        }

        if (event.packet instanceof GameMessageS2CPacket packet) {
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
        if (mc.getNetworkHandler() == null) return;
        if (timeout > 0) timeout--;
        if (!toBeSentMessages.isEmpty()) {
            if (timeout <= 0) {
                String message = toBeSentMessages.remove(0);
                if (randomLength > 0) message += ":" + UUID.randomUUID().toString().substring(0, randomLength);
                mc.getNetworkHandler().sendChatCommand(message);
                timeout = commandDelay;
            }
        }
    }

    private static void pollFileBus() {
        long now = System.currentTimeMillis();
        try {
            // First refresh peer snapshots and publish messages queued since
            // the previous poll.
            fileBus.flush(now);
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
                    FILE_MASTER_MAX_AGE_MS
                );
        boolean localReady = "true".equalsIgnoreCase(
            fileBus.localMetadata().get("ready")
        );
        return !masterAvailable || !localReady;
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
