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

package dev.stemcraft.api.service.minigame;

import dev.stemcraft.api.minigame.*;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service for managing mini-games within the STEMCraft plugin.
 */
public interface MiniGameService {

    /**
     * Creates a new mini-game with the specified namespace and arena handler.
     *
     * @param namespace The unique namespace for the mini-game.
     * @param handler   The arena handler for managing arenas of this mini-game.
     * @return The created MiniGame instance.
     */
    MiniGame create(String namespace, MiniGameArenaHandler handler);

    /**
     * Retrieves a mini-game by its namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @return The MiniGame instance, or null if not found.
     */
    MiniGame get(String namespace);

    /**
     * Lists all registered mini-games.
     *
     * @return A list of all MiniGame instances.
     */
    List<MiniGame> list();

    /**
     * Removes the player from their current arena, if any.
     *
     * @param player The player to remove.
     * @param restoreLocation Whether to restore the saved pre-minigame location.
     * @return True if the player was removed from an arena, false otherwise.
     */
    boolean removePlayerFromArena(Player player, boolean restoreLocation);
}
