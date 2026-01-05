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

public class WorldTimeUtil {
    /**
     * Convert a world time to a real time.
     *
     * @param world The world to convert.
     * @return The converted time.
     */
    public static String toClock(World world) {
        long time = world.getTime();

        // Shift so 0 ticks = 6:00 AM
        long adjusted = (time + 6000) % 24000;

        int hours24 = (int) (adjusted / 1000);
        int minutes = (int) ((adjusted % 1000) * 60 / 1000);

        String amPm = hours24 >= 12 ? "PM" : "AM";

        int hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;

        return String.format("%d:%02d %s", hours12, minutes, amPm);
    }
}
