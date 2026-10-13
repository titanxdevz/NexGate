package com.nexgate.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.messaging.PluginMessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.List;

/**
 * Server switch and auth gating listener.
 */
public class ServerListener {
    private final ProxyServer server;
    private final Logger logger;
    private final AuthManager authManager;
    private final ConfigManager configManager;
    private final PluginMessageManager messageManager;
    
    public ServerListener(ProxyServer server, Logger logger, AuthManager authManager,
                          ConfigManager configManager, PluginMessageManager messageManager) {
        this.server = server;
        this.logger = logger;
        this.authManager = authManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }
    
    @Subscribe(order = PostOrder.LOWEST)
    public void onServerConnect(ServerConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getServer();
        String targetName = target.getServerInfo().getName();
        
        if (!configManager.isRequireAuth()) {
            return;
        }
        
        boolean isAuthServer = configManager.getAuthServers().contains(targetName);
        boolean isAuthenticated = authManager.isAuthenticated(player);
        
        if (!isAuthenticated && !isAuthServer) {
            // Not authenticated and trying to connect to non-auth server - redirect to auth
            List<String> authServers = configManager.getAuthServers();
            if (!authServers.isEmpty()) {
                server.getServer(authServers.get(0)).ifPresent(authSrv -> {
                    event.setResult(ServerConnectEvent.ServerResult.Redirect(authSrv));
                });
            } else {
                event.setResult(ServerConnectEvent.ServerResult.denied(
                    Component.text("Authentication required").color(NamedTextColor.RED)));
            }
            return;
        }
        
        if (isAuthenticated && isAuthServer) {
            // Authenticated player going to auth server - maybe redirect to lobby
            String lobby = configManager.getTargetServer();
            if (lobby != null && !lobby.isEmpty()) {
                server.getServer(lobby).ifPresent(lobbySrv -> {
                    event.setResult(ServerConnectEvent.ServerResult.Redirect(lobbySrv));
                });
            }
        }
    }
    
    @Subscribe(order = PostOrder.LOWEST)
    public void onCommand(CommandExecuteEvent event) {
        if (event.getCommandSource() instanceof Player player) {
            if (configManager.isRequireAuth() && !authManager.isAuthenticated(player)) {
                String cmd = event.getCommand().toLowerCase();
                // Allow login/register commands
                if (cmd.startsWith("login") || cmd.startsWith("register") || cmd.startsWith("l") || cmd.startsWith("reg")) {
                    return;
                }
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(Component.text("Please login first. Use /login <password>")
                    .color(NamedTextColor.RED));
            }
        }
    }
    
    @Subscribe(order = PostOrder.LOWEST)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (configManager.isRequireAuth() && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Please login first. Use /login <password>")
                .color(NamedTextColor.RED));
        }
    }
}
