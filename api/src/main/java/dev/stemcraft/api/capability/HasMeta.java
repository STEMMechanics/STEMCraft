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

package dev.stemcraft.api.capability;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Capability for storing arbitrary metadata key-value pairs.
 */
public interface HasMeta<T extends HasMeta<T>> {

    /**
     * Check if the metadata contains a value for the given key.
     *
     * @param key the metadata key.
     * @return true if the metadata contains a value for the given key, false otherwise.
     */
    boolean contains(String key);

    /**
     * Get the metadata value for the given key, or defaultValue if not present.
     *
     * @param key the metadata key.
     * @param type the expected type of the metadata value.
     * @param defaultValue the default value to return if the key is not present.
     * @return the metadata value for the given key, or defaultValue if not present.
     */
    <V> V get(String key, Class<V> type, V defaultValue);
    default <V> V get(String key, Class<V> type) { return get(key, type, null); }

    /**
     * Get the metadata value for the given key, or create and store a new value using the supplier if not present.
     *
     * This overload allows type-safe retrieval of an existing value.
     *
     * @param key the metadata key.
     * @param type the expected type of the metadata value.
     * @param supplier the supplier to create a new value if the key is not present.
     * @return the metadata value for the given key, or a new value created by the supplier if not present.
     */
    <V> V getOrCreate(String key, Class<V> type, Supplier<? extends V> supplier);

    /**
     * Set the metadata value for the given key.
     *
     * @param key the metadata key.
     * @param value the metadata value.
     */
    <V> T set(String key, V value);

    /**
     * Set the metadata value for the given key if not already present.
     *
     * @param key the metadata key.
     * @param value the metadata value.
     */
    <V> T setIfAbsent(String key, V value);

    /**
     * Remove the metadata value for the given key.
     *
     * @param key the metadata key.
     */
    T remove(String key);

    /**
     * Clear all metadata.
     */
    T clear();

    /**
     * Perform the given action for each metadata key-value pair.
     *
     * @param consumer the action to perform for each metadata key-value pair.
     */
    void forEach(BiConsumer<String, Object> consumer);
}