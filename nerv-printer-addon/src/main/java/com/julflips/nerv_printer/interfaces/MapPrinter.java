package com.julflips.nerv_printer.interfaces;

import com.julflips.nerv_printer.utils.MapCyclePhase;
import net.minecraft.core.BlockPos;
import com.julflips.nerv_printer.utils.Tuple;

public interface MapPrinter {

    void setInterval(Tuple<Integer, Integer> interval);

    void mineLine(int minedLines);

    default void mineLine(int minedLines, boolean pairedTraversal) {
        mineLine(minedLines);
    }

    default void mineLine(
        int minedLines,
        boolean pairedTraversal,
        boolean reserveWholePair
    ) {
        mineLine(minedLines, pairedTraversal);
    }

    default void mineLine(
        int minedLines,
        boolean pairedTraversal,
        boolean reserveWholePair,
        long taskId
    ) {
        mineLine(minedLines, pairedTraversal, reserveWholePair);
    }

    void addError(BlockPos relativeBlockPos);

    void pause();

    void start();

    boolean isActive();

    void toggle();

    boolean getActivationReset();

    default boolean isBuildingInProgress() {
        return false;
    }

    default boolean isWorkInProgress() {
        return isBuildingInProgress();
    }

    void skipBuilding();

    void slaveFinished(String slave);

    default void slaveFinished(String slave, long taskId) {
        slaveFinished(slave);
    }

    default boolean slaveMined(String slave, long taskId) {
        slaveFinished(slave, taskId);
        return true;
    }

    default boolean slaveMined(
        String slave,
        long taskId,
        boolean assignedConnectorsClear
    ) {
        return slaveMined(slave, taskId);
    }

    default void slaveRemoved(String slave) {
    }

    default void finishMiningCycle(long sessionId) {
        start();
    }

    default void slaveMiningCycleFinalized(String slave, long sessionId) {
    }

    default void slaveSync(String slave) {
    }

    default void slaveResumed(String slave) {
    }

    default void slaveIntervalReady(String slave) {
    }

    default void masterRelationshipChanged() {
    }

    /** Requests a role change at the next safe U/task boundary. */
    default void prepareFileCoordinatorRoleChange() {
    }

    default boolean isFileCoordinatorRoleChangeReady() {
        return !isBuildingInProgress();
    }

    default void prepareFileRecovery(
        MapCyclePhase phase,
        long recoveryToken
    ) {
    }

    default void slaveFileRecoveryReady(
        String slave,
        long recoveryToken
    ) {
    }
}
