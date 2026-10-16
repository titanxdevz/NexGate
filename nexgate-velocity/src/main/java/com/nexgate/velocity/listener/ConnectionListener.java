package com.nexgate.velocity.listener;

import com.nexgate.model.AccountType;
import com.nexgate.model.AuthMode;
import com.nexgate.model.UserAuth;
import com.nexgate.premium.MojangProfileChecker;
import com.nexgate.security.UsernameValidator;
import com.nexgate.velocity.antibot.AntiBotManager;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.nexgate.velocity.config.ConfigManager;
import java.util.UUID;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

/**
 * Enterprise connection lifecycle listener for Velocity proxy.
 * Implements non-blocking async pre-login admission pipeline, anti-bot mitigation,
 * explicit authentication modes, strict ownership verification, and case-sensitivity enforcement.
 */
public class ConnectionListener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final VelocityAuthService authService;
    private final AuthManager authManager;
    private final ConfigManager configManager;

    public ConnectionListener(ProxyServer server, Logger logger, VelocityAuthService authService,
                              AuthManager authManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.authService = authService;
        this.authManager = authManager;
        this.configManager = configManager;
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.withContinuation(continuation -> {
            try {
                handlePreLoginAsync(event, continuation);
            } catch (Exception e) {
                logger.error("Unexpected error in PreLogin for {}", event.getUsername(), e);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("Internal authentication error. Please reconnect.").color(NamedTextColor.RED)
                ));
                continuation.resume();
            }
        });
    }

    private void handlePreLoginAsync(PreLoginEvent event, com.velocitypowered.api.event.Continuation continuation) {
        String username = event.getUsername();
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        int protocol = event.getConnection().getProtocolVersion().getProtocol();
        String virtualHost = event.getConnection().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");

        // 1. Multi-stage AntiBot connection admission evaluation
        AntiBotManager antiBot = authService.getAntiBotManager();
        AntiBotManager.AdmissionResult admission = antiBot.evaluateConnection(ip, username, protocol, virtualHost);
        if (!admission.allowed()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(admission.component()));
            continuation.resume();
            return;
        }

        // 2. IP rate limiter check
        if (!authService.getIpRateLimiter().tryAcquire(ip)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    MINI.deserialize(configManager.getConfig().messages.ipRateLimited)
            ));
            continuation.resume();
            return;
        }

        // 3. Username syntax & Bedrock/Floodgate validation
        boolean allowFloodgate = true;
        if (!UsernameValidator.isValidUsername(username, allowFloodgate)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Invalid username format. Allowed: 3-16 alphanumeric characters.").color(NamedTextColor.RED)
            ));
            continuation.resume();
            return;
        }

        boolean isBedrock = UsernameValidator.isFloodgateUsername(username);

        // Bedrock Edition / Floodgate verified direct login: bypass password auth and route straight to lobby
        if (isBedrock && configManager.isBedrockAutoLogin()) {
            authService.getDatabaseManager().getUser(username).thenAccept(userOpt -> {
                if (userOpt.isPresent()) {
                    UserAuth user = userOpt.get();
                    if (user.isLocked()) {
                        String reason = user.getLockReason() != null ? user.getLockReason() : "Account suspended by administrator.";
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                Component.text("Account is locked: " + reason).color(NamedTextColor.RED)
                        ));
                        continuation.resume();
                        return;
                    }
                    logger.info("Pre-resolved verified Bedrock session for {} in PreLogin", username);
                    authService.cachePreResolvedSession(username.toLowerCase(), user);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    continuation.resume();
                } else {
                    // Auto-register new Bedrock account
                    String dummyHash = authService.getArgon2Service().hashPassword(com.nexgate.crypto.HashingUtils.generateSecureToken(32));
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    UserAuth newUser = UserAuth.builder()
                            .username(username)
                            .usernameLower(username.toLowerCase())
                            .uuid(offlineUuid)
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

                    authService.getDatabaseManager().registerUser(newUser).thenAccept(success -> {
                        logger.info("Auto-registered verified Bedrock account for {} in PreLogin", username);
                        authService.cachePreResolvedSession(username.toLowerCase(), newUser);
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                        continuation.resume();
                    }).exceptionally(ex -> {
                        logger.error("Failed to auto-register Bedrock user {}", username, ex);
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                        continuation.resume();
                        return null;
                    });
                }
            }).exceptionally(ex -> {
                logger.error("DB error in Bedrock PreLogin for {}", username, ex);
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                continuation.resume();
                return null;
            });
            return;
        }

        AuthMode authMode = configManager.getAuthMode();

        // 4. Explicit AuthMode handling
        switch (authMode) {
            case ONLINE_ONLY -> {
                if (isBedrock) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            MINI.deserialize(configManager.getConfig().messages.authModeOnlineOnly)
                    ));
                    continuation.resume();
                    return;
                }
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                continuation.resume();
            }
            case OFFLINE_AUTH -> {
                // All players authenticate via NexGate; online mode auto-login disabled
                authService.getDatabaseManager().getUser(username).thenAccept(userOpt -> {
                    if (userOpt.isPresent()) {
                        UserAuth user = userOpt.get();
                        if (user.getAccountType() == AccountType.BEDROCK && !isBedrock) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("This account belongs to a verified Bedrock Edition player.").color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }
                        if (!UsernameValidator.isCaseMatch(username, user.getUsername())) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("Username casing mismatch! Registered: " + user.getUsername()).color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }
                        if (user.isLocked()) {
                            String reason = user.getLockReason() != null ? user.getLockReason() : "Account suspended by administrator.";
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("Account is locked: " + reason).color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }
                        // Offline account: check if user has active valid session
                        authService.getDatabaseManager().getSession(user.getUuid()).thenAccept(sessOpt -> {
                            if (sessOpt.isPresent()) {
                                com.nexgate.model.SessionRecord session = sessOpt.get();
                                if (session.isValid(ip, configManager.isSessionStrictIp())) {
                                    logger.info("Pre-resolved valid active session for {} in PreLogin (OFFLINE_AUTH)", username);
                                    authService.cachePreResolvedSession(username.toLowerCase(), user);
                                }
                            }
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                        }).exceptionally(ex -> {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                            return null;
                        });
                        return;
                    }
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    continuation.resume();
                }).exceptionally(ex -> {
                    logger.error("DB error in OFFLINE_AUTH check for {}", username, ex);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text("Database error. Please retry.").color(NamedTextColor.RED)
                    ));
                    continuation.resume();
                    return null;
                });
            }
            case HYBRID -> {
                authService.getDatabaseManager().getUser(username).thenAccept(userOpt -> {
                    if (userOpt.isPresent()) {
                        UserAuth user = userOpt.get();

                        if (user.getAccountType() == AccountType.BEDROCK && !isBedrock) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("This account belongs to a verified Bedrock Edition player.").color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }

                        // Enforce strict case match against registered identity
                        if (!UsernameValidator.isCaseMatch(username, user.getUsername())) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("Username casing mismatch! Registered: " + user.getUsername()).color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }

                        // Check administrative lock
                        if (user.isLocked()) {
                            String reason = user.getLockReason() != null ? user.getLockReason() : "Account suspended by administrator.";
                            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                    Component.text("Account is locked: " + reason).color(NamedTextColor.RED)
                            ));
                            continuation.resume();
                            return;
                        }

                        // Strict ownership verification:
                        // Verified Premium accounts MUST authenticate via official Mojang encryption.
                        // NEVER fall back to offline authentication for an account registered as Premium!
                        if (user.isPremium() && configManager.isForceOnlineModeForPremium()) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                            continuation.resume();
                            return;
                        }

                        // Account was marked for cryptographic verification via /premium command
                        if (user.isPendingPremium()) {
                            logger.info("Player {} has pending premium verification; enforcing online mode", username);
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                            continuation.resume();
                            return;
                        }

                        // Offline account: check if user has active valid session
                        authService.getDatabaseManager().getSession(user.getUuid()).thenAccept(sessOpt -> {
                            if (sessOpt.isPresent()) {
                                com.nexgate.model.SessionRecord session = sessOpt.get();
                                if (session.isValid(ip, configManager.isSessionStrictIp())) {
                                    logger.info("Pre-resolved valid active session for {} in PreLogin", username);
                                    authService.cachePreResolvedSession(username.toLowerCase(), user);
                                }
                            }
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                        }).exceptionally(ex -> {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                            return null;
                        });
                    } else {
                        // Unregistered user joining for the first time
                        if (isBedrock) {
                            // Bedrock accounts are never classified as Java Premium
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                            return;
                        }

                        if (configManager.isPremiumEnabled() && configManager.isPremiumAutoDetect()) {
                            // Asynchronously query official Mojang session servers
                            authService.getMojangChecker().checkProfileAsync(username).thenAccept(lookup -> {
                                if (lookup.exists()) {
                                    // Verify exact case match with official Mojang registry
                                    if (!UsernameValidator.isCaseMatch(username, lookup.exactName())) {
                                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                                Component.text("Username casing mismatch with official Mojang account (" + lookup.exactName() + ").").color(NamedTextColor.RED)
                                        ));
                                    } else if (configManager.isForceOnlineModeForPremium()) {
                                        // Official Java account detected: force online mode encryption handshake
                                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                                    } else {
                                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                                    }
                                } else if (lookup.isNotFound()) {
                                    // Definitive negative response: not a registered Mojang account, route to offline registration
                                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                                } else {
                                    // Mojang API error, rate limit, or timeout: FAIL CLOSED!
                                    // Prevents Mojang outages from becoming offline bypass vulnerabilities
                                    logger.warn("Mojang API unreachable or rate-limited checking '{}' ({}). Failing closed for security.", username, lookup.errorDetail());
                                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                            MINI.deserialize(configManager.getConfig().messages.mojangUnavailable)
                                    ));
                                }
                                continuation.resume();
                            }).exceptionally(ex -> {
                                logger.error("Mojang API request failed for {}", username, ex);
                                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                        MINI.deserialize(configManager.getConfig().messages.mojangUnavailable)
                                ));
                                continuation.resume();
                                return null;
                            });
                        } else {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                            continuation.resume();
                        }
                    }
                }).exceptionally(ex -> {
                    logger.error("DB error in PreLogin for {}", username, ex);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text("Database service unavailable.").color(NamedTextColor.RED)
                    ));
                    continuation.resume();
                    return null;
                });
            }
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Track active connection in AntiBot
        authService.getAntiBotManager().onPlayerConnected(ip);

        if (!configManager.isRequireAuth()) {
            return;
        }

        // Check if player authenticated via official Mojang online mode encryption
        if (player.isOnlineMode()) {
            logger.info("Player {} joined with verified online mode (Premium)", player.getUsername());
            authService.handlePremiumAutoLogin(player);
            return;
        }

        // If player was already authenticated on ServerPreConnect (e.g. from pre-resolved session)
        if (authManager.isAuthenticated(player)) {
            authService.maybeForwardToTarget(player);
            return;
        }

        // Check if pre-resolved session is waiting
        UserAuth preUser = authService.consumePreResolvedSession(player.getUsername().toLowerCase());
        if (preUser != null) {
            if (preUser.getAccountType() == AccountType.BEDROCK && !player.getUniqueId().equals(preUser.getUuid())) {
                authService.getDatabaseManager().updateUserUuid(player.getUsername(), player.getUniqueId());
            }
            authManager.authenticate(player, preUser, preUser.isPremium());
            String welcomeMsg = preUser.getAccountType() == AccountType.BEDROCK ?
                    configManager.getConfig().bedrock.verifiedWelcomeMessage :
                    configManager.getConfig().messages.sessionRestored
                            .replace("{player}", player.getUsername())
                            .replace("{ip}", ip);
            player.sendMessage(MINI.deserialize(authService.getPrefix() + welcomeMsg));
            authService.syncLoginToBackend(player, preUser.getMessageColor(), preUser.getMessageStyle());
            authService.maybeForwardToTarget(player);
            return;
        }

        // Bedrock fallback direct login
        if (UsernameValidator.isFloodgateUsername(player.getUsername()) && configManager.isBedrockAutoLogin()) {
            authService.handleBedrockAutoLogin(player);
            return;
        }

        // Player joined in OFFLINE mode:
        // CRITICAL DEFENSE: Disallow offline connections from accessing accounts registered as Premium!
        authService.getDatabaseManager().getUser(player.getUsername()).thenAccept(userOpt -> {
            if (userOpt.isPresent()) {
                UserAuth user = userOpt.get();
                if (user.isPremium()) {
                    logger.warn("SECURITY ALERT: Blocked offline impersonation attempt for Premium account '{}' from IP {}", player.getUsername(), ip);
                    authService.getDatabaseManager().recordAuditLog("security", "PREMIUM_IMPERSONATION_BLOCKED", player.getUsername(), ip, "Offline connection attempted on Premium account");
                    player.disconnect(MINI.deserialize(configManager.getConfig().messages.premiumImpersonationKick));
                    return;
                }

                if (user.isPendingPremium()) {
                    player.sendMessage(MINI.deserialize(configManager.getConfig().messages.pendingPremiumPrompt));
                }
            }

            // Offline mode: Check if existing valid session can be restored automatically
            authService.tryRestoreSession(player).thenAccept(restored -> {
                if (!restored && !authManager.isAuthenticated(player)) {
                    boolean isRegistered = userOpt.isPresent();
                    authManager.startAuthFlow(player, isRegistered);
                } else if (restored) {
                    authService.maybeForwardToTarget(player);
                }
            });
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        authService.getAntiBotManager().onPlayerDisconnected(ip);
        authManager.logout(player);
    }
}

