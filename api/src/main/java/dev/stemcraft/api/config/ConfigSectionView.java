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

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a read only section of a configuration file.
 */
public interface ConfigSectionView {

    /**
     * Checks if the configuration contains a value at the specified path.
     *
     * @param path The path to check.
     * @return True if the configuration contains a value at the specified path, false otherwise.
     */
    boolean contains(@NotNull String path);

    /**
     * Checks if the configuration contains a section at the specified path.
     *
     * @param path The path to check.
     * @return True if the configuration contains a section at the specified path, false otherwise.
     */
    boolean isSection(@NotNull String path);

    /**
     * Gets an object from the configuration at the specified path.
     *
     * @param path The path to get the object from.
     * @return The object at the specified path, or null if the path does not exist.
     */
    Object get(@NotNull String path);

    /**
     * Checks if the configuration contains a value at the specified path and is of the specified type.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    @NotNull String getString(@NotNull String path, String def);
    default @NotNull String getString(@NotNull String path) { return getString(path, ""); }

    /**
     * Gets an integer value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    int getInt(@NotNull String path, int def);
    default int getInt(@NotNull String path) { return getInt(path, 0); }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    long getLong(@NotNull String path, long def);
    default long getLong(@NotNull String path) { return getLong(path, 0L); }

    /**
     * Gets a float value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    float getFloat(@NotNull String path, float def);
    default float getFloat(@NotNull String path) { return getFloat(path, 0); }

    /**
     * Gets a double value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    double getDouble(@NotNull String path, double def);
    default double getDouble(@NotNull String path) { return getDouble(path, 0.0); }

    /**
     * Gets a boolean value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    boolean getBoolean(@NotNull String path, boolean def);
    default boolean getBoolean(@NotNull String path) { return getBoolean(path, false); }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    @NotNull List<String> getStringList(@NotNull String path);

    /**
     * Gets a list of integers from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    @NotNull List<Integer> getIntegerList(@NotNull String path);

    /**
     * Gets a list of floats from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    @NotNull List<Float> getFloatList(@NotNull String path);

    /**
     * Gets a list of doubles from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    @NotNull List<Double> getDoubleList(@NotNull String path);

    /**
     * Gets a list of booleans from the configuration at the specified path.
     *
     * @param path The path to check.
     * @return The value at the specified path, or an empty list if the path does not exist or is of a different type.
     */
    @NotNull List<Boolean> getBooleanList(@NotNull String path);

    /**
     * Gets a list of objects of type T from the configuration at the specified path.
     *
     * @param path The path to check.
     * @param def The default value to return if the path does not exist or is of a different type.
     * @return The value at the specified path, or the default value if the path does not exist or is of a different type.
     */
    List<?> getList(@NotNull String path, List<?> def);
    default List<?> getList(@NotNull String path) { return getList(path, List.of()); }

    /**
     * Gets a configuration section at the specified path.
     *
     * @param path The path to get the section from.
     * @return The configuration section at the specified path.
     */
    ConfigSectionView getSection(@NotNull String path);

    /**
     * Gets the keys in this configuration section.
     *
     * @param deep Whether to get keys recursively.
     * @return A set of keys in this configuration section.
     */
    @NotNull Set<String> getKeys(boolean deep);
    default @NotNull Set<String> getKeys() { return getKeys(false); }

    /**
     * Gets the keys in the configuration section at the specified path.
     *
     * @param path The path to get the keys from.
     * @param deep Whether to get keys recursively.
     * @return A set of keys in the configuration section at the specified path.
     */
    @NotNull Set<String> getSectionKeys(@NotNull String path, boolean deep);

    /**
     * Gets a map representation of the configuration section at the specified path.
     *
     * @param path The path to get the map from.
     * @param deep Whether to include nested sections.
     * @return A map representation of the configuration section at the specified path.
     */
    @NotNull Map<String, Object> getMap(@NotNull String path, boolean deep);
}