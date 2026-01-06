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
     * Set the command aliases
     *
     * @param aliases The command aliases
     * @return The command builder
     */
    CommandBuilder aliases(String... aliases);

    /**
     * Set the command description
     *
     * @param description The command description
     * @return The command builder
     */
    CommandBuilder description(String description);

    /**
     * Set the command usage string
     *
     * @param description The command usage string
     * @return The command builder
     */
    CommandBuilder usage(String description);

    /**
     * Set the command permission
     *
     * @param permission The command permission
     * @return The command builder
     */
    CommandBuilder permission(String permission);

    /**
     * Add a tab completion track.
     *
     * Each entry in the completions list represents one argument position in the command.
     *
     * completions can be:
     * - A static string
     *   Example: "player", "world", "duration"

     * - A registered callback key wrapped in {}
     *   Example: "{player}", "{world}", "{duration}"
     *   These are resolved via the TabCompletion service at runtime.

     * - A flag option (starts with "-")
     *   Example: "-force", "-silent"
     *   These appear when the user types "-" and are removed once used.
     *   STEMCraftCommandContext#hasFlag can be used to check if a flag was provided.
     *   Defining a flag in a track only requires the preceding positional arguments
     *   in that track to match. It does not depend on other flags or key-value options
     *   being present.

     * - A key-value option using the format key:value
     *   Example: "course:{courses}", "mode:{modes}"
     *   When the user types "course:", valid values will be suggested.
     *   STEMCraftCommandContext#getOption can be used to retrieve the value.
     *   Like flags, key-value options in a track are available once the preceding
     *   positional arguments match, regardless of whether other flags or options
     *   were provided.

     * Notes:
     * - Callback placeholders may also include extra arguments using ":" separators
     *   Example: "{world:nether}"

     * Each call to addTabCompletion(...) represents one valid argument pattern.
     */
    /**
     * Add a tab completion for the command
     *
     * @param completions The tab completions
     * @return The command builder
     */
    CommandBuilder tabCompletion(String... completions);

    /**
     * Set the command executor
     *
     * @param processor The command executor
     * @return The command builder
     */
    CommandBuilder executor(CommandExecutor processor);

    /**
     * Register the command on the server
     *
     * @param plugin The plugin registering the command
     */
    Command register(JavaPlugin plugin);
}
