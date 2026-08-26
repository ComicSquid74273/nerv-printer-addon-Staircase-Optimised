package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Scoped adapter around Meteor's Entity Control module. It intentionally uses
 * public module/setting names rather than private fields so a compatible Meteor
 * update can continue to work without reflection.
 */
public final class BoatFlyAdapter {
    public static final double CRUISE_BLOCKS_PER_SECOND = 20.0;
    public static final double BUILD_BLOCKS_PER_SECOND = 15.0;

    public enum DriveMode {
        TRAVEL(CRUISE_BLOCKS_PER_SECOND, CRUISE_BLOCKS_PER_SECOND),
        BUILD(BUILD_BLOCKS_PER_SECOND, BUILD_BLOCKS_PER_SECOND);

        private final double horizontalBlocksPerSecond;
        private final double verticalBlocksPerSecond;

        DriveMode(
            double horizontalBlocksPerSecond,
            double verticalBlocksPerSecond
        ) {
            this.horizontalBlocksPerSecond = horizontalBlocksPerSecond;
            this.verticalBlocksPerSecond = verticalBlocksPerSecond;
        }

        public double blocksPerSecond() {
            return horizontalBlocksPerSecond;
        }

        public double verticalBlocksPerSecond() {
            return verticalBlocksPerSecond;
        }
    }

    private static final List<RequiredSetting<?>> REQUIRED = List.of(
        new RequiredSetting<>("entities", Set.class, null),
        new RequiredSetting<>("speed", Boolean.class, true),
        new RequiredSetting<>("horizontal-speed", Double.class, CRUISE_BLOCKS_PER_SECOND),
        new RequiredSetting<>("only-on-ground", Boolean.class, false),
        new RequiredSetting<>("fly", Boolean.class, true),
        new RequiredSetting<>("vertical-speed", Double.class, CRUISE_BLOCKS_PER_SECOND),
        new RequiredSetting<>("fall-speed", Double.class, 0.0),
        new RequiredSetting<>("lock-yaw", Boolean.class, true),
        new RequiredSetting<>("cancel-server-packets", Boolean.class, false)
    );

    private Module entityControl;
    private Snapshot snapshot;
    private String failureReason;
    private double configuredTravelSpeed = CRUISE_BLOCKS_PER_SECOND;
    private double configuredBuildSpeed = BUILD_BLOCKS_PER_SECOND;
    private DriveMode lastDriveMode;

    public record Compatibility(boolean compatible, String reason) {
    }

    private record RequiredSetting<T>(String name, Class<T> type, T value) {
    }

    private record Snapshot(boolean active, Map<String, Object> values) {
        private Snapshot {
            values = Map.copyOf(values);
        }
    }

    public Compatibility compatibility() {
        Module candidate = Modules.get().get("entity-control");
        if (candidate == null) {
            return new Compatibility(false, "Meteor Entity Control is missing.");
        }
        for (RequiredSetting<?> required : REQUIRED) {
            Setting<?> setting = candidate.settings.get(required.name());
            if (setting == null || !required.type().isInstance(setting.get())) {
                return new Compatibility(
                    false,
                    "Entity Control setting '" + required.name() + "' is missing or incompatible."
                );
            }
        }
        return new Compatibility(true, "ready");
    }

    public boolean acquire() {
        if (snapshot != null) return true;
        Compatibility compatibility = compatibility();
        if (!compatibility.compatible()) {
            failureReason = compatibility.reason();
            return false;
        }

        entityControl = Objects.requireNonNull(Modules.get().get("entity-control"));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (RequiredSetting<?> required : REQUIRED) {
            Object current = entityControl.settings.get(required.name()).get();
            values.put(
                required.name(),
                current instanceof Set<?> set ? Set.copyOf(set) : current
            );
        }
        snapshot = new Snapshot(entityControl.isActive(), values);

        for (RequiredSetting<?> required : REQUIRED) {
            if (required.value() == null) continue;
            if (!setUnchecked(entityControl.settings.get(required.name()), required.value())) {
                failureReason = "Entity Control rejected setting '" + required.name() + "'.";
                release();
                return false;
            }
        }
        if (!entityControl.isActive()) entityControl.enable();
        failureReason = null;
        return true;
    }

    public void drive(Entity vehicle, Vec3d target, DriveMode mode) {
        if (snapshot == null || mc.player == null || vehicle == null || target == null) {
            stop();
            return;
        }
        double dx = target.x - vehicle.getX();
        double dz = target.z - vehicle.getZ();
        double dy = target.y - vehicle.getY();

        DriveMode requestedMode = Objects.requireNonNull(mode, "mode");
        double requestedSpeed = requestedMode == DriveMode.BUILD
            ? configuredBuildSpeed : configuredTravelSpeed;
        double requestedVerticalSpeed = requestedMode == DriveMode.BUILD
            ? configuredBuildSpeed : configuredTravelSpeed;
        double arrivalRadius = 0.10;
        BoatFlyHorizontalStep.Velocity horizontalStep =
            BoatFlyHorizontalStep.toward(
                dx,
                dz,
                requestedSpeed,
                arrivalRadius
            );
        boolean horizontalArrived = horizontalStep.arrived();
        // Apply the exact requested per-tick vector ourselves. Depending on a
        // previous Entity Control velocity sample made travel remain at zero,
        // while braking every BUILD re-entry created visible stutter.
        lastDriveMode = requestedMode;
        Utils.setLeftPressed(false);
        Utils.setRightPressed(false);
        if (!horizontalArrived) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            mc.player.setYaw(MathHelper.wrapDegrees(yaw));
            vehicle.setYaw(MathHelper.wrapDegrees(yaw));
            Utils.setForwardPressed(true);
        } else {
            Utils.setForwardPressed(false);
            Vec3d velocity = vehicle.getVelocity();
            vehicle.setVelocity(0.0, velocity.y, 0.0);
        }
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(dy > 0.10);
        Utils.setSprintPressed(dy < -0.10);
        double verticalVelocity = dy > 0.10
            ? Math.min(requestedVerticalSpeed / 20.0, dy)
            : dy < -0.10
                ? Math.max(-requestedVerticalSpeed / 20.0, dy)
                : 0.0;
        // Entity Control also consumes the injected movement keys. Give it
        // the clamped final-step speeds so its own tick cannot overwrite the
        // exact velocity below with a full-speed overshoot around a nearby
        // vertical or horizontal waypoint.
        setDouble(
            "horizontal-speed",
            horizontalArrived
                ? requestedSpeed
                : entityControlSpeedForStep(
                    requestedSpeed,
                    Math.hypot(horizontalStep.x(), horizontalStep.z())
                )
        );
        setDouble(
            "vertical-speed",
            Math.abs(verticalVelocity) < 1.0e-9
                ? requestedVerticalSpeed
                : entityControlSpeedForStep(
                    requestedVerticalSpeed,
                    verticalVelocity
                )
        );
        vehicle.setVelocity(
            horizontalStep.x(),
            verticalVelocity,
            horizontalStep.z()
        );
    }

    static double entityControlSpeedForStep(
        double requestedBlocksPerSecond,
        double signedBlocksThisTick
    ) {
        if (!(requestedBlocksPerSecond > 0.0)
            || !Double.isFinite(requestedBlocksPerSecond)
            || !Double.isFinite(signedBlocksThisTick)) {
            throw new IllegalArgumentException(
                "BoatFly control-step speed is invalid."
            );
        }
        return Math.min(
            requestedBlocksPerSecond,
            Math.abs(signedBlocksThisTick) * 20.0
        );
    }

    public void setSpeeds(
        double travelBlocksPerSecond,
        double buildBlocksPerSecond
    ) {
        configuredTravelSpeed = Math.max(
            1.0,
            Math.min(20.0, travelBlocksPerSecond)
        );
        configuredBuildSpeed = Math.max(
            1.0,
            Math.min(BUILD_BLOCKS_PER_SECOND, buildBlocksPerSecond)
        );
    }

    public void setBuildSpeed(double blocksPerSecond) {
        setSpeeds(configuredTravelSpeed, blocksPerSecond);
    }

    public double travelBlocksPerSecond() {
        return configuredTravelSpeed;
    }

    public double buildBlocksPerSecond() {
        return configuredBuildSpeed;
    }

    /** Ensures the mounted boat's concrete entity type is controlled. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean include(Entity vehicle) {
        if (entityControl == null || vehicle == null) return false;
        Setting<?> setting = entityControl.settings.get("entities");
        if (setting == null || !(setting.get() instanceof Set<?> existing)) return false;
        if (existing.contains(vehicle.getType())) return true;
        java.util.HashSet copy = new java.util.HashSet(existing);
        copy.add(vehicle.getType());
        return setUnchecked(setting, copy);
    }

    public void stop() {
        clearInjectedKeys();
        if (mc.player != null && mc.player.getVehicle() != null) {
            Entity vehicle = mc.player.getVehicle();
            vehicle.setVelocity(0, 0, 0);
        }
    }

    private void clearInjectedKeys() {
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setLeftPressed(false);
        Utils.setRightPressed(false);
        Utils.setJumpPressed(false);
        Utils.setSprintPressed(false);
    }

    /** Releases injected movement keys without cancelling the user's velocity. */
    public void yieldToManualInput() {
        clearInjectedKeys();
        lastDriveMode = null;
    }

    public void release() {
        stop();
        if (snapshot == null || entityControl == null) {
            snapshot = null;
            entityControl = null;
            lastDriveMode = null;
            return;
        }

        Snapshot restore = snapshot;
        snapshot = null;
        for (Map.Entry<String, Object> entry : restore.values().entrySet()) {
            Setting<?> setting = entityControl.settings.get(entry.getKey());
            if (setting != null) setUnchecked(setting, entry.getValue());
        }
        if (!restore.active() && entityControl.isActive()) entityControl.disable();
        if (restore.active() && !entityControl.isActive()) entityControl.enable();
        entityControl = null;
        lastDriveMode = null;
    }

    public boolean acquired() {
        return snapshot != null;
    }

    public String failureReason() {
        return failureReason;
    }

    private void setDouble(String name, double value) {
        if (entityControl == null) return;
        Setting<?> setting = entityControl.settings.get(name);
        if (setting != null) setUnchecked(setting, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setUnchecked(Setting setting, Object value) {
        return setting.set(value);
    }
}
