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

import dev.stemcraft.chunkgen.noise.SeedMixer;

/** One potential impact per 768-block region. Adjacent cells are included because rims cross borders. */
public final class CraterFeature implements RegionFeature {
    public static final int REGION_SIZE = 768;
    private final long seed;
    public CraterFeature(long seed) { this.seed = seed; }
    public record Crater(double x, double z, double radius, boolean impact) {}
    public Crater crater(int regionX, int regionZ) {
        long cell = SeedMixer.at(seed, regionX, 0, regionZ);
        double choice = SeedMixer.unit(cell);
        double size = SeedMixer.unit(cell ^ 0x70eeL);
        double radius = choice < .5 ? 10 + size * 15 : choice < .82 ? 25 + size * 35
                : choice < .97 ? 60 + size * 80 : 150 + size * 150;
        return new Crater((double) regionX * REGION_SIZE + SeedMixer.unit(cell ^ 0x1aL) * REGION_SIZE,
                (double) regionZ * REGION_SIZE + SeedMixer.unit(cell ^ 0x2bL) * REGION_SIZE,
                radius, choice > .9);
    }
    /** Cache the nine candidates once per chunk/model column batch, never per block. */
    public Crater[] nearby(int x, int z) {
        int rx = RegionFeature.coordinate(x, REGION_SIZE), rz = RegionFeature.coordinate(z, REGION_SIZE);
        Crater[] craters = new Crater[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            craters[i++] = crater(rx + dx, rz + dz);
        return craters;
    }
    @Override public double sample(int x, int z) { return contribution(nearby(x, z), x, z); }
    public static double contribution(Crater[] craters, int x, int z) {
        double height = 0;
        for (Crater crater : craters) {
            double r = Math.hypot(x - crater.x, z - crater.z) / crater.radius;
            if (r >= 1.25) continue;
            double bowl = r < 1 ? -crater.radius * .24 * square(1 - r * r) : 0;
            double rim = crater.radius * .065 * square(Math.max(0, 1 - Math.abs(r - 1) / .25));
            double uplift = crater.radius > 140 ? crater.radius * .09 * square(Math.max(0, 1 - r / .22)) : 0;
            height += bowl + rim + uplift;
        }
        return height;
    }
    public static boolean impact(Crater[] craters, int x, int z) {
        for (Crater c : craters) if (c.impact && Math.hypot(x - c.x, z - c.z) < c.radius * .65) return true;
        return false;
    }
    private static double square(double n) { return n * n; }
}
