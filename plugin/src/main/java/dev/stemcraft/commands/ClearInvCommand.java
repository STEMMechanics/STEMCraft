package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.entity.Player;

public class ClearInvCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("clearinv");
        setDescription("CLEAR_INV_DESCRIPTION");
        setUsage("/clearinv [player]");
        setPermission("stemcraft.command.clearinv");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        // check if console called without args
        if(ctx.fromConsole() && ctx.args().isEmpty()) {
            error("CONSOLE_PLAYER_REQUIRED");
            return;
        }

        // check permission for others (if args given)
        if(!ctx.args().isEmpty() && !ctx.hasPermission("stemcraft.command.clearinv.others")) {
            error(ctx.getSender(), "COMMAND_NO_PERMISSION_OTHERS");
            return;
        }

        // get target player
        Player target = ctx.getArgAsPlayer(1, ctx.getSender());
        if(target == null) {
            error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(1));
            return;
        }

        target.getInventory().clear();
        target.getInventory().setArmorContents(null);

        if (target.equals(ctx.getSender())) {
            success(ctx.getSender(), "CLEAR_INV_SUCCESS");
        } else {
            String senderName = ctx.fromConsole() ? api.locale().get("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "CLEAR_INV_OTHER_SUCCESS_SENDER", "player", target.getName());
            success(target, "CLEAR_INV_OTHER_SUCCESS_PLAYER", "player", senderName);
        }
    }
}
