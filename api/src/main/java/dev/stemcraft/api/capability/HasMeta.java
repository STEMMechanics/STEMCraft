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
public interface HasMeta {

    /**
     * Check if the metadata contains a value for the given key.
     */
    boolean contains(String key);

    /**
     * Get the metadata value for the given key, or defaultValue if not present.
     */
    <T> T get(String key, Class<T> type, T defaultValue);
    default <T> T get(String key, Class<T> type) { return get(key, type, null); }

    /**
     * Get the metadata value for the given key, or create and store a new value using the supplier if not present.
     */
    <T> T getOrCreate(String key, Supplier<T> supplier);

    /**
     * Set the metadata value for the given key.
     */
    <T> void set(String key, T value);

    /**
     * Set the metadata value for the given key if not already present.
     */
    <T> void setIfAbsent(String key, T value);

    /**
     * Remove the metadata value for the given key.
     */
    void remove(String key);

    /**
     * Clear all metadata.
     */
    void clear();

    /**
     * Perform the given action for each metadata key-value pair.
     */
    void forEach(BiConsumer<String, Object> consumer);
}