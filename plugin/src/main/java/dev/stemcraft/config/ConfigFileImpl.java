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

package dev.stemcraft.config;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import lombok.Getter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of the ConfigFile interface for managing YAML configuration files.
 */
public class ConfigFileImpl extends ConfigSectionImpl implements ConfigFile {
    @Getter
    private String name;
    @Getter
    private boolean dirty;
    private File file;
    private YamlConfiguration config;
    @Getter
    private boolean autoSave = false;
    private boolean saveDefaults = true;
    private final Set<String> dirtyPaths = new LinkedHashSet<>();
    private boolean fullSaveRequired = false;

    /**
     * Loads the configuration file with the given name.
     *
     * @param name The name of the configuration file.
     * @param createIfNotExist Whether to create the file if it does not exist.
     * @return true if the file was loaded successfully, false otherwise.
     */
    public boolean load(String name, boolean createIfNotExist) {
        return load(STEMCraftAPI.api().getDataFolder(), name, createIfNotExist);
    }

    public boolean load(File file, boolean createIfNotExist) {
        return load(file.getParentFile(), file.getName(), createIfNotExist);
    }

    public boolean load(File parent, String name, boolean createIfNotExist) {
        if (!name.contains(".")) {
            name += ".yml";
        }

        this.name = name;
        clearDirtyState();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            this.file = null;
            return false;
        }

        File targetFile = new File(parent, name);

        if (!targetFile.exists()) {
            if (!createIfNotExist) {
                this.file = null;
                return false;
            }

            try {
                if (!targetFile.createNewFile()) {
                    this.file = null;
                    return false;
                }
            } catch (IOException e) {
                this.file = null;
                return false;
            }
        }

        this.file = targetFile;
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(targetFile);
        } catch (IOException | InvalidConfigurationException exception) {
            this.file = null;
            this.config = null;
            return false;
        }

        this.config = loaded;
        setConfigFile(this);
        setSection(this.config);
        return true;
    }

    /**
     * Checks if the configuration file exists.
     *
     * @return true if the file exists, false otherwise.
     */
    public boolean exists() {
        return file != null && file.exists();
    }

    @Override
    public boolean reload() {
        if (file == null) {
            return false;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            return false;
        }

        this.config = loaded;
        clearDirtyState();
        setConfigFile(this);
        setSection(this.config);
        return true;
    }

    /**
     * Marks the configuration file as dirty (modified).
     */
    public void setDirty() {
        this.dirty = true;
        this.fullSaveRequired = true;
    }

    void setDirty(String path) {
        this.dirty = true;
        if (path == null) {
            this.fullSaveRequired = true;
            return;
        }

        this.dirtyPaths.add(path);
    }

    /**
     * Saves the configuration file.
     *
     * @param pruneEmptySections Whether to remove empty sections before saving.
     */
    public void save(boolean pruneEmptySections) {
        if (file == null || !dirty) {
            return;
        }

        try {
            YamlConfiguration saveTarget = buildSaveTarget();
            if (pruneEmptySections) {
                pruneEmptySections(saveTarget);
            }

            saveTarget.save(file);
            syncCurrentConfig(saveTarget);
            clearDirtyState();
        } catch (IOException e) {
            if (STEMCraftAPI.api() != null && STEMCraftAPI.api().messages() != null) {
                STEMCraftAPI.api().messages().error("Could not save config file: " + name, e);
            }
        } catch (InvalidConfigurationException e) {
            if (STEMCraftAPI.api() != null && STEMCraftAPI.api().messages() != null) {
                STEMCraftAPI.api().messages().error("Could not merge config file before save: " + name, e);
            }
        }
    }

    @Override
    public void save() {
        save(true);
    }

    /**
     * Saves the configuration file under a new name.
     *
     * @param name The new name for the configuration file.
     * @return The current ConfigFile instance.
     */
    public ConfigFile saveAs(String name) {
        if (file == null) return this;

        File newFile = new File(STEMCraftAPI.api().getDataFolder(), name);
        try {
            config.save(newFile);
            return this;
        } catch (IOException e) {
            STEMCraftAPI.api().messages().error("Could not save config file as: " + name, e);
            return this;
        }
    }

    /**
     * Sets whether the configuration file should auto-save.
     *
     * @param autoSave True to enable auto-save, false to disable.
     */
    @Override
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    /**
     * Gets whether the configuration file should save default values.
     *
     * @return true if default values should be saved, false otherwise.
     */
    @Override
    public boolean getSaveDefaults() {
        return saveDefaults;
    }

    /**
     * Sets whether the configuration file should save default values.
     *
     * @param saveDefaults True to save default values, false otherwise.
     */
    @Override
    public void setSaveDefaults(boolean saveDefaults) {
        this.saveDefaults = saveDefaults;
    }

    /**
     * Recursively prunes empty sections from the given configuration section.
     *
     * @param section The configuration section to prune.
     */
    private static void pruneEmptySections(ConfigurationSection section) {
        Set<String> keys = new LinkedHashSet<>(section.getKeys(false));
        for (String key : keys) {
            if (!section.isConfigurationSection(key)) {
                continue;
            }

            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }

            pruneEmptySections(child);

            // After pruning children, remove this section if it's now empty
            if (child.getKeys(false).isEmpty() && child.getValues(false).isEmpty()) {
                section.set(key, null);
            }
        }
    }

    private void clearDirtyState() {
        this.dirty = false;
        this.fullSaveRequired = false;
        this.dirtyPaths.clear();
    }

    private YamlConfiguration buildSaveTarget() throws IOException, InvalidConfigurationException {
        if (fullSaveRequired || dirtyPaths.isEmpty()) {
            return config;
        }

        YamlConfiguration merged = new YamlConfiguration();
        if (file.exists()) {
            merged.load(file);
        }

        for (String path : dirtyPaths) {
            applyDirtyPath(config, merged, path);
        }

        return merged;
    }

    private void syncCurrentConfig(YamlConfiguration saveTarget) {
        if (saveTarget == config) {
            return;
        }

        replaceSectionContents(config, saveTarget);
        setConfigFile(this);
        setSection(config);
    }

    private static void applyDirtyPath(ConfigurationSection source, YamlConfiguration target, String path) {
        if (path == null || path.isEmpty()) {
            replaceSectionContents(target, source);
            return;
        }

        ConfigurationSection sourceSection = source.getConfigurationSection(path);
        if (sourceSection != null) {
            target.set(path, null);
            ConfigurationSection targetSection = target.createSection(path);
            replaceSectionContents(targetSection, sourceSection);
            return;
        }

        if (!source.contains(path) && !source.isConfigurationSection(path)) {
            target.set(path, null);
            return;
        }

        target.set(path, cloneValue(source.get(path)));
    }

    private static void replaceSectionContents(ConfigurationSection target, ConfigurationSection source) {
        for (String key : new LinkedHashSet<>(target.getKeys(false))) {
            if (!source.contains(key) && !source.isConfigurationSection(key)) {
                target.set(key, null);
            }
        }

        for (String key : source.getKeys(false)) {
            ConfigurationSection sourceSection = source.getConfigurationSection(key);
            if (sourceSection != null) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    target.set(key, null);
                    targetSection = target.createSection(key);
                }

                replaceSectionContents(targetSection, sourceSection);
                continue;
            }

            target.set(key, cloneValue(source.get(key)));
        }
    }

    private static Object cloneValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(cloneValue(item));
            }
            return copy;
        }

        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), cloneValue(entry.getValue()));
            }
            return copy;
        }

        return value;
    }
}
