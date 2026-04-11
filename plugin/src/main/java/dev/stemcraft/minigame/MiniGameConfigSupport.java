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

package dev.stemcraft.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public final class MiniGameConfigSupport {
    private MiniGameConfigSupport() {}

    public static @NotNull World requireWorld(@NotNull STEMCraftAPI api, @NotNull String arenaId, @NotNull String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }

        world = api.worlds().loadWorld(worldName);
        if (world != null) {
            return world;
        }

        if (api.worlds().worldExists(worldName)) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' exists but could not be loaded.");
        }

        throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' does not exist.");
    }
}
