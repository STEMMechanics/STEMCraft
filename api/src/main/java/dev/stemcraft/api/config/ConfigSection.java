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

import org.jspecify.annotations.NonNull;

/**
 * Represents a section of a configuration file.
 */
public interface ConfigSection extends ConfigSectionView {

    /**
     * Saves the configuration section to its underlying file.
     */
    void save();

    /**
     * Gets a configuration section at the specified path.
     *
     * @param path The path to get the section from.
     * @param createIfAbsent Whether to create the section if it does not exist.
     * @return The configuration section at the specified path.
     */
    ConfigSection getSection(String path, boolean createIfAbsent);
    default ConfigSection getSection(@NonNull String path) { return getSection(path, true); }

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
     * Creates a configuration section at the specified path.
     *
     * @param path The path to create the section at.
     * @param overwriteIfExists Whether to overwrite the section if it already exists.
     * @return The newly created configuration section.
     */
    ConfigSection createSection(String path, boolean overwriteIfExists);
    default ConfigSection createSection(String path) { return createSection(path, false); }

    /**
     * Removes all keys and values from this configuration section.
     */
    void removeAll();
}