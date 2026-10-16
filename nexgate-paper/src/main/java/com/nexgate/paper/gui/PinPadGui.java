package com.nexgate.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive Virtual PIN-Pad GUI on Paper backend.
 * Enables clicking digits directly in a graphical interface to authenticate without typing in chat.
 */
public class PinPadGui implements InventoryHolder {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    public static final Component TITLE = MINI.deserialize("<gradient:#00C9FF:#92FE9D><bold>NexGate Security PIN-Pad</bold></gradient>");

    private static final Map<UUID, StringBuilder> ACTIVE_PINS = new ConcurrentHashMap<>();

    private final Inventory inventory;
    private final Player player;

    public PinPadGui(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, TITLE);
        initializeInventory();
    }

    public static void open(Player player) {
        if (player == null || !player.isOnline()) return;
        PinPadGui gui = new PinPadGui(player);
        ACTIVE_PINS.put(player.getUniqueId(), new StringBuilder());
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void initializeInventory() {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, "<gray> </gray>", List.of());
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Slot 4: PIN Display
        updateDisplayItem();

        // Digits 1-5 (Row 2: slots 11-15)
        inventory.setItem(11, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 1 ]</bold></aqua>", List.of("<gray>Click to enter 1</gray>")));
        inventory.setItem(12, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 2 ]</bold></aqua>", List.of("<gray>Click to enter 2</gray>")));
        inventory.setItem(13, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 3 ]</bold></aqua>", List.of("<gray>Click to enter 3</gray>")));
        inventory.setItem(14, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 4 ]</bold></aqua>", List.of("<gray>Click to enter 4</gray>")));
        inventory.setItem(15, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 5 ]</bold></aqua>", List.of("<gray>Click to enter 5</gray>")));

        // Digits 6-0 (Row 3: slots 20-24)
        inventory.setItem(20, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 6 ]</bold></aqua>", List.of("<gray>Click to enter 6</gray>")));
        inventory.setItem(21, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 7 ]</bold></aqua>", List.of("<gray>Click to enter 7</gray>")));
        inventory.setItem(22, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 8 ]</bold></aqua>", List.of("<gray>Click to enter 8</gray>")));
        inventory.setItem(23, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 9 ]</bold></aqua>", List.of("<gray>Click to enter 9</gray>")));
        inventory.setItem(24, createItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua><bold>[ 0 ]</bold></aqua>", List.of("<gray>Click to enter 0</gray>")));

        // Action Buttons:
        // Slot 18: Backspace / Clear
        inventory.setItem(18, createItem(Material.RED_STAINED_GLASS_PANE, "<red><bold>⌫ Backspace / Clear</bold></red>",
                List.of("<gray>Click to delete the last entered digit.</gray>")));

        // Slot 26: Submit PIN
        inventory.setItem(26, createItem(Material.EMERALD, "<green><bold>✔ Submit PIN</bold></green>",
                List.of("<gray>Click to log in with your entered PIN.</gray>")));
    }

    public void updateDisplayItem() {
        StringBuilder buf = ACTIVE_PINS.computeIfAbsent(player.getUniqueId(), k -> new StringBuilder());
        String masked = buf.length() == 0 ? "<dark_gray>_ _ _ _</dark_gray>" : "<yellow>" + "• ".repeat(buf.length()).trim() + "</yellow>";

        ItemStack star = createItem(Material.NETHER_STAR, "<gold><bold>Virtual PIN Display</bold></gold>", List.of(
                "<gray>Current PIN: </gray>" + masked,
                "<dark_gray>Click number buttons below to enter digits.</dark_gray>"
        ));
        inventory.setItem(4, star);
    }

    public static void handleClick(Player player, int slot) {
        StringBuilder buf = ACTIVE_PINS.computeIfAbsent(player.getUniqueId(), k -> new StringBuilder());

        // Check if digit slot
        Character digit = null;
        switch (slot) {
            case 11 -> digit = '1';
            case 12 -> digit = '2';
            case 13 -> digit = '3';
            case 14 -> digit = '4';
            case 15 -> digit = '5';
            case 20 -> digit = '6';
            case 21 -> digit = '7';
            case 22 -> digit = '8';
            case 23 -> digit = '9';
            case 24 -> digit = '0';
        }

        if (digit != null) {
            if (buf.length() < 16) {
                buf.append(digit);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof PinPadGui gui) {
                    gui.updateDisplayItem();
                }
            }
            return;
        }

        // Backspace
        if (slot == 18) {
            if (buf.length() > 0) {
                buf.deleteCharAt(buf.length() - 1);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.9f);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof PinPadGui gui) {
                    gui.updateDisplayItem();
                }
            }
            return;
        }

        // Submit
        if (slot == 26) {
            if (buf.length() < 4) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                player.sendMessage(MINI.deserialize("<gold><bold>NEXGATE</bold></gold> <white>→</white> <red>PIN must be at least 4 digits!</red>"));
                return;
            }

            String pin = buf.toString();
            ACTIVE_PINS.remove(player.getUniqueId());
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);

            // Execute /login command with the entered PIN
            player.chat("/login " + pin);
        }
    }

    public static void handleClose(Player player) {
        // Keep active pin or clear if needed
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI.deserialize(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(MINI::deserialize).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
