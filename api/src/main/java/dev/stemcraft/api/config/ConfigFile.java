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

import dev.stemcraft.api.service.config.ConfigChangeEvent;
import dev.stemcraft.api.service.config.ConfigSaveResult;
import dev.stemcraft.api.service.config.ConfigSubscription;

import java.util.function.Consumer;

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

    /** Performs a conflict-aware save and returns its structured result. */
    default ConfigSaveResult saveChecked(boolean pruneEmptySections) {
        return ConfigSaveResult.failure("", "This configuration implementation does not support checked saves.");
    }

    default ConfigSaveResult saveChecked() { return saveChecked(true); }

    /**
     * Replaces this file from YAML supplied by an editor, preserving the
     * server-side conflict checks and path-level merge rules.
     */
    default ConfigSaveResult saveYaml(String yaml, String expectedRevision) {
        return ConfigSaveResult.failure(revision(), "This configuration implementation does not support YAML editor saves.");
    }

    /** Returns the revision of the last successfully loaded or saved disk contents. */
    default String revision() { return ""; }

    /** Registers a listener for effective changes to this file. */
    default ConfigSubscription onChange(Consumer<ConfigChangeEvent> listener) {
        return ConfigSubscription.noop();
    }

    /** Registers a listener for changes affecting a path in this file. */
    default ConfigSubscription onChange(String path, Consumer<ConfigChangeEvent> listener) {
        return onChange(event -> {
            if (event.affects(path)) listener.accept(event);
        });
    }

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
