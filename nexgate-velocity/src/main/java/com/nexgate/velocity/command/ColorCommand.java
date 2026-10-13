package com.nexgate.velocity.command;

import com.nexgate.color.ChatColorFormatter;
import com.nexgate.color.MessageColor;
import com.nexgate.color.MessageStyle;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Command allowing users to customize their in-game chat message color and style.
 * Features an interactive clickable chat palette and live preview.
 */
public class ColorCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final VelocityAuthService authService;
    private final AuthManager authManager;

    public ColorCommand(VelocityAuthService authService, AuthManager authManager) {
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
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>You must be logged in to change your message color!</red>"));
            return;
        }

        String[] args = invocation.arguments();
        Optional<AuthManager.UserSessionData> sessionOpt = authManager.getSession(player.getUniqueId());
        String currentColor = sessionOpt.map(AuthManager.UserSessionData::messageColor).orElse("WHITE");
        String currentStyle = sessionOpt.map(AuthManager.UserSessionData::messageStyle).orElse("DEFAULT");

        if (args.length == 0) {
            sendInteractiveColorMenu(player, currentColor, currentStyle);
            return;
        }

        String sub = args[0].toUpperCase();

        // Style submenu
        if ("STYLE".equalsIgnoreCase(sub)) {
            if (args.length < 2) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: <aqua>/color style <DEFAULT|BOLD|ITALIC|UNDERLINED></aqua></yellow>"));
                return;
            }
            Optional<MessageStyle> styleOpt = MessageStyle.fromId(args[1]);
            if (styleOpt.isEmpty()) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Unknown style! Choose: DEFAULT, BOLD, ITALIC, UNDERLINED</red>"));
                return;
            }
            authService.updateMessageSettings(player, currentColor, styleOpt.get().getId());
            return;
        }

        // Preview submenu
        if ("PREVIEW".equalsIgnoreCase(sub)) {
            if (args.length < 2) {
                player.sendMessage(MINI.deserialize(authService.getPrefix() + "<yellow>Usage: <aqua>/color preview <text></aqua></yellow>"));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            String preview = ChatColorFormatter.formatMessage(sb.toString().trim(), currentColor, currentStyle);
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<gray>Your message preview: </gray>" + preview));
            return;
        }

        // Direct color setting
        String targetColor = args[0];
        if (MessageColor.isValidHex(targetColor)) {
            authService.updateMessageSettings(player, targetColor.toUpperCase(), currentStyle);
            return;
        }

        Optional<MessageColor> colorOpt = MessageColor.fromId(targetColor);
        if (colorOpt.isPresent()) {
            authService.updateMessageSettings(player, colorOpt.get().getId(), currentStyle);
        } else {
            player.sendMessage(MINI.deserialize(authService.getPrefix() + "<red>Unknown color '" + targetColor + "'! Use /color to view the palette.</red>"));
        }
    }

    private void sendInteractiveColorMenu(Player player, String currentColor, String currentStyle) {
        Component header = MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━ [ Message Appearance ] ━━━━━━━━━━━━━━━</gradient>
            <gray>Current Color: <white><b>""" + currentColor + """
            </b></white> | Style: <white><b>""" + currentStyle + """
            </b></white></gray>
            <gray>Click any swatch below to set your message color:</gray>
            """);

        StringBuilder paletteBuilder = new StringBuilder("\n<dark_gray>Solid Colors:</dark_gray>\n");
        for (MessageColor c : MessageColor.values()) {
            if (!c.isGradient()) {
                paletteBuilder.append("  <click:run_command:'/color ")
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

        paletteBuilder.append("\n\n<dark_gray>Premium Gradients:</dark_gray>\n");
        for (MessageColor c : MessageColor.values()) {
            if (c.isGradient()) {
                paletteBuilder.append("  <click:run_command:'/color ")
                        .append(c.getId())
                        .append("'><hover:show_text:'<gray>Click to activate ")
                        .append(c.getDisplayName())
                        .append("</gray>'>");

                if (c == MessageColor.RAINBOW) {
                    paletteBuilder.append("<rainbow>[★ Rainbow]</rainbow>");
                } else {
                    paletteBuilder.append(c.getHexOrTag()).append("[★ ").append(c.getDisplayName()).append("]</gradient>");
                }
                paletteBuilder.append("</hover></click> ");
            }
        }

        paletteBuilder.append("\n\n<dark_gray>Text Styles:</dark_gray>\n");
        paletteBuilder.append("  <click:run_command:'/color style DEFAULT'><hover:show_text:'Reset text decoration'><gray>[ Normal ]</gray></hover></click> ");
        paletteBuilder.append("  <click:run_command:'/color style BOLD'><hover:show_text:'Toggle Bold text'><white><b>[ Bold ]</b></white></hover></click> ");
        paletteBuilder.append("  <click:run_command:'/color style ITALIC'><hover:show_text:'Toggle Italic text'><white><i>[ Italic ]</i></white></hover></click> ");
        paletteBuilder.append("  <click:run_command:'/color style UNDERLINED'><hover:show_text:'Toggle Underlined text'><white><u>[ Underlined ]</u></white></hover></click>\n");

        paletteBuilder.append("\n<dark_gray>Tip: You can also use custom hex! <white>/color #FF55AA</white></dark_gray>\n");
        paletteBuilder.append("<gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");

        Component menu = header.append(MINI.deserialize(paletteBuilder.toString()));
        player.sendMessage(menu);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
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
