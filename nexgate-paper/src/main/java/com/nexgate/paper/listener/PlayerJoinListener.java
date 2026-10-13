package com.nexgate.paper.listener;

import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import com.nexgate.paper.NexGatePaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit events on Paper.
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
        // Start unauthenticated - will be authenticated via proxy message or direct login
        // Defense in depth: default to not authenticated
        authManager.deauthenticate(player);
        plugin.getLogger().debug("Player {} joined backend, unauthenticated by default", player.getName());
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.deauthenticate(event.getPlayer());
    }
}
```