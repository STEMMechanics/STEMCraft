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

package dev.stemcraft.api.service.world;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for managing worlds in STEMCraft.
 */
public interface WorldService {

    enum SettingCommandMode {
        FLAG,           // e.g. /world flags <world> [flag] [args]
        SUBCOMMAND      // e.g. /world [subcommand] <world> [args]
    }

    /**
     * Check if a world with the given name exists on disk.
     *
     * @param worldName The name of the world to check.
     * @return True if the world exists, false otherwise.
     */
    boolean worldExists(String worldName);

    /**
     * Check if a world with the given name is currently loaded.
     *
     * @param worldName The name of the world to check.
     * @return True if the world is loaded, false otherwise.
     */
    boolean isWorldLoaded(String worldName);

    /**
     * Load the world with the given name into memory.
     *
     * @param worldName The name of the world to load.
     * @return The loaded World object.
     */
    World loadWorld(String worldName);

    /**
     * Unload the world with the given name from memory.
     *
     * @param worldName The name of the world to unload.
     * @param save Whether to save the world before unloading.
     * @return True if the world was successfully unloaded, false otherwise.
     */
    boolean unloadWorld(String worldName, boolean save);

    /**
     * Create a new world with the given name.
     *
     * @param worldName The name of the world to create.
     * @param generatorName The name of the custom generator to use (or null for default).
     * @param generatorOptions The options for the custom generator (or null for default).
     * @return The created World object.
     */
    World createWorld(String worldName, @Nullable String generatorName, @Nullable String generatorOptions);
    default World createWorld(String worldName) { return createWorld(worldName, null, null); }
    default World createWorld(String worldName, String generatorName) { return createWorld(worldName, generatorName, null); }

    /**
     * Delete the world with the given name from disk.
     *
     * @param worldName The name of the world to delete.
     */
    void deleteWorld(String worldName) throws Exception;

    /**
     * Rename the world with the given name on disk.
     *
     * @param oldName The current name of the world.
     * @param newName The new name for the world.
     */
    void renameWorld(String oldName, String newName) throws Exception;

    /**
     * Duplicate the world with the given name on disk.
     *
     * @param sourceWorldName The name of the world to duplicate.
     * @param targetWorldName The name for the duplicated world.
     */
    void duplicateWorld(String sourceWorldName, String targetWorldName) throws Exception;

    /**
     * Get a list of all worlds currently loaded and on disk.
     *
     * @return A list of world names.
     */
    List<String> listWorlds();

    /**
     * Get the file system path to the world folder with the given name.
     *
     * @param worldName The name of the world.
     * @return The Path to the world folder.
     */
    Path getWorldFolder(String worldName);

    /**
     * Get the WorldGeneration service for managing custom chunk generators.
     *
     * @return The WorldGeneration service.
     */
    WorldGeneration generator();

    /**
     * Evict all players from the given world to the main world.
     *
     * @param world The world to evict players from.
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
     *
     * @return The default World object.
     */
    World getDefaultWorld();

    /**
     * Set the default world.
     *
     * @param world The World object to set as default.
     */
    void setDefaultWorld(World world);

    /**
     * Get the configuration section for the given world.
     *
     * @param world The world to get the config section for.
     * @return The ConfigSection for the world.
     */
    ConfigSection getConfigSection(World world);
    ConfigSection getConfigSection(String worldName);

    /**
     * Register a world base setting.
     *
     * @param setting The WorldBaseSetting to register.
     * @param mode The command mode for the setting.
     */
    void registerSettingHandler(WorldBaseSetting setting, SettingCommandMode mode);
    default void registerSettingHandler(WorldBaseSetting setting) { registerSettingHandler(setting, SettingCommandMode.FLAG); }

    /**
     * Check if a setting with the given key is registered.
     *
     * @param key The key of the setting.
     * @return True if the setting is registered, false otherwise.
     */
    boolean isSettingRegistered(String key);

    /**
     * Check if a setting exists for a specific world.
     *
     * @param world The world to check.
     * @param key The key of the setting.
     * @return True if the setting exists, false otherwise.
     */
    boolean settingExists(World world, String key);

    /**
     * Get the value of a setting for a specific world.
     *
     * @param world The world to get the setting for.
     * @param key The key of the setting.
     * @return The value of the setting, or null if not found.
     */
    @Nullable String getSetting(World world, String key);

    /**
     * Set the value of a setting for a specific world.
     *
     * @param world The world to set the setting for.
     * @param key The key of the setting.
     * @param value The key or value to set.
     * @throws IllegalArgumentException if the value is invalid.
     */
    void setSetting(World world, String key, String value);

    /**
     * Begin a world change session for the given world.
     *
     * @param world The world to change.
     * @return A WorldChangeSession for batching changes.
     */
    WorldChangeSession changes(World world);
}
