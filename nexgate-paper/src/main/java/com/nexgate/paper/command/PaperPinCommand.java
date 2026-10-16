package com.nexgate.paper.command;

import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.gui.PinPadGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles /pin, /authgui, and /gui commands to open the interactive PIN pad.
 */
public class PaperPinCommand implements CommandExecutor {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold><bold>NEXGATE</bold></gold> <white>→</white> ";

    private final NexGatePaperPlugin plugin;

    public PaperPinCommand(NexGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize(PREFIX + "<red>This command can only be executed by in-game players.</red>"));
            return true;
        }

        if (plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(PREFIX + "<yellow>You are already authenticated!</yellow>"));
            return true;
        }

        if (args.length > 0) {
            // Direct PIN entry: /pin 1234 -> chat("/login 1234")
            player.chat("/login " + args[0]);
            return true;
        }

        // Open graphical PIN-Pad GUI
        PinPadGui.open(player);
        return true;
    }
}
