package com.comicsquid.autoprt;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public class AutoPortalAddon extends MeteorAddon {
    private final AutoEnableListener autoEnableListener = new AutoEnableListener();
    private final TitleScreenAutoJoinListener titleScreenAutoJoinListener = new TitleScreenAutoJoinListener();

    @Override
    public void onInitialize() {
        Modules.get().add(new AutoPortalResume());
        MeteorClient.EVENT_BUS.subscribe(autoEnableListener);
        MeteorClient.EVENT_BUS.subscribe(titleScreenAutoJoinListener);
        MeteorClient.LOG.info("[AutoPortal] Loaded!");
    }

    @Override
    public String getPackage() {
        return "com.comicsquid.autoprt";
    }

    private static final class AutoEnableListener {
        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

            var mod = Modules.get().get("auto-portal-resume");
            if (!(mod instanceof AutoPortalResume resume)) return;

            resume.tryDeferredPrinterRestore();

            if (mod.isActive()) return;
            if (!resume.shouldAutoEnable()) return;

            if (resume.isPlayerNearConfiguredLoginOrSpawnPortal(MeteorClient.mc.player.getBlockPos())) {
                resume.enable();
            }
        }
    }

    private static final class TitleScreenAutoJoinListener {
        private static final String SERVER_NAME = "AutoJoin";

        private boolean hasAttemptedJoin = false;
        private int titleTicks = -1;
        private boolean lastWasTitle = false;

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (MeteorClient.mc == null) return;
            var mod = Modules.get().get("auto-portal-resume");
            if (!(mod instanceof AutoPortalResume resume)) return;
            if (!resume.isTitleAutoJoinEnabled()) return;

            Screen current = MeteorClient.mc.currentScreen;
            boolean onTitle = current instanceof TitleScreen;
            boolean connected = MeteorClient.mc.world != null;

            if (!onTitle || connected) {
                titleTicks = -1;
                lastWasTitle = onTitle;
                return;
            }

            if (!lastWasTitle) {
                hasAttemptedJoin = false;
                titleTicks = 0;
            } else if (titleTicks >= 0) {
                titleTicks++;
            }

            int delay = resume.getTitleAutoJoinDelayTicks();
            if (!hasAttemptedJoin && titleTicks >= delay) {
                hasAttemptedJoin = true;
                String host = resume.getTitleAutoJoinHost();
                if (host.isEmpty()) return;

                try {
                    ServerInfo serverInfo = new ServerInfo(SERVER_NAME, host, ServerInfo.ServerType.OTHER);
                    ConnectScreen.connect(
                        current,
                        MeteorClient.mc,
                        ServerAddress.parse(host),
                        serverInfo,
                        false,
                        null
                    );
                } catch (Exception ignored) {
                    // Invalid host formatting: skip this title session without crashing client.
                }
            }

            lastWasTitle = true;
        }
    }
}
