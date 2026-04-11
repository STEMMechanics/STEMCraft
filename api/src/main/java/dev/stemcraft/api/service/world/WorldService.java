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
import org.jetbrains.annotations.NotNull;

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
    boolean worldExists(@NotNull String worldName);

    /**
     * Check if a world with the given name is currently loaded.
     *
     * @param worldName The name of the world to check.
     * @return True if the world is loaded, false otherwise.
     */
    boolean isWorldLoaded(@NotNull String worldName);

    /**
     * Load the world with the given name into memory.
     *
     * @param worldName The name of the world to load.
     * @return The loaded World object.
     */
    @Nullable World loadWorld(@NotNull String worldName);

    /**
     * Unload the world with the given name from memory.
     *
     * @param worldName The name of the world to unload.
     * @param save Whether to save the world before unloading.
     * @return True if the world was successfully unloaded, false otherwise.
     */
    boolean unloadWorld(@NotNull String worldName, boolean save);

    /**
     * Create a new world with the given name.
     *
     * @param worldName The name of the world to create.
     * @param generatorName The name of the custom generator to use (or null for default).
     * @param generatorOptions The options for the custom generator (or null for default).
     * @return The created World object.
     */
    @Nullable World createWorld(@NotNull String worldName, @NotNull String generatorName, @NotNull String generatorOptions);
    default @Nullable World createWorld(@NotNull String worldName, @NotNull String generatorName, @NotNull String generatorOptions, @Nullable Long seed) {
        return createWorld(worldName, generatorName, generatorOptions);
    }
    default @Nullable World createWorld(@NotNull String worldName) { return createWorld(worldName, "", ""); }
    default @Nullable World createWorld(@NotNull String worldName, @NotNull String generatorName) { return createWorld(worldName, generatorName, ""); }
    default @Nullable World createWorld(@NotNull String worldName, @Nullable Long seed) {
        return createWorld(worldName, "", "", seed);
    }
    default @Nullable World createWorld(@NotNull String worldName, @NotNull String generatorName, @Nullable Long seed) {
        return createWorld(worldName, generatorName, "", seed);
    }

    /**
     * Delete the world with the given name from disk.
     *
     * @param worldName The name of the world to delete.
     */
    void deleteWorld(@NotNull String worldName) throws Exception;

    /**
     * Rename the world with the given name on disk.
     *
     * @param oldName The current name of the world.
     * @param newName The new name for the world.
     */
    void renameWorld(@NotNull String oldName, @NotNull String newName) throws Exception;

    /**
     * Duplicate the world with the given name on disk.
     *
     * @param sourceWorldName The name of the world to duplicate.
     * @param targetWorldName The name for the duplicated world.
     */
    void duplicateWorld(@NotNull String sourceWorldName, @NotNull String targetWorldName) throws Exception;

    /**
     * Get a list of all worlds currently loaded and on disk.
     *
     * @return A list of world names.
     */
    @NotNull List<String> listWorlds();

    /**
     * Get the file system path to the world folder with the given name.
     *
     * @param worldName The name of the world.
     * @return The Path to the world folder.
     */
    @NotNull Path getWorldFolder(@NotNull String worldName);

    /**
     * Get the WorldGeneration service for managing custom chunk generators.
     *
     * @return The WorldGeneration service.
     */
    @NotNull WorldGeneration generator();

    /**
     * Evict all players from the given world to the main world.
     *
     * @param world The world to evict players from.
     */
    void evictAllPlayers(@NotNull World world);
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
    @NotNull World getDefaultWorld();

    /**
     * Set the default world.
     *
     * @param world The World object to set as default.
     */
    void setDefaultWorld(@NotNull World world);

    /**
     * Get the configuration section for the given world.
     *
     * @param world The world to get the config section for.
     * @return The ConfigSection for the world.
     */
    @NotNull ConfigSection getConfigSection(@NotNull World world);
    @NotNull ConfigSection getConfigSection(@NotNull String worldName);

    /**
     * Register a world base setting.
     *
     * @param setting The WorldBaseSetting to register.
     * @param mode The command mode for the setting.
     */
    void registerSettingHandler(@NotNull WorldBaseSetting setting, @NotNull SettingCommandMode mode);
    default void registerSettingHandler(@NotNull WorldBaseSetting setting) { registerSettingHandler(setting, SettingCommandMode.FLAG); }

    /**
     * Check if a setting with the given key is registered.
     *
     * @param key The key of the setting.
     * @return True if the setting is registered, false otherwise.
     */
    boolean isSettingRegistered(@NotNull String key);

    /**
     * Check if a setting exists for a specific world.
     *
     * @param world The world to check.
     * @param key The key of the setting.
     * @return True if the setting exists, false otherwise.
     */
    boolean settingExists(@NotNull World world, @NotNull String key);

    /**
     * Get the value of a setting for a specific world.
     *
     * @param world The world to get the setting for.
     * @param key The key of the setting.
     * @return The value of the setting, or null if not found.
     */
    @Nullable String getSetting(@NotNull World world, @NotNull String key);

    /**
     * Set the value of a setting for a specific world.
     *
     * @param world The world to set the setting for.
     * @param key The key of the setting.
     * @param value The key or value to set.
     * @throws IllegalArgumentException if the value is invalid.
     */
    void setSetting(@NotNull World world, @NotNull String key, @NotNull String value);

    /**
     * Begin a world change session for the given world.
     *
     * @param world The world to change.
     * @return A WorldChangeSession for batching changes.
     */
    @NotNull WorldChangeSession changes(@NotNull World world);
}
