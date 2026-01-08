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

package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Class to register core tab completions.
 */
public class CoreTabCompletions {

    /**
     * Register all core tab completions.
     *
     * @param api The STEMCraft API instance.
     */
    public static void registerAll(STEMCraftAPI api) {
        // Online players
        api.tabComplete().register("player", (player, args) ->
                Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player::canSee)
                        .map(Player::getName)
                        .toList()
        );

        // Common durations
        api.tabComplete().register("duration", (player, args) -> List.of(
                "1m", "2m", "5m", "10m", "15m", "30m",
                "1h", "2h", "4h",
                "1d", "1w"
        ));

        // Worlds
        api.tabComplete().register("world", (player, args) -> Bukkit.getWorlds().stream().map(World::getName).toList());

        // Game-modes
        api.tabComplete().register("gamemode", (player, args) -> List.of(
                "survival", "creative", "spectator", "adventure"
        ));

        api.tabComplete().register("int", (player, args) -> List.of(
                "1", "2", "5", "10", "15", "20", "25", "50", "100"
        ));
    }
}