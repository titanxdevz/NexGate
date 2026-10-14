package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Enterprise RFC 6238 TOTP two-factor authentication management command for players.
 * Supports:
 *   /2fa setup
 *   /2fa confirm <code>
 *   /2fa verify <code>
 *   /2fa recovery <code>
 *   /2fa disable <password> <code>
 */
public class TotpCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public TotpCommand(VelocityAuthService authService, AuthManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MINI.deserialize("<red>Only in-game players can execute /2fa.</red>"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(player);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "setup" -> authService.initiateTotpSetup(player);
            case "confirm" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /2fa confirm <code></yellow>"));
                    return;
                }
                authService.confirmTotpSetup(player, args[1].trim());
            }
            case "verify" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /2fa verify <code></yellow>"));
                    return;
                }
                authService.handleTotpVerify(player, args[1].trim());
            }
            case "recovery" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /2fa recovery <recovery_code></yellow>"));
                    return;
                }
                authService.handleTotpRecovery(player, args[1].trim());
            }
            case "disable" -> {
                if (args.length < 3) {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: /2fa disable <password> <code></yellow>"));
                    return;
                }
                authService.disableTotp(player, args[1], args[2].trim());
            }
            default -> sendHelp(player);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━ [ Two-Factor Authentication (2FA) ] ━━━━━━━━━━━</gradient>
            <aqua>/2fa setup</aqua> <gray>- Begin authenticator app enrollment</gray>
            <aqua>/2fa confirm <code></aqua> <gray>- Confirm setup with 6-digit authenticator code</gray>
            <aqua>/2fa verify <code></aqua> <gray>- Submit 6-digit code during login challenge</gray>
            <aqua>/2fa recovery <code></aqua> <gray>- Redeem a single-use backup recovery code</gray>
            <aqua>/2fa disable <pass> <code></aqua> <gray>- Disable 2FA on your account</gray>
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
            """));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("setup", "confirm", "verify", "recovery", "disable", "help");
        }
        return List.of();
    }
}
