package com.nexgate.velocity.auth;

import com.nexgate.model.UserAuth;
import com.nexgate.velocity.config.ConfigManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise Auth & Session state manager for Velocity proxy.
 * Controls player authentication state, TOTP challenges, interactive BossBars, titles, and sound feedback.
 */
public class AuthManager {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private Object pluginInstance; // reference for scheduler

    // Active authenticated sessions: UUID -> SessionData
    private final Map<UUID, UserSessionData> authenticatedSessions = new ConcurrentHashMap<>();

    // Unauthenticated players undergoing password auth flow: UUID -> PendingAuth
    private final Map<UUID, PendingAuth> pendingAuths = new ConcurrentHashMap<>();

    // Players who passed password but need 2FA verification: UUID -> PendingTotpAuth
    private final Map<UUID, PendingTotpAuth> pendingTotpAuths = new ConcurrentHashMap<>();

    public AuthManager(ProxyServer server, Logger logger, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
    }

    public void setPluginInstance(Object pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public boolean isAuthenticated(Player player) {
        if (player == null) return false;
        return isAuthenticated(player.getUniqueId());
    }

    public boolean isAuthenticated(UUID uuid) {
        if (uuid == null) return false;
        UserSessionData session = authenticatedSessions.get(uuid);
        if (session == null) return false;
        if (System.currentTimeMillis() > session.expiry) {
            authenticatedSessions.remove(uuid);
            return false;
        }
        return true;
    }

    public boolean isPendingTotp(UUID uuid) {
        if (uuid == null) return false;
        return pendingTotpAuths.containsKey(uuid);
    }

    public Optional<PendingTotpAuth> getPendingTotp(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(pendingTotpAuths.get(uuid));
    }

    public Optional<UserSessionData> getSession(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(authenticatedSessions.get(uuid));
    }

    public void authenticate(Player player, UserAuth user, boolean premium) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        long expiry = System.currentTimeMillis() +
                TimeUnit.MINUTES.toMillis(configManager.getSessionTimeoutMinutes());

        String color = user != null ? user.getMessageColor() : "WHITE";
        String style = user != null ? user.getMessageStyle() : "DEFAULT";

        UserSessionData session = new UserSessionData(
                player.getUsername(),
                uuid,
                System.currentTimeMillis(),
                expiry,
                premium,
                color,
                style
        );
        authenticatedSessions.put(uuid, session);

        // Cancel pending auth UI (bossbar, timers, etc.)
        stopAuthFlow(player);
        stopTotpFlow(player);

        // Play success chime
        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 1.0f));
        }

        // Show welcome title
        if (configManager.isShowTitle()) {
            Component mainTitle = MINI_MESSAGE.deserialize("<bold><gradient:#00C9FF:#92FE9D>Authenticated</gradient></bold>");
            Component subtitle = MINI_MESSAGE.deserialize("<gray>Welcome, <white>" + player.getUsername() + "</white>!</gray>");
            player.showTitle(Title.title(mainTitle, subtitle,
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }

        logger.info("Player {} ({}) authenticated successfully (premium={})", player.getUsername(), uuid, premium);
    }

    public void updatePlayerSettings(UUID uuid, String color, String style) {
        UserSessionData existing = authenticatedSessions.get(uuid);
        if (existing != null) {
            authenticatedSessions.put(uuid, new UserSessionData(
                    existing.username,
                    existing.uuid,
                    existing.loginTime,
                    existing.expiry,
                    existing.premium,
                    color,
                    style
            ));
        }
    }

    public void logout(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        authenticatedSessions.remove(uuid);
        stopAuthFlow(player);
        stopTotpFlow(player);
        logger.debug("Player {} logged out", player.getUsername());
    }

    public void startAuthFlow(Player player, boolean isRegistered) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        stopAuthFlow(player);
        stopTotpFlow(player);

        long maxSeconds = configManager.getLoginTimeoutSeconds();
        BossBar bossBar = null;

        if (configManager.isShowBossBar()) {
            bossBar = BossBar.bossBar(
                    MINI_MESSAGE.deserialize("<gradient:#00C9FF:#92FE9D>🔐 " + (isRegistered ? "Please /login" : "Please /register") + "</gradient> <gray>(" + maxSeconds + "s)</gray>"),
                    1.0f,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.PROGRESS
            );
            player.showBossBar(bossBar);
        }

        PendingAuth pending = new PendingAuth(player, isRegistered, System.currentTimeMillis(), maxSeconds, bossBar);
        pendingAuths.put(uuid, pending);

        if (pluginInstance != null) {
            ScheduledTask task = server.getScheduler().buildTask(pluginInstance, () -> {
                tickAuthFlow(uuid);
            }).repeat(1, TimeUnit.SECONDS).schedule();
            pending.scheduledTask = task;
        }

        sendAuthPrompt(player, isRegistered);
    }

    public void startTotpChallenge(Player player, UserAuth user) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        stopAuthFlow(player);
        stopTotpFlow(player);

        long maxSeconds = configManager.getLoginTimeoutSeconds();
        BossBar bossBar = null;

        if (configManager.isShowBossBar()) {
            bossBar = BossBar.bossBar(
                    MINI_MESSAGE.deserialize("<gradient:#FF512F:#DD2476>🛡 2FA Required: /2fa verify</gradient> <gray>(" + maxSeconds + "s)</gray>"),
                    1.0f,
                    BossBar.Color.RED,
                    BossBar.Overlay.PROGRESS
            );
            player.showBossBar(bossBar);
        }

        PendingTotpAuth pending = new PendingTotpAuth(player, user, System.currentTimeMillis(), maxSeconds, bossBar);
        pendingTotpAuths.put(uuid, pending);

        if (pluginInstance != null) {
            ScheduledTask task = server.getScheduler().buildTask(pluginInstance, () -> {
                tickTotpFlow(uuid);
            }).repeat(1, TimeUnit.SECONDS).schedule();
            pending.scheduledTask = task;
        }

        sendTotpPrompt(player);
    }

    public void sendAuthPrompt(Player player, boolean isRegistered) {
        if (player == null) return;

        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 0.8f, 1.2f));
        }

        Component banner = MINI_MESSAGE.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                         <bold><gradient:#00C9FF:#92FE9D>NexGate Security</gradient></bold>
            """);

        Component prompt;
        if (isRegistered) {
            prompt = MINI_MESSAGE.deserialize(
                    "  <gray>Account registered! Please sign in:</gray>\n" +
                    "  <click:suggest_command:'/login '><hover:show_text:'<green>Click to suggest /login command'><gradient:#00F260:#0575E6><bold>[ 🔑 CLICK TO LOGIN ]</bold></gradient></hover></click> <dark_gray>or use <aqua>/login <password></aqua></dark_gray>\n"
            );
        } else {
            prompt = MINI_MESSAGE.deserialize(
                    "  <gray>New player detected! Create a password:</gray>\n" +
                    "  <click:suggest_command:'/register '><hover:show_text:'<green>Click to suggest /register command'><gradient:#00C9FF:#92FE9D><bold>[ 📝 CLICK TO REGISTER ]</bold></gradient></hover></click> <dark_gray>or use <aqua>/register <pass> <confirm></aqua></dark_gray>\n"
            );
        }

        Component footer = MINI_MESSAGE.deserialize(
                "<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"
        );

        player.sendMessage(banner.append(prompt).append(footer));

        if (configManager.isShowTitle()) {
            Component mainTitle = MINI_MESSAGE.deserialize("<bold><gradient:#00C9FF:#92FE9D>NexGate</gradient></bold>");
            Component subTitle = MINI_MESSAGE.deserialize(isRegistered ?
                    "<yellow>Use <gradient:#00F260:#0575E6><bold>/login <password></bold></gradient></yellow>" :
                    "<yellow>Use <gradient:#00C9FF:#92FE9D><bold>/register <pass> <pass></bold></gradient></yellow>");
            player.showTitle(Title.title(mainTitle, subTitle,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
        }
    }

    public void sendTotpPrompt(Player player) {
        if (player == null) return;

        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.MASTER, 1.0f, 1.0f));
        }

        Component banner = MINI_MESSAGE.deserialize("""
            <gradient:#FF512F:#DD2476>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                         <bold><gradient:#FF512F:#DD2476>Two-Factor Authentication</gradient></bold>
            """);

        Component prompt = MINI_MESSAGE.deserialize(
                "  <gray>Two-factor authentication is active on this account:</gray>\n" +
                "  <click:suggest_command:'/2fa verify '><hover:show_text:'<green>Click to enter 6-digit TOTP code'><gradient:#00F260:#0575E6><bold>[ 🛡 CLICK TO VERIFY 2FA ]</bold></gradient></hover></click> <dark_gray>or use <aqua>/2fa verify <code></aqua></dark_gray>\n" +
                "  <dark_gray>Lost your authenticator? Use </dark_gray><yellow>/2fa recovery <backup-code></yellow>\n"
        );

        Component footer = MINI_MESSAGE.deserialize(
                "<gradient:#FF512F:#DD2476>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"
        );

        player.sendMessage(banner.append(prompt).append(footer));

        if (configManager.isShowTitle()) {
            Component mainTitle = MINI_MESSAGE.deserialize("<bold><gradient:#FF512F:#DD2476>2FA Verification</gradient></bold>");
            Component subTitle = MINI_MESSAGE.deserialize("<yellow>Use <gradient:#00F260:#0575E6><bold>/2fa verify <code></bold></gradient></yellow>");
            player.showTitle(Title.title(mainTitle, subTitle,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
        }
    }

    private void tickAuthFlow(UUID uuid) {
        PendingAuth pending = pendingAuths.get(uuid);
        if (pending == null) return;

        Player player = pending.player;
        if (!player.isActive()) {
            stopAuthFlow(player);
            return;
        }

        long elapsedSeconds = (System.currentTimeMillis() - pending.startTime) / 1000;
        long remaining = pending.maxSeconds - elapsedSeconds;

        if (remaining <= 0) {
            stopAuthFlow(player);
            player.disconnect(MINI_MESSAGE.deserialize(configManager.getConfig().messages.authTimeout));
            return;
        }

        if (pending.bossBar != null) {
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) pending.maxSeconds));
            pending.bossBar.progress(progress);

            BossBar.Color color = BossBar.Color.GREEN;
            if (remaining <= 15) {
                color = BossBar.Color.RED;
            } else if (remaining <= 30) {
                color = BossBar.Color.YELLOW;
            }
            pending.bossBar.color(color);

            String promptText = pending.isRegistered ? "Please /login" : "Please /register";
            pending.bossBar.name(MINI_MESSAGE.deserialize(
                    "<gradient:#00C9FF:#92FE9D>🔐 " + promptText + "</gradient> <gray>(" + remaining + "s remaining)</gray>"
            ));
        }

        player.sendActionBar(MINI_MESSAGE.deserialize(
                "<red>⚠ You must authenticate to interact! <gray>(" + remaining + "s left)</gray></red>"
        ));

        if (elapsedSeconds > 0 && elapsedSeconds % 15 == 0) {
            sendAuthPrompt(player, pending.isRegistered);
        }
    }

    private void tickTotpFlow(UUID uuid) {
        PendingTotpAuth pending = pendingTotpAuths.get(uuid);
        if (pending == null) return;

        Player player = pending.player;
        if (!player.isActive()) {
            stopTotpFlow(player);
            return;
        }

        long elapsedSeconds = (System.currentTimeMillis() - pending.startTime) / 1000;
        long remaining = pending.maxSeconds - elapsedSeconds;

        if (remaining <= 0) {
            stopTotpFlow(player);
            player.disconnect(MINI_MESSAGE.deserialize(configManager.getConfig().messages.authTimeout));
            return;
        }

        if (pending.bossBar != null) {
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) pending.maxSeconds));
            pending.bossBar.progress(progress);
            pending.bossBar.name(MINI_MESSAGE.deserialize(
                    "<gradient:#FF512F:#DD2476>🛡 Enter 2FA: /2fa verify</gradient> <gray>(" + remaining + "s remaining)</gray>"
            ));
        }

        player.sendActionBar(MINI_MESSAGE.deserialize(
                "<red>⚠ 2FA code required! <gray>(" + remaining + "s left)</gray></red>"
        ));

        if (elapsedSeconds > 0 && elapsedSeconds % 15 == 0) {
            sendTotpPrompt(player);
        }
    }

    public void stopAuthFlow(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PendingAuth pending = pendingAuths.remove(uuid);
        if (pending != null) {
            if (pending.scheduledTask != null) {
                pending.scheduledTask.cancel();
            }
            if (pending.bossBar != null) {
                player.hideBossBar(pending.bossBar);
            }
        }
    }

    public void stopTotpFlow(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PendingTotpAuth pending = pendingTotpAuths.remove(uuid);
        if (pending != null) {
            if (pending.scheduledTask != null) {
                pending.scheduledTask.cancel();
            }
            if (pending.bossBar != null) {
                player.hideBossBar(pending.bossBar);
            }
        }
    }

    public Set<UUID> getAuthenticatedUuids() {
        return authenticatedSessions.keySet();
    }

    public static class UserSessionData {
        public final String username;
        public final UUID uuid;
        public final long loginTime;
        public final long expiry;
        public final boolean premium;
        public final String messageColor;
        public final String messageStyle;

        public UserSessionData(String username, UUID uuid, long loginTime, long expiry,
                               boolean premium, String messageColor, String messageStyle) {
            this.username = username;
            this.uuid = uuid;
            this.loginTime = loginTime;
            this.expiry = expiry;
            this.premium = premium;
            this.messageColor = messageColor;
            this.messageStyle = messageStyle;
        }

        public String username() {
            return username;
        }

        public UUID uuid() {
            return uuid;
        }

        public long loginTime() {
            return loginTime;
        }

        public long expiry() {
            return expiry;
        }

        public boolean premium() {
            return premium;
        }

        public String messageColor() {
            return messageColor;
        }

        public String messageStyle() {
            return messageStyle;
        }
    }

    private static class PendingAuth {
        final Player player;
        final boolean isRegistered;
        final long startTime;
        final long maxSeconds;
        final BossBar bossBar;
        ScheduledTask scheduledTask;

        PendingAuth(Player player, boolean isRegistered, long startTime, long maxSeconds, BossBar bossBar) {
            this.player = player;
            this.isRegistered = isRegistered;
            this.startTime = startTime;
            this.maxSeconds = maxSeconds;
            this.bossBar = bossBar;
        }
    }

    public static class PendingTotpAuth {
        public final Player player;
        public final UserAuth user;
        public final long startTime;
        public final long maxSeconds;
        public final BossBar bossBar;
        public ScheduledTask scheduledTask;
        public int attempts = 0;

        PendingTotpAuth(Player player, UserAuth user, long startTime, long maxSeconds, BossBar bossBar) {
            this.player = player;
            this.user = user;
            this.startTime = startTime;
            this.maxSeconds = maxSeconds;
            this.bossBar = bossBar;
        }
    }
}
