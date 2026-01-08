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

package dev.stemcraft.api.service.config;

import dev.stemcraft.api.config.ConfigFile;

import java.io.File;

/**
 * Service for managing configuration files.
 */
public interface ConfigService {

    /**
     * Retrieves a configuration file by name.
     *
     * @param name The name of the configuration file.
     * @param createIfNotExist Whether to create the file if it does not exist.
     * @return The configuration file object.
     */
    ConfigFile load(String name, boolean createIfNotExist);
    default ConfigFile load(String name) { return load(name, true); }

    ConfigFile load(File parent, String name, boolean createIfNotExist);
    default ConfigFile load(File parent, String name) { return load(parent, name, true); }

    ConfigFile load(File file, boolean createIfNotExist);
    default ConfigFile load(File file) { return load(file, true); }
}