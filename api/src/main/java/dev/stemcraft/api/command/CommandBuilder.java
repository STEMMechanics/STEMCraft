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

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builder interface for creating and registering commands.
 */
public interface CommandBuilder {

    /**
     * Set the command aliases.
     *
     * @param aliases The command aliases.
     * @return The command builder.
     */
    CommandBuilder aliases(String... aliases);

    /**
     * Set the command description.
     *
     * @param description The command description.
     * @return The command builder.
     */
    CommandBuilder description(String description);

    /**
     * Set the command usage string.
     *
     * @param description The command usage string.
     * @return The command builder.
     */
    CommandBuilder usage(String description);

    /**
     * Set the command permission.
     *
     * @param permission The command permission.
     * @return The command builder.
     */
    CommandBuilder permission(String permission);

    /**
     * Add a tab completion for the command.
     *
     * @param completions The tab completions.
     * @return The command builder.
     */
    CommandBuilder tabCompletion(String... completions);

    /**
     * Set the command executor.
     *
     * @param processor The command executor.
     * @return The command builder.
     */
    CommandBuilder executor(CommandExecutor processor);

    /**
     * Register the command on the server.
     *
     * @param plugin The plugin registering the command.
     */
    Command register(JavaPlugin plugin);
}
