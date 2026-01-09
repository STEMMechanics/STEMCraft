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

package dev.stemcraft.api.service.player;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Service for managing player-related functionalities.
 */
public interface PlayerService {
    
    /**
     * Hide a player from all other players.
     *
     * @param player The player to hide.
     */
    void hide(@NotNull Player player);

    /**
     * Show a player to all other players.
     *
     * @param player The player to show.
     */
    void show(@NotNull Player player);
}
