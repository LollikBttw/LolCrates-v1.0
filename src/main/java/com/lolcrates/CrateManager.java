package com.lolcrates;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

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
        messagePrefix = config.getString("settings.message-prefix", "<gray>[<gold>Кейсы</gold>]</gray> ");
        openSound = config.getString("settings.open-sound", "BLOCK_SHULKER_BOX_OPEN");
        closeSound = config.getString("settings.close-sound", "BLOCK_SHULKER_BOX_CLOSE");
        usePermission = config.getString("settings.require-permission", "lolcrates.use");
        winMessage = config.getString("settings.win-message",
                "<prefix><green>Вы выиграли <gold>{reward}</gold>!</green>");
        openAnimation = config.getBoolean("settings.open-animation", true);

        crates = loadCrates(config);
        plugin.getLogger().info("Загружено типов кейсов: " + crates.size() + ".");
    }

    private Map<String, CrateDefinition> loadCrates(@NotNull FileConfiguration config) {
        ConfigurationSection cratesSection = config.getConfigurationSection("crates");
        if (cratesSection == null) {
            plugin.getLogger().warning("Секция crates отсутствует в конфиге.");
            return Map.of();
        }

        Map<String, CrateDefinition> loaded = new LinkedHashMap<>();
        for (String crateId : cratesSection.getKeys(false)) {
            ConfigurationSection section = cratesSection.getConfigurationSection(crateId);
            if (section == null) continue;

            CrateDefinition definition = CrateDefinition.fromConfig(crateId, section);
            if (definition != null) {
                loaded.put(crateId, definition);
            } else {
                plugin.getLogger().warning("Пропущен некорректный кейс: " + crateId);
            }
        }

        return Collections.unmodifiableMap(loaded);
    }

    @NotNull
    public String getUsePermission() {
        return usePermission;
    }

    @Nullable
    public String getCrateId(@NotNull Block block) {
        return CrateKeys.readCrateId(plugin, block);
    }

    public boolean hasCrate(@NotNull String crateId) {
        return crates.containsKey(crateId);
    }

    @Nullable
    public CrateDefinition getCrate(@NotNull String crateId) {
        return crates.get(crateId);
    }

    @NotNull
    public List<String> getCrateIds() {
        return List.copyOf(crates.keySet());
    }

    @NotNull
    public ItemStack createKeyItemForCrate(@NotNull String crateId) {
        CrateDefinition crate = crates.get(crateId);
        if (crate == null) {
            return new ItemStack(Material.TRIPWIRE_HOOK);
        }
        net.kyori.adventure.text.Component name = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(crate.getKeyDisplayName());
        return CrateKeys.createKeyItem(plugin, crateId, crate.getKeyMaterial(), name);
    }

    @Nullable
    public ItemStack createCrateItem(@NotNull String crateId) {
        CrateDefinition crate = crates.get(crateId);
        if (crate == null) return null;

        ItemStack item = ItemStack.of(crate.getMaterial(), 1);
        CrateKeys.writeCrateIdToItem(plugin, item, crateId);

        if (crate.getDisplayName() != null && !crate.getDisplayName().isBlank()) {
            item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(crate.getDisplayName()));
        }

        if (!crate.getLoreLines().isEmpty()) {
            List<Component> lore = new ArrayList<>(crate.getLoreLines().size());
            for (String line : crate.getLoreLines()) {
                lore.add(MINI_MESSAGE.deserialize(line));
            }
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }

        return item;
    }

    public boolean giveCrateItem(@NotNull Player player, @NotNull String crateId) {
        ItemStack item = createCrateItem(crateId);
        if (item == null) return false;

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage(MINI_MESSAGE.deserialize(
                    messagePrefix + "<yellow>Инвентарь полон; кейс выброшен рядом с вами.</yellow>"));
        }
        return true;
    }

    @Nullable
    public Reward selectReward(@NotNull CrateDefinition crate) {
        List<Reward> rewards = crate.getRewards();
        if (rewards.isEmpty()) return null;

        double totalWeight = 0.0;
        for (Reward r : rewards) {
            totalWeight += r.getChance();
        }
        if (totalWeight <= 0.0) return null;

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        for (Reward r : rewards) {
            roll -= r.getChance();
            if (roll < 0.0) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    public void grantReward(@NotNull Player player, @NotNull Reward reward) {
        switch (reward.getType()) {
            case COMMAND -> executeCommandReward(player, reward);
            case ITEM -> giveItemReward(player, reward);
        }

        Component message = MINI_MESSAGE.deserialize(
                winMessage
                        .replace("<prefix>", messagePrefix)
                        .replace("{reward}", reward.getName())
                        .replace("{player}", player.getName()));
        player.sendMessage(message);
    }

    private void executeCommandReward(@NotNull Player player, @NotNull Reward reward) {
        String template = reward.getCommandValue();
        if (template == null || template.isBlank()) {
            plugin.getLogger().warning("COMMAND-награда '" + reward.getName() + "' не содержит value.");
            return;
        }

        String cmd = template
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName());

        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }

    private void giveItemReward(@NotNull Player player, @NotNull Reward reward) {
        if (reward.getMaterial() == null) {
            plugin.getLogger().warning("ITEM-награда '" + reward.getName() + "' не содержит material.");
            return;
        }

        ItemStack item = ItemStack.of(reward.getMaterial(), reward.getAmount());

        if (reward.getDisplayName() != null && !reward.getDisplayName().isBlank()) {
            item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(reward.getDisplayName()));
        }
        if (!reward.getLoreLines().isEmpty()) {
            List<Component> lore = new ArrayList<>(reward.getLoreLines().size());
            for (String line : reward.getLoreLines()) {
                lore.add(MINI_MESSAGE.deserialize(line));
            }
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage(MINI_MESSAGE.deserialize(
                    messagePrefix + "<yellow>Инвентарь полон; лишние предметы выброшены рядом с вами.</yellow>"));
        }
    }

    public void playOpenSound(@NotNull Player player, @NotNull Block block) {
        playSoundAtBlock(player, block, openSound);
    }

    public void playCloseSound(@NotNull Player player, @NotNull Block block) {
        playSoundAtBlock(player, block, closeSound);
    }

    private void playSoundAtBlock(@NotNull Player player, @NotNull Block block, @NotNull String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
            player.playSound(block.getLocation(), sound, 1.0F, 1.0F);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Некорректный звук в конфиге: " + soundName, ex);
        }
    }

    private Material resolveDisplayMaterial(Reward reward) {
        if (reward.getDisplayMaterial() != null) return reward.getDisplayMaterial();
        if (reward.getMaterial() != null) return reward.getMaterial();
        String name = reward.getName().toLowerCase();
        if (name.contains("алмаз") || name.contains("diamond")) return Material.DIAMOND;
        if (name.contains("изумруд") || name.contains("emerald")) return Material.EMERALD;
        if (name.contains("желез") || name.contains("iron")) return Material.IRON_INGOT;
        if (name.contains("золот") || name.contains("gold")) return Material.GOLD_INGOT;
        if (name.contains("кирк") || name.contains("pickaxe")) return Material.DIAMOND_PICKAXE;
        if (name.contains("меч") || name.contains("sword")) return Material.DIAMOND_SWORD;
        if (name.contains("незерит") || name.contains("netherite")) return Material.NETHERITE_INGOT;
        if (name.contains("книг") || name.contains("book")) return Material.ENCHANTED_BOOK;
        if (name.contains("тотем") || name.contains("totem")) return Material.TOTEM_OF_UNDYING;
        if (name.contains("элитр") || name.contains("elytra")) return Material.ELYTRA;
        if (name.contains("опыт") || name.contains("xp") || name.contains("experience")) return Material.EXPERIENCE_BOTTLE;
        if (name.contains("кварц") || name.contains("quartz")) return Material.QUARTZ;
        if (name.contains("шалк") || name.contains("shulker")) return Material.SHULKER_SHELL;
        return Material.PAPER;
    }

    public void startAnimation(@NotNull Block block, @NotNull CrateDefinition crate, @NotNull Player player) {
        if (!openAnimation) {
            Reward winner = selectReward(crate);
            if (winner != null) {
                playOpenSound(player, block);
                grantReward(player, winner);
            }
            return;
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        List<Reward> rewards = crate.getRewards();

        if (rewards.isEmpty()) {
            player.sendMessage(MINI_MESSAGE.deserialize(
                    messagePrefix + "<red>У этого кейса нет наград.</red>"));
            return;
        }

        playOpenSound(player, block);

        List<Object> displayElements = new ArrayList<>(); // Item или ArmorStand
        for (Reward r : rewards) {
            String text = r.getDisplayText();
            if (text != null && !text.isBlank()) {
                ArmorStand stand = block.getWorld().spawn(center, ArmorStand.class);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.customName(MINI_MESSAGE.deserialize(text));
                stand.setCustomNameVisible(true);
                displayElements.add(stand);
            } else {
                Material mat = resolveDisplayMaterial(r);
                ItemStack stack = ItemStack.of(mat, 1);
                Item drop = block.getWorld().dropItem(center, stack);
                drop.setPickupDelay(Integer.MAX_VALUE);
                drop.setGravity(false);
                drop.setVelocity(new Vector(0, 0.1, 0));
                displayElements.add(drop);
            }
        }

        Location animCenter = block.getLocation().add(0.5, 1.8, 0.5);

        new BukkitRunnable() {
            int tick = 0;
            final int riseTicks = 10;
            final int orbitTicks = 50;
            final int slowTicks = 20;

            private void teleportElement(Object elem, Location loc) {
                if (elem instanceof Item item) {
                    item.teleport(loc);
                } else if (elem instanceof ArmorStand stand) {
                    stand.teleport(loc);
                }
            }

            private void removeElement(Object elem) {
                if (elem instanceof Item item) {
                    item.remove();
                } else if (elem instanceof ArmorStand stand) {
                    stand.remove();
                }
            }

            @Override
            public void run() {
                tick++;

                if (tick <= riseTicks) {
                    double p = (double) tick / riseTicks;
                    for (int i = 0; i < displayElements.size(); i++) {
                        double a = (2.0 * Math.PI / displayElements.size()) * i;
                        double x = Math.cos(a) * 0.5 * p;
                        double z = Math.sin(a) * 0.5 * p;
                        double y = p * 1.5;
                        teleportElement(displayElements.get(i), animCenter.clone().add(x, y, z));
                    }
                } else if (tick <= riseTicks + orbitTicks) {
                    int ot = tick - riseTicks;
                    double angle = ot * 0.12;
                    double radius = 1.2;
                    double vRadius = 0.8;

                    for (int i = 0; i < displayElements.size(); i++) {
                        double a = angle + (2.0 * Math.PI / displayElements.size()) * i;
                        double x = Math.cos(a) * radius;
                        double z = Math.sin(a) * radius;
                        double y = Math.sin(a * 1.5) * vRadius;
                        teleportElement(displayElements.get(i), animCenter.clone().add(x, y, z));
                    }

                    block.getWorld().spawnParticle(
                            org.bukkit.Particle.PORTAL,
                            animCenter, 2, 0.3, 0.3, 0.3, 0.02);
                } else if (tick <= riseTicks + orbitTicks + slowTicks) {
                    int st = tick - riseTicks - orbitTicks;
                    double angle = orbitTicks * 0.12 + st * 0.04;
                    double radius = Math.max(0.2, 1.2 * (1.0 - (double) st / slowTicks));
                    double vRadius = Math.max(0.1, 0.8 * (1.0 - (double) st / slowTicks));

                    if (st % 4 == 0) {
                        block.getWorld().spawnParticle(
                                org.bukkit.Particle.ENCHANT,
                                animCenter, 4, 0.4, 0.4, 0.4, 0);
                    }

                    for (int i = 0; i < displayElements.size(); i++) {
                        double a = angle + (2.0 * Math.PI / displayElements.size()) * i;
                        double x = Math.cos(a) * radius;
                        double z = Math.sin(a) * radius;
                        double y = Math.sin(a * 1.5) * vRadius;
                        teleportElement(displayElements.get(i), animCenter.clone().add(x, y, z));
                    }

                    if (st >= slowTicks) {
                        final Reward winner = selectReward(crate);
                        if (winner == null) {
                            for (Object d : displayElements) removeElement(d);
                            this.cancel();
                            return;
                        }

                        for (Object d : displayElements) removeElement(d);

                        Material winMat = resolveDisplayMaterial(winner);
                        ItemStack winStack = ItemStack.of(winMat, winner.getAmount());
                        Item winDrop = block.getWorld().dropItem(animCenter, winStack);
                        winDrop.setPickupDelay(Integer.MAX_VALUE);
                        winDrop.setGravity(false);
                        winDrop.setVelocity(new Vector(0, 0, 0));

                        block.getWorld().spawnParticle(
                                org.bukkit.Particle.FLAME,
                                animCenter.clone().add(0, 0.5, 0), 25, 0.3, 0.3, 0.3, 0.03);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            winDrop.remove();
                            playCloseSound(player, block);
                            grantReward(player, winner);
                        }, 25L);

                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}