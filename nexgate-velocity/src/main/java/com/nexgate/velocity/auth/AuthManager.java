package com.nexgate.velocity.auth;

import com.velocitypowered.api.proxy.Player;
import com.nexgate.velocity.config.ConfigManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages authentication state for Velocity proxy.
 */
public class AuthManager {
    private final Logger logger;
    private final ConfigManager configManager;
    
    // Authenticated players (by UUID lowercase key)
    private final Map<String, Long> authenticatedPlayers = new ConcurrentHashMap<>();
    private final Map<String, Long> authStartTimes = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    
    public AuthManager(Logger logger, ConfigManager configManager) {
        this.logger = logger;
        this.configManager = configManager;
    }
    
    public boolean isAuthenticated(Player player) {
        if (player == null) return false;
        String key = player.getUniqueId().toString().toLowerCase();
        Long expiry = authenticatedPlayers.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            authenticatedPlayers.remove(key);
            return false;
        }
        return true;
    }
    
    public boolean isAuthenticated(String username) {
        // For username-based checks (pre-login)
        // In practice, use UUID; simplified for this design
        return false;
    }
    
    public void authenticate(Player player) {
        String key = player.getUniqueId().toString().toLowerCase();
        long expiry = System.currentTimeMillis() + 
                      TimeUnit.MINUTES.toMillis(configManager.getSessionTimeoutMinutes());
        authenticatedPlayers.put(key, expiry);
        logger.debug("Player {} authenticated", player.getUsername());
    }
    
    public void logout(Player player) {
        String key = player.getUniqueId().toString().toLowerCase();
        authenticatedPlayers.remove(key);
        authStartTimes.remove(key);
        logger.debug("Player {} logged out", player.getUsername());
    }
    
    public void startAuth(Player player) {
        String key = player.getUniqueId().toString().toLowerCase();
        authStartTimes.put(key, System.currentTimeMillis());
    }
    
    public boolean isAuthTimedOut(Player player) {
        String key = player.getUniqueId().toString().toLowerCase();
        Long start = authStartTimes.get(key);
        if (start == null) return false;
        long timeout = TimeUnit.SECONDS.toMillis(configManager.getLoginTimeoutSeconds());
        return System.currentTimeMillis() - start > timeout;
    }
    
    public Set<String> getAuthenticatedUuids() {
        cleanupExpired();
        return authenticatedPlayers.keySet();
    }
    
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        authenticatedPlayers.entrySet().removeIf(entry -> entry.getValue() < now);
    }
    
    public record LoginAttempt(int count, long lastAttempt) {
    }
}
