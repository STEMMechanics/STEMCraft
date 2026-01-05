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

import java.util.HashMap;
import java.util.Map;

public class ConfigServiceImpl extends BaseService implements ConfigService {

    private static final long AUTO_SAVE_INTERVAL = 20 * 60 * 5; // 5 minutes

    /**
     * Map of loaded configuration files.
     */
    private final Map<String, ConfigFile> files = new HashMap<>();

    /**
     * Constructor for ConfigServiceImpl.
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
     * Retrieves a configuration file by name.
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
}
