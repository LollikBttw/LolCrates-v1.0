package com.lolcrates;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class CrateKeys {

    public static final String CRATE_ID = "lol_crate_id";
    public static final String KEY_ID = "lol_key_id";

    private CrateKeys() {
    }

    @NotNull
    public static NamespacedKey crateIdKey(@NotNull Plugin plugin) {
        return new NamespacedKey(plugin, CRATE_ID);
    }

    @NotNull
    public static NamespacedKey keyIdKey(@NotNull Plugin plugin) {
        return new NamespacedKey(plugin, KEY_ID);
    }

    @Nullable
    public static String readCrateId(@NotNull Plugin plugin, @NotNull Block block) {
        if (!(block.getState() instanceof TileState tileState))
            return null;
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        return container.get(crateIdKey(plugin), PersistentDataType.STRING);
    }

    public static void writeCrateId(@NotNull Plugin plugin, @NotNull Block block, @NotNull String crateId) {
        if (!(block.getState() instanceof TileState tileState))
            return;
        tileState.getPersistentDataContainer().set(crateIdKey(plugin), PersistentDataType.STRING, crateId);
        tileState.update(true, false);
    }

    public static boolean isKeyForCrate(@NotNull Plugin plugin, @NotNull ItemStack item, @NotNull String crateId) {
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        String keyCrate = item.getItemMeta().getPersistentDataContainer()
                .get(keyIdKey(plugin), PersistentDataType.STRING);
        return crateId.equals(keyCrate);
    }

    @NotNull
    public static ItemStack createKeyItem(@NotNull Plugin plugin, @NotNull String crateId,
                                           @NotNull Material material, @NotNull Component displayName) {
        ItemStack key = new ItemStack(material);
        key.setData(DataComponentTypes.CUSTOM_NAME, displayName);
        key.editMeta(meta -> {
            meta.getPersistentDataContainer().set(keyIdKey(plugin), PersistentDataType.STRING, crateId);
        });
        return key;
    }

    @Nullable
    public static String readCrateIdFromItem(@NotNull Plugin plugin, @NotNull ItemStack item) {
        if (item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(crateIdKey(plugin), PersistentDataType.STRING);
    }

    @NotNull
    public static ItemStack writeCrateIdToItem(@NotNull Plugin plugin, @NotNull ItemStack item,
            @NotNull String crateId) {
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(crateIdKey(plugin), PersistentDataType.STRING, crateId);
        });
        return item;
    }

    @NotNull
    public static String readCrateIdFromItemOrBlock(@NotNull Plugin plugin, @Nullable Block block,
            @Nullable ItemStack item) {
        if (block != null) {
            String id = readCrateId(plugin, block);
            if (id != null) return id;
        }
        if (item != null) {
            String id = readCrateIdFromItem(plugin, item);
            if (id != null) return id;
        }
        return "";
    }
}