package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Command for players to check and migrate their account to Mojang Premium auto-login.
 */
public class PremiumCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public PremiumCommand(VelocityAuthService authService, AuthManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MINI.deserialize("<red>Players only.</red>"));
            return;
        }

        if (!authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>You must be logged in to check premium status!</red>"));
            return;
        }

        player.sendMessage(MINI.deserialize(authService.getPrefix() + "<gray>Checking Mojang session servers for official account...</gray>"));

        authService.getMojangChecker().checkProfileAsync(player.getUsername()).thenAccept(profile -> {
            if (profile.exists()) {
                authService.getDatabaseManager().updatePremiumStatus(player.getUsername(), true, profile.uuid());
                player.sendMessage(MINI.deserialize(authService.getPrefix() +
                        "<gradient:#00C9FF:#92FE9D><bold>✔ Mojang account verified!</bold></gradient>\n" +
                        "<gray>Official UUID: <aqua>" + profile.uuid() + "</aqua></gray>\n" +
                        "<green>Your account is now set to Premium! On your next login, Velocity will automatically authenticate you without passwords.</green>"));
            } else {
                player.sendMessage(MINI.deserialize(authService.getPrefix() +
                        "<red>No paid Mojang Minecraft account found for username '<white>" + player.getUsername() + "</white>'.</red>"));
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
