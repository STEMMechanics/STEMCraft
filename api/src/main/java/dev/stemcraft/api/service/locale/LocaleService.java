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

public interface LocaleService {

    /**
     * Get the default locale of the server
     */
    String getDefaultLocale();

    /**
     * Get a locale string based on the lang and key and fill placeholders.
     * If key is a string, then it will be used in place of the key.
     */
    String resolve(String lang, String key);
    default String resolve(String key) { return resolve(getDefaultLocale(), key); }
    default String resolve(CommandSender sender, String key) { return resolve(sender instanceof Player p ? p.locale().toLanguageTag() : getDefaultLocale(), key); }
}