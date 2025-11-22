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
package dev.stemcraft.api.commands;

import dev.stemcraft.api.services.MessengerService;
import org.bukkit.plugin.java.JavaPlugin;

public interface STEMCraftCommand extends MessengerService {

    /**
     * Get the command aliases
     */
    String getLabel();

    /**
     * Set the command aliases
     */
    STEMCraftCommand setAlias(String... aliases);

    /**
     * Set the command description
     */
    STEMCraftCommand setDescription(String description);

    /**
     * Get the command usage string
     */
    String getUsage();

    /**
     * Set the command usage string
     */
    STEMCraftCommand setUsage(String description);

    /**
     * Add a tab completion track
     */
    STEMCraftCommand addTabCompletion(String... completions);

    /**
     * Set the command executor
     */
    STEMCraftCommand setExecutor(STEMCraftCommandExecutor processor);

    /**
     * Register the command on the server
     */
    void register(JavaPlugin plugin);
}
