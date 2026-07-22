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

/**
 * Command to open another player's ender chest.
 */
public class EnderChestCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.enderchest";

    /**
     * Constructs the EnderChestCommand.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public EnderChestCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the command with its label, description, usage, permission, and tab completions.
     */
    @Override
    public void onLoad() {
        setLabel("enderchest");
        setDescription("ENDERCHEST_DESCRIPTION");
        setUsage("ENDERCHEST_USAGE");
        setPermission(PERMISSION);
        addTabCompletion("{player}");
        register(plugin);
    }

    /**
     * Executes the command to open another player's ender chest.
     *
     * @param cmd The command being executed.
     * @param ctx The context of the command execution.
     */
    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        if(ctx.args().isEmpty()) {
            cmd.error("PLAYER_REQUIRED");
            return;
        }

        Player target = ctx.getPlayer(0, null);
        if(target == null) {
            cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
            return;
        }

        // Open the *live* inventory. Any changes are applied directly.
        target.openInventory(target.getEnderChest());
        cmd.info(ctx.getSender(), "ENDERCHEST_VIEWING", "player", target.getName());
    }
}
