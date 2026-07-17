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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SlaveSystem {

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
    }

    public static void queueMasterDM(String message) {
        if (master != null) {
            queueDM(master, message);
        }
    }

    public static void queueDM(String recipient, String message) {
        if (removedSlaves.contains(recipient)) return;
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
        int sectionSize = (int) Math.ceil(
            (float) 64 / (float) (participatingSlaves.size() + 1)
        );
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int endPair = 63; endPair >= 0; endPair -= sectionSize) {
            int startPair = Math.max(0, endPair - sectionSize + 1);
            intervals.add(new Pair<>(startPair * 2, endPair * 2 + 1));
        }
        Collections.reverse(intervals);
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

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (printerModule == null) return;

        if (event.packet instanceof ChatMessageS2CPacket packet) {
            handleMessage(packet.body().content(), packet.serializedParameters().name().getString());
        }

        if (event.packet instanceof GameMessageS2CPacket packet) {
            handleMessage(packet.content().getString(), null);
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
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
}
