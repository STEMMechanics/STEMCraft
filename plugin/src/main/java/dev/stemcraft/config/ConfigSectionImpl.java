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

import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import lombok.Setter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the ConfigSection interface for managing configuration sections.
 */
public class ConfigSectionImpl implements ConfigSection {
    @Setter
    private ConfigFile configFile;
    @Setter
    private ConfigurationSection section;

    /**
     * Constructs a ConfigSectionImpl with the given ConfigFile and ConfigurationSection.
     *
     * @param configFile The ConfigFile this section belongs to.
     * @param section The underlying ConfigurationSection.
     */
    public ConfigSectionImpl(ConfigFile configFile, ConfigurationSection section) {
        this.configFile = configFile;
        this.section = section;
    }

    public ConfigSectionImpl() {}


    /**
     * Determines if the default value should be persisted to the configuration.
     *
     * @param path The configuration path.
     * @return true if the default value should be persisted, false otherwise.
     */
    private boolean shouldPersistDefault(String path) {
        return configFile != null && configFile.getSaveDefaults() && !section.contains(path);
    }

    /**
     * Persists the default value to the configuration if it should be saved.
     *
     * @param path The configuration path.
     * @param value The default value to persist.
     */
    private void persistDefault(String path, Object value) {
        if (!shouldPersistDefault(path) || value == null) {
            return;
        }

        Object storedValue = value instanceof List ? new ArrayList<>((List<?>) value) : value;
        section.set(path, storedValue);
        configFile.setDirty();
    }

    /**
     * Saves the configuration section to its underlying file.
     */
    public void save() {
        if(configFile != null) {
            configFile.save();
        }
    }

    /**
     * Checks if the configuration contains a value at the specified path.
     *
     * @param path The configuration path.
     * @return true if the configuration contains a value at the specified path, false otherwise.
     */
    public boolean contains(String path) {
        return section.contains(path);
    }

    /**
     * Checks if the configuration contains a section at the specified path.
     *
     * @param path The configuration path.
     * @return true if the configuration contains a section at the specified path, false otherwise.
     */
    public boolean isSection(String path) {
        return section.isConfigurationSection(path);
    }

    /**
     * Gets an object from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return the object at the specified path, or null if not present.
     */
    public Object get(String path) {
        return section.get(path);
    }

    /**
     * Checks if the configuration contains a value at the specified path and is of the specified type.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the value at the specified path, or the default value if not present.
     */
    public String getString(String path, String def) {
        persistDefault(path, def);
        return section.getString(path, def);
    }

    /**
     * Gets an integer value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the integer value at the specified path, or the default value if not present
     */
    public int getInt(String path, int def) {
        persistDefault(path, def);
        return section.getInt(path, def);
    }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the long value at the specified path, or the default value if not present
     */
    public long getLong(String path, long def) {
        persistDefault(path, def);
        return section.getLong(path, def);
    }

    /**
     * Gets a float value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the float value at the specified path, or the default value if not present
     */
    public float getFloat(String path, float def) {
        persistDefault(path, def);
        return (float) section.getDouble(path, def);
    }

    /**
     * Gets a double value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the double value at the specified path, or the default value if not present
     */
    public double getDouble(String path, double def) {
        persistDefault(path, def);
        return section.getDouble(path, def);
    }

    /**
     * Gets a boolean value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the boolean value at the specified path, or the default value if not present
     */
    public boolean getBoolean(String path, boolean def) {
        persistDefault(path, def);
        return section.getBoolean(path, def);
    }

    /**
     * Gets a list of strings from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of strings from the configuration.
     */
    public List<String> getStringList(String path) {
        List<String> value = section.getStringList(path);
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of integers from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of integers from the configuration.
     */
    public List<Integer> getIntegerList(String path) {
        List<Integer> value = section.getIntegerList(path);
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of floats from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of floats from the configuration.
     */
    public List<Float> getFloatList(String path) {
        List<Float> value = section.getFloatList(path);
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of doubles from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of doubles from the configuration.
     */
    public List<Double> getDoubleList(String path) {
        List<Double> value = section.getDoubleList(path);
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of booleans from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of booleans from the configuration.
     */
    public List<Boolean> getBooleanList(String path) {
        List<Boolean> value = section.getBooleanList(path);
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of objects of type T from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return A list of objects from the configuration.
     */
    public List<?> getList(String path, List<?> def) {
        List<?> value = section.getList(path, def);
        persistDefault(path, value);
        return value;
    }

    /**
     * Sets a value in the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param value The value to set.
     */
    public void set(String path, Object value) {
        section.set(path, value);
        configFile.setDirty();
    }

    /**
     * Removes a value from the configuration at the specified path.
     *
     * @param path The configuration path to remove.
     */
    public void remove(String path) {
        section.set(path, null);
        configFile.setDirty();
    }

    /**
     * Gets a configuration section at the specified path.
     *
     * @param path The configuration path.
     * @param createIfAbsent Whether to create the section if it does not exist.
     * @return The configuration section at the specified path, or null if it does not exist and createIfAbsent is false.
     */
    public ConfigSection getSection(String path, boolean createIfAbsent) {
        ConfigurationSection subSection = section.getConfigurationSection(path);
        if (subSection == null) {
            if (!createIfAbsent) {
                return null;
            }

            subSection = section.createSection(path);
            if (configFile != null) {
                configFile.setDirty();
            }
        }

        return new ConfigSectionImpl(configFile, subSection);
    }

    /**
     * Creates a configuration section at the specified path.
     *
     * @param path The configuration path.
     * @param overwriteIfExists Whether to overwrite the section if it already exists.
     * @return The newly created configuration section.
     */
    public ConfigSection createSection(String path, boolean overwriteIfExists) {
        if (section.isConfigurationSection(path)) {
            if (!overwriteIfExists) {
                return new ConfigSectionImpl(configFile, section.getConfigurationSection(path));
            }

            section.set(path, null);
        }

        return new ConfigSectionImpl(configFile, section.createSection(path));
    }

    /**
     * Gets the keys in this configuration section.
     *
     * @param deep Whether to get keys recursively.
     * @return A set of keys in this configuration section.
     */
    public Set<String> getKeys(boolean deep) {
        return section.getKeys(deep);
    }

    /**
     * Gets the keys in the configuration section at the specified path.
     *
     * @param path The path to get the keys from.
     * @param deep Whether to get keys recursively.
     * @return A set of keys in the configuration section at the specified path.
     */
    public Set<String> getSectionKeys(String path, boolean deep) {
        ConfigurationSection subSection = section.getConfigurationSection(path);
        if (subSection == null) {
            return Set.of();
        }
        return subSection.getKeys(deep);
    }

    /**
     * Gets a map representation of the configuration section at the specified path.
     *
     * @param path The path to get the map from.
     * @param deep Whether to get values recursively.
     * @return A map representation of the configuration section at the specified path.
     */
    public Map<String, Object> getMap(String path, boolean deep) {
        ConfigurationSection subSection = section.getConfigurationSection(path);
        if (subSection == null) {
            return Map.of();
        }
        return subSection.getValues(deep);
    }

    /**
     * Removes all keys and values from this configuration section.
     */
    public void removeAll() {
        for (String key : section.getKeys(false)) {
            section.set(key, null);
        }

        if (configFile != null) {
            configFile.setDirty();
        }
    }
}