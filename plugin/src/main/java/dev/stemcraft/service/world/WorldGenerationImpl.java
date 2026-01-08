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

package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.api.service.world.WorldGeneration;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing world chunk generators.
 */
public class WorldGenerationImpl implements WorldGeneration {
    private final STEMCraftAPI api;
    private final Map<String, ChunkGeneratorFactory> registry = new ConcurrentHashMap<>();

    /**
     * Creates a new WorldGeneration service.
     *
     * @param api The STEMCraft API instance.
     */
    public WorldGenerationImpl(STEMCraftAPI api) {
        this.api = api;
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        api.tabComplete().register("world-generators", (player, args) -> list());
    }

    /**
     * Called when the service is disabled.
     */
    @SuppressWarnings("EmptyMethod")
    public void onDisable() {
        // not used
    }

    /**
     * Returns a sorted list of all registered chunk generator keys.
     *
     * @return A list of registered chunk generator keys.
     */
    public List<String> list() {
        List<String> out = new ArrayList<>(registry.keySet());
        Collections.sort(out);
        return out;
    }

    /**
     * Registers a new chunk generator factory with the given key.
     *
     * @param key     The unique key for the chunk generator.
     * @param factory The factory to create chunk generator instances.
     */
    public void register(String key, ChunkGeneratorFactory factory) {
        String k = normalizeKey(key);
        if (factory == null) throw new IllegalArgumentException("factory cannot be null");
        registry.put(k, factory);
    }

    /**
     * Checks if a chunk generator with the given key is registered.
     *
     * @param key The chunk generator key.
     * @return True if the generator is registered, false otherwise.
     */
    public boolean isRegistered(String key) {
        String k = normalizeKey(key);
        return registry.containsKey(k);
    }

    /**
     * Creates a new chunk generator instance for the given key and configuration.
     *
     * @param key The chunk generator key.
     * @param cfg The configuration string for the generator.
     * @return A new ChunkGenerator instance.
     * @throws IllegalArgumentException if the key is unknown.
     */
    public ChunkGenerator get(String key, String cfg) {
        String k = normalizeKey(key);
        ChunkGeneratorFactory f = registry.get(k);
        if (f == null) throw new IllegalArgumentException("Unknown generator key: " + key);
        return f.create(cfg);
    }

    /**
     * Normalizes a chunk generator key.
     *
     * @param key The chunk generator key.
     * @return The normalized key.
     * @throws IllegalArgumentException if the key is null or empty.
     */
    private static String normalizeKey(String key) {
        if (key == null) throw new IllegalArgumentException("key cannot be null");
        String k = key.trim();
        if (k.isEmpty()) throw new IllegalArgumentException("key cannot be empty");
        return k.toLowerCase(Locale.ROOT);
    }
}