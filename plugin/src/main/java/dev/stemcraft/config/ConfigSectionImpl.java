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
import org.jspecify.annotations.NonNull;

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

    private boolean pathExists(String path) {
        return section.contains(path) || section.isConfigurationSection(path);
    }

    private boolean segmentExists(ConfigurationSection current, String segment) {
        return current.contains(segment) || current.isConfigurationSection(segment);
    }

    private String resolveSegment(ConfigurationSection current, String segment) {
        if (current == null || segment.isEmpty()) {
            return segment;
        }

        if (segmentExists(current, segment)) {
            return segment;
        }

        String hyphen = segment.replace('_', '-');
        if (!hyphen.equals(segment) && segmentExists(current, hyphen)) {
            return hyphen;
        }

        String snake = segment.replace('-', '_');
        if (!snake.equals(segment) && segmentExists(current, snake)) {
            return snake;
        }

        return segment;
    }

    private String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        String[] parts = path.split("\\.");
        ConfigurationSection current = section;
        StringBuilder resolved = new StringBuilder(path.length());

        for (int i = 0; i < parts.length; i++) {
            String chosen = resolveSegment(current, parts[i]);
            if (i > 0) {
                resolved.append('.');
            }
            resolved.append(chosen);
            current = current == null ? null : current.getConfigurationSection(chosen);
        }

        return resolved.toString();
    }


    /**
     * Determines if the default value should be persisted to the configuration.
     *
     * @param path The configuration path.
     * @return true if the default value should be persisted, false otherwise.
     */
    private boolean shouldPersistDefault(String path) {
        return configFile != null && configFile.getSaveDefaults() && !pathExists(resolvePath(path));
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
    public boolean contains(@NonNull String path) {
        return pathExists(resolvePath(path));
    }

    /**
     * Checks if the configuration contains a section at the specified path.
     *
     * @param path The configuration path.
     * @return true if the configuration contains a section at the specified path, false otherwise.
     */
    public boolean isSection(@NonNull String path) {
        return section.isConfigurationSection(resolvePath(path));
    }

    /**
     * Gets an object from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return the object at the specified path, or null if not present.
     */
    public Object get(@NonNull String path) {
        return section.get(resolvePath(path));
    }

    /**
     * Checks if the configuration contains a value at the specified path and is of the specified type.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the value at the specified path, or the default value if not present.
     */
    public @NonNull String getString(@NonNull String path, @NonNull String def) {
        persistDefault(path, def);
        String value = section.getString(resolvePath(path), def);
        return value;
    }

    /**
     * Gets an integer value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the integer value at the specified path, or the default value if not present.
     */
    public int getInt(@NonNull String path, int def) {
        persistDefault(path, def);
        return section.getInt(resolvePath(path), def);
    }

    /**
     * Gets a long value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the long value at the specified path, or the default value if not present.
     */
    public long getLong(@NonNull String path, long def) {
        persistDefault(path, def);
        return section.getLong(resolvePath(path), def);
    }

    /**
     * Gets a float value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the float value at the specified path, or the default value if not present.
     */
    public float getFloat(@NonNull String path, float def) {
        persistDefault(path, def);
        return (float) section.getDouble(resolvePath(path), def);
    }

    /**
     * Gets a double value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the double value at the specified path, or the default value if not present.
     */
    public double getDouble(@NonNull String path, double def) {
        persistDefault(path, def);
        return section.getDouble(resolvePath(path), def);
    }

    /**
     * Gets a boolean value from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @param def The default value if the path does not exist.
     * @return the boolean value at the specified path, or the default value if not present.
     */
    public boolean getBoolean(@NonNull String path, boolean def) {
        persistDefault(path, def);
        return section.getBoolean(resolvePath(path), def);
    }

    /**
     * Gets a list of strings from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of strings from the configuration.
     */
    public @NonNull List<String> getStringList(@NonNull String path) {
        List<String> value = section.getStringList(resolvePath(path));
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of integers from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of integers from the configuration.
     */
    public @NonNull List<Integer> getIntegerList(@NonNull String path) {
        List<Integer> value = section.getIntegerList(resolvePath(path));
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of floats from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of floats from the configuration.
     */
    public @NonNull List<Float> getFloatList(@NonNull String path) {
        List<Float> value = section.getFloatList(resolvePath(path));
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of doubles from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of doubles from the configuration.
     */
    public @NonNull List<Double> getDoubleList(@NonNull String path) {
        List<Double> value = section.getDoubleList(resolvePath(path));
        persistDefault(path, value);
        return value;
    }

    /**
     * Gets a list of booleans from the configuration at the specified path.
     *
     * @param path The configuration path.
     * @return A list of booleans from the configuration.
     */
    public @NonNull List<Boolean> getBooleanList(@NonNull String path) {
        List<Boolean> value = section.getBooleanList(resolvePath(path));
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
    public List<?> getList(@NonNull String path, List<?> def) {
        List<?> value = section.getList(resolvePath(path), def);
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
        section.set(resolvePath(path), value);
        configFile.setDirty();
    }

    /**
     * Removes a value from the configuration at the specified path.
     *
     * @param path The configuration path to remove.
     */
    public void remove(String path) {
        section.set(resolvePath(path), null);
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
        String resolvedPath = resolvePath(path);
        ConfigurationSection subSection = section.getConfigurationSection(resolvedPath);
        if (subSection == null) {
            if (!createIfAbsent) {
                return null;
            }

            subSection = section.createSection(resolvedPath);
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
        String resolvedPath = resolvePath(path);
        if (section.isConfigurationSection(resolvedPath)) {
            if (!overwriteIfExists) {
                return new ConfigSectionImpl(configFile, section.getConfigurationSection(resolvedPath));
            }

            section.set(resolvedPath, null);
        }

        return new ConfigSectionImpl(configFile, section.createSection(resolvedPath));
    }

    /**
     * Gets the keys in this configuration section.
     *
     * @param deep Whether to get keys recursively.
     * @return A set of keys in this configuration section.
     */
    public @NonNull Set<String> getKeys(boolean deep) {
        return section.getKeys(deep);
    }

    /**
     * Gets the keys in the configuration section at the specified path.
     *
     * @param path The path to get the keys from.
     * @param deep Whether to get keys recursively.
     * @return A set of keys in the configuration section at the specified path.
     */
    public @NonNull Set<String> getSectionKeys(@NonNull String path, boolean deep) {
        ConfigurationSection subSection = section.getConfigurationSection(resolvePath(path));
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
    public @NonNull Map<String, Object> getMap(@NonNull String path, boolean deep) {
        ConfigurationSection subSection = section.getConfigurationSection(resolvePath(path));
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
