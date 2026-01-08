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

package dev.stemcraft.api.factory;

import org.bukkit.generator.ChunkGenerator;

/**
 * Functional interface for creating ChunkGenerator instances with specific options.
 */
@FunctionalInterface
public interface ChunkGeneratorFactory {

    /**
     * Creates a ChunkGenerator instance based on the provided options.
     *
     * @param options A string representing configuration options for the ChunkGenerator.
     * @return A new ChunkGenerator instance configured with the given options.
     */
    ChunkGenerator create(String options);
}