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
import org.bukkit.World;
import org.bukkit.entity.Player;

@SuppressWarnings({"unused", "EmptyMethod"})
public class RegionListener {

    /*
     * Called when a player enters a region.
     */
    public void onEnter(Player player, SCRegion region) { }

    /*
     * Called when a player enters a world.
     */
    public void onEnterWorld(Player player, World world) { }

    /*
     * Called when a player exits a region.
     */
    public void onExit(Player player, SCRegion region) { }

    /*
     * Called when a player exits a world.
     */
    public void onExitWorld(Player player, World world) { }
}
