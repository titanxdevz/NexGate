package com.nexgate.paper.command;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nexgate.color.ChatColorFormatter;
import com.nexgate.color.MessageColor;
import com.nexgate.color.MessageStyle;
import com.nexgate.message.MessageChannel;
import com.nexgate.message.MessageType;
import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.auth.PaperAuthManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PaperColorCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final NexGatePaperPlugin plugin;
    private final PaperAuthManager authManager;

    public PaperColorCommand(NexGatePaperPlugin plugin, PaperAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize("<red>Players only.</red>"));
            return true;
        }

        if (!authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize("<red>You must be authenticated to customize your chat color!</red>"));
            return true;
        }

        String currentColor = authManager.getPlayerColor(player.getUniqueId());
        String currentStyle = authManager.getPlayerStyle(player.getUniqueId());

        if (args.length == 0) {
            sendInteractiveColorMenu(player, currentColor, currentStyle);
            return true;
        }

        String sub = args[0].toUpperCase();

        if ("STYLE".equalsIgnoreCase(sub)) {
            if (args.length < 2) {
                player.sendMessage(MINI.deserialize("<yellow>Usage: <aqua>/color style <DEFAULT|BOLD|ITALIC|UNDERLINED></aqua></yellow>"));
                return true;
            }
            Optional<MessageStyle> styleOpt = MessageStyle.fromId(args[1]);
            if (styleOpt.isEmpty()) {
                player.sendMessage(MINI.deserialize("<red>Unknown style! Choose: DEFAULT, BOLD, ITALIC, UNDERLINED</red>"));
                return true;
            }
            authManager.setPlayerSettings(player.getUniqueId(), currentColor, styleOpt.get().getId());
            syncToProxy(player, currentColor, styleOpt.get().getId());
            player.sendMessage(MINI.deserialize("<green>Chat message style updated to " + styleOpt.get().getDisplayName() + "!</green>"));
            return true;
        }

        if ("PREVIEW".equalsIgnoreCase(sub)) {
            if (args.length < 2) {
                player.sendMessage(MINI.deserialize("<yellow>Usage: <aqua>/color preview <text></aqua></yellow>"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            String preview = ChatColorFormatter.formatMessage(sb.toString().trim(), currentColor, currentStyle);
            player.sendMessage(MINI.deserialize("<gray>Preview: </gray>" + preview));
            return true;
        }

        String targetColor = args[0];
        if (MessageColor.isValidHex(targetColor)) {
            authManager.setPlayerSettings(player.getUniqueId(), targetColor.toUpperCase(), currentStyle);
            syncToProxy(player, targetColor.toUpperCase(), currentStyle);
            player.sendMessage(MINI.deserialize("<green>Chat color updated to custom hex " + targetColor.toUpperCase() + "!</green>"));
            return true;
        }

        Optional<MessageColor> colorOpt = MessageColor.fromId(targetColor);
        if (colorOpt.isPresent()) {
            MessageColor color = colorOpt.get();
            authManager.setPlayerSettings(player.getUniqueId(), color.getId(), currentStyle);
            syncToProxy(player, color.getId(), currentStyle);
            player.sendMessage(MINI.deserialize("<green>Chat color updated to " + color.getDisplayName() + "!</green>"));
        } else {
            player.sendMessage(MINI.deserialize("<red>Unknown color '" + targetColor + "'! Use /color to view the palette.</red>"));
        }

        return true;
    }

    private void syncToProxy(Player player, String color, String style) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.SYNC_SETTINGS.getId());
        out.writeUTF(player.getName());
        out.writeUTF(color);
        out.writeUTF(style);
        player.sendPluginMessage(plugin, MessageChannel.MAIN_CHANNEL, out.toByteArray());
    }

    private void sendInteractiveColorMenu(Player player, String currentColor, String currentStyle) {
        Component header = MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ Chat Appearance ] ━━━━━━━━━━━━━━━</gradient>
            <gray>Active Color: <white><b>""" + currentColor + """
            </b></white> | Style: <white><b>""" + currentStyle + """
            </b></white></gray>
            <gray>Click any color below to select it:</gray>
            """);

        StringBuilder sb = new StringBuilder("\n<dark_gray>Colors:</dark_gray>\n");
        for (MessageColor c : MessageColor.values()) {
            if (!c.isGradient()) {
                sb.append("  <click:run_command:'/color ")
                        .append(c.getId())
                        .append("'><hover:show_text:'<gray>Click to select <color:")
                        .append(c.getHexOrTag())
                        .append(">")
                        .append(c.getDisplayName())
                        .append("</color></gray>'><color:")
                        .append(c.getHexOrTag())
                        .append(">[■ ")
                        .append(c.getDisplayName())
                        .append("]</color></hover></click> ");
            }
        }

        sb.append("\n\n<dark_gray>Gradients:</dark_gray>\n");
        for (MessageColor c : MessageColor.values()) {
            if (c.isGradient()) {
                sb.append("  <click:run_command:'/color ")
                        .append(c.getId())
                        .append("'><hover:show_text:'<gray>Click to activate ")
                        .append(c.getDisplayName())
                        .append("</gray>'>");

                if (c == MessageColor.RAINBOW) {
                    sb.append("<rainbow>[★ Rainbow]</rainbow>");
                } else {
                    sb.append(c.getHexOrTag()).append("[★ ").append(c.getDisplayName()).append("]</gradient>");
                }
                sb.append("</hover></click> ");
            }
        }

        sb.append("\n\n<dark_gray>Styles:</dark_gray>\n");
        sb.append("  <click:run_command:'/color style DEFAULT'><hover:show_text:'Normal text'><gray>[ Normal ]</gray></hover></click> ");
        sb.append("  <click:run_command:'/color style BOLD'><hover:show_text:'Bold text'><white><b>[ Bold ]</b></white></hover></click> ");
        sb.append("  <click:run_command:'/color style ITALIC'><hover:show_text:'Italic text'><white><i>[ Italic ]</i></white></hover></click> ");
        sb.append("  <click:run_command:'/color style UNDERLINED'><hover:show_text:'Underlined text'><white><u>[ Underlined ]</u></white></hover></click>\n");

        sb.append("\n<dark_gray>Tip: You can also use custom hex! <white>/color #FF55AA</white></dark_gray>\n");
        sb.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");

        player.sendMessage(header.append(MINI.deserialize(sb.toString())));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> list = new ArrayList<>();
            list.add("style");
            list.add("preview");
            for (MessageColor c : MessageColor.values()) {
                list.add(c.getId());
            }
            return list.stream().filter(s -> args.length == 0 || s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        } else if (args.length == 2 && "style".equalsIgnoreCase(args[0])) {
            return Arrays.stream(MessageStyle.values()).map(MessageStyle::getId).toList();
        }
        return List.of();
    }
}
