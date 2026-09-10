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

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.loot.LootTables;

/** Shared terrain checks and seeded loot for local landmarks. */
final class LandmarkSupport {
    private LandmarkSupport() {}
    static boolean replaceable(Material material) {
        return material.isAir() || material == Material.SHORT_GRASS || material == Material.POPPY
                || material == Material.DEAD_BUSH || material == Material.BROWN_MUSHROOM;
    }
    static boolean foundation(Material material) {
        return material.isSolid() && material.isOccluding() && material != Material.BEDROCK
                && material != Material.MAGMA_BLOCK && material != Material.CACTUS
                && !material.name().endsWith("_LOG") && !material.name().endsWith("MUSHROOM_BLOCK");
    }
    static void loot(LimitedRegion region, int x, int y, int z, long seed, boolean cave) {
        region.setType(x, y, z, Material.CHEST);
        if (region.getBlockState(x, y, z) instanceof Chest chest) {
            chest.setLootTable((cave ? LootTables.SIMPLE_DUNGEON : LootTables.ABANDONED_MINESHAFT).getLootTable());
            chest.setSeed(seed);
            chest.update(true, false);
        }
    }
}
