/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.api.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ConfigSection {
    /**
     * Saves the configuration section to its underlying file.
     */
    void save();

    /**
     * Checks if the configuration contains a value at the specified path.
     */
    boolean contains(String path);

    /**
     * Checks if the configuration contains a section at the specified path.
     */
    boolean isSection(String path);

    /**
     * Checks if the configuration contains a value at the specified path and is of the specified type.
     */
    String getString(String path, String def);
    default String getString(String path) { return getString(path, ""); }

    /**
     * Gets an integer value from the configuration at the specified path.
     */
    int getInt(String path, int def);
    default int getInt(String path) { return getInt(path, 0); }

    /**
     * Gets a long value from the configuration at the specified path.
     */
    long getLong(String path, long def);
    default long getLong(String path) { return getLong(path, 0L); }

    /**
     * Gets a float value from the configuration at the specified path.
     */
    float getFloat(String path, float def);
    default float getFloat(String path) { return getFloat(path, 0); }

    /**
     * Gets a double value from the configuration at the specified path.
     */
    double getDouble(String path, double def);
    default double getDouble(String path) { return getDouble(path, 0.0); }

    /**
     * Gets a boolean value from the configuration at the specified path.
     */
    boolean getBoolean(String path, boolean def);
    default boolean getBoolean(String path) { return getBoolean(path, false); }

    /**
     * Gets a long value from the configuration at the specified path.
     */
    List<String> getStringList(String path);

    /**
     * Gets a list of integers from the configuration at the specified path.
     */
    List<Integer> getIntegerList(String path);

    /**
     * Gets a list of floats from the configuration at the specified path.
     */
    List<Float> getFloatList(String path);

    /**
     * Gets a list of doubles from the configuration at the specified path.
     */
    List<Double> getDoubleList(String path);

    /**
     * Gets a list of booleans from the configuration at the specified path.
     */
    List<Boolean> getBooleanList(String path);

    /**
     * Gets a list of objects of type T from the configuration at the specified path.
     */
    List<?> getList(String path, List<?> def);
    default List<?> getList(String path) { return getList(path, List.of()); }

    /**
     * Sets a value in the configuration at the specified path.
     */
    void set(String path, Object value);

    /**
     * Removes a value from the configuration at the specified path.
     */
    void remove(String path);

    /**
     * Gets a configuration section at the specified path.
     */
    ConfigSection getSection(String path);

    /**
     * Creates a configuration section at the specified path.
     */
    ConfigSection createSection(String path);

    /**
     * Gets the keys in this configuration section.
     */
    Set<String> getKeys(boolean deep);

    /**
     * Gets the keys in the configuration section at the specified path.
     */
    Set<String> getSectionKeys(String path, boolean deep);

    /**
     * Gets a map representation of the configuration section at the specified path.
     */
    Map<String, Object> getMap(String path, boolean deep);
}