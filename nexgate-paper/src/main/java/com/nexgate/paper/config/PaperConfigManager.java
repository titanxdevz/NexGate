package com.nexgate.paper.config;

import com.nexgate.paper.NexGatePaperPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration manager for Paper backend companion.
 */
public class PaperConfigManager {
    private final NexGatePaperPlugin plugin;
    private String hmacSecret;
    private boolean strictGating;
    private boolean applyBlindness;
    private boolean applySlowness;
    private boolean hideUnauthenticated;
    private boolean allowChatColorFormatting;
    private String chatFormat;

    public PaperConfigManager(NexGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.hmacSecret = config.getString("hmac-secret", "");
        this.strictGating = config.getBoolean("strict-gating", true);
        this.applyBlindness = config.getBoolean("apply-blindness", true);
        this.applySlowness = config.getBoolean("apply-slowness", true);
        this.hideUnauthenticated = config.getBoolean("hide-unauthenticated-players", true);
        this.allowChatColorFormatting = config.getBoolean("allow-chat-color-formatting", true);
        this.chatFormat = config.getString("chat-format", "<gray><player></gray> <dark_gray>»</dark_gray> <message>");

        if (hmacSecret.isEmpty()) {
            plugin.getSLF4JLogger().warn("hmac-secret is not configured in config.yml! Communication from Velocity will be rejected.");
        }
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public boolean isStrictGating() {
        return strictGating;
    }

    public boolean isApplyBlindness() {
        return applyBlindness;
    }

    public boolean isApplySlowness() {
        return applySlowness;
    }

    public boolean isHideUnauthenticated() {
        return hideUnauthenticated;
    }

    public boolean isAllowChatColorFormatting() {
        return allowChatColorFormatting;
    }

    public String getChatFormat() {
        return chatFormat;
    }
}
