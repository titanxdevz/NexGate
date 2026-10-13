package com.nexgate.velocity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration manager for Velocity plugin.
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
            logger.error("Failed to load config", e);
            config = new VelocityConfig();
        }
    }
    
    private void saveDefault() {
        try {
            String content = GSON.toJson(new VelocityConfig());
            Files.writeString(configFile, content);
        } catch (IOException e) {
            logger.error("Failed to save default config", e);
        }
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
    
    public boolean isRequireAuth() {
        return config.auth.requireAuth;
    }
    
    // Config POJO classes
    public static class VelocityConfig {
        public Auth auth = new Auth();
        public Security security = new Security();
        public Database database = new Database();
    }
    
    public static class Auth {
        public List<String> authServers = new ArrayList<>(List.of("auth"));
        public String targetServer = "lobby";
        public long loginTimeoutSeconds = 120;
        public long sessionTimeoutMinutes = 60;
        public boolean requireAuth = true;
    }
    
    public static class Security {
        public String hmacSecret = com.nexgate.crypto.HashingUtils.generateSecureToken(32);
        public RateLimit rateLimitIp = new RateLimit(5, 60);
        public RateLimit rateLimitAccount = new RateLimit(10, 300);
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
    
    public static class Database {
        public String type = "mysql";
        public String host = "localhost";
        public int port = 3306;
        public String database = "nexgate";
        public String username = "nexgate";
        public String password = "password";
    }
}
```