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

import org.bukkit.entity.Player;

public interface PlayerLogService extends STEMCraftService {

    /**
     * Log an action against the player. If player is null it is logged as a server action.
     */
    void logPlayerAction(Player player, String action, String... placeholders);

    /**
     * Log a server action.
     */
    default void logServerAction(String action, String... placeholders) { logPlayerAction(null, action, placeholders); }
}
