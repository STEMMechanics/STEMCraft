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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BreakCommand extends STEMCraftCommandImpl {

    private static final int MAX_RADIUS = 10;
    private static final int MAX_DISTANCE = 120;

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("break");
        setDescription("BREAK_DESCRIPTION");
        setUsage("BREAK_USAGE");
        addTabCompletion("{int}");
        setPermission("stemcraft.command.break");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        ctx.checkNotConsole();

        Player player = ctx.getSenderAsPlayer();

        int radius = 1;
        if (!ctx.args().isEmpty()) {
            ctx.checkArgIsInt(1, "BREAK_RADIUS_INVALID", "radius");
            radius = ctx.getArgAsInt(1, 1, 1, MAX_RADIUS);
        }

        Block target = player.getTargetBlockExact(MAX_DISTANCE);
        if (target == null) {
            ctx.returnError("BREAK_NO_TARGET");
        }

        int broken = 0;

        // Treat 1 as "just the target", 2 as "small cluster", etc
        int r = radius - 1;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {

                    // Manhattan distance within r
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > r) continue;

                    Block b = target.getRelative(x, y, z);
                    if (b.getType() == Material.AIR) continue;

                    b.setType(Material.AIR, false); // no drops, no physics spam
                    broken++;
                }
            }
        }

        ctx.returnInfo("BREAK_SUCCESS",
                "count", broken,
                "radius", radius);
    }
}