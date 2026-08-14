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

/**
 * Implementation of the CommandBuilder interface.
 */
public class CommandBuilderImpl implements CommandBuilder {
    private final STEMCraftAPI api;
    private String label;
    private String description;
    private String usage;
    private final List<String> aliases = new ArrayList<>();
    private String permission = "";
    private CommandExecutor executor;
    private final List<String[]> tabCompletions = new ArrayList<>();
    private final Set<Integer> ignoredArgs = new HashSet<>();

    /**
     * Constructor for CommandBuilderImpl.
     *
     * @param api The STEMCraft API instance.
     * @param label The command label.
     */
    public CommandBuilderImpl(STEMCraftAPI api, String label) {
        this.api = api;
        this.label = label;
    }

    /**
     * Set the command label.
     *
     * @param label The command label.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder label(String label) {
        this.label = label;
        return this;
    }

    /**
     * Set the command aliases.
     *
     * @param aliases The command aliases.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder aliases(String... aliases) {
        Collections.addAll(this.aliases, aliases);
        return this;
    }

    /**
     * Set the command description.
     *
     * @param description The command description.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the command usage string.
     *
     * @param description The command usage string.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder usage(String description) {
        this.usage = description;
        return this;
    }

    /**
     * Set the command permission.
     *
     * @param permission The command permission.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }

    @Override
    public CommandBuilder ignoreArg(int... positions) {
        for (int position : positions) {
            if (position < 0) throw new IllegalArgumentException("Argument positions cannot be negative");
            ignoredArgs.add(position);
        }
        return this;
    }

    /**
     * Add a tab completion track.
     *
     * @param completions The tab completions for a specific argument index.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder tabCompletion(String... completions) {
        this.tabCompletions.add(completions);
        return this;
    }

    /**
     * Set the command executor.
     *
     * @param processor The command executor.
     * @return The CommandBuilder instance.
     */
    public CommandBuilder executor(CommandExecutor processor) {
        this.executor = processor;
        return this;
    }

    /**
     * Register the command on the server.
     *
     * @param plugin The JavaPlugin instance.
     * @return The registered Command instance.
     */
    public Command register(JavaPlugin plugin) {
        Command command = new CommandImpl(api, label, description, usage, aliases, permission, executor,
            tabCompletions, ignoredArgs);
        command.register(plugin);
        return command;
    }
}
