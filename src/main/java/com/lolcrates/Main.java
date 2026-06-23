package com.lolcrates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin {
   private CrateManager crateManager;

   public void onEnable() {
      this.saveDefaultConfig();
      this.ensureDefaultConfigExists();
      this.crateManager = new CrateManager(this);
      this.reloadPluginConfig();
      this.getServer().getPluginManager().registerEvents(new CrateListener(this, this.crateManager), this);
      this.registerCommands();
      this.getLogger().info("LolCrates v" + this.getDescription().getVersion() + " включён.");
   }

   public void onDisable() {
      this.getLogger().info("LolCrates выключен.");
   }

   private void registerCommands() {
      PluginCommand command = this.getCommand("lolcrate");
      if (command == null) {
         this.getLogger().severe("Команда lolcrate не зарегистрирована в plugin.yml!");
      } else {
         LolCrateCommand handler = new LolCrateCommand(this, this.crateManager);
         command.setExecutor(handler);
         command.setTabCompleter(handler);
      }
   }

   public void reloadPluginConfig() {
      this.reloadConfig();
      this.crateManager.load(this.getConfig());
   }

   public @NotNull CrateManager getCrateManager() {
      return this.crateManager;
   }

   private void ensureDefaultConfigExists() {
      Path configPath = this.getDataFolder().toPath().resolve("config.yml");
      if (!Files.exists(configPath, new LinkOption[0])) {
         try {
            InputStream stream = this.getResource("config.yml");

            label61: {
               try {
                  if (stream == null) {
                     this.getLogger().warning("Встроенный config.yml не найден в jar-файле.");
                     break label61;
                  }

                  Files.createDirectories(configPath.getParent());
                  Files.copy(stream, configPath, new CopyOption[0]);
               } catch (Throwable var6) {
                  if (stream != null) {
                     try {
                        stream.close();
                     } catch (Throwable var5) {
                        var6.addSuppressed(var5);
                     }
                  }

                  throw var6;
               }

               if (stream != null) {
                  stream.close();
               }

               return;
            }

            if (stream != null) {
               stream.close();
            }

         } catch (IOException ex) {
            this.getLogger().log(Level.SEVERE, "Не удалось записать config.yml по умолчанию", ex);
         }
      }
   }

   public void saveDefaultConfig() {
      if (!this.getDataFolder().exists() && !this.getDataFolder().mkdirs()) {
         this.getLogger().severe("Не удалось создать папку данных плагина.");
      }

      FileConfiguration config = this.getConfig();
      if (config.getKeys(false).isEmpty()) {
         this.saveResource("config.yml", false);
      }

   }
}
