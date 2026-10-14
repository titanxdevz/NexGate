package com.nexgate.velocity.command;

import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/**
 * Command for players to check and migrate their account to Mojang Premium auto-login.
 * Enforces cryptographic proof of ownership via official online-mode re-authentication.
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

        authService.initiatePremiumMigration(player);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}

