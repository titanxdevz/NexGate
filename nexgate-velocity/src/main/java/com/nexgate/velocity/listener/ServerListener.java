package com.nexgate.velocity.listener;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.messaging.PluginMessageManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Server switching, command gating, and chat gating listener on Velocity.
 */
public class ServerListener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

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

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getOriginalServer();
        String targetName = target.getServerInfo().getName();

        if (!configManager.isRequireAuth()) {
            return;
        }

        boolean isAuthServer = configManager.getAuthServers().contains(targetName);
        boolean isAuthenticated = authManager.isAuthenticated(player);

        if (!isAuthenticated && !isAuthServer) {
            // Not authenticated: enforce redirection to auth server
            List<String> authServers = configManager.getAuthServers();
            if (!authServers.isEmpty()) {
                Optional<RegisteredServer> authSrv = server.getServer(authServers.get(0));
                if (authSrv.isPresent()) {
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(authSrv.get()));
                    return;
                }
            }
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        if (isAuthenticated && isAuthServer) {
            // Authenticated player trying to go to auth server: redirect to lobby
            String lobby = configManager.getTargetServer();
            if (lobby != null && !lobby.isEmpty()) {
                server.getServer(lobby).ifPresent(lobbySrv -> {
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(lobbySrv));
                });
            }
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getServer();

        // If player is already authenticated, send HMAC signed perform.login to the backend
        if (authManager.isAuthenticated(player)) {
            Optional<AuthManager.UserSessionData> sessionOpt = authManager.getSession(player.getUniqueId());
            String color = sessionOpt.map(AuthManager.UserSessionData::messageColor).orElse("WHITE");
            String style = sessionOpt.map(AuthManager.UserSessionData::messageStyle).orElse("DEFAULT");

            messageManager.sendPerformLogin(target, player.getUsername(), player.getUniqueId(), color, style);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommand(CommandExecuteEvent event) {
        if (event.getCommandSource() instanceof Player player) {
            if (configManager.isRequireAuth() && !authManager.isAuthenticated(player)) {
                String cmd = event.getCommand().toLowerCase().trim();
                // Whitelist auth commands
                if (cmd.startsWith("login") || cmd.startsWith("register") ||
                    cmd.startsWith("l ") || cmd.startsWith("reg ") ||
                    cmd.equals("l") || cmd.equals("reg")) {
                    return;
                }

                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(MINI.deserialize(configManager.getConfig().messages.prefix +
                        "<red>You must authenticate first! Use <yellow>/login</yellow> or <yellow>/register</yellow></red>"));
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (configManager.isRequireAuth() && !authManager.isAuthenticated(player)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(MINI.deserialize(configManager.getConfig().messages.prefix +
                    "<red>Chat is disabled until you authenticate! Use <yellow>/login</yellow> or <yellow>/register</yellow></red>"));
        }
    }
}
