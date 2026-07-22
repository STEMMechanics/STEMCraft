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
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Command to toggle flight mode for players.
 */
public class FlyCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.fly";

    /**
     * Constructs the FlyCommand with the given plugin and API instances.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public FlyCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the command with its label, description, usage, and permission.
     */
    @Override
    public void onLoad() {
        setLabel("fly");
        setDescription("FLY_DESCRIPTION");
        setUsage("FLY_USAGE");
        setPermission(PERMISSION);
        register(plugin);
    }

    /**
     * Executes the fly command, toggling flight mode for the target player.
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
        Player target = ctx.getPlayer(0, ctx.getSender());
        if(target == null) {
            error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(0));
            return;
        }

        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            cmd.error(ctx.getSender(), "FLY_CANNOT_BE_TOGGLED");
            return;
        }

        boolean newState = !target.getAllowFlight();
        target.setAllowFlight(newState);

        if (target.equals(ctx.getSender())) {
            success(ctx.getSender(), "FLY_SUCCESS", "state", newState ? "enabled" : "disabled");
        } else {
            String senderName = ctx.isConsole() ? api.locales().resolve("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "FLY_OTHER_SUCCESS_SENDER", "state", newState ? "enabled" : "disabled", "player", target.getName());
            success(target, "FLY_OTHER_SUCCESS_PLAYER", "state", newState ? "enabled" : "disabled", "player", senderName);
        }
    }
}
