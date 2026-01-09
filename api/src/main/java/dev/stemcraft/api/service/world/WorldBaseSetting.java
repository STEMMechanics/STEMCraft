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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Interface representing a world base setting in STEMCraft.
 */
@SuppressWarnings("EmptyMethod")
public interface WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @NotNull String key();

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The world service instance.
     */
    void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service);

    /**
     * Called when the setting is disabled.
     */
    default void onDisable() { }

    /**
     * Called when a world is loaded.
     */
    default void onWorldLoad(@NotNull World world, @NotNull ConfigSection config) { }

    /**
     * Called when a world is unloaded.
     */
    default void onWorldUnload(@NotNull World world, @NotNull ConfigSection config) { }

    /**
     * Called when a world is deleted.
     */
    default void onWorldDeleted(@NotNull String worldName, @NotNull ConfigSection config) { }

    /**
     * Returns a list of tab completions for this setting.
     */
    @NotNull List<String[]> tabCompletions();

    /**
     * Called when the command for this setting is executed.
     */
    void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world);

    /**
     * Return the value of this setting for the given world from the config.
     */
    default @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        return config.getString(key(), "unset").toLowerCase(Locale.ROOT);
    }

    /**
     * Set the value of this setting for the given world in the config.
     */
    void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value);
}
