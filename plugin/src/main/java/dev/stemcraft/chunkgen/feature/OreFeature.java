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

import dev.stemcraft.api.service.world.generation.GenerationContext;
import dev.stemcraft.chunkgen.noise.SeedMixer;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;

/** Small world-coordinate ore clusters. Relative depth permits resources in every floating band. */
public final class OreFeature implements GenerationFeature {
    @Override public void generate(GenerationContext c, int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        long seed = SeedMixer.derive(c.seed(), c.generatorKey() + "/ores/v" + c.generatorVersion());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
            for (int y = data.getMinHeight(); y < data.getMaxHeight(); y++) {
                Material stone = data.getType(x, y, z);
                if (stone != Material.STONE && stone != Material.DEEPSLATE) continue;
                long hash = SeedMixer.at(seed, Math.floorDiv(wx, 3), Math.floorDiv(y, 3), Math.floorDiv(wz, 3));
                int chance = (int) (hash & 1023);
                if (chance >= 58) continue;
                boolean deep = stone == Material.DEEPSLATE;
                Material ore = chance < 3 ? (deep ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE)
                        : chance < 10 ? (deep ? Material.DEEPSLATE_REDSTONE_ORE : Material.REDSTONE_ORE)
                        : chance < 23 ? (deep ? Material.DEEPSLATE_COPPER_ORE : Material.COPPER_ORE)
                        : chance < 40 ? (deep ? Material.DEEPSLATE_IRON_ORE : Material.IRON_ORE)
                        : (deep ? Material.DEEPSLATE_COAL_ORE : Material.COAL_ORE);
                if ((SeedMixer.at(seed, wx, y, wz) & 3) != 0) data.setBlock(x, y, z, ore);
            }
        }
    }
}
