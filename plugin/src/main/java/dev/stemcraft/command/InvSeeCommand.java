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