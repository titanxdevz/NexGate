package com.nexgate.color;

import java.util.Optional;

/**
 * Utility for formatting chat and messages with player color/style preferences.
 */
public class ChatColorFormatter {

    private ChatColorFormatter() {
    }

    /**
     * Formats raw text into a MiniMessage formatted string with the given color and style.
     * Sanitizes input to prevent tag injection while preserving player text.
     */
    public static String formatMessage(String rawText, String colorKey, String styleKey) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        // Sanitize raw text to prevent arbitrary tag injection like <click:run_command:...>
        String sanitized = sanitizeRaw(rawText);

        String openTag = "";
        String closeTag = "";

        // Style
        Optional<MessageStyle> styleOpt = MessageStyle.fromId(styleKey);
        if (styleOpt.isPresent()) {
            MessageStyle style = styleOpt.get();
            if (style == MessageStyle.BOLD) {
                openTag += "<bold>";
                closeTag = "</bold>" + closeTag;
            } else if (style == MessageStyle.ITALIC) {
                openTag += "<italic>";
                closeTag = "</italic>" + closeTag;
            } else if (style == MessageStyle.UNDERLINED) {
                openTag += "<underlined>";
                closeTag = "</underlined>" + closeTag;
            }
        }

        // Color
        if (colorKey != null && MessageColor.isValidHex(colorKey)) {
            openTag = "<color:" + colorKey + ">" + openTag;
            closeTag = closeTag + "</color>";
        } else {
            Optional<MessageColor> colorOpt = MessageColor.fromId(colorKey);
            if (colorOpt.isPresent()) {
                MessageColor color = colorOpt.get();
                if (color.isGradient()) {
                    if (color == MessageColor.RAINBOW) {
                        openTag = "<rainbow>" + openTag;
                        closeTag = closeTag + "</rainbow>";
                    } else {
                        // Extract gradient tag e.g. <gradient:#FF512F:#DD2476>
                        openTag = color.getHexOrTag() + openTag;
                        closeTag = closeTag + "</gradient>";
                    }
                } else {
                    openTag = "<color:" + color.getHexOrTag() + ">" + openTag;
                    closeTag = closeTag + "</color>";
                }
            } else {
                openTag = "<white>" + openTag;
                closeTag = closeTag + "</white>";
            }
        }

        return openTag + sanitized + closeTag;
    }

    /**
     * Sanitizes user input so they cannot accidentally or maliciously inject MiniMessage markup.
     */
    public static String sanitizeRaw(String input) {
        if (input == null) return "";
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    /**
     * Returns a preview representation of a color preset for menus and interactive lists.
     */
    public static String getPreview(MessageColor color) {
        if (color.isGradient()) {
            if (color == MessageColor.RAINBOW) {
                return "<rainbow>" + color.getDisplayName() + "</rainbow>";
            }
            return color.getHexOrTag() + color.getDisplayName() + "</gradient>";
        }
        return "<color:" + color.getHexOrTag() + ">" + color.getDisplayName() + "</color>";
    }
}
