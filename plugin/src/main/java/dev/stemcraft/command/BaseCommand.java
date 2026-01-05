package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.service.command.CommandBuilderImpl;

public class BaseCommand extends HasMessagesImpl {
    protected STEMCraft plugin;
    protected STEMCraftAPI api;
    private CommandBuilderImpl builder;

    /**
     * Constructor for BaseCommand.
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
     */
    public void onExecute(Command cmd, CommandContext ctx) { }

    public void setLabel(String label) {
        builder.label(label);
    }

    public void setDescription(String description) {
        builder.description(description);
    }

    public void setUsage(String usage) {
        builder.usage(usage);
    }

    public void setPermission(String permission) {
        builder.permission(permission);
    }

    public void addAliases(String... aliases) {
        builder.aliases(aliases);
    }

    public void addTabCompletion(String... completions) {
        builder.tabCompletion(completions);
    }

    public void register(STEMCraft plugin) {
        builder.executor((api, cmd, ctx) -> onExecute(cmd, ctx)).register(plugin);
    }
}
