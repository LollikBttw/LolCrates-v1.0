package com.lolcrates;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrateDefinition {

    private final String id;
    private final Material material;
    @Nullable
    private final String displayName;
    private final List<String> loreLines;
    private final List<Reward> rewards;
    private final Material keyMaterial;
    private final String keyDisplayName;

    private CrateDefinition(
            @NotNull String id,
            @NotNull Material material,
            @Nullable String displayName,
            @NotNull List<String> loreLines,
            @NotNull List<Reward> rewards,
            @NotNull Material keyMaterial,
            @NotNull String keyDisplayName
    ) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.loreLines = loreLines;
        this.rewards = rewards;
        this.keyMaterial = keyMaterial;
        this.keyDisplayName = keyDisplayName;
    }

    @Nullable
    public static CrateDefinition fromConfig(@NotNull String id, @NotNull ConfigurationSection section) {
        String materialName = section.getString("material", "PURPLE_SHULKER_BOX");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isBlock() || !material.name().endsWith("SHULKER_BOX")) {
            return null;
        }

        String displayName = section.getString("display-name");
        List<String> lore = section.getStringList("lore");
        if (lore == null) {
            lore = List.of();
        }

        List<Reward> rewards = parseRewards(section);
        if (rewards.isEmpty()) {
            return null;
        }

        Material keyMaterial = Material.TRIPWIRE_HOOK;
        String keyMaterialRaw = section.getString("key-material");
        if (keyMaterialRaw != null) {
            Material parsed = Material.matchMaterial(keyMaterialRaw);
            if (parsed != null && parsed.isItem()) {
                keyMaterial = parsed;
            }
        }

        String keyDisplayName = section.getString("key-name", "Ключ от кейса");

        return new CrateDefinition(id, material, displayName, List.copyOf(lore), rewards, keyMaterial, keyDisplayName);
    }

    private static @NotNull List<Reward> parseRewards(@NotNull ConfigurationSection section) {
        List<Map<?, ?>> rawRewards = section.getMapList("rewards");
        if (rawRewards == null || rawRewards.isEmpty()) {
            return List.of();
        }

        List<Reward> result = new ArrayList<>();
        for (Map<?, ?> raw : rawRewards) {
            MemoryConfiguration scratch = new MemoryConfiguration();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    scratch.set(key, entry.getValue());
                }
            }
            Reward reward = Reward.fromConfig(scratch);
            if (reward != null) {
                result.add(reward);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public Material getMaterial() {
        return material;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public List<String> getLoreLines() {
        return loreLines;
    }

    @NotNull
    public List<Reward> getRewards() {
        return rewards;
    }

    @NotNull
    public Material getKeyMaterial() {
        return keyMaterial;
    }

    @NotNull
    public String getKeyDisplayName() {
        return keyDisplayName;
    }

    @NotNull
    public String getDisplayLabel() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return id.toUpperCase(Locale.ROOT);
    }
}