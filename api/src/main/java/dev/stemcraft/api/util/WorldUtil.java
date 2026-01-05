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

package dev.stemcraft.api.util;

import org.bukkit.World;

public class WorldUtil {

    /**
     * Returns the base name of a world, stripping any dimension suffixes.
     */
    public static String baseName(String worldName) {
        if(worldName == null) return null;
        if(worldName.endsWith("_the_end")) {
            return worldName.substring(0, worldName.length() - "_the_end".length());
        } else if(worldName.endsWith("_nether")) {
            return worldName.substring(0, worldName.length() - "_nether".length());
        } else {
            return worldName;
        }
    }

    public static String baseName(World world) {
        if(world == null) return null;
        return baseName(world.getName());
    }
}