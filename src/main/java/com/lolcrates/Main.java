package com.lolcrates;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    private CrateManager crateManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDefaultConfigExists();

        crateManager = new CrateManager(this);
        reloadPluginConfig();

        getServer().getPluginManager().registerEvents(new CrateListener(this, crateManager), this);
        registerCommands();

        getLogger().info("LolCrates v" + getDescription().getVersion() + " включён.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LolCrates выключен.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("lolcrate");
        if (command == null) {
            getLogger().severe("Команда lolcrate не зарегистрирована в plugin.yml!");
            return;
        }

        LolCrateCommand handler = new LolCrateCommand(this, crateManager);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        crateManager.load(getConfig());
    }

    @NotNull
    public CrateManager getCrateManager() {
        return crateManager;
    }

    private void ensureDefaultConfigExists() {
        Path configPath = getDataFolder().toPath().resolve("config.yml");
        if (Files.exists(configPath)) {
            return;
        }

        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                getLogger().warning("Встроенный config.yml не найден в jar-файле.");
                return;
            }
            Files.createDirectories(configPath.getParent());
            Files.copy(stream, configPath);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Не удалось записать config.yml по умолчанию", ex);
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Не удалось создать папку данных плагина.");
        }

        FileConfiguration config = getConfig();
        if (config.getKeys(false).isEmpty()) {
            saveResource("config.yml", false);
        }
    }
}