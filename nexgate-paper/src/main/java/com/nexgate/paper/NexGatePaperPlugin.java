package com.nexgate.paper;

import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.command.PaperColorCommand;
import com.nexgate.paper.config.PaperConfigManager;
import com.nexgate.paper.limbo.LimboManager;
import com.nexgate.paper.listener.AuthGateListener;
import com.nexgate.paper.listener.PlayerJoinListener;
import com.nexgate.paper.messaging.PaperPluginMessageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NexGate Paper backend companion - Defense in depth and chat styling.
 */
public class NexGatePaperPlugin extends JavaPlugin {
    private PaperConfigManager configManager;
    private PaperAuthManager authManager;
    private PaperPluginMessageManager messageManager;
    private LimboManager limboManager;

    @Override
    public void onEnable() {
        getSLF4JLogger().info("Starting NexGate Paper Companion...");

        this.configManager = new PaperConfigManager(this);
        configManager.load();

        this.authManager = new PaperAuthManager(this, getSLF4JLogger(), configManager);
        this.messageManager = new PaperPluginMessageManager(this, getSLF4JLogger(), configManager, authManager);
        messageManager.register();

        this.limboManager = new LimboManager(this, getSLF4JLogger(), configManager);
        limboManager.initialize();

        // Register defense-in-depth security listeners
        getServer().getPluginManager().registerEvents(new AuthGateListener(this, authManager, configManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, authManager, configManager), this);

        // Register commands
        PluginCommand colorCmd = getCommand("color");
        if (colorCmd != null) {
            PaperColorCommand executor = new PaperColorCommand(this, authManager);
            colorCmd.setExecutor(executor);
            colorCmd.setTabCompleter(executor);
        }

        getSLF4JLogger().info("NexGate Paper enabled successfully with strict gating, potion barriers, and chat styling!");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("NexGate Paper shutting down...");
        if (messageManager != null) {
            messageManager.unregister();
        }
    }

    public PaperConfigManager getConfigManager() {
        return configManager;
    }

    public PaperAuthManager getAuthManager() {
        return authManager;
    }

    public PaperPluginMessageManager getMessageManager() {
        return messageManager;
    }

    public LimboManager getLimboManager() {
        return limboManager;
    }
}
