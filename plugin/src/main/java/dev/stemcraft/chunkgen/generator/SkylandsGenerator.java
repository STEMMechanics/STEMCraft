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

/** Multi-scale island masks in three height bands; no ground plane or bottom bedrock. */
public final class SkylandsGenerator extends TerrainModel {
    private final NoiseSampler small, normal, large, continent, shape, caves, bridges, groups;
    private final DomainWarp warp;
    public SkylandsGenerator(GenerationContext context) {
        super(context);
        small = noise(context, "small", 1.0 / 55, 2);
        normal = noise(context, "normal", 1.0 / 190, 2);
        large = noise(context, "large", 1.0 / 600, 2);
        continent = noise(context, "continent", 1.0 / 1800, 2);
        shape = noise(context, "shape", 1.0 / 75, 2);
        caves = noise(context, "caves", 1.0 / 55, 2);
        bridges = noise(context, "bridges", 1.0 / 400, 1);
        groups = noise(context, "island-groups", 1.0 / 1100, 2);
        warp = new DomainWarp(seed(context, "warp"), 1.0 / 340, 85);
    }
    @Override public TerrainColumn column(int x, int z) {
        double wx = warp.x(x, z), wz = warp.z(x, z);
        double grouping = groups.sample(wx, wz);
        double groupMask = (grouping + .05) * 90;
        double[] masks = new double[3];
        double[] tops = new double[3];
        double[] thickness = new double[3];
        double span = context.maxHeight() - context.minHeight();
        for (int band = 0; band < 3; band++) {
            double bx = wx + band * 1901, bz = wz - band * 2377;
            double a = (small.sample(bx, bz) - .52) * 24;
            double b = (normal.sample(bx, bz) - .40) * 55;
            double c = (large.sample(bx, bz) - .46) * 90;
            double d = (continent.sample(bx, bz) - .65) * 160;
            masks[band] = Math.min(Math.max(Math.max(a, b), Math.max(c, d)), groupMask);
            tops[band] = height(.25 + band * .23) + shape.sample(bx, bz) * 12;
            thickness[band] = Math.min(span * .20, 12 + Math.max(0, masks[band]) * 2.5);
        }
        // A naturally tapered starting island and a few surrounding satellites.
        double start = 1 - Math.hypot(x, z) / 80;
        if (start > 0) {
            masks[1] = Math.max(masks[1], start * 24);
            tops[1] = spawnFloor() + (tops[1] - spawnFloor()) * (1 - start);
            thickness[1] = Math.max(thickness[1], start * 55);
        }
        double connector = smooth(.65, .85, bridges.sample(wx, wz)) * smooth(.05, .3, grouping);
        Biome biome = biome(x, spawnFloor(), z);
        MaterialPalette palette = biome == Biome.SNOWY_TAIGA ? MaterialPalette.FROZEN
                : biome == Biome.BADLANDS ? MaterialPalette.BADLANDS
                : biome == Biome.MUSHROOM_FIELDS ? MaterialPalette.MUSHROOM : MaterialPalette.GRASS;
        return new TerrainColumn() {
            @Override public double density(int y) {
                double density = -100;
                for (int band = 0; band < 3; band++) {
                    double down = (tops[band] - y) / thickness[band];
                    double side = masks[band] - Math.max(0, down) * Math.max(8, masks[band]) * .95;
                    double vertical = Math.min(tops[band] - y + .5, y - (tops[band] - thickness[band]));
                    density = Math.max(density, Math.min(side, vertical));
                }
                if (connector > 0) density = Math.max(density,
                        Math.min(connector * 9 - Math.abs(shape.sample(wx, wz)) * 18,
                                Math.min(y - height(.23), height(.73) - y)));
                if (density <= -4) return density;
                double detail = shape.sample(wx, y * 1.3, wz) * 3;
                double hollow = (Math.abs(caves.sample(wx, y, wz)) - .085) * 45;
                return Math.min(density + detail, hollow);
            }
            @Override public Material material(int y, int depth) { return palette.at(depth); }
        };
    }
    @Override public Biome biome(int x, int y, int z) {
        double c = climate.sample(x, z);
        return c < -.55 ? Biome.MUSHROOM_FIELDS : c < -.3 ? Biome.BADLANDS : c < -.08 ? Biome.SNOWY_TAIGA
                : c < .16 ? Biome.FOREST : c < .4 ? Biome.JUNGLE : Biome.STONY_PEAKS;
    }
    @Override public List<Biome> biomes() {
        return List.of(Biome.MUSHROOM_FIELDS, Biome.BADLANDS, Biome.SNOWY_TAIGA, Biome.FOREST, Biome.JUNGLE, Biome.STONY_PEAKS);
    }
}
