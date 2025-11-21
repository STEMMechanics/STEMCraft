package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import dev.stemcraft.api.utils.SCPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class HubCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("hub");
        setDescription("Teleports a player to the hub world.");
        setUsage("/hub [player]");
        register(plugin);
    }

    public void onExecute(STEMCraftAPI api, String label, STEMCraftCommandContext ctx) {
        // check if console called without args
        if(ctx.isConsole() && ctx.args().isEmpty()) {
            error("HUB_CONSOLE_NO_PLAYER");
            return;
        }

        // check permission for others (if args given)
        if(!ctx.args().isEmpty() && !ctx.hasPermission("stemcraft.hub.others")) {
            error("HUB_TELEPORT_OTHER_DENY");
            return;
        }

        // get target player
        Player target = ctx.getArgAsPlayer(1, ctx.getSender());
        if(target == null) {
            error("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
            return;
        }

        // get hub world from config, else first world
        String hubWorldName = api.config().getString("hub");
        World hubWorld = null;
        if (hubWorldName != null && !hubWorldName.isEmpty()) {
            hubWorld = Bukkit.getWorld(hubWorldName);
        } else {
            hubWorld = Bukkit.getWorlds().get(0);
        }

        if (hubWorld == null) {
            error("HUB_WORLD_NOT_FOUND", "world", hubWorldName);
            return;
        }

        Location hubLocation = hubWorld.getSpawnLocation();
        SCPlayer.teleport(target, hubLocation);

        if (target.equals(ctx.getSender())) {
            success("HUB_TELEPORT_SUCCESS");
        } else {
            String senderName = ctx.isConsole() ? api.locale().get("CONSOLE_NAME") : ctx.getSender().getName();
            success("HUB_TELEPORT_OTHER_SUCCESS_SENDER", "player", target.getName());
            success(target, "HUB_TELEPORT_OTHER_SUCCESS_PLAYER", "player", senderName);
        }
    }

}
