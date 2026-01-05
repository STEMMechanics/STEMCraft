package dev.stemcraft.service.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.service.BaseService;

public class CommandServiceImpl extends BaseService implements CommandService {

    /**
     * Constructor for CommandServiceImpl.
     */
    public CommandServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Create a new command builder with the given label.
     */
    public CommandBuilder create(String label) {
        return new CommandBuilderImpl(api, label);
    }
}
