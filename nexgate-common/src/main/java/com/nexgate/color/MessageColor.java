package com.nexgate.color;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Modern color presets and gradient styles for player messages.
 */
public enum MessageColor {
    // Solid Vibrant Colors
    WHITE("WHITE", "#FFFFFF", "White", false),
    GRAY("GRAY", "#AAAAAA", "Gray", false),
    DARK_GRAY("DARK_GRAY", "#555555", "Dark Gray", false),
    RED("RED", "#FF5555", "Red", false),
    CRIMSON("CRIMSON", "#DC143C", "Crimson", false),
    CORAL("CORAL", "#FF7F50", "Coral", false),
    GOLD("GOLD", "#FFAA00", "Gold", false),
    YELLOW("YELLOW", "#FFFF55", "Yellow", false),
    GREEN("GREEN", "#55FF55", "Green", false),
    EMERALD("EMERALD", "#2ECC71", "Emerald", false),
    MINT("MINT", "#1ABC9C", "Mint", false),
    AQUA("AQUA", "#55FFFF", "Aqua", false),
    CYAN("CYAN", "#00E5FF", "Cyan", false),
    BLUE("BLUE", "#5555FF", "Blue", false),
    LIGHT_PURPLE("LIGHT_PURPLE", "#FF55FF", "Light Purple", false),
    PURPLE("PURPLE", "#AA00AA", "Purple", false),
    PINK("PINK", "#FF69B4", "Pink", false),
    ROSE("ROSE", "#FF1493", "Rose", false),

    // Premium Gradients
    SUNSET("SUNSET", "<gradient:#FF512F:#DD2476>", "Sunset Gradient", true),
    CYBERPUNK("CYBERPUNK", "<gradient:#00F260:#0575E6>", "Cyberpunk Gradient", true),
    AURORA("AURORA", "<gradient:#7F00FF:#E100FF>", "Aurora Gradient", true),
    OCEAN("OCEAN", "<gradient:#2B5876:#4E4376>", "Ocean Gradient", true),
    NEON("NEON", "<gradient:#12C2E9:#C471ED:#F64F59>", "Neon Gradient", true),
    FLAME("FLAME", "<gradient:#F12711:#F5AF19>", "Flame Gradient", true),
    RAINBOW("RAINBOW", "<rainbow>", "Rainbow Spectrum", true);

    private final String id;
    private final String hexOrTag;
    private final String displayName;
    private final boolean gradient;

    MessageColor(String id, String hexOrTag, String displayName, boolean gradient) {
        this.id = id;
        this.hexOrTag = hexOrTag;
        this.displayName = displayName;
        this.gradient = gradient;
    }

    public String getId() {
        return id;
    }

    public String getHexOrTag() {
        return hexOrTag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isGradient() {
        return gradient;
    }

    public static Optional<MessageColor> fromId(String id) {
        if (id == null) return Optional.empty();
        String clean = id.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(c -> c.id.equalsIgnoreCase(clean) || c.name().equalsIgnoreCase(clean))
                .findFirst();
    }

    public static List<MessageColor> getAllColors() {
        return List.of(values());
    }

    /**
     * Determines if a raw input is a valid custom hex string like #RRGGBB.
     */
    public static boolean isValidHex(String hex) {
        if (hex == null) return false;
        return hex.matches("^#([A-Fa-f0-9]{6})$");
    }
}
