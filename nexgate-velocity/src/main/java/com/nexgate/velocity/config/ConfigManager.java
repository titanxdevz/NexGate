package com.nexgate.velocity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexgate.crypto.HashingUtils;
import com.nexgate.model.AuthMode;
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
            } else {
                sanitizeConfig();
            }
        } catch (IOException e) {
            logger.error("Failed to load config, falling back to defaults", e);
            config = new VelocityConfig();
        }
    }

    private void sanitizeConfig() {
        if (config.security == null) {
            config.security = new Security();
        }
        if (config.security.maxFailedKicksBeforeIpCooldown <= 0) {
            config.security.maxFailedKicksBeforeIpCooldown = 3;
        }
        if (config.security.ipCooldownMinutes <= 0) {
            config.security.ipCooldownMinutes = 5;
        }
        if (config.messages == null) {
            config.messages = new Messages();
        }
        if (config.messages.ipCooldownKick == null || config.messages.ipCooldownKick.isBlank()) {
            config.messages.ipCooldownKick = "<gold><bold>NEXGATE SECURITY</bold></gold>\n\n<red>IP Temporarily Cooled Down</red>\n<gray>Too many failed login attempts originated from your network.</gray>\n<gray>Please wait <yellow><bold>{time}</bold></yellow> before reconnecting.</gray>";
        }
        if (config.messages.maxFailedAttemptsKick == null || config.messages.maxFailedAttemptsKick.isBlank()) {
            config.messages.maxFailedAttemptsKick = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Too many incorrect login attempts!</red>\n<gray>You have been disconnected to protect account security.</gray>\n<gray>Please reconnect and verify your password.</gray>";
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

    public AuthMode getAuthMode() {
        return AuthMode.fromString(config.auth.authMode);
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

    public boolean isIpCooldownEnabled() {
        return config != null && config.security != null && config.security.ipCooldownEnabled;
    }

    public int getMaxFailedKicksBeforeIpCooldown() {
        return (config != null && config.security != null && config.security.maxFailedKicksBeforeIpCooldown > 0)
                ? config.security.maxFailedKicksBeforeIpCooldown : 3;
    }

    public long getIpCooldownMinutes() {
        return (config != null && config.security != null && config.security.ipCooldownMinutes > 0)
                ? config.security.ipCooldownMinutes : 5;
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

    public boolean isBedrockAutoLogin() {
        return config.bedrock != null && config.bedrock.autoLogin;
    }

    // Config POJO structure
    public static class VelocityConfig {
        public Auth auth = new Auth();
        public BedrockConfig bedrock = new BedrockConfig();
        public Security security = new Security();
        public Premium premium = new Premium();
        public AntiBotConfig antibot = new AntiBotConfig();
        public TotpConfig totp = new TotpConfig();
        public Argon2Config argon2 = new Argon2Config();
        public Database database = new Database();
        public Messages messages = new Messages();
    }

    public static class BedrockConfig {
        public boolean autoLogin = true;
        public boolean allowFloodgatePrefix = true;
        public String verifiedWelcomeMessage = "<green>Bedrock Edition Verified!</green> <gray>Logged in automatically via Xbox Live identity.</gray>";
    }

    public static class Auth {
        public String authMode = "HYBRID"; // ONLINE_ONLY, OFFLINE_AUTH, HYBRID
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
        public boolean ipCooldownEnabled = true;
        public int maxFailedKicksBeforeIpCooldown = 3;
        public long ipCooldownMinutes = 5;
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

    public static class AntiBotConfig {
        public boolean enabled = true;
        public String mode = "AUTO"; // AUTO, ON, OFF
        public int globalRateLimit = 40;
        public int perIpRateLimit = 4;
        public int maxConcurrentPerIp = 3;
        public long fastReconnectThresholdMs = 1200;
        public int mitigationThreshold = 15;
        public long mitigationDurationSeconds = 60;
        public List<String> blacklistedCidrs = new ArrayList<>();
        public List<String> whitelistedCidrs = new ArrayList<>();
    }

    public static class TotpConfig {
        public boolean enabled = true;
        public String issuer = "NexGate Network";
        public int windowSteps = 1;
        public int maxAttempts = 5;
        public long lockoutDurationSeconds = 300;
    }

    public static class Argon2Config {
        public int maxConcurrent = 2;
        public int iterations = 3;
        public int memoryKb = 65536;
        public int parallelism = 1;
    }

    public static class Database {
        public String type = "sqlite"; // "sqlite", "mysql", or "postgresql"
        public String sqliteFile = "plugins/nexgate/nexgate.db";
        public String host = "localhost";
        public int port = 3306;
        public String database = "nexgate";
        public String username = "nexgate";
        public String password = "password";
    }

    public static class Messages {
        public String prefix = "<gold><bold>NEXGATE</bold></gold> <white>→</white> ";
        public String registerPrompt = "<white>Please, register using <gold><click:suggest_command:'/register '>/register \\<password> \\<repeat password></click></gold></white>";
        public String loginPrompt = "<white>Please use <gold><click:suggest_command:'/login '>/login \\<password></click></gold>, you have <gold><bold>{attempts}</bold></gold> attempts.</white>";
        public String alreadyLoggedIn = "<yellow>You are already logged in!</yellow>";
        public String registerSuccess = "<green>Registration successful! Welcome to the server.</green>";
        public String loginSuccess = "<green>Login successful! Welcome back.</green>";
        public String sessionRestored = "<green>Welcome back, <aqua>{player}</aqua>! Session restored.</green>";
        public String premiumAutoLogin = "<gold><bold>Mojang Premium verified!</bold></gold> <gray>Logged in securely via official session server.</gray>";
        public String wrongPassword = "<red>Incorrect password! Attempts remaining: <yellow><bold>{attempts}</bold></yellow></red>";
        public String accountLocked = "<red>Too many failed attempts! Account locked for {seconds}s.</red>";
        public String maxFailedAttemptsKick = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Too many incorrect login attempts!</red>\n<gray>You have been disconnected to protect account security.</gray>\n<gray>Please reconnect and verify your password.</gray>";
        public String ipCooldownKick = "<gold><bold>NEXGATE SECURITY</bold></gold>\n\n<red>IP Temporarily Cooled Down</red>\n<gray>Too many failed login attempts originated from your network.</gray>\n<gray>Please wait <yellow><bold>{time}</bold></yellow> before reconnecting.</gray>";
        public String ipRateLimited = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Connection Rate Limited</red>\n<gray>Too many join attempts from your IP address. Please wait a moment.</gray>";
        public String maxAccountsReached = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Account Limit Reached</red>\n<gray>Maximum registered accounts reached for your IP address ({max}).</gray>";
        public String passwordMismatch = "<red>Passwords do not match!</red>";
        public String passwordTooWeak = "<red>Password rejected: {reason}</red>";
        public String authTimeout = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Authentication timed out!</red>\n<gray>You took too long to authenticate.</gray>\n<gray>Please reconnect and log in with <gold>/login \\<password></gold></gray>";
        public String authModeOnlineOnly = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Official Account Required</red>\n<gray>This server requires a verified Minecraft Java Edition account.</gray>";
        public String premiumImpersonationKick = "<gold><bold>NEXGATE SECURITY</bold></gold>\n\n<red>Premium Account Protection</red>\n<gray>This username belongs to a verified official Minecraft account.</gray>\n<gray>Please launch Minecraft using your official paid launcher.</gray>";
        public String mojangUnavailable = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Mojang Service Unreachable</red>\n<gray>Official session servers are currently unreachable.</gray>\n<gray>Please try reconnecting in a few moments.</gray>";
        public String pendingPremiumPrompt = "<yellow>Pending Premium verification! Please join using your official paid Minecraft launcher.</yellow>";
        public String totpPrompt = "<yellow>Two-factor authentication required. Use <gold><bold>/2fa verify \\<code></bold></gold></yellow>";
        public String totpRequiredAction = "<red>You must enter your 2FA code before continuing! Use /2fa verify \\<code></red>";
        public String totpCodeInvalid = "<red>Invalid two-factor authentication code! Attempts remaining: <yellow><bold>{attempts}</bold></yellow></red>";
        public String totpLocked = "<red>Too many invalid 2FA attempts! Account locked for {seconds}s.</red>";
        public String totpFailedAttemptsKick = "<gold><bold>NEXGATE NETWORK</bold></gold>\n\n<red>Too many invalid 2FA attempts!</red>\n<gray>You have been disconnected to protect account security.</gray>";
    }
}
