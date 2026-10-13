package com.nexgate.velocity.command;

import com.nexgate.crypto.HashingUtils;
import com.nexgate.security.PasswordValidator;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class ChangePasswordCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public ChangePasswordCommand(VelocityAuthService authService, AuthManager authManager) {
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
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>You must be logged in to change your password!</red>"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize(authService.getPrefix() +
                    "<yellow>Usage: <aqua>/changepassword <oldPassword> <newPassword></aqua></yellow>"));
            return;
        }

        String oldPass = args[0];
        String newPass = args[1];

        authService.getDatabaseManager().getUser(player.getUsername()).thenAccept(userOpt -> {
            if (userOpt.isEmpty()) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>User not found in database.</red>"));
                return;
            }

            if (!HashingUtils.verifyPassword(oldPass, userOpt.get().getPasswordHash())) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Old password is incorrect!</red>"));
                return;
            }

            PasswordValidator val = PasswordValidator.createDefault();
            PasswordValidator.ValidationResult res = val.validate(newPass, player.getUsername());
            if (!res.valid()) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>New password rejected: " + res.message() + "</red>"));
                return;
            }

            String newHash = HashingUtils.hashPassword(newPass);
            authService.getDatabaseManager().updatePassword(player.getUsername(), newHash).thenAccept(ok -> {
                if (ok) {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<green>✔ Password updated successfully!</green>"));
                } else {
                    player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Failed to update password.</red>"));
                }
            });
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
