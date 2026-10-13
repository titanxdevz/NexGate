package com.nexgate.color;

import java.util.Arrays;
import java.util.Optional;

/**
 * Text styling options for player messages.
 */
public enum MessageStyle {
    DEFAULT("DEFAULT", "", "Default"),
    BOLD("BOLD", "<bold>", "Bold"),
    ITALIC("ITALIC", "<italic>", "Italic"),
    UNDERLINED("UNDERLINED", "<underlined>", "Underlined");

    private final String id;
    private final String tag;
    private final String displayName;

    MessageStyle(String id, String tag, String displayName) {
        this.id = id;
        this.tag = tag;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<MessageStyle> fromId(String id) {
        if (id == null) return Optional.empty();
        String clean = id.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(s -> s.id.equalsIgnoreCase(clean) || s.name().equalsIgnoreCase(clean))
                .findFirst();
    }
}
