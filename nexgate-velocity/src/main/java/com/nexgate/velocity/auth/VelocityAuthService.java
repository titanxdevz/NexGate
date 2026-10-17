package com.nexgate.velocity.auth;

import com.nexgate.color.ChatColorFormatter;
import com.nexgate.crypto.Argon2Service;
import com.nexgate.crypto.HashingUtils;
import com.nexgate.db.DatabaseManager;
import com.nexgate.model.AccountType;
import com.nexgate.model.SessionRecord;
import com.nexgate.model.UserAuth;
import com.nexgate.premium.MojangProfileChecker;
import com.nexgate.ratelimit.RateLimiter;
import com.nexgate.security.PasswordValidator;
import com.nexgate.security.TotpUtils;
import com.nexgate.velocity.antibot.AntiBotManager;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.messaging.PluginMessageManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise authentication service for Velocity proxy.
 * Manages registration, password authentication, TOTP two-factor authentication,
 * cryptographically verified premium migrations, sessions, and anti-bot mitigation.
 */
public class VelocityAuthService {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final AuthManager authManager;
    private final PluginMessageManager messageManager;

    private final DatabaseManager databaseManager;
    private final AntiBotManager antiBotManager;
    private final Argon2Service argon2Service;
    private final RateLimiter ipRateLimiter;
    private final RateLimiter accountRateLimiter;
    private final RateLimiter totpRateLimiter;
    private final RateLimiter ipFailedKicksLimiter;
    private final PasswordValidator passwordValidator;
    private final MojangProfileChecker mojangChecker;

    // Temporary storage for in-progress TOTP setups: UUID -> Base32 Secret
    private final Map<UUID, String> pendingTotpSetups = new ConcurrentHashMap<>();

    // Pre-resolved sessions discovered during PreLogin: username (lowercase) -> UserAuth
    private final Map<String, UserAuth> preResolvedSessions = new ConcurrentHashMap<>();
    private Object pluginInstance;

    public void setPluginInstance(Object pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public void cachePreResolvedSession(String usernameLower, UserAuth user) {
        if (usernameLower != null && user != null) {
            preResolvedSessions.put(usernameLower, user);
        }
    }

    public UserAuth getPreResolvedSession(String usernameLower) {
        return usernameLower != null ? preResolvedSessions.get(usernameLower) : null;
    }

    public UserAuth consumePreResolvedSession(String usernameLower) {
        return usernameLower != null ? preResolvedSessions.remove(usernameLower) : null;
    }

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

        this.antiBotManager = new AntiBotManager();
        this.antiBotManager.setEnabled(cfg.antibot.enabled);
        this.antiBotManager.setMode(AntiBotManager.Mode.valueOf(cfg.antibot.mode.toUpperCase()));
        this.antiBotManager.setGlobalRateLimit(cfg.antibot.globalRateLimit);
        this.antiBotManager.setPerIpRateLimit(cfg.antibot.perIpRateLimit);
        this.antiBotManager.setMaxConcurrentPerIp(cfg.antibot.maxConcurrentPerIp);
        for (String cidr : cfg.antibot.blacklistedCidrs) {
            this.antiBotManager.addBlacklistCidr(cidr);
        }
        for (String cidr : cfg.antibot.whitelistedCidrs) {
            this.antiBotManager.addWhitelistCidr(cidr);
        }

        this.argon2Service = new Argon2Service(
                cfg.argon2.maxConcurrent,
                cfg.argon2.iterations,
                cfg.argon2.memoryKb,
                cfg.argon2.parallelism
        );

        this.ipRateLimiter = new RateLimiter(
                configManager.getRateLimitIpMax(),
                configManager.getRateLimitIpWindow()
        );

        this.accountRateLimiter = new RateLimiter(
                configManager.getRateLimitAccountMax(),
                configManager.getRateLimitAccountWindow()
        );

        this.totpRateLimiter = new RateLimiter(
                cfg.totp.maxAttempts,
                cfg.totp.lockoutDurationSeconds
        );

        this.ipFailedKicksLimiter = new RateLimiter(
                configManager.getMaxFailedKicksBeforeIpCooldown(),
                Math.max(1800, configManager.getIpCooldownMinutes() * 60 * 2),
                10_000
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

    public AntiBotManager getAntiBotManager() {
        return antiBotManager;
    }

    public RateLimiter getIpRateLimiter() {
        return ipRateLimiter;
    }

    public RateLimiter getAccountRateLimiter() {
        return accountRateLimiter;
    }

    public RateLimiter getTotpRateLimiter() {
        return totpRateLimiter;
    }

    public RateLimiter getIpFailedKicksLimiter() {
        return ipFailedKicksLimiter;
    }

    public MojangProfileChecker getMojangChecker() {
        return mojangChecker;
    }

    public String getPrefix() {
        return configManager.getConfig().messages.prefix;
    }

    public void recordFailedKickForIp(String ip) {
        if (!configManager.isIpCooldownEnabled()) return;
        String key = RateLimiter.normalizeIpKey(ip);
        ipFailedKicksLimiter.tryAcquire(key);
        int attempts = ipFailedKicksLimiter.getAttempts(key);
        int max = configManager.getMaxFailedKicksBeforeIpCooldown();
        if (attempts >= max) {
            long duration = configManager.getIpCooldownMinutes() * 60;
            ipFailedKicksLimiter.lockout(key, duration);
            ipFailedKicksLimiter.resetAttempts(key);
            logger.warn("IP {} cooled down for {}m after reaching {} failed login kicks",
                    key, configManager.getIpCooldownMinutes(), attempts);
            databaseManager.recordAuditLog("security", "IP_COOLDOWN_ACTIVATED", key, ip,
                    "Cooled down for " + configManager.getIpCooldownMinutes() + " minutes");
        }
    }

    public boolean isIpCooledDown(String ip) {
        if (!configManager.isIpCooldownEnabled()) return false;
        String key = RateLimiter.normalizeIpKey(ip);
        return ipFailedKicksLimiter.isLockedOut(key);
    }

    public long getIpCooldownRemainingSeconds(String ip) {
        String key = RateLimiter.normalizeIpKey(ip);
        return ipFailedKicksLimiter.getRemainingLockTimeSeconds(key);
    }

    public void resetIpFailedKicks(String ip) {
        String key = RateLimiter.normalizeIpKey(ip);
        ipFailedKicksLimiter.reset(key);
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

            return databaseManager.getUser(player.getUsername()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() + "<red>This account is already registered! Use /login <password></red>"));
                    return CompletableFuture.completedFuture(null);
                }

                // Asynchronously hash password with bounded concurrency
                return argon2Service.hashPasswordAsync(password).thenCompose(hash -> {
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
                            .accountType(AccountType.OFFLINE)
                            .messageColor("WHITE")
                            .messageStyle("DEFAULT")
                            .build();

                    return databaseManager.registerUser(newUser).thenAccept(success -> {
                        if (success) {
                            authManager.authenticate(player, newUser, false);
                            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.registerSuccess));

                            resetIpFailedKicks(ip);
                            createAndSaveSession(player);
                            syncLoginToBackend(player, newUser.getMessageColor(), newUser.getMessageStyle());
                            maybeForwardToTarget(player);

                            databaseManager.recordAuditLog(player.getUsername(), "REGISTER", player.getUsername(), ip, "Offline account registered");
                        } else {
                            playErrorSound(player);
                            player.sendMessage(MINI.deserialize(getPrefix() + "<red>Failed to register. Please try again later.</red>"));
                        }
                    });
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

        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                playErrorSound(player);
                player.sendMessage(MINI.deserialize(getPrefix() + "<red>You are not registered yet! Use /register <pass> <confirm></red>"));
                return CompletableFuture.completedFuture(null);
            }

            UserAuth user = userOpt.get();

            // Check if administrator locked the account
            if (user.isLocked()) {
                playErrorSound(player);
                String reason = user.getLockReason() != null ? user.getLockReason() : "Account suspended by administrator.";
                player.sendMessage(MINI.deserialize(getPrefix() + "<red>Account is locked: " + reason + "</red>"));
                return CompletableFuture.completedFuture(null);
            }

            // Asynchronously verify Argon2id hash with bounded concurrency
            return argon2Service.verifyPasswordAsync(password, user.getPasswordHash()).thenAccept(correct -> {
                if (!correct) {
                    playErrorSound(player);
                    boolean acquired = accountRateLimiter.tryAcquire(userKey);
                    if (!acquired) {
                        // Exceeded maximum attempts: kick user immediately, never lock account across server
                        databaseManager.recordAuditLog("security", "FAILED_LOGIN_KICK", username, ip, "Exceeded maximum failed password attempts");
                        accountRateLimiter.reset(userKey);
                        recordFailedKickForIp(ip);
                        player.disconnect(MINI.deserialize(configManager.getConfig().messages.maxFailedAttemptsKick));
                        return;
                    }

                    int remaining = configManager.getRateLimitAccountMax() - accountRateLimiter.getAttempts(userKey);
                    player.sendMessage(MINI.deserialize(getPrefix() +
                            configManager.getConfig().messages.wrongPassword.replace("{attempts}", String.valueOf(Math.max(0, remaining)))));
                    return;
                }

                // Password matches! Reset lockout counters
                accountRateLimiter.reset(userKey);
                ipRateLimiter.reset(ip);
                resetIpFailedKicks(ip);

                // Check if hash needs parameter rehash
                if (argon2Service.needsRehash(user.getPasswordHash())) {
                    argon2Service.hashPasswordAsync(password).thenAccept(newHash -> {
                        databaseManager.updatePassword(username, newHash);
                    });
                }

                // Update last login
                databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());

                // Check if TOTP 2FA is enabled on this account
                if (user.isTotpEnabled() && user.getTotpSecret() != null) {
                    authManager.startTotpChallenge(player, user);
                    return;
                }

                // Fully authenticated
                authManager.authenticate(player, user, user.isPremium());
                player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.loginSuccess));

                createAndSaveSession(player);
                syncLoginToBackend(player, user.getMessageColor(), user.getMessageStyle());
                maybeForwardToTarget(player);

                databaseManager.recordAuditLog(player.getUsername(), "LOGIN", player.getUsername(), ip, "Password login successful");
            });
        });
    }

    /**
     * Verifies 6-digit TOTP code during interactive 2FA challenge.
     */
    public void handleTotpVerify(Player player, String code) {
        UUID uuid = player.getUniqueId();
        Optional<AuthManager.PendingTotpAuth> pendingOpt = authManager.getPendingTotp(uuid);
        if (pendingOpt.isEmpty()) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<yellow>You do not have an active 2FA challenge.</yellow>"));
            return;
        }

        AuthManager.PendingTotpAuth pending = pendingOpt.get();
        String userKey = player.getUsername().toLowerCase();

        boolean valid = TotpUtils.verifyCode(
                pending.user.getTotpSecret(), code, configManager.getConfig().totp.windowSteps
        );

        if (!valid) {
            playErrorSound(player);
            boolean acquired = totpRateLimiter.tryAcquire(userKey);
            if (!acquired) {
                String ip = player.getRemoteAddress().getAddress().getHostAddress();
                databaseManager.recordAuditLog("security", "FAILED_TOTP_KICK", player.getUsername(),
                        ip, "Exceeded maximum failed 2FA attempts");
                totpRateLimiter.reset(userKey);
                recordFailedKickForIp(ip);
                player.disconnect(MINI.deserialize(configManager.getConfig().messages.totpFailedAttemptsKick));
                return;
            }

            int remaining = configManager.getConfig().totp.maxAttempts - totpRateLimiter.getAttempts(userKey);
            player.sendMessage(MINI.deserialize(getPrefix() +
                    configManager.getConfig().messages.totpCodeInvalid.replace("{attempts}", String.valueOf(Math.max(0, remaining)))));
            return;
        }

        // 2FA Verified!
        totpRateLimiter.reset(userKey);
        resetIpFailedKicks(player.getRemoteAddress().getAddress().getHostAddress());
        authManager.stopTotpFlow(player);
        authManager.authenticate(player, pending.user, pending.user.isPremium());
        player.sendMessage(MINI.deserialize(getPrefix() + "<green>Two-Factor Authentication verified! Welcome back.</green>"));

        createAndSaveSession(player);
        syncLoginToBackend(player, pending.user.getMessageColor(), pending.user.getMessageStyle());
        maybeForwardToTarget(player);

        databaseManager.recordAuditLog(player.getUsername(), "TOTP_VERIFY", player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(), "2FA verification successful");
    }

    /**
     * Verifies single-use backup recovery code during interactive 2FA challenge.
     */
    public void handleTotpRecovery(Player player, String plainCode) {
        UUID uuid = player.getUniqueId();
        Optional<AuthManager.PendingTotpAuth> pendingOpt = authManager.getPendingTotp(uuid);
        if (pendingOpt.isEmpty()) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<yellow>You do not have an active 2FA challenge.</yellow>"));
            return;
        }

        AuthManager.PendingTotpAuth pending = pendingOpt.get();
        String rawCodes = pending.user.getTotpRecoveryCodes();
        if (rawCodes == null || rawCodes.trim().isEmpty()) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>No backup recovery codes found for your account. Please contact an administrator.</red>"));
            return;
        }

        List<String> storedHashes = new ArrayList<>(Arrays.asList(rawCodes.split(",")));
        Optional<String> matchedHash = TotpUtils.verifyAndFindRecoveryCodeHash(plainCode, storedHashes);

        if (matchedHash.isEmpty()) {
            playErrorSound(player);
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>Invalid or already used backup recovery code!</red>"));
            return;
        }

        // Consume recovery code
        storedHashes.remove(matchedHash.get());
        String updatedCodes = String.join(",", storedHashes);
        databaseManager.updateTotp(player.getUsername(), pending.user.getTotpSecret(), true, updatedCodes);

        totpRateLimiter.reset(player.getUsername().toLowerCase());
        authManager.stopTotpFlow(player);
        authManager.authenticate(player, pending.user, pending.user.isPremium());

        player.sendMessage(MINI.deserialize(getPrefix() +
                "<green>Recovery code accepted! <yellow>" + storedHashes.size() + " recovery codes remaining.</yellow></green>"));

        createAndSaveSession(player);
        syncLoginToBackend(player, pending.user.getMessageColor(), pending.user.getMessageStyle());
        maybeForwardToTarget(player);

        databaseManager.recordAuditLog(player.getUsername(), "RECOVERY_CODE_USED", player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(), "Consumed recovery code; remaining: " + storedHashes.size());
    }

    /**
     * Initiates TOTP setup for an authenticated player.
     */
    public void initiateTotpSetup(Player player) {
        if (!authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>You must be authenticated to setup 2FA.</red>"));
            return;
        }

        String secret = TotpUtils.generateSecret();
        pendingTotpSetups.put(player.getUniqueId(), secret);

        String uri = TotpUtils.formatTotpUri(configManager.getConfig().totp.issuer, player.getUsername(), secret);

        player.sendMessage(MINI.deserialize("""
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━ [ Two-Factor Authentication Setup ] ━━━━━━━━━━━</gradient>
            <gray>1. Open your Authenticator app (Google Authenticator, Aegis, 1Password, etc.)</gray>
            <gray>2. Add a new account manually with Secret Key:</gray>
               <aqua><bold>%s</bold></aqua>
            <gray>3. Or click to copy configuration URI:</gray>
               <click:copy_to_clipboard:'%s'><hover:show_text:'<green>Click to copy OTP URI'><yellow>[ CLICK TO COPY OTP URI ]</yellow></hover></click>
            <gray>4. Confirm by running: <green>/2fa confirm <code></green></gray>
            <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
            """.formatted(secret, uri)));
    }

    /**
     * Confirms TOTP setup, generates recovery codes, and enables 2FA in database.
     */
    public void confirmTotpSetup(Player player, String code) {
        UUID uuid = player.getUniqueId();
        String pendingSecret = pendingTotpSetups.get(uuid);
        if (pendingSecret == null) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>No pending 2FA setup found. Run <yellow>/2fa setup</yellow> first.</red>"));
            return;
        }

        boolean valid = TotpUtils.verifyCode(pendingSecret, code, 1);
        if (!valid) {
            playErrorSound(player);
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>Invalid 6-digit confirmation code! Please verify the time on your authenticator device.</red>"));
            return;
        }

        // Setup verified! Generate 8 single-use recovery codes
        List<String> plainCodes = TotpUtils.generatePlainRecoveryCodes(8);
        List<String> hashedCodes = TotpUtils.hashRecoveryCodes(plainCodes);
        String joinedHashes = String.join(",", hashedCodes);

        databaseManager.updateTotp(player.getUsername(), pendingSecret, true, joinedHashes).thenRun(() -> {
            pendingTotpSetups.remove(uuid);
            playSuccessSound(player);

            StringBuilder codesDisplay = new StringBuilder();
            for (String c : plainCodes) {
                codesDisplay.append("  <aqua>• ").append(c).append("</aqua>\n");
            }

            player.sendMessage(MINI.deserialize("""
                <gradient:#00C9FF:#92FE9D>━━━━━━━━━━ [ 2FA Successfully Enabled! ] ━━━━━━━━━━</gradient>
                <green>Two-factor authentication is now active on your account!</green>
                <yellow><bold>IMPORTANT: Save these single-use recovery codes in a safe place!</bold></yellow>
                <gray>If you lose your phone, you can use these to recover access:</gray>
                %s<dark_gray>Each code can only be used once.</dark_gray>
                <gradient:#00C9FF:#92FE9D>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
                """.formatted(codesDisplay.toString())));

            databaseManager.recordAuditLog(player.getUsername(), "TOTP_ENABLED", player.getUsername(),
                    player.getRemoteAddress().getAddress().getHostAddress(), "2FA enabled with 8 recovery codes");
        });
    }

    /**
     * Disables TOTP 2FA after password and code verification.
     */
    public CompletableFuture<Void> disableTotp(Player player, String password, String code) {
        return databaseManager.getUser(player.getUsername()).thenCompose(userOpt -> {
            if (userOpt.isEmpty() || !userOpt.get().isTotpEnabled()) {
                player.sendMessage(MINI.deserialize(getPrefix() + "<yellow>2FA is not enabled on your account.</yellow>"));
                return CompletableFuture.completedFuture(null);
            }

            UserAuth user = userOpt.get();
            return argon2Service.verifyPasswordAsync(password, user.getPasswordHash()).thenAccept(passOk -> {
                if (!passOk) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() + "<red>Incorrect password! Cannot disable 2FA.</red>"));
                    return;
                }

                boolean codeOk = TotpUtils.verifyCode(user.getTotpSecret(), code, 1);
                if (!codeOk) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() + "<red>Invalid 2FA code! Cannot disable 2FA.</red>"));
                    return;
                }

                databaseManager.updateTotp(player.getUsername(), null, false, null).thenRun(() -> {
                    player.sendMessage(MINI.deserialize(getPrefix() + "<green>Two-factor authentication has been disabled.</green>"));
                    databaseManager.recordAuditLog(player.getUsername(), "TOTP_DISABLED", player.getUsername(),
                            player.getRemoteAddress().getAddress().getHostAddress(), "2FA disabled by user");
                });
            });
        });
    }

    /**
     * Changes password securely, invalidating all other sessions.
     */
    public CompletableFuture<Void> handleChangePassword(Player player, String oldPass, String newPass) {
        String username = player.getUsername();
        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                player.sendMessage(MINI.deserialize(getPrefix() + "<red>User record not found.</red>"));
                return CompletableFuture.completedFuture(null);
            }

            UserAuth user = userOpt.get();
            return argon2Service.verifyPasswordAsync(oldPass, user.getPasswordHash()).thenCompose(oldOk -> {
                if (!oldOk) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() + "<red>Current password is incorrect.</red>"));
                    return CompletableFuture.completedFuture(null);
                }

                PasswordValidator.ValidationResult val = passwordValidator.validate(newPass, username);
                if (!val.valid()) {
                    playErrorSound(player);
                    player.sendMessage(MINI.deserialize(getPrefix() +
                            configManager.getConfig().messages.passwordTooWeak.replace("{reason}", val.message())));
                    return CompletableFuture.completedFuture(null);
                }

                return argon2Service.hashPasswordAsync(newPass).thenCompose(newHash -> {
                    return databaseManager.updatePassword(username, newHash).thenAccept(ok -> {
                        if (ok) {
                            playSuccessSound(player);
                            player.sendMessage(MINI.deserialize(getPrefix() + "<green>Password changed successfully! Other active sessions were revoked.</green>"));

                            // Invalidate all existing sessions and create a fresh one
                            databaseManager.invalidateSession(player.getUniqueId());
                            createAndSaveSession(player);

                            databaseManager.recordAuditLog(username, "PASSWORD_CHANGE", username,
                                    player.getRemoteAddress().getAddress().getHostAddress(), "Password changed; active sessions revoked");
                        } else {
                            playErrorSound(player);
                            player.sendMessage(MINI.deserialize(getPrefix() + "<red>Failed to update password.</red>"));
                        }
                    });
                });
            });
        });
    }

    /**
     * Auto-authenticates verified Mojang premium players with cryptographic ownership verification.
     */
    public CompletableFuture<Void> handlePremiumAutoLogin(Player player) {
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        UUID onlineUuid = player.getUniqueId();

        return databaseManager.getUser(username).thenAccept(userOpt -> {
            UserAuth user;
            if (userOpt.isPresent()) {
                UserAuth existing = userOpt.get();

                // If account was pending cryptographic migration from /premium command
                if (existing.isPendingPremium()) {
                    logger.info("Player {} verified pending Mojang premium migration (UUID={})", username, onlineUuid);
                    databaseManager.updatePremiumStatus(username, true, onlineUuid, AccountType.PREMIUM);
                    databaseManager.recordAuditLog("system", "PREMIUM_LINK_COMPLETE", username, ip, "Upgraded to verified Premium via official login");
                } else if (existing.isPremium()) {
                    databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());
                } else {
                    // Existing offline account matched username. Identity separation: do not overwrite password!
                    logger.warn("Official Mojang player {} joined, matching existing offline account. Preserving identity separation.", username);
                    databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());
                }
                user = existing;
            } else {
                // Register verified Mojang player automatically
                user = UserAuth.builder()
                        .username(username)
                        .usernameLower(username.toLowerCase())
                        .uuid(onlineUuid)
                        .passwordHash(HashingUtils.hashPassword(HashingUtils.generateSecureToken(32)))
                        .ip(ip)
                        .lastIp(ip)
                        .regDate(System.currentTimeMillis())
                        .lastLogin(System.currentTimeMillis())
                        .premium(true)
                        .premiumUuid(onlineUuid)
                        .accountType(AccountType.PREMIUM)
                        .messageColor("WHITE")
                        .messageStyle("DEFAULT")
                        .build();
                databaseManager.registerUser(user);
                databaseManager.recordAuditLog("system", "PREMIUM_AUTO_REGISTER", username, ip, "Registered new verified Mojang account");
            }

            authManager.authenticate(player, user, true);
            resetIpFailedKicks(ip);
            player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().messages.premiumAutoLogin));

            createAndSaveSession(player);
            syncLoginToBackend(player, user.getMessageColor(), user.getMessageStyle());
            maybeForwardToTarget(player);
        });
    }

    /**
     * Initiates cryptographic /premium migration requiring official Mojang re-authentication.
     */
    public void initiatePremiumMigration(Player player) {
        if (!authManager.isAuthenticated(player)) {
            player.sendMessage(MINI.deserialize(getPrefix() + "<red>You must be authenticated to check premium status!</red>"));
            return;
        }

        player.sendMessage(MINI.deserialize(getPrefix() + "<gray>Checking Mojang session servers for official account...</gray>"));

        mojangChecker.checkProfileAsync(player.getUsername()).thenAccept(result -> {
            if (result.exists()) {
                databaseManager.setPendingPremium(player.getUsername(), true).thenRun(() -> {
                    player.sendMessage(MINI.deserialize(getPrefix() + """
                        <gradient:#00C9FF:#92FE9D><bold>Official Mojang Account Found!</bold></gradient>
                        <gray>Mojang UUID: <aqua>%s</aqua></gray>
                        <yellow><bold>To complete verification and enable passwordless auto-login:</bold></yellow>
                        <gray>1. Disconnect from the server.</gray>
                        <gray>2. Launch Minecraft using your official paid Mojang / Microsoft launcher.</gray>
                        <gray>3. Rejoin the server. NexGate will cryptographically verify your ownership.</gray>
                        <green>Once verified, your account will be permanently protected from offline impersonation!</green>
                        """.formatted(result.uuid())));
                });
            } else if (result.isError()) {
                player.sendMessage(MINI.deserialize(getPrefix() + "<red>Mojang API is currently unreachable. Please try again shortly.</red>"));
            } else {
                player.sendMessage(MINI.deserialize(getPrefix() +
                        "<red>No paid Mojang Minecraft account found for username '<white>" + player.getUsername() + "</white>'.</red>"));
            }
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

            return databaseManager.getUser(player.getUsername()).thenApply(userOpt -> {
                UserAuth user = userOpt.orElse(null);
                if (user != null && user.isLocked()) {
                    return false;
                }

                authManager.authenticate(player, user, user != null && user.isPremium());
                resetIpFailedKicks(ip);

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
     * Updates player message color and style preference.
     */
    public CompletableFuture<Void> updateMessageSettings(Player player, String colorName, String styleName) {
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        return databaseManager.updateMessageColor(username, colorName, styleName).thenRun(() -> {
            authManager.updatePlayerSettings(uuid, colorName, styleName);

            player.getCurrentServer().ifPresent(serverConn -> {
                messageManager.sendSyncSettings(serverConn.getServer(), username, uuid, colorName, styleName);
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

    public void maybeForwardToTarget(Player player) {
        if (player == null || !player.isActive()) return;

        Runnable forward = () -> {
            if (!player.isActive()) return;
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
        };

        if (player.getCurrentServer().isPresent()) {
            forward.run();
        } else {
            // Player is still connecting to initial server; schedule retries
            if (pluginInstance != null) {
                server.getScheduler().buildTask(pluginInstance, forward).delay(200, TimeUnit.MILLISECONDS).schedule();
                server.getScheduler().buildTask(pluginInstance, forward).delay(600, TimeUnit.MILLISECONDS).schedule();
                server.getScheduler().buildTask(pluginInstance, forward).delay(1200, TimeUnit.MILLISECONDS).schedule();
            }
        }
    }

    /**
     * Transfers an unauthenticated or logged-out player back to the auth/limbo server.
     */
    public void transferToAuthServer(Player player) {
        if (player == null || !player.isActive()) return;
        List<String> authServers = configManager.getAuthServers();
        if (!authServers.isEmpty()) {
            String authName = authServers.get(0);

            // Check if player is already on the auth server
            Optional<com.velocitypowered.api.proxy.ServerConnection> cur = player.getCurrentServer();
            if (cur.isPresent() && cur.get().getServerInfo().getName().equalsIgnoreCase(authName)) {
                databaseManager.getUser(player.getUsername()).thenAccept(userOpt -> {
                    authManager.startAuthFlow(player, userOpt.isPresent());
                });
                return;
            }

            server.getServer(authName).ifPresentOrElse(authSrv -> {
                logger.info("Transferring unauthenticated player {} to auth server {}", player.getUsername(), authName);
                player.createConnectionRequest(authSrv).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        logger.info("Player {} successfully transferred to auth server {}", player.getUsername(), authName);
                        databaseManager.getUser(player.getUsername()).thenAccept(userOpt -> {
                            authManager.startAuthFlow(player, userOpt.isPresent());
                        });
                    } else {
                        logger.warn("Failed to transfer {} to auth server: {}", player.getUsername(), result.getReasonComponent());
                    }
                });
            }, () -> {
                logger.warn("Configured auth server '{}' not found!", authName);
                player.disconnect(MINI.deserialize("<red>Authentication server unavailable.</red>"));
            });
        }
    }

    /**
     * Direct auto-login and registration for verified Bedrock Edition (Floodgate) players.
     */
    public CompletableFuture<Void> handleBedrockAutoLogin(Player player) {
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        UUID uuid = player.getUniqueId();

        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isPresent()) {
                UserAuth user = userOpt.get();
                if (user.isLocked()) {
                    player.disconnect(MINI.deserialize("<red>Account suspended: " + (user.getLockReason() != null ? user.getLockReason() : "Contact administrator.") + "</red>"));
                    return CompletableFuture.completedFuture(null);
                }

                // If UUID changed, update to current Floodgate connection UUID
                if (!uuid.equals(user.getUuid())) {
                    databaseManager.updateUserUuid(username, uuid);
                }

                authManager.authenticate(player, user, false);
                resetIpFailedKicks(ip);
                playSuccessSound(player);
                player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().bedrock.verifiedWelcomeMessage));

                createAndSaveSession(player);
                syncLoginToBackend(player, user.getMessageColor(), user.getMessageStyle());
                maybeForwardToTarget(player);

                databaseManager.updateLastLogin(username, ip, System.currentTimeMillis());
                databaseManager.recordAuditLog(username, "BEDROCK_LOGIN", username, ip, "Xbox Live auto-login verified");
                return CompletableFuture.completedFuture(null);
            }

            // New Bedrock player: automatically register in database with AccountType.BEDROCK
            String dummyHash = argon2Service.hashPassword(com.nexgate.crypto.HashingUtils.generateSecureToken(32));
            UserAuth newUser = UserAuth.builder()
                    .username(username)
                    .usernameLower(username.toLowerCase())
                    .uuid(uuid)
                    .passwordHash(dummyHash)
                    .ip(ip)
                    .lastIp(ip)
                    .regDate(System.currentTimeMillis())
                    .lastLogin(System.currentTimeMillis())
                    .premium(false)
                    .accountType(AccountType.BEDROCK)
                    .messageColor("WHITE")
                    .messageStyle("DEFAULT")
                    .build();

            return databaseManager.registerUser(newUser).thenAccept(ok -> {
                authManager.authenticate(player, newUser, false);
                playSuccessSound(player);
                player.sendMessage(MINI.deserialize(getPrefix() + configManager.getConfig().bedrock.verifiedWelcomeMessage));

                createAndSaveSession(player);
                syncLoginToBackend(player, newUser.getMessageColor(), newUser.getMessageStyle());
                maybeForwardToTarget(player);

                databaseManager.recordAuditLog(username, "BEDROCK_REGISTER", username, ip, "Xbox Live auto-registered");
            });
        });
    }

    private void playErrorSound(Player player) {
        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.MASTER, 1.0f, 0.8f));
        }
    }

    private void playSuccessSound(Player player) {
        if (configManager.isPlaySounds()) {
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 1.2f));
        }
    }

    /**
     * Admin reset password: updates hash, invalidates sessions, and records audit trail.
     */
    public CompletableFuture<Boolean> handleAdminResetPassword(String actor, String username, String newPassword) {
        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return argon2Service.hashPasswordAsync(newPassword).thenCompose(hash -> {
                return databaseManager.updatePassword(username, hash).thenApply(ok -> {
                    if (ok) {
                        server.getPlayer(username).ifPresent(p -> {
                            databaseManager.invalidateSession(p.getUniqueId());
                            authManager.logout(p);
                            p.sendMessage(MINI.deserialize(getPrefix() + "<yellow>Your password was reset by an administrator. Please re-authenticate.</yellow>"));
                            authManager.startAuthFlow(p, true);
                        });
                        databaseManager.recordAuditLog(actor, "ADMIN_PASSWORD_RESET", username, "admin-console", "Password reset by " + actor);
                    }
                    return ok;
                });
            });
        });
    }

    /**
     * Admin lock account: sets lock flag and terminates active sessions.
     */
    public CompletableFuture<Boolean> handleAdminLockAccount(String actor, String username, String reason) {
        String lockReason = (reason != null && !reason.isBlank()) ? reason : "Account suspended by administrator.";
        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return databaseManager.updateAccountLock(username, true, lockReason).thenApply(v -> {
                server.getPlayer(username).ifPresent(p -> {
                    databaseManager.invalidateSession(p.getUniqueId());
                    authManager.logout(p);
                    p.disconnect(MINI.deserialize("<red>Account suspended: " + lockReason + "</red>"));
                });
                databaseManager.recordAuditLog(actor, "ADMIN_ACCOUNT_LOCK", username, "admin-console", "Locked: " + lockReason);
                return true;
            });
        });
    }

    /**
     * Admin unlock account: clears lock flag and resets rate-limit lockouts.
     */
    public CompletableFuture<Boolean> handleAdminUnlockAccount(String actor, String username) {
        return databaseManager.getUser(username).thenCompose(userOpt -> {
            if (userOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return databaseManager.updateAccountLock(username, false, null).thenApply(v -> {
                accountRateLimiter.reset(username.toLowerCase());
                totpRateLimiter.reset(username.toLowerCase());
                databaseManager.recordAuditLog(actor, "ADMIN_ACCOUNT_UNLOCK", username, "admin-console", "Account unlocked by " + actor);
                return true;
            });
        });
    }

    public com.nexgate.crypto.Argon2Service getArgon2Service() {
        return argon2Service;
    }

    public com.nexgate.velocity.messaging.PluginMessageManager getMessageManager() {
        return messageManager;
    }

    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (argon2Service != null) {
            argon2Service.close();
        }
    }
}
