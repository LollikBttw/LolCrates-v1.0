package com.lolcrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class LolCrateCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String ADMIN_PERMISSION = "lolcrates.admin";

    private final Main plugin;
    private final CrateManager crateManager;

    public LolCrateCommand(@NotNull Main plugin, @NotNull CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, label, args);
            case "key" -> handleKey(sender, label, args);
            default -> {
                sender.sendMessage("§cНеизвестная подкоманда: §e" + args[0]);
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cУ вас нет прав на перезагрузку LolCrates.");
            return true;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage("§aКонфигурация LolCrates перезагружена.");
        return true;
    }

    private boolean handleGive(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cУ вас нет прав на выдачу кейсов.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§eИспользование: /" + label + " give <игрок> <crate_id>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(deserializeMessage(
                    "settings.unknown-player",
                    "<prefix><red>Игрок <yellow>{player}</yellow> не найден или не в сети.</red>",
                    "{player}", args[1]));
            return true;
        }

        String crateId = args[2];
        if (!crateManager.hasCrate(crateId)) {
            sender.sendMessage(deserializeMessage(
                    "settings.unknown-crate-id",
                    "<prefix><red>Кейс с ID <yellow>{crate_id}</yellow> не найден в конфиге.</red>",
                    "{crate_id}", crateId));
            return true;
        }

        if (!crateManager.giveCrateItem(target, crateId)) {
            sender.sendMessage("§cНе удалось создать предмет кейса. Проверьте конфиг.");
            return true;
        }

        CrateDefinition crate = crateManager.getCrate(crateId);
        String crateLabel = crate != null ? crate.getDisplayLabel() : crateId;

        sender.sendMessage(deserializeMessage(
                "settings.give-success-sender",
                "<prefix><green>Кейс <gold>{crate}</gold> выдан игроку <yellow>{player}</yellow>.</green>",
                "{crate}", crateLabel,
                "{player}", target.getName()));
        target.sendMessage(deserializeMessage(
                "settings.give-success-target",
                "<prefix><green>Вы получили кейс <gold>{crate}</gold>!</green>",
                "{crate}", crateLabel));
        return true;
    }

    private boolean handleKey(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cУ вас нет прав на выдачу ключей.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§eИспользование: /" + label + " key <игрок> <crate_id> [количество]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(deserializeMessage(
                    "settings.unknown-player",
                    "<prefix><red>Игрок <yellow>{player}</yellow> не найден или не в сети.</red>",
                    "{player}", args[1]));
            return true;
        }

        String crateId = args[2];
        if (!crateManager.hasCrate(crateId)) {
            sender.sendMessage(deserializeMessage(
                    "settings.unknown-crate-id",
                    "<prefix><red>Кейс с ID <yellow>{crate_id}</yellow> не найден в конфиге.</red>",
                    "{crate_id}", crateId));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§cКоличество должно быть числом от 1 до 64.");
                return true;
            }
        }

        ItemStack key = crateManager.createKeyItemForCrate(crateId);
        key.setAmount(amount);

        Map<Integer, ItemStack> overflow = target.getInventory().addItem(key);
        if (!overflow.isEmpty()) {
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            target.sendMessage(MINI_MESSAGE.deserialize(
                    "<gray>[<gold>Кейсы</gold>]</gray> <yellow>Инвентарь полон; ключи выброшены рядом с вами.</yellow>"));
        }

        CrateDefinition crate = crateManager.getCrate(crateId);
        String crateLabel = crate != null ? crate.getDisplayLabel() : crateId;

        sender.sendMessage("§aКлюч для кейса §e" + crateLabel + "§a в количестве §e" + amount + "§a выдан игроку §e" + target.getName() + "§a.");
        if (!sender.equals(target)) {
            target.sendMessage("§aВы получили §e" + amount + "§a ключей для кейса §e" + crateLabel + "§a.");
        }
        return true;
    }

    private void sendHelp(@NotNull CommandSender sender, @NotNull String label) {
        sender.sendMessage("§6LolCrates §7— доступные команды:");
        sender.sendMessage("§e/" + label + " reload §7— перезагрузить конфиг");
        sender.sendMessage("§e/" + label + " give <игрок> <crate_id> §7— выдать кейс игроку");
        sender.sendMessage("§e/" + label + " key <игрок> <crate_id> [количество] §7— выдать ключи для кейса");
    }

    @NotNull
    private Component deserializeMessage(
            @NotNull String configPath,
            @NotNull String fallback,
            @NotNull String... replacements) {
        String template = plugin.getConfig().getString(configPath, fallback);
        String prefix = plugin.getConfig().getString("settings.message-prefix", "<gray>[<gold>Кейсы</gold>]</gray> ");
        template = template.replace("<prefix>", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            template = template.replace(replacements[i], replacements[i + 1]);
        }
        return MINI_MESSAGE.deserialize(template);
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(List.of("reload", "give", "key"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("key"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("key"))) {
            return filterPrefix(crateManager.getCrateIds(), args[2]);
        }

        return Collections.emptyList();
    }

    @NotNull
    private List<String> filterPrefix(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}