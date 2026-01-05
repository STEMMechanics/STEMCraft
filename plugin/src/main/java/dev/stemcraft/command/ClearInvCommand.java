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

public class ClearInvCommand extends BaseCommand {

    ClearInvCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onLoad() {
        setLabel("clearinv");
        setDescription("CLEAR_INV_DESCRIPTION");
        setUsage("CLEAR_INV_USAGE");
        setPermission("stemcraft.command.clearinv");
        register(plugin);
    }

    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        // check if console called without args
        if(ctx.isConsole() && ctx.args().isEmpty()) {
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
            String senderName = ctx.isConsole() ? api.locales().resolve("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "CLEAR_INV_OTHER_SUCCESS_SENDER", "player", target.getName());
            success(target, "CLEAR_INV_OTHER_SUCCESS_PLAYER", "player", senderName);
        }
    }
}
