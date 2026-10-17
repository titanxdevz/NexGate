package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Handles /pin on Velocity proxy.
 * If digits are provided, logs in directly. If empty, provides assistance or triggers backend GUI.
 */
public class PinCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public PinCommand(VelocityAuthService authService, AuthManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MINI.deserialize("<red>This command can only be executed by players.</red>"));
            return;
        }

        if (authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>You are already authenticated!</yellow>"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length >= 1) {
            authService.handleLogin(player, args[0]);
            return;
        }

        player.sendMessage(MINI.deserialize(authService.getPrefix() +
                "<yellow>To authenticate, use: <gold><click:suggest_command:'/login '>/login <password></click></gold></yellow>"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
