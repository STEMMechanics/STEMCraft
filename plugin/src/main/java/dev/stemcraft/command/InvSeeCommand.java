package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.entity.Player;

public class InvSeeCommand extends STEMCraftCommandImpl {
    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("invsee");
        setDescription("INVSEE_DESCRIPTION");
        setUsage("INVSEE_USAGE");
        setPermission("stemcraft.command.invsee");
        addTabCompletion("{player}");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        if(ctx.args().isEmpty()) {
            cmd.error("PLAYER_REQUIRED");
            return;
        }

        Player target = ctx.getArgAsPlayer(1, null);
        if(target == null) {
            cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
            return;
        }

        // Open the *live* inventory. Any changes are applied directly.
        target.openInventory(target.getInventory());
        cmd.info(ctx.getSender(), "INVSEE_VIEWING", "player", target.getName());
    }
}