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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;

/**
 * Interface representing a world base setting in STEMCraft.
 */
public interface WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    String key();

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The world service instance.
     */
    void onEnable(STEMCraftAPI api, WorldService service);

    /**
     * Called when the setting is disabled.
     */
    default void onDisable() {}

    /**
     * Called when a world is loaded.
     */
    default void onWorldLoad(World world, ConfigSection config) {}

    /**
     * Called when a world is unloaded.
     */
    default void onWorldUnload(World world, ConfigSection config) {}

    /**
     * Called when a world is deleted.
     */
    default void onWorldDeleted(String worldName, ConfigSection config) {}

    /**
     * Returns a list of tab completions for this setting.
     */
    List<String[]> tabCompletions();

    /**
     * Called when the command for this setting is executed.
     */
    void onCommand(CommandContext ctx, ConfigSection config, World world);

    /**
     * Return the value of this setting for the given world from the config.
     */
    default String get(World world, ConfigSection config) {
        return config.getString(key(), "unset").toLowerCase(Locale.ROOT);
    }

    /**
     * Set the value of this setting for the given world in the config.
     */
    void set(World world, ConfigSection config, String value);
}
