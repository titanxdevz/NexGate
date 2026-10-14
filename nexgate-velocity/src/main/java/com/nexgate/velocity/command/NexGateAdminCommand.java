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
            case "lock" -> {
                if (args.length < 2) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate lock <username> [reason]</yellow>"));
                    return;
                }
                String target = args[1];
                String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Administrative suspension";
                String actor = (source instanceof Player p) ? p.getUsername() : "console";
                authService.handleAdminLockAccount(actor, target, reason).thenAccept(ok -> {
                    if (ok) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Account '" + target + "' locked: " + reason + "</green>"));
                    } else {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>User '" + target + "' not found.</red>"));
                    }
                });
            }
            case "unlock" -> {
                if (args.length < 2) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate unlock <username></yellow>"));
                    return;
                }
                String target = args[1];
                String actor = (source instanceof Player p) ? p.getUsername() : "console";
                authService.handleAdminUnlockAccount(actor, target).thenAccept(ok -> {
                    if (ok) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Account '" + target + "' unlocked and rate-limit counters reset.</green>"));
                    } else {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>User '" + target + "' not found.</red>"));
                    }
                });
            }
            case "resetpassword" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate resetpassword <username> <newpassword></yellow>"));
                    return;
                }
                String target = args[1];
                String newPass = args[2];
                String actor = (source instanceof Player p) ? p.getUsername() : "console";
                authService.handleAdminResetPassword(actor, target, newPass).thenAccept(ok -> {
                    if (ok) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Password for '" + target + "' reset and all active sessions revoked.</green>"));
                    } else {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>User '" + target + "' not found.</red>"));
                    }
                });
            }
            case "sessions" -> {
                authService.getDatabaseManager().getActiveSessions().thenAccept(sessions -> {
                    if (sessions.isEmpty()) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<gray>No active sessions found in database.</gray>"));
                        return;
                    }
                    long now = System.currentTimeMillis();
                    StringBuilder sb = new StringBuilder();
                    sb.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ Active Sessions (%d) ] ━━━━━━━━━━━━━━━</gradient>\n".formatted(sessions.size()));
                    for (var s : sessions) {
                        long remainingMin = Math.max(0, (s.getExpiresAt() - now) / 60000L);
                        sb.append("<gray>• <white>%s</white> (<aqua>%s</aqua>) | IP: <dark_aqua>%s</dark_aqua> | Expires: <yellow>%dm</yellow></gray>\n"
                                .formatted(s.getUsernameLower(), s.getUuid().toString().substring(0, 8) + "...", s.getIpAddress(), remainingMin));
                    }
                    sb.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
                    source.sendMessage(MINI.deserialize(sb.toString()));
                });
            }
            case "audit" -> {
                String targetFilter = (args.length >= 2 && !args[1].isBlank()) ? args[1] : null;
                int limit = 10;
                if (args.length >= 3) {
                    try {
                        limit = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {}
                }
                authService.getDatabaseManager().getRecentAuditLogs(targetFilter, limit).thenAccept(logs -> {
                    if (logs.isEmpty()) {
                        source.sendMessage(MINI.deserialize(authService.getPrefix() + "<gray>No audit logs matching query.</gray>"));
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ Security Audit Logs ] ━━━━━━━━━━━━━━━</gradient>\n");
                    for (var log : logs) {
                        String time = DATE_FORMAT.format(new Date(log.timestamp()));
                        sb.append("<gray>[%s] <yellow>%s</yellow> by <aqua>%s</aqua> on <white>%s</white> (<dark_aqua>%s</dark_aqua>): <white>%s</white></gray>\n"
                                .formatted(time, log.action(), log.actor(), log.target() != null ? log.target() : "N/A", log.ip() != null ? log.ip() : "-", log.detail() != null ? log.detail() : ""));
                    }
                    sb.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
                    source.sendMessage(MINI.deserialize(sb.toString()));
                });
            }
            case "antibot" -> handleAntiBotCommand(source, args);
            default -> sendHelp(source);
        }
    }

    private void handleAntiBotCommand(CommandSource source, String[] args) {
        var antiBot = authService.getAntiBotManager();
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            var m = antiBot.getMetrics();
            source.sendMessage(MINI.deserialize("""
                <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ AntiBot Security Status ] ━━━━━━━━━━━━━━━</gradient>
                <gray>Protection Enabled: <yellow>%s</yellow> | Operating Mode: <aqua>%s</aqua></gray>
                <gray>Under-Attack Mitigation: %s</gray>
                <gray>Connections Evaluated: <white>%d</white></gray>
                <gray>Connections Rejected: <red>%d</red></gray>
                <gray>Rate Limited: <yellow>%d</yellow> | Fast Reconnect Bursts: <yellow>%d</yellow></gray>
                <gray>Blacklisted / Banned Drops: <red>%d</red></gray>
                <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                """.formatted(
                    antiBot.isEnabled(),
                    antiBot.getMode().name(),
                    m.mitigationActive ? "<red><bold>ACTIVE (DEFENSE ENGAGED)</bold></red>" : "<green>Inactive (Normal Traffic)</green>",
                    m.evaluated, m.rejected, m.rateLimited, m.fastReconnects, m.blacklisted
            )));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "mode" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot mode <auto|on|off></yellow>"));
                    return;
                }
                try {
                    com.nexgate.velocity.antibot.AntiBotManager.Mode newMode =
                            com.nexgate.velocity.antibot.AntiBotManager.Mode.valueOf(args[2].toUpperCase());
                    antiBot.setMode(newMode);
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>AntiBot mode updated to: <aqua>" + newMode.name() + "</aqua></green>"));
                } catch (IllegalArgumentException e) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Invalid mode. Choose from: auto, on, off.</red>"));
                }
            }
            case "ban" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot ban <ip/cidr> [durationSeconds]</yellow>"));
                    return;
                }
                String target = args[2];
                long duration = 300;
                if (args.length >= 4) {
                    try {
                        duration = Long.parseLong(args[3]);
                    } catch (NumberFormatException ignored) {}
                }
                if (target.contains("/")) {
                    antiBot.addBlacklistCidr(target);
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Added CIDR subnet <aqua>" + target + "</aqua> to AntiBot blacklist.</green>"));
                } else {
                    antiBot.addTemporaryBan(target, duration);
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Temporarily throttled IP <aqua>" + target + "</aqua> for <yellow>" + duration + "s</yellow>.</green>"));
                }
            }
            case "unban" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot unban <ip/cidr></yellow>"));
                    return;
                }
                String target = args[2];
                if (target.contains("/")) {
                    boolean removed = antiBot.removeBlacklistCidr(target);
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + (removed ? "<green>Removed CIDR from blacklist.</green>" : "<yellow>CIDR not found.</yellow>")));
                } else {
                    boolean removed = antiBot.removeTemporaryBan(target);
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + (removed ? "<green>Unbanned IP from temporary throttling.</green>" : "<yellow>IP was not throttled.</yellow>")));
                }
            }
            case "whitelist" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot whitelist <ip/cidr></yellow>"));
                    return;
                }
                antiBot.addWhitelistCidr(args[2]);
                source.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>Added <aqua>" + args[2] + "</aqua> to AntiBot whitelist.</green>"));
            }
            case "unwhitelist" -> {
                if (args.length < 3) {
                    source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot unwhitelist <ip/cidr></yellow>"));
                    return;
                }
                boolean removed = antiBot.removeWhitelistCidr(args[2]);
                source.sendMessage(MINI.deserialize(authService.getPrefix() + (removed ? "<green>Removed CIDR from whitelist.</green>" : "<yellow>Not found in whitelist.</yellow>")));
            }
            default -> source.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /nexgate antibot <status|mode|ban|unban|whitelist|unwhitelist></yellow>"));
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ NexGate Admin Commands ] ━━━━━━━━━━━━━━━</gradient>
            <aqua>/nexgate info</aqua> <gray>- View system health, session metrics, and config</gray>
            <aqua>/nexgate reload</aqua> <gray>- Reload configuration file</gray>
            <aqua>/nexgate lookup <player></aqua> <gray>- Inspect player account details and IP logs</gray>
            <aqua>/nexgate lock <player> [reason]</aqua> <gray>- Administratively suspend an account</gray>
            <aqua>/nexgate unlock <player></aqua> <gray>- Restore account access and reset lockout</gray>
            <aqua>/nexgate resetpassword <player> <newpass></aqua> <gray>- Force reset player password</gray>
            <aqua>/nexgate sessions</aqua> <gray>- List active authenticated sessions</gray>
            <aqua>/nexgate audit [player] [limit]</aqua> <gray>- View security audit log entries</gray>
            <aqua>/nexgate antibot <status|mode|ban|unban|whitelist></aqua> <gray>- Manage AntiBot mitigation</gray>
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
            return List.of("info", "reload", "lookup", "lock", "unlock", "resetpassword", "sessions", "audit", "antibot", "unregister", "forcelogin", "setpremium", "setcolor");
        } else if (args.length == 2 && "antibot".equalsIgnoreCase(args[0])) {
            return List.of("status", "mode", "ban", "unban", "whitelist", "unwhitelist");
        } else if (args.length == 3 && "antibot".equalsIgnoreCase(args[0]) && "mode".equalsIgnoreCase(args[1])) {
            return List.of("auto", "on", "off");
        } else if (args.length == 3 && "setpremium".equalsIgnoreCase(args[0])) {
            return List.of("true", "false");
        } else if (args.length == 3 && "setcolor".equalsIgnoreCase(args[0])) {
            return java.util.Arrays.stream(MessageColor.values()).map(MessageColor::getId).toList();
        }
        return List.of();
    }
}
