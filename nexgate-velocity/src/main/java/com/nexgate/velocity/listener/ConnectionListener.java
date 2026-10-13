package com.nexgate.velocity.listener;

import com.nexgate.model.UserAuth;
import com.nexgate.premium.MojangProfileChecker;
import com.nexgate.security.UsernameValidator;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.nexgate.velocity.config.ConfigManager;
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

import java.util.Optional;

/**
 * PreLogin, PostLogin, and Disconnect lifecycle management on Velocity.
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
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        // 1. Username syntax check
        if (!UsernameValidator.isValidUsername(username)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Invalid username. Allowed: 3-16 alphanumeric or underscores.").color(NamedTextColor.RED)
            ));
            return;
        }

        // 2. IP rate limiter check
        if (!authService.getIpRateLimiter().tryAcquire(ip)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    MINI.deserialize(configManager.getConfig().messages.ipRateLimited)
            ));
            return;
        }

        // 3. Database user lookup & Case-sensitivity check
        try {
            Optional<UserAuth> userOpt = authService.getDatabaseManager().getUser(username).join();

            if (userOpt.isPresent()) {
                UserAuth user = userOpt.get();
                // Check exact case match
                if (!UsernameValidator.isCaseMatch(username, user.getUsername())) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text("Username casing mismatch! Registered: " + user.getUsername()).color(NamedTextColor.RED)
                    ));
                    return;
                }

                // If registered as premium, enforce online mode
                if (user.isPremium() && configManager.isForceOnlineModeForPremium()) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                    return;
                } else {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    return;
                }
            } else {
                // Not registered in DB yet: Check if official Mojang account
                if (configManager.isPremiumEnabled() && configManager.isPremiumAutoDetect()) {
                    MojangProfileChecker.MojangProfile mojangProfile =
                            authService.getMojangChecker().checkProfileAsync(username).join();

                    if (mojangProfile.exists() && UsernameValidator.isCaseMatch(username, mojangProfile.exactName())) {
                        if (configManager.isForceOnlineModeForPremium()) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                            return;
                        }
                    }
                }

                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            }
        } catch (Exception e) {
            logger.error("Error in PreLogin check for " + username, e);
            event.setResult(PreLoginEvent.PreLoginComponentResult.allowed());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        if (!configManager.isRequireAuth()) {
            return;
        }

        // Check if player authenticated via official Mojang online mode encryption
        if (player.isOnlineMode()) {
            logger.info("Player {} joined with verified online mode (Premium)", player.getUsername());
            authService.handlePremiumAutoLogin(player);
            return;
        }

        // Offline mode: Check if existing session can be restored automatically
        authService.tryRestoreSession(player).thenAccept(restored -> {
            if (!restored) {
                // Not restored: Start interactive login/register flow
                authService.getDatabaseManager().getUser(player.getUsername()).thenAccept(userOpt -> {
                    boolean isRegistered = userOpt.isPresent();
                    authManager.startAuthFlow(player, isRegistered);
                });
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        authManager.logout(player);
    }
}
