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

import dev.stemcraft.api.service.world.generation.GeneratorDefinition;
import dev.stemcraft.chunkgen.noise.SeedMixer;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import java.util.Random;

/** Local landmarks: sparse, independently seeded and wholly contained in their owning chunk. */
public final class LandmarkPopulator extends BlockPopulator {
    private final GeneratorDefinition definition;
    private final boolean fossils;
    public LandmarkPopulator(GeneratorDefinition definition, boolean fossils) {
        this.definition = definition; this.fossils = fossils;
    }
    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        long cell = SeedMixer.at(seed, Math.floorDiv(chunkX, 32), 0, Math.floorDiv(chunkZ, 32));
        return Math.floorMod(chunkX, 32) == (int) (cell & 31)
                && Math.floorMod(chunkZ, 32) == (int) ((cell >>> 5) & 31);
    }
    @Override public void populate(@NotNull WorldInfo info, @NotNull Random ignored, int chunkX, int chunkZ,
                                   @NotNull LimitedRegion region) {
        long seed = SeedMixer.derive(info.getSeed(), definition.key() + "/v" + definition.version() + "/landmarks");
        if (!candidate(seed, chunkX, chunkZ)) return;
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        if (Math.hypot(x, z) < 128) return;
        long shape = SeedMixer.at(seed, chunkX, 0, chunkZ);
        boolean skeleton = fossils && (shape & 1) == 0;
        for (int y = info.getMaxHeight() - 8; y > info.getMinHeight() + 4; y--) {
            if (!LandmarkSupport.replaceable(region.getType(x, y, z))
                    || !LandmarkSupport.foundation(region.getType(x, y - 1, z))) continue;
            if (!fits(region, x, y, z, skeleton ? 6 : 3)) continue;
            if (skeleton) skeleton(region, x, y, z, shape);
            else ruin(region, x, y, z, shape);
            return;
        }
    }
    private static boolean fits(LimitedRegion region, int x, int y, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (!LandmarkSupport.foundation(region.getType(x + dx, y - 1, z + dz))) return false;
            for (int dy = 0; dy <= 5; dy++)
                if (!LandmarkSupport.replaceable(region.getType(x + dx, y + dy, z + dz))) return false;
        }
        return true;
    }
    private static void ruin(LimitedRegion region, int x, int y, int z, long seed) {
        Biome biome = region.getBiome(x, y, z);
        boolean cave = biome == Biome.DEEP_DARK || biome == Biome.LUSH_CAVES || biome == Biome.DRIPSTONE_CAVES;
        boolean dry = biome == Biome.DESERT || biome == Biome.BADLANDS || biome == Biome.ERODED_BADLANDS;
        Material stone = dry ? Material.SANDSTONE : cave ? Material.CRACKED_DEEPSLATE_BRICKS : Material.MOSSY_STONE_BRICKS;
        int design = (int) ((seed >>> 2) % 3);
        int rotation = (int) ((seed >>> 8) & 3);
        if (design == 0) { // Collapsed L-shaped shelter; no square enclosure.
            for (int i = -3; i <= 2; i++) for (int h = 0; h < 1 + (int) (SeedMixer.at(seed, i, 0, 0) & 3); h++) {
                place(region, x, y, z, i, h, 2, rotation, stone);
                if (i < 2) place(region, x, y, z, -3, h, i, rotation, stone);
            }
            place(region, x, y, z, 2, 0, -2, rotation, Material.COBBLESTONE);
        } else if (design == 1) { // Mostly buried foundations and scattered masonry.
            for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
                long block = SeedMixer.at(seed, dx, 0, dz);
                if ((Math.abs(dx) == 3 || Math.abs(dz) == 2) && (block & 3) != 0)
                    place(region, x, y, z, dx, -1, dz, rotation, stone);
                if ((block & 15) == 0) place(region, x, y, z, dx, 0, dz, rotation, stone);
            }
        } else { // Abandoned camp: decayed bedroll, cold fire and broken supports.
            place(region, x, y, z, 0, 0, 0, rotation, Material.COAL_BLOCK);
            for (int dx : new int[]{-1, 1}) place(region, x, y, z, dx, 0, 0, rotation, Material.COBBLESTONE_SLAB);
            for (int dz = -1; dz <= 1; dz++) place(region, x, y, z, -2, 0, dz, rotation, Material.BROWN_CARPET);
            for (int h = 0; h < 3; h++) place(region, x, y, z, 2, h, 2, rotation, Material.OAK_FENCE);
        }
        // Only one quarter of ruins contain supplies, tucked off-centre.
        if ((seed & 48) == 0) {
            int dx = rotation == 0 ? 1 : rotation == 2 ? -1 : 0;
            int dz = rotation == 1 ? 1 : rotation == 3 ? -1 : 0;
            LandmarkSupport.loot(region, x + dx, y, z + dz, seed, cave);
        }
    }
    private static void skeleton(LimitedRegion region, int x, int y, int z, long seed) {
        int rotation = (int) ((seed >>> 8) & 3);
        int length = (seed & 4) == 0 ? 4 : 5;
        // Raised spine, descending tail, hollow skull and ribs reaching down into the soil.
        for (int a = -length; a <= 3; a++) place(region, x, y, z, a, 3, 0, rotation, Material.BONE_BLOCK);
        place(region, x, y, z, -length - 1, 2, 0, rotation, Material.BONE_BLOCK);
        for (int a = -3; a <= 1; a += 2) for (int side : new int[]{-1, 1}) {
            place(region, x, y, z, a, 3, side, rotation, Material.BONE_BLOCK);
            place(region, x, y, z, a, 2, side * 2, rotation, Material.BONE_BLOCK);
            for (int h = 0; h <= 1; h++) {
                // Missing rib tips make the remains look weathered rather than manufactured.
                if (h == 0 && (SeedMixer.at(seed, a, h, side) & 3) == 0) continue;
                place(region, x, y, z, a, h, side * 3, rotation, Material.BONE_BLOCK);
            }
        }
        for (int a = 4; a <= 5; a++) for (int side = -1; side <= 1; side++) {
            place(region, x, y, z, a, 3, side, rotation, Material.BONE_BLOCK);
            if (side != 0) place(region, x, y, z, a, 2, side, rotation, Material.BONE_BLOCK);
        }
        place(region, x, y, z, 6, 2, 0, rotation, Material.BONE_BLOCK);
        place(region, x, y, z, 2, 0, -4, rotation, Material.BONE_BLOCK);
        place(region, x, y, z, 3, -1, -4, rotation, Material.BONE_BLOCK);
    }
    private static void place(LimitedRegion region, int x, int y, int z, int dx, int dy, int dz,
                              int rotation, Material material) {
        int rx = switch (rotation) { case 1 -> -dz; case 2 -> -dx; case 3 -> dz; default -> dx; };
        int rz = switch (rotation) { case 1 -> dx; case 2 -> -dz; case 3 -> -dx; default -> dz; };
        region.setType(x + rx, y + dy, z + rz, material);
    }
}
