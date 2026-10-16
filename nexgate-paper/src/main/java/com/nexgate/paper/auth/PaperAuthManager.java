package com.nexgate.paper.auth;

import com.nexgate.paper.config.PaperConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication and security state manager on Paper backend.
 * Enforces defense-in-depth visual effects, immobility, and player ghosting.
 */
public class PaperAuthManager {
    private final Plugin plugin;
    private final Logger logger;
    private final PaperConfigManager configManager;

    private final Map<UUID, PlayerSettings> authenticatedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, QueuedProxySession> proxySessionQueue = new ConcurrentHashMap<>();

    public PaperAuthManager(Plugin plugin, Logger logger, PaperConfigManager configManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.configManager = configManager;
    }

    public boolean isAuthenticated(Player player) {
        if (player == null) return false;
        return authenticatedPlayers.containsKey(player.getUniqueId());
    }

    public boolean isAuthenticated(UUID uuid) {
        if (uuid == null) return false;
        return authenticatedPlayers.containsKey(uuid);
    }

    public void authenticate(Player player, String messageColor, String messageStyle) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String color = messageColor != null ? messageColor : "WHITE";
        String style = messageStyle != null ? messageStyle : "DEFAULT";

        authenticatedPlayers.put(uuid, new PlayerSettings(color, style));
        proxySessionQueue.remove(uuid);

        // Remove potion effects and restore flight/gravity
        removeAuthRestrictions(player);

        // Make visible to other players
        showToEveryone(player);

        // If configured, teleport to lobby spawn area on login
        if (!configManager.isLimboCageEnabled() && configManager.isSpawnTeleportOnLogin()) {
            if (plugin instanceof com.nexgate.paper.NexGatePaperPlugin ngPlugin) {
                org.bukkit.Location spawn = ngPlugin.getSpawnLocation();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
        }

        logger.info("Player {} authenticated on Paper backend (color={})", player.getName(), color);
    }

    public void deauthenticate(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        authenticatedPlayers.remove(uuid);

        if (configManager.isStrictGating()) {
            applyAuthRestrictions(player);
            hideUnauthenticated(player);
        }
    }

    public void setPlayerSettings(UUID uuid, String color, String style) {
        if (uuid != null) {
            authenticatedPlayers.put(uuid, new PlayerSettings(
                    color != null ? color : "WHITE",
                    style != null ? style : "DEFAULT"
            ));
        }
    }

    public String getPlayerColor(UUID uuid) {
        PlayerSettings s = authenticatedPlayers.get(uuid);
        return s != null ? s.color : "WHITE";
    }

    public String getPlayerStyle(UUID uuid) {
        PlayerSettings s = authenticatedPlayers.get(uuid);
        return s != null ? s.style : "DEFAULT";
    }

    public void queueProxySession(UUID uuid, String color, String style) {
        if (uuid != null) {
            proxySessionQueue.put(uuid, new QueuedProxySession(color, style, System.currentTimeMillis()));
        }
    }

    public boolean checkAndConsumeQueuedSession(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        QueuedProxySession queued = proxySessionQueue.remove(uuid);
        if (queued != null && System.currentTimeMillis() - queued.timestamp <= 30000) {
            authenticate(player, queued.color, queued.style);
            return true;
        }
        return false;
    }

    public void applyAuthRestrictions(Player player) {
        if (player == null || !player.isOnline()) return;

        if (configManager.isLimboCageEnabled()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            return;
        }

        if (configManager.isApplyBlindness()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    false,
                    false
            ));
        } else {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        if (configManager.isApplySlowness()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    PotionEffect.INFINITE_DURATION,
                    255,
                    false,
                    false
            ));
        } else {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    public void removeAuthRestrictions(Player player) {
        if (player == null || !player.isOnline()) return;
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGravity(true);
        player.setInvulnerable(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
    }

    public void hideUnauthenticated(Player player) {
        if (!configManager.isHideUnauthenticated()) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hidePlayer(plugin, player);
            }
        }
    }

    public void showToEveryone(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
    }

    public static class PlayerSettings {
        public final String color;
        public final String style;

        public PlayerSettings(String color, String style) {
            this.color = color;
            this.style = style;
        }

        public String color() {
            return color;
        }

        public String style() {
            return style;
        }
    }

    private static class QueuedProxySession {
        final String color;
        final String style;
        final long timestamp;

        QueuedProxySession(String color, String style, long timestamp) {
            this.color = color;
            this.style = style;
            this.timestamp = timestamp;
        }
    }
}
