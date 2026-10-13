package com.nexgate.paper;

import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import com.nexgate.paper.listener.AuthGateListener;
import com.nexgate.paper.listener.PlayerJoinListener;
import com.nexgate.paper.messaging.PaperPluginMessageManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NexGate Paper backend companion - Defense in depth.
 */
public class NexGatePaperPlugin extends JavaPlugin {
    private PaperConfigManager configManager;
    private PaperAuthManager authManager;
    private PaperPluginMessageManager messageManager;
    
    @Override
    public void onEnable() {
        getLogger().info("NexGate Paper starting...");
        
        this.configManager = new PaperConfigManager(this);
        configManager.load();
        
        this.authManager = new PaperAuthManager(getLogger(), configManager);
        this.messageManager = new PaperPluginMessageManager(this, getLogger(), configManager, authManager);
        messageManager.register();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new AuthGateListener(this, authManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, authManager, configManager), this);
        
        getLogger().info("NexGate Paper enabled successfully");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("NexGate Paper shutting down...");
        if (messageManager != null) {
            messageManager.unregister();
        }
    }
}
