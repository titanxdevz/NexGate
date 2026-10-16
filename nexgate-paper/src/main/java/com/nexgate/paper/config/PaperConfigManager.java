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
    private boolean limboCageEnabled;
    private int limboPlatformY;
    private int limboRadius;
    private boolean lockTimeNoon;
    private boolean lockWeatherClear;
    private boolean disableMobSpawning;
    private boolean suppressJoinQuitMessages;

    // Configurable spawn and login area
    private boolean spawnEnabled;
    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
    private boolean spawnTeleportOnJoin;
    private boolean spawnTeleportOnLogin;

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

        this.limboCageEnabled = config.getBoolean("limbo-cage.enabled", false);
        this.limboPlatformY = config.getInt("limbo-cage.platform-y", 100);
        this.limboRadius = config.getInt("limbo-cage.radius", 3);
        this.lockTimeNoon = config.getBoolean("limbo-cage.lock-time-noon", true);
        this.lockWeatherClear = config.getBoolean("limbo-cage.lock-weather-clear", true);
        this.disableMobSpawning = config.getBoolean("limbo-cage.disable-mob-spawning", true);
        this.suppressJoinQuitMessages = config.getBoolean("limbo-cage.suppress-join-quit-messages", true);

        this.spawnEnabled = config.getBoolean("spawn.enabled", true);
        this.spawnWorld = config.getString("spawn.world", "world");
        this.spawnX = config.getDouble("spawn.x", 0.5);
        this.spawnY = config.getDouble("spawn.y", 100.0);
        this.spawnZ = config.getDouble("spawn.z", 0.5);
        this.spawnYaw = (float) config.getDouble("spawn.yaw", 0.0);
        this.spawnPitch = (float) config.getDouble("spawn.pitch", 0.0);
        this.spawnTeleportOnJoin = config.getBoolean("spawn.teleport-on-join", true);
        this.spawnTeleportOnLogin = config.getBoolean("spawn.teleport-on-login", true);

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

    public boolean isLimboCageEnabled() {
        return limboCageEnabled;
    }

    public int getLimboPlatformY() {
        return limboPlatformY;
    }

    public int getLimboRadius() {
        return limboRadius;
    }

    public boolean isLockTimeNoon() {
        return lockTimeNoon;
    }

    public boolean isLockWeatherClear() {
        return lockWeatherClear;
    }

    public boolean isDisableMobSpawning() {
        return disableMobSpawning;
    }

    public boolean isSuppressJoinQuitMessages() {
        return suppressJoinQuitMessages;
    }

    public boolean isSpawnEnabled() {
        return spawnEnabled;
    }

    public String getSpawnWorld() {
        return spawnWorld;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public boolean isSpawnTeleportOnJoin() {
        return spawnTeleportOnJoin;
    }

    public boolean isSpawnTeleportOnLogin() {
        return spawnTeleportOnLogin;
    }

    public void setSpawn(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.spawnEnabled = true;
        this.spawnWorld = loc.getWorld().getName();
        this.spawnX = loc.getX();
        this.spawnY = loc.getY();
        this.spawnZ = loc.getZ();
        this.spawnYaw = loc.getYaw();
        this.spawnPitch = loc.getPitch();

        FileConfiguration config = plugin.getConfig();
        config.set("spawn.enabled", true);
        config.set("spawn.world", this.spawnWorld);
        config.set("spawn.x", this.spawnX);
        config.set("spawn.y", this.spawnY);
        config.set("spawn.z", this.spawnZ);
        config.set("spawn.yaw", (double) this.spawnYaw);
        config.set("spawn.pitch", (double) this.spawnPitch);
        plugin.saveConfig();

        loc.getWorld().setSpawnLocation((int) this.spawnX, (int) this.spawnY, (int) this.spawnZ, this.spawnYaw);
    }
}
