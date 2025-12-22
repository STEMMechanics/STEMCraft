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
import dev.stemcraft.annotations.IgnoreLocaleKeyCheck;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.utils.SCString;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@IgnoreLocaleKeyCheck
public class LocaleManager implements LocaleService {
    private static final Pattern LOCALE_KEY_PATTERN = Pattern.compile("[A-Z]+_[A-Z_]+");
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER =
            PlainTextComponentSerializer.plainText();

    private final STEMCraft plugin;
    private final Map<String, YamlConfiguration> locales = new HashMap<>();
    @Getter
    private String defaultLocale;
    private final Map<Pattern, String> bindings = new HashMap<>();

    public LocaleManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        defaultLocale = plugin.config().getString("default-locale", "en").toLowerCase(Locale.ROOT);
        loadLocales();

        ConfigurationSection sec = plugin.config().getConfigurationSection("bindings");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                addBinding(key, sec.getString(key));
            }
        }

        plugin.registerEvent(AsyncChatEvent.class, event -> {
            event.message(processBindings(event.message()));
        }, EventPriority.LOWEST, true);
    }

    public void onDisable() {
        locales.clear();
    }

    @Override
    public String get(String lang, String key, Object... placeholders) {
        String str = processKey(lang, key);

        str = processBindings(str);
        if (placeholders != null && placeholders.length > 1) {
            String[] processed = SCString.toStrings(placeholders);

            for (int i = 1; i < processed.length; i += 2) {
                processed[i] = processKey(lang, processed[i]);
            }

            return SCString.placeholders(str, processed);
        }

        return str;
    }

    @Override
    public void addBinding(String placeholder, String value) {
        bindings.put(Pattern.compile(Pattern.quote(":" + placeholder + ":")), value);
        String escaped = value.chars()
                .mapToObj(c -> String.format("\\u%04X", c))
                .reduce("", String::concat);
        plugin.debug("Added locale binding: " + placeholder + " -> " + escaped);
    }

    @Override
    public void removeBinding(String placeholder) {
        bindings.remove(Pattern.compile(Pattern.quote(":" + placeholder + ":")));
    }

    @Override
    public void removeBindings(Iterable<String> placeholders) {
        for (String placeholder : placeholders) {
            bindings.remove(Pattern.compile(Pattern.quote(":" + placeholder + ":")));
        }
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
            plugin.getComponentLogger().info("The following locale key was not found: " + key);
            return key;
        }

        return raw;
    }

    public String processBindings(String str) {
        for (var entry : bindings.entrySet()) {
            str = entry.getKey().matcher(str).replaceAll(entry.getValue());
        }
        return str;
    }

    public Component processBindings(Component comp) {
        // Convert the incoming component to plain text, apply bindings, then re-apply colour formatting.
        String plain = PLAIN_SERIALIZER.serialize(comp);
        String processed = processBindings(plain);
        return SCString.colourise(processed);
    }

    private void loadLocales() {
        plugin.exportBundledDirectory("locales");
        locales.clear();

        File folder = new File(plugin.getDataFolder(), "locales");
        if (!folder.exists()) {
//            plugin.error("LOCALE_FAILED_CREATE_FOLDER");
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
            plugin.warn(
                    "Default locale {lang} not found; available: {available}",
                    "lang", defaultLocale,
                    "available", String.join(", ", locales.keySet())
            );
        }
    }
}