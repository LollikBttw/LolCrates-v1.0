package com.lolcrates;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Reward {

    public enum Type {
        COMMAND,
        ITEM
    }

    private final String name;
    private final double chance;
    private final Type type;

    @Nullable
    private final String commandValue;

    @Nullable
    private final Material material;
    @Nullable
    private final Material displayMaterial;
    @Nullable
    private final String displayText;
    private final int amount;
    @Nullable
    private final String displayName;
    private final List<String> loreLines;

    private Reward(
            @NotNull String name,
            double chance,
            @NotNull Type type,
            @Nullable String commandValue,
            @Nullable Material material,
            @Nullable Material displayMaterial,
            @Nullable String displayText,
            int amount,
            @Nullable String displayName,
            @NotNull List<String> loreLines) {
        this.name = name;
        this.chance = chance;
        this.type = type;
        this.commandValue = commandValue;
        this.material = material;
        this.displayMaterial = displayMaterial;
        this.displayText = displayText;
        this.amount = amount;
        this.displayName = displayName;
        this.loreLines = loreLines;
    }

    @Nullable
    public static Reward fromConfig(@NotNull ConfigurationSection section) {
        String name = section.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }

        double chance = section.getDouble("chance", 0.0D);
        if (chance <= 0.0D) {
            return null;
        }

        String typeRaw = section.getString("type", "COMMAND");
        Type type;
        try {
            type = Type.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        Material displayMaterial = null;
        String displayMatRaw = section.getString("display-material");
        if (displayMatRaw != null) {
            Material parsed = Material.matchMaterial(displayMatRaw);
            if (parsed != null && parsed.isItem()) {
                displayMaterial = parsed;
            }
        }

        String displayText = section.getString("display-text");

        return switch (type) {
            case COMMAND -> {
                String value = section.getString("value");
                if (value == null || value.isBlank()) {
                    yield null;
                }
                yield new Reward(name, chance, type, value, null, displayMaterial, displayText, 1, null, List.of());
            }
            case ITEM -> {
                String materialName = section.getString("material");
                if (materialName == null || materialName.isBlank()) {
                    yield null;
                }
                Material material = Material.matchMaterial(materialName);
                if (material == null || !material.isItem()) {
                    yield null;
                }
                int amount = Math.max(1, section.getInt("amount", 1));
                String displayName = section.getString("display-name");
                List<String> lore = section.getStringList("lore");
                yield new Reward(
                        name,
                        chance,
                        type,
                        null,
                        material,
                        displayMaterial,
                        displayText,
                        amount,
                        displayName,
                        lore == null ? List.of() : List.copyOf(lore));
            }
        };
    }

    @NotNull
    public String getName() {
        return name;
    }

    public double getChance() {
        return chance;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    @Nullable
    public String getCommandValue() {
        return commandValue;
    }

    @Nullable
    public Material getMaterial() {
        return material;
    }

    @Nullable
    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    @Nullable
    public String getDisplayText() {
        return displayText;
    }

    public int getAmount() {
        return amount;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public List<String> getLoreLines() {
        return Collections.unmodifiableList(loreLines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reward reward)) {
            return false;
        }
        return Double.compare(reward.chance, chance) == 0
                && amount == reward.amount
                && name.equals(reward.name)
                && type == reward.type
                && Objects.equals(commandValue, reward.commandValue)
                && material == reward.material
                && Objects.equals(displayName, reward.displayName)
                && loreLines.equals(reward.loreLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, chance, type, commandValue, material, amount, displayName, loreLines);
    }

    @Override
    public String toString() {
        return "Reward{name='" + name + "', chance=" + chance + ", type=" + type + "}";
    }
}