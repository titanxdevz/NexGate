package com.nexgate.paper.config;

import com.nexgate.paper.NexGatePaperPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Config manager for Paper plugin.
 */
public class PaperConfigManager {
    private final NexGatePaperPlugin plugin;
    private String hmacSecret;
    
    public PaperConfigManager(NexGatePaperPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void load() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        this.hmacSecret = config.getString("hmac-secret", "");
        if (hmacSecret.isEmpty()) {
            plugin.getLogger().warning("hmac-secret is not configured! Set the same secret as Velocity.");
        }
    }
    
    public String getHmacSecret() {
        return hmacSecret;
    }
}
```