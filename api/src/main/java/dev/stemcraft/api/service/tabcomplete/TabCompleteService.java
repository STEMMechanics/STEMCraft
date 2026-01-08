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

package dev.stemcraft.api.service.tabcomplete;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service for managing tab completion providers.
 */
public interface TabCompleteService {

    /**
     * Register a tab completion provider with a specific name.
     *
     * @param name The name of the tab completion provider.
     * @param callback The callback to provide tab completions.
     */
    void register(String name, TabCompletionProvider callback);

    /**
     * Get a list of tab completions for a specific provider name.
     *
     * @param name The name of the tab completion provider.
     * @param player The player requesting tab completions.
     * @param args The current arguments typed by the player.
     * @return A list of tab completions.
     */
    List<String> getCompletionList(String name, Player player, String... args);
}