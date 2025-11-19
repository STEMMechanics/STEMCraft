package dev.stemcraft.services;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.utils.SCText;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LocaleServiceImpl implements LocaleService {

    private final STEMCraft plugin;
    private final Map<String, YamlConfiguration> locales = new HashMap<>();
    private final String defaultLocale = "en";

    public LocaleServiceImpl(STEMCraft plugin) {
        this.plugin = plugin;

    }

    public void onEnable() {
        reload();
    }

    public void onDisable() {
        locales.clear();
    }

    private String getLocale(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.locale().toLanguageTag();
        }

        return defaultLocale;
    }

    private Component translate(String lang, String key, String... args) {
        if(lang == null || lang.isEmpty()) {
            lang = defaultLocale;
        }

        YamlConfiguration cfg = locales.get(lang);
        if (cfg == null) {
            cfg = locales.get(defaultLocale);
        }

        String raw = cfg.getString(key);
        if (raw == null) {
            raw = key; // Fallback to key if not found
        }

        if( args != null && args.length > 0) {
            raw = SCText.placeholders(raw, args);
        }

        return SCText.colourise(raw);
    }

    public Component get(CommandSender sender, String key, String... args) {
        return translate(getLocale(sender), key, args);
    }

    public Component get(CommandSender sender, String key) {
        return translate(getLocale(sender), key);
    }

    public Component get(Player player, String key, String... args) {
        return translate(getLocale(player), key, args);
    }

    public Component get(Player player, String key) {
        return translate(getLocale(player), key);
    }

    public Component get(String key, String... args) {
        return translate(defaultLocale, key, args);
    }

    public Component get(String key) {
        return translate(defaultLocale, key);
    }

    public void reload() {
        locales.clear();

        File folder = new File(plugin.getDataFolder(), "locales");
        if (!folder.exists()) {
            if(!folder.mkdirs()) {
                STEMCraft.getLogService().error("Failed to create locales folder");
                return;
            }
        }

        // Export all locale files packaged in the plugin JAR
        exportBundledLocales();

        // Load all locale files from disk
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".yml")) {
                    String lang = file.getName().replace(".yml", "");
                    locales.put(lang, YamlConfiguration.loadConfiguration(file));
                }
            }
        }
    }

    private void exportBundledLocales() {
        try {
            String path = "locales/";
            var jar = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();

            try (JarFile jf = new JarFile(new File(jar.toURI()))) {
                Enumeration<JarEntry> entries = jf.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (!name.startsWith(path) || !name.endsWith(".yml")) {
                        continue;
                    }

                    String fileName = name.substring(path.length());
                    File out = new File(plugin.getDataFolder(), "locales/" + fileName);

                    // Only export if not already on disk
                    if (!out.exists()) {
                        plugin.saveResource(name, false);
                    }
                }
            }
        } catch (Exception e) {
            STEMCraft.getLogService().error("Failed to export locale files", e);
        }
    }
}