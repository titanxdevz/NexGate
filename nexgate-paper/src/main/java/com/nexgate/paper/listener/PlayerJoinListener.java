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

        // Check if proxy sent perform.login ahead of join event (race condition defense)
        boolean consumed = authManager.checkAndConsumeQueuedSession(player);
        if (!consumed) {
            authManager.deauthenticate(player);
            plugin.getSLF4JLogger().debug("Player {} joined backend, unauthenticated by default (defense-in-depth)", player.getName());
        } else {
            plugin.getSLF4JLogger().debug("Player {} joined backend, authenticated from queued proxy session", player.getName());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.deauthenticate(event.getPlayer());
    }
}
