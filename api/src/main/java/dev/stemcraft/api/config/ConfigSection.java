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

package dev.stemcraft.api.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a section of a configuration file.
 */
public interface ConfigSection {

    /**
     * Saves the configuration section to its underlying file.
     */
    void save();

    /**
     * Checks if the configuration contains a value at the specified path.
     *
     * @param path The path to check.
     * @return True if the configuration contains a value at the specified path, false otherwise.
     */
    boolean contains(String path);

    /**
     * Checks if the configuration contains a section at the specified path.
     *
     * @param path The path to check.
     * @return True if the configuration contains a section at the specified path, false otherwise.
     */
    boolean isSection(String path);

    /**
     * Checks if the configuration contains a value at the specified path and is of the specified type.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    String getString(String path, String def);
    default String getString(String path) { return getString(path, ""); }

    /**
     * Gets an integer value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    int getInt(String path, int def);
    default int getInt(String path) { return getInt(path, 0); }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    long getLong(String path, long def);
    default long getLong(String path) { return getLong(path, 0L); }

    /**
     * Gets a float value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    float getFloat(String path, float def);
    default float getFloat(String path) { return getFloat(path, 0); }

    /**
     * Gets a double value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    double getDouble(String path, double def);
    default double getDouble(String path) { return getDouble(path, 0.0); }

    /**
     * Gets a boolean value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    boolean getBoolean(String path, boolean def);
    default boolean getBoolean(String path) { return getBoolean(path, false); }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    List<String> getStringList(String path);

    /**
     * Gets a list of integers from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    List<Integer> getIntegerList(String path);

    /**
     * Gets a list of floats from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    List<Float> getFloatList(String path);

    /**
     * Gets a list of doubles from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    List<Double> getDoubleList(String path);

    /**
     * Gets a list of booleans from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    List<Boolean> getBooleanList(String path);

    /**
     * Gets a list of objects of type T from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    List<?> getList(String path, List<?> def);
    default List<?> getList(String path) { return getList(path, List.of()); }

    /**
     * Sets a value in the configuration at the specified path.
     *
     * @param path The path to set the value at.
     * @param value The value to set.
     */
    void set(String path, Object value);

    /**
     * Removes a value from the configuration at the specified path.
     *
     * @param path The path to remove the value from.
     */
    void remove(String path);

    /**
     * Gets a configuration section at the specified path.
     *
     * @param path The path to get the section from.
     * @param createIfAbsent Whether to create the section if it does not exist.
     * @return The configuration section at the specified path.
     */
    ConfigSection getSection(String path, boolean createIfAbsent);
    default ConfigSection getSection(String path) { return getSection(path, true); }

    /**
     * Creates a configuration section at the specified path.
     *
     * @param path The path to create the section at.
     * @return The newly created configuration section.
     */
    ConfigSection createSection(String path);

    /**
     * Gets the keys in this configuration section.
     *
     * @param deep Whether to get keys recursively.
     * @return A set of keys in this configuration section.
     */
    Set<String> getKeys(boolean deep);

    /**
     * Gets the keys in the configuration section at the specified path.
     *
     * @param path The path to get the keys from.
     * @param deep Whether to get keys recursively.
     * @return A set of keys in the configuration section at the specified path.
     */
    Set<String> getSectionKeys(String path, boolean deep);

    /**
     * Gets a map representation of the configuration section at the specified path.
     *
     * @param path The path to get the map from.
     * @param deep Whether to include nested sections.
     * @return A map representation of the configuration section at the specified path.
     */
    Map<String, Object> getMap(String path, boolean deep);
}