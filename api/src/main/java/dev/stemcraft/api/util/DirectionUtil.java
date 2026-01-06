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

/**
 * Utility class for direction-related functions.
 */
public final class DirectionUtil {

    /**
     * Converts a YAW value to a compass direction.
     *
     * @param yaw The yaw value to convert.
     * @return The compass direction.
     */
    public static String getCompassDirection(float yaw) {
        double rotation = (yaw - 90) % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }

        if (0 <= rotation && rotation < 22.5 || 337.5 <= rotation && rotation < 360) {
            return "W"; // West
        } else if (22.5 <= rotation && rotation < 67.5) {
            return "NW"; // Northwest
        } else if (67.5 <= rotation && rotation < 112.5) {
            return "N"; // North
        } else if (112.5 <= rotation && rotation < 157.5) {
            return "NE"; // Northeast
        } else if (157.5 <= rotation && rotation < 202.5) {
            return "E"; // East
        } else if (202.5 <= rotation && rotation < 247.5) {
            return "SE"; // Southeast
        } else if (247.5 <= rotation && rotation < 292.5) {
            return "S"; // South
        } else if (292.5 <= rotation && rotation < 337.5) {
            return "SW"; // Southwest
        }
        return ""; // This should never happen
    }
}