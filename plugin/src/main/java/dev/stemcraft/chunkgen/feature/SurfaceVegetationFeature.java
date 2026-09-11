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

/** Small local trees fit wholly inside their deterministic 16-block placement cell.
 * This feature never truncates a crown at a chunk boundary or asks for neighboring chunks.
 */
public final class SurfaceVegetationFeature implements GenerationFeature {
    private final java.util.Map<Material, org.bukkit.block.data.BlockData[]> leafStates = new java.util.EnumMap<>(Material.class);
    public SurfaceVegetationFeature() {
        for (Material material : new Material[]{Material.OAK_LEAVES, Material.JUNGLE_LEAVES, Material.SPRUCE_LEAVES}) {
            org.bukkit.block.data.BlockData[] distances = new org.bukkit.block.data.BlockData[7];
            for (int i = 0; i < distances.length; i++) {
                var leaves = (org.bukkit.block.data.type.Leaves) material.createBlockData();
                leaves.setDistance(i + 1);
                distances[i] = leaves;
            }
            leafStates.put(material, distances);
        }
    }
    @Override public void generate(GenerationContext context, int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        long seed = SeedMixer.derive(context.seed(), context.generatorKey() + "/v" + context.generatorVersion() + "/vegetation");
        long cell = SeedMixer.at(seed, chunkX, 0, chunkZ);
        if ((cell & 3) == 0) return;
        int x = 4 + (int) ((cell >>> 4) & 7), z = 4 + (int) ((cell >>> 7) & 7);
        int ground = data.getMaxHeight() - 1;
        while (ground > data.getMinHeight() && data.getType(x, ground, z).isAir()) ground--;
        for (; ground > data.getMinHeight(); ground--) {
            if (data.getType(x, ground, z).isSolid()) tree(data, x, ground, z, cell);
        }
    }
    private void tree(ChunkGenerator.ChunkData data, int x, int ground, int z, long cell) {
        Material floor = data.getType(x, ground, z);
        if (floor != Material.GRASS_BLOCK && floor != Material.MYCELIUM && floor != Material.SNOW_BLOCK) return;
        Biome biome = data.getBiome(x, ground, z);
        boolean jungle = biome == Biome.JUNGLE;
        boolean frozen = biome == Biome.SNOWY_TAIGA;
        boolean mushroom = biome == Biome.MUSHROOM_FIELDS;
        if (!jungle && !frozen && !mushroom && biome != Biome.FOREST && biome != Biome.WINDSWEPT_FOREST
                && biome != Biome.WOODED_BADLANDS) return;
        int height = 4 + (int) ((cell >>> 10) & 1);
        if (ground + height + 2 >= data.getMaxHeight()) return;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            for (int y = ground + 1; y <= ground + height + 1; y++)
                if (!data.getType(x + dx, y, z + dz).isAir()) return;
        Material log = mushroom ? Material.MUSHROOM_STEM : jungle ? Material.JUNGLE_LOG : frozen ? Material.SPRUCE_LOG : Material.OAK_LOG;
        Material leaves = mushroom ? Material.RED_MUSHROOM_BLOCK : jungle ? Material.JUNGLE_LEAVES
                : frozen ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES;
        for (int y = ground + height - 1; y <= ground + height + 1; y++) {
            int radius = y == ground + height + 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                if (mushroom) data.setBlock(x + dx, y, z + dz, leaves);
                else {
                    int distance = Math.clamp(Math.abs(dx) + Math.abs(dz) + Math.max(0, y - ground - height), 1, 7);
                    data.setBlock(x + dx, y, z + dz, leafStates.get(leaves)[distance - 1]);
                }
            }
        }
        for (int y = ground + 1; y <= ground + height; y++) data.setBlock(x, y, z, log);
    }
}
