package dev.stemcraft;

import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.services.LogService;
import dev.stemcraft.api.services.WorldService;
import dev.stemcraft.api.utils.SCText;
import dev.stemcraft.chunkgen.FlatGenerator;
import dev.stemcraft.chunkgen.VoidGenerator;
import dev.stemcraft.services.LocaleServiceImpl;
import dev.stemcraft.services.LogServiceImpl;
import dev.stemcraft.services.WorldServiceImpl;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class STEMCraft extends JavaPlugin {
    @Getter
    private static STEMCraft instance;

    @Getter
    private static LocaleService localeService;
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

        localeService = new LocaleServiceImpl(this);
        localeService.reload();
        logService = new LogServiceImpl(this);
        worldService = new WorldServiceImpl(this);

        getServer().getServicesManager().register(LocaleService.class, localeService, this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(LogService.class, logService, this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(WorldService.class, worldService, this, org.bukkit.plugin.ServicePriority.Normal);

        logService.info("STEMCraft enabled");

        worldService.registerGenerator("void", (options) -> new VoidGenerator());
        worldService.registerGenerator("flat",   FlatGenerator::fromOptions);       // e.g., "grass_block;dirt:3;bedrock"
        worldService.registerGenerator("normal", cfg -> null);               // null => vanilla normal

        loadFeatures();
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

    private void loadFeatures() {
        YamlConfiguration config = pluginConfig;
        var pm = Bukkit.getPluginManager();

        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.getName().startsWith("dev/stemcraft/features/")
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }

                String className = entry.getName()
                        .replace('/', '.')
                        .substring(0, entry.getName().length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className);

                    if (!Listener.class.isAssignableFrom(clazz)) continue;
                    if (Modifier.isAbstract(clazz.getModifiers())) continue;

                    String simple = SCText.toSnakeCase(clazz.getSimpleName()).toLowerCase();
                    String key = "features." + simple + ".enabled";

                    if (!config.getBoolean(key, true)) {
                        STEMCraft.info("Feature {name} disabled in config", "name", clazz.getSimpleName());
                        continue;
                    }

                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    try {
                        Method onEnableMethod = clazz.getMethod("onEnable", STEMCraft.class);

                        Object result = onEnableMethod.invoke(instance, this);

                        // If method returns a boolean, use it.
                        if (onEnableMethod.getReturnType() == boolean.class || onEnableMethod.getReturnType() == Boolean.class) {
                            if(!(Boolean) result) {
                                STEMCraft.warn("Feature {name} could not be loaded");
                                continue;
                            }
                        }

                    } catch (NoSuchMethodException ignored) {
                        // No enable() method = consider it enabled
                    }

                    // Load as event listener if applicable
                    if (instance instanceof Listener listener) {
                        pm.registerEvents(listener, this);
                    }

                    STEMCraft.info("Feature {name} loaded", "name", clazz.getSimpleName());

                } catch (ReflectiveOperationException ex) {
                    error("Failed to load feature class " + className);
                }
            }
        } catch (IOException ex) {
            error("Failed to scan features in plugin jar");
        }
    }
}
