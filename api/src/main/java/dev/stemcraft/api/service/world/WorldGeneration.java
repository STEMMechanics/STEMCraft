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

    /** Immutable metadata snapshots for versioned generators. Legacy factories have no metadata. */
    default Collection<dev.stemcraft.api.service.world.generation.GeneratorDefinition> getGenerators() { return List.of(); }

    default Optional<dev.stemcraft.api.service.world.generation.GeneratorDefinition> getGenerator(org.bukkit.NamespacedKey key) { return Optional.empty(); }

    /** Lookup a retained historical generation version. */
    default Optional<dev.stemcraft.api.service.world.generation.GeneratorDefinition> getGenerator(org.bukkit.NamespacedKey key, int version) {
        return getGenerator(key).filter(definition -> definition.version() == version);
    }

    default Optional<dev.stemcraft.api.service.world.generation.GeneratedWorld> getGeneratedWorld(org.bukkit.World world) { return Optional.empty(); }

    default boolean isGeneratedWorld(org.bukkit.World world) { return getGeneratedWorld(world).isPresent(); }

    /** Register on the server thread while owner is enabled. The key namespace must match the
     * owner's plugin name (lowercase). Duplicate key/version pairs are rejected; one owner may retain multiple versions of its key. Factories must return fresh,
     * thread-safe generators; use WorldInfo for seed and height inputs. Registrations disappear
     * on owner disable; already loaded worlds keep their attached generator until unloaded.
     */
    default void registerGenerator(org.bukkit.plugin.Plugin owner,
            dev.stemcraft.api.service.world.generation.GeneratorDefinition definition,
            ChunkGeneratorFactory factory) { throw new UnsupportedOperationException(); }

    /** Server-thread only. Cannot remove another plugin's registration. */
    default boolean unregisterGenerator(org.bukkit.plugin.Plugin owner, org.bukkit.NamespacedKey key) {
        throw new UnsupportedOperationException();
    }


    /**
     * Returns a sorted list of available chunk generator keys.
     *
     * @return A list of available chunk generator keys.
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
     * Checks if a chunk generator key is available for a world.
     * <p>
     * Registered STEMCraft generators are always available. Bukkit plugin
     * generators are available only when the named plugin can provide a
     * generator for the supplied world name.
     *
     * @param key The generator key or Bukkit plugin[:id] generator spec.
     * @param worldName The world name the generator would be used for.
     * @return True if the generator can be resolved, false otherwise.
     */
    default boolean isAvailable(@NotNull String key, @NotNull String worldName) {
        return isRegistered(key);
    }

    /**
     * Returns tab-completion suggestions for a generator's options.
     *
     * @param key The chunk generator key.
     * @param cfg The current configuration string.
     * @return A list of suggested option values.
     */
    @NotNull List<String> tabCompleteOptions(@NotNull String key, @NotNull String cfg);

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
