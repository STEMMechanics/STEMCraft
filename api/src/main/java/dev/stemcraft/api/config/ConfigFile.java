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

public interface ConfigFile extends ConfigSection {

    /**
     * Gets the name of the configuration file.
     */
    String getName();

    /**
     * Checks if the configuration file exists.
     */
    boolean exists();

    /**
     * Checks if the configuration file has unsaved changes.
     */
    boolean isDirty();

    /**
     * Marks the configuration file as having unsaved changes.
     */
    void setDirty();

    /**
     * Saves the configuration file.
     */
    void save();

    /**
     * Saves the configuration file under a new name.
     */
    ConfigFile saveAs(String name);

    /**
     * Checks if the configuration file is set to auto-save.
     */
    boolean isAutoSave();

    /**
     * Sets whether the configuration file should auto-save.
     */
    void setAutoSave(boolean autoSave);

    /**
     * Checks if the configuration file should save default values.
     */
    boolean getSaveDefaults();

    /**
     * Sets whether the configuration file should save default values.
     */
    void setSaveDefaults(boolean saveDefaults);
}