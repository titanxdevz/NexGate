package com.nexgate.velocity.auth;

import com.nexgate.color.ChatColorFormatter;
import com.nexgate.color.MessageColor;
import com.nexgate.color.MessageStyle;
import com.nexgate.crypto.HashingUtils;
import com.nexgate.db.DatabaseManager;
import com.nexgate.model.SessionRecord;
import com.nexgate.model.UserAuth;
import com.nexgate.premium.MojangProfileChecker;
import com.nexgate.ratelimit.RateLimiter;
import com.nexgate.security.PasswordValidator;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.messaging.PluginMessageManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.File;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Core authentication service handling registration, login, session restore,
 * premium verification, and security rate limits.
 */
public class VelocityAuthService {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final AuthManager authManager;
    private final PluginMessageManager messageManager;

    private final DatabaseManager databaseManager;
    private final RateLimiter ipRateLimiter;
    private final RateLimiter accountRateLimiter;
    private final PasswordValidator passwordValidator;
    private final MojangProfileChecker mojangChecker;

    public VelocityAuthService(ProxyServer server, Logger logger, ConfigManager configManager,
                               AuthManager authManager, PluginMessageManager messageManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.authManager = authManager;
        this.messageManager = messageManager;

        ConfigManager.VelocityConfig cfg = configManager.getConfig();
        File dbFile = configManager.getDataDir().resolve("nexgate.db").toFile();

        this.databaseManager = new DatabaseManager(
                cfg.database.type,
                cfg.database.host,
                cfg.database.port,
                cfg.database.database,
                cfg.database.username,
                cfg.database.password,
                dbFile
        );

        this.ipRateLimiter = new RateLimiter(
                configManager.getRateLimitIpMax(),
                configManager.getRateLimitIpWindow()
        );

        this.accountRateLimiter = new RateLimiter(
                configManager.getRateLimitAccountMax(),
                configManager.getRateLimitAccountWindow()
        );

        this.passwordValidator = new PasswordValidator(
                cfg.security.passwordMinLength,
                cfg.security.passwordMaxLength,
                cfg.security.requireNumbers,
                false,
                cfg.security.requireSpecialChars
        );

        this.mojangChecker = new MojangProfileChecker(
                Duration.ofSeconds(cfg.premium.mojangTimeoutSeconds),
                Duration.ofMinutes(cfg.premium.cacheDurationMinutes)
        );
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RateLimiter getIpRateLimiter() {
        return ipRateLimiter;
    }

    public MojangProfileChecker getMojangChecker() {
        return mojangChecker;
    }

    public String getPrefix() {
        return configManager.getConfig().messages.prefix;
    }

    /**
     * Attempts player registration.
     */
    public CompletableFuture<Void> handleRegister(Player player, String password, String confirmPassword) {
        if (authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.alreadyLoggedIn));
            return CompletableFuture.completedFuture(null);
        }

        if (!password.equals(confirmPassword)) {
            playErrorSound(player);
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.passwordMismatch));
            return CompletableFuture.completedFuture(null);
        }

        PasswordValidator.ValidationResult val = passwordValidator.validate(password, player.getUsername());
        if (!val.valid()) {
            playErrorSound(player);
            player.sendMessage(MINI.deserialize(getPrefix() +
                    configManager.getConfig().messages.passwordTooWeak.replace("{reason}", val.message())));
            return CompletableFuture.completedFuture(null);
        }

        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return databaseManager.countAccountsByIp(ip).thenCompose(count -> {
            int maxAllowed = configManager.getMaxAccountsPerIp();
            if (count >= maxAllowed) {
                playErrorSound(player);
                player.sendMessage(MINI.deserialize(getPrefix() +
                        configManager.getConfig().messages.maxAccountsReached.replace("{max}", String.valueOf(maxAllowed))));
                return CompletableFuture.completedFuture(null);
            }

            return databaseManager.getUser(player.getUsername()).thenAccept(existing -> {
                if (existing.isPresent()) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() + "<red>This account is already registered! Use /login <password></red>"));
                    return;
                }

                // Hash password using Argon2id
                String hash = HashingUtils.hashPassword(password);
                UserAuth newUser = UserAuth.builder()
                        .username(player.getUsername())
                        .usernameLower(player.getUsername().toLowerCase())
                        .uuid(player.getUniqueId())
                        .passwordHash(hash)
                        .ip(ip)
                        .lastIp(ip)
                        .regDate(System.currentTimeMillis())
                        .lastLogin(System.currentTimeMillis())
                        .premium(false)
                        .messageColor("WHITE")
                        .messageStyle("DEFAULT")
                        .build();

                databaseManager.registerUser(newUser).thenAccept(success -> {
                    if (success) {
                        authManager.authenticate(player, newUser, false);
                        player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.registerSuccess));

                        // Create session
                        createAndSaveSession(player);

                        // Sync to current backend server
                        syncLoginToBackend(player, newUser.getMessageColor(), newUser.getMessageStyle());

                        // Forward to target server if currently on auth server
                        maybeForwardToTarget(player);
                    } else {
                        playErrorSound(player);
                        player.sendMessage(MINI.deserialize(getPrefix() + "<red>Failed to register. Please try again later.</red>"));
                    }
                });
            });
        });
    }

    /**
     * Attempts player login with password.
     */
    public CompletableFuture<Void> handleLogin(Player player, String password) {
        if (authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.alreadyLoggedIn));
            return CompletableFuture.completedFuture(null);
        }

        String username = player.getUsername();
        String userKey = username.toLowerCase();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Check lockouts
        if (accountRateLimiter.isLockedOut(userKey)) {
            long remaining = accountRateLimiter.getRemainingLockTimeSeconds(userKey);
            playErrorSound(player);
            player.sendMessage(MINI.deserialize(getPrefix() +
                    configManager.getConfig().messages.accountLocked.replace("{seconds}", String.valueOf(remaining))));
            return CompletableFuture.completedFuture(null);
        }

        return databaseManager.getUser(username).thenAccept(userOpt -> {
            if (userOpt.isEmpty()) {
                playErrorSound(player);
                player.sendMessage(MINI.deserialize(getPrefix() + "<red>You are not registered yet! Use /register <pass> <confirm></red>"));
                return;
            }

            UserAuth user = userOpt.get();
            boolean correct = HashingUtils.verifyPassword(password, user.getPasswordHash());

            if (!correct) {
                playErrorSound(player);
                boolean acquired = accountRateLimiter.tryAcquire(userKey);
                if (!acquired) {
                    accountRateLimiter.lockout(userKey, configManager.getLockoutDurationSeconds());
                    long secs = configManager.getLockoutDurationSeconds();
                    player.sendMessage(MINI.deserialize(getPrefix() +
                            configManager.getConfig().messages.accountLocked.replace("{seconds}", String.valueOf(secs))));
                } else {
                    int remaining = configManager.getRateLimitAccountMax() - accountRateLimiter.getAttempts(userKey);
                    player.sendMessage(MINI.deserialize(getPrefix() +
                            configManager.getConfig().messages.wrongPassword.replace("{attempts}", String.valueOf(Math.max(0, remaining)))));
                }
                return;
            }

            // Successful authentication
            accountRateLimiter.reset(userKey);
            ipRateLimiter.reset(ip);

            // Update last login
            databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());

            authManager.authenticate(player, user, user.isPremium());
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.loginSuccess));

            // Create and persist session
            createAndSaveSession(player);

            // Send perform.login to backend
            syncLoginToBackend(player, user.getMessageColor(), user.getMessageStyle());

            // Forward to lobby/survival if currently on auth server
            maybeForwardToTarget(player);
        });
    }

    /**
     * Checks and restores session on join if valid.
     */
    public CompletableFuture<Boolean> tryRestoreSession(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return databaseManager.getSession(uuid).thenCompose(sessOpt -> {
            if (sessOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }

            SessionRecord session = sessOpt.get();
            if (!session.isValid(ip, configManager.isSessionStrictIp())) {
                databaseManager.invalidateSession(uuid);
                return CompletableFuture.completedFuture(false);
            }

            // Valid session found! Load user details and authenticate
            return databaseManager.getUser(player.getUsername()).thenApply(userOpt -> {
                UserAuth user = userOpt.orElse(null);
                authManager.authenticate(player, user, user != null && user.isPremium());

                player.sendMessage(MINI.deserialize(getPrefix() +
                        configManager.getConfig().messages.sessionRestored
                                .replace("{player}", player.getUsername())
                                .replace("{ip}", ip)));

                String color = user != null ? user.getMessageColor() : "WHITE";
                String style = user != null ? user.getMessageStyle() : "DEFAULT";
                syncLoginToBackend(player, color, style);

                maybeForwardToTarget(player);
                return true;
            });
        });
    }

    /**
     * Auto-authenticates verified Mojang premium players.
     */
    public CompletableFuture<Void> handlePremiumAutoLogin(Player player) {
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return databaseManager.getUser(username).thenAccept(userOpt -> {
            UserAuth user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
                databaseManager.updatePremiumStatus(username, true, player.getUniqueId());
                databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());
            } else {
                // Register premium user automatically
                user = UserAuth.builder()
                        .username(username)
                        .usernameLower(username.toLowerCase())
                        .uuid(player.getUniqueId())
                        .passwordHash(HashingUtils.hashPassword(HashingUtils.generateSecureToken(32)))
                        .ip(ip)
                        .lastIp(ip)
                        .regDate(System.currentTimeMillis())
                        .lastLogin(System.currentTimeMillis())
                        .premium(true)
                        .premiumUuid(player.getUniqueId())
                        .messageColor("WHITE")
                        .messageStyle("DEFAULT")
                        .build();
                databaseManager.registerUser(user);
            }

            authManager.authenticate(player, user, true);
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.premiumAutoLogin));

            createAndSaveSession(player);
            syncLoginToBackend(player, user.getMessageColor(), user.getMessageStyle());
            maybeForwardToTarget(player);
        });
    }

    /**
     * Updates player message color and style preference.
     */
    public CompletableFuture<Void> updateMessageSettings(Player player, String colorName, String styleName) {
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        return databaseManager.updateMessageColor(username, colorName, styleName).thenRun(() -> {
            authManager.updatePlayerSettings(uuid, colorName, styleName);

            // Sync with backend server
            player.getCurrentServer().ifPresent(serverConn -> {
                messageManager.sendSyncSettings(serverConn.getServer(), username, colorName, styleName);
            });

            String preview = ChatColorFormatter.formatMessage("This is how your chat message will look!", colorName, styleName);
            player.sendMessage(MINI.deserialize(getPrefix() + "<green>Message appearance updated!</green>"));
            player.sendMessage(MINI.deserialize(getPrefix() + "<gray>Preview: </gray>" + preview));
        });
    }

    private void createAndSaveSession(Player player) {
        String token = HashingUtils.generateSecureToken(24);
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long expires = now + TimeUnit.MINUTES.toMillis(configManager.getSessionTimeoutMinutes());

        SessionRecord session = new SessionRecord(player.getUniqueId(), player.getUsername().toLowerCase(),
                token, ip, now, expires);
        databaseManager.saveSession(session);
    }

    public void syncLoginToBackend(Player player, String color, String style) {
        player.getCurrentServer().ifPresent(serverConn -> {
            messageManager.sendPerformLogin(serverConn.getServer(), player.getUsername(),
                    player.getUniqueId(), color, style);
        });
    }

    private void maybeForwardToTarget(Player player) {
        player.getCurrentServer().ifPresent(conn -> {
            String currentName = conn.getServerInfo().getName();
            if (configManager.getAuthServers().contains(currentName)) {
                String target = configManager.getTargetServer();
                if (target != null && !target.isEmpty() && !target.equalsIgnoreCase(currentName)) {
                    server.getServer(target).ifPresent(targetSrv -> {
                        logger.info("Forwarding authenticated player {} to target server {}", player.getUsername(), target);
                        player.createConnectionRequest(targetSrv).fireAndForget();
                    });
                }
            }
        });
    }

    private void playErrorSound(Player player) {
        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.MASTER, 1.0f, 0.8f));
        }
    }

    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
