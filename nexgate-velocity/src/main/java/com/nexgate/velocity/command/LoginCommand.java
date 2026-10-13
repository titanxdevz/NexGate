package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class LoginCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public LoginCommand(VelocityAuthService authService, AuthManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MINI.deserialize("<red>This command can only be executed by players.</red>"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: <aqua>/login <password></aqua></yellow>"));
            return;
        }

        authService.handleLogin(player, args[0]);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
