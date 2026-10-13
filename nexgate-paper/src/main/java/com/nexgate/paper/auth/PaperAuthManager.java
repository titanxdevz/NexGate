package com.nexgate.paper.auth;

import com.nexgate.paper.config.PaperConfigManager;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth manager for Paper backend (defense-in-depth).
 */
public class PaperAuthManager {
    private final Logger logger;
    private final PaperConfigManager configManager;
    private final Map<UUID, Boolean> authenticated = new ConcurrentHashMap<>();
    private final Map<UUID, Long> proxySessionTimestamps = new ConcurrentHashMap<>();
    
    public PaperAuthManager(Logger logger, PaperConfigManager configManager) {
        this.logger = logger;
        this.configManager = configManager;
    }
    
    public boolean isAuthenticated(Player player) {
        if (player == null) return false;
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }
    
    public void authenticate(Player player) {
        if (player != null) {
            authenticated.put(player.getUniqueId(), true);
            logger.debug("Authenticated player {}", player.getName());
        }
    }
    
    public void deauthenticate(Player player) {
        if (player != null) {
            authenticated.remove(player.getUniqueId());
        }
    }
    
    public void queueProxySession(UUID uuid) {
        if (uuid != null) {
            proxySessionTimestamps.put(uuid, System.currentTimeMillis());
        }
    }
    
    public boolean hasRecentProxySession(UUID uuid) {
        if (uuid == null) return false;
        Long ts = proxySessionTimestamps.get(uuid);
        if (ts == null) return false;
        if (System.currentTimeMillis() - ts > 30000) { // 30s
            proxySessionTimestamps.remove(uuid);
            return false;
        }
        return true;
    }
}
```