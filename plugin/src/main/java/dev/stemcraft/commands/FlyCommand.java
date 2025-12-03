package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class FlyCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("fly");
        setDescription("FLY_DESCRIPTION");
        setUsage("/fly [player]");
        setPermission("stemcraft.command.fly");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        // check if console called without args
        if(ctx.isConsole() && ctx.args().isEmpty()) {
            error("CONSOLE_PLAYER_REQUIRED");
            return;
        }

        // check permission for others (if args given)
        if(!ctx.args().isEmpty() && !ctx.hasPermission(cmd.getPermission() + ".others")) {
            error(ctx.getSender(), "COMMAND_NO_PERMISSION_OTHERS");
            return;
        }

        // get target player
        Player target = ctx.getArgAsPlayer(1, ctx.getSender());
        if(target == null) {
            error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(1));
            return;
        }

        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            cmd.error(ctx.getSender(), "FLY_CANNOT_BE_TOGGLED");
            return;
        }

        boolean newState = !target.getAllowFlight();
        target.setAllowFlight(newState);

        if (target.equals(ctx.getSender())) {
            success(ctx.getSender(), "FLY_SUCCESS", "status", newState ? "enabled" : "disabled");
        } else {
            String senderName = ctx.isConsole() ? api.locale().get("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "FLY_OTHER_SUCCESS_SENDER", "status", newState ? "enabled" : "disabled", "player", target.getName());
            success(target, "FLY_OTHER_SUCCESS_PLAYER", "status", newState ? "enabled" : "disabled", "player", senderName);
        }
    }
}
