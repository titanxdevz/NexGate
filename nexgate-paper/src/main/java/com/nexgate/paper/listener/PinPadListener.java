package com.nexgate.paper.listener;

import com.nexgate.paper.gui.PinPadGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles interactions within the NexGate PIN-Pad GUI.
 */
public class PinPadListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PinPadGui) {
            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {
                int rawSlot = event.getRawSlot();
                if (rawSlot >= 0 && rawSlot < 27) {
                    PinPadGui.handleClick(player, rawSlot);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PinPadGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PinPadGui) {
            if (event.getPlayer() instanceof Player player) {
                PinPadGui.handleClose(player);
            }
        }
    }
}
