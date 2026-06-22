package com.lolcrates;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CrateListener implements Listener {

    private final Main plugin;
    private final CrateManager crateManager;

    public CrateListener(@NotNull Main plugin, @NotNull CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    @EventHandler
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        String crateId = CrateKeys.readCrateIdFromItem(plugin, item);
        if (crateId == null || crateId.isEmpty()) return;

        CrateKeys.writeCrateId(plugin, event.getBlockPlaced(), crateId);
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() == Material.AIR) return;

        String crateId = crateManager.getCrateId(block);
        if (crateId == null) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();

        String permission = crateManager.getUsePermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            String msg = plugin.getConfig().getString(
                    "settings.no-permission-message",
                    "<gray>[<red>Кейсы</red>]</gray> <red>У вас нет доступа к этому кейсу.</red>");
            player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        CrateDefinition crate = crateManager.getCrate(crateId);
        if (crate == null) {
            player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            "<red>Этот кейс не найден в конфиге.</red>"));
            return;
        }

        if (crate.getRewards().isEmpty()) {
            String msg = plugin.getConfig().getString(
                    "settings.no-rewards-message",
                    "<gray>[<red>Кейсы</red>]</gray> <red>У этого кейса не настроены награды.</red>");
            player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!CrateKeys.isKeyForCrate(plugin, itemInHand, crateId)) {
            player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            "<gray>[<gold>Кейсы</gold>]</gray> <red>У вас нет ключа для этого кейса!</red>"));
            return;
        }

        itemInHand.setAmount(itemInHand.getAmount() - 1);
        if (itemInHand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(itemInHand);
        }

        crateManager.startAnimation(block, crate, player);
    }
}