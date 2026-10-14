package com.nexgate.paper.limbo;

import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.config.PaperConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.slf4j.Logger;

/**
 * Manages the isolated 100% void limbo environment for the authentication server.
 * Pure empty void with zero blocks: players hover weightlessly in mid-air with
 * gravity disabled and flight enabled until authenticated and forwarded to lobby.
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
            world.setGameRule(GameRule.FALL_DAMAGE, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

            int baseY = configManager.getLimboPlatformY();
            world.setSpawnLocation(0, baseY, 0, 0.0f);

            logger.info("Limbo pure void environment initialized at Y={} (zero blocks, weightless void hover)", baseY);
        });
    }

    public World getLimboWorld() {
        return plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
    }

    public Location getSpawnLocation() {
        World world = getLimboWorld();
        if (world == null) return null;
        int y = configManager.getLimboPlatformY();
        return new Location(world, 0.5, y, 0.5, 0.0f, 0.0f);
    }

    /**
     * Prepares player for void limbo: suspends in mid-air, disables gravity,
     * enables flight, removes visual distortions, and positions cleanly at spawn.
     */
    public void setupLimboPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        Location spawn = getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
        }

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGravity(false);
        player.setInvulnerable(true);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.sendActionBar(Component.empty());
    }

    /**
     * Legacy alias for setupLimboPlayer.
     */
    public void teleportToLimbo(Player player) {
        setupLimboPlayer(player);
    }
}
