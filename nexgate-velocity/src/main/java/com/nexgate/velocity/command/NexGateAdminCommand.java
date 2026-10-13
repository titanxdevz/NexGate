package com.nexgate.velocity.command;

import com.nexgate.color.MessageColor;
import com.nexgate.model.UserAuth;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.nexgate.velocity.config.ConfigManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class NexGateAdminCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final ProxyServer server;
    private final ConfigManager configManager;
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public NexGateAdminCommand(ProxyServer server, ConfigManager configManager,
                               VelocityAuthService authService, AuthManager authManager) {
        this.server = server;
        this.configManager = configManager;
        this.authService = authService;
        this.authManager = authManager;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("nexgate.admin");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(source);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                configManager.load();
                source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Configuration reloaded successfully!</green>"));
            }
            case "info" -> {
                int onlineAuthed = authManager.getAuthenticatedUuids().size();
                int totalOnline = server.getPlayerCount();
                source.sendMessage(MINI.deserialize("""
                    <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━ [ NexGate System Info ] ━━━━━━━━━━━━━━━━</gradient>
                    <gray>Version: <white>1.0.0-SNAPSHOT</white></gray>
                    <gray>Online Players: <aqua>%d</aqua> | Authenticated Sessions: <green>%d</green></gray>
                    <gray>Database Type: <yellow>%s</yellow></gray>
                    <gray>Premium Auto-Detect: <aqua>%s</aqua></gray>
                    <gray>Auth Timeout: <white>%ds</white> | Session Timeout: <white>%dm</white></gray>
                    <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                    """.formatted(
                        totalOnline, onlineAuthed,
                        configManager.getConfig().database.type,
                        configManager.isPremiumAutoDetect(),
                        configManager.getLoginTimeoutSeconds(),
                        configManager.getSessionTimeoutMinutes()
                )));
            }
            case "lookup" -> {
                if (args.length < 2) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate lookup <username></yellow>"));
                    return;
                }
                String target = args[1];
                authService.getDatabaseManager().getUser(target).thenAccept(userOpt -> {
                    if (userOpt.isEmpty()) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>User '" + target + "' not found in database.</red>"));
                        return;
                    }
                    UserAuth u = userOpt.get();
                    source.sendMessage(MINI.deserialize("""
                        <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━ [ Player Lookup: %s ] ━━━━━━━━━━━</gradient>
                        <gray>Username: <white>%s</white> (ID: %d)</gray>
                        <gray>UUID: <aqua>%s</aqua></gray>
                        <gray>Registered: <white>%s</white> from IP <dark_aqua>%s</dark_aqua></gray>
                        <gray>Last Login: <white>%s</white> from IP <dark_aqua>%s</dark_aqua></gray>
                        <gray>Premium Account: <yellow>%s</yellow> (UUID: %s)</gray>
                        <gray>Message Color: <white>%s</white> | Style: <white>%s</white></gray>
                        <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                        """.formatted(
                            u.getUsername(), u.getUsername(), u.getId(),
                            u.getUuid() != null ? u.getUuid().toString() : "None",
                            DATE_FORMAT.format(new Date(u.getRegDate())), u.getIp(),
                            u.getLastLogin() > 0 ? DATE_FORMAT.format(new Date(u.getLastLogin())) : "Never",
                            u.getLastIp() != null ? u.getLastIp() : "N/A",
                            u.isPremium() ? "Yes" : "No",
                            u.getPremiumUuid() != null ? u.getPremiumUuid().toString() : "None",
                            u.getMessageColor(), u.getMessageStyle()
                    )));
                });
            }
            case "unregister" -> {
                if (args.length < 2) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate unregister <username></yellow>"));
                    return;
                }
                String target = args[1];
                authService.getDatabaseManager().unregisterUser(target).thenAccept(ok -> {
                    if (ok) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>User '" + target + "' unregistered and all sessions purged.</green>"));
                        server.getPlayer(target).ifPresent(p -> {
                            authManager.logout(p);
                            authManager.startAuthFlow(p, false);
                            p.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Your account has been reset by an administrator.</red>"));
                        });
                    } else {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Could not unregister user '" + target + "'. Not found?</red>"));
                    }
                });
            }
            case "setpremium" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate setpremium <username> <true|false></yellow>"));
                    return;
                }
                String target = args[1];
                boolean isPrem = Boolean.parseBoolean(args[2]);
                authService.getDatabaseManager().updatePremiumStatus(target, isPrem, null).thenRun(() -> {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Updated premium status for '" + target + "' to " + isPrem + "</green>"));
                });
            }
            case "forcelogin" -> {
                if (args.length < 2) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate forcelogin <username></yellow>"));
                    return;
                }
                String target = args[1];
                Optional<Player> playerOpt = server.getPlayer(target);
                if (playerOpt.isEmpty()) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Player '" + target + "' is not online on proxy.</red>"));
                    return;
                }
                Player p = playerOpt.get();
                authManager.authenticate(p, null, false);
                p.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>You were force-logged in by an administrator.</green>"));
                source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Force-authenticated " + target + ".</green>"));
            }
            case "setcolor" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate setcolor <username> <color></yellow>"));
                    return;
                }
                String target = args[1];
                String color = args[2].toUpperCase();
                authService.getDatabaseManager().updateMessageColor(target, color, "DEFAULT").thenRun(() -> {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Set message color for '" + target + "' to " + color + "</green>"));
                    server.getPlayer(target).ifPresent(p -> {
                        authManager.updatePlayerSettings(p.getUniqueId(), color, "DEFAULT");
                        p.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>An administrator updated your message color to " + color + "!</green>"));
                    });
                });
            }
            default -> sendHelp(source);
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ NexGate Admin Commands ] ━━━━━━━━━━━━━━━</gradient>
            <aqua>/nexgate info</aqua> <gray>- View system health, session metrics, and config</gray>
            <aqua>/nexgate reload</aqua> <gray>- Reload configuration file</gray>
            <aqua>/nexgate lookup <player></aqua> <gray>- Inspect player account details and IP logs</gray>
            <aqua>/nexgate unregister <player></aqua> <gray>- Purge player account and active sessions</gray>
            <aqua>/nexgate forcelogin <player></aqua> <gray>- Bypass auth gate for player</gray>
            <aqua>/nexgate setpremium <player> <true|false></aqua> <gray>- Toggle player's premium status</gray>
            <aqua>/nexgate setcolor <player> <color></aqua> <gray>- Assign custom message color to player</gray>
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
            """));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("info", "reload", "lookup", "unregister", "forcelogin", "setpremium", "setcolor");
        } else if (args.length == 3 && "setpremium".equalsIgnoreCase(args[0])) {
            return List.of("true", "false");
        } else if (args.length == 3 && "setcolor".equalsIgnoreCase(args[0])) {
            return java.util.Arrays.stream(MessageColor.values()).map(MessageColor::getId).toList();
        }
        return List.of();
    }
}
