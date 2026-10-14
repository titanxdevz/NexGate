package com.nexgate.velocity;

import com.google.inject.Inject;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.auth.VelocityAuthService;
import com.nexgate.velocity.command.*;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.listener.ConnectionListener;
import com.nexgate.velocity.listener.ServerListener;
import com.nexgate.velocity.messaging.PluginMessageManager;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "nexgate",
    name = "NexGate",
    version = "1.0.0-SNAPSHOT",
    description = "Enterprise proxy authentication & defense-in-depth security suite for Velocity",
    authors = {"NexGate Team"}
)
public class NexGateVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private AuthManager authManager;
    private PluginMessageManager messageManager;
    private VelocityAuthService authService;

    @Inject
    public NexGateVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Initializing NexGate Velocity Auth Suite...");

        this.configManager = new ConfigManager(logger, dataDirectory);
        configManager.load();

        this.authManager = new AuthManager(server, logger, configManager);
        authManager.setPluginInstance(this);

        this.messageManager = new PluginMessageManager(server, logger, configManager, authManager);
        messageManager.register(this);

        this.authService = new VelocityAuthService(server, logger, configManager, authManager, messageManager);

        // Register event listeners
        EventManager eventManager = server.getEventManager();
        eventManager.register(this, new ConnectionListener(server, logger, authService, authManager, configManager));
        eventManager.register(this, new ServerListener(server, logger, authManager, configManager, messageManager));

        // Register player and administrative commands
        registerCommands();

        logger.info("NexGate Velocity initialized successfully with Argon2id, Mojang Premium check, and interactive UI!");
    }

    private void registerCommands() {
        CommandManager cmdMgr = server.getCommandManager();

        // /login and /l
        CommandMeta loginMeta = cmdMgr.metaBuilder("login")
                .aliases("l")
                .plugin(this)
                .build();
        cmdMgr.register(loginMeta, new LoginCommand(authService, authManager));

        // /register and /reg
        CommandMeta regMeta = cmdMgr.metaBuilder("register")
                .aliases("reg")
                .plugin(this)
                .build();
        cmdMgr.register(regMeta, new RegisterCommand(authService, authManager));

        // /changepassword and /cp
        CommandMeta cpMeta = cmdMgr.metaBuilder("changepassword")
                .aliases("cp")
                .plugin(this)
                .build();
        cmdMgr.register(cpMeta, new ChangePasswordCommand(authService, authManager));

        // /logout
        CommandMeta logoutMeta = cmdMgr.metaBuilder("logout")
                .plugin(this)
                .build();
        cmdMgr.register(logoutMeta, new LogoutCommand(authService, authManager));

        // /color and /msgcolor
        CommandMeta colorMeta = cmdMgr.metaBuilder("color")
                .aliases("msgcolor", "chatcolor")
                .plugin(this)
                .build();
        cmdMgr.register(colorMeta, new ColorCommand(authService, authManager));

        // /premium
        CommandMeta premMeta = cmdMgr.metaBuilder("premium")
                .plugin(this)
                .build();
        cmdMgr.register(premMeta, new PremiumCommand(authService, authManager));

        // /2fa and /totp
        CommandMeta totpMeta = cmdMgr.metaBuilder("2fa")
                .aliases("totp")
                .plugin(this)
                .build();
        cmdMgr.register(totpMeta, new TotpCommand(authService, authManager));

        // /nexgate admin command
        CommandMeta adminMeta = cmdMgr.metaBuilder("nexgate")
                .aliases("ng")
                .plugin(this)
                .build();
        cmdMgr.register(adminMeta, new NexGateAdminCommand(server, configManager, authService, authManager));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("NexGate Velocity shutting down...");
        if (messageManager != null) {
            messageManager.unregister();
        }
        if (authService != null) {
            authService.close();
        }
    }
}
