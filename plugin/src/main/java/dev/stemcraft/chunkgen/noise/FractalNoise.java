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

/** Immutable octave stack; all samplers and amplitudes are constructed once. */
public final class FractalNoise implements NoiseSampler {
    private final NoiseSampler[] layers;
    private final double frequency, gain, lacunarity, normalization;
    public FractalNoise(long seed, double frequency, int octaves, double gain, double lacunarity) {
        if (!Double.isFinite(frequency) || frequency <= 0 || octaves < 1 || octaves > 12
                || !Double.isFinite(gain) || gain <= 0 || gain > 1
                || !Double.isFinite(lacunarity) || lacunarity < 1 || lacunarity > 4)
            throw new IllegalArgumentException("Invalid noise parameters");
        this.frequency = frequency; this.gain = gain; this.lacunarity = lacunarity;
        layers = new NoiseSampler[octaves];
        double total = 0, amplitude = 1;
        for (int i = 0; i < octaves; i++) {
            layers[i] = NoiseFactory.coherent(SeedMixer.derive(seed, "octave/" + i));
            total += amplitude; amplitude *= gain;
        }
        normalization = total;
    }
    @Override public double sample(double x, double y, double z) {
        double sum = 0, amplitude = 1, scale = frequency;
        for (NoiseSampler layer : layers) {
            sum += layer.sample(x * scale, y * scale, z * scale) * amplitude;
            scale *= lacunarity; amplitude *= gain;
        }
        return sum / normalization;
    }
}
