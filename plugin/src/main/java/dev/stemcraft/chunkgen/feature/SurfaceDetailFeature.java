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
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

/** Sparse surface and cavern details; also visits lower floating island bands. */
public final class SurfaceDetailFeature implements GenerationFeature {
    @Override public void generate(GenerationContext context, int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        long seed = SeedMixer.derive(context.seed(), context.generatorKey() + "/v" + context.generatorVersion() + "/details");
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
            if (Math.hypot(chunkX * 16 + x, chunkZ * 16 + z) < 24) continue;
            for (int y = data.getMinHeight() + 1; y < data.getMaxHeight() - 3; y++) {
                if (!data.getType(x, y, z).isAir() || !data.getType(x, y + 1, z).isAir()) continue;
                Material floor = data.getType(x, y - 1, z);
                if (!floor.isSolid()) continue;
                long hash = SeedMixer.at(seed, chunkX * 16 + x, y, chunkZ * 16 + z);
                if ((hash & 15) != 0) continue;
                Biome biome = data.getBiome(x, y, z);
                if (floor == Material.SAND || floor == Material.RED_SAND || floor == Material.COARSE_DIRT) {
                    data.setBlock(x, y, z, Material.DEAD_BUSH);
                } else if (floor == Material.GRASS_BLOCK || floor == Material.MOSS_BLOCK) {
                    data.setBlock(x, y, z, (hash & 64) == 0 ? Material.SHORT_GRASS : Material.POPPY);
                } else if (floor == Material.MYCELIUM) {
                    data.setBlock(x, y, z, Material.BROWN_MUSHROOM);
                } else if (biome == Biome.LUSH_CAVES && (floor == Material.STONE || floor == Material.CLAY)) {
                    data.setBlock(x, y - 1, z, Material.MOSS_BLOCK);
                    data.setBlock(x, y, z, (hash & 64) == 0 ? Material.AZALEA : Material.BROWN_MUSHROOM);
                    if ((hash & 256) == 0) data.setBlock(x, y - 1, z, Material.SHROOMLIGHT);
                } else if (biome == Biome.DEEP_DARK
                        && (floor == Material.DEEPSLATE || floor == Material.TUFF) && (hash & 64) == 0) {
                    data.setBlock(x, y, z, Material.BROWN_MUSHROOM);
                } else if (biome == Biome.DRIPSTONE_CAVES && floor == Material.STONE) {
                    data.setBlock(x, y, z, Material.DRIPSTONE_BLOCK);
                }
            }
        }
    }
}
