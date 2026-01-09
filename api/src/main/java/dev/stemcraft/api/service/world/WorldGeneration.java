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

package dev.stemcraft.api.service.world;

import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public interface WorldGeneration {

    /**
     * Returns a sorted list of all registered chunk generator keys.
     *
     * @return A list of registered chunk generator keys.
     */
    @NotNull List<String> list();

    /**
     * Registers a new chunk generator factory with the given key.
     *
     * @param key The unique key for the chunk generator.
     * @param factory The factory to create chunk generator instances.
     */
    void register(@NotNull String key, @NotNull ChunkGeneratorFactory factory);

    /**
     * Checks if a chunk generator with the given key is registered.
     *
     * @param key The chunk generator key.
     * @return True if the generator is registered, false otherwise.
     */
    boolean isRegistered(@NotNull String key);

    /**
     * Creates a new chunk generator instance for the given key and configuration.
     *
     * @param key The chunk generator key.
     * @param cfg The configuration string for the generator.
     * @return A new ChunkGenerator instance.
     * @throws IllegalArgumentException if the key is unknown.
     */
    @NotNull ChunkGenerator get(@NotNull String key, @NotNull String cfg);
}
