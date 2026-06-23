package com.lolcrates;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Reward {
   private final String name;
   private final double chance;
   private final Type type;
   private final @Nullable String commandValue;
   private final @Nullable Material material;
   private final @Nullable Material displayMaterial;
   private final @Nullable String displayText;
   private final int amount;
   private final @Nullable String displayName;
   private final List<String> loreLines;

   private Reward(@NotNull String name, double chance, @NotNull Type type, @Nullable String commandValue, @Nullable Material material, @Nullable Material displayMaterial, @Nullable String displayText, int amount, @Nullable String displayName, @NotNull List<String> loreLines) {
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

   public static @Nullable Reward fromConfig(@NotNull ConfigurationSection section) {
      String name = section.getString("name");
      if (name != null && !name.isBlank()) {
         double chance = section.getDouble("chance", (double)0.0F);
         if (chance <= (double)0.0F) {
            return null;
         } else {
            String typeRaw = section.getString("type", "COMMAND");

            Type type;
            try {
               type = Reward.Type.valueOf(typeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var14) {
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
            Reward var10000;
            switch (type.ordinal()) {
               case 0:
                  String value = section.getString("value");
                  var10000 = value != null && !value.isBlank() ? new Reward(name, chance, type, value, (Material)null, displayMaterial, displayText, 1, (String)null, List.of()) : null;
                  break;
               case 1:
                  String materialName = section.getString("material");
                  if (materialName != null && !materialName.isBlank()) {
                     Material material = Material.matchMaterial(materialName);
                     if (material != null && material.isItem()) {
                        int amount = Math.max(1, section.getInt("amount", 1));
                        String displayName = section.getString("display-name");
                        List<String> lore = section.getStringList("lore");
                        var10000 = new Reward(name, chance, type, (String)null, material, displayMaterial, displayText, amount, displayName, lore == null ? List.of() : List.copyOf(lore));
                     } else {
                        var10000 = null;
                     }
                  } else {
                     var10000 = null;
                  }
                  break;
               default:
                  throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
         }
      } else {
         return null;
      }
   }

   public @NotNull String getName() {
      return this.name;
   }

   public double getChance() {
      return this.chance;
   }

   public @NotNull Type getType() {
      return this.type;
   }

   public @Nullable String getCommandValue() {
      return this.commandValue;
   }

   public @Nullable Material getMaterial() {
      return this.material;
   }

   public @Nullable Material getDisplayMaterial() {
      return this.displayMaterial;
   }

   public @Nullable String getDisplayText() {
      return this.displayText;
   }

   public int getAmount() {
      return this.amount;
   }

   public @Nullable String getDisplayName() {
      return this.displayName;
   }

   public @NotNull List<String> getLoreLines() {
      return Collections.unmodifiableList(this.loreLines);
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof Reward)) {
         return false;
      } else {
         Reward reward = (Reward)o;
         return Double.compare(reward.chance, this.chance) == 0 && this.amount == reward.amount && this.name.equals(reward.name) && this.type == reward.type && Objects.equals(this.commandValue, reward.commandValue) && this.material == reward.material && Objects.equals(this.displayName, reward.displayName) && this.loreLines.equals(reward.loreLines);
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.name, this.chance, this.type, this.commandValue, this.material, this.amount, this.displayName, this.loreLines});
   }

   public String toString() {
      String var10000 = this.name;
      return "Reward{name='" + var10000 + "', chance=" + this.chance + ", type=" + String.valueOf(this.type) + "}";
   }

   public static enum Type {
      COMMAND,
      ITEM;

      // $FF: synthetic method
      private static Type[] $values() {
         return new Type[]{COMMAND, ITEM};
      }
   }
}
