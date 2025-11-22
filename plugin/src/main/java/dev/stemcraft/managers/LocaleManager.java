/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */
package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.utils.SCText;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class LocaleManager implements LocaleService {
    private static final Pattern LOCALE_KEY_PATTERN = Pattern.compile("[A-Z_]+");

    private final STEMCraft plugin;
    private final Map<String, YamlConfiguration> locales = new HashMap<>();
    @Getter
    private String defaultLocale;

    public LocaleManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        defaultLocale = plugin.config().getString("default-locale", "en").toLowerCase(Locale.ROOT);
        loadLocales();
    }

    public void onDisable() {
        locales.clear();
    }

    @Override
    public String get(String lang, String key, String... placeholders) {
        String str = processKey(lang, key);

        if (placeholders != null && placeholders.length > 1) {
            String[] processed = placeholders.clone();

            for (int i = 1; i < processed.length; i += 2) {
                processed[i] = processKey(lang, processed[i]);
            }

            return SCText.placeholders(str, processed);
        }

        return str;
    }

    private String processKey(String lang, String key) {
        if (!LOCALE_KEY_PATTERN.matcher(key).matches()) {
            return key;
        }

        if (lang == null || lang.isEmpty()) {
            lang = defaultLocale;
        }

        YamlConfiguration cfg = locales.get(lang);
        if (cfg == null) {
            cfg = locales.get(defaultLocale);
        }

        String raw = cfg.getString(key);
        if (raw == null) {
            return key;
        }

        return raw;
    }

    private void loadLocales() {
        locales.clear();

        File folder = new File(plugin.getDataFolder(), "locales");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.error("Failed to create locales folder");
            return;
        }

        exportBundledLocales();

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;

            String lang = file.getName()
                    .substring(0, file.getName().length() - 4)
                    .toLowerCase(Locale.ROOT);

            locales.put(lang, YamlConfiguration.loadConfiguration(file));
        }

        if (!locales.containsKey(defaultLocale)) {
            plugin.warn(
                    "Default locale {lang} not found; available: {available}",
                    "lang", defaultLocale,
                    "available", String.join(", ", locales.keySet())
            );
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

                    if (!out.exists()) {
                        plugin.saveResource(name, false);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.error("Failed to export locale files", ex);
        }
    }
}