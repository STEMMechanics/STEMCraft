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

/**
 * Represents a configuration file.
 */
public interface ConfigFile extends ConfigSection {

    /**
     * Gets the name of the configuration file.
     *
     * @return The name of the configuration file.
     */
    String getName();

    /**
     * Checks if the configuration file exists.
     *
     * @return True if the configuration file exists, false otherwise.
     */
    boolean exists();

    /**
     * Reloads the configuration file from disk.
     *
     * @return True if the file was reloaded successfully, false otherwise.
     */
    boolean reload();

    /**
     * Checks if the configuration file has unsaved changes.
     *
     * @return True if the configuration file is dirty, false otherwise.
     */
    boolean isDirty();

    /**
     * Marks the configuration file as having unsaved changes.
     */
    void setDirty();

    /**
     * Saves the configuration file.
     *
     * @param pruneEmptySections True to remove empty sections before saving, false otherwise.
     */
    void save(boolean pruneEmptySections);
    default void save() { save(true); }

    /**
     * Saves the configuration file under a new name.
     *
     * @param name The new name for the configuration file.
     * @return The new configuration file instance.
     */
    ConfigFile saveAs(String name);

    /**
     * Checks if the configuration file is set to auto-save.
     *
     * @return True if the configuration file should auto-save, false otherwise.
     */
    boolean isAutoSave();

    /**
     * Sets whether the configuration file should auto-save.
     *
     * @param autoSave True to enable auto-save, false to disable.
     */
    void setAutoSave(boolean autoSave);

    /**
     * Checks if the configuration file should save default values.
     *
     * @return True if the configuration file should save default values, false otherwise.
     */
    boolean getSaveDefaults();

    /**
     * Sets whether the configuration file should save default values.
     *
     * @param saveDefaults True to save default values, false otherwise.
     */
    void setSaveDefaults(boolean saveDefaults);
}
