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
package dev.stemcraft.api.services;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface LocaleService extends STEMCraftService {

    /**
     * Get the default locale of the server
     */
    String getDefaultLocale();

    /**
     * Get a locale string based on the lang and key and fill placeholders.
     * If key is a string, then it will be used in place of the key.
     */
    String get(String lang, String key, String... placeholders);

    default String get(String key, String... placeholders) { return get(getDefaultLocale(), key, placeholders); }

    default String get(CommandSender sender, String key, String... placeholders) {
        String lang;

        if (sender instanceof Player p) {
            lang = p.locale().toLanguageTag();
        } else {
            lang = getDefaultLocale();
        }

        return get(lang, key, placeholders);
    }
}