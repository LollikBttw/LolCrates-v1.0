package com.lolcrates;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CrateKeys {
   public static final String CRATE_ID = "lol_crate_id";
   public static final String KEY_ID = "lol_key_id";

   private CrateKeys() {
   }

   public static @NotNull NamespacedKey crateIdKey(@NotNull Plugin plugin) {
      return new NamespacedKey(plugin, "lol_crate_id");
   }

   public static @NotNull NamespacedKey keyIdKey(@NotNull Plugin plugin) {
      return new NamespacedKey(plugin, "lol_key_id");
   }

   public static @Nullable String readCrateId(@NotNull Plugin plugin, @NotNull Block block) {
      BlockState var3 = block.getState();
      if (var3 instanceof TileState tileState) {
         PersistentDataContainer container = tileState.getPersistentDataContainer();
         return (String)container.get(crateIdKey(plugin), PersistentDataType.STRING);
      } else {
         return null;
      }
   }

   public static void writeCrateId(@NotNull Plugin plugin, @NotNull Block block, @NotNull String crateId) {
      BlockState var4 = block.getState();
      if (var4 instanceof TileState tileState) {
         tileState.getPersistentDataContainer().set(crateIdKey(plugin), PersistentDataType.STRING, crateId);
         tileState.update(true, false);
      }
   }

   public static boolean isKeyForCrate(@NotNull Plugin plugin, @NotNull ItemStack item, @NotNull String crateId) {
      if (item.getType() != Material.AIR && item.hasItemMeta()) {
         String keyCrate = (String)item.getItemMeta().getPersistentDataContainer().get(keyIdKey(plugin), PersistentDataType.STRING);
         return crateId.equals(keyCrate);
      } else {
         return false;
      }
   }

   public static @NotNull ItemStack createKeyItem(@NotNull Plugin plugin, @NotNull String crateId, @NotNull Material material, @NotNull Component displayName) {
      ItemStack key = new ItemStack(material);
      key.setData(DataComponentTypes.CUSTOM_NAME, displayName);
      key.editMeta((meta) -> meta.getPersistentDataContainer().set(keyIdKey(plugin), PersistentDataType.STRING, crateId));
      return key;
   }

   public static @Nullable String readCrateIdFromItem(@NotNull Plugin plugin, @NotNull ItemStack item) {
      return item.getItemMeta() == null ? null : (String)item.getItemMeta().getPersistentDataContainer().get(crateIdKey(plugin), PersistentDataType.STRING);
   }

   public static @NotNull ItemStack writeCrateIdToItem(@NotNull Plugin plugin, @NotNull ItemStack item, @NotNull String crateId) {
      item.editMeta((meta) -> meta.getPersistentDataContainer().set(crateIdKey(plugin), PersistentDataType.STRING, crateId));
      return item;
   }

   public static @NotNull String readCrateIdFromItemOrBlock(@NotNull Plugin plugin, @Nullable Block block, @Nullable ItemStack item) {
      if (block != null) {
         String id = readCrateId(plugin, block);
         if (id != null) {
            return id;
         }
      }

      if (item != null) {
         String id = readCrateIdFromItem(plugin, item);
         if (id != null) {
            return id;
         }
      }

      return "";
   }
}
