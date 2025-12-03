package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class RepairCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("repair");
        setDescription("REPAIR_DESCRIPTION");
        setUsage("/repair [player]");
        setPermission("stemcraft.command.repair");
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

        ItemStack item = target.getInventory().getItemInMainHand();
        if (!item.getType().isAir() && item.getItemMeta() instanceof Damageable meta) {
            meta.setDamage(0);
            item.setItemMeta(meta);
            cmd.info(target, "REPAIR_ITEM_REPAIRED");
            if(!ctx.args().isEmpty()) {
                cmd.info(ctx.getSender(), "REPAIR_ITEM_REPAIRED_OTHER", "player", target.getName());
            }
        } else {
            if(ctx.args().isEmpty()) {
                cmd.error("REPAIR_NO_ITEM");
            } else {
                cmd.error("REPAIR_NO_ITEM_OTHER", "player", target.getName());
            }
        }
    }
}
