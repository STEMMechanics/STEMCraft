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

import dev.stemcraft.api.service.world.generation.GenerationContext;
import dev.stemcraft.api.service.world.generation.GeneratorDefinition;
import dev.stemcraft.chunkgen.feature.GenerationFeature;
import dev.stemcraft.chunkgen.feature.OreFeature;
import dev.stemcraft.chunkgen.terrain.TerrainColumn;
import dev.stemcraft.chunkgen.terrain.TerrainModel;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/** Shared Paper pipeline. Factories provide independent terrain models, with no generator-type switches.
 * Models retain only immutable inputs and samplers. No World/chunk references or Bukkit block reads.
 */
public final class StemChunkGenerator extends ChunkGenerator {
    private final GeneratorDefinition definition;
    private final Function<GenerationContext, TerrainModel> factory;
    private final ConcurrentMap<GenerationContext, TerrainModel> models = new ConcurrentHashMap<>();
    private final BiomeProvider biomes = new StemBiomeProvider(this);
    private final List<GenerationFeature> features = List.of(new OreFeature());
    private final GenerationFeature details = new dev.stemcraft.chunkgen.feature.SurfaceDetailFeature();
    private final GenerationFeature vegetation = new dev.stemcraft.chunkgen.feature.SurfaceVegetationFeature();
    private final List<GenerationFeature> extraDetails;
    private final LongAdder chunks = new LongAdder();
    private final LongAdder nanos = new LongAdder();

    public StemChunkGenerator(GeneratorDefinition definition, Function<GenerationContext, TerrainModel> factory) {
        this(definition, factory, List.of());
    }
    public StemChunkGenerator(GeneratorDefinition definition, Function<GenerationContext, TerrainModel> factory,
                              List<GenerationFeature> extraDetails) {
        this.definition = definition; this.factory = factory; this.extraDetails = List.copyOf(extraDetails);
    }
    public GeneratorDefinition definition() { return definition; }
    public long generatedChunks() { return chunks.sum(); }
    public double averageMillis() { long count = chunks.sum(); return count == 0 ? 0 : nanos.sum() / 1_000_000.0 / count; }
    TerrainModel model(WorldInfo info) {
        GenerationContext context = new GenerationContext(info.getSeed(), info.getMinHeight(), info.getMaxHeight(),
                definition.key(), definition.version());
        return models.computeIfAbsent(context, factory);
    }
    /** Shared bounded spawn chamber/shelf. A smooth collar blends into the natural terrain.
     * Only a 24-block radius is affected; density outside it is entirely generator-defined.
     */
    private double density(TerrainModel model, TerrainColumn column, double distance, int y) {
        double natural = column.density(y);
        int floor = model.spawnFloor();
        if (distance >= 24 || y < floor - 12 || y > floor + 16) return natural;
        double weight = Math.clamp((24 - distance) / 12, 0, 1);
        double vertical = Math.min(Math.clamp((y - (floor - 12)) / 4.0, 0, 1),
                Math.clamp((floor + 16 - y) / 4.0, 0, 1));
        double target = y <= floor ? 8 : -8;
        return natural + (target - natural) * weight * vertical;
    }
    @Override public void generateNoise(@NotNull WorldInfo info, @NotNull Random random, int chunkX, int chunkZ,
                                        @NotNull ChunkData data) {
        long start = System.nanoTime();
        TerrainModel model = model(info);
        TerrainModel.Columns columns = model.columns(chunkX, chunkZ);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
            TerrainColumn column = columns.at(wx, wz);
            double distance = Math.hypot(wx, wz);
            int depth = 0;
            for (int y = data.getMaxHeight() - 1; y >= data.getMinHeight(); y--) {
                boolean solid = density(model, column, distance, y) > 0;
                Material material;
                boolean spawn = distance <= 12 && y >= model.spawnFloor() - 2 && y <= model.spawnFloor() + 3;
                if (solid) material = spawn ? Material.STONE : column.material(y, depth++);
                else { material = spawn ? Material.AIR : column.fluid(y); depth = 0; }
                if (material != Material.AIR) data.setBlock(x, y, z, material);
            }
        }
        GenerationContext context = new GenerationContext(info.getSeed(), info.getMinHeight(), info.getMaxHeight(),
                definition.key(), definition.version());
        for (GenerationFeature feature : features) feature.generate(context, chunkX, chunkZ, data);
        nanos.add(System.nanoTime() - start); chunks.increment();
    }
    @Override public void generateSurface(@NotNull WorldInfo info, @NotNull Random random, int chunkX, int chunkZ,
                                          @NotNull ChunkData data) {
        vegetation.generate(new GenerationContext(info.getSeed(), info.getMinHeight(), info.getMaxHeight(),
                definition.key(), definition.version()), chunkX, chunkZ, data);
        details.generate(new GenerationContext(info.getSeed(), info.getMinHeight(), info.getMaxHeight(),
                definition.key(), definition.version()), chunkX, chunkZ, data);
        for (GenerationFeature feature : extraDetails) feature.generate(new GenerationContext(info.getSeed(), info.getMinHeight(),
                info.getMaxHeight(), definition.key(), definition.version()), chunkX, chunkZ, data);
    }
    @Override public @NotNull List<org.bukkit.generator.BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of(new dev.stemcraft.chunkgen.feature.LandmarkPopulator(definition, model(world).hasFossilLandmarks()));
    }
    @Override public int getBaseHeight(@NotNull WorldInfo info, @NotNull Random random, int x, int z, @NotNull HeightMap map) {
        TerrainModel model = model(info);
        TerrainColumn column = model.column(x, z);
        boolean surface = map == HeightMap.WORLD_SURFACE || map == HeightMap.WORLD_SURFACE_WG
                || map == HeightMap.MOTION_BLOCKING || map == HeightMap.MOTION_BLOCKING_NO_LEAVES;
        double distance = Math.hypot(x, z);
        for (int y = info.getMaxHeight() - 1; y >= info.getMinHeight(); y--) {
            if (density(model, column, distance, y) > 0 || (surface && column.fluid(y) != Material.AIR)) return y + 1;
        }
        return info.getMinHeight();
    }
    @Override public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, .5, model(world).spawnFloor() + 1, .5);
    }
    @Override public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo info) { return biomes; }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public boolean shouldGenerateMobs() { return true; }
}
