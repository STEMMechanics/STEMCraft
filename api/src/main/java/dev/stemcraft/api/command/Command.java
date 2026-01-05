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

import java.util.List;

public interface Command extends HasMessages {

    /**
     * Get the command aliases
     */
    String getLabel();

    /**
     * Get the command usage string
     */
    String getUsage();

    /**
     * Get the command permission
     */
    String getPermission();
    
    /**
     * Register the command on the server
     */
    void register(JavaPlugin plugin);

    /**
     * Update the permission required to run this command. Use empty string to clear.
     */
    void setPermission(@NotNull String permission);

    /**
     * Replace aliases.
     */
    void setAliases(@NotNull String... alias);

    /**
     * Update the usage string.
     */
    void setUsage(@NotNull String usage);

    /**
     * Update the description.
     */
    void setDescription(@NotNull String description);

    /**
     * Add a tab completion pattern (your existing format: String[] items).
     */
    void addTabCompletion(@NotNull String... completions);
}
