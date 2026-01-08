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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.config.ConfigFileImpl;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the ConfigService for managing configuration files.
 */
public class ConfigServiceImpl extends BaseService implements ConfigService {

    private static final long AUTO_SAVE_INTERVAL = 20 * 60 * 5; // 5 minutes

    /**
     * Map of loaded configuration files.
     */
    private final Map<String, ConfigFile> files = new HashMap<>();

    /**
     * Constructor for ConfigServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public ConfigServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the configuration service.
     */
    @Override
    public void onEnable() {
        api.tasks().repeating(AUTO_SAVE_INTERVAL, () -> {
            for (ConfigFile file : files.values()) {
                if (file.isAutoSave() && file.isDirty()) {
                    file.save();
                }
            }
        });
    }

    /**
     * Retrieves a configuration file by name. If no extension is provided, ".yml" is assumed.
     *
     * @param name The name of the configuration file.
     * @param createIfNotExist Whether to create the file if it does not exist.
     * @return The configuration file object, or null if loading failed.
     */
    public ConfigFile load(String name, boolean createIfNotExist) {
        if(files.containsKey(name)) {
            return files.get(name);
        }

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(name, createIfNotExist)) {
            files.put(name, configFile);
            return configFile;
        }

        return null;
    }

    public ConfigFile load(File parent, String name, boolean createIfNotExist) {
        if(!parent.exists() || !parent.isDirectory()) {
            throw new IllegalArgumentException("Parent must be an existing directory");
        }

        String fullName = parent.getPath() + "/" + name;
        if(files.containsKey(fullName)) {
            return files.get(fullName);
        }

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(parent, name, createIfNotExist)) {
            files.put(fullName, configFile);
            return configFile;
        }

        return null;
    }

    public ConfigFile load(File file, boolean createIfNotExist) {
        if(file.exists() && file.isDirectory()) {
            throw new IllegalArgumentException("File must not be a directory");
        }

        String fullName = file.getPath();
        if(files.containsKey(fullName)) {
            return files.get(fullName);
        }

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(file, createIfNotExist)) {
            files.put(fullName, configFile);
            return configFile;
        }

        return null;
    }
}
