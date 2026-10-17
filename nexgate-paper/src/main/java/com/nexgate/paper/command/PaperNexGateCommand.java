package com.nexgate.paper.command;

import com.nexgate.paper.NexGatePaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Administrative and spawn management command on Paper backend.
 * Enables setting custom login/auth spawn and lobby spawn in-game via /nexgate setspawn or /setspawn.
 */
public class PaperNexGateCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold><bold>NEXGATE</bold></gold> <white>→</white> ";

    private final NexGatePaperPlugin plugin;

    public PaperNexGateCommand(NexGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        // Handle /setspawn or /setlogin direct aliases
        if (cmdName.equals("setspawn") || cmdName.equals("setlogin") || cmdName.equals("setauthspawn")) {
            return handleSetSpawn(sender);
        }

        // Handle /spawn direct alias
        if (cmdName.equals("spawn")) {
            return handleSpawn(sender);
        }

        // Handle /nexgate <subcommand>
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "setspawn", "setlogin", "setauthspawn" -> {
                return handleSetSpawn(sender);
            }
            case "spawn" -> {
                return handleSpawn(sender);
            }
            case "reload" -> {
                if (!sender.hasPermission("nexgate.admin")) {
                    sender.sendMessage(MINI.deserialize(PREFIX + "<red>You lack permission to use this command.</red>"));
                    return true;
                }
                plugin.getConfigManager().load();
                sender.sendMessage(MINI.deserialize(PREFIX + "<green>Paper companion configuration reloaded!</green>"));
                return true;
            }
            case "info" -> {
                Location spawn = plugin.getSpawnLocation();
                String spawnStr = spawn != null ? String.format("%s at %.1f, %.1f, %.1f",
                        spawn.getWorld().getName(), spawn.getX(), spawn.getY(), spawn.getZ()) : "Default";
                sender.sendMessage(MINI.deserialize(PREFIX + "<yellow>Limbo Cage: <white>" + plugin.getConfigManager().isLimboCageEnabled()
                        + "</white> | Spawn: <aqua>" + spawnStr + "</aqua></yellow>"));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!sender.hasPermission("nexgate.admin")) {
            sender.sendMessage(MINI.deserialize(PREFIX + "<red>You lack permission to set the spawn point.</red>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize(PREFIX + "<red>This command can only be executed by in-game players.</red>"));
            return true;
        }

        Location loc = player.getLocation();
        plugin.getConfigManager().setSpawn(loc);

        player.sendMessage(MINI.deserialize(PREFIX + "<green>Server spawn & login area set to <white>"
                + String.format("%.2f, %.2f, %.2f (yaw: %.1f, pitch: %.1f in '%s')",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), loc.getWorld().getName())
                + "</white>!</green>"));
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize(PREFIX + "<red>This command can only be executed by in-game players.</red>"));
            return true;
        }

        Location spawn = plugin.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
            player.sendMessage(MINI.deserialize(PREFIX + "<green>Teleported to server spawn.</green>"));
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage(MINI.deserialize(PREFIX + "<yellow>Teleported to world default spawn.</yellow>"));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━ [ NexGate Backend Management ] ━━━━━━━━━</gradient>
            <gray>• <gold>/nexgate setspawn</gold> - Set the spawn & login area to your location</gray>
            <gray>• <gold>/nexgate spawn</gold> - Teleport to the configured spawn area</gray>
            <gray>• <gold>/nexgate reload</gold> - Reload backend companion configuration</gray>
            <gray>• <gold>/nexgate info</gold> - View limbo and spawn status</gray>
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
            """));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("setspawn", "spawn", "reload", "info", "help");
            List<String> matched = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    matched.add(s);
                }
            }
            return matched;
        }
        return Collections.emptyList();
    }
}
