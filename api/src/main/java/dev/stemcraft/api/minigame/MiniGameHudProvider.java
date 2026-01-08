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

package dev.stemcraft.api.minigame;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Functional interface for providing HUD data for a player in a mini-game arena.
 */
@FunctionalInterface
public interface MiniGameHudProvider {

    /**
     * Provides HUD data for the specified player in the given mini-game arena.
     *
     * @param arena The mini-game arena.
     * @param player The player for whom to provide HUD data.
     * @return A map of HUD data key-value pairs.
     */
    Map<String,String> provide(MiniGameArena arena, Player player);
}