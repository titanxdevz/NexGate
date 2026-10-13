package com.nexgate.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Connection and auth lifecycle listener.
 */
public class ConnectionListener {
    private final ProxyServer server;
    private final Logger logger;
    private final AuthManager authManager;
    private final ConfigManager configManager;
    
    public ConnectionListener(ProxyServer server, Logger logger, AuthManager authManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.authManager = authManager;
        this.configManager = configManager;
    }
    
    @Subscribe(order = PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        if (username == null || username.isEmpty()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text("Invalid username").color(NamedTextColor.RED)));
            return;
        }
        // Basic validation - alphanumeric and length
        if (username.length() < 3 || username.length() > 16) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text("Username must be 3-16 characters").color(NamedTextColor.RED)));
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text("Invalid username characters").color(NamedTextColor.RED)));
            return;
        }
        // For cracked/premium - in this design, let backend/Paper handle specifics
        // Allow connection to proceed; auth gating happens on auth server
        event.setResult(PreLoginEvent.PreLoginComponentResult.allowed());
    }
    
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (configManager.isRequireAuth()) {
            authManager.startAuth(player);
            // If connecting to auth server, they need to authenticate
            // If connecting elsewhere without auth - will be handled by server connect event
        }
    }
    
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        authManager.logout(player);
    }
}
