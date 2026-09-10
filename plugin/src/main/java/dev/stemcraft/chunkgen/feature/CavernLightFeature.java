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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.generator.ChunkGenerator;

/** Deterministic pockets of life and light on cave surfaces, using only this chunk's data. */
public final class CavernLightFeature implements GenerationFeature {
    private final MultipleFacing floorLichen;
    private final CaveVines berries;

    public CavernLightFeature() {
        floorLichen = (MultipleFacing) Material.GLOW_LICHEN.createBlockData();
        floorLichen.setFace(BlockFace.DOWN, true);
        berries = (CaveVines) Material.CAVE_VINES.createBlockData();
        berries.setBerries(true);
        berries.setAge(25); // Keep the initial hanging tips compact.
    }

    @Override public void generate(GenerationContext context, int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        long seed = SeedMixer.derive(context.seed(), context.generatorKey() + "/v" + context.generatorVersion() + "/cavern-light");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
            // Shared world-space patches continue across chunk boundaries, including negative coordinates.
            long patch = SeedMixer.at(seed, Math.floorDiv(wx, 12), 0, Math.floorDiv(wz, 12));
            if ((patch & 3) == 0) continue;
            for (int y = data.getMinHeight() + 1; y < data.getMaxHeight() - 1; y++) {
                if (!data.getType(x, y, z).isAir()) continue;
                long hash = SeedMixer.at(seed, wx, y, wz);
                if (rock(data.getType(x, y + 1, z)) && (hash & 15) == 0) {
                    data.setBlock(x, y, z, berries);
                } else if (rock(data.getType(x, y - 1, z))) {
                    if ((hash & 63) == 0) data.setBlock(x, y - 1, z, Material.GLOWSTONE);
                    else if ((hash & 7) == 0 && (Math.abs((long) wx) > 2 || Math.abs((long) wz) > 2)) data.setBlock(x, y, z, floorLichen);
                }
            }
        }
    }

    private static boolean rock(Material material) {
        return material == Material.STONE || material == Material.DEEPSLATE || material == Material.TUFF
                || material == Material.CALCITE || material == Material.DRIPSTONE_BLOCK
                || material == Material.CLAY || material == Material.BASALT || material == Material.MOSS_BLOCK;
    }
}
