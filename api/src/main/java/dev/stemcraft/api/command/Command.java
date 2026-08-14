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

package dev.stemcraft.api.command;

import dev.stemcraft.api.capability.HasMessages;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a command that can be registered on the server.
 */
public interface Command extends HasMessages {

    /**
     * Get the command label.
     *
     * @return the command label.
     */
    String getLabel();

    /**
     * Get the command usage string.
     *
     * @return the command usage string.
     */
    String getUsage();

    /**
     * Get the command permission.
     *
     * @return the command permission.
     */
    String getPermission();

    /** Returns whether this zero-based argument position must remain positional. */
    default boolean isPositionalArgument(int position) { return false; }
    
    /**
     * Register the command on the server.
     *
     * @param plugin the plugin registering the command.
     */
    void register(JavaPlugin plugin);

    /**
     * Update the permission required to run this command. Use empty string to clear.
     *
     * @param permission the new permission required to run this command.
     */
    void setPermission(@NotNull String permission);

    /**
     * Replace aliases.
     *
     * @param alias the new aliases for this command.
     */
    void setAliases(@NotNull String... alias);

    /**
     * Update the usage string.
     *
     * @param usage the new usage string.
     */
    void setUsage(@NotNull String usage);

    /**
     * Update the description.
     *
     * @param description the new description.
     */
    void setDescription(@NotNull String description);

    /**
     * Add a tab completion pattern (your existing format: String[] items).
     *
     * @param completions the tab completion patterns to add.
     */
    void addTabCompletion(@NotNull String... completions);

    /**
     * Remove a tab completion pattern (your existing format: String[] items).
     *
     * @param completions the tab completion patterns to remove.
     */
    void removeTabCompletion(@NotNull String... completions);

    /**
     * Clear all tab completion patterns.
     */
    void clearTabCompletions();

    /**
     * Unregister the command from the server.
     */
    default void unregister() { }
}
