package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class LogoutCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public LogoutCommand(VelocityAuthService authService, AuthManager authManager) {
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
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>You are not logged in!</red>"));
            return;
        }

        authService.getDatabaseManager().invalidateSession(player.getUniqueId());
        authManager.logout(player);
        player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>You have been logged out.</yellow>"));
        authManager.startAuthFlow(player, true);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
