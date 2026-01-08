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

package dev.stemcraft.api.service.locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Service for managing localization and locale strings.
 */
public interface LocaleService {

    /**
     * Get the default locale of the server.
     *
     * @return The default locale string.
     */
    String getDefaultLocale();

    /**
     * Resolve a locale key to a localized string.
     * If the key does not look like a locale key, the raw key is returned unchanged.
     *
     * @param lang The language code (e.g., "en-US").
     * @param key The key for the locale string.
     * @return The resolved locale string, or the raw key when no lookup is performed.
     */
    String resolve(String lang, String key);
    default String resolve(String key) { return resolve(getDefaultLocale(), key); }
    default String resolve(CommandSender sender, String key) { return resolve(sender instanceof Player p ? p.locale().toLanguageTag() : getDefaultLocale(), key); }
}