package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.entity.Player;

public class InvSeeCommand extends STEMCraftCommandImpl {
    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("invsee");
        setDescription("INVSEE_DESCRIPTION");
        setUsage("/invsee <player>");
        setPermission("stemcraft.command.invsee");
        addTabCompletion("{player}");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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