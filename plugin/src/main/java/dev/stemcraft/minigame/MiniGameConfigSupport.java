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
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.bukkit.Location;

import java.util.List;

import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public final class MiniGameConfigSupport {
    private MiniGameConfigSupport() {
    }

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

    /**
     * Validate common arena geometry and spawn capacity; games validate their own additional rules.
     */
    public static void validateArenaSpawns(
            @NotNull STEMCraftAPI api,
            @NotNull MiniGameArena arena,
            @NotNull List<Location> spawns,
            int maximumSupportedPlayers,
            @NotNull ArenaValidationResult result
    ) {
        if (arena.getRegion() == null || !arena.world().equals(arena.getRegion().getWorld())) {
            result.addError("Select the full arena region in its world.", "arena");
        }
        if (arena.getRegion() != null) {
            for (var game : api.minigames().list()) {
                for (MiniGameArena other : game.arenas()) {
                    if (other != arena && other.getRegion() != null && arena.world().equals(other.world())
                            && arena.getRegion().intersects(other.getRegion())) {
                        result.addError("Arena overlaps " + other.namespace() + ":" + other.id() + ". Use separate play areas.", "arena");
                    }
                }
            }
        }
        if (arena.getLobbySpawn() == null || !arena.world().equals(arena.getLobbySpawn().getWorld())) {
            result.addError("Set a lobby in the arena world.", "lobby");
        }
        if (arena.getSpectatorSpawn() == null || !arena.world().equals(arena.getSpectatorSpawn().getWorld())) {
            result.addError("Set a spectator spawn in the arena world.", "spectator");
        }
        if (arena.getMinPlayers() < 1 || arena.getMaxPlayers() < arena.getMinPlayers() || arena.getMaxPlayers() > maximumSupportedPlayers) {
            result.addError("Require 1 <= minplayers <= maxplayers <= " + maximumSupportedPlayers + ".", "players");
        }
        if (spawns.size() < arena.getMaxPlayers()) {
            result.addError("Add at least maxplayers starting spawns.", "spawns");
        }
        for (Location spawn : spawns) {
            if (arena.getRegion() == null || !arena.getRegion().contains(spawn)) {
                result.addError("Every player spawn must be inside the arena.", "spawns");
            }
        }
    }
}
