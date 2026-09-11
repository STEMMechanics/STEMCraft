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

package dev.stemcraft.chunkgen;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Uses the same cached model as the chunk generator. */
final class StemBiomeProvider extends BiomeProvider {
    private final StemChunkGenerator generator;
    StemBiomeProvider(StemChunkGenerator generator) { this.generator = generator; }
    @Override public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
        return generator.model(info).biome(x, y, z);
    }
    @Override public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
        return generator.model(info).biomes();
    }
}
