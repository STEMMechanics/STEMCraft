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

package dev.stemcraft.chunkgen.feature;

/** World-coordinate feature field. Must evaluate independently of chunk visitation order. */
@FunctionalInterface
public interface RegionFeature {
    double sample(int blockX, int blockZ);
    static int coordinate(int block, int regionSize) {
        if (regionSize <= 0) throw new IllegalArgumentException("Region size must be positive");
        return Math.floorDiv(block, regionSize);
    }
}
