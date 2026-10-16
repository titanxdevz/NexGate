package com.nexgate.paper.listener;

import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join and quit on Paper backend with race-condition safety.
 */
public class PlayerJoinListener implements Listener {
    private final NexGatePaperPlugin plugin;
    private final PaperAuthManager authManager;
    private final PaperConfigManager configManager;

    public PlayerJoinListener(NexGatePaperPlugin plugin, PaperAuthManager authManager, PaperConfigManager configManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Limbo void setup & vanilla join message suppression, or lobby spawn positioning
        if (configManager.isLimboCageEnabled()) {
            plugin.getLimboManager().setupLimboPlayer(player);
            if (configManager.isSuppressJoinQuitMessages()) {
                event.joinMessage(null);
            }
        } else if (configManager.isSpawnTeleportOnJoin()) {
            org.bukkit.Location spawn = plugin.getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
        }

        // 2. Check if proxy sent perform.login ahead of join event (race condition defense)
        boolean consumed = authManager.checkAndConsumeQueuedSession(player);
        if (!consumed) {
            authManager.deauthenticate(player);
            plugin.getSLF4JLogger().debug("Player {} joined backend, unauthenticated by default (defense-in-depth)", player.getName());

            // Query proxy immediately
            plugin.getMessageManager().sendCheckAuth(player);

            // Scheduled verification pulses (at 5 ticks and 15 ticks) in case proxy/network was transitioning
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !authManager.isAuthenticated(player)) {
                    plugin.getMessageManager().sendCheckAuth(player);
                }
            }, 5L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !authManager.isAuthenticated(player)) {
                    plugin.getMessageManager().sendCheckAuth(player);
                }
            }, 15L);
        } else {
            plugin.getSLF4JLogger().debug("Player {} joined backend, authenticated from queued proxy session", player.getName());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (configManager.isLimboCageEnabled() && configManager.isSuppressJoinQuitMessages()) {
            event.quitMessage(null);
        }
        authManager.deauthenticate(event.getPlayer());
    }
}
