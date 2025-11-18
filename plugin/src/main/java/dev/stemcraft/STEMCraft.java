package dev.stemcraft;

import dev.stemcraft.api.services.LogService;
import dev.stemcraft.api.services.WorldService;
import dev.stemcraft.api.utils.SCText;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.services.LogServiceImpl;
import dev.stemcraft.services.WorldServiceImpl;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class STEMCraft extends JavaPlugin {
    @Getter
    private static STEMCraft instance;

    @Getter
    private static LogService logService;
    @Getter
    private static WorldService worldService;

    @Getter
    private static YamlConfiguration pluginConfig;

    @Getter
    private static boolean debugging = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        File configFile = new File(instance.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        pluginConfig = YamlConfiguration.loadConfiguration(configFile);
        debugging = pluginConfig.getBoolean("debug", false);

        logService = new LogServiceImpl(this);
        worldService = new WorldServiceImpl(this);

        getServer().getServicesManager().register(LogService.class, logService, this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(WorldService.class, worldService, this, org.bukkit.plugin.ServicePriority.Normal);

        logService.info("STEMCraft enabled");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    public static void debug(String message) { if(debugging) { logService.log(message); } }
    public static void debug(String message, String ...placeholders) { debug(SCText.placeholders(message, placeholders)); }
    public static void log(String message) { logService.log(message); }
    public static void log(String message, String ...placeholders) { log(SCText.placeholders(message, placeholders)); }
    public static void info(String message) { logService.info(message); }
    public static void info(String message, String ...placeholders) { info(SCText.placeholders(message, placeholders)); }
    public static void warn(String message) { logService.warn(message); }
    public static void warn(String message, String ...placeholders) { warn(SCText.placeholders(message, placeholders)); }
    public static void error(String message) { logService.error(message); }
    public static void error(String message, String ...placeholders) { error(SCText.placeholders(message, placeholders)); }
}
