package com.nexgate.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.nexgate.velocity.auth.AuthManager;
import com.nexgate.velocity.config.ConfigManager;
import com.nexgate.velocity.listener.ConnectionListener;
import com.nexgate.velocity.listener.ServerListener;
import com.nexgate.velocity.messaging.PluginMessageManager;
import org.slf4j.Logger;

@Plugin(
    id = "nexgate",
    name = "NexGate",
    version = "1.0.0-SNAPSHOT",
    description = "Original authentication plugin for Velocity with Paper backends",
    authors = {"NexGate"}
)
public class NexGateVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final AuthManager authManager;
    private final PluginMessageManager messageManager;
    
    @Inject
    public NexGateVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.configManager = new ConfigManager(logger, dataDirectory());
        this.authManager = new AuthManager(logger, configManager);
        this.messageManager = new PluginMessageManager(server, logger, configManager, authManager);
    }
    
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("NexGate Velocity starting...");
        configManager.load();
        messageManager.register();
        
        EventManager eventManager = server.getEventManager();
        eventManager.register(this, new ConnectionListener(server, logger, authManager, configManager));
        eventManager.register(this, new ServerListener(server, logger, authManager, configManager, messageManager));
        
        logger.info("NexGate Velocity initialized successfully");
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("NexGate Velocity shutting down...");
        messageManager.unregister();
    }
    
    private java.nio.file.Path dataDirectory() {
        // Velocity provides data directory via Path; simplified
        return java.nio.file.Paths.get("plugins/nexgate");
    }
}
