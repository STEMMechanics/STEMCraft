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

package dev.stemcraft.api.service.cache;

import dev.stemcraft.api.config.ConfigFile;

import java.io.File;

/**
 * Service for managing cache files and configurations.
 */
public interface CacheService {

    /**
     * Gets the directory where cache files are stored.
     *
     * @return The cache directory.
     */
    File cacheDir();

    /**
     * Retrieves the cache configuration for the specified file name.
     *
     * @param fileName The name of the cache file.
     * @return The configuration file object.
     */
    ConfigFile cacheConfig(String fileName);
}