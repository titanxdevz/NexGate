package com.nexgate.velocity.listener;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
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
import java.util.concurrent.TimeUnit;

/**
 * Server switching, command gating, and chat gating listener on Velocity.
 */
public class ServerListener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Object pluginInstance;
    private final ProxyServer server;
    private final Logger logger;
    private final VelocityAuthService authService;
    private final AuthManager authManager;
    private final ConfigManager configManager;
    private final PluginMessageManager messageManager;

    public ServerListener(Object pluginInstance, ProxyServer server, Logger logger,
                          VelocityAuthService authService, AuthManager authManager,
                          ConfigManager configManager, PluginMessageManager messageManager) {
        this.pluginInstance = pluginInstance;
        this.server = server;
        this.logger = logger;
        this.authService = authService;
        this.authManager = authManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public ServerListener(ProxyServer server, Logger logger, VelocityAuthService authService,
                          AuthManager authManager, ConfigManager configManager, PluginMessageManager messageManager) {
        this(null, server, logger, authService, authManager, configManager, messageManager);
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getOriginalServer();
        String targetName = target.getServerInfo().getName();

        if (!configManager.isRequireAuth()) {
            return;
        }

        // 1. Consume any pre-resolved session discovered in PreLogin
        if (authService != null && !authManager.isAuthenticated(player)) {
            com.nexgate.model.UserAuth preUser = authService.consumePreResolvedSession(player.getUsername().toLowerCase());
            if (preUser != null) {
                authManager.authenticate(player, preUser, preUser.isPremium());
                String ip = player.getRemoteAddress().getAddress().getHostAddress();
                player.sendMessage(MINI.deserialize(authService.getPrefix() +
                        configManager.getConfig().messages.sessionRestored
                                .replace("{player}", player.getUsername())
                                .replace("{ip}", ip)));
                authService.syncLoginToBackend(player, preUser.getMessageColor(), preUser.getMessageStyle());
                logger.info("Restored valid active session for {} on PreConnect; routing to main lobby", player.getUsername());
            }
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
            // Authenticated player trying to go to auth server: redirect to lobby immediately!
            String lobby = configManager.getTargetServer();
            if (lobby != null && !lobby.isEmpty()) {
                server.getServer(lobby).ifPresent(lobbySrv -> {
                    logger.info("Redirecting authenticated player {} directly to target server {}", player.getUsername(), lobby);
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(lobbySrv));
                });
            }
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getServer();
        String currentName = target.getServerInfo().getName();

        // Safety fallback: If player is authenticated and connected to an auth server, REDIRECT TO LOBBY!
        if (authManager.isAuthenticated(player) && configManager.getAuthServers().contains(currentName)) {
            String lobby = configManager.getTargetServer();
            if (lobby != null && !lobby.isEmpty() && !lobby.equalsIgnoreCase(currentName)) {
                server.getServer(lobby).ifPresent(lobbySrv -> {
                    logger.info("Redirecting authenticated player {} from auth server to lobby", player.getUsername());
                    player.createConnectionRequest(lobbySrv).fireAndForget();
                });
            }
            return;
        }

        // If player is already authenticated, send HMAC signed perform.login to the backend
        if (authManager.isAuthenticated(player)) {
            Optional<AuthManager.UserSessionData> sessionOpt = authManager.getSession(player.getUniqueId());
            String color = sessionOpt.map(AuthManager.UserSessionData::messageColor).orElse("WHITE");
            String style = sessionOpt.map(AuthManager.UserSessionData::messageStyle).orElse("DEFAULT");

            // 1. Immediate dispatch
            messageManager.sendPerformLogin(target, player.getUsername(), player.getUniqueId(), color, style);

            // 2. Scheduled retry pulses (+250ms, +600ms, +1200ms) to guarantee arrival after Paper transition to PLAY
            if (pluginInstance != null) {
                Runnable retrySend = () -> {
                    if (player.isActive() && authManager.isAuthenticated(player)) {
                        player.getCurrentServer().ifPresent(cur -> {
                            if (cur.getServer().equals(target)) {
                                messageManager.sendPerformLogin(target, player.getUsername(), player.getUniqueId(), color, style);
                            }
                        });
                    }
                };

                server.getScheduler().buildTask(pluginInstance, retrySend)
                        .delay(250, TimeUnit.MILLISECONDS).schedule();
                server.getScheduler().buildTask(pluginInstance, retrySend)
                        .delay(600, TimeUnit.MILLISECONDS).schedule();
                server.getScheduler().buildTask(pluginInstance, retrySend)
                        .delay(1200, TimeUnit.MILLISECONDS).schedule();
            }
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
                    cmd.equals("l") || cmd.equals("reg") ||
                    cmd.startsWith("2fa") || cmd.startsWith("pin")) {
                    return;
                }

                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(MINI.deserialize(configManager.getConfig().messages.prefix +
                        "<red>You must authenticate first! Use <yellow>/login <password></yellow></red>"));
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (configManager.isRequireAuth() && !authManager.isAuthenticated(player)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(MINI.deserialize(configManager.getConfig().messages.prefix +
                    "<red>You must authenticate first! Use <yellow><click:suggest_command:'/login '>/login <password></click></yellow></red>"));
        }
    }
}
