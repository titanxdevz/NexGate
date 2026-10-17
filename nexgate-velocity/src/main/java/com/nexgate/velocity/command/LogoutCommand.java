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

        // 1. Notify current backend server to deauthenticate
        player.getCurrentServer().ifPresent(cur -> {
            authService.getMessageManager().sendLogoutState(cur.getServer(), player.getUsername(), player.getUniqueId());
        });

        // 2. Invalidate session in database
        authService.getDatabaseManager().invalidateSession(player.getUniqueId());

        // 3. Clear authenticated state on proxy
        authManager.logout(player);
        player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>You have been safely logged out. Returning to authentication...</yellow>"));

        // 4. Transfer player back to auth limbo server immediately (auth flow starts on arrival)
        authService.transferToAuthServer(player);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
