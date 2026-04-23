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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility class for serializing and deserializing Locations.
 */
public class LocationUtil {
    private static final String DELIM = ",";

    /**
     * Deserialize a Location from a string.
     * Format: world (optional), x, y, z, yaw (optional), pitch (optional).
     *
     * @param serialized The serialized location string.
     * @param defaultWorld The default world to use if none is specified in the string.
     * @return The deserialized Location, or null if the string is invalid.
     */
    static public Location deserialize(String serialized, World defaultWorld) {
        // world (optional), x, y, z, yaw (optional), pitch (optional)

        String[] parts = serialized.trim().split(DELIM);
        World world = defaultWorld;
        double x, y, z;
        float yaw = 0f, pitch = 0f;

        if(parts.length < 3) return null;
        int offset = 0;

        if(!isNumber(parts[0])) {
            world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            offset = 1;
        }

        x = Double.parseDouble(parts[offset]);
        y = Double.parseDouble(parts[offset + 1]);
        z = Double.parseDouble(parts[offset + 2]);
        if(parts.length >= 4 + offset) yaw = Float.parseFloat(parts[offset + 3]);
        if(parts.length >= 5 + offset) pitch = Float.parseFloat(parts[offset + 4]);

        return new Location(world, x, y, z, yaw, pitch);
    }

    static public Location deserialize(String serialized) {
        return deserialize(serialized, null);
    }

    /**
     * Serialize a Location to a string.
     *
     * @param location The Location to serialize.
     * @param includeWorld Whether to include the world name in the serialization.
     * @param includePitchYaw Whether to include pitch and yaw in the serialization.
     * @return The serialized location string, or null if the location is null.
     */
    public static String serialize(Location location, boolean includeWorld, boolean includePitchYaw) {
        if (location == null) return null;

        StringBuilder sb = new StringBuilder();
        if (includeWorld) {
            World world = location.getWorld();
            if (world != null) {
                sb.append(world.getName()).append(DELIM);
            }
        }

        sb.append(location.getX()).append(DELIM)
          .append(location.getY()).append(DELIM)
          .append(location.getZ());

        if (includePitchYaw) {
            sb.append(DELIM).append(location.getYaw())
              .append(DELIM).append(location.getPitch());
        }

        return sb.toString();
    }

    /**
     * Check if a string represents a valid number.
     * @param s The string to check.
     * @return True if the string is a valid number, false otherwise.
     */
    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}