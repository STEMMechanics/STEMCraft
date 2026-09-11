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

/** Immutable independent horizontal warp fields, evaluated once per terrain column. */
public final class DomainWarp {
    private final NoiseSampler xNoise, zNoise;
    private final double strength;
    public DomainWarp(long seed, double frequency, double strength) {
        if (!Double.isFinite(strength)) throw new IllegalArgumentException("Invalid warp strength");
        xNoise = NoiseFactory.fractal(SeedMixer.derive(seed, "x"), frequency, 2, .5, 2);
        zNoise = NoiseFactory.fractal(SeedMixer.derive(seed, "z"), frequency, 2, .5, 2);
        this.strength = strength;
    }
    public double x(double x, double z) { return x + xNoise.sample(x, z) * strength; }
    public double z(double x, double z) { return z + zNoise.sample(x, z) * strength; }
}
