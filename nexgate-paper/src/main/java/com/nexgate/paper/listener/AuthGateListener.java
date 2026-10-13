package com.nexgate.paper.listener;

import com.nexgate.color.ChatColorFormatter;
import com.nexgate.paper.NexGatePaperPlugin;
import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.config.PaperConfigManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.UUID;

/**
 * Enterprise defense-in-depth security gate for Paper backend.
 * Blocks all world interactions, movements, and attacks for unauthenticated players.
 * Formats in-game chat messages using player's custom color preferences.
 */
public class AuthGateListener implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final NexGatePaperPlugin plugin;
    private final PaperAuthManager authManager;
    private final PaperConfigManager configManager;

    public AuthGateListener(NexGatePaperPlugin plugin, PaperAuthManager authManager, PaperConfigManager configManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            // Void fall protection: re-teleport to spawn
            if (to != null && to.getY() < player.getWorld().getMinHeight() + 5) {
                event.setCancelled(true);
                player.teleport(player.getWorld().getSpawnLocation());
                return;
            }

            // Allow pitch/yaw looking, but freeze X/Y/Z translation
            if (to != null && (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ())) {
                event.setCancelled(true);
                player.sendActionBar(MINI.deserialize("<red>⚠ You must authenticate to move!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!authManager.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(MINI.deserialize("<red>You must authenticate before chatting!</red>"));
            return;
        }

        // Apply player's personalized chat color and style
        if (configManager.isAllowChatColorFormatting()) {
            String color = authManager.getPlayerColor(uuid);
            String style = authManager.getPlayerStyle(uuid);

            String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
            String formattedMessageMini = ChatColorFormatter.formatMessage(plainMessage, color, style);

            Component styledMessage = MINI.deserialize(formattedMessageMini);
            event.message(styledMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            String cmd = event.getMessage().toLowerCase().trim();
            if (cmd.startsWith("/login") || cmd.startsWith("/register") ||
                cmd.startsWith("/l ") || cmd.startsWith("/reg ") ||
                cmd.equals("/l") || cmd.equals("/reg")) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(MINI.deserialize("<red>You must authenticate first! Use <yellow>/login <password></yellow></red>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        // Godmode: Unauthenticated players neither take damage nor deal damage
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }
}
