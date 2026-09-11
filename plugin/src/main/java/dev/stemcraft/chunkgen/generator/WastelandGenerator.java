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
import dev.stemcraft.chunkgen.feature.CraterFeature;
import dev.stemcraft.chunkgen.noise.DomainWarp;
import dev.stemcraft.chunkgen.noise.NoiseFactory;
import dev.stemcraft.chunkgen.noise.NoiseSampler;
import dev.stemcraft.chunkgen.terrain.*;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import java.util.List;

/** Broad buildable plains with localized erosion, long channels and regional impacts. */
public final class WastelandGenerator extends TerrainModel {
    private final NoiseSampler continental, erosion, rivers, mountains, oases;
    private final DomainWarp warp;
    private final CraterFeature craters;
    public WastelandGenerator(GenerationContext context) {
        super(context);
        oases = noise(context, "oases", 1.0 / 160, 2);
        continental = noise(context, "continental", 1.0 / 1300, 3);
        erosion = NoiseFactory.ridged(noise(context, "erosion", 1.0 / 360, 2));
        rivers = noise(context, "rivers", 1.0 / 900, 2);
        mountains = noise(context, "mountains", 1.0 / 1800, 2);
        warp = new DomainWarp(seed(context, "warp"), 1.0 / 550, 140);
        craters = new CraterFeature(seed(context, "craters"));
    }
    @Override public boolean hasFossilLandmarks() { return true; }
    private double oasis(int x, int z) { return smooth(.48, .66, oases.sample(x, z)); }
    @Override public Columns columns(int chunkX, int chunkZ) {
        CraterFeature.Crater[] nearby = craters.nearby(chunkX * 16, chunkZ * 16);
        return (x, z) -> column(x, z, nearby);
    }
    @Override public TerrainColumn column(int x, int z) { return column(x, z, craters.nearby(x, z)); }
    private TerrainColumn column(int x, int z, CraterFeature.Crater[] nearby) {
        double wx = warp.x(x, z), wz = warp.z(x, z);
        double land = continental.sample(x, z);
        double mesa = smooth(.15, .5, land) * 38;
        double ridge = erosion.sample(wx, wz);
        double mountain = smooth(.35, .8, mountains.sample(x, z)) * 85;
        double channel = 1 - smooth(.018, .065, Math.abs(rivers.sample(wx, wz)));
        double ground = height(.40) + land * 13 + mesa + mountain + (mesa + mountain) * ridge * .24
                - channel * 18 + CraterFeature.contribution(nearby, x, z);
        double wet = oasis(x, z);
        ground -= wet * 4;
        double calm = smooth(20, 95, Math.hypot(x, z));
        final double top = clampHeight(spawnFloor() + (ground - spawnFloor()) * calm);
        boolean impact = CraterFeature.impact(nearby, x, z);
        Biome biome = biome(x, (int) top, z);
        MaterialPalette palette = wet > .1 || biome == Biome.WOODED_BADLANDS ? MaterialPalette.GRASS
                : biome == Biome.BADLANDS || biome == Biome.ERODED_BADLANDS ? MaterialPalette.BADLANDS
                : biome == Biome.DESERT ? MaterialPalette.DESERT : MaterialPalette.DRY;
        return new TerrainColumn() {
            @Override public double density(int y) { return top - y + .5; }
            @Override public Material fluid(int y) {
                return wet > .65 && y <= (int) top + 2 ? Material.WATER : Material.AIR;
            }
            @Override public Material material(int y, int depth) {
                if (y == context.minHeight()) return Material.BEDROCK;
                if (impact && depth < 4) return depth == 0 ? Material.BASALT : Material.BLACKSTONE;
                if (channel > .65 && depth == 0) return Material.GRAVEL;
                return palette.at(depth);
            }
        };
    }
    @Override public Biome biome(int x, int y, int z) {
        if (oasis(x, z) > 0) return Biome.FOREST;
        double c = climate.sample(x, z);
        return c > .6 ? Biome.WOODED_BADLANDS : c > .18 ? Biome.BADLANDS
                : c < -.45 ? Biome.ERODED_BADLANDS : c < -.15 ? Biome.DESERT : Biome.PLAINS;
    }
    @Override public List<Biome> biomes() {
        return List.of(Biome.WOODED_BADLANDS, Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.DESERT, Biome.PLAINS, Biome.FOREST);
    }
}
