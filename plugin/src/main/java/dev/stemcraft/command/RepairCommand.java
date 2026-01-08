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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Command to repair the item in a player's main hand.
 */
public class RepairCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.repair";

    /**
     * Creates a new RepairCommand instance.
     *
     * @param plugin The main STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public RepairCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the command with its label, description, usage, and permission.
     */
    @Override
    public void onLoad() {
        setLabel("repair");
        setDescription("REPAIR_DESCRIPTION");
        setUsage("REPAIR_USAGE");
        setPermission(PERMISSION);
        register(plugin);
    }

    /**
     * Executes the repair command, repairing the item in the target player's main hand.
     *
     * @param cmd The command being executed.
     * @param ctx The context of the command execution.
     */
    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
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