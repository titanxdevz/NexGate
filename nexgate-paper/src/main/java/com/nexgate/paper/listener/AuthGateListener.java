package com.nexgate.paper.listener;

import com.nexgate.paper.auth.PaperAuthManager;
import com.nexgate.paper.NexGatePaperPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Defense-in-depth auth gating on Paper.
 * Blocks all actions for unauthenticated players.
 */
public class AuthGateListener implements Listener {
    private final NexGatePaperPlugin plugin;
    private final PaperAuthManager authManager;
    
    public AuthGateListener(NexGatePaperPlugin plugin, PaperAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player)) {
            // Allow looking around but not moving to different block
            if (event.getTo() != null && event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY()) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("You must be logged in!").color(NamedTextColor.RED));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Please login first.").color(NamedTextColor.RED));
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/login") || cmd.startsWith("/register") || cmd.startsWith("/l") || cmd.startsWith("/reg")) {
            return;
        }
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Please login first. Use /login <password>")
                .color(NamedTextColor.RED));
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
    public void onPickupItem(PlayerPickupItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
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
    public void onDamage(EntityDamageEvent event) {
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
}
