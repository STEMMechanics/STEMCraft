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

package dev.stemcraft.api.service.region;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener for region-related events.
 */
@SuppressWarnings("EmptyMethod")
public interface RegionListener {

    /**
     * Called when a player enters a region.
     *
     * @param player The player entering the region.
     * @param region The region being entered.
     */
    default void onEnter(@NotNull Player player, @NotNull SCRegion region) { }

    /**
     * Called when a player enters a region.
     *
     * @param player The player entering the region.
     * @param region The region being entered.
     * @param from   The move origin.
     * @param to     The move destination that triggered the enter.
     */
    default void onEnter(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
        onEnter(player, region);
    }

    /**
     * Called when a player enters a world.
     *
     * @param player The player entering the world.
     * @param world The world being entered.
     */
    default void onEnterWorld(@NotNull Player player, @NotNull World world) { }

    /**
     * Called when a player enters a world.
     *
     * @param player The player entering the world.
     * @param world  The world being entered.
     * @param from   The move origin.
     * @param to     The move destination that triggered the enter.
     */
    default void onEnterWorld(@NotNull Player player, @NotNull World world, @Nullable Location from, @Nullable Location to) {
        onEnterWorld(player, world);
    }

    /**
     * Called when a player exits a region.
     *
     * @param player The player exiting the region.
     * @param region The region being exited.
     */
    default void onExit(@NotNull Player player, @NotNull SCRegion region) { }

    /**
     * Called when a player exits a region.
     *
     * @param player The player exiting the region.
     * @param region The region being exited.
     * @param from   The move origin.
     * @param to     The move destination that triggered the exit.
     */
    default void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
        onExit(player, region);
    }

    /**
     * Called when a player exits a world.
     *
     * @param player The player exiting the world.
     * @param world The world being exited.
     */
    default void onExitWorld(@NotNull Player player, @NotNull World world) { }

    /**
     * Called when a player exits a world.
     *
     * @param player The player exiting the world.
     * @param world  The world being exited.
     * @param from   The move origin.
     * @param to     The move destination that triggered the exit.
     */
    default void onExitWorld(@NotNull Player player, @NotNull World world, @Nullable Location from, @Nullable Location to) {
        onExitWorld(player, world);
    }
}
