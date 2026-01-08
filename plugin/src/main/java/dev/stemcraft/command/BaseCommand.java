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

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.service.command.CommandBuilderImpl;

/**
 * Base class for creating commands in STEMCraft.
 */
@SuppressWarnings("unused")
public class BaseCommand extends HasMessagesImpl {
    protected final STEMCraft plugin;
    protected final STEMCraftAPI api;
    private final CommandBuilderImpl builder;

    /**
     * Constructor for BaseCommand.
     *
     * @param plugin the STEMCraft plugin instance
     * @param api the STEMCraft API instance
     */
    public BaseCommand(STEMCraft plugin, STEMCraftAPI api) {
        this.plugin = plugin;
        this.api = api;
        this.builder = new CommandBuilderImpl(api, "");
    }

    /**
     * Called when the command is loaded.
     */
    public void onLoad() { }

    /**
     * Called when the command is executed.
     *
     * @param cmd the command being executed
     * @param ctx the context of the command execution
     */
    public void onExecute(Command cmd, CommandContext ctx) { }

    /**
     * Set the label of the command.
     *
     * @param label the label of the command
     */
    public void setLabel(String label) {
        builder.label(label);
    }

    /**
     * Set the description of the command.
     *
     * @param description the description of the command
     */
    public void setDescription(String description) {
        builder.description(description);
    }

    /**
     * Set the usage message of the command.
     *
     * @param usage the usage message of the command
     */
    public void setUsage(String usage) {
        builder.usage(usage);
    }

    /**
     * Set the permission required to execute the command.
     *
     * @param permission the permission string
     */
    public void setPermission(String permission) {
        builder.permission(permission);
    }

    /**
     * Add aliases for the command.
     *
     * @param aliases the aliases to add
     */
    public void addAliases(String... aliases) {
        builder.aliases(aliases);
    }

    /**
     * Add tab completions for the command.
     *
     * @param completions the tab completions to add
     */
    public void addTabCompletion(String... completions) {
        builder.tabCompletion(completions);
    }

    public void register(STEMCraft plugin) {
        builder.executor((api, cmd, ctx) -> onExecute(cmd, ctx)).register(plugin);
    }
}
