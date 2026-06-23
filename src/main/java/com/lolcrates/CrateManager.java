package com.lolcrates;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CrateManager {
   private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
   private final Main plugin;
   private Map<String, CrateDefinition> crates = Map.of();
   private String messagePrefix = "";
   private String openSound = "BLOCK_SHULKER_BOX_OPEN";
   private String closeSound = "BLOCK_SHULKER_BOX_CLOSE";
   private String usePermission = "lolcrates.use";
   private String winMessage = "<prefix><green>Вы выиграли <gold>{reward}</gold>!</green>";
   private boolean openAnimation = true;

   public CrateManager(@NotNull Main plugin) {
      this.plugin = plugin;
   }

   public void load(@NotNull FileConfiguration config) {
      this.messagePrefix = config.getString("settings.message-prefix", "<gray>[<gold>Кейсы</gold>]</gray> ");
      this.openSound = config.getString("settings.open-sound", "BLOCK_SHULKER_BOX_OPEN");
      this.closeSound = config.getString("settings.close-sound", "BLOCK_SHULKER_BOX_CLOSE");
      this.usePermission = config.getString("settings.require-permission", "lolcrates.use");
      this.winMessage = config.getString("settings.win-message", "<prefix><green>Вы выиграли <gold>{reward}</gold>!</green>");
      this.openAnimation = config.getBoolean("settings.open-animation", true);
      this.crates = this.loadCrates(config);
      this.plugin.getLogger().info("Загружено типов кейсов: " + this.crates.size() + ".");
   }

   private Map<String, CrateDefinition> loadCrates(@NotNull FileConfiguration config) {
      ConfigurationSection cratesSection = config.getConfigurationSection("crates");
      if (cratesSection == null) {
         this.plugin.getLogger().warning("Секция crates отсутствует в конфиге.");
         return Map.of();
      } else {
         Map<String, CrateDefinition> loaded = new LinkedHashMap();

         for(String crateId : cratesSection.getKeys(false)) {
            ConfigurationSection section = cratesSection.getConfigurationSection(crateId);
            if (section != null) {
               CrateDefinition definition = CrateDefinition.fromConfig(crateId, section);
               if (definition != null) {
                  loaded.put(crateId, definition);
               } else {
                  this.plugin.getLogger().warning("Пропущен некорректный кейс: " + crateId);
               }
            }
         }

         return Collections.unmodifiableMap(loaded);
      }
   }

   public @NotNull String getUsePermission() {
      return this.usePermission;
   }

   public @Nullable String getCrateId(@NotNull Block block) {
      return CrateKeys.readCrateId(this.plugin, block);
   }

   public boolean hasCrate(@NotNull String crateId) {
      return this.crates.containsKey(crateId);
   }

   public @Nullable CrateDefinition getCrate(@NotNull String crateId) {
      return (CrateDefinition)this.crates.get(crateId);
   }

   public @NotNull List<String> getCrateIds() {
      return List.copyOf(this.crates.keySet());
   }

   public @NotNull ItemStack createKeyItemForCrate(@NotNull String crateId) {
      CrateDefinition crate = (CrateDefinition)this.crates.get(crateId);
      if (crate == null) {
         return new ItemStack(Material.TRIPWIRE_HOOK);
      } else {
         Component name = MiniMessage.miniMessage().deserialize(crate.getKeyDisplayName());
         return CrateKeys.createKeyItem(this.plugin, crateId, crate.getKeyMaterial(), name);
      }
   }

   public @Nullable ItemStack createCrateItem(@NotNull String crateId) {
      CrateDefinition crate = (CrateDefinition)this.crates.get(crateId);
      if (crate == null) {
         return null;
      } else {
         ItemStack item = ItemStack.of(crate.getMaterial(), 1);
         CrateKeys.writeCrateIdToItem(this.plugin, item, crateId);
         if (crate.getDisplayName() != null && !crate.getDisplayName().isBlank()) {
            item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(crate.getDisplayName()));
         }

         if (!crate.getLoreLines().isEmpty()) {
            List<Component> lore = new ArrayList(crate.getLoreLines().size());

            for(String line : crate.getLoreLines()) {
               lore.add(MINI_MESSAGE.deserialize(line));
            }

            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
         }

         return item;
      }
   }

   public boolean giveCrateItem(@NotNull Player player, @NotNull String crateId) {
      ItemStack item = this.createCrateItem(crateId);
      if (item == null) {
         return false;
      } else {
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{item});
         if (!overflow.isEmpty()) {
            for(ItemStack leftover : overflow.values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            player.sendMessage(MINI_MESSAGE.deserialize(this.messagePrefix + "<yellow>Инвентарь полон; кейс выброшен рядом с вами.</yellow>"));
         }

         return true;
      }
   }

   public @Nullable Reward selectReward(@NotNull CrateDefinition crate) {
      List<Reward> rewards = crate.getRewards();
      if (rewards.isEmpty()) {
         return null;
      } else {
         double totalWeight = (double)0.0F;

         for(Reward r : rewards) {
            totalWeight += r.getChance();
         }

         if (totalWeight <= (double)0.0F) {
            return null;
         } else {
            double roll = ThreadLocalRandom.current().nextDouble(totalWeight);

            for(Reward r : rewards) {
               roll -= r.getChance();
               if (roll < (double)0.0F) {
                  return r;
               }
            }

            return (Reward)rewards.get(rewards.size() - 1);
         }
      }
   }

   public void grantReward(@NotNull Player player, @NotNull Reward reward) {
      switch (reward.getType()) {
         case COMMAND -> this.executeCommandReward(player, reward);
         case ITEM -> this.giveItemReward(player, reward);
      }

      Component message = MINI_MESSAGE.deserialize(this.winMessage.replace("<prefix>", this.messagePrefix).replace("{reward}", reward.getName()).replace("{player}", player.getName()));
      player.sendMessage(message);
   }

   private void executeCommandReward(@NotNull Player player, @NotNull Reward reward) {
      String template = reward.getCommandValue();
      if (template != null && !template.isBlank()) {
         String cmd = template.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString()).replace("{world}", player.getWorld().getName());
         Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
      } else {
         this.plugin.getLogger().warning("COMMAND-награда '" + reward.getName() + "' не содержит value.");
      }
   }

   private void giveItemReward(@NotNull Player player, @NotNull Reward reward) {
      if (reward.getMaterial() == null) {
         this.plugin.getLogger().warning("ITEM-награда '" + reward.getName() + "' не содержит material.");
      } else {
         ItemStack item = ItemStack.of(reward.getMaterial(), reward.getAmount());
         if (reward.getDisplayName() != null && !reward.getDisplayName().isBlank()) {
            item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(reward.getDisplayName()));
         }

         if (!reward.getLoreLines().isEmpty()) {
            List<Component> lore = new ArrayList(reward.getLoreLines().size());

            for(String line : reward.getLoreLines()) {
               lore.add(MINI_MESSAGE.deserialize(line));
            }

            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
         }

         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{item});
         if (!overflow.isEmpty()) {
            for(ItemStack leftover : overflow.values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            player.sendMessage(MINI_MESSAGE.deserialize(this.messagePrefix + "<yellow>Инвентарь полон; лишние предметы выброшены рядом с вами.</yellow>"));
         }

      }
   }

   public void playOpenSound(@NotNull Player player, @NotNull Block block) {
      this.playSoundAtBlock(player, block, this.openSound);
   }

   public void playCloseSound(@NotNull Player player, @NotNull Block block) {
      this.playSoundAtBlock(player, block, this.closeSound);
   }

   private void playSoundAtBlock(@NotNull Player player, @NotNull Block block, @NotNull String soundName) {
      try {
         Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
         player.playSound(block.getLocation(), sound, 1.0F, 1.0F);
      } catch (IllegalArgumentException ex) {
         this.plugin.getLogger().log(Level.WARNING, "Некорректный звук в конфиге: " + soundName, ex);
      }

   }

   private Material resolveDisplayMaterial(Reward reward) {
      if (reward.getDisplayMaterial() != null) {
         return reward.getDisplayMaterial();
      } else if (reward.getMaterial() != null) {
         return reward.getMaterial();
      } else {
         String name = reward.getName().toLowerCase();
         if (!name.contains("алмаз") && !name.contains("diamond")) {
            if (!name.contains("изумруд") && !name.contains("emerald")) {
               if (!name.contains("желез") && !name.contains("iron")) {
                  if (!name.contains("золот") && !name.contains("gold")) {
                     if (!name.contains("кирк") && !name.contains("pickaxe")) {
                        if (!name.contains("меч") && !name.contains("sword")) {
                           if (!name.contains("незерит") && !name.contains("netherite")) {
                              if (!name.contains("книг") && !name.contains("book")) {
                                 if (!name.contains("тотем") && !name.contains("totem")) {
                                    if (!name.contains("элитр") && !name.contains("elytra")) {
                                       if (!name.contains("опыт") && !name.contains("xp") && !name.contains("experience")) {
                                          if (!name.contains("кварц") && !name.contains("quartz")) {
                                             return !name.contains("шалк") && !name.contains("shulker") ? Material.PAPER : Material.SHULKER_SHELL;
                                          } else {
                                             return Material.QUARTZ;
                                          }
                                       } else {
                                          return Material.EXPERIENCE_BOTTLE;
                                       }
                                    } else {
                                       return Material.ELYTRA;
                                    }
                                 } else {
                                    return Material.TOTEM_OF_UNDYING;
                                 }
                              } else {
                                 return Material.ENCHANTED_BOOK;
                              }
                           } else {
                              return Material.NETHERITE_INGOT;
                           }
                        } else {
                           return Material.DIAMOND_SWORD;
                        }
                     } else {
                        return Material.DIAMOND_PICKAXE;
                     }
                  } else {
                     return Material.GOLD_INGOT;
                  }
               } else {
                  return Material.IRON_INGOT;
               }
            } else {
               return Material.EMERALD;
            }
         } else {
            return Material.DIAMOND;
         }
      }
   }

   private Display createDisplayForReward(Location loc, Reward reward) {
      String text = reward.getDisplayText();
      if (text != null && !text.isBlank()) {
         TextDisplay display = (TextDisplay)loc.getWorld().spawn(loc, TextDisplay.class);
         display.text(MINI_MESSAGE.deserialize(text));
         display.setBillboard(Billboard.CENTER);
         display.setBackgroundColor(Color.fromARGB(0));
         display.setShadowed(false);
         display.setSeeThrough(true);
         return display;
      } else {
         Material mat = this.resolveDisplayMaterial(reward);
         ItemStack stack = ItemStack.of(mat, 1);
         ItemDisplay display = (ItemDisplay)loc.getWorld().spawn(loc, ItemDisplay.class);
         display.setItemStack(stack);
         display.setBillboard(Billboard.CENTER);
         return display;
      }
   }

   private void transform(Display display, Vector3f translation, Vector3f scale, Quaternionf rotation, int duration) {
      Transformation t = new Transformation(translation, rotation, scale, new Quaternionf());
      display.setInterpolationDuration(duration);
      display.setTransformation(t);
   }

   private void removeDisplays(List<Display> displays) {
      for(Display d : displays) {
         if (d != null && d.isValid()) {
            d.remove();
         }
      }

   }

   public void startAnimation(@NotNull Block block, @NotNull CrateDefinition crate, @NotNull Player player) {
      if (!this.openAnimation) {
         Reward winner = this.selectReward(crate);
         if (winner != null) {
            this.playOpenSound(player, block);
            this.grantReward(player, winner);
         }

      } else {
         List<Reward> rewards = crate.getRewards();
         if (rewards.isEmpty()) {
            player.sendMessage(MINI_MESSAGE.deserialize(this.messagePrefix + "<red>У этого кейса нет наград.</red>"));
         } else {
            this.playOpenSound(player, block);
            switch (crate.getAnimationType()) {
               case MAGNET_VORTEX -> this.magnetVortexAnimation(block, crate, player);
               case QUANTUM_SHUFFLE -> this.quantumShuffleAnimation(block, crate, player);
               case RISING_GRID -> this.risingGridAnimation(block, crate, player);
               case ASTRAL_BURST -> this.astralBurstAnimation(block, crate, player);
               default -> this.orbitAnimation(block, crate, player);
            }

         }
      }
   }

   private void orbitAnimation(final @NotNull Block block, final @NotNull CrateDefinition crate, final @NotNull Player player) {
      final Location center = block.getLocation().add((double)0.5F, 1.8, (double)0.5F);
      List<Reward> rewards = crate.getRewards();
      final int count = rewards.size();
      final List<Display> displays = new ArrayList();

      for(int i = 0; i < count; ++i) {
         displays.add(this.createDisplayForReward(center, (Reward)rewards.get(i)));
      }

      final int rise = 40;
      final int orbit = 120;
      final int slow = 50;
      (new BukkitRunnable() {
         int tick = 0;

         public void run() {
            ++this.tick;
            if (this.tick <= rise) {
               double p = (double)this.tick / (double)rise;

               for(int i = 0; i < count; ++i) {
                  double a = (Math.PI * 2D) / (double)count * (double)i;
                  float r = 0.5F * (float)p;
                  float y = (float)(p * 1.8);
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)Math.cos(a) * r, y, (float)Math.sin(a) * r), new Vector3f(0.6F, 0.6F, 0.6F), (new Quaternionf()).rotateY((float)a), 5);
               }
            } else if (this.tick <= rise + orbit) {
               int ot = this.tick - rise;
               double angle = (double)ot * 0.06;
               double radius = 1.3;
               double bounce = Math.sin((double)ot * 0.04) * 0.3;

               for(int i = 0; i < count; ++i) {
                  double a = angle + (Math.PI * 2D) / (double)count * (double)i;
                  double y = Math.sin(a * (double)1.5F) * 0.7 + bounce;
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)(Math.cos(a) * radius), (float)y, (float)(Math.sin(a) * radius)), new Vector3f(0.6F, 0.6F, 0.6F), (new Quaternionf()).rotateY((float)(a + (double)ot * 0.02)), 5);
               }

               block.getWorld().spawnParticle(Particle.PORTAL, center, 1, 0.3, 0.3, 0.3, 0.02);
            } else if (this.tick <= rise + orbit + slow) {
               int st = this.tick - rise - orbit;
               double angle = (double)orbit * 0.06 + (double)st * 0.02;
               float radius = Math.max(0.2F, 1.3F * (1.0F - (float)st / (float)slow));
               float bounce = Math.max(0.0F, 0.3F * (1.0F - (float)st / (float)slow));
               if (st % 6 == 0) {
                  block.getWorld().spawnParticle(Particle.ENCHANT, center, 3, 0.4, 0.4, 0.4, (double)0.0F);
               }

               for(int i = 0; i < count; ++i) {
                  double a = angle + (Math.PI * 2D) / (double)count * (double)i;
                  double y = Math.sin(a * (double)1.5F) * 0.7 + (double)bounce;
                  int dur = 5 + st / 3;
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)(Math.cos(a) * (double)radius), (float)y, (float)(Math.sin(a) * (double)radius)), new Vector3f(0.6F, 0.6F, 0.6F), (new Quaternionf()).rotateY((float)a), dur);
               }

               if (st >= slow) {
                  CrateManager.this.finishAnimation(displays, block, center, crate, player);
                  this.cancel();
               }
            }

         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private void magnetVortexAnimation(final @NotNull Block block, final @NotNull CrateDefinition crate, final @NotNull Player player) {
      final Location center = block.getLocation().add((double)0.5F, 1.8, (double)0.5F);
      List<Reward> rewards = crate.getRewards();
      final int count = rewards.size();
      final List<Display> displays = new ArrayList();

      for(int i = 0; i < count; ++i) {
         displays.add(this.createDisplayForReward(center, (Reward)rewards.get(i)));
      }

      final int total = 140;
      (new BukkitRunnable() {
         int tick = 0;

         public void run() {
            ++this.tick;
            double p = (double)this.tick / (double)total;
            if (this.tick <= total - 20) {
               double speed = Math.sin(p * Math.PI) * (double)3.0F + (double)1.0F;
               double angle = (double)this.tick * 0.08 * speed;
               double height = p * (double)2.5F;
               double radius = (double)1.5F * ((double)1.0F - p * 0.7);
               float scale = 0.3F + (float)((double)0.5F * Math.sin(p * Math.PI));

               for(int i = 0; i < count; ++i) {
                  double a = angle + (Math.PI * 2D) / (double)count * (double)i;
                  float x = (float)(Math.cos(a) * radius);
                  float z = (float)(Math.sin(a) * radius);
                  float y = (float)(height + Math.sin(a * (double)2.0F + p * (double)4.0F) * 0.3);
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f(x, y, z), new Vector3f(scale, scale, scale), (new Quaternionf()).rotateY((float)(a + p * (double)3.0F)), 5);
               }

               block.getWorld().spawnParticle(Particle.WITCH, center, 1, (double)0.5F * radius, (double)0.5F, (double)0.5F * radius, (double)0.0F);
            }

            if (this.tick >= total) {
               CrateManager.this.finishAnimation(displays, block, center, crate, player);
               this.cancel();
            }

         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private void quantumShuffleAnimation(final @NotNull Block block, final @NotNull CrateDefinition crate, final @NotNull Player player) {
      final Location center = block.getLocation().add((double)0.5F, 1.8, (double)0.5F);
      List<Reward> rewards = crate.getRewards();
      final int count = rewards.size();
      final List<Display> displays = new ArrayList();

      for(int i = 0; i < count; ++i) {
         displays.add(this.createDisplayForReward(center, (Reward)rewards.get(i)));
      }

      for(Display d : displays) {
         this.transform(d, new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), 0);
      }

      (new BukkitRunnable() {
         int tick = 0;
         int current = 0;
         int shuffles = 0;
         final int maxShuffles = 40;

         public void run() {
            ++this.tick;
            if (this.shuffles < 40) {
               if (this.tick % 3 == 0) {
                  CrateManager.this.transform((Display)displays.get(this.current), new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), 4);
                  this.current = ThreadLocalRandom.current().nextInt(count);
                  CrateManager.this.transform((Display)displays.get(this.current), new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.9F, 0.9F, 0.9F), new Quaternionf(), 5);
                  block.getWorld().spawnParticle(Particle.ENCHANT, center, 2, 0.3, 0.3, 0.3, 0.2);
                  ++this.shuffles;
               }

               if (this.shuffles >= 40) {
                  Bukkit.getScheduler().runTaskLater(CrateManager.this.plugin, () -> CrateManager.this.finishAnimation(displays, block, center, crate, player), 20L);
               }
            }

            if (this.shuffles >= 40 && this.tick >= 140) {
               this.cancel();
            }

         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private void risingGridAnimation(final @NotNull Block block, final @NotNull CrateDefinition crate, final @NotNull Player player) {
      final Location center = block.getLocation().add((double)0.5F, 1.8, (double)0.5F);
      List<Reward> rewards = crate.getRewards();
      final int count = rewards.size();
      final List<Display> displays = new ArrayList();

      for(int i = 0; i < count; ++i) {
         displays.add(this.createDisplayForReward(center, (Reward)rewards.get(i)));
      }

      final int total = 120;
      (new BukkitRunnable() {
         int tick = 0;

         public void run() {
            ++this.tick;
            double p = (double)this.tick / (double)total;
            if (this.tick <= total) {
               for(int i = 0; i < count; ++i) {
                  double a = (Math.PI * 2D) / (double)count * (double)i;
                  double rise = Math.min((double)1.0F, (double)this.tick / (double)35.0F);
                  double peak = (double)2.0F + (double)(i % 3) * 0.4;
                  double fall = Math.max((double)0.0F, (double)(this.tick - 45) / (double)45.0F) * (peak - 0.3);
                  double y = peak * rise - fall;
                  double radius = 0.6 + (double)i / (double)count * 0.8;
                  float x = (float)(Math.cos(a) * radius);
                  float z = (float)(Math.sin(a) * radius);
                  float scale = 0.3F + (float)((double)0.5F * ((double)1.0F - Math.abs(p - (double)0.5F) * (double)2.0F));
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f(x, (float)y, z), new Vector3f(scale, scale, scale), (new Quaternionf()).rotateY((float)(a + p * (double)4.0F)), 5);
               }

               if (this.tick % 3 == 0) {
                  block.getWorld().spawnParticle(Particle.END_ROD, center, 1, 0.6, 0.8, 0.6, 0.02);
               }
            }

            if (this.tick >= total) {
               CrateManager.this.finishAnimation(displays, block, center, crate, player);
               this.cancel();
            }

         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private void astralBurstAnimation(final @NotNull Block block, final @NotNull CrateDefinition crate, final @NotNull Player player) {
      final Location center = block.getLocation().add((double)0.5F, 1.8, (double)0.5F);
      List<Reward> rewards = crate.getRewards();
      final int count = rewards.size();
      final List<Display> displays = new ArrayList();

      for(int i = 0; i < count; ++i) {
         displays.add(this.createDisplayForReward(center, (Reward)rewards.get(i)));
      }

      for(Display d : displays) {
         this.transform(d, new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), 0);
      }

      final int burst = 25;
      final int hold = 35;
      final int converge = 30;
      (new BukkitRunnable() {
         int tick = 0;

         public void run() {
            ++this.tick;
            if (this.tick <= burst) {
               double p = (double)this.tick / (double)burst;

               for(int i = 0; i < count; ++i) {
                  double a = (Math.PI * 2D) / (double)count * (double)i - p * 0.3;
                  float radius = 2.0F * (float)p;
                  float y = (float)(Math.sin(a * (double)2.0F) * 0.6 * p);
                  float scale = (float)(0.2 + 0.6 * p);
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)Math.cos(a) * radius, y, (float)Math.sin(a) * radius), new Vector3f(scale, scale, scale), (new Quaternionf()).rotateY((float)(a * (double)2.0F)), 5);
               }

               block.getWorld().spawnParticle(Particle.FIREWORK, center, 3, (double)0.5F, (double)0.5F, (double)0.5F, 0.05);
            } else if (this.tick <= burst + hold) {
               float pulse = 1.0F + (float)Math.sin((double)this.tick * 0.08) * 0.05F;

               for(int i = 0; i < count; ++i) {
                  double a = (Math.PI * 2D) / (double)count * (double)i - (double)burst * 0.3;
                  float radius = 2.0F;
                  float y = (float)(Math.sin(a * (double)2.0F) * 0.6);
                  CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)Math.cos(a) * radius, y, (float)Math.sin(a) * radius), new Vector3f(0.8F * pulse, 0.8F * pulse, 0.8F * pulse), (new Quaternionf()).rotateY((float)(a * (double)2.0F + (double)this.tick * 0.02)), 6);
               }

               if (this.tick % 4 == 0) {
                  block.getWorld().spawnParticle(Particle.GLOW, center, 2, 0.8, 0.8, 0.8, (double)0.0F);
               }
            } else {
               int ct = this.tick - burst - hold;
               double p = (double)ct / (double)converge;
               int winnerIdx = CrateManager.this.selectRewardIndex(crate) % count;

               for(int i = 0; i < count; ++i) {
                  double a = (Math.PI * 2D) / (double)count * (double)i - (double)burst * 0.3;
                  float radius = 2.0F * (1.0F - (float)p);
                  float y = (float)(Math.sin(a * (double)2.0F) * 0.6 * ((double)1.0F - p));
                  float s = 0.8F * (1.0F - (float)p);
                  if (i == winnerIdx) {
                     CrateManager.this.transform((Display)displays.get(i), new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.9F, 0.9F, 0.9F), new Quaternionf(), 6);
                  } else {
                     CrateManager.this.transform((Display)displays.get(i), new Vector3f((float)Math.cos(a) * radius, y, (float)Math.sin(a) * radius), new Vector3f(s, s, s), (new Quaternionf()).rotateY((float)(a * (double)2.0F)), 5);
                  }
               }

               block.getWorld().spawnParticle(Particle.FIREWORK, center, 2, 0.3, 0.3, 0.3, 0.03);
               if (ct >= converge) {
                  CrateManager.this.finishAnimation(displays, block, center, crate, player);
                  this.cancel();
               }
            }

         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private int selectRewardIndex(CrateDefinition crate) {
      List<Reward> rewards = crate.getRewards();
      if (rewards.isEmpty()) {
         return 0;
      } else {
         Reward r = this.selectReward(crate);
         return r == null ? 0 : rewards.indexOf(r);
      }
   }

   private void finishAnimation(List<Display> displays, Block block, Location center, CrateDefinition crate, Player player) {
      this.removeDisplays(displays);
      Reward winner = this.selectReward(crate);
      if (winner != null) {
         Material winMat = this.resolveDisplayMaterial(winner);
         ItemStack winStack = ItemStack.of(winMat, winner.getAmount());
         ItemDisplay winDisplay = (ItemDisplay)block.getWorld().spawn(center, ItemDisplay.class);
         winDisplay.setItemStack(winStack);
         winDisplay.setBillboard(Billboard.CENTER);
         this.transform(winDisplay, new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), 1);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.transform(winDisplay, new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.9F, 0.9F, 0.9F), new Quaternionf(), 12), 1L);
         block.getWorld().spawnParticle(Particle.FLAME, center.clone().add((double)0.0F, (double)0.5F, (double)0.0F), 30, 0.3, 0.3, 0.3, 0.03);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            winDisplay.remove();
            this.playCloseSound(player, block);
            this.grantReward(player, winner);
         }, 30L);
      }
   }
}
