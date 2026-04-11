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

package dev.stemcraft.capability;

import dev.stemcraft.api.capability.HasMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Capability for storing arbitrary metadata key-value pairs.
 */
public class HasMetaImpl<T extends HasMeta<T>> implements HasMeta<T> {

    /**
     * Metadata storage.
     */
    private final Map<String, Object> meta = new HashMap<>();

    /**
     * Returns the current instance cast to the generic type T.
     *
     * @return the current instance as type T.
     */
    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    /**
     * Check if the metadata contains a value for the given key.
     *
     * @param key the metadata key.
     * @return true if the metadata contains a value for the given key, false otherwise.
     */
    @Override
    public boolean contains(String key) {
        return meta.containsKey(key);
    }

    /**
     * Get the metadata value for the given key, or defaultValue if not present.
     *
     * @param key the metadata key.
     * @param type the expected type of the metadata value.
     * @param defaultValue the default value to return if the key is not present.
     * @return the metadata value for the given key, or defaultValue if not present.
     */
    @Override
    public <V> V get(String key, Class<V> type, V defaultValue) {
        Object value = meta.get(key);
        if (value == null) return defaultValue;
        if (!type.isInstance(value)) return defaultValue;
        return type.cast(value);
    }

    /**
     * Get the metadata value for the given key, or create and store a new value using the supplier if not present.
     *
     * @param key the metadata key.
     * @param supplier the supplier to create a new value if the key is not present.
     * @return the metadata value for the given key, or a new value created by the supplier if not present.
     */
    @Override
    public <V> V getOrCreate(String key, Class<V> type, Supplier<? extends V> supplier) {
        Object existing = meta.get(key);
        if (existing != null) {
            if (!type.isInstance(existing)) {
                throw new IllegalStateException(
                        "Metadata key '" + key + "' is not of type " + type.getName() + ", got " + existing.getClass().getName()
                );
            }
            return type.cast(existing);
        }

        V created = supplier.get();
        meta.put(key, created);
        return created;
    }

    /**
     * Set the metadata value for the given key.
     *
     * @param key the metadata key.
     * @param value the metadata value.
     */
    @Override
    public <V> T set(String key, V value) {
        meta.put(key, value);
        return self();
    }

    /**
     * Set the metadata value for the given key if not already present.
     *
     * @param key the metadata key.
     * @param value the metadata value.
     */
    @Override
    public <V> T setIfAbsent(String key, V value) {
        meta.putIfAbsent(key, value);
        return self();
    }

    /**
     * Remove the metadata value for the given key.
     *
     * @param key the metadata key.
     */
    @Override
    public T remove(String key) {
        meta.remove(key);
        return self();
    }

    /**
     * Clear all metadata.
     */
    @Override
    public T clear() {
        meta.clear();
        return self();
    }

    /**
     * Perform the given action for each metadata key-value pair.
     *
     * @param consumer the action to perform for each metadata key-value pair.
     */
    @Override
    public void forEach(BiConsumer<String, Object> consumer) {
        meta.forEach(consumer);
    }
}