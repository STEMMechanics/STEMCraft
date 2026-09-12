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

/** Solid world carved by broad folded fields, connected passages and preserved support columns. */
public final class DeepGenerator extends TerrainModel {
    private final NoiseSampler cavern, detail, pillars, lakes, ravines, geology;
    private final DomainWarp warp;
    public DeepGenerator(GenerationContext context) {
        super(context);
        cavern = noise(context, "cavern", 1.0 / 210, 2);
        detail = noise(context, "detail", 1.0 / 65, 2);
        pillars = noise(context, "pillars", 1.0 / 155, 2);
        lakes = noise(context, "lakes", 1.0 / 480, 2);
        ravines = noise(context, "ravines", 1.0 / 420, 2);
        geology = noise(context, "geology", 1.0 / 90, 1);
        warp = new DomainWarp(seed(context, "warp"), 1.0 / 400, 100);
    }
    /**
     * Map the first exposed cavern floor below the generated roof. Solid columns without
     * a cavern are transparent, and lower stacked caves remain hidden by the first floor.
     * @return a stateless policy suitable for concurrent map workers
     */
    public static dev.stemcraft.api.service.world.generation.GeneratorMapRenderer mapRenderer() {
        return new dev.stemcraft.api.service.world.generation.GeneratorMapRenderer() {
            @Override public String displayName() { return "The Deep"; }
            @Override public int surfaceY(Column column) {
                int roof = (int) (column.minY() + (column.maxY() - column.minY()) * .66) - 8;
                int y = Math.min(roof - 1, column.maxY() - 1);
                while (y >= column.minY() && !column.isAir(y)) y--;
                while (y >= column.minY() && !column.isVisible(y)) y--;
                return y;
            }
            @Override public int color(Column column, int y, int argb) {
                // Elevation shading uses the selected cavern floor, never the roof heightmap.
                double brightness = .65 + .35 * Math.clamp(
                    (y - column.minY()) / ((column.maxY() - column.minY()) * .66), 0, 1);
                int red = (int) (((argb >>> 16) & 255) * brightness);
                int green = (int) (((argb >>> 8) & 255) * brightness);
                int blue = (int) ((argb & 255) * brightness);
                return (argb & 0xff000000) | (red << 16) | (green << 8) | blue;
            }
        };
    }

    private double cavernHeight(double fraction) { return height(.66 * fraction); }
    private double roofStart() { return (int) cavernHeight(1) - 8; }
    private boolean roofBedrock(int y) { return y == context.maxHeight() - 1 || y == (int) roofStart(); }
    @Override public int spawnFloor() { return (int) cavernHeight(.46); }
    @Override public TerrainColumn column(int x, int z) {
        double wx = warp.x(x, z), wz = warp.z(x, z);
        double pillar = smooth(.36, .68, pillars.sample(wx, wz));
        double ravine = 1 - smooth(.015, .055, Math.abs(ravines.sample(wx, wz)));
        double lake = lakes.sample(wx, wz);
        int water = (int) cavernHeight(.32);
        int lava = (int) cavernHeight(.09);
        double lakeFloor = water - smooth(.3, .65, lake) * 14;
        double rock = geology.sample(x, z);
        return new TerrainColumn() {
            @Override public double density(int y) {
                if (y <= context.minHeight() + 3 || y >= roofStart()) return 20;
                double vertical = Math.min(smooth(cavernHeight(.04), cavernHeight(.18), y), 1 - smooth(cavernHeight(.78), cavernHeight(.98), y));
                double broad = Math.abs(cavern.sample(wx, y * 1.12, wz));
                double fine = detail.sample(wx, y * .8, wz);
                double density = (broad - .26 * vertical) * 65 + pillar * 24 + fine * 3;
                double passage = (Math.abs(detail.sample(wx * .65, y * .7, wz * .65)) - .075) * 35;
                density = Math.min(density, passage + (1 - vertical) * 20 + pillar * 14);
                density -= ravine * vertical * 9;
                // Horizontal rock shelves retain ledges along cavern walls.
                double shelf = Math.pow(Math.max(0, Math.cos((y - context.minHeight()) * .105)), 12);
                density += shelf * 2;
                if (lake > .3 && y <= water + 1) density = Math.max(density, (lakeFloor - y) * 3);
                return density;
            }
            @Override public Material material(int y, int depth) {
                if (y == context.minHeight() || roofBedrock(y)) return Material.BEDROCK;
                if (y < lava + 8 && depth < 2) return rock > .3 ? Material.MAGMA_BLOCK : Material.BASALT;
                if (depth < 2 && y <= water && lake > .3) return Material.CLAY;
                if (rock > .52) return Material.CALCITE;
                if (rock < -.5) return Material.TUFF;
                if (depth == 0 && rock > .2) return Material.DRIPSTONE_BLOCK;
                if (depth == 0 && rock < -.25) return Material.GRAVEL;
                return y < cavernHeight(.35) ? Material.DEEPSLATE : Material.STONE;
            }
            @Override public Material fluid(int y) {
                if (y <= lava) return Material.LAVA;
                // Separate low-frequency lake regions, never a universal aquifer.
                return lake > .3 && y <= water && y > water - 14 ? Material.WATER : Material.AIR;
            }
        };
    }
    @Override public Biome biome(int x, int y, int z) {
        if (y < cavernHeight(.22)) return Biome.DEEP_DARK;
        return climate.sample(x, z) > .18 ? Biome.LUSH_CAVES : Biome.DRIPSTONE_CAVES;
    }
    @Override public List<Biome> biomes() { return List.of(Biome.DEEP_DARK, Biome.LUSH_CAVES, Biome.DRIPSTONE_CAVES); }
}
