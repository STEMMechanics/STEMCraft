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

/** Stable UTF-16 label hashing and SplitMix64 finalization. Part of the v1 terrain contract. */
public final class SeedMixer {
    private SeedMixer() {}
    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    public static long derive(long seed, String label) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < label.length(); i++) hash = (hash ^ label.charAt(i)) * 0x100000001b3L;
        return mix(seed ^ hash);
    }
    public static long at(long seed, int x, int y, int z) {
        return mix(seed ^ mix(x * 0x632be59bd9b4e019L) ^ mix(y * 0x9e3779b97f4a7c15L)
                ^ mix(z * 0x85157af5d66d2b7dL));
    }
    public static double unit(long value) { return (mix(value) >>> 11) * 0x1.0p-53; }
}
