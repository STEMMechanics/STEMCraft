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

package dev.stemcraft.chunkgen.noise;

/** Allocation-free sampling of quintic-interpolated lattice noise. No external dependency. */
public final class NoiseFactory {
    private NoiseFactory() {}
    public static NoiseSampler coherent(long seed) {
        return (x, y, z) -> {
            int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            double fx = fade(x - ix), fy = fade(y - iy), fz = fade(z - iz);
            double a = lerp(value(seed, ix, iy, iz), value(seed, ix + 1, iy, iz), fx);
            double b = lerp(value(seed, ix, iy + 1, iz), value(seed, ix + 1, iy + 1, iz), fx);
            double c = lerp(value(seed, ix, iy, iz + 1), value(seed, ix + 1, iy, iz + 1), fx);
            double d = lerp(value(seed, ix, iy + 1, iz + 1), value(seed, ix + 1, iy + 1, iz + 1), fx);
            return lerp(lerp(a, b, fy), lerp(c, d, fy), fz);
        };
    }
    public static NoiseSampler fractal(long seed, double frequency, int octaves, double gain, double lacunarity) {
        return new FractalNoise(seed, frequency, octaves, gain, lacunarity);
    }
    public static NoiseSampler ridged(NoiseSampler source) { return new RidgedNoise(source); }
    private static double value(long seed, int x, int y, int z) {
        return (SeedMixer.at(seed, x, y, z) >>> 11) * 0x1.0p-52 - 1;
    }
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
