package com.lolcrates;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
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
      if (!event.isCancelled()) {
         ItemStack item = event.getItemInHand();
         if (item.getType() != Material.AIR && item.hasItemMeta()) {
            String crateId = CrateKeys.readCrateIdFromItem(this.plugin, item);
            if (crateId != null && !crateId.isEmpty()) {
               CrateKeys.writeCrateId(this.plugin, event.getBlockPlaced(), crateId);
            }
         }
      }
   }

   @EventHandler
   public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         if (event.getHand() == EquipmentSlot.HAND) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() != Material.AIR) {
               String crateId = this.crateManager.getCrateId(block);
               if (crateId != null) {
                  event.setCancelled(true);
                  event.setUseInteractedBlock(Result.DENY);
                  Player player = event.getPlayer();
                  String permission = this.crateManager.getUsePermission();
                  if (!permission.isEmpty() && !player.hasPermission(permission)) {
                     String msg = this.plugin.getConfig().getString("settings.no-permission-message", "<gray>[<red>Кейсы</red>]</gray> <red>У вас нет доступа к этому кейсу.</red>");
                     player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                  } else {
                     CrateDefinition crate = this.crateManager.getCrate(crateId);
                     if (crate == null) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Этот кейс не найден в конфиге.</red>"));
                     } else if (crate.getRewards().isEmpty()) {
                        String msg = this.plugin.getConfig().getString("settings.no-rewards-message", "<gray>[<red>Кейсы</red>]</gray> <red>У этого кейса не настроены награды.</red>");
                        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                     } else {
                        ItemStack itemInHand = player.getInventory().getItemInMainHand();
                        if (!CrateKeys.isKeyForCrate(this.plugin, itemInHand, crateId)) {
                           player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>[<gold>Кейсы</gold>]</gray> <red>У вас нет ключа для этого кейса!</red>"));
                        } else {
                           itemInHand.setAmount(itemInHand.getAmount() - 1);
                           if (itemInHand.getAmount() <= 0) {
                              player.getInventory().setItemInMainHand((ItemStack)null);
                           } else {
                              player.getInventory().setItemInMainHand(itemInHand);
                           }

                           this.crateManager.startAnimation(block, crate, player);
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
