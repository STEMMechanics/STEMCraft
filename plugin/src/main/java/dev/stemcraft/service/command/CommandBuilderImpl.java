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

package dev.stemcraft.service.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CommandBuilderImpl implements CommandBuilder {
    private final STEMCraftAPI api;
    private String label;
    private String description;
    private String usage;
    private final List<String> aliases = new ArrayList<>();
    private String permission = "";
    private CommandExecutor executor;
    private final List<String[]> tabCompletions = new ArrayList<>();

    /**
     * Constructor for CommandBuilderImpl.
     */
    public CommandBuilderImpl(STEMCraftAPI api, String label) {
        this.api = api;
        this.label = label;
    }

    /**
     * Set the command label
     */
    public CommandBuilder label(String label) {
        this.label = label;
        return this;
    }

    /**
     * Set the command aliases
     */
    public CommandBuilder aliases(String... aliases) {
        Collections.addAll(this.aliases, aliases);
        return this;
    }

    /**
     * Set the command description
     */
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the command usage string
     */
    public CommandBuilder usage(String description) {
        this.usage = description;
        return this;
    }

    /**
     * Set the command permission
     */
    public CommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Add a tab completion track.
     */
    public CommandBuilder tabCompletion(String... completions) {
        this.tabCompletions.add(completions);
        return this;
    }

    /**
     * Set the command executor
     */
    public CommandBuilder executor(CommandExecutor processor) {
        this.executor = processor;
        return this;
    }

    /**
     * Register the command on the server
     */
    public Command register(JavaPlugin plugin) {
        Command command = new CommandImpl(api, label, description, usage, aliases, permission, executor, tabCompletions);
        command.register(plugin);
        return command;
    }
}
