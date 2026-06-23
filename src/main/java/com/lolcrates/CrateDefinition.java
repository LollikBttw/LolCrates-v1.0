package com.lolcrates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CrateDefinition {
   private final String id;
   private final Material material;
   private final @Nullable String displayName;
   private final List<String> loreLines;
   private final List<Reward> rewards;
   private final Material keyMaterial;
   private final String keyDisplayName;
   private final CrateAnimationType animationType;

   private CrateDefinition(@NotNull String id, @NotNull Material material, @Nullable String displayName, @NotNull List<String> loreLines, @NotNull List<Reward> rewards, @NotNull Material keyMaterial, @NotNull String keyDisplayName, @NotNull CrateAnimationType animationType) {
      this.id = id;
      this.material = material;
      this.displayName = displayName;
      this.loreLines = loreLines;
      this.rewards = rewards;
      this.keyMaterial = keyMaterial;
      this.keyDisplayName = keyDisplayName;
      this.animationType = animationType;
   }

   public static @Nullable CrateDefinition fromConfig(@NotNull String id, @NotNull ConfigurationSection section) {
      String materialName = section.getString("material", "PURPLE_SHULKER_BOX");
      Material material = Material.matchMaterial(materialName);
      if (material != null && material.isBlock() && material.name().endsWith("SHULKER_BOX")) {
         String displayName = section.getString("display-name");
         List<String> lore = section.getStringList("lore");
         if (lore == null) {
            lore = List.of();
         }

         List<Reward> rewards = parseRewards(section);
         if (rewards.isEmpty()) {
            return null;
         } else {
            Material keyMaterial = Material.TRIPWIRE_HOOK;
            String keyMaterialRaw = section.getString("key-material");
            if (keyMaterialRaw != null) {
               Material parsed = Material.matchMaterial(keyMaterialRaw);
               if (parsed != null && parsed.isItem()) {
                  keyMaterial = parsed;
               }
            }

            String keyDisplayName = section.getString("key-name", "Ключ от кейса");
            CrateAnimationType animType = CrateAnimationType.ORBIT;
            String animRaw = section.getString("animation-type");
            if (animRaw != null) {
               try {
                  animType = CrateAnimationType.valueOf(animRaw.toUpperCase(Locale.ROOT));
               } catch (IllegalArgumentException var13) {
               }
            }

            return new CrateDefinition(id, material, displayName, List.copyOf(lore), rewards, keyMaterial, keyDisplayName, animType);
         }
      } else {
         return null;
      }
   }

   private static @NotNull List<Reward> parseRewards(@NotNull ConfigurationSection section) {
      List<Map<?, ?>> rawRewards = section.getMapList("rewards");
      if (rawRewards != null && !rawRewards.isEmpty()) {
         List<Reward> result = new ArrayList();

         for(Map<?, ?> raw : rawRewards) {
            MemoryConfiguration scratch = new MemoryConfiguration();

            for(Map.Entry<?, ?> entry : raw.entrySet()) {
               Object var9 = entry.getKey();
               if (var9 instanceof String) {
                  String key = (String)var9;
                  scratch.set(key, entry.getValue());
               }
            }

            Reward reward = Reward.fromConfig(scratch);
            if (reward != null) {
               result.add(reward);
            }
         }

         return Collections.unmodifiableList(result);
      } else {
         return List.of();
      }
   }

   public @NotNull String getId() {
      return this.id;
   }

   public @NotNull Material getMaterial() {
      return this.material;
   }

   public @Nullable String getDisplayName() {
      return this.displayName;
   }

   public @NotNull List<String> getLoreLines() {
      return this.loreLines;
   }

   public @NotNull List<Reward> getRewards() {
      return this.rewards;
   }

   public @NotNull Material getKeyMaterial() {
      return this.keyMaterial;
   }

   public @NotNull String getKeyDisplayName() {
      return this.keyDisplayName;
   }

   public @NotNull CrateAnimationType getAnimationType() {
      return this.animationType;
   }

   public @NotNull String getDisplayLabel() {
      return this.displayName != null && !this.displayName.isBlank() ? this.displayName : this.id.toUpperCase(Locale.ROOT);
   }
}
