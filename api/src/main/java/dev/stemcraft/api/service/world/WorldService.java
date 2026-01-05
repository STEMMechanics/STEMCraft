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
package dev.stemcraft.api.service.world;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

import java.nio.file.Path;
import java.util.List;

public interface WorldService {

    enum SettingCommandMode { FLAG, SUBCOMMAND }

    /**
     * Check if a world with the given name exists on disk.
     */
    boolean worldExists(String worldName);

    /**
     * Check if a world with the given name is currently loaded.
     */
    boolean isWorldLoaded(String worldName);

    /**
     * Load the world with the given name into memory.
     */
    World loadWorld(String worldName);

    /**
     * Unload the world with the given name from memory.
     */
    boolean unloadWorld(String worldName, boolean save);

    /**
     * Create a new world with the given name.
     */
    World createWorld(String worldName, String generatorName, String generatorOptions);
    default World createWorld(String worldName) { return createWorld(worldName, null, null); }
    default World createWorld(String worldName, String generatorName) { return createWorld(worldName, generatorName, null); }

    /**
     * Delete the world with the given name from disk.
     */
    void deleteWorld(String worldName) throws Exception;

    /**
     * Rename the world with the given name on disk.
     */
    void renameWorld(String oldName, String newName) throws Exception;

    /**
     * Duplicate the world with the given name on disk.
     */
    void duplicateWorld(String sourceWorldName, String targetWorldName) throws Exception;

    /**
     * Get a list of all worlds currently loaded and on disk.
     */
    List<String> listWorlds();

    /**
     * Get the file system path to the world folder with the given name.
     */
    Path getWorldFolder(String worldName);

    /**
     * Register a custom chunk generator factory with the given name.
     */
    void registerGenerator(String name, ChunkGeneratorFactory factory);

    /**
     * Evict all players from the given world to the main world.
     */
    void evictAllPlayers(World world);
    default void evictAllPlayers(String worldName) {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if(world != null) {
            evictAllPlayers(world);
        }
    }

    /**
     * Get the default world.
     */
    World getDefaultWorld();

    /**
     * Set the default world.
     */
    void setDefaultWorld(World world);

    /**
     * Get the configuration section for the given world.
     */
    ConfigSection getConfigSection(World world);
    ConfigSection getConfigSection(String worldName);

    void registerSetting(WorldBaseSetting setting, SettingCommandMode mode);
    default void registerSetting(WorldBaseSetting setting) {
        registerSetting(setting, SettingCommandMode.FLAG);
    }

    WorldChangeSession changes(World world);
}