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

package dev.stemcraft.chunkgen.generator;

import dev.stemcraft.api.service.world.generation.GenerationContext;
import dev.stemcraft.chunkgen.noise.*;
import dev.stemcraft.chunkgen.terrain.*;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import java.util.List;

/** Deliberate density distortions; no overflow or historical numerical bug emulation. */
public final class FarawayLandsGenerator extends TerrainModel {
    public enum Regime { SHATTERED, WALLS, SWISS, SPIRES, FOLDED, CALM }
    private final NoiseSampler base, mountain, regions, distortion, cavities;
    private final DomainWarp warp;
    public FarawayLandsGenerator(GenerationContext context) {
        super(context);
        base = noise(context, "base", 1.0 / 1200, 3);
        mountain = NoiseFactory.ridged(noise(context, "mountain", 1.0 / 650, 2));
        regions = noise(context, "regions", 1.0 / 1900, 2);
        distortion = noise(context, "distortion", 1.0 / 150, 2);
        cavities = noise(context, "cavities", 1.0 / 100, 2);
        warp = new DomainWarp(seed(context, "warp"), 1.0 / 600, 260);
    }
    public Regime regime(int x, int z) {
        if (Math.hypot(x, z) < 110) return Regime.CALM;
        double r = regions.sample(x, z);
        return r < -.52 ? Regime.CALM : r < -.28 ? Regime.SPIRES : r < -.06 ? Regime.WALLS
                : r < .16 ? Regime.SHATTERED : r < .4 ? Regime.SWISS : Regime.FOLDED;
    }
    @Override public TerrainColumn column(int x, int z) {
        double wx = warp.x(x, z), wz = warp.z(x, z);
        double baseHeight = clampHeight(height(.37) + base.sample(x, z) * 30 + mountain.sample(x, z) * 14);
        double distance = Math.hypot(x, z);
        double strength = smooth(100, 330, distance);
        double calmHeight = spawnFloor() + (baseHeight - spawnFloor()) * smooth(24, 100, distance);
        double region = regions.sample(x, z);
        // Continuous regime weights avoid hard boundaries between large regions.
        double[] centers = { .04, -.17, .28, -.4, .62, -.72 };
        double[] weights = new double[6];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.exp(-Math.pow((region - centers[i]) / .12, 2));
            total += weights[i];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= total;
        double quantization = smooth(-.5, .5, distortion.sample(wx, wz));
        double qx = wx + (Math.floor(wx / 64) * 64 - wx) * quantization;
        double qz = wz + (Math.floor(wz / 64) * 64 - wz) * quantization;
        double axis = Math.sin(qx / 55 + distortion.sample(wx * .15, wz) * 3);
        double foldedAxis = Math.abs(axis);
        double wallHeight = height(.28) + smooth(.38, .55, foldedAxis) * (height(.92) - height(.28));
        double spireHeight = height(.26) + Math.pow(Math.max(0, mountain.sample(qx * 3, qz * 3)), 5)
                * (height(.93) - height(.26));
        return new TerrainColumn() {
            @Override public double density(int y) {
                if (y <= context.minHeight() + 2) return 10;
                if (y >= context.maxHeight() - 3) return -10;
                double vertical = distortion.sample(qx, y * .85, qz);
                double hole = (Math.abs(cavities.sample(wx, y, wz)) - .14) * 80;
                double normal = calmHeight - y + .5;
                double shattered = Math.min(baseHeight - y + vertical * 45, hole + 5);
                double walls = Math.min(wallHeight - y + vertical * 12, hole + 2);
                double swiss = Math.min(height(.76) - y + vertical * 25, hole - 3);
                double spires = spireHeight - y + vertical * 20;
                double shelves = Math.sin((y - context.minHeight()) / 14.0 + foldedAxis * 4) * 15 + vertical * 8;
                double folded = Math.max(height(.24) - y, Math.min(shelves, height(.88) - y));
                double broken = weights[0] * shattered + weights[1] * walls + weights[2] * swiss
                        + weights[3] * spires + weights[4] * folded + weights[5] * normal;
                return normal + (broken - normal) * strength;
            }
            @Override public Material material(int y, int depth) {
                if (y == context.minHeight()) return Material.BEDROCK;
                if (y < height(.18)) return Material.DEEPSLATE;
                return MaterialPalette.GRASS.at(depth);
            }
        };
    }
    @Override public Biome biome(int x, int y, int z) {
        return switch (regime(x, z)) {
            case CALM -> Biome.MEADOW;
            case SHATTERED -> Biome.WINDSWEPT_FOREST;
            case WALLS, SPIRES -> Biome.STONY_PEAKS;
            case SWISS -> Biome.WINDSWEPT_GRAVELLY_HILLS;
            case FOLDED -> Biome.WINDSWEPT_HILLS;
        };
    }
    @Override public List<Biome> biomes() {
        return List.of(Biome.MEADOW, Biome.WINDSWEPT_FOREST, Biome.STONY_PEAKS, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_HILLS);
    }
}
