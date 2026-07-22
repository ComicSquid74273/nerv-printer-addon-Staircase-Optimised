package com.comicsquid.autoprt;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Blocks;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class AutoPortalResume extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAuto = settings.createGroup("Auto enable");
    private final SettingGroup sgBaritone = settings.createGroup("Baritone");
    private final SettingGroup sgLoginPortal = settings.createGroup("Login portal zone");
    private final SettingGroup sgSpawnPortal = settings.createGroup("Spawn portal");
    private final SettingGroup sgPrinterResume = settings.createGroup("Carpet printer resume");
    private final SettingGroup sgTitleAutoJoin = settings.createGroup("Title auto join");

    private final Setting<Boolean> autoEnable = sgAuto.add(new BoolSetting.Builder()
        .name("auto-enable")
        .description("When off, the addon will not turn this module on for you. When on, it enables this module when you stand near the spawn disk or the login portal box (see toggles below).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoEnableSpawnPortalDisk = sgAuto.add(new BoolSetting.Builder()
        .name("auto-enable-near-spawn-disk")
        .description("Auto-enable when inside the horizontal spawn disk (spawn-disk-center-x/z and spawn-disk-radius) — the world hub / return portal area (often ~0, ~0).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoEnableLoginPortal = sgAuto.add(new BoolSetting.Builder()
        .name("auto-enable-near-login-portal")
        .description("Auto-enable when inside the login portal box (login-portal-x/y/z and login-portal-radius), e.g. -999 100 -999.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spawnPortalEnabled = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-portal-enabled")
        .description("In the spawn disk (hub): after login phrase (if required) and delay, Baritone to your configured waypoints / nether portal. Separate from the login portal at login-portal coordinates.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spawnPortalRequireLoginChat = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-portal-require-login-chat")
        .description("If true, spawn-portal Baritone only runs after login-chat-trigger matched. If false, it runs whenever you are in the spawn disk and the module is on.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> spawnPortalTicksAfterLoginChat = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-ticks-after-login-chat")
        .description("After login chat, wait this many ticks before spawn-portal Baritone. Ignored if no login happened this session.")
        .defaultValue(60)
        .min(0)
        .sliderMax(400)
        .build());

    private final Setting<String> spawnPortalDimension = sgSpawnPortal.add(new StringSetting.Builder()
        .name("spawn-portal-dimension")
        .description("Dimension for spawn-disk Baritone: overworld, end, nether, or any. Many servers put the return portal in the End.")
        .defaultValue("end")
        .build());

    private final Setting<Integer> spawnDiskCenterX = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-disk-center-x")
        .description("Horizontal disk center X for the spawn portal / hub area (not the login portal box). Default 0 matches world spawn.")
        .defaultValue(0)
        .build());

    private final Setting<Integer> spawnDiskCenterZ = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-disk-center-z")
        .description("Horizontal disk center Z for the spawn portal / hub area.")
        .defaultValue(0)
        .build());

    private final Setting<Integer> spawnDiskRadius = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-disk-radius")
        .description("Horizontal distance from spawn-disk center XZ. Must include where you stand at the hub; increase for large hubs.")
        .defaultValue(128)
        .min(1)
        .sliderMax(65536)
        .build());

    private final Setting<Boolean> spawnSkipIfOutsideDiskAfterLogin = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-skip-if-outside-disk-after-login")
        .description("If login latched but you are outside the spawn disk, skip spawn portal and clear login latch.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spawnRepeatWhenReenterDisk = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-repeat-when-reenter-disk")
        .description("If you leave the spawn disk and come back, allow another spawn-portal Baritone run.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> spawnPortalWaitAfterEnterTicks = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-wait-after-enter-ticks")
        .description("After you first enter the spawn disk, wait this many ticks before spawn-portal Baritone and printer latch. Set 0 to disable.")
        .defaultValue(50)
        .min(0)
        .sliderMax(200)
        .build());

    private final Setting<Boolean> spawnTwoStepRoute = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-two-step-route")
        .description("When spawn-portal-thisway is off: two gotos (spawn-waypoint then spawn-portal-target). If off, one goto to spawn-portal-target only.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> spawnWaypointX = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-waypoint-x")
        .description("Step 1 waypoint X before portal goto.")
        .defaultValue(7)
        .build());

    private final Setting<Integer> spawnWaypointY = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-waypoint-y")
        .description("Step 1 waypoint Y before portal goto.")
        .defaultValue(18)
        .build());

    private final Setting<Integer> spawnWaypointZ = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-waypoint-z")
        .description("Step 1 waypoint Z before portal goto.")
        .defaultValue(61)
        .build());

    private final Setting<Integer> spawnPortalTargetX = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-target-x")
        .description("Baritone target X for the nether portal block in the spawn/hub area.")
        .defaultValue(0)
        .build());

    private final Setting<Integer> spawnPortalTargetY = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-target-y")
        .description("Baritone target Y for the nether portal block.")
        .defaultValue(20)
        .build());

    private final Setting<Integer> spawnPortalTargetZ = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-target-z")
        .description("Baritone target Z for the nether portal block.")
        .defaultValue(-16)
        .build());

    private final Setting<Boolean> spawnPortalThiswayEnabled = sgSpawnPortal.add(new BoolSetting.Builder()
        .name("spawn-portal-thisway")
        .description("Spawn disk: use Baritone thisway + path in your look direction instead of goto waypoints.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> spawnPortalThiswayBlocks = sgSpawnPortal.add(new IntSetting.Builder()
        .name("spawn-portal-thisway-blocks")
        .description("Block count for spawn-portal-thisway when enabled.")
        .defaultValue(96)
        .min(8)
        .sliderMax(256)
        .build());

    private final Setting<Boolean> baritoneNoBreak = sgBaritone.add(new BoolSetting.Builder()
        .name("baritone-no-break")
        .description("Before goto, set Baritone allowBreak false so it will not mine blocks. Path may fail if no break-free route exists.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> baritoneRestoreAllowBreak = sgBaritone.add(new BoolSetting.Builder()
        .name("baritone-restore-allow-break")
        .description("After Baritone stop or when this module turns off, send allowBreak true if this module disabled breaking.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> baritoneSpawnStuckRecovery = sgBaritone.add(new BoolSetting.Builder()
        .name("baritone-stuck-recovery")
        .description("Only after a Baritone path was sent: if you stay on the same block long enough (see below), force stop then jump+walk recovery on the login-portal leg, or walk back 5 blocks on the spawn-portal leg, then resend goto. Does nothing before the path runs or during walk-into-portal.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> baritoneStuckAfterTicks = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-stuck-after-path-ticks")
        .description("Only check for stuck after this many ticks once a portal Baritone path was sent.")
        .defaultValue(100)
        .min(20)
        .sliderMax(400)
        .build());

    private final Setting<Integer> baritoneStuckSameBlockTicks = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-stuck-same-block-ticks")
        .description("If block position unchanged this long after the above delay, trigger jump+retry.")
        .defaultValue(80)
        .min(20)
        .sliderMax(300)
        .build());

    private final Setting<Integer> baritoneRecoveryMaxAttempts = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-stuck-max-retries")
        .description("Max jump+goto retries per portal attempt.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build());

    private final Setting<Integer> baritoneRecoveryJumpDelayTicks = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-recovery-jump-at-ticks")
        .description("End of the jump+walk window: while remaining ticks are from this value down, hold jump key (with forward on login-portal recovery).")
        .defaultValue(22)
        .min(1)
        .sliderMax(60)
        .build());

    private final Setting<Integer> baritoneRecoveryJumpHoldTicks = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-recovery-jump-hold-ticks")
        .description("How many ticks to hold jump together with forward during login-portal recovery. Uses real key presses so movement and jump overlap.")
        .defaultValue(12)
        .min(1)
        .sliderMax(40)
        .build());

    private final Setting<Integer> baritoneRecoveryCooldownTicks = sgBaritone.add(new IntSetting.Builder()
        .name("baritone-recovery-cooldown-ticks")
        .description("Ticks between stop and resending the Baritone portal command.")
        .defaultValue(35)
        .min(10)
        .sliderMax(100)
        .build());

    private final Setting<String> printerModuleId = sgGeneral.add(new StringSetting.Builder()
        .name("printer-module-id")
        .description("Meteor module id for Carpet Printer; empty = auto-detect by name.")
        .defaultValue("carpet-printer")
        .build());

    private final Setting<String> loginChatTrigger = sgGeneral.add(new StringSetting.Builder()
        .name("login-chat-trigger")
        .description("When chat contains this text, pause Carpet Printer again. Use a phrase from the server's login-done message so it does not match /login prompts. Empty = off.")
        .defaultValue("you are now logged in")
        .build());

    private final Setting<Boolean> resumeCarpetPrinter = sgPrinterResume.add(new BoolSetting.Builder()
        .name("resume-carpet-printer")
        .description("If on, pause Carpet Printer when it is on during portal automation and remember that state for possible base auto-start. If off, this addon never pauses Carpet Printer.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoStartPrinterAtBase = sgPrinterResume.add(new BoolSetting.Builder()
        .name("auto-start-printer-at-base")
        .description("If on, turns Carpet Printer back on inside the printer-restore zone when it was on before pause. If off, only pauses during the run — you start the printer yourself at base.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> titleAutoJoinEnabled = sgTitleAutoJoin.add(new BoolSetting.Builder()
        .name("title-auto-join-enabled")
        .description("If on, auto-join the configured server only when you are on TitleScreen.")
        .defaultValue(true)
        .build());

    private final Setting<String> titleAutoJoinHost = sgTitleAutoJoin.add(new StringSetting.Builder()
        .name("title-auto-join-host")
        .description("Server host used by title auto-join.")
        .defaultValue("alt.6b6t.org")
        .build());

    private final Setting<Integer> titleAutoJoinDelayTicks = sgTitleAutoJoin.add(new IntSetting.Builder()
        .name("title-auto-join-delay-ticks")
        .description("Delay in ticks before connecting from TitleScreen (20 ticks ~= 1 second).")
        .defaultValue(80)
        .min(0)
        .sliderMax(200)
        .build());

    private final Setting<Integer> printerRestoreCenterX = sgPrinterResume.add(new IntSetting.Builder()
        .name("printer-restore-x")
        .description("Center X of the resume zone. Set in-game to your base; defaults are placeholders only.")
        .defaultValue(0)
        .min(-60000000)
        .max(60000000)
        .sliderMax(60000000)
        .build());

    private final Setting<Integer> printerRestoreCenterY = sgPrinterResume.add(new IntSetting.Builder()
        .name("printer-restore-y")
        .description("Center Y of the resume zone.")
        .defaultValue(0)
        .min(-64)
        .max(320)
        .sliderMax(320)
        .build());

    private final Setting<Integer> printerRestoreCenterZ = sgPrinterResume.add(new IntSetting.Builder()
        .name("printer-restore-z")
        .description("Center Z of the resume zone. Set in-game; defaults are placeholders only.")
        .defaultValue(0)
        .min(-60000000)
        .max(60000000)
        .sliderMax(60000000)
        .build());

    private final Setting<Integer> printerRestoreRadius = sgPrinterResume.add(new IntSetting.Builder()
        .name("printer-restore-radius")
        .description("Chebyshev distance from center; the only place this addon may turn Carpet Printer back on (when auto-start at base is on).")
        .defaultValue(128)
        .min(1)
        .sliderMax(512)
        .build());

    private final Setting<String> printerRestoreDimension = sgPrinterResume.add(new StringSetting.Builder()
        .name("printer-restore-dimension")
        .description("Dimension for the resume zone: overworld, end, nether, or any.")
        .defaultValue("any")
        .build());

    private final Setting<Integer> printerRestoreDelayTicks = sgPrinterResume.add(new IntSetting.Builder()
        .name("printer-restore-delay-ticks")
        .description("Consecutive ticks you must stay inside the printer-restore zone before auto-starting Carpet Printer (~20 ticks ≈ 1 second at 20 TPS). 0 = instant. Leaving the zone resets the timer.")
        .defaultValue(80)
        .min(0)
        .sliderMax(400)
        .build());

    private final Setting<Integer> portalTargetZ = sgGeneral.add(new IntSetting.Builder()
        .name("portal-goto-z")
        .description("Overworld only: after Baritone to nether_portal in the login portal box, stop Baritone and hold forward when your block Z is >= this. Ignored in End/Nether so Baritone is not cancelled the same tick it starts (login-portal-only-overworld off).")
        .defaultValue(-989)
        .build());

    private final Setting<Integer> waitBeforeMoveTicks = sgGeneral.add(new IntSetting.Builder()
        .name("wait-before-move-ticks")
        .description("Delay before starting login-portal movement after entering the login portal box, in ticks.")
        .defaultValue(100)
        .min(0)
        .sliderMax(400)
        .build());

    private final Setting<Integer> postPortalDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("post-portal-delay-ticks")
        .description("Delay after entering nether portal before disabling the module, in ticks. Carpet Printer is only toggled back on in your printer-restore zone.")
        .defaultValue(240)
        .min(0)
        .sliderMax(600)
        .build());

    private final Setting<Integer> loginPortalX = sgLoginPortal.add(new IntSetting.Builder()
        .name("login-portal-x")
        .description("Login portal box center X (e.g. -999). Baritone nether_portal automation only runs inside this box.")
        .defaultValue(-999)
        .build());

    private final Setting<Integer> loginPortalY = sgLoginPortal.add(new IntSetting.Builder()
        .name("login-portal-y")
        .description("Login portal box center Y.")
        .defaultValue(100)
        .build());

    private final Setting<Integer> loginPortalZ = sgLoginPortal.add(new IntSetting.Builder()
        .name("login-portal-z")
        .description("Login portal box center Z (e.g. -999).")
        .defaultValue(-999)
        .build());

    private final Setting<Integer> loginPortalRadius = sgLoginPortal.add(new IntSetting.Builder()
        .name("login-portal-radius")
        .description("Chebyshev distance from login portal center in blocks.")
        .defaultValue(10)
        .min(1)
        .sliderMax(64)
        .build());

    private final Setting<Boolean> loginPortalOnlyOverworld = sgLoginPortal.add(new BoolSetting.Builder()
        .name("login-portal-only-overworld")
        .description("Only treat the login portal box as active in the Overworld. When off, the same X/Y/Z radius is used in any dimension which can break completion after nether portal travel; leave this on for normal use.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> loginPortalThiswayEnabled = sgLoginPortal.add(new BoolSetting.Builder()
        .name("login-portal-thisway")
        .description("Login portal box: use Baritone thisway + path in your look direction instead of goto nether_portal.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> loginPortalThiswayBlocks = sgLoginPortal.add(new IntSetting.Builder()
        .name("login-portal-thisway-blocks")
        .description("Block count for login-portal-thisway when enabled.")
        .defaultValue(96)
        .min(8)
        .sliderMax(256)
        .build());

    private int ticks = 0;

    private boolean printerPauseDone = false;
    private boolean warnedPrinterMissing = false;
    private boolean warnedOutsideSpawn = false;

    private boolean baritoneStarted = false;
    private boolean walkingIn = false;
    private boolean inPortal = false;

    /** Original Carpet Printer on-state before this module changes it. Null until first successful lookup. */
    @Nullable
    private Boolean rememberedPrinterWasOn = null;

    private boolean baritoneAllowBreakDisabledByUs = false;

    /** Set on login message; spawn-portal path may require this depending on settings. */
    private boolean spawnPortalLoginLatch = false;

    private boolean spawnPortalPathStarted = false;
    private boolean warnedSpawnPortalWaiting = false;

    /** Shown once when spawn-portal dimension matches but player is outside the configured spawn disk (no login path). */
    private boolean warnedSpawnPortalNotInDisk = false;

    /** -1 = no login this session; else ticks since login message for spawn-portal delay. */
    private int ticksSinceLoginMessage = -1;

    private boolean wasInLoginPortalZone = false;
    private boolean wasInSpawnDisk = false;

    /** Counts down after entering spawn disk; spawn-portal Baritone waits until 0. */
    private int spawnDiskStabilizeTicksRemaining = 0;

    /** Player stood in a nether portal block in the Overworld during this run. */
    private boolean sawNetherPortalInOverworld = false;

    /** After we finished handling base (turned printer on, or skipped because auto-start off). */
    private boolean carpetPrinterRestoredByUs = false;

    /** Consecutive ticks in printer-restore zone while waiting for delay; -1 = not accumulating. */
    private int printerRestoreTicksInZoneAccum = -1;

    private int recoveryCooldownTicks = 0;
    /** During login-portal stuck recovery, hold walk forward as well as jump. */
    private boolean recoveryHoldForward = false;
    /** Which Baritone leg to retry after recovery countdown (true = login portal, false = spawn disk). */
    private boolean recoveryIsLoginPortalLeg = false;
    /** Clamped jump-at for this recovery (never larger than cooldown length). */
    private int recoveryJumpAtEffective = 0;

    private int baritoneRecoveryAttempts = 0;
    private int stuckSameBlockTicks = 0;
    @Nullable
    private BlockPos lastStuckCheckPos = null;
    private int ticksSinceBaritonePortalCmd = 0;

    /** Set when login-portal nether sequence finishes normally; avoids treating that as an aborted run. */
    private boolean loginPortalSequenceFinished = false;

    /** Spawn portal only: after Baritone stall, walk back this many horizontal blocks then retry Baritone. */
    private static final double SPAWN_PORTAL_WALK_BACK_BLOCKS = 5.0;

    private boolean spawnPortalWalkBackActive = false;
    private double spawnPortalWalkBackStartX;
    private double spawnPortalWalkBackStartZ;

    public AutoPortalResume() {
        super(Categories.Movement, "auto-portal-resume", "Reconnect helper: login portal (-999 area) and spawn disk (~0,0) Baritone paths, printer pause/resume.");
    }

    /** Used by {@link AutoPortalAddon} to turn the module on near configured areas. */
    public boolean shouldAutoEnable() {
        return autoEnable.get();
    }

    public boolean isPlayerNearConfiguredLoginOrSpawnPortal(BlockPos pos) {
        if (!autoEnable.get()) return false;
        boolean atSpawnDisk = autoEnableSpawnPortalDisk.get()
            && spawnPortalDimensionMatches()
            && inSpawnDiskHorizontal(pos, spawnDiskCenterX.get(), spawnDiskCenterZ.get(), spawnDiskRadius.get());
        boolean dimOk = mc.world == null || !loginPortalOnlyOverworld.get() || mc.world.getRegistryKey().equals(World.OVERWORLD);
        boolean atLoginPortal = autoEnableLoginPortal.get()
            && dimOk
            && chebyshev(pos, loginPortalX.get(), loginPortalY.get(), loginPortalZ.get()) <= loginPortalRadius.get();
        return atSpawnDisk || atLoginPortal;
    }

    public boolean isTitleAutoJoinEnabled() {
        return titleAutoJoinEnabled.get();
    }

    public String getTitleAutoJoinHost() {
        return titleAutoJoinHost.get().trim();
    }

    public int getTitleAutoJoinDelayTicks() {
        return titleAutoJoinDelayTicks.get();
    }

    @Override
    public void onActivate() {
        ticks = 0;
        printerPauseDone = false;
        warnedPrinterMissing = false;
        warnedOutsideSpawn = false;
        baritoneStarted = false;
        walkingIn = false;
        inPortal = false;
        baritoneAllowBreakDisabledByUs = false;

        if (carpetPrinterRestoredByUs) {
            rememberedPrinterWasOn = null;
            carpetPrinterRestoredByUs = false;
        }

        spawnPortalLoginLatch = false;
        spawnPortalPathStarted = false;
        warnedSpawnPortalWaiting = false;
        warnedSpawnPortalNotInDisk = false;
        ticksSinceLoginMessage = -1;

        wasInLoginPortalZone = false;
        wasInSpawnDisk = false;

        spawnDiskStabilizeTicksRemaining = 0;

        sawNetherPortalInOverworld = false;

        recoveryCooldownTicks = 0;
        recoveryHoldForward = false;
        recoveryIsLoginPortalLeg = false;
        recoveryJumpAtEffective = 0;
        baritoneRecoveryAttempts = 0;
        stuckSameBlockTicks = 0;
        lastStuckCheckPos = null;
        ticksSinceBaritonePortalCmd = 0;
        loginPortalSequenceFinished = false;

        spawnPortalWalkBackActive = false;

        printerRestoreTicksInZoneAccum = -1;

        info("Enabled. Pauses Carpet Printer only while it is on; at base, turns it on only if auto-start-printer-at-base is on.");
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        recoveryHoldForward = false;
        recoveryIsLoginPortalLeg = false;
        recoveryJumpAtEffective = 0;
        restoreBaritoneAllowBreakIfNeeded();

        spawnPortalLoginLatch = false;
        spawnPortalPathStarted = false;
        spawnPortalWalkBackActive = false;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;

        String trigger = loginChatTrigger.get();
        if (trigger.isBlank()) return;

        String msg = event.getMessage().getString().toLowerCase(Locale.ROOT);
        if (!msg.contains(trigger.toLowerCase(Locale.ROOT))) return;

        warnedPrinterMissing = false;
        tryPauseCarpetPrinter(true);

        if (spawnPortalEnabled.get()) {
            spawnPortalLoginLatch = true;
            ticksSinceLoginMessage = 0;
            spawnPortalPathStarted = false;
            warnedSpawnPortalWaiting = false;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (ticksSinceLoginMessage >= 0) {
            ticksSinceLoginMessage++;
        }

        if (recoveryCooldownTicks > 0) {
            recoveryCooldownTicks--;

            if (recoveryCooldownTicks == 0) {
                mc.options.jumpKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
                if (recoveryHoldForward) {
                    mc.options.forwardKey.setPressed(false);
                    recoveryHoldForward = false;
                }
                boolean leg = recoveryIsLoginPortalLeg;
                recoveryIsLoginPortalLeg = false;
                recoveryJumpAtEffective = 0;
                if (runBaritonePortalPath(leg)) {
                    info("Baritone retry after jump+walk recovery.");
                    ticksSinceBaritonePortalCmd = 0;
                }
            }
        }

        tryPauseCarpetPrinter(false);

        if (spawnPortalWalkBackActive && mc.player != null) {
            double wdx = mc.player.getX() - spawnPortalWalkBackStartX;
            double wdz = mc.player.getZ() - spawnPortalWalkBackStartZ;
            if (Math.sqrt(wdx * wdx + wdz * wdz) >= SPAWN_PORTAL_WALK_BACK_BLOCKS) {
                mc.options.backKey.setPressed(false);
                spawnPortalWalkBackActive = false;
                if (runBaritonePortalPath(false)) {
                    info("Spawn portal: retry Baritone after walk-back.");
                    ticksSinceBaritonePortalCmd = 0;
                }
            } else {
                mc.options.backKey.setPressed(true);
                return;
            }
        }

        BlockPos pos = mc.player.getBlockPos();
        int lx = loginPortalX.get();
        int ly = loginPortalY.get();
        int lz = loginPortalZ.get();
        int lr = loginPortalRadius.get();

        boolean dimOverworld = mc.world.getRegistryKey().equals(World.OVERWORLD);
        boolean inLoginPortalZone = (!loginPortalOnlyOverworld.get() || dimOverworld)
            && chebyshev(pos, lx, ly, lz) <= lr;
        boolean inSpawnDiskNow = inSpawnDiskHorizontal(pos, spawnDiskCenterX.get(), spawnDiskCenterZ.get(), spawnDiskRadius.get());

        if (wasInSpawnDisk && !inSpawnDiskNow && spawnPortalPathStarted) {
            spawnPortalWalkBackActive = false;
            mc.options.backKey.setPressed(false);
            runBaritoneStop(false);
            restoreBaritoneAllowBreakIfNeeded();
            info("Left spawn disk; Baritone stopped. Carpet Printer is only toggled in your printer-restore zone.");
            spawnPortalPathStarted = false;
        }

        if (spawnRepeatWhenReenterDisk.get() && wasInSpawnDisk && !inSpawnDiskNow) {
            spawnPortalPathStarted = false;
        }

        boolean justEnteredSpawnDisk = inSpawnDiskNow && !wasInSpawnDisk;
        if (justEnteredSpawnDisk) {
            spawnDiskStabilizeTicksRemaining = spawnPortalWaitAfterEnterTicks.get();
        }
        if (!inSpawnDiskNow) {
            spawnDiskStabilizeTicksRemaining = 0;
        } else if (!justEnteredSpawnDisk && spawnDiskStabilizeTicksRemaining > 0) {
            spawnDiskStabilizeTicksRemaining--;
        }
        wasInSpawnDisk = inSpawnDiskNow;

        if (inSpawnDiskNow) {
            warnedSpawnPortalNotInDisk = false;
        }

        if (wasInLoginPortalZone && !inLoginPortalZone && !loginPortalSequenceFinished) {
            if (sawNetherPortalInOverworld && !dimOverworld) {
                finishLoginPortalSequence();
                return;
            }
            mc.options.forwardKey.setPressed(false);
            runBaritoneStop(false);
            restoreBaritoneAllowBreakIfNeeded();
            info("Left login portal box before portal finished. Carpet Printer is only toggled in your printer-restore zone.");
        }

        if (inLoginPortalZone && !wasInLoginPortalZone) {
            spawnPortalWalkBackActive = false;
            mc.options.backKey.setPressed(false);
            ticks = 0;
            baritoneStarted = false;
            walkingIn = false;
            inPortal = false;
            sawNetherPortalInOverworld = false;
            loginPortalSequenceFinished = false;
            baritoneRecoveryAttempts = 0;
            stuckSameBlockTicks = 0;
            lastStuckCheckPos = null;
            ticksSinceBaritonePortalCmd = 0;
            recoveryCooldownTicks = 0;
            recoveryHoldForward = false;
            recoveryIsLoginPortalLeg = false;
            recoveryJumpAtEffective = 0;
            mc.options.jumpKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
        }
        wasInLoginPortalZone = inLoginPortalZone;

        if (recoveryCooldownTicks > 0) {
            if (inLoginPortalZone) {
                ticks++;
            }
            return;
        }

        if (inLoginPortalZone) {
            if (warnedOutsideSpawn) {
                info("Inside login portal box, continuing portal automation.");
                warnedOutsideSpawn = false;
            }

            ticks++;

            int tgz = portalTargetZ.get();

            if (ticks < waitBeforeMoveTicks.get()) {
                return;
            }

            if (!baritoneStarted) {
                baritoneStarted = true;
                baritoneRecoveryAttempts = 0;
                if (!runBaritonePortalPath(true)) {
                    baritoneStarted = false;
                } else {
                    info(loginPortalThiswayEnabled.get() ? "Baritone thisway + path (login portal)." : "Baritone goto nether_portal.");
                    ticksSinceBaritonePortalCmd = 0;
                }
            } else {
                ticksSinceBaritonePortalCmd++;
                maybeSpawnStuckRecovery(pos, true);
            }

            if (dimOverworld && !walkingIn && pos.getZ() >= tgz) {
                runBaritoneStop();
                walkingIn = true;
                baritoneRecoveryAttempts = 0;
                stuckSameBlockTicks = 0;
            }

            if (walkingIn && !inPortal && dimOverworld) {
                mc.options.forwardKey.setPressed(true);
            }

            if (mc.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
                inPortal = true;
                sawNetherPortalInOverworld = true;
                mc.options.forwardKey.setPressed(false);
            }

            if (inPortal && ticks > waitBeforeMoveTicks.get() + postPortalDelayTicks.get()) {
                finishLoginPortalSequence();
            }

            return;
        }

        if (!warnedOutsideSpawn && !(spawnPortalEnabled.get() && (spawnPortalPathStarted || inSpawnDiskNow))) {
            if (!loginPortalOnlyOverworld.get() || dimOverworld) {
                warning(
                    "Outside login portal box center "
                        + lx + " " + ly + " " + lz + " radius " + lr
                        + " — your block pos "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ". Login portal steps skipped; printer pause still applies."
                );
                warnedOutsideSpawn = true;
            }
        }

        trySpawnPortalAutomation(pos, inSpawnDiskNow);

        if (spawnPortalPathStarted && inSpawnDiskNow && recoveryCooldownTicks == 0) {
            ticksSinceBaritonePortalCmd++;
            maybeSpawnStuckRecovery(pos, false);
        }
    }

    /**
     * Called from {@link com.comicsquid.autoprt.mixin.KeyboardInputMixin} after {@link net.minecraft.client.input.KeyboardInput#tick}
     * so forward + jump apply together before movement (KeyBinding alone loses forward to Baritone).
     */
    public static void applyRecoveryClientInput(Input input) {
        Module m = Modules.get().get("auto-portal-resume");
        if (!(m instanceof AutoPortalResume mod) || !mod.isActive()) return;
        if (mod.recoveryCooldownTicks <= 0) return;
        mod.applyRecoveryToInputInstance(input);
    }

    private void applyRecoveryToInputInstance(Input input) {
        boolean jump = recoveryJumpHeldThisTick();
        input.playerInput = new PlayerInput(
            recoveryHoldForward,
            false,
            false,
            false,
            jump,
            false,
            recoveryHoldForward
        );
    }

    private boolean recoveryJumpHeldThisTick() {
        if (recoveryCooldownTicks <= 0) return false;
        int jumpAt = recoveryJumpAtEffective > 0
            ? recoveryJumpAtEffective
            : Math.min(baritoneRecoveryJumpDelayTicks.get(), baritoneRecoveryCooldownTicks.get());
        int jumpHold = baritoneRecoveryJumpHoldTicks.get();
        int windowLow = Math.max(0, jumpAt - jumpHold);
        return recoveryCooldownTicks <= jumpAt && recoveryCooldownTicks > windowLow;
    }

    private boolean spawnPortalDimensionMatches() {
        return dimensionMatchesString(spawnPortalDimension.get());
    }

    private boolean printerRestoreDimensionMatches() {
        return dimensionMatchesString(printerRestoreDimension.get());
    }

    /** Same rules as spawn-portal-dimension / printer-restore-dimension. */
    private boolean dimensionMatchesString(String raw) {
        if (mc.world == null) return false;
        String d = raw.trim().toLowerCase(Locale.ROOT);
        if (d.isEmpty() || d.equals("any")) return true;
        var key = mc.world.getRegistryKey();
        if (d.equals("overworld")) return key.equals(World.OVERWORLD);
        if (d.equals("end")) return key.equals(World.END);
        if (d.equals("nether")) return key.equals(World.NETHER);
        return true;
    }

    private boolean inPrinterResumeBaseZone(BlockPos pos) {
        if (!printerRestoreDimensionMatches()) return false;
        long bx = printerRestoreCenterX.get();
        long by = printerRestoreCenterY.get();
        long bz = printerRestoreCenterZ.get();
        long r = printerRestoreRadius.get();
        long dx = Math.abs((long) pos.getX() - bx);
        long dy = Math.abs((long) pos.getY() - by);
        long dz = Math.abs((long) pos.getZ() - bz);
        long cheb = Math.max(dx, Math.max(dy, dz));
        return cheb <= r;
    }

    /** Called every tick from the addon so printer can resume at base after this module turns off. */
    public void tryDeferredPrinterRestore() {
        restoreCarpetPrinterIfWanted();
    }

    /** Printer restore, Baritone cleanup, chat line, then disable module. Idempotent. */
    private void finishLoginPortalSequence() {
        if (loginPortalSequenceFinished) return;

        loginPortalSequenceFinished = true;
        runBaritoneStop(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        sawNetherPortalInOverworld = false;
        inPortal = false;
        walkingIn = false;

        restoreBaritoneAllowBreakIfNeeded();
        if (Boolean.TRUE.equals(rememberedPrinterWasOn)) {
            if (autoStartPrinterAtBase.get()) {
                sendAddonChatInfo(
                    "Finished: login portal sequence complete. Carpet Printer will turn on after you stay in the printer-restore zone for the configured delay."
                );
            } else {
                sendAddonChatInfo("Finished: login portal sequence complete. Auto-start at base is off — start Carpet Printer yourself when ready.");
            }
        } else {
            sendAddonChatInfo("Finished: login portal sequence complete.");
        }
        toggle();
    }

    /** ChatUtils path so finish/restore lines show even when this module is off (deferred restore). */
    private void sendAddonChatInfo(String message) {
        ChatUtils.forceNextPrefixClass(getClass());
        ChatUtils.infoPrefix(title, message);
    }

    private void maybeSpawnStuckRecovery(BlockPos pos, boolean loginPortalLeg) {
        if (!baritoneSpawnStuckRecovery.get()) return;
        if (baritoneRecoveryAttempts >= baritoneRecoveryMaxAttempts.get()) return;
        if (spawnPortalWalkBackActive) return;
        if (loginPortalLeg) {
            if (!baritoneStarted || walkingIn || inPortal) return;
        } else {
            if (!spawnPortalPathStarted) return;
        }
        if (ticksSinceBaritonePortalCmd < baritoneStuckAfterTicks.get()) {
            stuckSameBlockTicks = 0;
            lastStuckCheckPos = null;
            return;
        }

        if (lastStuckCheckPos == null || !lastStuckCheckPos.equals(pos)) {
            lastStuckCheckPos = pos.toImmutable();
            stuckSameBlockTicks = 0;
        } else {
            stuckSameBlockTicks++;
        }

        if (stuckSameBlockTicks >= baritoneStuckSameBlockTicks.get()) {
            stuckSameBlockTicks = 0;
            lastStuckCheckPos = null;
            baritoneRecoveryAttempts++;
            runBaritoneStop(false, true);
            if (loginPortalLeg) {
                recoveryIsLoginPortalLeg = true;
                recoveryHoldForward = true;
                int cool = baritoneRecoveryCooldownTicks.get();
                recoveryJumpAtEffective = Math.min(baritoneRecoveryJumpDelayTicks.get(), cool);
                recoveryCooldownTicks = cool;
                info("Login portal: Baritone stalled — holding forward + jump keys, then retry Baritone.");
            } else if (mc.player != null) {
                spawnPortalWalkBackStartX = mc.player.getX();
                spawnPortalWalkBackStartZ = mc.player.getZ();
                spawnPortalWalkBackActive = true;
                info("Spawn portal: Baritone stalled — walking back 5 blocks, then retry Baritone.");
            }
        }
    }

    private void trySpawnPortalAutomation(BlockPos pos, boolean inSpawnDiskNow) {
        if (!spawnPortalEnabled.get() || spawnPortalPathStarted) return;
        if (spawnPortalRequireLoginChat.get() && !spawnPortalLoginLatch) return;
        if (!spawnPortalLoginDelaySatisfied()) return;
        if (!spawnPortalDimensionMatches()) {
            return;
        }

        int cx = spawnDiskCenterX.get();
        int cz = spawnDiskCenterZ.get();
        int rad = spawnDiskRadius.get();

        if (!inSpawnDiskNow) {
            long dx = (long) pos.getX() - cx;
            long dz = (long) pos.getZ() - cz;
            long r2 = (long) rad * rad;
            if (spawnPortalLoginLatch && spawnSkipIfOutsideDiskAfterLogin.get() && dx * dx + dz * dz > r2) {
                spawnPortalLoginLatch = false;
                ticksSinceLoginMessage = -1;
                warnedSpawnPortalWaiting = false;
                info(
                    "Spawn portal skipped: outside "
                        + rad
                        + "-block spawn disk around XZ "
                        + cx + " " + cz
                        + " (pos "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ")."
                );
                return;
            }

            if (spawnPortalLoginLatch && !spawnSkipIfOutsideDiskAfterLogin.get() && !warnedSpawnPortalWaiting) {
                warning(
                    "Spawn portal: waiting until within "
                        + rad
                        + " blocks horizontal of spawn disk XZ "
                        + cx + " " + cz
                        + ". Your block pos "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + "."
                );
                warnedSpawnPortalWaiting = true;
            }
            if (!spawnPortalLoginLatch && !warnedSpawnPortalNotInDisk) {
                warnedSpawnPortalNotInDisk = true;
                warning(
                    "Spawn portal: you are outside the configured spawn disk (center XZ "
                        + cx + " " + cz + ", radius " + rad
                        + "). Your block pos "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ". Set spawn-disk-center-x/z and spawn-disk-radius to cover your hub (often ~0, ~0)."
                );
            }
            return;
        }

        if (spawnDiskStabilizeTicksRemaining > 0) return;

        warnedSpawnPortalWaiting = false;

        if (runBaritonePortalPath(false)) {
            spawnPortalPathStarted = true;
            spawnPortalLoginLatch = false;
            ticksSinceLoginMessage = -1;
            baritoneRecoveryAttempts = 0;
            stuckSameBlockTicks = 0;
            lastStuckCheckPos = null;
            ticksSinceBaritonePortalCmd = 0;
            if (spawnPortalThiswayEnabled.get()) {
                info("Spawn portal: Baritone thisway + path.");
            } else {
                info(spawnTwoStepRoute.get() ? "Spawn portal: two-step route to portal." : "Spawn portal: goto portal.");
            }
        }
    }

    private boolean spawnPortalLoginDelaySatisfied() {
        if (ticksSinceLoginMessage < 0) {
            return true;
        }
        return ticksSinceLoginMessage >= spawnPortalTicksAfterLoginChat.get();
    }

    /**
     * Pauses Carpet Printer only when it is actually on (state change). Tick path runs once until done;
     * login-chat path can pause again if the printer was turned back on.
     */
    private void tryPauseCarpetPrinter(boolean fromChat) {
        if (!fromChat && printerPauseDone) return;

        if (!resumeCarpetPrinter.get()) {
            printerPauseDone = true;
            if (rememberedPrinterWasOn == null) {
                rememberedPrinterWasOn = false;
            }
            return;
        }

        Module printer = findCarpetPrinter();

        if (printer == null) {
            if (!warnedPrinterMissing) {
                warning(
                    "Could not find Carpet Printer. Set printer-module-id to its Meteor .name or load the addon."
                );
                warnedPrinterMissing = true;
            }
            return;
        }

        warnedPrinterMissing = false;

        if (!printer.isActive()) {
            if (rememberedPrinterWasOn == null) {
                rememberedPrinterWasOn = false;
            }
            if (fromChat) {
                info("Login phrase matched — Carpet Printer was already off.");
            }
            printerPauseDone = true;
            return;
        }

        rememberedPrinterWasOn = true;
        printer.toggle();
        if (fromChat) {
            info("Login phrase matched — paused Carpet Printer.");
        } else {
            info("Paused Carpet Printer.");
        }
        printerPauseDone = true;
    }

    /** Only turns Carpet Printer on at the configured base zone when auto-start is enabled. */
    private void restoreCarpetPrinterIfWanted() {
        if (carpetPrinterRestoredByUs) {
            printerRestoreTicksInZoneAccum = -1;
            return;
        }
        if (!Boolean.TRUE.equals(rememberedPrinterWasOn)) {
            printerRestoreTicksInZoneAccum = -1;
            return;
        }
        if (mc.player == null || mc.world == null) {
            printerRestoreTicksInZoneAccum = -1;
            return;
        }
        if (!inPrinterResumeBaseZone(mc.player.getBlockPos())) {
            printerRestoreTicksInZoneAccum = -1;
            return;
        }
        if (!autoStartPrinterAtBase.get()) {
            printerRestoreTicksInZoneAccum = -1;
            carpetPrinterRestoredByUs = true;
            sendAddonChatInfo("At base: auto-start printer at base is off — not turning Carpet Printer on.");
            return;
        }

        int delay = printerRestoreDelayTicks.get();
        if (delay <= 0) {
            printerRestoreTicksInZoneAccum = -1;
            doRestoreCarpetPrinterToggle();
            return;
        }

        if (printerRestoreTicksInZoneAccum < 0) {
            printerRestoreTicksInZoneAccum = 0;
        }
        printerRestoreTicksInZoneAccum++;
        if (printerRestoreTicksInZoneAccum < delay) {
            return;
        }
        printerRestoreTicksInZoneAccum = -1;
        doRestoreCarpetPrinterToggle();
    }

    private void doRestoreCarpetPrinterToggle() {
        Module printer = findCarpetPrinter();
        if (printer == null) {
            warning("Carpet Printer module not found; cannot restore. Set printer-module-id.");
            return;
        }
        if (printer.isActive()) {
            carpetPrinterRestoredByUs = true;
            sendAddonChatInfo("Carpet Printer was already on — marked done.");
            return;
        }
        printer.toggle();
        carpetPrinterRestoredByUs = true;
        sendAddonChatInfo("Carpet Printer turned back on.");
    }

    /**
     * Per-leg: login portal uses {@code goto nether_portal} or thisway; spawn portal uses configured gotos or thisway.
     */
    private boolean runBaritonePortalPath(boolean loginPortalLeg) {
        if (!BaritoneUtils.IS_AVAILABLE) {
            warning("Baritone is not available — install Baritone / Meteor build with Baritone, or move to the portal manually.");
            return false;
        }

        String prefix = BaritoneUtils.getPrefix();
        if (prefix == null || prefix.isEmpty()) {
            warning("Baritone prefix is empty — check Baritone settings.");
            return false;
        }

        if (baritoneNoBreak.get()) {
            mc.player.networkHandler.sendChatMessage(prefix + "allowBreak false");
            if (baritoneRestoreAllowBreak.get()) {
                baritoneAllowBreakDisabledByUs = true;
            }
        }

        if (loginPortalLeg) {
            if (loginPortalThiswayEnabled.get()) {
                sendBaritoneThiswayThenPath(prefix, loginPortalThiswayBlocks.get());
            } else {
                mc.player.networkHandler.sendChatMessage(prefix + "goto nether_portal");
            }
        } else if (spawnPortalThiswayEnabled.get()) {
            sendBaritoneThiswayThenPath(prefix, spawnPortalThiswayBlocks.get());
        } else if (spawnTwoStepRoute.get()) {
            int wx = spawnWaypointX.get();
            int wy = spawnWaypointY.get();
            int wz = spawnWaypointZ.get();
            int px = spawnPortalTargetX.get();
            int py = spawnPortalTargetY.get();
            int pz = spawnPortalTargetZ.get();

            mc.player.networkHandler.sendChatMessage(prefix + "goto " + wx + " " + wy + " " + wz);
            mc.player.networkHandler.sendChatMessage(prefix + "goto " + px + " " + py + " " + pz);
        } else {
            int px = spawnPortalTargetX.get();
            int py = spawnPortalTargetY.get();
            int pz = spawnPortalTargetZ.get();
            mc.player.networkHandler.sendChatMessage(prefix + "goto " + px + " " + py + " " + pz);
        }
        return true;
    }

    private void sendBaritoneThiswayThenPath(String prefix, int blocks) {
        mc.player.networkHandler.sendChatMessage(prefix + "thisway " + blocks);
        mc.player.networkHandler.sendChatMessage(prefix + "path");
    }

    private void runBaritoneStop() {
        runBaritoneStop(true, false);
    }

    private void runBaritoneStop(boolean restoreAllowBreakAfter) {
        runBaritoneStop(restoreAllowBreakAfter, false);
    }

    private void runBaritoneStop(boolean restoreAllowBreakAfter, boolean forceCancel) {
        if (!BaritoneUtils.IS_AVAILABLE) return;

        String prefix = BaritoneUtils.getPrefix();
        if (prefix == null || prefix.isEmpty()) return;

        mc.player.networkHandler.sendChatMessage(prefix + "stop");
        if (forceCancel) {
            mc.player.networkHandler.sendChatMessage(prefix + "forcecancel");
        }
        if (restoreAllowBreakAfter) {
            restoreBaritoneAllowBreakIfNeeded();
        }
    }

    private void restoreBaritoneAllowBreakIfNeeded() {
        if (!baritoneAllowBreakDisabledByUs) return;
        if (!baritoneRestoreAllowBreak.get()) {
            baritoneAllowBreakDisabledByUs = false;
            return;
        }
        if (!BaritoneUtils.IS_AVAILABLE) {
            baritoneAllowBreakDisabledByUs = false;
            return;
        }
        String prefix = BaritoneUtils.getPrefix();
        if (prefix == null || prefix.isEmpty() || mc.player == null) {
            baritoneAllowBreakDisabledByUs = false;
            return;
        }

        mc.player.networkHandler.sendChatMessage(prefix + "allowBreak true");
        baritoneAllowBreakDisabledByUs = false;
    }

    private static boolean inSpawnDiskHorizontal(BlockPos pos, int cx, int cz, int radius) {
        long dx = (long) pos.getX() - cx;
        long dz = (long) pos.getZ() - cz;
        long r2 = (long) radius * radius;
        return dx * dx + dz * dz <= r2;
    }

    private static int chebyshev(BlockPos pos, int bx, int by, int bz) {
        int dx = Math.abs(pos.getX() - bx);
        int dy = Math.abs(pos.getY() - by);
        int dz = Math.abs(pos.getZ() - bz);
        return Math.max(dx, Math.max(dy, dz));
    }

    @Nullable
    private Module findCarpetPrinter() {
        String id = printerModuleId.get().trim();
        if (!id.isEmpty()) {
            Module m = Modules.get().get(id);
            if (m != null) return m;
        }

        for (Module mod : Modules.get().getAll()) {
            String n = mod.name.toLowerCase(Locale.ROOT);
            if (n.contains("carpet") && n.contains("printer")) return mod;

            String t = mod.title.toLowerCase(Locale.ROOT);
            if (t.contains("carpet") && t.contains("printer")) return mod;
        }

        return null;
    }
}