package com.nexgate.velocity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexgate.crypto.HashingUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise configuration manager for Velocity plugin.
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Logger logger;
    private final Path dataDir;
    private final Path configFile;
    private VelocityConfig config;

    public ConfigManager(Logger logger, Path dataDir) {
        this.logger = logger;
        this.dataDir = dataDir;
        this.configFile = dataDir.resolve("velocity-config.json");
        this.config = new VelocityConfig();
    }

    public void load() {
        try {
            Files.createDirectories(dataDir);
            if (!Files.exists(configFile)) {
                saveDefault();
                return;
            }
            String content = Files.readString(configFile);
            config = GSON.fromJson(content, VelocityConfig.class);
            if (config == null) {
                config = new VelocityConfig();
                saveDefault();
            }
        } catch (IOException e) {
            logger.error("Failed to load config, falling back to defaults", e);
            config = new VelocityConfig();
        }
    }

    public void saveDefault() {
        try {
            String content = GSON.toJson(config != null ? config : new VelocityConfig());
            Files.writeString(configFile, content);
        } catch (IOException e) {
            logger.error("Failed to save default config", e);
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public VelocityConfig getConfig() {
        return config;
    }

    public String getHmacSecret() {
        return config.security.hmacSecret;
    }

    public List<String> getAuthServers() {
        return config.auth.authServers;
    }

    public String getTargetServer() {
        return config.auth.targetServer;
    }

    public long getLoginTimeoutSeconds() {
        return config.auth.loginTimeoutSeconds;
    }

    public long getSessionTimeoutMinutes() {
        return config.auth.sessionTimeoutMinutes;
    }

    public boolean isSessionStrictIp() {
        return config.auth.sessionStrictIp;
    }

    public boolean isShowBossBar() {
        return config.auth.showBossBar;
    }

    public boolean isShowTitle() {
        return config.auth.showTitle;
    }

    public boolean isPlaySounds() {
        return config.auth.playSounds;
    }

    public int getRateLimitIpMax() {
        return config.security.rateLimitIp.max;
    }

    public long getRateLimitIpWindow() {
        return config.security.rateLimitIp.windowSeconds;
    }

    public int getRateLimitAccountMax() {
        return config.security.rateLimitAccount.max;
    }

    public long getRateLimitAccountWindow() {
        return config.security.rateLimitAccount.windowSeconds;
    }

    public int getMaxAccountsPerIp() {
        return config.security.maxAccountsPerIp;
    }

    public long getLockoutDurationSeconds() {
        return config.security.lockoutDurationSeconds;
    }

    public boolean isRequireAuth() {
        return config.auth.requireAuth;
    }

    public boolean isPremiumEnabled() {
        return config.premium.enabled;
    }

    public boolean isPremiumAutoDetect() {
        return config.premium.autoDetect;
    }

    public boolean isForceOnlineModeForPremium() {
        return config.premium.forceOnlineModeForPremium;
    }

    // Config POJO structure
    public static class VelocityConfig {
        public Auth auth = new Auth();
        public Security security = new Security();
        public Premium premium = new Premium();
        public Database database = new Database();
        public Messages messages = new Messages();
    }

    public static class Auth {
        public List<String> authServers = new ArrayList<>(List.of("auth"));
        public String targetServer = "lobby";
        public long loginTimeoutSeconds = 60;
        public long sessionTimeoutMinutes = 120;
        public boolean sessionStrictIp = false;
        public boolean requireAuth = true;
        public boolean showBossBar = true;
        public boolean showTitle = true;
        public boolean playSounds = true;
    }

    public static class Security {
        public String hmacSecret = HashingUtils.generateSecureToken(32);
        public RateLimit rateLimitIp = new RateLimit(5, 60);
        public RateLimit rateLimitAccount = new RateLimit(5, 300);
        public int maxAccountsPerIp = 3;
        public long lockoutDurationSeconds = 300;
        public int passwordMinLength = 6;
        public int passwordMaxLength = 64;
        public boolean requireNumbers = false;
        public boolean requireSpecialChars = false;
    }

    public static class RateLimit {
        public int max;
        public long windowSeconds;

        public RateLimit() {
        }

        public RateLimit(int max, long windowSeconds) {
            this.max = max;
            this.windowSeconds = windowSeconds;
        }
    }

    public static class Premium {
        public boolean enabled = true;
        public boolean autoDetect = true;
        public boolean forceOnlineModeForPremium = true;
        public int mojangTimeoutSeconds = 3;
        public int cacheDurationMinutes = 60;
    }

    public static class Database {
        public String type = "sqlite"; // "sqlite" or "mysql"
        public String sqliteFile = "plugins/nexgate/nexgate.db";
        public String host = "localhost";
        public int port = 3306;
        public String database = "nexgate";
        public String username = "nexgate";
        public String password = "password";
    }

    public static class Messages {
        public String prefix = "<gradient:#00C9FF:#92FE9D><bold>NexGate</bold></gradient> <dark_gray>»</dark_gray> ";
        public String registerPrompt = "<yellow>Welcome! Please register using <gradient:#00C9FF:#92FE9D><bold>/register <password> <confirm></bold></gradient></yellow>";
        public String loginPrompt = "<yellow>Welcome back! Please login using <gradient:#00C9FF:#92FE9D><bold>/login <password></bold></gradient></yellow>";
        public String alreadyLoggedIn = "<red>You are already logged in!</red>";
        public String registerSuccess = "<green>✔ Registration successful! Welcome to the server.</green>";
        public String loginSuccess = "<green>✔ Login successful! Welcome back.</green>";
        public String sessionRestored = "<green>✔ Welcome back, <aqua>{player}</aqua>! Session restored from <dark_aqua>{ip}</dark_aqua>.</green>";
        public String premiumAutoLogin = "<gradient:#00C9FF:#92FE9D><bold>✔ Mojang Premium verified!</bold></gradient> <gray>Logged in securely via official session server.</gray>";
        public String wrongPassword = "<red>Incorrect password! Attempts remaining: {attempts}</red>";
        public String accountLocked = "<red>Too many failed attempts! Account locked for {seconds}s.</red>";
        public String ipRateLimited = "<red>Too many connection attempts from this IP. Please wait.</red>";
        public String maxAccountsReached = "<red>Maximum registered accounts reached for your IP address ({max}).</red>";
        public String passwordMismatch = "<red>Passwords do not match!</red>";
        public String passwordTooWeak = "<red>Password rejected: {reason}</red>";
        public String authTimeout = "<red>Authentication timed out. Please rejoin.</red>";
    }
}
