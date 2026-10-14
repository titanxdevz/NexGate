package com.nexgate.paper.limbo;

import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.config.PaperConfigManager;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Manages the isolated limbo void environment for the authentication server.
 * Creates an invisible barrier cage in the open sky at Y=100, locks daylight to noon,
 * clears weather, disables mob spawning, and protects players from falling.
 */
public class LimboManager {
    private final NexGatePaperPlugin plugin;
    private final Logger logger;
    private final PaperConfigManager configManager;

    public LimboManager(NexGatePaperPlugin plugin, Logger logger, PaperConfigManager configManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.configManager = configManager;
    }

    public void initialize() {
        if (!configManager.isLimboCageEnabled()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = getLimboWorld();
            if (world == null) {
                logger.warn("Could not find primary world to initialize Limbo environment!");
                return;
            }

            // 1. Lock environment rules
            if (configManager.isLockTimeNoon()) {
                world.setTime(6000L); // Noon
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
            if (configManager.isLockWeatherClear()) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            }
            if (configManager.isDisableMobSpawning()) {
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            }

            // 2. Build invisible barrier platform and cage
            buildBarrierCage(world);

            logger.info("Limbo void barrier cage initialized at Y={} with radius {}",
                    configManager.getLimboPlatformY(), configManager.getLimboRadius());
        });
    }

    public World getLimboWorld() {
        return plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
    }

    public Location getSpawnLocation() {
        World world = getLimboWorld();
        if (world == null) return null;
        int y = configManager.getLimboPlatformY();
        return new Location(world, 0.5, y + 1.0, 0.5, 0.0f, 0.0f);
    }

    public void teleportToLimbo(Player player) {
        Location spawn = getSpawnLocation();
        if (spawn != null && player != null && player.isOnline()) {
            player.teleport(spawn);
        }
    }

    private void buildBarrierCage(World world) {
        int baseY = configManager.getLimboPlatformY();
        int r = configManager.getLimboRadius();

        // Floor at baseY: -r..r
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                Block block = world.getBlockAt(x, baseY, z);
                if (block.getType() != Material.BARRIER) {
                    block.setType(Material.BARRIER, false);
                }
            }
        }

        // Inside air and perimeter walls from baseY + 1 to baseY + 3
        for (int y = baseY + 1; y <= baseY + 3; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean isWall = (x == -r || x == r || z == -r || z == r);
                    Block block = world.getBlockAt(x, y, z);
                    Material target = isWall ? Material.BARRIER : Material.AIR;
                    if (block.getType() != target) {
                        block.setType(target, false);
                    }
                }
            }
        }

        // Ceiling at baseY + 4
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                Block block = world.getBlockAt(x, baseY + 4, z);
                if (block.getType() != Material.BARRIER) {
                    block.setType(Material.BARRIER, false);
                }
            }
        }

        // Set spawn point
        world.setSpawnLocation(0, baseY + 1, 0, 0.0f);
    }
}
