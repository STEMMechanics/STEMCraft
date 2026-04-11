/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.locale.LocaleService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementation of the LocaleService for managing localization.
 */
public class LocaleServiceImpl extends BaseService implements LocaleService {
    private final Map<String, YamlConfiguration> locales = new HashMap<>();
    private String defaultLocale;

    private static final Pattern LOCALE_KEY_PATTERN = Pattern.compile("[A-Z]+_[A-Z_]+");
    private final Set<String> missingKeysLogged = ConcurrentHashMap.newKeySet();


    /**
     * Constructor for LocaleServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public LocaleServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is being enabled.
     */
    @Override
    public void onEnable() {
        reload();
    }

    /**
     * Called when the service is being disabled.
     */
    @Override
    public void onDisable() {
        locales.clear();
        missingKeysLogged.clear();
    }

    /**
     * Get the default locale of the server.
     *
     * @return The default locale string.
     */
    @Override
    public @NotNull String getDefaultLocale() {
        return defaultLocale;
    }

    @Override
    public void reload() {
        defaultLocale = getRootConfigSection().getString("default-locale", "en").toLowerCase(Locale.ROOT);
        missingKeysLogged.clear();
        loadLocales();
    }

    /**
     * Resolve a locale key or return the raw string unchanged if it does not look like a locale key.
     *
     * @param lang The language code to use for resolution.
     * @param key The locale key to resolve.
     * @return The resolved locale string or the original key if not found.
     */
    public @NotNull String resolve(@NotNull String lang, @NotNull String key) {
        if (key.isEmpty()) {
            return key;
        }

        // Fast-path: avoid locale lookup for normal text.
        if (!LOCALE_KEY_PATTERN.matcher(key).matches()) {
            return key;
        }

        if (lang.isEmpty()) {
            lang = defaultLocale;
        }

        YamlConfiguration cfg = locales.get(lang);
        if (cfg == null) {
            cfg = locales.get(defaultLocale);
        }

        // If we still have no config, just return the key.
        if (cfg == null) {
            return key;
        }

        String raw = cfg.getString(key);
        if (raw == null) {
            // Log missing keys once to avoid spam.
            String logKey = lang + ":" + key;
            if (missingKeysLogged.add(logKey)) {
                plugin.getComponentLogger().info("The following locale ({}) key was not found: {}", lang, key);
            }
            return key;
        }

        return raw;
    }

    /**
     * Load locale files from the plugin's data folder.
     */
    private void loadLocales() {
        plugin.exportBundledDirectory("locales");
        locales.clear();

        File folder = new File(plugin.getDataFolder(), "locales");
        if (!folder.exists()) {
            api.messages().error("LOCALE_FAILED_CREATE_FOLDER");
            return;
        }

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
            api.messages().warn(
                    "Default locale {lang} not found; available: {available}",
                    "lang", defaultLocale,
                    "available", String.join(", ", locales.keySet())
            );
        }
    }
}
