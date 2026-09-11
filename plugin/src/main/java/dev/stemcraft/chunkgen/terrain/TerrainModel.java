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

import dev.stemcraft.api.service.world.generation.GenerationContext;
import dev.stemcraft.chunkgen.noise.NoiseFactory;
import dev.stemcraft.chunkgen.noise.NoiseSampler;
import dev.stemcraft.chunkgen.noise.SeedMixer;
import org.bukkit.block.Biome;
import java.util.List;

/** Immutable per-seed terrain composition shared by generation, biomes, heights and spawn queries. */
public abstract class TerrainModel {
    protected final GenerationContext context;
    protected final NoiseSampler climate;
    protected TerrainModel(GenerationContext context) {
        this.context = context;
        climate = noise(context, "biomes", 1.0 / 1600, 2);
    }
    // Construction helpers depend only on immutable input, never the partially built model.
    protected static long seed(GenerationContext context, String layer) {
        return SeedMixer.derive(context.seed(), context.generatorKey() + "/v" + context.generatorVersion() + "/" + layer);
    }
    protected static NoiseSampler noise(GenerationContext context, String layer, double frequency, int octaves) {
        return NoiseFactory.fractal(seed(context, layer), frequency, octaves, .5, 2);
    }
    protected final double height(double fraction) {
        return context.minHeight() + (context.maxHeight() - context.minHeight()) * fraction;
    }
    protected final double clampHeight(double y) {
        return Math.clamp(y, context.minHeight() + 6, context.maxHeight() - 8);
    }
    public boolean hasFossilLandmarks() { return false; }
    public int spawnFloor() { return (int) height(.46); }
    public abstract TerrainColumn column(int x, int z);
    public abstract Biome biome(int x, int y, int z);
    public abstract List<Biome> biomes();
    @FunctionalInterface public interface Columns { TerrainColumn at(int x, int z); }
    public Columns columns(int chunkX, int chunkZ) { return this::column; }
    protected static double smooth(double a, double b, double value) {
        double t = Math.clamp((value - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
