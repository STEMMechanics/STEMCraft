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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Command to set a player's personal time.
 */
public class PTimeCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.ptime";

    /**
     * Creates a new PTimeCommand instance.
     *
     * @param plugin The main STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public PTimeCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the command with its label, description, usage, and permission.
     */
    @Override
    public void onLoad() {
        setLabel("ptime");
        setDescription("PTIME_DESCRIPTION");
        setUsage("PTIME_USAGE");
        setPermission(PERMISSION);
        addTabCompletion("day", "{player}");
        addTabCompletion("night", "{player}");
        addTabCompletion("reset", "{player}");
        register(plugin);
    }

    /**
     * Executes the ptime command, allowing a player to set their personal time.
     *
     * @param cmd The command being executed.
     * @param ctx The context of the command execution.
     */
    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            cmd.error("PTIME_MODE_REQUIRED");
            return;
        }

        String mode = ctx.getArg(0).toLowerCase(Locale.ROOT);
        Player target = resolveTarget(cmd, ctx);
        if (target == null) return;

        switch (mode) {
            case "day" -> {
                target.setPlayerTime(6000L, false);
                cmd.info(ctx.getSender(), "PTIME_SET_DAY", "player", target.getName());
            }
            case "night" -> {
                target.setPlayerTime(18000L, false);
                cmd.info(ctx.getSender(), "PTIME_SET_NIGHT", "player", target.getName());
            }
            case "reset" -> {
                target.resetPlayerTime();
                cmd.info(ctx.getSender(), "PTIME_RESET", "player", target.getName());
            }
            default -> {
                long ticks;
                try {
                    ticks = Long.parseLong(mode);
                } catch (NumberFormatException e) {
                    cmd.error("PTIME_INVALID_MODE", "mode", mode);
                    return;
                }

                target.setPlayerTime(ticks, false);
                cmd.info(ctx.getSender(), "PTIME_SET_TICKS",
                        "player", target.getName(),
                        "time", String.valueOf(ticks));
            }
        }
    }

    /**
     * Resolves the target player for the command.
     *
     * @param cmd The command being executed.
     * @param ctx The context of the command execution.
     * @return The target Player, or null if not found or invalid.
     */
    private Player resolveTarget(Command cmd, CommandContext ctx) {
        if (ctx.args().size() >= 2) {
            OfflinePlayer off = ctx.getArgAsOfflinePlayer(1, null);
            if (off == null || !off.isOnline()) {
                cmd.error("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                return null;
            }
            return off.getPlayer();
        }

        if (!(ctx.getSender() instanceof Player sender)) {
            cmd.error("COMMAND_PLAYER_ONLY");
            return null;
        }

        return sender;
    }
}
