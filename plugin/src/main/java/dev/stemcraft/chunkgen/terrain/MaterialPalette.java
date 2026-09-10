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

package dev.stemcraft.chunkgen.terrain;

import org.bukkit.Material;

/** Shared surface/depth selection for terrain models. */
public record MaterialPalette(Material top, Material soil, Material rock) {
    public Material at(int depth) { return depth == 0 ? top : depth < 4 ? soil : rock; }
    public static final MaterialPalette GRASS = new MaterialPalette(Material.GRASS_BLOCK, Material.DIRT, Material.STONE);
    public static final MaterialPalette DRY = new MaterialPalette(Material.COARSE_DIRT, Material.DIRT, Material.STONE);
    public static final MaterialPalette BADLANDS = new MaterialPalette(Material.RED_SAND, Material.TERRACOTTA, Material.STONE);
    public static final MaterialPalette DESERT = new MaterialPalette(Material.SAND, Material.SANDSTONE, Material.STONE);
    public static final MaterialPalette FROZEN = new MaterialPalette(Material.SNOW_BLOCK, Material.DIRT, Material.STONE);
    public static final MaterialPalette MUSHROOM = new MaterialPalette(Material.MYCELIUM, Material.DIRT, Material.STONE);
}
