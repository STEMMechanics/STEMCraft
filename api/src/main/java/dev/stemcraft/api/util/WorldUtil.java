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

import java.util.Locale;

/**
 * Utility class for world-related operations.
 */
public class WorldUtil {

    /**
     * Returns the base name of a world, stripping any dimension suffixes.
     *
     * @param worldName The name of the world.
     * @return The base name of the world.
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

    /**
     * Resolves the world environment based on the world name.
     *
     * @param name The name of the world.
     * @return The corresponding World.Environment.
     */
    public static World.Environment resolveEnvironment(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return World.Environment.NETHER;
        if (lower.endsWith("_the_end")) return World.Environment.THE_END;
        if (lower.endsWith("_end")) return World.Environment.THE_END;
        return World.Environment.NORMAL;
    }
}